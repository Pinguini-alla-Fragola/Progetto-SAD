package it.unina.picus.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.ScenarioAccessi.Account;

import jakarta.servlet.Filter;

/**
 * Client HTTP dei test #28: chiama il server con la catena di Spring Security attiva, come un client
 * reale, e usa le regole di {@code SecurityConfig}. L'accesso è il login JSON pubblico {@link #LOGIN}
 * con {@code email} e {@code password}; il token CSRF si legge da {@link #TOKEN_CSRF} e si invia
 * nell'header indicato dal server.
 *
 * <p>Prima di attribuire un esito a una risorsa, i test verificano con {@link #richiediEndpointConsegnato}
 * che la stessa richiesta abbia un handler reale: la sicurezza risponde {@code 401} anche a un URL senza
 * controller, quindi un {@code 401} da solo non dimostra che la risorsa sia protetta. Un endpoint di una
 * capacità non ancora consegnata usa {@link #richiediEndpointDellaCapacita}: la sospensione dipende dallo stato
 * dichiarato in {@link Capacita}, non dalla presenza dell'handler.</p>
 *
 * <p>I dinieghi e gli errori si verificano sul corpo completo: {@link #assertDiniegoSenzaCorpo} per il
 * {@code 403} e {@link #assertErrorePubblico} per gli errori JSON.</p>
 */
public final class ClientHttp {

    /** Token CSRF della sessione, pubblico. */
    public static final String TOKEN_CSRF = "/api/auth/csrf";
    /** Accesso con email e password in JSON, pubblico. */
    public static final String LOGIN = "/api/auth/login";

