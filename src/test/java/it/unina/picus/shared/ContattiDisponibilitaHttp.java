package it.unina.picus.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettaglioContatto;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.shared.ClientHttp.Csrf;
import it.unina.picus.shared.ScenarioAccessi.Account;

/**
 * Contatti di disponibilità usati attraverso gli endpoint HTTP reali di {@code ContattiDisponibilitaController}.
 * Consultazione e risposta dell'erogatore e storico dei contatti sono già consegnati ma non hanno un contratto
 * {@code *Api}: la suite li esercita come un client, con il login JSON e il token CSRF della sessione.
 *
 * <p>Gli esiti HTTP sono tradotti nelle eccezioni dei contratti, così i casi restano scritti con le stesse
 * convenzioni: {@code 403} diventa {@link SecurityException}, {@code 404} {@link NoSuchElementException},
 * {@code 409} {@link IllegalStateException} e {@code 400} {@link IllegalArgumentException}. Ogni altro esito fa
 * fallire il test. Un diniego non deve contenere l'identificativo della risorsa richiesta.</p>
 */
public final class ContattiDisponibilitaHttp {

    /** Consultazione del contatto da parte del destinatario. */
    public static final String CONSULTA_CONTATTO = "/api/contatti-disponibilita/{id}";
    /** Risposta del destinatario al contatto. */
    public static final String RISPONDI_CONTATTO = "/api/contatti-disponibilita/{id}/risposta";
    /** Storico dei contatti di una richiesta, riservato al Banco. */
    public static final String CONTATTI_RICHIESTA = "/api/richieste/{id}/contatti";

    private final ClientHttp http;
    private final Map<UUID, MockHttpSession> sessioni = new HashMap<>();

    /**
     * Crea l'adattatore sul client HTTP della suite.
     *
     * @param http client con la catena di sicurezza attiva
     */
    public ContattiDisponibilitaHttp(ClientHttp http) {
        this.http = http;
    }

    /**
     * Dati minimi del contatto per l'account che lo consulta ({@code GET}).
     *
     * @param attore account che consulta
     * @param contattoId contatto richiesto
     * @return proiezione restituita dal server
     */
    public DettaglioContatto consultaContatto(Account attore, UUID contattoId) {
        MockHttpServletRequestBuilder richiesta = get(CONSULTA_CONTATTO, contattoId).accept(MediaType.APPLICATION_JSON);
        String corpo = esegui(attore, richiesta, contattoId);
        Map<String, Object> json = JsonPath.read(corpo, "$");
        @SuppressWarnings("unchecked")
        Map<String, Object> contatto = (Map<String, Object>) json.get("contatto");
        return new DettaglioContatto(contatto(contatto), ((Number) json.get("misuraId")).longValue(),
                (String) json.get("nomeMisura"), TipoDestinatario.valueOf((String) json.get("tipoDestinatario")),
                (String) json.get("comuneCodice"));
    }

    /**
     * Risposta dell'account al contatto ({@code POST} con corpo JSON e token CSRF).
     *
     * @param attore account che risponde
     * @param contattoId contatto a cui si risponde
     * @param disponibile esito dichiarato
     * @return contatto con l'esito registrato
     */
    public ContattoDisponibilita rispondiDisponibilita(Account attore, UUID contattoId, boolean disponibile) {
        MockHttpSession sessione = sessione(attore);
        Csrf csrf = esegui(() -> http.csrf(sessione));
        MockHttpServletRequestBuilder richiesta = post(RISPONDI_CONTATTO, contattoId)
                .header(csrf.header(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"disponibile\":" + disponibile + "}")
                .accept(MediaType.APPLICATION_JSON);
        return contatto(JsonPath.read(esegui(attore, richiesta, contattoId), "$"));
    }

    /**
     * Storico dei contatti di una richiesta ({@code GET}), riservato al Banco.
     *
     * @param attore account che consulta
     * @param richiestaId richiesta di cui leggere i contatti
     * @return contatti in ordine di invio
     */
    public List<ContattoDisponibilita> elencoContatti(Account attore, UUID richiestaId) {
        MockHttpServletRequestBuilder richiesta = get(CONTATTI_RICHIESTA, richiestaId).accept(MediaType.APPLICATION_JSON);
        List<Map<String, Object>> json = JsonPath.read(esegui(attore, richiesta, richiestaId), "$");
        return json.stream().map(ContattiDisponibilitaHttp::contatto).toList();
    }

    private MockHttpSession sessione(Account attore) {
        return sessioni.computeIfAbsent(attore.id(), id -> esegui(() -> http.accedi(attore)));
    }

    private String esegui(Account attore, MockHttpServletRequestBuilder richiesta, UUID risorsa) {
        esegui(() -> {
            http.richiediEndpointConsegnato(richiesta);
            return null;
        });
        MockHttpServletResponse risposta = esegui(() -> http.esegui(richiesta.session(sessione(attore))));
        String corpo = esegui(risposta::getContentAsString);
        int stato = risposta.getStatus();
        if (stato == 200) {
            return corpo;
        }
        assertFalse(corpo.contains(risorsa.toString()), "un esito negativo non deve restituire dati della risorsa");
        String esito = "HTTP " + stato + " su " + risorsa;
        switch (stato) {
            case 403 -> throw new SecurityException("FORBIDDEN: " + esito);
            case 404 -> throw new NoSuchElementException("NOT_FOUND: " + esito);
            case 409 -> throw new IllegalStateException("CONFLICT: " + esito);
            case 400 -> throw new IllegalArgumentException("BAD_REQUEST: " + esito);
            default -> throw new AssertionError("esito HTTP inatteso: " + esito + " " + corpo);
        }
    }

    private static ContattoDisponibilita contatto(Map<String, Object> json) {
        return new ContattoDisponibilita(UUID.fromString((String) json.get("id")),
                UUID.fromString((String) json.get("richiestaId")), UUID.fromString((String) json.get("erogatoreId")),
                AmbitoErogazione.valueOf((String) json.get("ambito")), EsitoContatto.valueOf((String) json.get("esito")));
    }

    /** Operazione HTTP che può lanciare eccezioni controllate. */
    @FunctionalInterface
    private interface Chiamata<T> {
        T esegui() throws Exception;
    }

    /** Le eccezioni controllate di MockMvc sono errori del test, non esiti del server. */
    private static <T> T esegui(Chiamata<T> chiamata) {
        try {
            return chiamata.esegui();
        } catch (RuntimeException | Error errore) {
            throw errore;
        } catch (Exception errore) {
            throw new AssertionError("chiamata HTTP non eseguita", errore);
        }
    }
}
