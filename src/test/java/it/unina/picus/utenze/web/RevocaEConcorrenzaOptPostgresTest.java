package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.ComandoRegistrazioneOpt;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.EsitoRegistrazioneOpt;
import it.unina.picus.utenze.interno.applicazione.TokenAttivazioneNonValidoException;

@DisplayName("Registrazione, reinvio e revoca concorrente OPT e Operatore su PostgreSQL (#26, #30, #44)")
class RevocaEConcorrenzaOptPostgresTest extends IntegrazionePostgresBase {

    private static final String PASSWORD_TEST = "Password-Test-123";
    private static final Pattern PATTERN_ACCOUNT_ID = Pattern.compile("\"accountId\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private RegistrazioneOptService registrazioneOptService;

    @Autowired
    private AttivazioneUtenzaService attivazioneUtenzaService;

    @Autowired
    private UtenzeApi utenzeApi;

    @MockitoBean
    private InvioEmail invioEmail;

    private MockHttpSession sessioneBanco;

    @BeforeEach
    void preparaSessioneBanco() throws Exception {
        doNothing().when(invioEmail).inviaLinkAttivazione(anyString(), anyString(), anyString());
        sessioneBanco = creaEAccedi("BANCO");
    }

    @Test
    @DisplayName("Rifiuta nome booleano ed email con doppio punto prima di creare account o inviare email")
    void creaOpt_rifiutaTipoNonStringaEDoppioPuntoSenzaCreareAccount() throws Exception {
        int prima = jdbc.queryForObject("select count(*) from utenze_account", Integer.class);

        mvc.perform(post("/api/opt")
                        .session(sessioneBanco)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "codiceStruttura": "OPT-JSON-01",
                                  "nome": true,
                                  "comune": "napoli",
                                  "codiceFiscale": "12345678901",
                                  "email": "valida@picus.test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("nome"));

        mvc.perform(post("/api/opt")
                        .session(sessioneBanco)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "codiceStruttura": "OPT-JSON-02",
                                  "nome": "OPT Test",
                                  "comune": "napoli",
                                  "codiceFiscale": "12345678901",
                                  "email": "doppio..punto@picus.test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("email"));

        int dopo = jdbc.queryForObject("select count(*) from utenze_account", Integer.class);
        assertEquals(prima, dopo);
        verify(invioEmail, never()).inviaLinkAttivazione(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Con errore SMTP restituisce invitoInviato=false e consente il reinvio successivo")
    void creaOpt_conErroreSmtp_segnalaInvitoNonInviatoEConsenteReinvio() throws Exception {
        doThrow(new IllegalStateException("SMTP timeout"))
                .when(invioEmail).inviaLinkAttivazione(anyString(), anyString(), anyString());

        String codice = "OPT-SMTP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String emailOpt = "smtp-" + UUID.randomUUID() + "@picus.test";

        String response = mvc.perform(post("/api/opt")
                        .session(sessioneBanco)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "codiceStruttura": "%s",
                                  "nome": "OPT Errore SMTP",
                                  "comune": "napoli",
                                  "codiceFiscale": "12345678902",
                                  "email": "%s"
                                }
                                """.formatted(codice, emailOpt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitoInviato").value(false))
                .andExpect(jsonPath("$.erroreInvio").value(true))
                .andReturn().getResponse().getContentAsString();

        Matcher matcher = PATTERN_ACCOUNT_ID.matcher(response);
        assertTrue(matcher.find());
        String accountId = matcher.group(1);

        doNothing().when(invioEmail).inviaLinkAttivazione(anyString(), anyString(), anyString());

        mvc.perform(post("/api/opt/" + accountId + "/reinvia-invito")
                        .session(sessioneBanco)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitoInviato").value(true))
                .andExpect(jsonPath("$.erroreInvio").value(false));
    }

    @Test
    @DisplayName("Il reinvio sotto /api/opt rifiuta un account con ruolo OPERATORE in attesa")
    void reinviaInvito_rifiutaAccountNonOpt() throws Exception {
        UUID operatoreId = UUID.randomUUID();
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, null, 'Operatore Test', 'OPERATORE', 'IN_ATTESA_DI_ATTIVAZIONE')
                """, operatoreId, "op-" + operatoreId + "@picus.test");

        mvc.perform(post("/api/opt/" + operatoreId + "/reinvia-invito")
                        .session(sessioneBanco)
                        .with(csrf()))
                .andExpect(status().isConflict());

        verify(invioEmail, never()).inviaLinkAttivazione(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Due reinvii concorrenti lasciano un solo token attivo utilizzabile")
    void reinviiConcorrenti_lascianoUnSoloTokenAttivo() throws Exception {
        String codice = "OPT-REINV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String emailOpt = "reinv-" + UUID.randomUUID() + "@picus.test";
        EsitoRegistrazioneOpt reg = registrazioneOptService.registraOpt(new ComandoRegistrazioneOpt(
                codice, "OPT Reinvio Concorrente", "napoli", "12345678904", emailOpt
        ));
        UUID accountId = reg.accountId();
        String tokenIniziale = reg.datiInvio().tokenInChiaro();

        List<String> tokenInviati = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            tokenInviati.add(inv.getArgument(2, String.class));
            return null;
        }).when(invioEmail).inviaLinkAttivazione(anyString(), anyString(), anyString());

        CountDownLatch pronti = new CountDownLatch(2);
        CountDownLatch via = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> taskReinvio = () -> {
                pronti.countDown();
                via.await(5, TimeUnit.SECONDS);
                return mvc.perform(post("/api/opt/" + accountId + "/reinvia-invito")
                                .session(sessioneBanco)
                                .with(csrf()))
                        .andReturn().getResponse().getStatus();
            };

            Future<Integer> f1 = pool.submit(taskReinvio);
            Future<Integer> f2 = pool.submit(taskReinvio);

            assertTrue(pronti.await(5, TimeUnit.SECONDS));
            via.countDown();

            assertEquals(200, f1.get(15, TimeUnit.SECONDS));
            assertEquals(200, f2.get(15, TimeUnit.SECONDS));
        }

        int tokenAttivi = jdbc.queryForObject(
                "select count(*) from utenze_token_attivazione where account_id = ? and usato_il is null",
                Integer.class, accountId);
        assertEquals(1, tokenAttivi);
        assertFalse(attivazioneUtenzaService.isTokenValido(tokenIniziale));

        String tokenSopravvissuto = tokenInviati.stream()
                .filter(attivazioneUtenzaService::isTokenValido)
                .findFirst()
                .orElseThrow();

        attivazioneUtenzaService.attivaAccount(tokenSopravvissuto, "PasswordSicura123!");
        assertEquals(StatoUtenza.ATTIVO, utenzeApi.cercaAccount(accountId).orElseThrow().stato());
    }

    @Test
    @DisplayName("Revoca manuale OPT e Operatore via HTTP: permessi BANCO, blocco non-BANCO, invalidazione token e conservazione anagrafica")
    void revocaManualeOptEOperatore_viaHttp() throws Exception {
        String codice = "OPT-REV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String emailOpt = "rev-opt-" + UUID.randomUUID() + "@picus.test";
        EsitoRegistrazioneOpt reg = registrazioneOptService.registraOpt(new ComandoRegistrazioneOpt(
                codice, "OPT Da Revocare", "napoli", "12345678905", emailOpt
        ));
        UUID optId = reg.accountId();
        String tokenOpt = reg.datiInvio().tokenInChiaro();

        UUID operatoreId = UUID.randomUUID();
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, 'Operatore Da Revocare', 'OPERATORE', 'ATTIVO')
                """, operatoreId, "rev-op-" + operatoreId + "@picus.test", encoder.encode(PASSWORD_TEST));

        // Un utente OPT non può revocare né OPT né Operatori (403 Forbidden)
        MockHttpSession sessioneOpt = creaEAccedi("OPT");
        mvc.perform(post("/api/opt/" + optId + "/revoca")
                        .session(sessioneOpt)
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/operatori/" + operatoreId + "/revoca")
                        .session(sessioneOpt)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // Il Banco revoca sia la OPT sia l'Operatore (200 OK)
        mvc.perform(post("/api/opt/" + optId + "/revoca")
                        .session(sessioneBanco)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("REVOCATO"));

        mvc.perform(post("/api/operatori/" + operatoreId + "/revoca")
                        .session(sessioneBanco)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("REVOCATO"));

        // Una seconda revoca sullo stesso account restituisce 409 Conflict
        mvc.perform(post("/api/opt/" + optId + "/revoca")
                        .session(sessioneBanco)
                        .with(csrf()))
                .andExpect(status().isConflict());

        // Token pendente invalidato e anagrafiche conservate in stato REVOCATO
        assertFalse(attivazioneUtenzaService.isTokenValido(tokenOpt));
        assertEquals(StatoUtenza.REVOCATO, utenzeApi.cercaAccount(optId).orElseThrow().stato());
        assertEquals(StatoUtenza.REVOCATO, utenzeApi.cercaAccount(operatoreId).orElseThrow().stato());
    }

    @Test
    @DisplayName("Revoca e attivazione simultanee: nessun deadlock e la revoca prevale")
    void revocaEAttivazioneSimultanee_nessunDeadlockERevocaPrevale() throws Exception {
        String codice = "OPT-CONC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String emailOpt = "conc-" + UUID.randomUUID() + "@picus.test";
        EsitoRegistrazioneOpt reg = registrazioneOptService.registraOpt(new ComandoRegistrazioneOpt(
                codice, "OPT Concorrente", "napoli", "12345678903", emailOpt
        ));

        UUID accountId = reg.accountId();
        String token = reg.datiInvio().tokenInChiaro();

        CountDownLatch pronti = new CountDownLatch(2);
        CountDownLatch via = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Throwable> taskRevoca = () -> {
                pronti.countDown();
                via.await(5, TimeUnit.SECONDS);
                try {
                    utenzeApi.revocaUtenzaOpt(accountId);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            };

            Callable<Throwable> taskAttivazione = () -> {
                pronti.countDown();
                via.await(5, TimeUnit.SECONDS);
                try {
                    attivazioneUtenzaService.attivaAccount(token, "PasswordSicura123!");
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            };

            Future<Throwable> fRevoca = pool.submit(taskRevoca);
            Future<Throwable> fAttivazione = pool.submit(taskAttivazione);

            assertTrue(pronti.await(5, TimeUnit.SECONDS));
            via.countDown();

            Throwable errRevoca = fRevoca.get(15, TimeUnit.SECONDS);
            Throwable errAttivazione = fAttivazione.get(15, TimeUnit.SECONDS);

            assertNull(errRevoca);
            if (errAttivazione != null) {
                assertTrue(errAttivazione instanceof TokenAttivazioneNonValidoException,
                        () -> "Eccezione inattesa dall'attivazione concorrente: " + errAttivazione);
            }        }

        assertEquals(StatoUtenza.REVOCATO, utenzeApi.cercaAccount(accountId).orElseThrow().stato());
        assertThrows(TokenAttivazioneNonValidoException.class,
                () -> attivazioneUtenzaService.attivaAccount(token, "AltraPassword123!"));
    }

    private MockHttpSession creaEAccedi(String ruolo) throws Exception {
        UUID id = UUID.randomUUID();
        String email = ruolo.toLowerCase() + "-" + id + "@picus.test";
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, ?, ?, 'ATTIVO')
                """, id, email, encoder.encode(PASSWORD_TEST), "Utente " + ruolo, ruolo);

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD_TEST + "\"}"))
                .andReturn();
        assertEquals(204, login.getResponse().getStatus());
        MockHttpSession sessione = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(sessione);
        return sessione;
    }
}