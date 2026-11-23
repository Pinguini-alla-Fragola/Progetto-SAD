package it.unina.picus.monitoraggio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.shared.DatiSintetici;

@SpringBootTest(properties = "spring.datasource.url=${PICUS_TEST_SCHEDE_DB:jdbc:h2:mem:schede-operatore}")
@AutoConfigureMockMvc
@Transactional
class SchedeOperatoreControllerTest {

    private static final String PERCORSO = "/api/monitoraggio/schede-operatore";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private UUID operatore;

    @BeforeEach
    void preparaOperatore() {
        dati = new DatiSintetici(jdbc);
        operatore = dati.operatore("Operatore sintetico", "INTERNO_BANCO", "napoli", false);
    }

    @Test
    void nascondeAlloStessoOperatoreSchedeAltruiEAssenti() throws Exception {
        String percorsoScheda = invia("nome", "Nome sintetico").andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String percorsoAssente = PERCORSO + "/" + UUID.randomUUID();
        UUID collega = dati.operatore("Collega sintetico", "INTERNO_BANCO", "napoli", false);
        UUID banco = dati.banco("Banco sintetico");

        mvc.perform(get(percorsoScheda).with(user(operatore.toString()).roles("OPERATORE")))
                .andExpect(status().isOk());
        var altrui = mvc.perform(get(percorsoScheda).with(user(collega.toString()).roles("OPERATORE")))
                .andExpect(status().isForbidden()).andReturn().getResponse();
        var assente = mvc.perform(get(percorsoAssente).with(user(collega.toString()).roles("OPERATORE")))
                .andExpect(status().isForbidden()).andReturn().getResponse();
        assertThat(assente.getContentAsString()).isEqualTo(altrui.getContentAsString());
        mvc.perform(get(percorsoAssente).with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"nome, D800", "tipologiaContratto, DC00"})
    void rifiutaSurrogatiIsolatiSenzaRegistrareLaScheda(String campo, String esadecimale) throws Exception {
        invia(campo, "\\u" + esadecimale)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi." + campo).isString());
        assertThat(jdbc.queryForObject("select count(*) from monitoraggio_schede_operatore where operatore_id = ?",
                Integer.class, operatore)).isZero();
    }

    @Test
    void conservaUnaCoppiaUnicodeValida() throws Exception {
        invia("nome", "\\uD83D\\uDE00").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select nome from monitoraggio_schede_operatore where operatore_id = ?",
                String.class, operatore)).isEqualTo("\uD83D\uDE00");
    }

    private ResultActions invia(String campo, String valoreJson) throws Exception {
        return mvc.perform(post(PERCORSO).param("anno", "2025").param("trimestre", "1")
                .with(user(operatore.toString()).roles("OPERATORE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"%s":"%s","ore":1,"costoOrarioLordo":1,"conteggi":{}}
                        """.formatted(campo, valoreJson)));
    }
}
