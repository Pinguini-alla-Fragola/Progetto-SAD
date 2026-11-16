package it.unina.picus.richieste.interno.persistenza;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.unina.picus.DatiDiProva;
import it.unina.picus.TestIntegrazionePostgres;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Criterio C1 della #92: su PostgreSQL, Flyway applica V6 a un database già migrato a V5 e
 * crea i vincoli attesi, senza toccare le tabelle precedenti.
 *
 * Ogni test parte da uno schema dedicato migrato fino a V5 e già popolato con una
 * richiesta, poi applica V6. Lo schema è separato da quello dell'applicazione, quindi la
 * prova non interferisce con gli altri test che usano lo stesso container.
 */
@TestIntegrazionePostgres
@DisplayName("#92 C1 - Migrazione V6 dei contatti di disponibilità su PostgreSQL")
@Tag("issue-51")
class MigrazioneContattiDisponibilitaTest {

    private static final String SCHEMA = "verifica_v6";
    private static final String CONTATTI = SCHEMA + ".richieste_contatti_disponibilita";

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiDiProva dati;
    private UUID richiestaPrecedente;
    private UUID erogatore;
    private List<String> colonnePrima;
    private List<String> vincoliPrima;
    private int migrazioniApplicate;

    @BeforeEach
    void migraDaV5AV6() {
        jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
        flyway("5").migrate();
        assertEquals("5", flyway("5").info().current().getVersion().getVersion());

        dati = new DatiDiProva(jdbc, SCHEMA + ".");
        UUID titolare = dati.creaOptConProfilo("OPT titolare");
        erogatore = dati.creaAccount("OPT", "OPT erogatrice");
        richiestaPrecedente = dati.creaRichiesta(titolare, "PRESA_IN_CARICO", null);
        colonnePrima = colonneDelleTabellePrecedenti();
        vincoliPrima = vincoliDelleTabellePrecedenti();

        migrazioniApplicate = flyway("6").migrate().migrationsExecuted;
    }

    @Test
    @DisplayName("V6 si applica a un database già a V5 e popolato")
    void v6ApplicataSuDatabaseGiaMigrato() {
        assertAll(
                () -> assertEquals(1, migrazioniApplicate, "deve essere applicata la sola V6"),
                () -> assertEquals("6", flyway("6").info().current().getVersion().getVersion()),
                () -> assertEquals(0, contaContatti(), "la nuova tabella nasce vuota"),
                () -> assertEquals("PRESA_IN_CARICO", dati.statoRichiesta(richiestaPrecedente)));
    }

    @Test
    @DisplayName("V6 preserva colonne e vincoli delle tabelle precedenti")
    void tabellePrecedentiInvariate() {
        assertAll(
                () -> assertEquals(colonnePrima, colonneDelleTabellePrecedenti()),
                () -> assertEquals(vincoliPrima, vincoliDelleTabellePrecedenti()));
    }

    @Test
    @DisplayName("Un contatto valido si inserisce e l'esito predefinito è IN_ATTESA")
    void esitoPredefinitoInAttesa() {
        UUID contatto = dati.creaContatto(richiestaPrecedente, erogatore, 0);

        assertEquals("IN_ATTESA", dati.esitoContatto(contatto));
    }

