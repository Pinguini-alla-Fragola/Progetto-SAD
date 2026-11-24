package it.unina.picus.comunicazioni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.unina.picus.shared.AccessoDiProva;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.SessioneDiProva;

/**
 * Test di integrazione della #145 sul codice della #143: comando {@code POST
 * /api/comunicazioni/{comunicazioneId}/ricezione} con login reale e PostgreSQL.
 *
 * I test riportano nel nome il criterio della user story #56 che verificano. L'apertura usa il
 * dettaglio della #197 e il Banco consulta i riscontri tramite la rotta dedicata.
 *
 * Lo scenario nasce dal flusso dell'applicazione: il Banco invia la comunicazione con l'endpoint della #140 e
 * i destinatari confermano nella propria sessione. Account, profili e l'allegato del caso di
 * apertura sono dati di prova preparati direttamente nel database.
 */
@IntegrazionePostgresql
@Tag("issue-145")
@DisplayName("#145 Conferma di ricezione: integrazione su HTTP e PostgreSQL")
class ConfermaRicezioneIntegrazioneTest {

    private static final String CONFERMA = "/api/comunicazioni/{id}/ricezione";
    private static final String RISCONTRI = "/api/comunicazioni/{id}/riscontri";

    /** Destinatari possibili che provano a confermare una comunicazione indirizzata ad altri. */
    enum Estraneo {
        OPT_FUORI_DAL_GRUPPO,
        OPERATORE
    }

    /** Tipologie di operatore destinatario. */
    enum Tipologia {
        INTERNO_BANCO,
        ESTERNO_PAGATO_BANCO
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private PlatformTransactionManager transazioni;

    private DatiSintetici dati;
    private AccessoDiProva accesso;
    private final Map<UUID, SessioneDiProva> sessioni = new HashMap<>();

    private UUID banco;
    private UUID optA;
    private UUID optB;
    private UUID optEstranea;
    private UUID operatoreA;
    private UUID operatoreB;

    @BeforeEach
    void prepara() {
        svuotaDati();
        dati = new DatiSintetici(jdbc);
        accesso = new AccessoDiProva(mvc, jdbc, encoder);
        sessioni.clear();

        banco = dati.banco("Banco di prova");
        optA = dati.opt("OPT A", "napoli");
        optB = dati.opt("OPT B", "napoli");
        optEstranea = dati.opt("OPT estranea", "napoli");
        operatoreA = dati.operatore("Operatore A", "INTERNO_BANCO", "napoli", false);
        operatoreB = dati.operatore("Operatore B", "ESTERNO_PAGATO_BANCO", "napoli", false);
    }

    @AfterEach
    void svuotaDati() {
        new TransactionTemplate(transazioni).executeWithoutResult(stato -> new DatiSintetici(jdbc).svuota());
    }

    // ------------------------------------------------------------------ AC1 e AC5

    @Test
    @DisplayName("AC1/AC5 - la OPT conferma: un solo riscontro con data e ora, intestato all'account dell'organizzazione")
    void ac1_optConfermaPerLOrganizzazione() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);
        Instant prima = Instant.now();

        Instant restituito = conferma(optA, id);