    private static final Pattern CAMPO_HEADER = Pattern.compile("\"headerName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CAMPO_TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Token CSRF restituito da {@link #TOKEN_CSRF}.
     *
     * @param header nome dell'header in cui inviarlo
     * @param token valore del token
     */
    public record Csrf(String header, String token) {}

    /**
     * Sessione senza identità autenticata, con il proprio token CSRF.
     *
     * @param sessione sessione HTTP anonima
     * @param csrf token CSRF della sessione
     */
    public record SessioneAnonima(MockHttpSession sessione, Csrf csrf) {}

    private final WebApplicationContext web;
    private final MockMvc mvc;
    private final RequestMappingHandlerMapping mapping;

    /**
     * Crea il client sul contesto web dei test.
     *
     * @param web contesto web con la catena di Spring Security
     */
    public ClientHttp(WebApplicationContext web) {
        this.web = web;
        Filter sicurezza = web.getBean("springSecurityFilterChain", Filter.class);
        this.mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(sicurezza).build();
        this.mapping = web.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
    }

    /**
     * Esegue una richiesta e restituisce la risposta.
     *
     * @param richiesta richiesta da eseguire
     * @return risposta del server
     * @throws Exception se MockMvc non riesce a eseguirla
     */
    public MockHttpServletResponse esegui(MockHttpServletRequestBuilder richiesta) throws Exception {
        return mvc.perform(richiesta).andReturn().getResponse();
    }

    /**
     * Dice se un controller gestisce la richiesta, costruita esattamente come quella che il test esegue:
     * stesso metodo, percorso, {@code Content-Type}, {@code Accept} e corpo. Una rotta che esiste solo per
     * un altro metodo HTTP non è esposta per questo metodo. Ogni altra eccezione del mapping, per esempio un
     * tipo di contenuto non accettato, emerge e fa fallire il test, perché indica una richiesta del test
     * diversa da quella prevista dal controller.
     *
     * @param richiesta richiesta che il test esegue
     * @return {@code true} se un controller gestisce la richiesta
     * @throws Exception errore imprevisto del mapping
     */
    public boolean mappato(MockHttpServletRequestBuilder richiesta) throws Exception {
        MockHttpServletRequest costruita = richiesta.buildRequest(web.getServletContext());
        ServletRequestPathUtils.parseAndCache(costruita);
        try {
            return mapping.getHandler(costruita) != null;
        } catch (HttpRequestMethodNotSupportedException soloAltriMetodi) {
            return false;
        }
    }

    /**
     * Collega l'endpoint allo stato di consegna dichiarato in {@link Capacita}: se la capacità non è ancora
     * consegnata il caso si sospende, senza guardare il mapping; se è consegnata l'endpoint deve avere un
     * handler, altrimenti il test fallisce con {@link #richiediEndpointConsegnato}.
     *
     * @param capacita capacità che comprende l'endpoint
     * @param richiesta richiesta che il test esegue
     * @throws Exception errore imprevisto del mapping
     */
    public void richiediEndpointDellaCapacita(Capacita capacita, MockHttpServletRequestBuilder richiesta)
            throws Exception {
        assumeTrue(capacita.consegnata(),
                () -> "capacità " + capacita + " non ancora consegnata: caso sospeso (" + descrivi(richiesta) + ")");
        richiediEndpointConsegnato(richiesta);
    }

    /**
     * Fa fallire il test se l'endpoint di una capacità già consegnata non ha un handler: è una regressione.
     *
     * @param richiesta richiesta che il test esegue
     * @throws Exception errore imprevisto del mapping
     */
    public void richiediEndpointConsegnato(MockHttpServletRequestBuilder richiesta) throws Exception {
        boolean esposto = mappato(richiesta);
        assertTrue(esposto, () -> "endpoint consegnato ma senza handler (regressione): " + descrivi(richiesta));
    }

    /**
     * Diniego di una API: {@code 403} con corpo vuoto, come lo produce {@code GestioneRifiuti}. Il corpo vuoto
     * esclude qualunque dato della risorsa, del titolare o della descrizione, non solo l'identificativo.
     *
     * @param risposta risposta del server
     * @throws Exception se il corpo non è leggibile
     */
    public static void assertDiniegoSenzaCorpo(MockHttpServletResponse risposta) throws Exception {
        assertEquals(403, risposta.getStatus(), "atteso FORBIDDEN (403)");
        assertEquals("", risposta.getContentAsString(), "il 403 delle API ha il corpo vuoto");
    }

    /**
     * Errore JSON di {@code ErroriRichieste}: soli campi pubblici {@code codice}, {@code messaggio} e
     * {@code campi}, con codice e messaggio fissi del contratto e nessun campo: nessun dato della pratica, della
     * OPT o della risorsa.
     *
     * @param risposta risposta del server
     * @param stato stato HTTP atteso
     * @param codice codice pubblico atteso
     * @param messaggio messaggio fisso atteso
     * @throws Exception se il corpo non è leggibile
     */
    public static void assertErrorePubblico(MockHttpServletResponse risposta, int stato, String codice,
            String messaggio) throws Exception {
        assertEquals(stato, risposta.getStatus());
        Map<String, Object> errore = JsonPath.read(risposta.getContentAsString(), "$");
        assertEquals(Set.of("codice", "messaggio", "campi"), errore.keySet(), "solo i campi pubblici dell'errore");
        assertEquals(codice, errore.get("codice"));
        assertEquals(messaggio, errore.get("messaggio"));
        assertEquals(Map.of(), errore.get("campi"), "nessun campo nell'errore");
    }

    private String descrivi(MockHttpServletRequestBuilder richiesta) {
        MockHttpServletRequest costruita = richiesta.buildRequest(web.getServletContext());
        return costruita.getMethod() + " " + costruita.getRequestURI();
    }

    /**
     * Apre una sessione anonima e ne legge il token CSRF.
     *
     * @return sessione anonima con il suo token
     * @throws Exception se la richiesta non riesce
     */
    public SessioneAnonima sessioneAnonima() throws Exception {
        MvcResult esito = mvc.perform(get(TOKEN_CSRF).accept(MediaType.APPLICATION_JSON)).andReturn();
        Csrf csrf = estraiCsrf(esito);
        MockHttpSession sessione = (MockHttpSession) esito.getRequest().getSession(false);
        assertNotNull(sessione, "il token CSRF deve essere legato a una sessione");
        return new SessioneAnonima(sessione, csrf);
    }

    /**
     * Rilegge il token CSRF di una sessione: Spring Security lo rinnova quando la sessione si autentica.
     *
     * @param sessione sessione di cui leggere il token
     * @return token CSRF corrente
     * @throws Exception se la richiesta non riesce
     */
    public Csrf csrf(MockHttpSession sessione) throws Exception {
        return estraiCsrf(mvc.perform(get(TOKEN_CSRF).session(sessione).accept(MediaType.APPLICATION_JSON))
                .andReturn());
    }

    /**
     * Richiesta di accesso JSON con il token CSRF della sessione anonima.
     *
     * @param anonima sessione anonima da autenticare
     * @param email email dell'account
     * @param password password dell'account
     * @return richiesta di login pronta da eseguire
     */
    public static MockHttpServletRequestBuilder login(SessioneAnonima anonima, String email, String password) {
        return post(LOGIN).session(anonima.sessione())
                .header(anonima.csrf().header(), anonima.csrf().token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .accept(MediaType.APPLICATION_JSON);
    }

    /**
     * Apre una sessione autenticata con il login JSON, come il client.
     *
     * @param account account sintetico con la password di prova
     * @return sessione autenticata
     * @throws Exception se la richiesta non riesce
     */
    public MockHttpSession accedi(Account account) throws Exception {
        SessioneAnonima anonima = sessioneAnonima();
        MockHttpServletResponse esito = esegui(login(anonima, account.email(), ScenarioAccessi.PASSWORD));
        assertEquals(204, esito.getStatus(), "le credenziali di prova devono essere accettate");
        return anonima.sessione(); // MockHttpSession conserva l'oggetto anche dopo il cambio di identificativo
    }

    private static Csrf estraiCsrf(MvcResult esito) throws Exception {
        String corpo = esito.getResponse().getContentAsString();
        Matcher header = CAMPO_HEADER.matcher(corpo);
        Matcher token = CAMPO_TOKEN.matcher(corpo);
        assertEquals(200, esito.getResponse().getStatus(), TOKEN_CSRF + " deve essere pubblico");
        assertTrue(header.find() && token.find(), "risposta di " + TOKEN_CSRF + " senza headerName o token");
        return new Csrf(header.group(1), token.group(1));
    }
}
