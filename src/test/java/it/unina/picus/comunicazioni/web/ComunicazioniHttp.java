package it.unina.picus.comunicazioni.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.ClientHttp;
import it.unina.picus.shared.ClientHttp.Csrf;
import it.unina.picus.shared.ScenarioAccessi.Account;

/**
 * Comunicazioni usate attraverso le rotte HTTP reali di {@code ComunicazioniController} e
 * {@code ConsultazioneComunicazioniController}, come le usa la pagina: login JSON, token CSRF e una sessione per
 * account. Prima di ogni richiesta si verifica con {@link ClientHttp#richiediEndpointConsegnato} che la rotta
 * abbia un handler, così un esito non viene attribuito a un URL inesistente.
 *
 * <p>Le risposte si restituiscono così come arrivano: gli esiti attesi li verificano i test. Gli helper
 * {@code *Ok} controllano solo lo stato di successo e leggono il corpo.</p>
 */
final class ComunicazioniHttp {

    static final String INVIO = "/api/comunicazioni";
    static final String DESTINATARI = "/api/comunicazioni/destinatari";
    static final String INVIATE = "/api/comunicazioni/inviate";
    static final String RICEVUTE = "/api/comunicazioni/ricevute";
    static final String CONSULTA = "/api/comunicazioni/{id}";
    static final String ALLEGATO = "/api/comunicazioni/allegati/{id}";
    static final String DA_CONFERMARE = "/api/comunicazioni/da-confermare";

    /** Nome della parte JSON con i campi del modulo nell'invio multipart. */
    static final String PARTE_DATI = "dati";
    /** Nome delle parti con i file allegati. */
    static final String PARTE_FILE = "files";

    static final String CODICE_400 = "BAD_REQUEST";
    static final String MESSAGGIO_400 = "Controlla i campi della comunicazione";

    /** Come arrivano i campi del modulo: corpo JSON oppure parte {@code dati} di un multipart senza file. */
    enum Modalita {
        JSON, MULTIPART
    }

    /**
     * Risposta {@code 201} all'invio.
     *
     * @param id identificativo della comunicazione
     * @param titolo oggetto
     * @param destinatariTipo tipo di destinatari
     * @param destinatari numero di account che l'hanno ricevuta
     */
    record Inviata(UUID id, String titolo, String destinatariTipo, int destinatari) {}

    /**
     * Prima pagina di una vista, letta senza parametri.
     *
     * @param elementi comunicazioni della pagina, come mappe JSON
     * @param totale comunicazioni trovate in tutto lo storico dell'account
     */
    record Pagina(List<Map<String, Object>> elementi, long totale) {

        /** Vero se la comunicazione è tra gli elementi della pagina. */
        boolean contiene(UUID id) {
            return elementi.stream().anyMatch(elemento -> id.toString().equals(elemento.get("id")));
        }

        /** Vero se un elemento della pagina ha il marcatore nel titolo o nel contenuto. */
        boolean contieneMarcatore(String marcatore) {
            return elementi.stream().anyMatch(elemento -> contiene(elemento.get("titolo"), marcatore)
                    || contiene(elemento.get("contenuto"), marcatore));
        }

        private static boolean contiene(Object testo, String marcatore) {
            return testo instanceof String valore && valore.contains(marcatore);
        }
    }

    /**
     * File da allegare.
     *
     * @param nome nome del file
     * @param tipoDichiarato tipo dichiarato dal client nella parte multipart
     * @param contenuto byte del file
     */
    record File(String nome, String tipoDichiarato, byte[] contenuto) {}

    private final ClientHttp http;
    private final Map<UUID, MockHttpSession> sessioni = new HashMap<>();
    private final Map<UUID, Csrf> token = new HashMap<>();

    ComunicazioniHttp(ClientHttp http) {
        this.http = http;
    }

    // ------------------------------------------------------------------ corpo del modulo

