package it.unina.picus.utenze.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

@IntegrazionePostgresql
@Transactional
@DisplayName("#182 Rubrica aggiornata per il Banco senza esposizione di email")
class RubricaBancoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private UUID banco;
    private UUID op1;
    private UUID op2;
    private UUID opt;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        banco = dati.banco("Banco rubrica");
        op1 = dati.operatore("Mario Rossi", "INTERNO_BANCO", "napoli", false);
        op2 = dati.operatore("Mario Rossi", "ESTERNO_PAGATO_BANCO", "napoli", false);
        dati.impostaStatoAccount(op2, "REVOCATO");
        opt = dati.opt("OPT Rubrica", "napoli");
    }

    @Test
    @DisplayName("Il Banco legge la rubrica aggiornata con omonimi e revocati ma senza email")
    void bancoLeggeRubricaAggiornataSenzaEmail() throws Exception {
        String risposta = mvc.perform(get("/api/utenze/rubrica")
                        .param("ruolo", "OPERATORE")
                        .with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + op1 + "')].nome").value("Mario Rossi"))
                .andExpect(jsonPath("$[?(@.id == '" + op2 + "')].stato").value("REVOCATO"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(risposta).doesNotContain("email");
        assertThat(risposta).doesNotContain("@");

        UUID nuovo = dati.operatore("Nuovo Operatore Live", "INTERNO_BANCO", "napoli", false);
        mvc.perform(post("/api/monitoraggio/schede-operatore")
                        .param("anno", "2025").param("trimestre", "1")
                        .with(user(nuovo.toString()).roles("OPERATORE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ore":6,"costoOrarioLordo":20.00,"conteggi":{"donne":9223372036854775807}}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/monitoraggio/schede-operatore/ricevute/riepilogo")
                        .param("anno", "2025").param("trimestre", "1")
                        .with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schede[0].operatoreId").value(nuovo.toString()))
                .andExpect(jsonPath("$.conteggi.donne").value("9223372036854775807"));

        mvc.perform(get("/api/utenze/rubrica")
                        .param("ruolo", "OPERATORE")
                        .with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + nuovo + "')].nome").value("Nuovo Operatore Live"));

        mvc.perform(get("/api/utenze/rubrica")
                        .param("ruolo", "OPT")
                        .with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + opt + "')].nome").value("OPT Rubrica"));
    }

    @Test
    @DisplayName("Ruolo rubrica non valido risponde 400 e gli altri ruoli ricevono 403/401")
    void validazioneRuoloEAccessiNegati() throws Exception {
        mvc.perform(get("/api/utenze/rubrica")
                        .param("ruolo", "BANCO")
                        .with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.ruolo").isString());

        mvc.perform(get("/api/utenze/rubrica")
                        .param("ruolo", "OPERATORE")
                        .with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/utenze/rubrica")
                        .param("ruolo", "OPERATORE")
                        .with(user(op1.toString()).roles("OPERATORE")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/utenze/rubrica").param("ruolo", "OPERATORE"))
                .andExpect(status().isUnauthorized());

        mvc.perform(head("/api/utenze/rubrica").with(user(opt.toString()).roles("OPT")))
                .andExpect(status().isForbidden());
    }
}
