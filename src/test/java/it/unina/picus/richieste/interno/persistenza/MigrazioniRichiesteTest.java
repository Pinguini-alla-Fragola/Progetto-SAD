package it.unina.picus.richieste.interno.persistenza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import it.unina.picus.shared.PostgresqlDiProva;

/**
 * Aggiornamento di un database PostgreSQL esistente alle migrazioni del coordinamento (#49,
 * criterio 6 della #102): i dati già presenti sono conservati, i nuovi vincoli valgono dopo
 * l'upgrade e il preflight ferma l'aggiornamento davanti a uno storico incoerente senza
 * modificarlo.
 *
 * <p>Ogni test crea un database vuoto nel container, lo porta a una versione precedente con le
 * stesse location di {@code application.yml}, inserisce dati sintetici e completa l'upgrade.</p>
 */
@Testcontainers
@DisplayName("Migrazioni del coordinamento su database PostgreSQL esistente")
class MigrazioniRichiesteTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresqlDiProva.IMMAGINE);

    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-02T09:00:00Z");

    private String url;
    private JdbcTemplate jdbc;

    @BeforeEach
    void nuovoDatabase() {
        String nome = "picus_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(sorgente(POSTGRES.getJdbcUrl())).execute("create database " + nome);
        url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nome;
        jdbc = new JdbcTemplate(sorgente(url));
    }

    @Test
    @DisplayName("Da V8 (prima del coordinamento) all'ultima versione: richieste e contatti sono conservati")
    void upgradeDaV8SenzaPerditaDiDati() {
        flyway("8").migrate();
        long m01 = misura("M01");
        UUID titolare = opt("OPT titolare");
        UUID erogatore = opt("OPT erogatrice");
        UUID creata = richiesta(titolare, m01, "CREATA", null);
        UUID presa = richiesta(titolare, m01, "PRESA_IN_CARICO", null);
        UUID programmata = richiesta(titolare, m01, "IN_PROGRAMMAZIONE", erogatore);
        // Inseriti in ordine fisico inverso: l'ordine logico deve seguire l'istante di invio.
        UUID secondo = contatto(presa, erogatore, T2, "DISPONIBILE");
        UUID primo = contatto(presa, erogatore, T1, "NON_DISPONIBILE");

        var risultato = flyway(null).migrate();

        assertThat(risultato.success).isTrue();
        assertThat(flyway(null).info().pending()).isEmpty();
        assertThat(conta("select count(*) from richieste")).isEqualTo(3);
        assertThat(stato(creata)).isEqualTo("CREATA");
        assertThat(stato(presa)).isEqualTo("PRESA_IN_CARICO");
        assertThat(stato(programmata)).isEqualTo("IN_PROGRAMMAZIONE");
        assertThat(jdbc.queryForObject("select erogatore_id from richieste where id = ?", UUID.class, programmata))
                .isEqualTo(erogatore);
        assertThat(jdbc.queryForObject("select erogatore_ambito from richieste where id = ?", String.class, programmata))
                .as("le assegnazioni precedenti non ricevono un ambito inventato").isNull();
        assertThat(esito(primo)).isEqualTo("NON_DISPONIBILE");
        assertThat(esito(secondo)).isEqualTo("DISPONIBILE");
        assertThat(ordine(primo)).isLessThan(ordine(secondo));
        assertThat(conta("select count(*) from catalogo_offerte_opt")).as("nessuna offerta attribuita d'ufficio").isZero();
        assertThat(conta("select count(*) from utenze_operatori_misure")).isZero();
    }

    @Test
    @DisplayName("Da V10 con avvisi coerenti: avvisi conservati e vincoli V11 e V13 attivi dopo l'upgrade")
    void upgradeDaV10ConAvvisi() {
        flyway("10").migrate();
        long m01 = misura("M01");
        UUID titolare = opt("OPT titolare");
        UUID erogatoreA = opt("OPT A");
        UUID erogatoreB = opt("OPT B");
        UUID presa = richiesta(titolare, m01, "PRESA_IN_CARICO", null);
        UUID declinato = contatto(presa, erogatoreA, T1, "NON_DISPONIBILE");
        UUID aperto = contatto(presa, erogatoreB, T2, "IN_ATTESA");
        avviso(presa, declinato, erogatoreA);
        avviso(presa, aperto, erogatoreB);

        flyway(null).migrate();

        assertThat(conta("select count(*) from comunicazioni_avvisi_operativi")).isEqualTo(2);
        assertThat(conta("select count(*) from comunicazioni_avvisi_operativi where contatto_id = ? and destinatario_id = ?",
                aperto, erogatoreB)).isEqualTo(1);
        assertThat(esito(aperto)).isEqualTo("IN_ATTESA");
        assertThat(ordine(declinato)).isLessThan(ordine(aperto));

        assertThatThrownBy(() -> contatto(presa, erogatoreA, Instant.now(), "IN_ATTESA"))
                .as("V11: un solo contatto in attesa per richiesta")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> avviso(presa, declinato, erogatoreB))
                .as("V13: l'avviso deve corrispondere a pratica e destinatario del contatto")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Due contatti in attesa sulla stessa richiesta fermano l'upgrade prima di V11 senza modifiche")
    void preflightV11() {
        flyway("10").migrate();
        long m01 = misura("M01");
        UUID titolare = opt("OPT titolare");
        UUID erogatoreA = opt("OPT A");
        UUID erogatoreB = opt("OPT B");
        UUID presa = richiesta(titolare, m01, "PRESA_IN_CARICO", null);
        UUID primo = contatto(presa, erogatoreA, T1, "IN_ATTESA");
        UUID secondo = contatto(presa, erogatoreB, T2, "IN_ATTESA");

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .satisfies(ex -> assertThat(messaggi(ex)).contains("Preflight PICUS V11"));

        assertThat(versioneCorrente()).isEqualTo("10");
        assertThat(esito(primo)).isEqualTo("IN_ATTESA");
        assertThat(esito(secondo)).isEqualTo("IN_ATTESA");
        assertThat(stato(presa)).isEqualTo("PRESA_IN_CARICO");
    }

    @Test
    @DisplayName("Un avviso indirizzato a un account diverso dal contatto ferma l'upgrade prima di V13 senza modifiche")
    void preflightV13() {
        flyway("10").migrate();
        long m01 = misura("M01");
        UUID titolare = opt("OPT titolare");
        UUID erogatore = opt("OPT erogatrice");
        UUID estranea = opt("OPT estranea");
        UUID presa = richiesta(titolare, m01, "PRESA_IN_CARICO", null);
        UUID aperto = contatto(presa, erogatore, T1, "IN_ATTESA");
        avviso(presa, aperto, estranea);

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .satisfies(ex -> assertThat(messaggi(ex)).contains("Preflight PICUS V13"));

        assertThat(versioneCorrente()).isEqualTo("12");
        assertThat(conta("select count(*) from comunicazioni_avvisi_operativi where contatto_id = ? and destinatario_id = ?",
                aperto, estranea)).as("l'avviso storico non è eliminato né riscritto").isEqualTo(1);
        assertThat(esito(aperto)).isEqualTo("IN_ATTESA");
    }

    private Flyway flyway(String versioneObiettivo) {
        FluentConfiguration configurazione = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/postgresql")
                .callbacks(new PreflightMigrazioniRichieste());
        if (versioneObiettivo != null) {
            configurazione.target(versioneObiettivo);
        }
        return configurazione.load();
    }

    private String versioneCorrente() {
        return flyway(null).info().current().getVersion().getVersion();
    }

    private DriverManagerDataSource sorgente(String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long misura(String codice) {
        return jdbc.queryForObject("select id from catalogo_misure where codice = ?", Long.class, codice);
    }

    private UUID opt(String nome) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, 'accesso-non-previsto-nei-test', ?, 'OPT', 'ATTIVO')
                """, id, id + "@picus.test", nome);
        jdbc.update("insert into utenze_profili_opt (account_id, codice_struttura, comune_codice) values (?, ?, 'napoli')",
                id, "S-" + id.toString().replace("-", "").substring(0, 20));
        return id;
    }

    private UUID richiesta(UUID titolare, long misura, String stato, UUID erogatore) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario,
                                       inviata_il, stato, erogatore_id)
                values (?, ?, ?, 'napoli', 'NUCLEO', ?, ?, ?)
                """, id, titolare, misura, Timestamp.from(T1), stato, erogatore);
        return id;
    }

    private UUID contatto(UUID richiesta, UUID erogatore, Instant inviatoIl, String esito) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into richieste_contatti_disponibilita
                    (id, richiesta_id, erogatore_id, ambito, nome_misura, inviato_il, esito)
                values (?, ?, ?, 'STESSO_COMUNE', 'Accoglienza e ascolto', ?, ?)
                """, id, richiesta, erogatore, Timestamp.from(inviatoIl), esito);
        return id;
    }

    private void avviso(UUID richiesta, UUID contatto, UUID destinatario) {
        jdbc.update("""
                insert into comunicazioni_avvisi_operativi
                    (id, richiesta_id, contatto_id, destinatario_id, tipo, registrato_il)
                values (?, ?, ?, ?, 'RICHIESTA_DISPONIBILITA', ?)
                """, UUID.randomUUID(), richiesta, contatto, destinatario, Timestamp.from(T2));
    }

    private String stato(UUID richiesta) {
        return jdbc.queryForObject("select stato from richieste where id = ?", String.class, richiesta);
    }

    private String esito(UUID contatto) {
        return jdbc.queryForObject("select esito from richieste_contatti_disponibilita where id = ?", String.class,
                contatto);
    }

    private long ordine(UUID contatto) {
        return jdbc.queryForObject("select ordine_invio from richieste_contatti_disponibilita where id = ?", Long.class,
                contatto);
    }

    private int conta(String sql, Object... parametri) {
        return jdbc.queryForObject(sql, Integer.class, parametri);
    }

    private static String messaggi(Throwable eccezione) {
        var testo = new StringBuilder();
        for (Throwable corrente = eccezione; corrente != null; corrente = corrente.getCause()) {
            testo.append(corrente.getMessage()).append('\n');
        }
        return testo.toString();
    }
}
