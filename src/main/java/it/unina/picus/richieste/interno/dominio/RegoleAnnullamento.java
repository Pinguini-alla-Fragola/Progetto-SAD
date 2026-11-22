package it.unina.picus.richieste.interno.dominio;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;

/**
 * Regole sullo stato e sul motivo dell'annullamento di una richiesta (#28, criteri 10, 11, 12 e 15).
 *
 * <p>Stabiliscono se un comando di annullamento può proseguire. Chi può annullare lo decide
 * {@link RegoleAccessoRichiesta#verificaAnnullamento}, che va chiamata per prima: a un account non
 * autorizzato non si rivela nulla sulle regole di stato. L'annullamento è ammesso in qualsiasi stato.
 *
 * <p>Una ripetizione su una richiesta già Annullata è idempotente anche senza motivo, come
 * promette il contratto di {@code RichiesteApi} e come prevede la #54. Per questo Annullata è tra
 * gli stati con motivo facoltativo, e {@link #motivoObbligatorio} e {@link #valuta} danno la
 * stessa risposta.
 *
 * @see RichiesteApi#annullaRichiesta
 */
public final class RegoleAnnullamento {

    /** Cosa deve fare il caso d'uso dopo aver applicato le regole. */
    public enum Decisione {
        /** Annulla la richiesta, conserva i dati già registrati e fa avvisare i soggetti coinvolti. */
        ANNULLA,
        /** La richiesta è già Annullata: restituisce l'esito esistente senza riaprire la ricerca. */
        GIA_ANNULLATA
    }

    // Stati con motivo facoltativo: Creata e Annullata (la ripetizione è idempotente).
    private static final Set<StatoRichiesta> MOTIVO_FACOLTATIVO =
            Set.of(StatoRichiesta.CREATA, StatoRichiesta.ANNULLATA);

    private RegoleAnnullamento() {
    }

    /**
     * Dice se per annullare partendo da questo stato serve un motivo.
     *
     * @param stato stato corrente della richiesta
     * @return {@code false} partendo da Creata e da Annullata, {@code true} negli altri stati
     */
    public static boolean motivoObbligatorio(StatoRichiesta stato) {
        return !MOTIVO_FACOLTATIVO.contains(Objects.requireNonNull(stato, "stato"));
    }

    /**
     * Applica le regole di stato e di motivo a un comando di annullamento già autorizzato.
     * Il motivo mancante è un valore {@code null} oppure fatto solo di spazi, anche non separabili
     * come U+00A0 e U+202F.
     *
     * @param stato stato corrente della richiesta
     * @param motivo motivo indicato da chi annulla, facoltativo partendo da Creata e da Annullata
     * @return {@link Decisione#GIA_ANNULLATA} se la richiesta è già Annullata, con o senza motivo,
     *         altrimenti {@link Decisione#ANNULLA}
     * @throws IllegalArgumentException se manca il motivo negli stati in cui è obbligatorio, cioè
     *         tutti tranne Creata e Annullata
     */
    public static Decisione valuta(StatoRichiesta stato, String motivo) {
        if (motivoObbligatorio(stato) && eVuoto(motivo)) {
            throw new DatiRichiestaNonValidiException(Map.of("motivo", "Motivo obbligatorio"));
        }
        return stato == StatoRichiesta.ANNULLATA ? Decisione.GIA_ANNULLATA : Decisione.ANNULLA;
    }

    /**
     * Prepara il motivo del primo annullamento per la persistenza.
     *
     * @param motivo testo già valutato rispetto allo stato di partenza
     * @return testo originale o {@code null} se il motivo è vuoto
     * @throws DatiRichiestaNonValidiException se il motivo contiene caratteri di controllo
     *         diversi da tabulazioni e ritorni a capo
     */
    static String motivoDaRegistrare(String motivo) {
        if (eVuoto(motivo)) {
            return null;
        }
        if (motivo.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r')) {
            throw new DatiRichiestaNonValidiException(Map.of("motivo", "Usa testo semplice per il motivo"));
        }
        return motivo;
    }

    // isBlank() non considera spazi i caratteri non separabili: isSpaceChar li include.
    private static boolean eVuoto(String motivo) {
        return motivo == null
                || motivo.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }
}
