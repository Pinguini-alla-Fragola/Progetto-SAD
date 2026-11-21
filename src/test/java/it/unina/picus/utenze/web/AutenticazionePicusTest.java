package it.unina.picus.utenze.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.ComandoRegistrazioneOpt;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.EsitoRegistrazioneOpt;
import it.unina.picus.utenze.interno.applicazione.TokenAttivazioneNonValidoException;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;

@SpringBootTest
@ActiveProfiles("test")
class RevocaEConcorrenzaOptTest {

    private static final Pattern PATTERN_ACCOUNT_ID = Pattern.compile("\"accountId\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private RegistrazioneOptService registrazioneOptService;

    @Autowired
    private AttivazioneUtenzaService attivazioneUtenzaService;

    @Autowired
    private UtenzeApi utenzeApi;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoBean
    private InvioEmail invioEmail;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void creaOpt_rifiutaTipoNonStringaEDoppioPuntoSenzaCreareAccount() throws Exception {
        long prima = accountRepository.count();

        // 1. nome booleano: nome: true
        mockMvc.perform(post("/api/opt")
                        .with(user("banco@picus.test").roles("BANCO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "codiceStruttura": "OPT-JSON-01",
                                  "nome": true,
                                  "comune": "063049",
                                  "codiceFiscale": "12345678901",
                                  "email": "valida@picus.test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("nome"));

        // 2. email con doppio punto non valida per Jakarta Mail strict
        mockMvc.perform(post("/api/opt")
                        .with(user("banco@picus.test").roles("BANCO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "codiceStruttura": "OPT-JSON-02",
                                  "nome": "OPT Test",
                                  "comune": "063049",
                                  "codiceFiscale": "12345678901",
                                  "email": "doppio..punto@picus.test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("email"));

        assertThat(accountRepository.count()).isEqualTo(prima);
        verify(invioEmail, never()).inviaLinkAttivazione(anyString(), anyString(), anyString());
    }

    @Test
    void creaOpt_conErroreSmtp_segnalaInvitoNonInviatoEConsenteReinvio() throws Exception {
        doThrow(new IllegalStateException("SMTP timeout"))
                .when(invioEmail).inviaLinkAttivazione(anyString(), anyString(), anyString());

        String response = mockMvc.perform(post("/api/opt")
                        .with(user("banco@picus.test").roles("BANCO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "codiceStruttura": "OPT-SMTP-ERR",
                                  "nome": "OPT Errore SMTP",
                                  "comune": "063049",
                                  "codiceFiscale": "12345678902",
                                  "email": "smtp.fail@picus.test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitoInviato").value(false))
                .andExpect(jsonPath("$.erroreInvio").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("invio dell'email è fallito");

        Matcher matcher = PATTERN_ACCOUNT_ID.matcher(response);
        assertThat(matcher.find()).isTrue();
        String accountId = matcher.group(1);

        // Ripristina il trasporto SMTP e verifica il reinvio dell'invito
        doNothing().when(invioEmail).inviaLinkAttivazione(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/opt/" + accountId + "/reinvia-invito")
                        .with(user("banco@picus.test").roles("BANCO"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitoInviato").value(true))
                .andExpect(jsonPath("$.erroreInvio").value(false));
    }

    @Test
    void revocaEAttivazioneSimultanee_nessunDeadlockERevocaPrevale() throws Exception {
        EsitoRegistrazioneOpt reg = registrazioneOptService.registraOpt(new ComandoRegistrazioneOpt(
                "OPT-CONC-01",
                "OPT Concorrente",
                "063049",
                "12345678903",
                "conc@picus.test"
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

            assertThat(pronti.await(5, TimeUnit.SECONDS)).isTrue();
            via.countDown();

            Throwable errRevoca = fRevoca.get(10, TimeUnit.SECONDS);
            Throwable errAttivazione = fAttivazione.get(10, TimeUnit.SECONDS);

            // La revoca deve riuscire senza deadlock e il token non può più riattivare l'account
            assertThat(errRevoca).isNull();
            assertThat(errAttivazione).isInstanceOf(TokenAttivazioneNonValidoException.class);
        }

        assertThat(utenzeApi.cercaAccount(accountId))
                .isPresent()
                .get()
                .extracting(UtenzeApi.AccountSintetico::stato)
                .isEqualTo(StatoUtenza.REVOCATO);

        assertThatThrownBy(() -> attivazioneUtenzaService.attivaAccount(token, "AltraPassword123!"))
                .isInstanceOf(TokenAttivazioneNonValidoException.class);
    }
}