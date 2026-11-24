package it.unina.picus.monitoraggio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

@IntegrazionePostgresql
class ConteggiOptControllerTest {

    private static final String PERCORSO = "/api/monitoraggio/conteggi-opt";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private UUID opt;
    private UUID banco;
    private long misura;

    @BeforeEach
    void preparaDati() {
        var dati = new DatiSintetici(jdbc);
        opt = dati.opt("OPT di prova per i conteggi", "napoli");
        banco = dati.banco("Banco di prova per i conteggi");
        misura = dati.misura("M01");
    }

    @AfterEach
    void rimuoveConteggi() {
        jdbc.update("delete from monitoraggio_conteggi_opt where opt_id = ?", opt);
    }

    @Test
    void salvaERestituisceIConteggiConPrecisioneIntera() throws Exception {
        invia(1, "9007199254740991").andExpect(status().isNoContent());
        invia(2, "\"9007199254740993\"").andExpect(status().isNoContent());
        invia(3, "9223372036854775807").andExpect(status().isNoContent());
        invia(4, "\"9223372036854775808\"").andExpect(status().isBadRequest());

        mvc.perform(consulta("1")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conteggio.valore").isNumber())
                .andExpect(jsonPath("$[0].conteggio.valore").value(9_007_199_254_740_991L));
        mvc.perform(consulta("2")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conteggio.valore").value("9007199254740993"));
        mvc.perform(consulta("3")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conteggio.valore").value("9223372036854775807"));

        assertThat(jdbc.queryForList("""
                select valore from monitoraggio_conteggi_opt where opt_id = ? order by trimestre
                """, Long.class, opt))
                .containsExactly(9_007_199_254_740_991L, 9_007_199_254_740_993L, Long.MAX_VALUE);
    }

    @Test
    void richiedeAnnoETrimestreFinaliInsieme() throws Exception {
        invia(1, "7").andExpect(status().isNoContent());
        invia(2, "8").andExpect(status().isNoContent());

        mvc.perform(consulta("1").param("annoFine", "2025"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campi.trimestreFine").exists());
        mvc.perform(consulta("1").param("trimestreFine", "2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campi.annoFine").exists());
        mvc.perform(consulta("1")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conteggio.valore").value(7));
        mvc.perform(consulta("1").param("annoFine", "2025").param("trimestreFine", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].conteggio.valore").value(15));
    }

    @Test
    void accettaDueInviiConcorrentiConLeStesseVociInOrdineOpposto() throws Exception {
        String minori = voce("MINORI", "7");
        String anziani = voce("ANZIANI", "8");
        var pronti = new CountDownLatch(2);
        try (var esecutore = Executors.newFixedThreadPool(2)) {
            var primo = esecutore.submit(() -> inviaInsieme(pronti, minori + "," + anziani));
            var secondo = esecutore.submit(() -> inviaInsieme(pronti, anziani + "," + minori));
            primo.get(15, TimeUnit.SECONDS).andExpect(status().isNoContent());
            secondo.get(15, TimeUnit.SECONDS).andExpect(status().isNoContent());
        }

        assertThat(jdbc.queryForList("""
                select valore from monitoraggio_conteggi_opt where opt_id = ? order by voce
                """, Long.class, opt)).containsExactly(8L, 7L);
    }

    private ResultActions inviaInsieme(CountDownLatch pronti, String voci) throws Exception {
        pronti.countDown();
        assertThat(pronti.await(5, TimeUnit.SECONDS)).isTrue();
        return inviaVoci(2, voci);
    }

    private ResultActions invia(int trimestre, String valoreJson) throws Exception {
        return inviaVoci(trimestre, voce("MINORI", valoreJson));
    }

    private String voce(String codice, String valoreJson) {
        return """
                {"misuraId":%d,"voce":"%s","valore":%s}
                """.formatted(misura, codice, valoreJson);
    }

    private ResultActions inviaVoci(int trimestre, String voci) throws Exception {
        String corpo = """
                {"anno":2025,"trimestre":%d,"voci":[%s]}
                """.formatted(trimestre, voci);
        return mvc.perform(post(PERCORSO).with(user(opt.toString()).roles("OPT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private MockHttpServletRequestBuilder consulta(String trimestre) {
        return get(PERCORSO).with(user(banco.toString()).roles("BANCO"))
                .param("optId", opt.toString()).param("anno", "2025").param("trimestre", trimestre);
    }
}
