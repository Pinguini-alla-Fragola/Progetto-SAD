package it.unina.picus.monitoraggio.interno.persistenza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioVersioniCategorie;
import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestreGiaPubblicataException;
import it.unina.picus.monitoraggio.interno.dominio.StatoVersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoMonitoraggio;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorieImmutabileException;
import it.unina.picus.monitoraggio.interno.dominio.VoceCategoriaVersione;
import it.unina.picus.shared.IntegrazionePostgresql;

/** Verifica persistenza versionata, immutabilità, concorrenza e upgrade V25->V26 su PostgreSQL 17. */
@IntegrazionePostgresql
@Transactional
class CategorieSchedeJpaTest {

    @Autowired
    private CategorieSchedeRendicontabili categorie;

    @Autowired
    private ArchivioVersioniCategorie versioni;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void gestisceBozzaPubblicazioneImmutabilitaERiusoPerTrimestre() {
        var vuoto = new Trimestre(2020, 1);
        assertThat(categorie.configurazionePer(vuoto)).isInstanceOf(ConfigurazioneTrimestre.NonConfigurata.class);

        Instant ora = Instant.parse("2026-01-10T10:00:00Z");
        VersioneCategorie bozza = versioni.creaBozza(
                TracciatoMonitoraggio.TM_OP_01,
                "v2020-draft",
                "Bozza iniziale",
                List.of(new VoceCategoriaVersione("X", "Categoria X", "Definizione X", 0)),
                ora);
        assertThat(bozza.stato()).isEqualTo(StatoVersioneCategorie.BOZZA);

        VersioneCategorie aggiornata = versioni.aggiornaBozza(
                bozza.id(),
                "Versione 2020 rivista",
                List.of(
                        new VoceCategoriaVersione("X", "Categoria X agg.", "Definizione X agg.", 0),
                        new VoceCategoriaVersione("Y", "Categoria Y", "Definizione Y", 1)));
        assertThat(aggiornata.codiciAmmessi()).containsExactlyInAnyOrder("X", "Y");

        var t2 = new Trimestre(2020, 2);
        var pubblicata = versioni.pubblicaPerTrimestre(bozza.id(), t2, ora.plusSeconds(60));
        assertThat(pubblicata.versione().stato()).isEqualTo(StatoVersioneCategorie.PUBBLICATA);
        assertThat(categorie.configurazionePer(t2)).isInstanceOf(ConfigurazioneTrimestre.Pubblicata.class);

        // Una versione pubblicata diventa immutabile
        assertThatThrownBy(() -> versioni.aggiornaBozza(
                bozza.id(),
                "Tentativo post pubblicazione",
                List.of(new VoceCategoriaVersione("Y", "Solo Y", "Def Y", 0))))
                .isInstanceOf(VersioneCategorieImmutabileException.class);

        // Più trimestri possono riusare la stessa versione immutabile dello stesso tracciato
        var t3Reuse = new Trimestre(2020, 3);
        versioni.pubblicaPerTrimestre(bozza.id(), t3Reuse, ora.plusSeconds(120));
        assertThat(categorie.configurazionePer(t3Reuse))
                .isInstanceOfSatisfying(ConfigurazioneTrimestre.Pubblicata.class,
                        p -> assertThat(p.versione().id()).isEqualTo(bozza.id()));

        // TM-OPT-01 resta isolato da TM-OP-01
        assertThat(versioni.configurazionePer(TracciatoMonitoraggio.TM_OPT_01, t2))
                .isInstanceOf(ConfigurazioneTrimestre.NonConfigurata.class);

        // Vincolo FK composto vieta di associare a TM-OP-01 una versione nata per TM-OPT-01
        VersioneCategorie optBozza = versioni.creaBozza(
                TracciatoMonitoraggio.TM_OPT_01,
                "opt-v1",
                "Solo OPT",
                List.of(new VoceCategoriaVersione("OPT_SOLO", "Opt", "Definizione", 0)),
                ora);
        assertThatThrownBy(() -> jdbc.update("""
                insert into monitoraggio_configurazioni_trimestre (tracciato, anno, trimestre, versione_id, assegnata_il)
                values ('TM-OP-01', 2020, 4, ?, now())
                """, optBozza.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "_prefisso", "Fuori catalogo", "spazio ", "linea\n", "caffè"})
    void vincoloRifiutaCodiceVoceFuoriFormato(String codice) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into monitoraggio_versioni_categorie (id, tracciato, codice_versione, stato, descrizione, creata_il)
                values (?, 'TM-OP-01', 'ver-test', 'BOZZA', 'Test', now())
                """, id);
        assertThatThrownBy(() -> jdbc.update("""
                insert into monitoraggio_voci_versione_categorie (versione_id, codice, descrizione, definizione, ordine)
                values (?, ?, 'Categoria', 'Definizione', 0)
                """, id, codice))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, errore -> {
                    assertThat(errore.getSQLState()).isEqualTo("23514");
                    assertThat(errore.getMessage()).contains("chk_mon_voce_cat_codice");
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pubblicazioneConcorrenteSulloStessoTrimestreLasciaUnaSolaAssociazione() throws Exception {
        Instant ora = Instant.parse("2026-02-01T10:00:00Z");
        Trimestre conteso = new Trimestre(2021, 1);
        VersioneCategorie v1 = versioni.creaBozza(
                TracciatoMonitoraggio.TM_OP_01,
                "concorrente-1-" + UUID.randomUUID().toString().substring(0, 8),
                "Versione 1",
                List.of(new VoceCategoriaVersione("X", "Cat X", "Def X", 0)),
                ora);
        VersioneCategorie v2 = versioni.creaBozza(
                TracciatoMonitoraggio.TM_OP_01,
                "concorrente-2-" + UUID.randomUUID().toString().substring(0, 8),
                "Versione 2",
                List.of(new VoceCategoriaVersione("Y", "Cat Y", "Def Y", 0)),
                ora);

        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch pronti = new CountDownLatch(2);
            CountDownLatch via = new CountDownLatch(1);
            Callable<Boolean> task1 = () -> {
                pronti.countDown();
                via.await(5, TimeUnit.SECONDS);
                return provaPubblicazione(v1.id(), conteso, ora.plusSeconds(10));
            };
            Callable<Boolean> task2 = () -> {
                pronti.countDown();
                via.await(5, TimeUnit.SECONDS);
                return provaPubblicazione(v2.id(), conteso, ora.plusSeconds(10));
            };
            Future<Boolean> f1 = pool.submit(task1);
            Future<Boolean> f2 = pool.submit(task2);
            assertThat(pronti.await(5, TimeUnit.SECONDS)).isTrue();
            via.countDown();

            boolean esito1 = f1.get(10, TimeUnit.SECONDS);
            boolean esito2 = f2.get(10, TimeUnit.SECONDS);
            assertThat(esito1 ^ esito2).as("esattamente una pubblicazione deve vincere").isTrue();

            Integer righe = jdbc.queryForObject("""
                    select count(*) from monitoraggio_configurazioni_trimestre
                    where tracciato = 'TM-OP-01' and anno = 2021 and trimestre = 1
                    """, Integer.class);
            assertThat(righe).isEqualTo(1);
            assertThat(categorie.configurazionePer(conteso))
                    .isInstanceOf(ConfigurazioneTrimestre.Pubblicata.class);
        } finally {
            jdbc.update("delete from monitoraggio_configurazioni_trimestre where tracciato = 'TM-OP-01' and anno = 2021 and trimestre = 1");
            jdbc.update("delete from monitoraggio_versioni_categorie where id in (?, ?)", v1.id(), v2.id());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void upgradeV25AV26AssociaTrimestriV01EsistentiPreservaConteggiENonRendePermissiviNuoviPeriodi() {
        String schemaUpgrade = "upgrade_v26_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try {
            jdbc.execute("create schema " + schemaUpgrade);
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schemaUpgrade)
                    .locations("classpath:db/migration", "classpath:db/postgresql")
                    .target("25")
                    .load()
                    .migrate();

            UUID operatoreId = UUID.randomUUID();
            UUID schedaId = UUID.randomUUID();
            jdbc.update("""
                    insert into %s.utenze_account (id, email, password_hash, nome, ruolo, stato)
                    values (?, ?, 'accesso-non-previsto-nei-test', 'Op Legacy', 'OPERATORE', 'ATTIVO')
                    """.formatted(schemaUpgrade), operatoreId, "legacy-" + operatoreId + "@picus.test");
            jdbc.update("""
                    insert into %s.utenze_profili_operatore (account_id, tipologia, comune_codice, centro_picus)
                    values (?, 'INTERNO_BANCO', 'napoli', false)
                    """.formatted(schemaUpgrade), operatoreId);
            jdbc.update("""
                    insert into %s.monitoraggio_schede_operatore
                    (id, operatore_id, anno, trimestre, inviata_il, versione_tracciato, ore, scala_ore, costo_orario_lordo, scala_costo_orario_lordo)
                    values (?, ?, 2025, 1, '2025-04-02T09:00:00Z', '0.1', ?, 0, ?, 0)
                    """.formatted(schemaUpgrade), schedaId, operatoreId, BigDecimal.TEN, BigDecimal.valueOf(20));
            jdbc.update("""
                    insert into %s.monitoraggio_conteggi_scheda (scheda_id, categoria, valore)
                    values (?, 'donne', 5), (?, 'minori', 0)
                    """.formatted(schemaUpgrade), schedaId, schedaId);

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schemaUpgrade)
                    .locations("classpath:db/migration", "classpath:db/postgresql")
                    .target("26")
                    .load()
                    .migrate();

            UUID versioneCollegata = jdbc.queryForObject("""
                    select versione_categorie_id from %s.monitoraggio_schede_operatore where id = ?
                    """.formatted(schemaUpgrade), UUID.class, schedaId);
            assertThat(versioneCollegata).isEqualTo(CategorieSchedeRendicontabili.VERSIONE_LEGACY_V01_ID);

            UUID versioneTrimestre = jdbc.queryForObject("""
                    select versione_id from %s.monitoraggio_configurazioni_trimestre
                    where tracciato = 'TM-OP-01' and anno = 2025 and trimestre = 1
                    """.formatted(schemaUpgrade), UUID.class);
            assertThat(versioneTrimestre).isEqualTo(CategorieSchedeRendicontabili.VERSIONE_LEGACY_V01_ID);

            String versioneFormato = jdbc.queryForObject("""
                    select versione_tracciato from %s.monitoraggio_schede_operatore where id = ?
                    """.formatted(schemaUpgrade), String.class, schedaId);
            assertThat(versioneFormato).isEqualTo("0.1");

            Integer moderni = jdbc.queryForObject("""
                    select count(*) from %s.monitoraggio_configurazioni_trimestre
                    where tracciato = 'TM-OP-01' and anno = 2026 and trimestre = 4
                    """.formatted(schemaUpgrade), Integer.class);
            assertThat(moderni).isZero();

            Long conteggioZero = jdbc.queryForObject("""
                    select valore from %s.monitoraggio_conteggi_scheda where scheda_id = ? and categoria = 'minori'
                    """.formatted(schemaUpgrade), Long.class, schedaId);
            assertThat(conteggioZero).isZero();
        } finally {
            jdbc.execute("drop schema if exists " + schemaUpgrade + " cascade");
        }
    }

    private boolean provaPubblicazione(UUID versioneId, Trimestre trimestre, Instant istante) {
        try {
            versioni.pubblicaPerTrimestre(versioneId, trimestre, istante);
            return true;
        } catch (ConfigurazioneTrimestreGiaPubblicataException attesa) {
            return false;
        }
    }
}
