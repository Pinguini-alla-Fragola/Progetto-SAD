package it.unina.picus.richieste.interno.persistenza;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.PostgresqlDiProva;

/**
 * Criterio 1 della #92: su PostgreSQL, Flyway applica V6 a un database già migrato a V5 e
 * crea i vincoli attesi, senza toccare le tabelle precedenti.
 *
 * <p>Ogni test crea un database vuoto nel container, lo porta a V5 con le stesse location di
 * {@code application.yml}, inserisce una richiesta con {@link DatiSintetici}, fotografa lo
 * schema esistente e la riga della richiesta, poi applica V6. La fotografia dello schema
 * confronta, prima e dopo V6, tipo completo, obbligatorietà e valore predefinito delle colonne,
 * definizione dei vincoli e degli indici delle tabelle precedenti; quella della riga confronta
 * tutti i valori della richiesta.</p>
 */
@Testcontainers
@DisplayName("#92 C1 - Migrazione V6 dei contatti di disponibilità su PostgreSQL")
@Tag("issue-51")
class MigrazioneContattiDisponibilitaTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresqlDiProva.IMMAGINE);

    private static final String CONTATTI = "richieste_contatti_disponibilita";

    private String url;
    private JdbcTemplate jdbc;
    private DatiSintetici dati;
    private UUID titolare;
    private long misuraId;
    private UUID richiestaPrecedente;
    private Map<String, Object> richiestaPrima;
    private UUID erogatore;
    private List<String> colonnePrima;
    private List<String> vincoliPrima;
    private List<String> indiciPrima;
    private int migrazioniApplicate;

    @BeforeEach
    void migraDaV5AV6() {
        String nome = "verifica_v6_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(sorgente(POSTGRES.getJdbcUrl())).execute("create database " + nome);
        url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nome;
        jdbc = new JdbcTemplate(sorgente(url));

        flyway("5").migrate();
        assertEquals("5", flyway("5").info().current().getVersion().getVersion());

        dati = new DatiSintetici(jdbc);
        titolare = dati.opt("OPT titolare", "napoli");
        erogatore = dati.opt("OPT erogatrice", "napoli");
        misuraId = dati.misura("M01");
        richiestaPrecedente = dati.richiesta(titolare, misuraId, "napoli", "PRESA_IN_CARICO",
                Instant.parse("2026-10-01T09:00:00Z"));
        richiestaPrima = rigaDellaRichiestaPrecedente();
        colonnePrima = colonneDelleTabellePrecedenti();
        vincoliPrima = vincoliDelleTabellePrecedenti();
        indiciPrima = indiciDelleTabellePrecedenti();

        migrazioniApplicate = flyway("6").migrate().migrationsExecuted;
    }

    @Test
    @DisplayName("V6 si applica a un database già a V5 e popolato")
    void v6ApplicataSuDatabaseGiaMigrato() {
        assertAll(
                () -> assertEquals(1, migrazioniApplicate, "deve essere applicata la sola V6"),
                () -> assertEquals("6", flyway("6").info().current().getVersion().getVersion()),
                () -> assertEquals(0, contaContatti(), "la nuova tabella nasce vuota"),
                () -> assertEquals("PRESA_IN_CARICO", dati.stato(richiestaPrecedente)));
    }

    @Test
    @DisplayName("V6 conserva tutti i valori delle richieste già presenti, compresi titolare, misura e descrizione")
    void datiDelleRichiestePrecedentiConservati() {
        Object descrizione = richiestaPrima.get("description");
        assertAll("richiesta inserita a V5",
                () -> assertEquals(titolare, richiestaPrima.get("titolare_id")),
                () -> assertEquals(misuraId, richiestaPrima.get("misura_id")),
                () -> assertTrue(descrizione instanceof String testo && !testo.isBlank(), "descrizione non vuota"));

        assertEquals(richiestaPrima, rigaDellaRichiestaPrecedente(), "valori della richiesta dopo V6");
    }

    @Test
    @DisplayName("V6 lascia invariate le definizioni di colonne, vincoli e indici delle tabelle precedenti")
    void tabellePrecedentiInvariate() {
        assertAll(
                () -> assertEquals(colonnePrima, colonneDelleTabellePrecedenti(), "colonne"),
                () -> assertEquals(vincoliPrima, vincoliDelleTabellePrecedenti(), "vincoli"),
                () -> assertEquals(indiciPrima, indiciDelleTabellePrecedenti(), "indici"));
    }

    @Test
    @DisplayName("Un contatto valido si inserisce e l'esito predefinito è IN_ATTESA")
    void esitoPredefinitoInAttesa() {
        UUID contatto = UUID.randomUUID();
        jdbc.update("insert into " + CONTATTI + " (id, richiesta_id, erogatore_id, ambito, nome_misura, inviato_il)"
                        + " values (?, ?, ?, 'STESSO_COMUNE', 'Accoglienza e ascolto', now())",
                contatto, richiestaPrecedente, erogatore);

        assertEquals("IN_ATTESA", dati.esitoContatto(contatto));
    }

    @Test
    @DisplayName("Chiave primaria: lo stesso identificativo non si inserisce due volte")
    void chiavePrimaria() {
        UUID contatto = inserisci(UUID.randomUUID(), richiestaPrecedente, erogatore, "STESSO_COMUNE", "IN_ATTESA");

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
        UUID contatto = inserisci(UUID.randomUUID(), richiestaPrecedente, erogatore, "STESSO_COMUNE", "IN_ATTESA");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("update " + CONTATTI + " set " + colonna + " = null where id = ?", contatto));
    }

    @Test
    @DisplayName("L'indice per lo storico è sui contatti, con chiave richiesta_id, inviato_il e id in quest'ordine")
    void indicePerLoStorico() {
        List<String> indice = jdbc.queryForList("""
                select t.relname || ' (' || (select string_agg(pg_get_indexdef(ix.indexrelid, k, true), ', ' order by k)
                                             from generate_series(1, ix.indnkeyatts) as k)
                       || ')' || case when ix.indisunique then ' unique' else '' end
                       || coalesce(' where ' || pg_get_expr(ix.indpred, ix.indrelid), '')
                from pg_index ix
                join pg_class i on i.oid = ix.indexrelid
                join pg_class t on t.oid = ix.indrelid
                join pg_namespace n on n.oid = i.relnamespace
                where n.nspname = current_schema()
                  and i.relname = 'ix_contatti_richiesta_invio'
                """, String.class);

        assertEquals(List.of(CONTATTI + " (richiesta_id, inviato_il, id)"), indice);
    }

    // ------------------------------------------------------------------ supporto

    /** Flyway sul database del test, con le stesse location e lo stesso preflight dell'applicazione. */
    private Flyway flyway(String versioneObiettivo) {
        return Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/postgresql")
                .callbacks(new PreflightMigrazioniRichieste())
                .target(versioneObiettivo)
                .load();
    }

    private DriverManagerDataSource sorgente(String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private UUID inserisci(UUID id, UUID richiestaId, UUID erogatoreId, String ambito, String esito) {
        jdbc.update(
                "insert into " + CONTATTI + " (id, richiesta_id, erogatore_id, ambito, nome_misura, inviato_il, esito)"
                        + " values (?, ?, ?, ?, 'Accoglienza e ascolto', now(), ?)",
                id, richiestaId, erogatoreId, ambito, esito);
        return id;
    }

    /** Tutte le colonne della richiesta inserita a V5, lette con i tipi restituiti dal driver. */
    private Map<String, Object> rigaDellaRichiestaPrecedente() {
        return jdbc.queryForMap("select * from richieste where id = ?", richiestaPrecedente);
    }

    private int contaContatti() {
        return jdbc.queryForObject("select count(*) from " + CONTATTI, Integer.class);
    }

    /*
     * Fotografia delle tabelle precedenti: esclude solo la tabella creata da V6 e lo storico di
     * Flyway, quindi rileva anche una tabella aggiunta o eliminata.
     */

    /** Tipo completo, obbligatorietà, valore predefinito, identity e colonne generate. */
    private List<String> colonneDelleTabellePrecedenti() {
        return jdbc.queryForList("""
                select c.relname || '.' || a.attname
                       || ' ' || format_type(a.atttypid, a.atttypmod)
                       || case when a.attnotnull then ' not null' else ' null' end
                       || coalesce(' default ' || pg_get_expr(d.adbin, d.adrelid), '')
                       || case when a.attidentity <> '' then ' identity ' || a.attidentity::text else '' end
                       || case when a.attgenerated <> '' then ' generated ' || a.attgenerated::text else '' end
                from pg_attribute a
                join pg_class c on c.oid = a.attrelid
                join pg_namespace n on n.oid = c.relnamespace
                left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
                where n.nspname = current_schema()
                  and c.relkind in ('r', 'p')
                  and a.attnum > 0
                  and not a.attisdropped
                  and c.relname not in ('richieste_contatti_disponibilita', 'flyway_schema_history')
                order by 1
                """, String.class);
    }

    /** Nome e definizione completa di chiavi, vincoli di unicità, chiavi esterne e CHECK. */
    private List<String> vincoliDelleTabellePrecedenti() {
        return jdbc.queryForList("""
                select c.relname || ' ' || co.conname || ' ' || pg_get_constraintdef(co.oid)
                from pg_constraint co
                join pg_class c on c.oid = co.conrelid
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = current_schema()
                  and c.relname not in ('richieste_contatti_disponibilita', 'flyway_schema_history')
                order by 1
                """, String.class);
    }

    private List<String> indiciDelleTabellePrecedenti() {
        return jdbc.queryForList("""
                select tablename || ' ' || indexname || ' ' || indexdef
                from pg_indexes
                where schemaname = current_schema()
                  and tablename not in ('richieste_contatti_disponibilita', 'flyway_schema_history')
                order by 1
                """, String.class);
    }
}
