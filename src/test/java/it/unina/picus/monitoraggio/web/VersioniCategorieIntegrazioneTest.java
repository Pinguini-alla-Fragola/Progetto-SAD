package it.unina.picus.monitoraggio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.DatiSchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.applicazione.ServizioSchedeOperatore;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeOperatore;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestreAssenteException;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integrazione HTTP su PostgreSQL 17 per la pubblicazione per trimestre delle categorie TM-OP-01,
 * compilazione/invio operatore, protezione dello storico e isolamento rispetto a TM-OPT-01 (#181, #41).
 */
@IntegrazionePostgresql
@AutoConfigureMockMvc
@Transactional
class VersioniCategorieIntegrazioneTest {

    private static final String CATEGORIE_BASE = "/api/monitoraggio/categorie-tracciati";
    private static final String SCHEDE_BASE = "/api/monitoraggio/schede-operatore";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ArchivioSchedeOperatore archivioSchede;
    @Autowired
    private ArchivioSchedeRendicontabili archivioRendicontabile;
    @Autowired
    private CategorieSchedeRendicontabili categorie;
    @Autowired
    private ConteggiOptApi conteggiOpt;

    @Test
    void pubblicazioneT3NonAlteraT2EConservaZeroEsplicitoEIsolamentoOpt() throws Exception {
        DatiSintetici dati = new DatiSintetici(jdbc);
        UUID banco = dati.banco("Banco cat");
        UUID op1 = dati.operatore("Op 1", "INTERNO_BANCO", "napoli", false);
        UUID op2 = dati.operatore("Op 2", "ESTERNO_PAGATO_BANCO", "napoli", false);
        UUID opt = dati.opt("OPT isolata", "napoli");

        var optPrima = mvc.perform(get("/api/monitoraggio/categorie").with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 1. Banco prepara bozza v1 con X e Y, la modifica in bozza e la pubblica per T2 (2024-Q2).
        String resBozza1 = mvc.perform(post(CATEGORIE_BASE + "/versioni")
                        .with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tracciato":"TM-OP-01","codiceVersione":"v1-2024-t2","descrizione":"Bozza T2",
                                 "voci":[{"codice":"cat_x","descrizione":"Categoria X","definizione":"Def X","ordine":0}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stato").value("BOZZA"))
                .andReturn().getResponse().getContentAsString();
        String idV1 = JSON.readTree(resBozza1).get("id").asString();

        mvc.perform(put(CATEGORIE_BASE + "/versioni/" + idV1)
                        .with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descrizione":"Versione T2 definitiva",
                                 "voci":[
                                   {"codice":"cat_x","descrizione":"Categoria X","definizione":"Def X definitiva","ordine":0},
                                   {"codice":"cat_y","descrizione":"Categoria Y","definizione":"Def Y definitiva","ordine":1}
                                 ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci.length()").value(2));

        mvc.perform(post(CATEGORIE_BASE + "/versioni/" + idV1 + "/pubblicazione")
                        .with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anno":2024,"trimestre":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versioneId").value(idV1));

        // Modifica dopo pubblicazione rifiutata
        mvc.perform(put(CATEGORIE_BASE + "/versioni/" + idV1)
                        .with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descrizione":"Tentativo","voci":[{"codice":"cat_y","descrizione":"Y","definizione":"D","ordine":0}]}
                                """))
                .andExpect(status().isConflict());

        // 2. Modulo T2 espone X e Y; invii X=0 e X=7 -> totale X=7, Y assente, dettaglio conserva 0
        mvc.perform(get(SCHEDE_BASE + "/modulo").param("anno", "2024").param("trimestre", "2")
                        .with(user(op1.toString()).roles("OPERATORE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurazioneCategorie.stato").value("PUBBLICATA"))
                .andExpect(jsonPath("$.configurazioneCategorie.categorie.length()").value(2));

        String locOp1 = mvc.perform(post(SCHEDE_BASE).param("anno", "2024").param("trimestre", "2")
                        .with(user(op1.toString()).roles("OPERATORE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ore":10,"costoOrarioLordo":20,"conteggi":{"cat_x":0}}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(post(SCHEDE_BASE).param("anno", "2024").param("trimestre", "2")
                        .with(user(op2.toString()).roles("OPERATORE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ore":15,"costoOrarioLordo":20,"conteggi":{"cat_x":7}}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get(locOp1).with(user(op1.toString()).roles("OPERATORE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dati.conteggi.cat_x").value(0));

        mvc.perform(get(SCHEDE_BASE + "/ricevute/riepilogo").param("anno", "2024").param("trimestre", "2")
                        .with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteggi.cat_x").value("7"))
                .andExpect(jsonPath("$.conteggi.cat_y").doesNotHaveJsonPath());

        // 3. Banco pubblica T3 (2024-Q3) ritirando cat_x e mantenendo solo cat_y
        String resBozza2 = mvc.perform(post(CATEGORIE_BASE + "/versioni")
                        .with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tracciato":"TM-OP-01","codiceVersione":"v2-2024-t3","descrizione":"Versione T3",
                                 "voci":[{"codice":"cat_y","descrizione":"Categoria Y","definizione":"Def Y","ordine":0}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idV2 = JSON.readTree(resBozza2).get("id").asString();

        mvc.perform(post(CATEGORIE_BASE + "/versioni/" + idV2 + "/pubblicazione")
                        .with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anno":2024,"trimestre":3}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get(SCHEDE_BASE + "/modulo").param("anno", "2024").param("trimestre", "3")
                        .with(user(op1.toString()).roles("OPERATORE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurazioneCategorie.categorie.length()").value(1))
                .andExpect(jsonPath("$.configurazioneCategorie.categorie[0].codice").value("cat_y"));

        mvc.perform(post(SCHEDE_BASE).param("anno", "2024").param("trimestre", "3")
                        .with(user(op1.toString()).roles("OPERATORE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ore":8,"costoOrarioLordo":20,"conteggi":{"cat_x":1}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.conteggi").isString());

        mvc.perform(post(SCHEDE_BASE).param("anno", "2024").param("trimestre", "3")
                        .with(user(op1.toString()).roles("OPERATORE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ore":8,"costoOrarioLordo":20,"conteggi":{"cat_y":3}}
                                """))
                .andExpect(status().isCreated());

        // 4. Rilettura T2 con seconda sessione Banco conserva cat_x=7
        UUID altroBanco = dati.banco("Altro Banco cat");
        mvc.perform(get(SCHEDE_BASE + "/ricevute/riepilogo").param("anno", "2024").param("trimestre", "2")
                        .with(user(altroBanco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteggi.cat_x").value("7"));

        // 5. Ripeti con sola occorrenza cat_x=0 nel 2023-Q2 e cat_x ritirata nel 2023-Q3
        mvc.perform(post(CATEGORIE_BASE + "/versioni/" + idV1 + "/pubblicazione")
                        .with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anno":2023,"trimestre":2}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(post(SCHEDE_BASE).param("anno", "2023").param("trimestre", "2")
                        .with(user(op1.toString()).roles("OPERATORE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ore":5,"costoOrarioLordo":18,"conteggi":{"cat_x":0}}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(post(CATEGORIE_BASE + "/versioni/" + idV2 + "/pubblicazione")
                        .with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anno":2023,"trimestre":3}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(get(SCHEDE_BASE + "/ricevute/riepilogo").param("anno", "2023").param("trimestre", "2")
                        .with(user(altroBanco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteggi.cat_x").isString())
                .andExpect(jsonPath("$.conteggi.cat_x").value("0"));

        // 6. Ripubblicare su trimestre già configurato riceve 409
        mvc.perform(post(CATEGORIE_BASE + "/versioni/" + idV2 + "/pubblicazione")
                        .with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anno":2024,"trimestre":2}
                                """))
                .andExpect(status().isConflict());

        // 7. TM-OPT-01 conserva esattamente le proprie categorie
        var optDopo = mvc.perform(get("/api/monitoraggio/categorie").with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(optDopo).isEqualTo(optPrima);
    }

    @Test
    void trimestreModernoNonConfiguratoRifiutaModuloEInvioERiservaApiAlBanco() throws Exception {
        DatiSintetici dati = new DatiSintetici(jdbc);
        UUID banco = dati.banco("Banco auth");
        UUID op = dati.operatore("Op modern", "INTERNO_BANCO", "napoli", false);
        UUID opt = dati.opt("OPT auth", "napoli");

        // Controllo accessi sulle rotte delle versioni
        mvc.perform(get(CATEGORIE_BASE + "/versioni"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(CATEGORIE_BASE + "/versioni").with(user(op.toString()).roles("OPERATORE")))
                .andExpect(status().isForbidden());
        mvc.perform(get(CATEGORIE_BASE + "/versioni").with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isForbidden());
        mvc.perform(post(CATEGORIE_BASE + "/versioni")
                        .with(user(op.toString()).roles("OPERATORE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tracciato":"TM-OP-01","codiceVersione":"negata","descrizione":"D",
                                 "voci":[{"codice":"X","descrizione":"X","definizione":"X"}]}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(get(CATEGORIE_BASE + "/versioni").with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk());

        // Trimestre moderno 2026-Q4 privo di pubblicazione -> 409 CONFLICT al GET /modulo
        mvc.perform(get(SCHEDE_BASE + "/modulo").param("anno", "2026").param("trimestre", "4")
                        .with(user(op.toString()).roles("OPERATORE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codice").value("CONFLICT"));

        // Quando 2026-Q4 è concluso (clock 2027-Q1) ma privo di pubblicazione -> invio rifiutato senza salvataggi
        var servizio2027 = new ServizioSchedeOperatore(
                archivioSchede,
                archivioRendicontabile,
                categorie,
                conteggiOpt,
                Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC));
        var identitaOp = new IdentitaAutenticata(op, RuoloUtente.OPERATORE);
        var datiScheda = new DatiSchedaOperatore(
                "Mario", "Rossi", null, null, null, null, null, null,
                BigDecimal.TEN, BigDecimal.valueOf(15), Map.of("cat_x", 1L));

        assertThatThrownBy(() -> servizio2027.inviaSchedaOperatore(identitaOp, new Trimestre(2026, 4), datiScheda))
                .isInstanceOf(ConfigurazioneTrimestreAssenteException.class);
        assertThat(jdbc.queryForObject("select count(*) from monitoraggio_schede_operatore where operatore_id = ?",
                Integer.class, op)).isZero();
    }
}
