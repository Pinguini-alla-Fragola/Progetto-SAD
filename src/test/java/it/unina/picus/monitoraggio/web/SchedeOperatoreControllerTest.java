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
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

@SpringBootTest(properties = "spring.datasource.url=${PICUS_TEST_SCHEDE_DB:jdbc:h2:mem:schede-operatore}")
@AutoConfigureMockMvc
@Transactional
class SchedeOperatoreControllerTest {

    private static final String PERCORSO = "/api/monitoraggio/schede-operatore";
    private static final String RIEPILOGO = PERCORSO + "/ricevute/riepilogo";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationContext contesto;

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

    @Test
    void riepilogaLeSchedeSalvateConTotaliEsattiAncheDopoLaRevoca() throws Exception {
        UUID banco = dati.banco("Banco sintetico");
        UUID collega = dati.operatore("Collega sintetico", "ESTERNO_PAGATO_BANCO", "napoli", false);
        invia(operatore, "1", """
                {"ore":1,"costoOrarioLordo":1,"conteggi":{"minori":9223372036854775807,"famiglie":0}}
                """).andExpect(status().isCreated());
        invia(collega, "1", """
                {"ore":1,"costoOrarioLordo":1,"conteggi":{"minori":1}}
                """).andExpect(status().isCreated());
        invia(collega, "2", """
                {"ore":1,"costoOrarioLordo":1,"conteggi":{"minori":42,"persone":1}}
                """).andExpect(status().isCreated());
        dati.impostaStatoAccount(operatore, "REVOCATO");

        mvc.perform(riepilogo("1").with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schede.length()").value(2))
                .andExpect(jsonPath("$.conteggi.minori").isString())
                .andExpect(jsonPath("$.conteggi.minori").value("9223372036854775808"))
                .andExpect(jsonPath("$.conteggi.famiglie").isString())
                .andExpect(jsonPath("$.conteggi.famiglie").value("0"))
                .andExpect(jsonPath("$.conteggi.persone").doesNotHaveJsonPath());
        mvc.perform(get(PERCORSO + "/ricevute").param("anno", "2025").param("trimestre", "1")
                        .with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void esponeIlRiepilogoVuotoAttraversoIlContrattoApplicativo() {
        UUID banco = dati.banco("Banco sintetico");

        var riepilogo = contesto.getBean(MonitoraggioApi.class)
                .riepilogaSchede(new IdentitaAutenticata(banco, RuoloUtente.BANCO), new Trimestre(2025, 4));

        assertThat(riepilogo.schede()).isEmpty();
        assertThat(riepilogo.conteggi()).isEmpty();
    }

    @Test
    void riservaIlRiepilogoAlBancoAutenticato() throws Exception {
        UUID opt = dati.opt("OPT sintetica", "napoli");

        mvc.perform(riepilogo("1")).andExpect(status().isUnauthorized());
        mvc.perform(riepilogo("1").with(user(operatore.toString()).roles("OPERATORE")))
                .andExpect(status().isForbidden());
        mvc.perform(riepilogo("1").with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void richiedeUnPeriodoValidoPerIlRiepilogo() throws Exception {
        UUID banco = dati.banco("Banco sintetico");

        mvc.perform(get(RIEPILOGO).param("trimestre", "5").with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.anno").isString())
                .andExpect(jsonPath("$.campi.trimestre").isString());
    }

    @Test
    void conservaPrecisioneDiOreECostoOrarioERespigeValoriFuoriLimite() throws Exception {
        String percorso = invia(operatore, "1", """
                {"ore":1234567890123.123456,"costoOrarioLordo":9999999999999.999999,"conteggi":{}}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String dettaglio = mvc.perform(get(percorso).with(user(operatore.toString()).roles("OPERATORE")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(dettaglio)
                .contains("\"ore\":1234567890123.123456")
                .contains("\"costoOrarioLordo\":9999999999999.999999");

        invia(operatore, "2", """
                {"ore":0,"costoOrarioLordo":0,"conteggi":{}}
                """).andExpect(status().isCreated());

        invia(operatore, "3", """
                {"ore":10000000000000,"costoOrarioLordo":1,"conteggi":{}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.ore").isString());

        invia(operatore, "3", """
                {"ore":1,"costoOrarioLordo":1.1234567,"conteggi":{}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.costoOrarioLordo").isString());

        invia(operatore, "3", """
                {"ore":-0.01,"costoOrarioLordo":1,"conteggi":{}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.ore").isString());
    }

    private static MockHttpServletRequestBuilder riepilogo(String trimestre) {
        return get(RIEPILOGO).param("anno", "2025").param("trimestre", trimestre);
    }

    private ResultActions invia(String campo, String valoreJson) throws Exception {
        return invia(operatore, "1", """
                {"%s":"%s","ore":1,"costoOrarioLordo":1,"conteggi":{}}
                """.formatted(campo, valoreJson));
    }

    private ResultActions invia(UUID titolare, String trimestre, String corpo) throws Exception {
        return mvc.perform(post(PERCORSO).param("anno", "2025").param("trimestre", trimestre)
                .with(user(titolare.toString()).roles("OPERATORE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }
}