        Instant dopo = Instant.now();
        assertThat(restituito).isEqualTo(confermataIl(id, optA));
        assertThat(restituito).isBetween(prima.minusMillis(1), dopo.plusMillis(1));
        assertThat(righe(id)).isEqualTo(2);
        assertThat(confermati(id)).containsOnlyKeys(optA);
    }

    @ParameterizedTest(name = "AC1 - l''operatore {0} conferma per il proprio account personale")
    @EnumSource(Tipologia.class)
    void ac1_operatoreConfermaPerSe(Tipologia tipologia) throws Exception {
        UUID operatore = tipologia == Tipologia.INTERNO_BANCO ? operatoreA : operatoreB;
        UUID collega = tipologia == Tipologia.INTERNO_BANCO ? operatoreB : operatoreA;
        UUID id = inviaAGruppo("GRUPPO_OPERATORI", operatore, collega);

        Instant restituito = conferma(operatore, id);

        assertThat(confermati(id)).containsExactly(Map.entry(operatore, restituito));
        assertThat(confermataIl(id, collega)).isNull();
    }

    @Test
    @DisplayName("AC2 - il Banco vede la conferma e il destinatario ancora in attesa")
    void ac2_confermatoEInAttesaNellaStessaComunicazione() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

        Instant primaConferma = conferma(optA, id);
        come(optB).get("/api/comunicazioni/{id}", id).andExpect(status().isOk());
        assertThat(conferma(optA, id)).isEqualTo(primaConferma);

        come(banco).get(RISCONTRI, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].destinatarioId").value(optA.toString()))
                .andExpect(jsonPath("$[0].nomeVisualizzato").value("OPT A"))
                .andExpect(jsonPath("$[0].confermataIl").value(primaConferma.toString()))
                .andExpect(jsonPath("$[1].destinatarioId").value(optB.toString()))
                .andExpect(jsonPath("$[1].nomeVisualizzato").value("OPT B"))
                .andExpect(jsonPath("$[1].confermataIl").isEmpty());
        daConfermare(optA).andExpect(jsonPath("$.daConfermare").value(0));
        daConfermare(optB).andExpect(jsonPath("$.daConfermare").value(1));
    }

    @Test
    void riscontriRiservatiAlBanco() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);
        UUID assente = UUID.randomUUID();

        mvc.perform(get(RISCONTRI, id)).andExpect(status().isUnauthorized());
        come(optA).get(RISCONTRI, id).andExpect(status().isForbidden());
        come(optEstranea).get(RISCONTRI, id).andExpect(status().isForbidden());
        come(operatoreA).get(RISCONTRI, assente).andExpect(status().isForbidden());
        come(banco).get(RISCONTRI, assente).andExpect(status().isNotFound());
    }

    @Test
    void preservaINomiDeiDestinatariRevocati() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPERATORI", operatoreA, operatoreB);
        Instant primaConferma = conferma(operatoreB, id);
        jdbc.update("update utenze_account set stato = 'REVOCATO' where id = ?", operatoreB);

        come(banco).get(RISCONTRI, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].destinatarioId").value(operatoreB.toString()))
                .andExpect(jsonPath("$[1].nomeVisualizzato").value("Operatore B"))
                .andExpect(jsonPath("$[1].confermataIl").value(primaConferma.toString()));
    }

    @Test
    @DisplayName("AC1 - un invio a tutte le OPT: ogni OPT conferma solo il proprio riscontro")
    void ac1_invioATutteLeOpt() throws Exception {
        UUID id = invia("TUTTE_LE_OPT", List.of());

        Instant diB = conferma(optB, id);
        Instant diA = conferma(optA, id);

        assertThat(confermati(id)).containsOnly(Map.entry(optA, diA), Map.entry(optB, diB));
        assertThat(righe(id)).isEqualTo(3);
    }

    @Test
    @DisplayName("AC3 - aprire il dettaglio non registra alcun riscontro")
    void ac3_aperturaSenzaConferma() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

        UUID allegato = UUID.randomUUID();
        byte[] contenuto = "Nota sintetica".getBytes(StandardCharsets.UTF_8);
        jdbc.update("""
                insert into comunicazioni_allegati
                  (id, comunicazione_id, nome_file, tipo_mime, dimensione_byte, contenuto)
                values (?, ?, 'nota.txt', 'text/plain', ?, ?)
                """, allegato, id, contenuto.length, contenuto);

        come(optA).get("/api/comunicazioni/{id}", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.allegati[0]").value(allegato.toString()))
                .andExpect(jsonPath("$.confermataIl").isEmpty());
        daConfermare(optA).andExpect(jsonPath("$.daConfermare").value(1));

        assertThat(confermati(id)).isEmpty();
    }

    // ------------------------------------------------------------------ AC4

    @Test
    @DisplayName("AC4 - premere di nuovo il pulsante lascia una sola conferma con la data e ora originarie")
    void ac4_ripetizioneDelComando() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);
        Instant prima = conferma(optA, id);

        Instant seconda = conferma(optA, id);
        Instant terza = conferma(optA, id);

        assertThat(seconda).isEqualTo(prima);
        assertThat(terza).isEqualTo(prima);
        assertThat(confermataIl(id, optA)).isEqualTo(prima);
        assertThat(righe(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC4 - il ritento da una nuova sessione dello stesso account restituisce lo stesso istante")
    void ac4_ritentoDaUnAltraSessione() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPERATORI", operatoreA);
        Instant prima = conferma(operatoreA, id);

        String corpo = accesso.accedi(operatoreA).post(CONFERMA, null, id)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(istante(corpo)).isEqualTo(prima);
    }

    // ------------------------------------------------------------------ AC6

    @ParameterizedTest(name = "AC6 - {0} non destinatario: 403 senza modifiche")
    @EnumSource(Estraneo.class)
    void ac6_estraneoNonConferma(Estraneo estraneo) throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

        come(account(estraneo)).post(CONFERMA, null, id)
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        assertThat(confermati(id)).isEmpty();
        assertThat(righe(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("AC6 - un estraneo che indica nel corpo l'account di un destinatario riceve 403 e non conferma per lui")
    void ac6_estraneoNonConfermaPerAltri() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);

        come(optEstranea).post(CONFERMA, "{\"destinatarioId\": \"" + optA + "\"}", id)
                .andExpect(status().isForbidden());

        assertThat(confermati(id)).isEmpty();
    }

    @Test
    @DisplayName("AC6 - un destinatario che indica nel corpo un altro account conferma solo per sé")
    void ac6_destinatarioConfermaSoloPerSe() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

        come(optA).post(CONFERMA, "{\"destinatarioId\": \"" + optB + "\"}", id)
                .andExpect(status().isOk());

        assertThat(confermati(id)).containsOnlyKeys(optA);
    }

    @Test
    @DisplayName("AC6 - un destinatario di un'altra comunicazione non conferma questa")
    void ac6_destinatarioDiUnAltraComunicazione() throws Exception {
        UUID perA = inviaAGruppo("GRUPPO_OPT", optA);
        UUID perB = inviaAGruppo("GRUPPO_OPT", optB);

        come(optB).post(CONFERMA, null, perA).andExpect(status().isForbidden());

        assertThat(confermati(perA)).isEmpty();
        assertThat(confermati(perB)).isEmpty();
    }

    @Test
    @DisplayName("AC6 - il Banco che ha inviato la comunicazione non conferma al posto dei destinatari: 403 senza modifiche")
    void ac6_bancoNonConfermaSullaRotta() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

        come(banco).post(CONFERMA, null, id)
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        assertThat(confermati(id)).isEmpty();
        assertThat(righe(id)).isEqualTo(2);
    }

    // ------------------------------------------------------------------ casi limite della #143

    @Test
    @DisplayName("AC6 - comunicazione altrui o inesistente: stesso diniego senza riscontri")
    void comunicazioneNonAccessibile() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optB);
        var estranea = come(optA);

        estranea.post(CONFERMA, null, id)
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
        estranea.post(CONFERMA, null, UUID.randomUUID())
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        assertThat(confermati(id)).isEmpty();
        assertThat(righe(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("#143 - senza sessione: 401 e nessun riscontro")
    void senzaSessione() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);

        SessioneDiProva.anonima(mvc).post(CONFERMA, null, id).andExpect(status().isUnauthorized());

        assertThat(confermati(id)).isEmpty();
    }

    @Test
    @DisplayName("#143 - identificativo che non è un UUID: 400 e nessun riscontro")
    void identificativoNonUuid() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);

        come(optA).post(CONFERMA, null, "non-un-uuid").andExpect(status().isBadRequest());

        assertThat(confermati(id)).isEmpty();
    }

    @Test
    @DisplayName("#143 - destinatario autenticato senza token CSRF: 403 e nessun riscontro; con il token conferma")
    void senzaTokenCsrf() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);
        MockHttpSession sessione = accediSenzaClientDiProva(optA);

        eseguiNellaSessione(sessione, post(CONFERMA, id))
                .andExpect(status().isForbidden());
        assertThat(confermati(id)).isEmpty();

        // controllo: la stessa sessione con il token conferma, quindi il 403 dipendeva solo dal token mancante
        String csrf = leggiCsrf(sessione);
        eseguiNellaSessione(sessione, post(CONFERMA, id)
                .header(JsonPath.read(csrf, "$.headerName"), (String) JsonPath.read(csrf, "$.token")))
                .andExpect(status().isOk());
        assertThat(confermati(id)).containsOnlyKeys(optA);
    }

    @Test
    @DisplayName("#143 - account revocato dopo l'invio: la sessione non conferma più")
    void accountRevocato() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);
        SessioneDiProva sessione = come(optA);
        dati.impostaStatoAccount(optA, "REVOCATO");

        int esito = sessione.post(CONFERMA, null, id).andReturn().getResponse().getStatus();

        assertThat(esito).isIn(401, 403);
        assertThat(confermati(id)).isEmpty();
    }

    // ------------------------------------------------------------------ supporto

    private UUID inviaAGruppo(String tipo, UUID... gruppo) throws Exception {
        return invia(tipo, List.of(gruppo));
    }

    /** Invio reale del Banco con l'endpoint della #140. */
    private UUID invia(String tipo, List<UUID> gruppo) throws Exception {
        String elenco = gruppo.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(", "));
        String json = """
                {"titolo": "Avviso di prova", "contenuto": "Testo sintetico", "destinatariTipo": "%s", "gruppo": [%s]}
                """.formatted(tipo, elenco);
        String corpo = come(banco).post("/api/comunicazioni", json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    /** Conferma con esito {@code 200} e restituisce l'istante della risposta. */
    private Instant conferma(UUID destinatario, UUID comunicazione) throws Exception {
        String corpo = come(destinatario).post(CONFERMA, null, comunicazione)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return istante(corpo);
    }

    private ResultActions daConfermare(UUID destinatario) throws Exception {
        return come(destinatario).get("/api/comunicazioni/da-confermare").andExpect(status().isOk());
    }

    /** Legge l'istante sia come stringa ISO-8601 sia come secondi decimali, secondo la configurazione di Jackson. */
    private static Instant istante(String corpo) {
        String valore = corpo.trim();
        if (valore.startsWith("\"")) {
            return Instant.parse(valore.substring(1, valore.length() - 1));
        }
        BigDecimal secondi = new BigDecimal(valore);
        return Instant.ofEpochSecond(secondi.longValue(),
                secondi.remainder(BigDecimal.ONE).movePointRight(9).longValue());
    }

    private SessioneDiProva come(UUID account) throws Exception {
        SessioneDiProva sessione = sessioni.get(account);
        if (sessione == null) {
            sessione = accesso.accedi(account);
            sessioni.put(account, sessione);
        }
        return sessione;
    }

    private UUID account(Estraneo estraneo) {
        return switch (estraneo) {
            case OPT_FUORI_DAL_GRUPPO -> optEstranea;
            case OPERATORE -> operatoreA;
        };
    }

    /**
     * Login reale gestito qui invece che da {@link SessioneDiProva}, che aggiunge sempre il token CSRF: serve
     * per inviare un comando autenticato senza token. La password è generata a ogni esecuzione.
     */
    private MockHttpSession accediSenzaClientDiProva(UUID account) throws Exception {
        String password = "prova-" + UUID.randomUUID();
        jdbc.update("update utenze_account set password_hash = ? where id = ?", encoder.encode(password), account);
        String email = jdbc.queryForObject("select email from utenze_account where id = ?", String.class, account);
        MockHttpSession sessione = new MockHttpSession();
        String csrf = leggiCsrf(sessione);
        eseguiNellaSessione(sessione, post("/api/auth/login")
                .header(JsonPath.read(csrf, "$.headerName"), (String) JsonPath.read(csrf, "$.token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isNoContent());
        return sessione;
    }

    private String leggiCsrf(MockHttpSession sessione) throws Exception {
        return eseguiNellaSessione(sessione, get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Come in {@link SessioneDiProva}: l'identità arriva soltanto dalla sessione. */
    private ResultActions eseguiNellaSessione(MockHttpSession sessione, MockHttpServletRequestBuilder richiesta)
            throws Exception {
        TestSecurityContextHolder.clearContext();
        return mvc.perform(richiesta.session(sessione));
    }

    private Instant confermataIl(UUID comunicazione, UUID destinatario) {
        List<OffsetDateTime> valori = jdbc.queryForList("""
                select confermata_il from comunicazioni_destinatari
                where comunicazione_id = ? and destinatario_id = ?
                """, OffsetDateTime.class, comunicazione, destinatario);
        return valori.isEmpty() || valori.getFirst() == null ? null : valori.getFirst().toInstant();
    }

    /** Destinatari della comunicazione che hanno un riscontro, con il relativo istante. */
    private Map<UUID, Instant> confermati(UUID comunicazione) {
        Map<UUID, Instant> esito = new HashMap<>();
        jdbc.query("""
                select destinatario_id, confermata_il from comunicazioni_destinatari
                where comunicazione_id = ? and confermata_il is not null
                """, rs -> {
            esito.put(rs.getObject("destinatario_id", UUID.class),
                    rs.getObject("confermata_il", OffsetDateTime.class).toInstant());
        }, comunicazione);
        return esito;
    }

    private int righe(UUID comunicazione) {
        return jdbc.queryForObject("select count(*) from comunicazioni_destinatari where comunicazione_id = ?",
                Integer.class, comunicazione);
    }
}