    /**
     * Campi del modulo; un valore {@code null} lascia il campo assente dal JSON.
     *
     * @param titolo oggetto
     * @param contenuto testo
     * @param destinatariTipo tipo di destinatari
     * @param gruppo identificativi del gruppo
     * @return campi in ordine
     */
    static Map<String, Object> campi(String titolo, String contenuto, String destinatariTipo,
            Collection<?> gruppo) {
        Map<String, Object> campi = new LinkedHashMap<>();
        if (titolo != null) {
            campi.put("titolo", titolo);
        }
        if (contenuto != null) {
            campi.put("contenuto", contenuto);
        }
        if (destinatariTipo != null) {
            campi.put("destinatariTipo", destinatariTipo);
        }
        if (gruppo != null) {
            campi.put("gruppo", gruppo.stream().map(String::valueOf).toList());
        }
        return campi;
    }

    /** JSON minimo per testi e liste di testi, sufficiente per i campi del modulo. */
    static String json(Map<String, Object> campi) {
        StringBuilder json = new StringBuilder("{");
        campi.forEach((nome, valore) -> {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append(testoJson(nome)).append(':').append(valoreJson(valore));
        });
        return json.append('}').toString();
    }

    private static String valoreJson(Object valore) {
        if (valore instanceof Collection<?> lista) {
            List<String> elementi = new ArrayList<>();
            lista.forEach(elemento -> elementi.add(valoreJson(elemento)));
            return "[" + String.join(",", elementi) + "]";
        }
        return valore == null ? "null" : testoJson(valore.toString());
    }

