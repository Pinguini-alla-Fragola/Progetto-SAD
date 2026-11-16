package it.unina.picus.richieste.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Prova minima dell'infrastruttura dei test di integrazione: PostgreSQL reale avviato con
 * Testcontainers e chiamate HTTP autenticate con MockMvc, con la catena di sicurezza attiva.
 *
 * <p>Richiede Docker in esecuzione. La matrice completa dei casi della risposta di
 * disponibilità è tracciata nella #92.</p>
 *
 * <p>Gli utenti simulati hanno come nome l'UUID di un account di prova e ogni POST porta un
 * token CSRF valido. In questo modo un {@code 403} può dipendere soltanto dal controllo sul
 * destinatario del contatto, non dalla conversione del nome in UUID né dalla protezione CSRF.
 * L'utente simulato non è un {@code PrincipalePicus}: login e revoca della sessione non sono
 * oggetto di questa prova.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Infrastruttura dei test di integrazione su PostgreSQL")
class RispostaDisponibilitaPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private UUID destinatario;
    private UUID estraneo;
    private UUID contattoId;

    /** Dati di prova: una richiesta Presa in carico con un contatto in attesa per il destinatario. */
    @BeforeEach
    void preparaContattoInAttesa() {
        UUID titolare = creaAccountOpt("OPT titolare");
        jdbc.update(
                "insert into utenze_profili_opt (account_id, codice_struttura, comune_codice) values (?, ?, 'napoli')",
                titolare, "T-" + titolare.toString().substring(0, 8));
        destinatario = creaAccountOpt("OPT destinataria");
        estraneo = creaAccountOpt("OPT estranea");

        UUID richiestaId = UUID.randomUUID();
        jdbc.update(
                """
                insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario, inviata_il, stato)
                select ?, ?, id, 'napoli', 'PERSONA', now(), 'PRESA_IN_CARICO'
                from catalogo_misure where codice = 'M01'
                """,
                richiestaId, titolare);

        contattoId = UUID.randomUUID();
        jdbc.update(
                """
                insert into richieste_contatti_disponibilita (id, richiesta_id, erogatore_id, ambito, nome_misura, inviato_il)
                values (?, ?, ?, 'STESSO_COMUNE', 'Accoglienza e ascolto', now())
                """,
                contattoId, richiestaId, destinatario);
    }

    @Test
    @DisplayName("Il datasource dei test è il PostgreSQL del container, migrato da Flyway fino a V6")
    void datasourcePostgresqlMigrato() throws SQLException {
        try (Connection connessione = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connessione.getMetaData().getDatabaseProductName());
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where version = '6' and success",
                        Integer.class));
    }

    @Test
    @DisplayName("Un altro account riceve 403 e il contatto resta in attesa; il destinatario riceve 200")
    void rispostaRiservataAlDestinatario() throws Exception {
        mvc.perform(risposta(estraneo, true))
                .andExpect(status().isForbidden());
        assertEquals("IN_ATTESA", esitoPersistito(), "il diniego non deve modificare il contatto");

        mvc.perform(risposta(destinatario, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("DISPONIBILE"));
        assertEquals("DISPONIBILE", esitoPersistito());
    }

    /** POST di risposta con utente simulato identificato dall'UUID dell'account e CSRF valido. */
    private MockHttpServletRequestBuilder risposta(UUID account, boolean disponibile) {
        return post("/api/contatti-disponibilita/{contattoId}/risposta", contattoId)
                .with(user(account.toString()).roles("OPT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"disponibile\": " + disponibile + "}");
    }

    private String esitoPersistito() {
        return jdbc.queryForObject(
                "select esito from richieste_contatti_disponibilita where id = ?", String.class, contattoId);
    }

    private UUID creaAccountOpt(String nome) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, 'accesso-non-previsto-nei-test', ?, 'OPT', 'ATTIVO')
                """,
                id, id + "@picus.test", nome);
        return id;
    }
}

