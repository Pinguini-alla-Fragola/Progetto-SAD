package it.unina.picus.richieste.interno.dominio;

import java.util.Objects;
import java.util.Set;

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
 * promette il contratto di {@code RichiesteApi} e come prevede la #54: si riconosce prima di
 * validare il motivo.
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

    // Stati da cui il motivo è facoltativo.
    private static final Set<StatoRichiesta> MOTIVO_FACOLTATIVO = Set.of(StatoRichiesta.CREATA);

    private RegoleAnnullamento() {
    }

    /**
     * Dice se per annullare partendo da questo stato serve un motivo.
     *
     * @param stato stato corrente della richiesta
     * @return {@code false} solo partendo da Creata
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
     * @param motivo motivo indicato da chi annulla, facoltativo solo partendo da Creata
     * @return {@link Decisione#GIA_ANNULLATA} se la richiesta è già Annullata, con o senza motivo,
     *         altrimenti {@link Decisione#ANNULLA}
     * @throws IllegalArgumentException se la richiesta non è Annullata e manca il motivo richiesto
     */
    public static Decisione valuta(StatoRichiesta stato, String motivo) {
        if (stato == StatoRichiesta.ANNULLATA) {
            return Decisione.GIA_ANNULLATA;
        }
        if (motivoObbligatorio(stato) && eVuoto(motivo)) {
            throw new IllegalArgumentException("Motivo obbligatorio");
        }
        return Decisione.ANNULLA;
    }

    // isBlank() non considera spazi i caratteri non separabili: isSpaceChar li include.
    private static boolean eVuoto(String motivo) {
        return motivo == null
                || motivo.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }
}
