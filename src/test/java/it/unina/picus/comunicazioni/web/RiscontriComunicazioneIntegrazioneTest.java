package it.unina.picus.comunicazioni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.unina.picus.shared.AccessoDiProva;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.SessioneDiProva;

/**
 * Test di integrazione della #145 sul codice della #144 (PR #193): consultazione dei riscontri da parte del
 * Banco con {@code GET /api/comunicazioni/{comunicazioneId}/riscontri}, login reale e PostgreSQL.
 *
 * I test riportano nel nome il criterio della user story #56 che verificano. A differenza di
 * ConfermaRicezioneIntegrazioneTest, qui ogni esito si legge dalla vista del Banco e non dalle righe del
 * database: è quella vista che deve distinguere le conferme presenti da quelle mancanti.
 *
 * Lo scenario nasce dal flusso dell'applicazione: il Banco invia con l'endpoint della #140, i destinatari aprono
 * o confermano nella propria sessione e il Banco consulta i riscontri. Nel database si inseriscono con
 * {@link DatiSintetici} soltanto account e profili.
 */
@IntegrazionePostgresql
@Tag("issue-145")
@DisplayName("#145 Riscontri di ricezione nella vista del Banco: integrazione su HTTP e PostgreSQL")
class RiscontriComunicazioneIntegrazioneTest {

    private static final String RISCONTRI = "/api/comunicazioni/{id}/riscontri";
    private static final String CONFERMA = "/api/comunicazioni/{id}/ricezione";

    /** Account autenticati diversi dal Banco. */
    enum NonBanco {
        OPT_DESTINATARIA,
        OPT_ESTRANEA,
        OPERATORE
    }

    /** Voce della vista del Banco, letta dalla risposta JSON. */
    record Voce(UUID destinatarioId, String nomeVisualizzato, Instant confermataIl) {}

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
    private UUID optC;
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
        optC = dati.opt("OPT C", "napoli");
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
    @DisplayName("AC1 - la conferma registrata compare al Banco con la stessa data e ora restituita al destinatario")
    void ac1_confermaMostrataAlBanco() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

        Instant restituito = conferma(optA, id);

