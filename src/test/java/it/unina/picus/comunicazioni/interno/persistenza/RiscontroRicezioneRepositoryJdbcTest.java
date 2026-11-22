package it.unina.picus.comunicazioni.interno.persistenza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.unina.picus.comunicazioni.interno.dominio.RiscontroRicezioneRepository;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

/**
 * Test black box della porta {@link RiscontroRicezioneRepository} nell'adattatore JDBC su PostgreSQL
 * (#145, codice della #143), ricavati dal Javadoc della porta e dell'adattatore.
 *
 * {@code cercaDestinatari}: vuoto se la comunicazione manca, altrimenti l'insieme dei destinatari, anche
 * vuoto. {@code registraPrimaConferma}: richiede una transazione del chiamante, registra l'istante della prima
 * conferma con la precisione del database, restituisce quello già salvato a ritenti e conferme concorrenti e
 * restituisce vuoto se l'account non ha una riga di destinazione.
 *
 * Le comunicazioni sono scritte direttamente nelle tabelle della V18, perché l'oggetto del test è la sola
 * persistenza del riscontro.
 */
@IntegrazionePostgresql
@Tag("issue-145")
@DisplayName("#145 Persistenza dei riscontri di ricezione su PostgreSQL")
class RiscontroRicezioneRepositoryJdbcTest {

    /** Precisione di {@code timestamp with time zone} in PostgreSQL. */
    private static final Duration MICROSECONDO = Duration.ofNanos(1_000);

    /** Conferme concorrenti: ognuna tiene una connessione, si resta sotto il pool predefinito di Hikari (10). */
    private static final int CONFERME_CONCORRENTI = 6;

    @Autowired
    private RiscontroRicezioneRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transazioni;

    private TransactionTemplate inTransazione;
    private DatiSintetici dati;
    private UUID banco;
    private UUID optA;
    private UUID optB;
    private UUID operatore;

    @BeforeEach
    void prepara() {
        svuotaComunicazioni();
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        inTransazione = new TransactionTemplate(transazioni);
        banco = dati.banco("Banco di prova");
        optA = dati.opt("OPT A", "napoli");
        optB = dati.opt("OPT B", "napoli");
        operatore = dati.operatore("Operatore", "INTERNO_BANCO", "napoli", false);
    }

    /** {@link DatiSintetici#svuota()} non conosce ancora le tabelle della V18: senza pulizia fallirebbe sulle FK. */
    @AfterEach
    void svuotaComunicazioni() {
        jdbc.update("delete from comunicazioni_destinatari");
        jdbc.update("delete from comunicazioni");
    }

    // ------------------------------------------------------------------ cercaDestinatari

