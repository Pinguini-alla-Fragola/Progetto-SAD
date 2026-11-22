package it.unina.picus.catalogo.interno.dominio;

import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regole di validazione e normalizzazione dei dati descrittivi di una misura di catalogo.
 *
 * <p>Applica il principio GRASP <em>Pure Fabrication</em> e il pattern GoF <em>Strategy</em>
 * tramite specifiche parametriche per campo, garantendo uniformità con i vincoli di schema
 * ({@code catalogo_misure}) senza duplicare la logica tra creazione e aggiornamento.</p>
 */
public final class RegoleValidazioneMisura {

    /** Lunghezza massima ammessa per il codice della misura. */
    public static final int MAX_LUNGHEZZA_CODICE = 10;

    /** Lunghezza massima ammessa per il nome della misura. */
    public static final int MAX_LUNGHEZZA_NOME = 255;

    /** Lunghezza massima ammessa per la descrizione della misura. */
    public static final int MAX_LUNGHEZZA_DESCRIZIONE = 255;

    private static final SpecificaCampo SPEC_CODICE = new SpecificaCampo(
            "codice",
            MAX_LUNGHEZZA_CODICE,
            false,
            "Indica un codice misura in testo semplice, fino a 10 caratteri");

    private static final SpecificaCampo SPEC_NOME = new SpecificaCampo(
            "nome",
            MAX_LUNGHEZZA_NOME,
            false,
            "Indica il nome della misura in testo semplice, fino a 255 caratteri");

    private static final SpecificaCampo SPEC_DESCRIZIONE = new SpecificaCampo(
            "descrizione",
            MAX_LUNGHEZZA_DESCRIZIONE,
            true,
            "Indica la descrizione della misura in testo semplice, fino a 255 caratteri");

    private RegoleValidazioneMisura() {
    }

    /**
     * Valida e normalizza i dati della misura accumulando tutti gli errori riscontrati.
     *
     * @param dati dati della misura da validare
     * @return dati normalizzati (spazi esterni rimossi) pronti per essere applicati al dominio
     * @throws DatiMisuraNonValidiException se uno o più campi obbligatori mancano, superano i limiti
     *                                      o contengono caratteri di controllo non ammessi
     */
    public static DatiMisura validaENormalizza(DatiMisura dati) {
        Map<String, String> errori = new LinkedHashMap<>();
        String codice = validaCampo(dati == null ? null : dati.codice(), SPEC_CODICE, errori);
        String nome = validaCampo(dati == null ? null : dati.nome(), SPEC_NOME, errori);
        String descrizione = validaCampo(dati == null ? null : dati.descrizione(), SPEC_DESCRIZIONE, errori);
        if (!errori.isEmpty()) {
            throw new DatiMisuraNonValidiException(errori);
        }
        return new DatiMisura(codice, nome, descrizione, dati.erogabileADistanza());
    }

    private static String validaCampo(String valore, SpecificaCampo specifica, Map<String, String> errori) {
        String pulito = valore == null ? "" : valore.strip();
        if (pulito.isEmpty()
                || pulito.length() > specifica.lunghezzaMassima()
                || pulito.codePoints().anyMatch(c -> controlloNonAmmesso(c, specifica.consentiFormattazione()))) {
            errori.put(specifica.nomeCampo(), specifica.messaggioErrore());
        }
        return pulito;
    }

    private static boolean controlloNonAmmesso(int codePoint, boolean consentiFormattazione) {
        if (!Character.isISOControl(codePoint)) {
            return false;
        }
        return !consentiFormattazione || (codePoint != '\n' && codePoint != '\r' && codePoint != '\t');
    }

    /** Specifica parametrica di validazione per un singolo campo testuale della misura. */
    private record SpecificaCampo(
            String nomeCampo,
            int lunghezzaMassima,
            boolean consentiFormattazione,
            String messaggioErrore) {
    }
}
