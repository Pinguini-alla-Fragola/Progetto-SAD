package it.unina.picus.comunicazioni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
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

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

@IntegrazionePostgresql
class ConfermaRicezioneControllerTest {

    private static final String PERCORSO = "/api/comunicazioni";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID destinatario;
    private UUID estraneo;
    private UUID banco;
    private UUID comunicazione;

    @BeforeEach
    void inviaComunicazione() throws Exception {
        var dati = new DatiSintetici(jdbc);
        destinatario = dati.opt("OPT destinataria sintetica", "napoli");
        estraneo = dati.opt("OPT estranea sintetica", "napoli");
        banco = dati.banco("Banco sintetico");

        var risposta = mvc.perform(post(PERCORSO).with(user(banco.toString()).roles("BANCO")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"contenuto":"Messaggio sintetico","destinatariTipo":"GRUPPO_OPT","gruppo":["%s"]}
                        """.formatted(destinatario)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        comunicazione = UUID.fromString(JsonPath.read(risposta.getContentAsString(), "$.id"));
    }

    @AfterEach
    void rimuoviDatiDelCaso() {
        jdbc.update("""
                delete from comunicazioni_destinatari where comunicazione_id in
                (select id from comunicazioni where mittente_id = ?)
                """, banco);
        jdbc.update("delete from comunicazioni where mittente_id = ?", banco);
        jdbc.update("delete from utenze_profili_opt where account_id in (?, ?)", destinatario, estraneo);
        jdbc.update("delete from utenze_account where id in (?, ?, ?)", destinatario, estraneo, banco);
    }

    @Test
    void negaAlloStessoModoUnaComunicazioneAltruiOAssente() throws Exception {
        String altrui = conferma(comunicazione, estraneo, "OPT").andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        String assente = conferma(UUID.randomUUID(), estraneo, "OPT").andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertThat(altrui).isEmpty();
        assertThat(assente).isEmpty();
        conferma(comunicazione, banco, "BANCO").andExpect(status().isForbidden());
        mvc.perform(post(rotta(comunicazione)).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(rotta(comunicazione)).with(user(destinatario.toString()).roles("OPT")))
                .andExpect(status().isForbidden());
        assertThat(istantePersistito()).isNull();
    }

    @Test
    void conservaIlPrimoRiscontroAncheConConfermeConcorrenti() throws Exception {
        mvc.perform(get(PERCORSO + "/da-confermare").with(user(destinatario.toString()).roles("OPT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.daConfermare").value(1));
        var pronti = new CountDownLatch(2);
        try (var richieste = Executors.newFixedThreadPool(2)) {
            var prima = richieste.submit(() -> confermaInsieme(pronti));
            var seconda = richieste.submit(() -> confermaInsieme(pronti));
            assertThat(prima.get(15, TimeUnit.SECONDS)).isEqualTo(seconda.get(15, TimeUnit.SECONDS));
        }
        var salvato = istantePersistito();
        assertThat(salvato).isNotNull();
        conferma(comunicazione, destinatario, "OPT").andExpect(status().isOk())
                .andExpect(jsonPath("$").value(salvato.toInstant().toString()));
        assertThat(istantePersistito()).isEqualTo(salvato);
        mvc.perform(get(PERCORSO + "/da-confermare").with(user(destinatario.toString()).roles("OPT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.daConfermare").value(0));
    }

    private String confermaInsieme(CountDownLatch pronti) throws Exception {
        pronti.countDown();
        assertThat(pronti.await(5, TimeUnit.SECONDS)).isTrue();
        return conferma(comunicazione, destinatario, "OPT").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions conferma(UUID messaggio, UUID attore, String ruolo) throws Exception {
        return mvc.perform(post(rotta(messaggio)).with(user(attore.toString()).roles(ruolo)).with(csrf()));
    }

    private static String rotta(UUID messaggio) {
        return PERCORSO + "/" + messaggio + "/ricezione";
    }

    private OffsetDateTime istantePersistito() {
        return jdbc.queryForObject("""
                select confermata_il from comunicazioni_destinatari
                where comunicazione_id = ? and destinatario_id = ?
                """, OffsetDateTime.class, comunicazione, destinatario);
    }
}