    @Test
    @DisplayName("Chiave primaria: lo stesso identificativo non si inserisce due volte")
    void chiavePrimaria() {
        UUID contatto = dati.creaContatto(richiestaPrecedente, erogatore, 0);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> inserisci(contatto, richiestaPrecedente, erogatore, "STESSO_COMUNE", "IN_ATTESA"));
        assertEquals(1, contaContatti());
    }

    @Test
    @DisplayName("Chiave esterna: il contatto deve riferirsi a una richiesta esistente")
    void chiaveEsternaVersoLaRichiesta() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> inserisci(UUID.randomUUID(), UUID.randomUUID(), erogatore, "STESSO_COMUNE", "IN_ATTESA"));
        assertEquals(0, contaContatti());
    }

    @Test
    @DisplayName("Chiave esterna: il contatto deve riferirsi a un account esistente")
    void chiaveEsternaVersoLErogatore() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> inserisci(
                        UUID.randomUUID(), richiestaPrecedente, UUID.randomUUID(), "STESSO_COMUNE", "IN_ATTESA"));
        assertEquals(0, contaContatti());
    }

    @ParameterizedTest(name = "Ambito ammesso: {0}")
    @ValueSource(strings = {"STESSO_COMUNE", "COMUNE_LIMITROFO", "CENTRO_PICUS_A_DISTANZA"})
    void ambitiAmmessi(String ambito) {
        inserisci(UUID.randomUUID(), richiestaPrecedente, erogatore, ambito, "IN_ATTESA");

        assertEquals(1, contaContatti());
    }

    @ParameterizedTest(name = "Esito ammesso: {0}")
    @ValueSource(strings = {"IN_ATTESA", "DISPONIBILE", "NON_DISPONIBILE"})
    void esitiAmmessi(String esito) {
        inserisci(UUID.randomUUID(), richiestaPrecedente, erogatore, "STESSO_COMUNE", esito);

        assertEquals(1, contaContatti());
    }

    @Test
    @DisplayName("Vincoli di dominio: ambito ed esito fuori elenco sono rifiutati")
    void valoriFuoriElencoRifiutati() {
        assertAll(
                () -> assertThrows(
                        DataIntegrityViolationException.class,
                        () -> inserisci(
                                UUID.randomUUID(), richiestaPrecedente, erogatore, "ALTRA_REGIONE", "IN_ATTESA")),
                () -> assertThrows(
                        DataIntegrityViolationException.class,
                        () -> inserisci(
                                UUID.randomUUID(), richiestaPrecedente, erogatore, "STESSO_COMUNE", "FORSE")));
        assertEquals(0, contaContatti());
    }

    @ParameterizedTest(name = "Colonna obbligatoria: {0}")
    @ValueSource(strings = {"richiesta_id", "erogatore_id", "ambito", "nome_misura", "inviato_il", "esito"})
    void colonneObbligatorie(String colonna) {
        UUID contatto = dati.creaContatto(richiestaPrecedente, erogatore, 0);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("update " + CONTATTI + " set " + colonna + " = null where id = ?", contatto));
    }

    @Test
    @DisplayName("Esiste l'indice per lo storico in ordine di invio")
    void indicePerLoStorico() {
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from pg_indexes where schemaname = ? and indexname = 'ix_contatti_richiesta_invio'",
                        Integer.class, SCHEMA));
    }

    // ------------------------------------------------------------------ supporto

    /** Flyway sullo schema di verifica, con una connessione propria e le stesse migrazioni dell'applicazione. */
    private Flyway flyway(String versioneObiettivo) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(SCHEMA)
                .locations("classpath:db/migration", "classpath:db/postgresql")
                .target(versioneObiettivo)
                .load();
    }

    private void inserisci(UUID id, UUID richiestaId, UUID erogatoreId, String ambito, String esito) {
        jdbc.update(
                "insert into " + CONTATTI + " (id, richiesta_id, erogatore_id, ambito, nome_misura, inviato_il, esito)"
                        + " values (?, ?, ?, ?, 'Accoglienza e ascolto', now(), ?)",
                id, richiestaId, erogatoreId, ambito, esito);
    }

    private int contaContatti() {
        return jdbc.queryForObject("select count(*) from " + CONTATTI, Integer.class);
    }

    private List<String> colonneDelleTabellePrecedenti() {
        return jdbc.queryForList(
                """
                select table_name || '.' || column_name || ' ' || data_type || ' nullable=' || is_nullable
                from information_schema.columns
                where table_schema = ?
                  and table_name not in ('richieste_contatti_disponibilita', 'flyway_schema_history')
                order by 1
                """,
                String.class, SCHEMA);
    }

    private List<String> vincoliDelleTabellePrecedenti() {
        return jdbc.queryForList(
                """
                select table_name || ' ' || constraint_type || ' ' || constraint_name
                from information_schema.table_constraints
                where table_schema = ?
                  and table_name not in ('richieste_contatti_disponibilita', 'flyway_schema_history')
                order by 1
                """,
                String.class, SCHEMA);
    }
}
