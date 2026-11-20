package it.unina.picus.catalogo.web;

import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.interno.dominio.DatiMisuraNonValidiException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Adattatore di ingresso HTTP che converte il corpo JSON nei DTO di comando del catalogo,
 * applicando allow-list esplicite sui campi e controlli di tipo prima del dominio.
 *
 * <p>Applica il principio GRASP <em>Pure Fabrication</em> e il pattern GoF <em>Adapter</em>,
 * prevenendo assegnazioni di massa (<em>mass assignment</em>) e leak dei valori inviati.</p>
 */
final class MisuraJson {

    private static final Set<String> CAMPI_CREAZIONE =
            Set.of("codice", "nome", "descrizione", "erogabileADistanza");

    private static final Set<String> CAMPI_AGGIORNAMENTO =
            Set.of("codice", "nome", "descrizione", "erogabileADistanza", "attiva");

    private MisuraJson() {
    }

    /**
     * Dati e stato estratti da una richiesta di aggiornamento di misura.
     *
     * @param dati dati descrittivi della misura
     * @param attiva nuovo stato di attivazione richiesto
     */
    record RichiestaAggiornamento(DatiMisura dati, boolean attiva) {
    }

    /**
     * Converte e verifica i tipi dei campi JSON per la creazione di una misura.
     *
     * @param corpo mappa delle proprietà JSON ricevute
     * @return dati tipizzati pronti per la validazione di dominio
     * @throws DatiMisuraNonValidiException se il corpo è assente, contiene campi estranei o tipi errati
     */
    static DatiMisura leggiCreazione(Map<String, Object> corpo) {
        verificaCampiAmmessi(corpo, CAMPI_CREAZIONE);
        Map<String, String> errori = new LinkedHashMap<>();
        DatiMisura dati = estraiDatiComuni(corpo, errori);
        if (!errori.isEmpty()) {
            throw new DatiMisuraNonValidiException(errori);
        }
        return dati;
    }

    /**
     * Converte e verifica i tipi dei campi JSON per l'aggiornamento di una misura.
     *
     * @param corpo mappa delle proprietà JSON ricevute
     * @return dati tipizzati e stato di attivazione pronti per il dominio
     * @throws DatiMisuraNonValidiException se il corpo è assente, contiene campi estranei o tipi errati
     */
    static RichiestaAggiornamento leggiAggiornamento(Map<String, Object> corpo) {
        verificaCampiAmmessi(corpo, CAMPI_AGGIORNAMENTO);
        Map<String, String> errori = new LinkedHashMap<>();
        DatiMisura dati = estraiDatiComuni(corpo, errori);
        boolean attiva = leggiBooleano(corpo, "attiva", true, "Indica se la misura è attiva", errori);
        if (!errori.isEmpty()) {
            throw new DatiMisuraNonValidiException(errori);
        }
        return new RichiestaAggiornamento(dati, attiva);
    }

    /**
     * Verifica che un comando privo di parametri non riceva proprietà o valori {@code null} inattesi.
     *
     * @param corpo corpo opzionale letto dal parser HTTP
     * @throws DatiMisuraNonValidiException se il corpo non è assente né un oggetto JSON vuoto
     */
    static void verificaCorpoVuoto(CorpoJsonCatalogo corpo) {
        if (corpo != null && (corpo.campi() == null || !corpo.campi().isEmpty())) {
            throw new DatiMisuraNonValidiException(Map.of("comando", "Questo comando non prevede campi aggiuntivi"));
        }
    }

    private static void verificaCampiAmmessi(Map<String, Object> corpo, Set<String> ammessi) {
        if (corpo == null || !ammessi.containsAll(corpo.keySet())) {
            throw new DatiMisuraNonValidiException(Map.of("misura", "Sono ammessi solo i campi previsti per la misura"));
        }
    }

    private static DatiMisura estraiDatiComuni(Map<String, Object> corpo, Map<String, String> errori) {
        String codice = testo(corpo, "codice", "Indica un codice misura testuale", errori);
        String nome = testo(corpo, "nome", "Indica il nome della misura in formato testuale", errori);
        String descrizione = testo(corpo, "descrizione", "Indica una descrizione in formato testuale", errori);
        boolean erogabileADistanza = leggiBooleano(
                corpo,
                "erogabileADistanza",
                false,
                "Indica vero o falso per l'erogazione a distanza",
                errori);
        return new DatiMisura(codice, nome, descrizione, erogabileADistanza);
    }

    private static String testo(Map<String, Object> corpo, String campo, String messaggio, Map<String, String> errori) {
        Object valore = corpo.get(campo);
        if (valore == null || valore instanceof String) {
            return (String) valore;
        }
        errori.put(campo, messaggio);
        return null;
    }

    private static boolean leggiBooleano(
            Map<String, Object> corpo,
            String campo,
            boolean obbligatorio,
            String messaggio,
            Map<String, String> errori) {
        if (!corpo.containsKey(campo)) {
            if (obbligatorio) {
                errori.put(campo, messaggio);
            }
            return false;
        }
        Object valore = corpo.get(campo);
        if (valore instanceof Boolean flag) {
            return flag;
        }
        errori.put(campo, messaggio);
        return false;
    }
}