    @Test
    @DisplayName("cercaDestinatari: comunicazione inesistente, risultato vuoto")
    void destinatariDiComunicazioneInesistente() {
        assertThat(repository.cercaDestinatari(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("cercaDestinatari: restituisce esattamente gli account registrati all'invio")
    void destinatariRegistrati() {
        UUID comunicazione = comunicazione(optA, optB);
        comunicazione(operatore);

        assertThat(repository.cercaDestinatari(comunicazione)).contains(Set.of(optA, optB));
    }

    @Test
    @DisplayName("cercaDestinatari: comunicazione senza destinatari, insieme presente ma vuoto")
    void comunicazioneSenzaDestinatari() {
        UUID comunicazione = comunicazione();

        assertThat(repository.cercaDestinatari(comunicazione)).contains(Set.of());
    }

    @Test
    @DisplayName("cercaDestinatari: le conferme già registrate non cambiano l'elenco")
    void destinatariDopoUnaConferma() {
        UUID comunicazione = comunicazione(optA, optB);
        conferma(comunicazione, optA, Instant.parse("2026-11-22T09:00:00Z"));

        assertThat(repository.cercaDestinatari(comunicazione)).contains(Set.of(optA, optB));
    }

    // ------------------------------------------------------------------ registraPrimaConferma

    @Test
    @DisplayName("registraPrimaConferma: la prima conferma salva e restituisce l'istante con la precisione del database")
    void primaConferma() {
        UUID comunicazione = comunicazione(optA, optB);
        Instant istante = Instant.parse("2026-11-22T10:15:30.123456789Z");

        Optional<Instant> esito = conferma(comunicazione, optA, istante);

        assertThat(esito).isPresent();
        assertThat(esito.get()).isEqualTo(confermataIl(comunicazione, optA));
        assertThat(Duration.between(istante, esito.get()).abs()).isLessThan(MICROSECONDO);
    }

    @Test
    @DisplayName("registraPrimaConferma: un ritento restituisce l'istante originale e non lo modifica")
    void ritento() {
        UUID comunicazione = comunicazione(optA);
        Instant prima = conferma(comunicazione, optA, Instant.parse("2026-11-22T10:00:00Z")).orElseThrow();

        Optional<Instant> ritento = conferma(comunicazione, optA, Instant.parse("2026-11-22T11:00:00Z"));

        assertThat(ritento).contains(prima);
        assertThat(confermataIl(comunicazione, optA)).isEqualTo(prima);
        assertThat(righe(comunicazione, optA)).isEqualTo(1);
    }

    @Test
    @DisplayName("registraPrimaConferma: la conferma di un destinatario non tocca gli altri né altre comunicazioni")
    void isolamentoFraDestinatari() {
        UUID comunicazione = comunicazione(optA, optB);
        UUID altraComunicazione = comunicazione(optA);

        conferma(comunicazione, optA, Instant.parse("2026-11-22T10:00:00Z"));

        assertThat(confermataIl(comunicazione, optB)).isNull();
        assertThat(confermataIl(altraComunicazione, optA)).isNull();
    }

    @Test
    @DisplayName("registraPrimaConferma: un account senza riga di destinazione ottiene vuoto e non crea righe")
    void accountNonDestinatario() {
        UUID comunicazione = comunicazione(optA);

        assertThat(conferma(comunicazione, optB, Instant.parse("2026-11-22T10:00:00Z"))).isEmpty();
        assertThat(righe(comunicazione, optB)).isZero();
        assertThat(confermataIl(comunicazione, optA)).isNull();
    }

    @Test
    @DisplayName("registraPrimaConferma: comunicazione inesistente, risultato vuoto")
    void comunicazioneInesistente() {
        assertThat(conferma(UUID.randomUUID(), optA, Instant.parse("2026-11-22T10:00:00Z"))).isEmpty();
    }

    @Test
    @DisplayName("registraPrimaConferma: senza una transazione del chiamante viene rifiutata e non registra nulla")
    void richiedeTransazione() {
        UUID comunicazione = comunicazione(optA);

        assertThatThrownBy(() -> repository.registraPrimaConferma(comunicazione, optA, Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(confermataIl(comunicazione, optA)).isNull();
    }

    @RepeatedTest(3)
    @DisplayName("registraPrimaConferma: conferme concorrenti dello stesso account restituiscono tutte lo stesso istante")
    void confermeConcorrenti() throws Exception {
        UUID comunicazione = comunicazione(operatore);
        Instant base = Instant.parse("2026-11-22T10:00:00Z");
        List<Instant> istanti = new ArrayList<>();
        List<Callable<Object>> comandi = new ArrayList<>();
        for (int i = 0; i < CONFERME_CONCORRENTI; i++) {
            Instant istante = base.plusSeconds(i);
            istanti.add(istante);
            comandi.add(() -> conferma(comunicazione, operatore, istante).orElseThrow());
        }

        List<Object> esiti = inParallelo(comandi);

        Instant salvato = confermataIl(comunicazione, operatore);
        assertThat(esiti).allSatisfy(esito -> assertThat(esito).isEqualTo(salvato));
        assertThat(istanti).contains(salvato);
        assertThat(righe(comunicazione, operatore)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ supporto

    private Optional<Instant> conferma(UUID comunicazione, UUID destinatario, Instant istante) {
        return inTransazione.execute(stato -> repository.registraPrimaConferma(comunicazione, destinatario, istante));
    }

    private UUID comunicazione(UUID... destinatari) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into comunicazioni (id, mittente_id, titolo, testo, tipo_destinatari, inviata_il)
                values (?, ?, 'Avviso di prova', 'Testo sintetico', 'GRUPPO_OPT', ?)
                """, id, banco, Timestamp.from(Instant.parse("2026-11-22T08:00:00Z")));
        for (UUID destinatario : destinatari) {
            jdbc.update("insert into comunicazioni_destinatari (comunicazione_id, destinatario_id) values (?, ?)",
                    id, destinatario);
        }
        return id;
    }

    private Instant confermataIl(UUID comunicazione, UUID destinatario) {
        OffsetDateTime valore = jdbc.queryForObject("""
                select confermata_il from comunicazioni_destinatari
                where comunicazione_id = ? and destinatario_id = ?
                """, OffsetDateTime.class, comunicazione, destinatario);
        return valore == null ? null : valore.toInstant();
    }

    private int righe(UUID comunicazione, UUID destinatario) {
        return jdbc.queryForObject("""
                select count(*) from comunicazioni_destinatari where comunicazione_id = ? and destinatario_id = ?
                """, Integer.class, comunicazione, destinatario);
    }

    private static List<Object> inParallelo(List<Callable<Object>> comandi) throws Exception {
        var barriera = new CyclicBarrier(comandi.size());
        ExecutorService pool = Executors.newFixedThreadPool(comandi.size());
        try {
            List<Future<Object>> futuri = new ArrayList<>();
            for (Callable<Object> comando : comandi) {
                futuri.add(pool.submit(() -> {
                    barriera.await(30, TimeUnit.SECONDS);
                    return comando.call();
                }));
            }
            List<Object> esiti = new ArrayList<>();
            for (Future<Object> futuro : futuri) {
                esiti.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return esiti;
        } finally {
            pool.shutdownNow();
        }
    }
}
