package it.unina.picus.comunicazioni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import java.math.BigDecimal;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.unina.picus.shared.AccessoDiProva;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.SessioneDiProva;

/**
 * Test di integrazione della #145 sul codice della #143: comando {@code POST
 * /api/comunicazioni/{comunicazioneId}/ricezione} con login reale e PostgreSQL.
 *
 * I test riportano nel nome il criterio della user story #56 che verificano (AC1, AC4, AC5, AC6). La
 * consultazione dei riscontri da parte del Banco (AC2) e l'apertura della comunicazione (AC3) arriveranno con
 * la #144: qui la persistenza si legge dalla riga di {@code comunicazioni_destinatari}.
 *
 * Lo scenario nasce dal flusso dell'applicazione: il Banco invia la comunicazione con l'endpoint della #140 e
 * i destinatari confermano nella propria sessione. Nel database si inseriscono con {@link DatiSintetici}
 * soltanto gli account e i profili, che l'applicazione non permette ancora di creare.
 */
@IntegrazionePostgresql
@Tag("issue-145")
@DisplayName("#145 Conferma di ricezione: integrazione su HTTP e PostgreSQL")
class ConfermaRicezioneIntegrazioneTest {

    private static final String CONFERMA = "/api/comunicazioni/{id}/ricezione";

    /** Account che provano a confermare una comunicazione indirizzata ad altri. */
    enum Estraneo {
        OPT_FUORI_DAL_GRUPPO,
        OPERATORE,
        BANCO
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
        svuotaComunicazioni();
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        accesso = new AccessoDiProva(mvc, jdbc, encoder);
        sessioni.clear();

        banco = dati.banco("Banco di prova");
        optA = dati.opt("OPT A", "napoli");
        optB = dati.opt("OPT B", "napoli");
        optEstranea = dati.opt("OPT estranea", "napoli");
        operatoreA = dati.operatore("Operatore A", "INTERNO_BANCO", "napoli", false);
        operatoreB = dati.operatore("Operatore B", "ESTERNO_PAGATO_BANCO", "napoli", false);
    }

    /** {@link DatiSintetici#svuota()} non conosce ancora le tabelle della V18: senza pulizia fallirebbe sulle FK. */
    @AfterEach
    void svuotaComunicazioni() {
        jdbc.update("delete from comunicazioni_destinatari");
        jdbc.update("delete from comunicazioni");
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
    @DisplayName("AC1 - nella stessa comunicazione un destinatario risulta confermato e l'altro ancora in attesa")
    void ac1_confermatoEInAttesaNellaStessaComunicazione() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

        conferma(optA, id);

        assertThat(confermataIl(id, optA)).isNotNull();
        assertThat(confermataIl(id, optB)).isNull();
        daConfermare(optA).andExpect(jsonPath("$.daConfermare").value(0));
        daConfermare(optB).andExpect(jsonPath("$.daConfermare").value(1));
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
    @DisplayName("AC1 - senza il comando esplicito l'invio non registra alcun riscontro")
    void ac1_senzaComandoNessunRiscontro() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

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

    // ------------------------------------------------------------------ casi limite della #143

    @Test
    @DisplayName("#143 - comunicazione inesistente: 404 senza corpo")
    void comunicazioneInesistente() throws Exception {
        come(optA).post(CONFERMA, null, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
    }

    @Test
    @DisplayName("#143 - senza sessione: 401 e nessun riscontro")
    void senzaSessione() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);

        SessioneDiProva.anonima(mvc).post(CONFERMA, null, id).andExpect(status().isUnauthorized());

        assertThat(confermati(id)).isEmpty();
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
            case BANCO -> banco;
        };
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