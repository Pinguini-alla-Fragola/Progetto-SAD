package it.unina.picus.utenze.web;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${PICUS_TEST_IMPORTAZIONE_DB:jdbc:h2:mem:importazione-opt}",
        "server.servlet.session.cookie.secure=false"
})
class ImportazioneOptTomcatTest {

    private static final String INTESTAZIONE = "codice_struttura,nome_opt,comune,codice_fiscale,email\n";
    private static final String BOUNDARY = "picus-csv-test";
    private static final String PASSWORD = "Password-di-prova-1";

    @LocalServerPort
    private int porta;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JsonMapper json;
    @Autowired
    private AttivazioneUtenzaService attivazione;
    @MockitoBean
    private InvioEmail posta;

    private HttpClient client;

    @BeforeEach
    void accediComeBanco() throws Exception {
        String email = UUID.randomUUID() + "@picus.test";
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, 'Banco sintetico', 'BANCO', 'ATTIVO')
                """, UUID.randomUUID(), email, passwordEncoder.encode(PASSWORD));
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(new CookieManager()).build();
        JsonNode csrf = tokenCsrf();
        var richiesta = richiesta("/api/auth/login")
                .header("Content-Type", "application/json")
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(Map.of("email", email, "password", PASSWORD))));
        assertThat(invia(richiesta).statusCode()).isEqualTo(204);
    }

    @Test
    void importa450OptSenzaAttendereSmtpENonPerdeInviti() throws Exception {
        var primoInvio = new CountDownLatch(1);
        var sbloccaSmtp = new CountDownLatch(1);
        var completati = new CountDownLatch(450);
        Set<String> destinatari = ConcurrentHashMap.newKeySet();
        doAnswer(chiamata -> {
            String destinatario = chiamata.getArgument(0);
            if (!destinatario.startsWith("csv-")) {
                return null;
            }
            primoInvio.countDown();
            if (sbloccaSmtp.await(30, SECONDS) && attivazione.isTokenValido(chiamata.getArgument(2))) {
                destinatari.add(destinatario);
                completati.countDown();
            }
            return null;
        }).when(posta).inviaLinkAttivazione(anyString(), anyString(), anyString());

        var csv = new StringBuilder(INTESTAZIONE);
        for (int i = 1; i <= 450; i++) {
            csv.append(String.format(Locale.ROOT, "CSV-%d,OPT sintetica,napoli,%011d,csv-%d@picus.test%n", i, i, i));
        }
        try {
            var risposta = carica(csv.toString(), tokenCsrf());
            assertThat(risposta.statusCode()).isEqualTo(200);
            assertThat(primoInvio.await(5, SECONDS)).isTrue();
            var righe = json.readTree(risposta.body()).get("righe");
            assertThat(righe).hasSize(450);
            for (JsonNode riga : righe) {
                assertThat(riga.get("esito").asText()).isEqualTo("CREATA");
                assertThat(riga.get("motivi").get(0).asText()).startsWith("Invito di attivazione accodato.");
            }
            assertThat(jdbc.queryForObject("""
                    select count(*) from utenze_inviti_importazione_opt i
                      join utenze_account a on a.id = i.account_id
                     where a.email like 'csv-%'
                    """, Integer.class)).isEqualTo(450);
            assertThat(destinatari).isEmpty();
        } finally {
            sbloccaSmtp.countDown();
        }
        assertThat(completati.await(30, SECONDS)).isTrue();
        assertThat(destinatari).hasSize(450);

        var secondaImportazione = carica(csv.toString(), tokenCsrf());
        assertThat(secondaImportazione.statusCode()).isEqualTo(200);
        assertThat(json.readTree(secondaImportazione.body()).get("righe")).hasSize(450)
                .allSatisfy(riga -> assertThat(riga.get("esito").asText()).isEqualTo("INVARIATA"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void respingeCsvTroppoGrandeConUnMessaggioLeggibile(int mebibyte) throws Exception {
        var risposta = carica(INTESTAZIONE + " ".repeat(mebibyte * 1024 * 1024), tokenCsrf());
        assertThat(risposta.statusCode()).isEqualTo(413);
        assertThat(risposta.headers().firstValue("Content-Type")).hasValueSatisfying(
                tipo -> assertThat(tipo).startsWith("application/problem+json"));
        assertThat(json.readTree(risposta.body()).get("detail").asText()).contains("CSV", "1 MB");
    }

    @Test
    void riprovaUnInvitoGiaAccettatoSenzaInvalidareIlPrimoLink() throws Exception {
        String email = "interrotto@picus.test";
        var primaConsegna = new CountDownLatch(1);
        var secondaConsegna = new CountDownLatch(1);
        var primoToken = new AtomicReference<String>();
        var secondoToken = new AtomicReference<String>();
        doAnswer(chiamata -> {
            String token = chiamata.getArgument(2);
            if (primoToken.compareAndSet(null, token)) {
                primaConsegna.countDown();
                throw new IllegalStateException("SMTP ha accettato il messaggio ma la conferma è andata persa");
            }
            secondoToken.set(token);
            secondaConsegna.countDown();
            return null;
        }).when(posta).inviaLinkAttivazione(eq(email), anyString(), anyString());

        var risposta = carica(INTESTAZIONE + "INTERROTTA,OPT sintetica,napoli,12345678902," + email + "\n", tokenCsrf());
        assertThat(risposta.statusCode()).isEqualTo(200);
        assertThat(primaConsegna.await(5, SECONDS)).isTrue();
        assertThat(attivazione.isTokenValido(primoToken.get())).isTrue();
        rendiDisponibile(email);
        assertThat(secondaConsegna.await(5, SECONDS)).isTrue();
        assertThat(attivazione.isTokenValido(secondoToken.get())).isTrue();
        assertThat(attivazione.isTokenValido(primoToken.get())).isTrue();

        attivazione.attivaAccount(primoToken.get(), PASSWORD);
        assertThat(attivazione.isTokenValido(primoToken.get())).isFalse();
        assertThat(attivazione.isTokenValido(secondoToken.get())).isFalse();
    }

    @Test
    void interrompeIRitentativiDopoCinqueErroriSmtp() throws Exception {
        String email = "rifiutato@picus.test";
        var tentativi = new LinkedBlockingQueue<Boolean>();
        doAnswer(chiamata -> {
            tentativi.add(true);
            throw new IllegalStateException("SMTP rifiuta il destinatario");
        }).when(posta).inviaLinkAttivazione(eq(email), anyString(), anyString());

        var risposta = carica(INTESTAZIONE + "RIFIUTATA,OPT sintetica,napoli,12345678903," + email + "\n", tokenCsrf());
        assertThat(risposta.statusCode()).isEqualTo(200);

        for (int numero = 1; numero <= 5; numero++) {
            assertThat(tentativi.poll(5, SECONDS)).isTrue();
            if (numero < 5) {
                rendiDisponibile(email);
            }
        }
        assertThat(tentativi.poll(3, SECONDS)).isNull();
        assertThat(jdbc.queryForObject("""
                select tentativi from utenze_inviti_importazione_opt
                 where disponibile_dal is null and account_id = (select id from utenze_account where email = ?)
                """, Integer.class, email)).isEqualTo(5);
    }

    private void rendiDisponibile(String email) {
        assertThat(jdbc.update("""
                update utenze_inviti_importazione_opt set disponibile_dal = current_timestamp
                 where account_id = (select id from utenze_account where email = ?)
                """, email)).isEqualTo(1);
    }

    @Test
    void richiedeCsrfAnchePerUnMultipart() throws Exception {
        var csv = INTESTAZIONE + "CSRF,OPT sintetica,napoli,12345678901,csrf@picus.test\n";
        assertThat(carica(csv, null).statusCode()).isEqualTo(403);
        var risposta = carica(csv, tokenCsrf());
        assertThat(risposta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(risposta.body()).get("righe").get(0).get("esito").asText()).isEqualTo("CREATA");
    }

    private JsonNode tokenCsrf() throws Exception {
        var risposta = invia(richiesta("/api/auth/csrf").GET());
        assertThat(risposta.statusCode()).isEqualTo(200);
        return json.readTree(risposta.body());
    }

    private HttpResponse<String> carica(String csv, JsonNode csrf) throws Exception {
        String corpo = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"opt.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n" + csv + "\r\n--" + BOUNDARY + "--\r\n";
        var richiesta = richiesta("/api/opt/importazioni")
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofString(corpo));
        if (csrf != null) {
            richiesta.header(csrf.get("headerName").asText(), csrf.get("token").asText());
        }
        return invia(richiesta);
    }

    private HttpRequest.Builder richiesta(String percorso) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + percorso))
                .timeout(Duration.ofSeconds(20));
    }

    private HttpResponse<String> invia(HttpRequest.Builder richiesta) throws Exception {
        return client.send(richiesta.build(), HttpResponse.BodyHandlers.ofString());
    }
}
