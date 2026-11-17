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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.http.MediaType;

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
                insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario, description, inviata_il, stato, erogatore_id, erogatore_ambito)
                select ?, ?, id, 'napoli', 'NUCLEO', 'Esigenza <sintetica>', now(), 'IN_PROGRAMMAZIONE', ?, 'STESSO_COMUNE'
                from catalogo_misure where codice = 'M01'
                """, richiesta, titolare, account());
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
    void codaBancoEsponeNomeOptEComuneSenzaModificareIlContrattoApplicativo() throws Exception {
        jdbc.update("update utenze_account set ruolo = 'BANCO' where id = ?", estraneo);
        mvc.perform(get("/banco/richieste/coda").accept(MediaType.APPLICATION_JSON)
                .with(user(estraneo.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].richiesta.id").value(richiesta.toString()))
                .andExpect(jsonPath("$[0].optRichiedente").value("OPT di prova"))
                .andExpect(jsonPath("$[0].comune").value("Napoli"));
        mvc.perform(get("/banco/richieste/coda").accept(MediaType.APPLICATION_JSON)
                .with(user(titolare.toString()).roles("OPT"))).andExpect(status().isForbidden());
        mvc.perform(get("/banco/richieste/coda").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dettaglioBancoIdentificaLaOptEDistingueRiepilogoBisognoECoordinamento() throws Exception {
        jdbc.update("update utenze_account set ruolo = 'BANCO' where id = ?", estraneo);
        mvc.perform(get("/richieste/{id}", richiesta).with(user(estraneo.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OPT richiedente")))
                .andExpect(content().string(containsString("OPT di prova")))
                .andExpect(content().string(containsString("Bisogno e contesto")))
                .andExpect(content().string(containsString("Riferimenti della pratica")));
    }

    @Test
    void accordoRemotoMostraIlLinkSenzaCampiDelReferenteInPresenza() throws Exception {
        jdbc.update("update richieste set stato = 'CONFERMATA', erogatore_ambito = 'CENTRO_PICUS_A_DISTANZA', accordo_modalita = 'A_DISTANZA', accordo_data_ora = now(), accordo_link = 'https://example.test/incontro' where id = ?", richiesta);
        mvc.perform(get("/richieste/{id}", richiesta).with(user(titolare.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://example.test/incontro")))
                .andExpect(content().string(not(containsString("Telefono del referente"))));
    }

    @Test
    void soloBancoInProgrammazionePuoCompilareAccordoERifiuto() throws Exception {
        jdbc.update("update utenze_account set ruolo = 'BANCO' where id = ?", estraneo);
        mvc.perform(get("/richieste/{id}", richiesta).with(user(estraneo.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"form-accordo\"")))
                .andExpect(content().string(containsString("id=\"rifiuta-richiesta\"")))
                .andExpect(content().string(containsString("errore-telefonoReferente")));
        mvc.perform(get("/richieste/{id}", richiesta).with(user(titolare.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"form-accordo\""))))
                .andExpect(content().string(not(containsString("id=\"rifiuta-richiesta\""))));
    }

    @Test
    void accordoConfermatoEConsultabileMaNonModificabile() throws Exception {
        jdbc.update("update utenze_account set ruolo = 'BANCO' where id = ?", estraneo);
        jdbc.update("""
                update richieste set stato = 'CONFERMATA', accordo_modalita = 'IN_PRESENZA',
                accordo_data_ora = now(), accordo_luogo = 'Sede di prova', accordo_nome_referente = 'Nome',
                accordo_cognome_referente = 'Referente', accordo_telefono_referente = '+39 081 0000000' where id = ?
                """, richiesta);
        mvc.perform(get("/richieste/{id}", richiesta).with(user(estraneo.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sede di prova")))
                .andExpect(content().string(containsString("Nome Referente")))
                .andExpect(content().string(not(containsString("id=\"form-accordo\""))))
                .andExpect(content().string(not(containsString("id=\"rifiuta-richiesta\""))));
        var erogatore = jdbc.queryForObject("select erogatore_id from richieste where id = ?", UUID.class, richiesta);
        mvc.perform(get("/richieste/{id}", richiesta).with(user(erogatore.toString()).roles("OPT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sede di prova")))
                .andExpect(content().string(not(containsString("Esigenza"))))
                .andExpect(content().string(not(containsString("id=\"form-accordo\""))));
    }

    @Test
    void senzaSessioneSiTornaAlLoginENuovoInvioERiservatoAlleOpt() throws Exception {
        mvc.perform(get("/richieste/{id}", richiesta)).andExpect(status().is3xxRedirection());
        jdbc.update("update utenze_account set ruolo = 'BANCO' where id = ?", estraneo);
        mvc.perform(get("/richieste/nuova").with(user(estraneo.toString()).roles("BANCO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void praticaInesistenteHaUnaPagina404MaLeApiConservanoLErroreJson() throws Exception {
        UUID assente = UUID.randomUUID();
        mvc.perform(get("/richieste/{id}", assente).accept(MediaType.TEXT_HTML)
                .with(user(titolare.toString()).roles("OPT")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Pagina non trovata")));
        mvc.perform(get("/api/richieste/{id}", assente).accept(MediaType.APPLICATION_JSON)
                .with(user(titolare.toString()).roles("OPT")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codice").value("NOT_FOUND"));
    }

    @Test
    void pagina404GestisceAncheGliErroriDiPercorso() throws Exception {
        mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 404)
                .with(user(titolare.toString()).roles("OPT")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Pagina non trovata")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("method=\"post\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into utenze_account (id, email, password_hash, nome, ruolo, stato) values (?, ?, 'non-usata', 'OPT di prova', 'OPT', 'ATTIVO')",
                id, id + "@example.test");
        return id;
    }
}
