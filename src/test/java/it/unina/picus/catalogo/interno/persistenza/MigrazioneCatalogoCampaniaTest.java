package it.unina.picus.catalogo.interno.persistenza;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import it.unina.picus.shared.PostgresqlDiProva;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifica l'installazione pulita e l'aggiornamento da V26 di {@code V27__catalogo_comuni_campania.sql}
 * su PostgreSQL 17 reale: unicità dei codici ISTAT, integrità dei riferimenti esistenti,
 * corrispondenza catalogo-geometrie ISTAT 2026 (compresi multipoligoni) e regole di adiacenza.
 */
@Testcontainers
@DisplayName("Migrazione V27 del catalogo territoriale della Campania su PostgreSQL")
class MigrazioneCatalogoCampaniaTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresqlDiProva.IMMAGINE);

    private static final Instant ORA = Instant.parse("2026-11-20T10:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private String url;
    private JdbcTemplate jdbc;

    @BeforeEach
    void nuovoDatabase() {
        String nome = "picus_campania_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(sorgente(POSTGRES.getJdbcUrl())).execute("create database " + nome);
        url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nome;
        jdbc = new JdbcTemplate(sorgente(url));
    }

    @Test
    @DisplayName("Installazione su PostgreSQL vuoto: 550 comuni ISTAT univoci, 1508 adiacenze e corrispondenza 1-a-1 con GeoJSON")
    void installazioneSuDatabaseVuotoECorrispondenzaGeometrie() throws Exception {
        var esito = flyway(null).migrate();
        assertThat(esito.success).isTrue();
        assertThat(flyway(null).info().pending()).isEmpty();

        List<Map<String, Object>> righe = jdbc.queryForList("""
                select codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana
                from catalogo_comuni
                order by codice_istat
                """);
        assertThat(righe).hasSize(550);
        assertThat(righe).allSatisfy(r -> {
            String codiceIstat = (String) r.get("codice_istat");
            assertThat(codiceIstat).matches("^[0-9]{6}$");
            assertThat(codiceIstat).startsWith("06");
            assertThat((String) r.get("provincia")).isNotBlank();
            assertThat((String) r.get("sigla_provincia")).matches("^[A-Z]{2}$");
        });

        assertThat(conta("select count(distinct codice_istat) from catalogo_comuni")).isEqualTo(550);
        assertThat(conta("select count(*) from catalogo_comuni where nome = 'Napoli'")).isEqualTo(1);
        assertThat(conta("select count(*) from catalogo_comuni where nome = 'Portici'")).isEqualTo(1);
        assertThat(conta("select count(*) from catalogo_comuni where nome = 'Pozzuoli'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select codice_istat from catalogo_comuni where codice = 'napoli'", String.class))
                .isEqualTo("063049");

        // Adiacenze canoniche, reciproche e regole su isole e contatti in un solo punto.
        assertThat(conta("select count(*) from catalogo_adiacenze")).isEqualTo(1508);
        assertThat(conta("select count(*) from catalogo_adiacenze where comune_a >= comune_b")).isZero();
        assertThat(adiacenti("napoli")).contains("portici", "pozzuoli");
        assertThat(adiacenti("portici")).contains("napoli");
        assertThat(adiacenti("063061")).as("Procida: isola senza confini terrestri condivisi").isEmpty();
        assertThat(adiacenti("063014")).as("Capri confina via terra solo con Anacapri").containsExactly("063004");
        assertThat(adiacenti("063058")).as("Pompei e Sant'Antonio Abate: solo contatto puntiforme escluso")
                .doesNotContain("063074");
        assertThat(adiacenti("061022")).as("Caserta e Recale: solo contatto puntiforme escluso")
                .doesNotContain("061067");

        // Corrispondenza esatta 1-a-1 tra catalogo DB e GeoJSON statico pubblicato.
        Path geojsonPath = Path.of("src/main/resources/static/dati/comuni-campania-istat-2026.json");
        JsonNode radice = JSON.readTree(Files.readString(geojsonPath));
        JsonNode features = radice.get("features");
        assertThat(features.size()).isEqualTo(550);

        Map<String, Map<String, Object>> daDb = new HashMap<>();
        for (Map<String, Object> r : righe) {
            daDb.put((String) r.get("codice_istat"), r);
        }
        Set<String> codiciGeo = new java.util.HashSet<>();
        for (JsonNode f : features) {
            JsonNode props = f.get("properties");
            String istat = props.get("codiceIstat").asText();
            assertThat(codiciGeo.add(istat)).as("duplicato nel GeoJSON: %s", istat).isTrue();
            Map<String, Object> db = daDb.get(istat);
            assertThat(db).as("comune ISTAT presente in DB: %s", istat).isNotNull();
            assertThat(props.get("codiceCatalogo").asText()).isEqualTo(db.get("codice"));
            assertThat(props.get("nome").asText()).isEqualTo(db.get("nome"));
            assertThat(props.get("provincia").asText()).isEqualTo(db.get("provincia"));
            assertThat(props.get("siglaProvincia").asText()).isEqualTo(db.get("sigla_provincia"));
            assertThat(props.get("cittaMetropolitana").asBoolean()).isEqualTo(db.get("citta_metropolitana"));
            JsonNode geom = f.get("geometry");
            assertThat(geom.get("type").asText()).isIn("Polygon", "MultiPolygon");
            assertThat(geom.get("coordinates").size()).isPositive();
            if ("063049".equals(istat) || "063014".equals(istat) || "065146".equals(istat)) {
                assertThat(geom.get("type").asText()).as("comune multi-parte %s", istat).isEqualTo("MultiPolygon");
            }
        }
        assertThat(codiciGeo).isEqualTo(daDb.keySet());
    }

    @Test
    @DisplayName("Aggiornamento da schema V26 già popolato: conserva account, richieste e profili collegando i codici storici")
    void aggiornamentoDaSchemaV26Popolato() {
        flyway("26").migrate();
        long m01 = jdbc.queryForObject("select id from catalogo_misure where codice = 'M01'", Long.class);
        UUID optNapoli = inserisciOpt("OPT Napoli", "napoli");
        UUID opPortici = inserisciOperatore("Operatore Portici", "portici");
        UUID opPozzuoli = inserisciOperatore("Operatore Pozzuoli", "pozzuoli");
        UUID richiestaNapoli = inserisciRichiesta(optNapoli, m01, "napoli", opPortici);
        UUID richiestaPortici = inserisciRichiesta(optNapoli, m01, "portici", opPozzuoli);

        var esito = flyway(null).migrate();
        assertThat(esito.success).isTrue();
        assertThat(flyway(null).info().pending()).isEmpty();

        assertThat(conta("select count(*) from catalogo_comuni")).isEqualTo(550);
        assertThat(jdbc.queryForObject("""
                select c.codice_istat from richieste r
                join catalogo_comuni c on c.codice = r.comune_codice
                where r.id = ?
                """, String.class, richiestaNapoli)).isEqualTo("063049");
        assertThat(jdbc.queryForObject("""
                select c.codice_istat from richieste r
                join catalogo_comuni c on c.codice = r.comune_codice
                where r.id = ?
                """, String.class, richiestaPortici)).isEqualTo("063059");
        assertThat(jdbc.queryForObject("""
                select c.codice_istat from utenze_profili_opt p
                join catalogo_comuni c on c.codice = p.comune_codice
                where p.account_id = ?
                """, String.class, optNapoli)).isEqualTo("063049");
        assertThat(jdbc.queryForObject("""
                select c.codice_istat from utenze_profili_operatore p
                join catalogo_comuni c on c.codice = p.comune_codice
                where p.account_id = ?
                """, String.class, opPozzuoli)).isEqualTo("063060");

        // Nuova richiesta su un comune campano aggiunto da V27 (Aversa 061005).
        UUID richiestaAversa = inserisciRichiesta(optNapoli, m01, "061005", null);
        assertThat(jdbc.queryForObject("""
                select c.nome from richieste r
                join catalogo_comuni c on c.codice = r.comune_codice
                where r.id = ?
                """, String.class, richiestaAversa)).isEqualTo("Aversa");
    }

    private Flyway flyway(String versione) {
        var config = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/postgresql");
        if (versione != null) {
            config.target(versione);
        }
        return config.load();
    }

    private DriverManagerDataSource sorgente(String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private int conta(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private List<String> adiacenti(String codice) {
        return jdbc.queryForList("""
                select case when comune_a = ? then comune_b else comune_a end as vicino
                from catalogo_adiacenze
                where comune_a = ? or comune_b = ?
                order by vicino
                """, String.class, codice, codice, codice);
    }

    private UUID inserisciOpt(String nome, String comune) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, 'hash', ?, 'OPT', 'ATTIVO')
                """, id, id + "@picus.test", nome);
        jdbc.update("""
                insert into utenze_profili_opt (account_id, codice_struttura, comune_codice)
                values (?, ?, ?)
                """, id, "STR-" + id.toString().substring(0, 8), comune);
        return id;
    }

    private UUID inserisciOperatore(String nome, String comune) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, 'hash', ?, 'OPERATORE', 'ATTIVO')
                """, id, id + "@picus.test", nome);
        jdbc.update("""
                insert into utenze_profili_operatore (account_id, tipologia, comune_codice)
                values (?, 'INTERNO_BANCO', ?)
                """, id, comune);
        return id;
    }

    private UUID inserisciRichiesta(UUID titolare, long misura, String comune, UUID erogatore) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario,
                                       inviata_il, stato, erogatore_id)
                values (?, ?, ?, ?, 'PERSONA', ?, ?, ?)
                """, id, titolare, misura, comune, Timestamp.from(ORA),
                erogatore == null ? "CREATA" : "IN_PROGRAMMAZIONE", erogatore);
        return id;
    }
}
