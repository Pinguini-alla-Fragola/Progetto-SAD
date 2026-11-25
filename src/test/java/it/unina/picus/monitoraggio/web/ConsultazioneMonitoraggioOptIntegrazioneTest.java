package it.unina.picus.monitoraggio.web;

import static it.unina.picus.shared.ClientHttp.assertDiniegoSenzaCorpo;
import static it.unina.picus.shared.ClientHttp.assertNonAutenticatoSenzaDati;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.unina.picus.shared.AccessoDiProva;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.SessioneDiProva;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test permanenti di consultazione del monitoraggio OPT (#177, US #69):
 * filtri per trimestre e intervallo inclusivo, somme cumulative esatte,
 * categorie sovrapponibili distinte, distinzione tra zero dichiarato e dato assente,
 * sostituzione unica delle correzioni ammesse e isolamento degli accessi.
 *
 * <p>L'abilitazione e la scadenza delle correzioni sono preparate tramite la tabella
 * {@code monitoraggio_configurazione} in attesa della configurazione UI Banco della #172.</p>
 */
@IntegrazionePostgresql
@Tag("US-69")
@DisplayName("#177 Consultazione del monitoraggio OPT: filtri, somme, correzioni e accessi")
class ConsultazioneMonitoraggioOptIntegrazioneTest {

    private static final String CONTEGGI = "/api/monitoraggio/conteggi-opt";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    private DatiSintetici dati;
    private AccessoDiProva accesso;

    private long m01;
    private long m02;
    private UUID optA;
    private UUID optB;
    private UUID banco;
    private UUID operatore;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        accesso = new AccessoDiProva(mvc, jdbc, encoder);
        m01 = dati.misura("M01");
        m02 = dati.misura("M02");
        optA = dati.opt("OPT Alfa 177", "napoli");
        optB = dati.opt("OPT Beta 177", "portici");
        banco = dati.banco("Banco 177");
        operatore = dati.operatore("Operatore 177", "INTERNO_BANCO", "napoli", false, m01);
    }

    @AfterEach
    void ripristina() {
        dati.svuota();
    }

    @Test
    @DisplayName("Dataset #177: trimestre, cumulativo, categorie sovrapposte, zero, assenza e correzione sostituita")
    void filtriCumulativoZeroAssenzaECorrezione() throws Exception {
        SessioneDiProva sA = accesso.accedi(optA);
        SessioneDiProva sB = accesso.accedi(optB);
        SessioneDiProva sBanco = accesso.accedi(banco);

        // T1 (2025-1): OPT A M1 PERSONE_RAGGIUNTE = 5 poi corretto a 7; MINORI = 0; OPT B M1 PERSONE_RAGGIUNTE = 2
        registra(sA, 2025, 1, voce(m01, "PERSONE_RAGGIUNTE", 5), voce(m01, "MINORI", 0))
                .andExpect(status().isNoContent());
        registra(sB, 2025, 1, voce(m01, "PERSONE_RAGGIUNTE", 2)).andExpect(status().isNoContent());

        // T2 (2025-2): OPT A M1 PERSONE_RAGGIUNTE = 3; OPT A M2 PERSONE_RAGGIUNTE = 4
        registra(sA, 2025, 2, voce(m01, "PERSONE_RAGGIUNTE", 3), voce(m02, "PERSONE_RAGGIUNTE", 4))
                .andExpect(status().isNoContent());

        // T3 (2025-3, fuori filtro): OPT A M1 PERSONE_RAGGIUNTE = 20
        registra(sA, 2025, 3, voce(m01, "PERSONE_RAGGIUNTE", 20)).andExpect(status().isNoContent());

        // Correzione abilitata con scadenza futura: T1 OPT A M1 PERSONE_RAGGIUNTE 5 -> 7
        abilitaCorrezioni(Instant.now().plus(1, ChronoUnit.DAYS));
        registra(sA, 2025, 1, voce(m01, "PERSONE_RAGGIUNTE", 7), voce(m01, "MINORI", 0))
                .andExpect(status().isNoContent());
        // Ritrasmissione identica: resta 7 una sola volta
        registra(sA, 2025, 1, voce(m01, "PERSONE_RAGGIUNTE", 7)).andExpect(status().isNoContent());

        Map<String, String> t1 = indicizza(consulta(sBanco, null, 2025, 1, null, null));
        assertEquals(Map.of(
                chiave(optA, m01, "PERSONE_RAGGIUNTE"), "7",
                chiave(optA, m01, "MINORI"), "0",
                chiave(optB, m01, "PERSONE_RAGGIUNTE"), "2"), t1);

        Map<String, String> cumulativo = indicizza(consulta(sBanco, null, 2025, 1, 2025, 2));
        assertEquals(Map.of(
                chiave(optA, m01, "PERSONE_RAGGIUNTE"), "10",
                chiave(optA, m01, "MINORI"), "0",
                chiave(optA, m02, "PERSONE_RAGGIUNTE"), "4",
                chiave(optB, m01, "PERSONE_RAGGIUNTE"), "2"), cumulativo);
        assertFalse(cumulativo.containsKey(chiave(optB, m01, "MINORI")));
        assertFalse(cumulativo.containsKey(chiave(optA, m02, "MINORI")));
        assertFalse(cumulativo.containsKey(chiave(optB, m02, "PERSONE_RAGGIUNTE")));

        // Filtro per singola OPT dal Banco e dalla OPT titolare
        assertEquals(Map.of(
                chiave(optA, m01, "PERSONE_RAGGIUNTE"), "10",
                chiave(optA, m01, "MINORI"), "0",
                chiave(optA, m02, "PERSONE_RAGGIUNTE"), "4"),
                indicizza(consulta(sBanco, optA, 2025, 1, 2025, 2)));
        assertEquals(Map.of(chiave(optB, m01, "PERSONE_RAGGIUNTE"), "2"),
                indicizza(consulta(sB, optB, 2025, 1, 2025, 2)));
    }

    @Test
    @DisplayName("Inclusività a cavallo d'anno e precisione esatta oltre MAX_SAFE_INTEGER e Long.MAX_VALUE")
    void cambioAnnoEPrecisioneOltreLong() throws Exception {
        SessioneDiProva sA = accesso.accedi(optA);
        SessioneDiProva sBanco = accesso.accedi(banco);

        registra(sA, 2025, 4, voce(m01, "PERSONE_RAGGIUNTE", 9_223_372_036_854_775_807L))
                .andExpect(status().isNoContent());
        registra(sA, 2026, 1, voce(m01, "PERSONE_RAGGIUNTE", 2)).andExpect(status().isNoContent());
        registra(sA, 2026, 2, voce(m01, "PERSONE_RAGGIUNTE", 50)).andExpect(status().isNoContent());

        Map<String, String> cavalloAnno = indicizza(consulta(sBanco, optA, 2025, 4, 2026, 1));
        assertEquals(Map.of(
                chiave(optA, m01, "PERSONE_RAGGIUNTE"), new BigInteger("9223372036854775809").toString()),
                cavalloAnno);
    }

    @Test
    @DisplayName("Correzioni disattivate o scadute non alterano i riepiloghi e ricevono 409")
    void correzioniDisabilitateOScaduteNonModificano() throws Exception {
        SessioneDiProva sA = accesso.accedi(optA);
        SessioneDiProva sBanco = accesso.accedi(banco);

        registra(sA, 2025, 1, voce(m01, "MINORI", 9)).andExpect(status().isNoContent());

        // Correzioni disattivate di default
        registra(sA, 2025, 1, voce(m01, "MINORI", 15)).andExpect(status().isConflict());

        // Scadenza già trascorsa
        abilitaCorrezioni(Instant.now().minus(1, ChronoUnit.HOURS));
        registra(sA, 2025, 1, voce(m01, "MINORI", 15)).andExpect(status().isConflict());

        assertEquals(Map.of(chiave(optA, m01, "MINORI"), "9"),
                indicizza(consulta(sBanco, optA, 2025, 1, null, null)));
    }

    @Test
    @DisplayName("Accessi e intervalli invalidi: 403/401 senza dati per non autorizzati, 400 su range invertito")
    void accessiERangeInvalidi() throws Exception {
        SessioneDiProva sA = accesso.accedi(optA);
        SessioneDiProva sB = accesso.accedi(optB);
        SessioneDiProva sOp = accesso.accedi(operatore);
        SessioneDiProva sBanco = accesso.accedi(banco);

        registra(sA, 2025, 1, voce(m01, "PERSONE_RAGGIUNTE", 11)).andExpect(status().isNoContent());

        assertAll(
                () -> assertNonAutenticatoSenzaDati(mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(CONTEGGI)
                                .param("anno", "2025").param("trimestre", "1"))
                        .andReturn().getResponse()),
                () -> assertDiniegoSenzaCorpo(sOp.get(CONTEGGI + "?anno=2025&trimestre=1")
                        .andReturn().getResponse()),
                () -> assertDiniegoSenzaCorpo(sOp.get(CONTEGGI + "?optId={o}&anno=2025&trimestre=1", optA)
                        .andReturn().getResponse()),
                () -> assertDiniegoSenzaCorpo(sB.get(CONTEGGI + "?anno=2025&trimestre=1")
                        .andReturn().getResponse()),
                () -> assertDiniegoSenzaCorpo(sB.get(CONTEGGI + "?optId={o}&anno=2025&trimestre=1", optA)
                        .andReturn().getResponse()));

        sBanco.get(CONTEGGI + "?anno=2025&trimestre=3&annoFine=2025&trimestreFine=1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messaggio").isString());
        sBanco.get(CONTEGGI + "?anno=2025&trimestre=1&annoFine=2025")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.trimestreFine").isString());
    }

    private ResultActions registra(SessioneDiProva sessione, int anno, int trimestre, String... voci) throws Exception {
        String corpo = """
                {"anno":%d,"trimestre":%d,"voci":[%s]}
                """.formatted(anno, trimestre, String.join(",", voci));
        return sessione.post(CONTEGGI, corpo);
    }

    private static String voce(long misuraId, String codice, long valore) {
        return """
                {"misuraId":%d,"voce":"%s","valore":%d}
                """.formatted(misuraId, codice, valore);
    }

    private List<Map<String, Object>> consulta(
            SessioneDiProva sessione, UUID optId, int anno, int trimestre, Integer annoFine, Integer trimestreFine)
            throws Exception {
        StringBuilder url = new StringBuilder(CONTEGGI)
                .append("?anno=").append(anno)
                .append("&trimestre=").append(trimestre);
        if (optId != null) {
            url.append("&optId=").append(optId);
        }
        if (annoFine != null && trimestreFine != null) {
            url.append("&annoFine=").append(annoFine).append("&trimestreFine=").append(trimestreFine);
        }
        String json = sessione.get(url.toString())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readValue(json, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> indicizza(List<Map<String, Object>> righe) {
        Map<String, String> risultato = new LinkedHashMap<>();
        for (Map<String, Object> riga : righe) {
            UUID optId = UUID.fromString(String.valueOf(riga.get("optId")));
            Map<String, Object> conteggio = (Map<String, Object>) riga.get("conteggio");
            long misuraId = ((Number) conteggio.get("misuraId")).longValue();
            String voce = String.valueOf(conteggio.get("voce"));
            String valore = String.valueOf(conteggio.get("valore"));
            String precedente = risultato.put(chiave(optId, misuraId, voce), valore);
            if (precedente != null) {
                throw new AssertionError("Chiave duplicata nel riepilogo: " + optId + "|" + misuraId + "|" + voce);
            }
        }
        return risultato;
    }

    private static String chiave(UUID optId, long misuraId, String voce) {
        return optId + "|" + misuraId + "|" + voce;
    }

    private void abilitaCorrezioni(Instant scadenza) {
        jdbc.update("update monitoraggio_configurazione set correzioni_abilitate = true, scadenza_correzioni = ?",
                scadenza == null ? null : Timestamp.from(scadenza));
    }
}
