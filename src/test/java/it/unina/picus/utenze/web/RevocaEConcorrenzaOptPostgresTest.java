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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
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

import javax.sql.DataSource;

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

    /** Sessioni bloccate dal processo indicato, direttamente o attraverso altre sessioni in coda. */
    private static final String SESSIONI_IN_CODA_DIETRO_AL_BLOCCO = """
            with recursive in_coda(pid) as (
                select a.pid from pg_stat_activity a where ? = any(pg_blocking_pids(a.pid))
                union
                select a.pid from pg_stat_activity a join in_coda c on c.pid = any(pg_blocking_pids(a.pid))
            )
            select count(*) from in_coda
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

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

    /**
     * Revoca e attivazione serializzano sul lock dell'account, quindi l'ordine fra le due non è fissato:
     * se arriva prima l'attivazione, l'account si attiva e subito dopo viene revocato; se arriva prima la
     * revoca, l'attivazione è rifiutata. In entrambi i casi la revoca riesce e ha l'ultima parola.
     * I due ordini sono verificati in modo deterministico dai test successivi.
     */
    @Test
    @DisplayName("Revoca e attivazione simultanee: nessun deadlock e la revoca prevale")
    void revocaEAttivazioneSimultanee_nessunDeadlockERevocaPrevale() throws Exception {
        EsitoRegistrazioneOpt reg = registraOptInAttesa("OPT-CONC-");
        UUID accountId = reg.accountId();
        String token = reg.datiInvio().tokenInChiaro();

        CountDownLatch pronti = new CountDownLatch(2);
        CountDownLatch via = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Throwable> taskRevoca = () -> {
                pronti.countDown();
                via.await(5, TimeUnit.SECONDS);
                return esito(() -> utenzeApi.revocaUtenzaOpt(accountId));
            };

            Callable<Throwable> taskAttivazione = () -> {
                pronti.countDown();
                via.await(5, TimeUnit.SECONDS);
                return esito(() -> attivazioneUtenzaService.attivaAccount(token, "PasswordSicura123!"));
            };

            Future<Throwable> fRevoca = pool.submit(taskRevoca);
            Future<Throwable> fAttivazione = pool.submit(taskAttivazione);

            assertTrue(pronti.await(5, TimeUnit.SECONDS));
            via.countDown();

            Throwable errRevoca = fRevoca.get(15, TimeUnit.SECONDS);
            Throwable errAttivazione = fAttivazione.get(15, TimeUnit.SECONDS);

            assertNull(errRevoca, "la revoca riesce in qualunque ordine");
            assertTrue(errAttivazione == null || errAttivazione instanceof TokenAttivazioneNonValidoException,
                    "l'attivazione riesce se arriva prima della revoca, altrimenti è rifiutata: " + errAttivazione);
        }

        assertEquals(StatoUtenza.REVOCATO, utenzeApi.cercaAccount(accountId).orElseThrow().stato());
        assertThrows(TokenAttivazioneNonValidoException.class,
                () -> attivazioneUtenzaService.attivaAccount(token, "AltraPassword123!"));
    }

    @Test
    @DisplayName("Revoca in attesa prima dell'attivazione: la revoca riesce e l'attivazione è rifiutata")
    void revocaPrimaDellAttivazione_attivazioneRifiutata() throws Exception {
        EsitoRegistrazioneOpt reg = registraOptInAttesa("OPT-REV1-");
        UUID accountId = reg.accountId();
        String token = reg.datiInvio().tokenInChiaro();

        List<Throwable> esiti = inOrdineSulLockDellAccount(accountId,
                () -> utenzeApi.revocaUtenzaOpt(accountId),
                () -> attivazioneUtenzaService.attivaAccount(token, "PasswordSicura123!"));

        assertNull(esiti.get(0), "la revoca riesce");
        assertTrue(esiti.get(1) instanceof TokenAttivazioneNonValidoException,
                "l'attivazione dopo la revoca è rifiutata: " + esiti.get(1));
        assertEquals(StatoUtenza.REVOCATO, utenzeApi.cercaAccount(accountId).orElseThrow().stato());
    }

    @Test
    @DisplayName("Attivazione in attesa prima della revoca: l'account si attiva e la revoca lo revoca comunque")
    void attivazionePrimaDellaRevoca_revocaPrevale() throws Exception {
        EsitoRegistrazioneOpt reg = registraOptInAttesa("OPT-ATT1-");
        UUID accountId = reg.accountId();
        String token = reg.datiInvio().tokenInChiaro();

        List<Throwable> esiti = inOrdineSulLockDellAccount(accountId,
                () -> attivazioneUtenzaService.attivaAccount(token, "PasswordSicura123!"),
                () -> utenzeApi.revocaUtenzaOpt(accountId));

        assertNull(esiti.get(0), "l'attivazione arrivata prima riesce");
        assertNull(esiti.get(1), "la revoca di un account appena attivato riesce");
        assertEquals(StatoUtenza.REVOCATO, utenzeApi.cercaAccount(accountId).orElseThrow().stato());
        assertThrows(TokenAttivazioneNonValidoException.class,
                () -> attivazioneUtenzaService.attivaAccount(token, "AltraPassword123!"));
    }

    private EsitoRegistrazioneOpt registraOptInAttesa(String prefissoCodice) {
        String codice = prefissoCodice + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String emailOpt = "conc-" + UUID.randomUUID() + "@picus.test";
        return registrazioneOptService.registraOpt(new ComandoRegistrazioneOpt(
                codice, "OPT Concorrente", "napoli", "12345678903", emailOpt
        ));
    }

    /** Operazione del servizio eseguita da un thread del test. */
    @FunctionalInterface
    private interface Operazione {
        void esegui() throws Exception;
    }

    private static Throwable esito(Operazione operazione) {
        try {
            operazione.esegui();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    /**
     * Esegue le operazioni nell'ordine dato, mettendole in coda sul lock della riga dell'account.
     *
     * Il test tiene {@code FOR UPDATE} sull'account, su cui revoca e attivazione prendono il loro primo
     * lock. Avvia la prima operazione e attende che PostgreSQL la mostri in attesa di quel lock, poi
     * fa lo stesso con la seconda, e infine rilascia: PostgreSQL concede il lock della riga nell'ordine
     * di arrivo, quindi l'ordine non dipende dallo scheduler dei thread.
     *
     * @return esito di ogni operazione, nell'ordine; {@code null} se è riuscita
     */
    private List<Throwable> inOrdineSulLockDellAccount(UUID accountId, Operazione... operazioni)
            throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(operazioni.length)) {
            List<Future<Throwable>> esiti = new CopyOnWriteArrayList<>();
            try (Connection blocco = dataSource.getConnection()) {
                blocco.setAutoCommit(false);
                int processoDelBlocco;
                try (PreparedStatement lock = blocco.prepareStatement(
                        "select pg_backend_pid() from utenze_account where id = ? for update")) {
                    lock.setObject(1, accountId);
                    try (ResultSet riga = lock.executeQuery()) {
                        assertTrue(riga.next(), "account da bloccare non trovato");
                        processoDelBlocco = riga.getInt(1);
                    }
                }
                try {
                    for (int i = 0; i < operazioni.length; i++) {
                        Operazione operazione = operazioni[i];
                        esiti.add(pool.submit(() -> esito(operazione)));
                        attendiInAttesaDiLock(processoDelBlocco, i + 1);
                    }
                } finally {
                    blocco.rollback();
                }
            }
            List<Throwable> risultati = new ArrayList<>();
            for (Future<Throwable> esito : esiti) {
                risultati.add(esito.get(15, TimeUnit.SECONDS));
            }
            return risultati;
        }
    }

    /**
     * Attende che almeno {@code attesi} sessioni siano in coda dietro al lock del test, al più 10 secondi.
     *
     * Conta solo le sessioni bloccate, direttamente o attraverso un'altra sessione in coda, dal processo
     * che tiene il lock: un'attesa estranea nello stesso database non fa partire l'operazione successiva
     * prima che la precedente sia davvero in coda sull'account.
     */
    private void attendiInAttesaDiLock(int processoDelBlocco, int attesi) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            Integer inCoda = jdbc.queryForObject(SESSIONI_IN_CODA_DIETRO_AL_BLOCCO, Integer.class, processoDelBlocco);
            if (inCoda != null && inCoda >= attesi) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("l'operazione " + attesi + " non risulta in coda sul lock dell'account");
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