    private static String testoJson(String testo) {
        StringBuilder json = new StringBuilder("\"");
        for (char carattere : testo.toCharArray()) {
            switch (carattere) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (carattere < 0x20) {
                        json.append(String.format("\\u%04x", (int) carattere));
                    } else {
                        json.append(carattere);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    // ------------------------------------------------------------------ invio

    /**
     * Invio nella modalità indicata, senza file.
     *
     * @param banco account che invia
     * @param modalita JSON oppure multipart
     * @param campi campi del modulo
     * @return risposta del server
     * @throws Exception se la richiesta non riesce
     */
    MockHttpServletResponse invia(Account banco, Modalita modalita, Map<String, Object> campi) throws Exception {
        return modalita == Modalita.JSON ? inviaJson(banco, campi) : inviaMultipart(banco, campi, List.of());
    }

    MockHttpServletResponse inviaJson(Account banco, Map<String, Object> campi) throws Exception {
        Csrf csrf = csrf(banco);
        MockHttpServletRequestBuilder richiesta = post(INVIO)
                .header(csrf.header(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(campi))
                .accept(MediaType.APPLICATION_JSON);
        return esegui(banco, richiesta);
    }

    MockHttpServletResponse inviaMultipart(Account banco, Map<String, Object> campi, List<File> file)
            throws Exception {
        Csrf csrf = csrf(banco);
        List<MockPart> parti = new ArrayList<>();
        MockPart dati = new MockPart(PARTE_DATI, json(campi).getBytes(StandardCharsets.UTF_8));
        dati.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        parti.add(dati);
        for (File allegato : file) {
            MockPart parte = new MockPart(PARTE_FILE, allegato.nome(), allegato.contenuto());
            parte.getHeaders().setContentType(MediaType.parseMediaType(allegato.tipoDichiarato()));
            parti.add(parte);
        }
        // Le parti arrivano al resolver multipart del server come in una richiesta reale (request.getParts()).
        MockHttpServletRequestBuilder richiesta = post(INVIO)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(csrf.header(), csrf.token())
                .accept(MediaType.APPLICATION_JSON)
                .with(request -> {
                    parti.forEach(request::addPart);
                    return request;
                });
        return esegui(banco, richiesta);
    }

    /**
     * Verifica il {@code 201} di un invio e ne legge il corpo.
     *
     * @param risposta risposta all'invio
     * @return comunicazione inviata
     * @throws Exception se il corpo non è leggibile
     */
    static Inviata inviata(MockHttpServletResponse risposta) throws Exception {
        String corpo = risposta.getContentAsString();
        assertEquals(201, risposta.getStatus(), () -> "invio non accettato: " + corpo);
        Map<String, Object> json = JsonPath.read(corpo, "$");
        assertEquals(Set.of("id", "titolo", "inviataIl", "destinatariTipo", "destinatari"), json.keySet());
        return new Inviata(UUID.fromString((String) json.get("id")), (String) json.get("titolo"),
                (String) json.get("destinatariTipo"), ((Number) json.get("destinatari")).intValue());
    }

    // ------------------------------------------------------------------ viste

    /**
     * Account selezionabili per un gruppo, come li vede il Banco.
     *
     * @param banco account Banco
     * @param tipo tipo di destinatari
     * @return identificativi degli account selezionabili
     * @throws Exception se la richiesta non riesce
     */
    List<UUID> destinatari(Account banco, String tipo) throws Exception {
        MockHttpServletRequestBuilder richiesta = get(DESTINATARI).param("tipo", tipo)
                .accept(MediaType.APPLICATION_JSON);
        MockHttpServletResponse risposta = esegui(banco, richiesta);
        assertEquals(200, risposta.getStatus());
        List<Map<String, Object>> json = JsonPath.read(risposta.getContentAsString(), "$");
        json.forEach(account -> assertEquals(Set.of("id", "nome"), account.keySet()));
        return json.stream().map(account -> UUID.fromString((String) account.get("id"))).toList();
    }

    /**
     * Identificativo di un account preso dall'elenco dei selezionabili, come farebbe la pagina del Banco.
     *
     * @param banco account Banco
     * @param tipo tipo di destinatari a gruppo
     * @param account account da scegliere
     * @return identificativo restituito dal server
     * @throws Exception se la richiesta non riesce
     */
    UUID scegli(Account banco, String tipo, Account account) throws Exception {
        List<UUID> selezionabili = destinatari(banco, tipo);
        assertTrue(selezionabili.contains(account.id()),
                () -> "account " + account.ruolo() + " del test assente da /destinatari?tipo=" + tipo);
        return selezionabili.get(selezionabili.indexOf(account.id()));
    }

    /**
     * Prima pagina delle comunicazioni inviate dal Banco, senza ricerca né filtri: le più recenti per
     * {@code inviataIl}, al massimo 20 ({@code FiltroComunicazioni.DIMENSIONE_PAGINA}). Una comunicazione appena
     * inviata dal test è in questa pagina perché le classi della suite non vengono eseguite in parallelo.
     *
     * @param banco account Banco
     * @return prima pagina e totale
     * @throws Exception se la richiesta non riesce
     */
    Pagina inviate(Account banco) throws Exception {
        return pagina(banco, get(INVIATE).accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Prima pagina delle comunicazioni ricevute dall'account, senza ricerca né filtri: le più recenti per
     * {@code inviataIl}, al massimo 20. Ogni test usa account appena creati, che ricevono solo le comunicazioni
     * inviate dopo la loro creazione, e l'esecuzione non è parallela: le comunicazioni del test sono quindi tutte
     * in questa pagina e il {@code totale} cambia solo per gli invii del test.
     *
     * @param destinatario account OPT od operatore
     * @return prima pagina e totale
     * @throws Exception se la richiesta non riesce
     */
    Pagina ricevute(Account destinatario) throws Exception {
        return pagina(destinatario, get(RICEVUTE).accept(MediaType.APPLICATION_JSON));
    }

    private Pagina pagina(Account attore, MockHttpServletRequestBuilder richiesta) throws Exception {
        MockHttpServletResponse risposta = esegui(attore, richiesta);
        assertEquals(200, risposta.getStatus(), () -> "vista non disponibile per " + attore.ruolo());
        Map<String, Object> json = JsonPath.read(risposta.getContentAsString(), "$");
        assertEquals(Set.of("elementi", "pagina", "dimensione", "totale"), json.keySet());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elementi = (List<Map<String, Object>>) json.get("elementi");
        return new Pagina(elementi, ((Number) json.get("totale")).longValue());
    }

    long daConfermare(Account destinatario) throws Exception {
        MockHttpServletResponse risposta = esegui(destinatario,
                get(DA_CONFERMARE).accept(MediaType.APPLICATION_JSON));
        assertEquals(200, risposta.getStatus());
        Map<String, Object> json = JsonPath.read(risposta.getContentAsString(), "$");
        assertEquals(Set.of("daConfermare"), json.keySet());
        return ((Number) json.get("daConfermare")).longValue();
    }

    // ------------------------------------------------------------------ consultazione e download

    MockHttpServletResponse consulta(Account attore, UUID comunicazioneId) throws Exception {
        return esegui(attore, get(CONSULTA, comunicazioneId).accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Consultazione riuscita.
     *
     * @param attore account che consulta
     * @param comunicazioneId comunicazione
     * @return corpo JSON
     * @throws Exception se la richiesta non riesce
     */
    Map<String, Object> consultaOk(Account attore, UUID comunicazioneId) throws Exception {
        MockHttpServletResponse risposta = consulta(attore, comunicazioneId);
        assertEquals(200, risposta.getStatus(), () -> attore.ruolo() + " deve poter consultare la comunicazione");
        Map<String, Object> json = JsonPath.read(risposta.getContentAsString(), "$");
        assertEquals(Set.of("id", "titolo", "contenuto", "inviataIl", "destinatariTipo", "destinatari", "allegati",
                "dettagliAllegati", "confermataIl"), json.keySet());
        return json;
    }

    MockHttpServletResponse scarica(Account attore, UUID allegatoId) throws Exception {
        return esegui(attore, get(ALLEGATO, allegatoId).accept(MediaType.ALL));
    }

    // ------------------------------------------------------------------ errori

    /**
     * Errore {@code 400} di {@code ErroriComunicazioni}: soli campi {@code codice}, {@code messaggio} e
     * {@code campi}, codice e messaggio fissi, esattamente i campi attesi, e nessuno dei valori inseriti.
     *
     * @param risposta risposta del server
     * @param attesi nomi dei campi da correggere
     * @param marcatore valore inserito che non deve tornare nel corpo
     * @throws Exception se il corpo non è leggibile
     */
    static void assertCampiDaCompletare(MockHttpServletResponse risposta, Set<String> attesi, String marcatore)
            throws Exception {
        String corpo = risposta.getContentAsString();
        assertEquals(400, risposta.getStatus(), () -> "atteso 400, corpo: " + corpo);
        Map<String, Object> errore = JsonPath.read(corpo, "$");
        assertEquals(Set.of("codice", "messaggio", "campi"), errore.keySet(), "solo i campi pubblici dell'errore");
        assertEquals(CODICE_400, errore.get("codice"));
        assertEquals(MESSAGGIO_400, errore.get("messaggio"));
        @SuppressWarnings("unchecked")
        Map<String, Object> campi = (Map<String, Object>) errore.get("campi");
        assertEquals(attesi, campi.keySet(), "campi da completare");
        campi.values().forEach(messaggio -> assertTrue(messaggio instanceof String text && !text.isBlank(),
                "ogni campo ha un messaggio"));
        assertFalse(corpo.contains(marcatore), "l'errore non deve ripetere i valori inseriti");
    }

    // ------------------------------------------------------------------ sessioni

    private MockHttpServletResponse esegui(Account attore, MockHttpServletRequestBuilder richiesta)
            throws Exception {
        http.richiediEndpointConsegnato(richiesta);
        return http.esegui(richiesta.session(sessione(attore)));
    }

    private MockHttpSession sessione(Account attore) throws Exception {
        MockHttpSession sessione = sessioni.get(attore.id());
        if (sessione == null) {
            sessione = http.accedi(attore);
            sessioni.put(attore.id(), sessione);
        }
        return sessione;
    }

    private Csrf csrf(Account attore) throws Exception {
        Csrf csrf = token.get(attore.id());
        if (csrf == null) {
            csrf = http.csrf(sessione(attore));
            token.put(attore.id(), csrf);
        }
        return csrf;
    }
}
