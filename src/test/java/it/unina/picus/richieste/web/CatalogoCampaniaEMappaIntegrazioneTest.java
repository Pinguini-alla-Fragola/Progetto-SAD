package it.unina.picus.richieste.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

/**
 * Integrazione su PostgreSQL reale di catalogo campano completo (#251), ricerca territoriale
 * con isole e contatti in un solo punto, e visibilità della mappa erogatori (#253).
 */
@IntegrazionePostgresql
@DisplayName("Catalogo Campania e mappa erogatori nel dettaglio Banco (#251, #253)")
class CatalogoCampaniaEMappaIntegrazioneTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private long m01;
    private long m04;
    private UUID opt;
    private UUID banco;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        m01 = dati.misura("M01");
        m04 = dati.misura("M04");
        dati.abilitaADistanza(m04);
        opt = dati.opt("OPT Richiedente", "napoli");
        banco = dati.banco("Banco Coordinamento");
    }

    @Test
    @DisplayName("L'endpoint catalogo e il modulo nuova richiesta espongono tutti i comuni campani con provincia o città metropolitana")
    void catalogoENuovaRichiestaConComuniCampani() throws Exception {
        String json = mvc.perform(get("/api/catalogo/comuni").with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> istat = JsonPath.read(json, "$[*].codiceIstat");
        assertThat(istat).contains("063049", "061005", "063061", "065116");

        mvc.perform(get("/richieste/nuova").with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"ricerca-comune\"")))
                .andExpect(content().string(containsString("value=\"061005\"")))
                .andExpect(content().string(containsString("Aversa — Provincia di Caserta (CE)")))
                .andExpect(content().string(containsString("value=\"063061\"")))
                .andExpect(content().string(containsString("Procida — Città metropolitana di Napoli (NA)")))
                .andExpect(content().string(containsString("Napoli — Città metropolitana di Napoli (NA)")));
    }

    @Test
    @DisplayName("Una OPT invia una richiesta per un comune assente dal catalogo originario (Aversa) e la vista mostra comune e candidati compatibili")
    void invioECandidatiSuNuovoComuneCampano() throws Exception {
        UUID erogAversa = dati.operatore("Operatore Aversa", "INTERNO_BANCO", "061005", false, m01);
        UUID erogLimitrofo = dati.opt("OPT Trentola", "061094");
        dati.offerta(erogLimitrofo, m01);
        UUID erogLontano = dati.operatore("Operatore Salerno", "INTERNO_BANCO", "065116", false, m01);

        String rispostaInvio = mvc.perform(post("/api/richieste")
                        .with(user(opt.toString()).roles("OPT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"misuraId":%d,"tipoDestinatario":"PERSONA","comuneCodice":"061005","description":"Richiesta sintetica"}
                                """.formatted(m01)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID richiestaId = UUID.fromString(JsonPath.read(rispostaInvio, "$.id"));

        mvc.perform(get("/richieste/{id}", richiestaId).with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aversa")))
                .andExpect(content().string(containsString("Provincia di Caserta (CE)")))
                .andExpect(content().string(not(containsString("id=\"sezione-mappa-erogatori\""))));

        mvc.perform(post("/api/richieste/{id}/presa-in-carico", richiestaId)
                        .with(user(banco.toString()).roles("BANCO")).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/richieste/{id}", richiestaId).with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"sezione-mappa-erogatori\"")))
                .andExpect(content().string(containsString("data-comune-codice=\"061005\"")))
                .andExpect(content().string(containsString("data-comune-istat=\"061005\"")))
                .andExpect(content().string(containsString("data-comune-nome=\"Aversa\"")));

        String corpoCandidati = mvc.perform(get("/api/richieste/{id}/candidati", richiestaId)
                        .with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].erogatore.accountId").value(erogAversa.toString()))
                .andExpect(jsonPath("$[0].erogatore.ambito").value("STESSO_COMUNE"))
                .andExpect(jsonPath("$[1].erogatore.accountId").value(erogLimitrofo.toString()))
                .andExpect(jsonPath("$[1].erogatore.ambito").value("COMUNE_LIMITROFO"))
                .andReturn().getResponse().getContentAsString();
        assertThat(corpoCandidati).doesNotContain(erogLontano.toString());
    }

    @Test
    @DisplayName("Isole senza adiacenze (Procida) e contatti puntiformi (Pompei / Sant'Antonio Abate) rispettano la regola territoriale")
    void isolaSenzaLimitrofiEContattoPuntiforme() throws Exception {
        dati.operatore("Operatore Napoli", "INTERNO_BANCO", "napoli", false, m04);
        UUID centro = dati.operatore("Centro PICUS", "INTERNO_BANCO", "napoli", true, m04);
        UUID optSantAntonio = dati.opt("OPT Sant'Antonio Abate", "063074");
        dati.offerta(optSantAntonio, m01);

        UUID procida = dati.richiesta(opt, m04, "063061", "PRESA_IN_CARICO", Instant.parse("2026-11-21T09:00:00Z"));
        mvc.perform(get("/api/richieste/{id}/candidati", procida).with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].erogatore.accountId").value(centro.toString()))
                .andExpect(jsonPath("$[0].erogatore.ambito").value("CENTRO_PICUS_A_DISTANZA"));

        UUID pompei = dati.richiesta(opt, m01, "063058", "PRESA_IN_CARICO", Instant.parse("2026-11-21T09:05:00Z"));
        mvc.perform(get("/api/richieste/{id}/candidati", pompei).with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Vista mappa assente per ruoli non Banco e per stati diversi da PRESA_IN_CARICO; asset statici accessibili da autenticato")
    void permessiVistaMappaEAssetStatici() throws Exception {
        UUID operatore = dati.operatore("Operatore assegnato", "INTERNO_BANCO", "napoli", false, m01);
        UUID inProgrammazione = dati.richiesta(opt, m01, "napoli", "IN_PROGRAMMAZIONE", Instant.parse("2026-11-21T09:00:00Z"));
        jdbc.update("update richieste set erogatore_id = ?, erogatore_ambito = 'STESSO_COMUNE' where id = ?",
                operatore, inProgrammazione);
        UUID creata = dati.richiesta(opt, m01, "napoli", "CREATA", Instant.parse("2026-11-21T09:01:00Z"));

        mvc.perform(get("/richieste/{id}", inProgrammazione).with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"sezione-mappa-erogatori\""))));
        mvc.perform(get("/richieste/{id}", creata).with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"sezione-mappa-erogatori\""))));
        mvc.perform(get("/richieste/{id}", inProgrammazione).with(user(operatore.toString()).roles("OPERATORE")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"sezione-mappa-erogatori\""))));
        mvc.perform(get("/api/richieste/{id}/candidati", creata).with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/dati/comuni-campania-istat-2026.json").with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(550));
        mvc.perform(get("/js/vendor/d3-geo.js").with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk());
    }
}