        assertThat(riscontri(id)).contains(new Voce(optA, "OPT A", restituito));
    }

    @Test
    @DisplayName("AC5 - il riscontro della OPT è uno solo, intestato all'account dell'organizzazione con il suo nome")
    void ac5_riscontroDellOrganizzazione() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);
        Instant restituito = conferma(optA, id);

        assertThat(riscontri(id)).containsExactly(new Voce(optA, "OPT A", restituito));
    }

    @Test
    @DisplayName("AC1 - l'operatore conferma per il proprio account personale e il collega resta in attesa")
    void ac1_operatorePerSe() throws Exception {
        UUID id = invia("TUTTI_GLI_OPERATORI", List.of());

        Instant restituito = conferma(operatoreB, id);

        assertThat(riscontri(id)).containsExactly(
                new Voce(operatoreA, "Operatore A", null),
                new Voce(operatoreB, "Operatore B", restituito));
    }

    // ------------------------------------------------------------------ AC2

    @Test
    @DisplayName("AC2 - subito dopo l'invio il Banco vede tutti i destinatari in attesa")
    void ac2_tuttiInAttesaDopoLInvio() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB, optC);

        assertThat(riscontri(id)).containsExactly(
                new Voce(optA, "OPT A", null),
                new Voce(optB, "OPT B", null),
                new Voce(optC, "OPT C", null));
    }

    @Test
    @DisplayName("AC2 - con più destinatari il Banco distingue le conferme con data e ora da quelle mancanti")
    void ac2_confermeEMancanti() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB, optC);

        Instant diC = conferma(optC, id);
        Instant diA = conferma(optA, id);

        assertThat(riscontri(id)).containsExactly(
                new Voce(optA, "OPT A", diA),
                new Voce(optB, "OPT B", null),
                new Voce(optC, "OPT C", diC));
    }

    @Test
    @DisplayName("AC2 - invio a tutte le OPT: una voce per ogni OPT attiva all'invio, nessun operatore")
    void ac2_tutteLeOpt() throws Exception {
        UUID id = invia("TUTTE_LE_OPT", List.of());
        Instant diB = conferma(optB, id);

        assertThat(riscontri(id))
                .extracting(Voce::destinatarioId, Voce::confermataIl)
                .containsExactlyInAnyOrder(
                        tuple(optA, null), tuple(optB, diB), tuple(optC, null), tuple(optEstranea, null));
    }

    @Test
    @DisplayName("AC2 - i destinatari sono ordinati per nome, senza distinguere maiuscole e minuscole")
    void ac2_ordinePerNome() throws Exception {
        UUID beta = dati.opt("beta OPT", "napoli");
        UUID alfa = dati.opt("Alfa OPT", "napoli");
        UUID gamma = dati.opt("Gamma OPT", "napoli");
        UUID id = inviaAGruppo("GRUPPO_OPT", gamma, beta, alfa);

        assertThat(riscontri(id)).extracting(Voce::nomeVisualizzato)
                .containsExactly("Alfa OPT", "beta OPT", "Gamma OPT");
    }

    @Test
    @DisplayName("AC2 - omonimi: due voci distinte, ciascuna con il proprio account e il proprio stato")
    void ac2_omonimi() throws Exception {
        UUID primo = dati.operatore("Operatore Rossi", "INTERNO_BANCO", "napoli", false);
        UUID secondo = dati.operatore("Operatore Rossi", "ESTERNO_PAGATO_BANCO", "napoli", false);
        UUID id = inviaAGruppo("GRUPPO_OPERATORI", primo, secondo);
        Instant diSecondo = conferma(secondo, id);

        assertThat(riscontri(id)).containsExactlyInAnyOrder(
                new Voce(primo, "Operatore Rossi", null),
                new Voce(secondo, "Operatore Rossi", diSecondo));
    }

    @Test
    @DisplayName("AC2 - isolamento: i riscontri di un'altra comunicazione non compaiono")
    void ac2_isolamentoFraComunicazioni() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);
        UUID altra = inviaAGruppo("GRUPPO_OPT", optA, optB);
        conferma(optA, altra);

        assertThat(riscontri(id)).containsExactly(new Voce(optA, "OPT A", null));
    }

    @Test
    @DisplayName("AC2 - ogni voce espone solo account, nome e data della conferma")
    void ac2_soloICampiPrevisti() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);
        conferma(optA, id);

        List<Map<String, Object>> voci = JsonPath.read(corpoRiscontri(id), "$");

        assertThat(voci).allSatisfy(voce ->
                assertThat(voce.keySet()).containsExactlyInAnyOrder("destinatarioId", "nomeVisualizzato", "confermataIl"));
    }

    // ------------------------------------------------------------------ AC3

    @Test
    @DisplayName("AC3 - il destinatario apre il dettaglio senza premere il pulsante: il Banco lo vede ancora in attesa")
    void ac3_aperturaSenzaConferma() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

        come(optA).get("/api/comunicazioni/{id}", id).andExpect(status().isOk());
        come(optA).get("/api/comunicazioni/{id}", id).andExpect(status().isOk());

        assertThat(riscontri(id)).containsExactly(
                new Voce(optA, "OPT A", null),
                new Voce(optB, "OPT B", null));
        come(optA).get("/api/comunicazioni/da-confermare").andExpect(jsonPath("$.daConfermare").value(1));
    }

    @Test
    @DisplayName("AC3 - la consultazione del Banco non registra conferme e non cambia il conteggio dei destinatari")
    void ac3_consultazioneDelBancoSenzaEffetti() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);

        riscontri(id);
        riscontri(id);

        assertThat(riscontri(id)).containsExactly(new Voce(optA, "OPT A", null));
        come(optA).get("/api/comunicazioni/da-confermare").andExpect(jsonPath("$.daConfermare").value(1));
    }

    // ------------------------------------------------------------------ AC4

    @Test
    @DisplayName("AC4 - dopo più pressioni, anche da una nuova sessione, il Banco vede la data e ora originarie")
    void ac4_ripetizioneMostrataAlBanco() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);
        Instant prima = conferma(optA, id);

        conferma(optA, id);
        accesso.accedi(optA).post(CONFERMA, null, id).andExpect(status().isOk());

        assertThat(riscontri(id)).containsExactly(
                new Voce(optA, "OPT A", prima),
                new Voce(optB, "OPT B", null));
    }

    // ------------------------------------------------------------------ AC6

    @Test
    @DisplayName("AC6 - dopo il tentativo negato di un estraneo il Banco non vede conferme")
    void ac6_tentativoEstraneoInvisibile() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA, optB);

        come(optEstranea).post(CONFERMA, "{\"destinatarioId\": \"" + optA + "\"}", id)
                .andExpect(status().isForbidden());
        come(operatoreA).post(CONFERMA, null, id).andExpect(status().isForbidden());

        assertThat(riscontri(id)).containsExactly(
                new Voce(optA, "OPT A", null),
                new Voce(optB, "OPT B", null));
    }

    // ------------------------------------------------------------------ accesso alla vista

    @ParameterizedTest(name = "{0}: 403 senza corpo sui riscontri")
    @EnumSource(NonBanco.class)
    void soloIlBancoConsulta(NonBanco attore) throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);

        come(account(attore)).get(RISCONTRI, id)
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
    }

    @ParameterizedTest(name = "{0} su una comunicazione inesistente: 403, senza sapere se esiste")
    @EnumSource(NonBanco.class)
    void nonBancoSuComunicazioneInesistente(NonBanco attore) throws Exception {
        come(account(attore)).get(RISCONTRI, UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Senza sessione: 401")
    void senzaSessione() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPT", optA);

        SessioneDiProva.anonima(mvc).get(RISCONTRI, id).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Il Banco su una comunicazione inesistente: 404")
    void comunicazioneInesistente() throws Exception {
        come(banco).get(RISCONTRI, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Il Banco con un identificativo che non è un UUID: 400")
    void identificativoNonUuid() throws Exception {
        come(banco).get(RISCONTRI, "non-un-uuid").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Account revocato prima di confermare: resta fra i destinatari, con il suo nome e in attesa")
    void destinatarioRevocatoInAttesa() throws Exception {
        UUID id = inviaAGruppo("GRUPPO_OPERATORI", operatoreA, operatoreB);
        dati.impostaStatoAccount(operatoreA, "REVOCATO");

        assertThat(riscontri(id)).containsExactly(
                new Voce(operatoreA, "Operatore A", null),
                new Voce(operatoreB, "Operatore B", null));
    }

    // ------------------------------------------------------------------ supporto

    private List<Voce> riscontri(UUID comunicazione) throws Exception {
        List<Map<String, Object>> voci = JsonPath.read(corpoRiscontri(comunicazione), "$");
        return voci.stream().map(voce -> new Voce(
                        UUID.fromString((String) voce.get("destinatarioId")),
                        (String) voce.get("nomeVisualizzato"),
                        voce.get("confermataIl") == null ? null : Instant.parse((String) voce.get("confermataIl"))))
                .toList();
    }

    private String corpoRiscontri(UUID comunicazione) throws Exception {
        return come(banco).get(RISCONTRI, comunicazione)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

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

    private UUID account(NonBanco attore) {
        return switch (attore) {
            case OPT_DESTINATARIA -> optA;
            case OPT_ESTRANEA -> optEstranea;
            case OPERATORE -> operatoreA;
        };
    }
}