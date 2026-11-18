package it.unina.picus.shared;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Sessione HTTP di un utente nei test di integrazione, ottenuta con il login reale.
 *
 * Si comporta come il client web: chiede il token CSRF a {@code /api/auth/csrf}, accede con
 * {@code POST /api/auth/login} e presenta sessione e token a ogni chiamata. Nessun utente
 * simulato: l'identità è quella che l'applicazione ricava dalla sessione, quindi restano attivi
 * anche il controllo sull'account revocato e la protezione CSRF.
 */
public final class SessioneDiProva {

    private final MockMvc mvc;
    private final MockHttpSession sessione = new MockHttpSession();
    private String intestazioneCsrf;
    private String tokenCsrf;

    private SessioneDiProva(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** Accede con email e password e restituisce la sessione autenticata. */
    public static SessioneDiProva accedi(MockMvc mvc, String email, String password) throws Exception {
        SessioneDiProva nuova = new SessioneDiProva(mvc);
        nuova.leggiTokenCsrf();
        nuova.post("/api/auth/login", "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}")
                .andExpect(status().isNoContent());
        // dopo il login Spring Security rigenera il token: va richiesto di nuovo
        nuova.leggiTokenCsrf();
        return nuova;
    }

    /** Sessione senza login, con un token CSRF valido: serve per provare gli accessi non autenticati. */
    public static SessioneDiProva anonima(MockMvc mvc) throws Exception {
        SessioneDiProva nuova = new SessioneDiProva(mvc);
        nuova.leggiTokenCsrf();
        return nuova;
    }

    public ResultActions get(String percorso, Object... variabili) throws Exception {
        return esegui(MockMvcRequestBuilders.get(percorso, variabili));
    }

    /** POST con il token CSRF della sessione. Il corpo JSON è facoltativo: {@code null} per i comandi senza campi. */
    public ResultActions post(String percorso, String json, Object... variabili) throws Exception {
        MockHttpServletRequestBuilder richiesta =
                MockMvcRequestBuilders.post(percorso, variabili).header(intestazioneCsrf, tokenCsrf);
        if (json != null) {
            richiesta.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return esegui(richiesta);
    }

    private ResultActions esegui(MockHttpServletRequestBuilder richiesta) throws Exception {
        // l'identità deve arrivare soltanto dalla sessione, mai da un contesto di sicurezza di test
        TestSecurityContextHolder.clearContext();
        return mvc.perform(richiesta.session(sessione));
    }

    private void leggiTokenCsrf() throws Exception {
        String corpo = get("/api/auth/csrf")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        intestazioneCsrf = JsonPath.read(corpo, "$.headerName");
        tokenCsrf = JsonPath.read(corpo, "$.token");
    }
}
