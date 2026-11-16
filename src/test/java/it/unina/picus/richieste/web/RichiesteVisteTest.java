package it.unina.picus.richieste.web;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica le viste renderizzate con dati e permessi reali del backend. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:viste")
@AutoConfigureMockMvc
@Transactional
class RichiesteVisteTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID titolare;
    UUID estraneo;
    UUID richiesta;

    @BeforeEach
    void datiSintetici() {
        titolare = account();
        estraneo = account();
        jdbc.update("insert into utenze_profili_opt (account_id, codice_struttura, comune_codice) values (?, ?, 'napoli')",
                titolare, "UI-" + titolare.toString().substring(0, 8));
        richiesta = UUID.randomUUID();
        jdbc.update("""
                insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario, description, inviata_il, stato)
                select ?, ?, id, 'napoli', 'NUCLEO', 'Esigenza <sintetica>', now(), 'IN_PROGRAMMAZIONE'
                from catalogo_misure where codice = 'M01'
                """, richiesta, titolare);
    }

    @Test
    void nuovaRichiestaHaMisureEComuniSelezionabili() throws Exception {
        mvc.perform(get("/richieste/nuova").with(user(titolare.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Accoglienza e ascolto")))
                .andExpect(content().string(containsString("value=\"napoli\"")))
                .andExpect(content().string(not(containsString("data-form-anteprima"))));
    }

    @Test
    void dettaglioMostraDatiRealiSenzaCambiareStato() throws Exception {
        mvc.perform(get("/richieste/{id}", richiesta).with(user(titolare.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Accoglienza e ascolto")))
                .andExpect(content().string(containsString("Nucleo familiare")))
                .andExpect(content().string(containsString("Esigenza &lt;sintetica&gt;")))
                .andExpect(content().string(containsString("In programmazione")));
        assertEquals("IN_PROGRAMMAZIONE", jdbc.queryForObject("select stato from richieste where id = ?", String.class, richiesta));
    }

    @Test
    void optEstraneaNonRiceveIlDettaglio() throws Exception {
        mvc.perform(get("/richieste/{id}", richiesta).with(user(estraneo.toString()).roles("OPT")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("Esigenza"))));
    }

    @Test
    void accordoRemotoMostraIlLinkSenzaCampiDelReferenteInPresenza() throws Exception {
        jdbc.update("update richieste set stato = 'CONFERMATA', accordo_modalita = 'A_DISTANZA', accordo_data_ora = now(), accordo_link = 'https://example.test/incontro' where id = ?", richiesta);
        mvc.perform(get("/richieste/{id}", richiesta).with(user(titolare.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://example.test/incontro")))
                .andExpect(content().string(not(containsString("Telefono del referente"))));
    }

    @Test
    void senzaSessioneSiTornaAlLoginENuovoInvioERiservatoAlleOpt() throws Exception {
        mvc.perform(get("/richieste/{id}", richiesta)).andExpect(status().is3xxRedirection());
        jdbc.update("update utenze_account set ruolo = 'BANCO' where id = ?", estraneo);
        mvc.perform(get("/richieste/nuova").with(user(estraneo.toString()).roles("BANCO")))
                .andExpect(status().isForbidden());
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into utenze_account (id, email, password_hash, nome, ruolo, stato) values (?, ?, 'non-usata', 'OPT di prova', 'OPT', 'ATTIVO')",
                id, id + "@example.test");
        return id;
    }
}
