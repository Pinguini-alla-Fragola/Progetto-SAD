package it.unina.picus.utenze.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import it.unina.picus.utenze.interno.stub.StubUtenzeApi;
import jakarta.servlet.Filter;

/**
 * Walking skeleton: l'intera catena reale (Spring Security, CSRF, sessione, template, controller)
 * con le utenze sostituite da {@link StubUtenzeApi}. Nessun database. Copre i criteri di
 * accettazione dell'accesso con email e password.
 */
@SpringBootTest(properties = "picus.skeleton.password=" + AccessoSkeletonTest.PASSWORD)
@ActiveProfiles("skeleton")
class AccessoSkeletonTest {

    static final String PASSWORD = "password-di-prova";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        OrologioModificabile orologio() {
            return new OrologioModificabile();
        }
    }

    @Autowired WebApplicationContext contesto;
    @Autowired @Qualifier("springSecurityFilterChain") Filter catenaSicurezza;
    @Autowired LimitatoreAccessi limitatore;
    @Autowired OrologioModificabile orologio;
    @Autowired StubUtenzeApi stub;

    private MockMvc mvc;

    private record Sessione(MockHttpSession http, String csrf) {}

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(contesto).addFilters(catenaSicurezza).build();
        limitatore.azzeraTutto();
    }

    private static String csrf(MvcResult risultato) throws Exception {
        Matcher m = CSRF.matcher(risultato.getResponse().getContentAsString());
        assertThat(m.find()).as("campo _csrf nella pagina").isTrue();
        return m.group(1);
    }

    private Sessione apriLogin() throws Exception {
        MvcResult r = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        MockHttpSession http = (MockHttpSession) r.getRequest().getSession(false);
        assertThat(http).isNotNull();
        return new Sessione(http, csrf(r));
    }

    private ResultActions accedi(Sessione s, String email, String password) throws Exception {
        return mvc.perform(post("/login").session(s.http())
                .param("username", email).param("password", password).param("_csrf", s.csrf()));
    }

    private String pagina(Sessione s, String url) throws Exception {
        return mvc.perform(get(url).session(s.http())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void loSkeletonNonUsaIlDatabase() {
        assertThat(contesto.getBeanNamesForType(DataSource.class)).isEmpty();
    }

    // AC1 — credenziali corrette: sessione avviata e area del ruolo
    @ParameterizedTest
    @CsvSource({
        StubUtenzeApi.BANCO + ",/banco",
        StubUtenzeApi.OPT + ",/opt",
        StubUtenzeApi.OPERATORE + ",/operatore"})
    void accessoCorrettoApreAreaDelRuolo(String email, String area) throws Exception {
        Sessione s = apriLogin();

        accedi(s, email, PASSWORD).andExpect(redirectedUrl("/"));
        mvc.perform(get("/").session(s.http())).andExpect(redirectedUrl(area));
        mvc.perform(get(area).session(s.http())).andExpect(status().isOk());
    }

    @Test
    void unRuoloNonEntraNellAreaDiUnAltro() throws Exception {
        Sessione s = apriLogin();
        accedi(s, StubUtenzeApi.OPERATORE, PASSWORD);

        mvc.perform(get("/banco").session(s.http())).andExpect(status().isForbidden());
    }

    @Test
    void areaProtettaSenzaSessioneRimandaAlLogin() throws Exception {
        mvc.perform(get("/banco")).andExpect(redirectedUrl("/login"));
    }

    // Senza autenticazione valida: pagine HTML → 302 al login; API (/api/**, XHR, Accept JSON) → 401
    // senza redirect; con autenticazione ma ruolo sbagliato → 403 (vedi unRuoloNonEntraNellAreaDiUnAltro).
    @Test
    void unaPaginaHtmlProtettaSenzaSessioneRimandaAlLoginAncheDalBrowser() throws Exception {
        mvc.perform(get("/opt").accept(MediaType.TEXT_HTML)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void unaApiProtettaSenzaSessioneRispondeConUnauthorizedSenzaRedirect() throws Exception {
        mvc.perform(get("/api/richieste"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
    }

    // Senza sessione una API riceve 401 anche con POST senza token CSRF (senza sessione il token non esiste).
    @Test
    void unaPostApiSenzaSessioneESenzaTokenCsrfRispondeConUnauthorized() throws Exception {
        mvc.perform(post("/api/richieste"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
    }

    // Con sessione valida, token CSRF mancante o ruolo non abilitato → 403, per API e per pagine HTML.
    @Test
    void unaPostApiConSessioneMaSenzaTokenCsrfRispondeConForbidden() throws Exception {
        Sessione s = apriLogin();
        accedi(s, StubUtenzeApi.OPT, PASSWORD).andExpect(status().is3xxRedirection());

        mvc.perform(post("/api/richieste").session(s.http()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaPostDiUnaPaginaHtmlSenzaSessioneESenzaTokenCsrfRispondeConForbidden() throws Exception {
        mvc.perform(post("/opt")).andExpect(status().isForbidden());
    }

    @Test
    void unApiConRuoloNonAbilitatoRispondeConForbidden() throws Exception {
        Sessione s = apriLogin();
        accedi(s, StubUtenzeApi.OPERATORE, PASSWORD).andExpect(status().is3xxRedirection());

        mvc.perform(get("/banco").session(s.http()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void unAcceptSiInterpretaPerTipiEQualitaNonPerRicercaDiTesto() throws Exception {
        mvc.perform(get("/opt").accept("application/problem+json"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/opt").header("Accept", "text/html, application/json;q=0"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void unaRichiestaXhrOChePretendeJsonSenzaSessioneRiceveUnauthorized() throws Exception {
        mvc.perform(get("/opt").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/opt").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
    }

    // AC2 — credenziali errate o account revocato: stesso messaggio generico
    @ParameterizedTest
    @CsvSource({
        StubUtenzeApi.BANCO + ",password-sbagliata",
        "nessuno@picus.test," + PASSWORD,
        StubUtenzeApi.REVOCATO + "," + PASSWORD})
    void credenzialiErrateORevocatoMessaggioGenerico(String email, String password) throws Exception {
        Sessione s = apriLogin();

        accedi(s, email, password).andExpect(redirectedUrl("/login?error"));

        String html = pagina(s, "/login?error");
        assertThat(html).contains("Credenziali non valide o accesso non consentito.").doesNotContain(email);
    }

    // AC3 — cinque fallimenti, sesto entro 60 s rifiutato anche con la password giusta
    @Test
    void dopoCinqueFallimentiIlTentativoSuccessivoERifiutato() throws Exception {
        Sessione s = apriLogin();
        for (int i = 0; i < 5; i++) {
            accedi(s, StubUtenzeApi.OPT, "errata").andExpect(redirectedUrl("/login?error"));
        }

        accedi(s, StubUtenzeApi.OPT, PASSWORD).andExpect(redirectedUrl("/login?attesa"));
        mvc.perform(get("/opt").session(s.http())).andExpect(redirectedUrl("/login"));
        assertThat(pagina(s, "/login?attesa")).contains("Troppi tentativi").doesNotContain(StubUtenzeApi.OPT);

        orologio.avanza(Duration.ofSeconds(61));
        accedi(s, StubUtenzeApi.OPT, PASSWORD).andExpect(status().is3xxRedirection());
        mvc.perform(get("/opt").session(s.http())).andExpect(status().isOk());
    }

    @Test
    void lAttesaValeAncheSenzaAccountCosiNonSiScoprononoGliAccount() throws Exception {
        Sessione s = apriLogin();
        for (int i = 0; i < 5; i++) {
            accedi(s, "nessuno@picus.test", "x").andExpect(redirectedUrl("/login?error"));
        }

        accedi(s, "nessuno@picus.test", "x").andExpect(redirectedUrl("/login?attesa"));
    }

    // AC4 — logout: il server chiude la sessione e quella sessione non è più utilizzabile
    @Test
    void dopoIlLogoutLaSessioneNonDaPiuAccesso() throws Exception {
        Sessione s = apriLogin();
        accedi(s, StubUtenzeApi.BANCO, PASSWORD).andExpect(status().is3xxRedirection());
        String token = csrf(mvc.perform(get("/banco").session(s.http())).andExpect(status().isOk()).andReturn());

        mvc.perform(post("/logout").session(s.http()).param("_csrf", token))
                .andExpect(redirectedUrl("/login?logout"));

        assertThat(s.http().isInvalid()).isTrue();
        mvc.perform(get("/banco").session(s.http())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void ilLogoutSenzaTokenCsrfEVietato() throws Exception {
        Sessione s = apriLogin();
        accedi(s, StubUtenzeApi.BANCO, PASSWORD);

        mvc.perform(post("/logout").session(s.http())).andExpect(status().isForbidden());
    }

    @Test
    void laRevocaChiudeLaSessioneAllaRichiestaSuccessiva() throws Exception {
        Sessione s = apriLogin();
        accedi(s, StubUtenzeApi.REVOCABILE, PASSWORD).andExpect(status().is3xxRedirection());
        mvc.perform(get("/operatore").session(s.http())).andExpect(status().isOk());

        Sessione api = apriLogin();
        accedi(api, StubUtenzeApi.REVOCABILE, PASSWORD).andExpect(status().is3xxRedirection());
        Sessione altraArea = apriLogin();
        accedi(altraArea, StubUtenzeApi.REVOCABILE, PASSWORD).andExpect(status().is3xxRedirection());

        stub.revoca(StubUtenzeApi.REVOCABILE);

        // pagina HTML: la sessione si chiude e si torna al login
        mvc.perform(get("/operatore").session(s.http())).andExpect(redirectedUrl("/login?error"));
        // API: 401 senza redirect, nessun dato restituito
        mvc.perform(get("/operatore").session(api.http()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        assertThat(api.http().isInvalid()).isTrue();
        // la prima richiesta dopo la revoca è rivolta a un'altra area (/banco): 401 e non 403, sessione chiusa
        mvc.perform(get("/banco").session(altraArea.http()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        assertThat(altraArea.http().isInvalid()).isTrue();
    }
}
