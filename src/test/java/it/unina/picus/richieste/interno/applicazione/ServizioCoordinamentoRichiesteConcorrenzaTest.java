package it.unina.picus.richieste.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.CoordinamentoRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Comandi concorrenti su PostgreSQL reale (criteri 4 e 6 della #102): il lock della pratica e
 * l'indice parziale sul contatto in attesa impediscono stati incoerenti, contatti o avvisi in
 * più e assegnazioni doppie. Ogni comando gira in un thread e in una transazione propri.
 */
@IntegrazionePostgresql
@DisplayName("Coordinamento con comandi concorrenti su PostgreSQL")
class ServizioCoordinamentoRichiesteConcorrenzaTest {

    private static final Instant INVIO = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired
    private CoordinamentoRichiesteApi coordinamento;

    @Autowired
    private DisponibilitaService disponibilita;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private long m01;
    private IdentitaAutenticata banco;
    private UUID titolare;
    private UUID optLocale;
    private UUID operatore;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        m01 = dati.misura("M01");
        banco = new IdentitaAutenticata(dati.banco("Banco di coordinamento"), RuoloUtente.BANCO);
        titolare = dati.opt("OPT titolare", "napoli");
        optLocale = dati.opt("OPT locale", "napoli");
        dati.offerta(optLocale, m01);
        operatore = dati.operatore("Operatore interno", "INTERNO_BANCO", "napoli", false, m01);
    }

    @RepeatedTest(3)
    @DisplayName("Prese in carico simultanee: una sola riesce, le altre sono in conflitto")
    void presaInCaricoConcorrente() throws Exception {
        UUID richiesta = dati.richiesta(titolare, m01, "napoli", "CREATA", INVIO);
        List<Callable<Object>> comandi = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            comandi.add(() -> coordinamento.prendiInCarico(banco, richiesta));
        }

        var esiti = inParallelo(comandi);

        assertThat(successi(esiti)).hasSize(1);
        assertThat(conflitti(esiti)).hasSize(4);
        assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
    }

    @RepeatedTest(3)
    @DisplayName("Interpelli simultanei verso erogatori diversi: un solo contatto in attesa e un solo avviso")
    void interpelliConcorrenti() throws Exception {
        UUID richiesta = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIO);
        List<Callable<Object>> comandi = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID erogatore = i % 2 == 0 ? optLocale : operatore;
            comandi.add(() -> coordinamento.interpellaErogatore(banco, richiesta, erogatore,
                    AmbitoErogazione.STESSO_COMUNE));
        }

        var esiti = inParallelo(comandi);

        assertThat(successi(esiti)).hasSize(1);
        assertThat(conflitti(esiti)).hasSize(5);
        assertThat(dati.contatti(richiesta)).isEqualTo(1);
        assertThat(dati.contattiInAttesa(richiesta)).isEqualTo(1);
        assertThat(dati.avvisi(richiesta)).isEqualTo(1);
        assertThat(dati.avvisiCoerentiConIlContatto(richiesta)).isEqualTo(1);
        assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
    }

    @RepeatedTest(3)
    @DisplayName("Assegnazioni simultanee su due risposte positive: un solo assegnatario coerente con il suo contatto")
    void assegnazioniConcorrenti() throws Exception {
        UUID richiesta = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIO);
        var primo = coordinamento.interpellaErogatore(banco, richiesta, optLocale, AmbitoErogazione.STESSO_COMUNE);
        disponibilita.rispondiDisponibilita(come(optLocale, RuoloUtente.OPT), primo.id(), true);
        var secondo = coordinamento.interpellaErogatore(banco, richiesta, operatore, AmbitoErogazione.STESSO_COMUNE);
        disponibilita.rispondiDisponibilita(come(operatore, RuoloUtente.OPERATORE), secondo.id(), true);

        var esiti = inParallelo(List.of(
                () -> coordinamento.assegnaErogatore(banco, primo.id()),
                () -> coordinamento.assegnaErogatore(banco, secondo.id())));

        assertThat(successi(esiti)).hasSize(1);
        assertThat(conflitti(esiti)).hasSize(1);
        assertThat(dati.stato(richiesta)).isEqualTo("IN_PROGRAMMAZIONE");
        assertThat(dati.erogatoreAssegnato(richiesta)).isIn(optLocale, operatore);
        assertThat(dati.ambitoAssegnato(richiesta)).isEqualTo("STESSO_COMUNE");
    }

    @RepeatedTest(3)
    @DisplayName("Risposta negativa e nuovo interpello simultanei non lasciano mai due contatti in attesa")
    void rispostaEInterpelloConcorrenti() throws Exception {
        UUID richiesta = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIO);
        ContattoDisponibilita aperto = coordinamento.interpellaErogatore(banco, richiesta, optLocale,
                AmbitoErogazione.STESSO_COMUNE);

        var esiti = inParallelo(List.of(
                () -> disponibilita.rispondiDisponibilita(come(optLocale, RuoloUtente.OPT), aperto.id(), false),
                () -> coordinamento.interpellaErogatore(banco, richiesta, operatore, AmbitoErogazione.STESSO_COMUNE)));

        assertThat(esiti.get(0)).as("la risposta del destinatario è sempre registrata")
                .isInstanceOf(ContattoDisponibilita.class);
        assertThat(dati.esitoContatto(aperto.id())).isEqualTo("NON_DISPONIBILE");
        assertThat(dati.contattiInAttesa(richiesta)).isLessThanOrEqualTo(1);
        assertThat(dati.contatti(richiesta)).isEqualTo(esiti.get(1) instanceof ContattoDisponibilita ? 2 : 1);
        assertThat(dati.avvisi(richiesta)).isEqualTo(dati.contatti(richiesta));
        assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
    }

    @RepeatedTest(3)
    @DisplayName("Assegnazione e nuovo interpello simultanei: nessun contatto aperto su una pratica assegnata")
    void assegnazioneEInterpelloConcorrenti() throws Exception {
        UUID richiesta = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIO);
        var positivo = coordinamento.interpellaErogatore(banco, richiesta, optLocale, AmbitoErogazione.STESSO_COMUNE);
        disponibilita.rispondiDisponibilita(come(optLocale, RuoloUtente.OPT), positivo.id(), true);

        var esiti = inParallelo(List.of(
                () -> coordinamento.assegnaErogatore(banco, positivo.id()),
                () -> coordinamento.interpellaErogatore(banco, richiesta, operatore, AmbitoErogazione.STESSO_COMUNE)));

        assertThat(successi(esiti)).hasSize(1);
        assertThat(conflitti(esiti)).hasSize(1);
        if (esiti.get(0) instanceof RuntimeException) {
            assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
            assertThat(dati.erogatoreAssegnato(richiesta)).isNull();
            assertThat(dati.contattiInAttesa(richiesta)).isEqualTo(1);
        } else {
            assertThat(dati.stato(richiesta)).isEqualTo("IN_PROGRAMMAZIONE");
            assertThat(dati.erogatoreAssegnato(richiesta)).isEqualTo(optLocale);
            assertThat(dati.contattiInAttesa(richiesta)).isZero();
        }
    }

    @Test
    @DisplayName("L'indice PostgreSQL rifiuta un secondo contatto in attesa anche fuori dal servizio")
    void indiceUnicoContattoInAttesa() {
        UUID richiesta = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIO);
        coordinamento.interpellaErogatore(banco, richiesta, optLocale, AmbitoErogazione.STESSO_COMUNE);

        assertThatThrownBy(() -> jdbc.update("""
                insert into richieste_contatti_disponibilita (id, richiesta_id, erogatore_id, ambito, nome_misura, inviato_il)
                values (?, ?, ?, 'STESSO_COMUNE', 'Accoglienza e ascolto', ?)
                """, UUID.randomUUID(), richiesta, operatore, Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(dati.contatti(richiesta)).isEqualTo(1);
    }

    private static IdentitaAutenticata come(UUID account, RuoloUtente ruolo) {
        return new IdentitaAutenticata(account, ruolo);
    }

    /**
     * Avvia i comandi insieme dietro una barriera e restituisce, nello stesso ordine, il risultato
     * oppure l'eccezione applicativa di ciascuno.
     */
    private static List<Object> inParallelo(List<Callable<Object>> comandi) throws Exception {
        var barriera = new CyclicBarrier(comandi.size());
        ExecutorService pool = Executors.newFixedThreadPool(comandi.size());
        try {
            List<Future<Object>> futuri = new ArrayList<>();
            for (Callable<Object> comando : comandi) {
                Callable<Object> sincronizzato = () -> {
                    barriera.await(30, TimeUnit.SECONDS);
                    try {
                        return comando.call();
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                };
                futuri.add(pool.submit(sincronizzato));
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

    private static List<Object> successi(List<Object> esiti) {
        return esiti.stream().filter(e -> !(e instanceof RuntimeException)).toList();
    }

    /** Solo conflitti applicativi: qualunque altra eccezione (deadlock, vincolo violato) fa fallire il test. */
    private static List<Object> conflitti(List<Object> esiti) {
        assertThat(esiti).filteredOn(e -> e instanceof RuntimeException)
                .allSatisfy(e -> assertThat(e).isInstanceOf(ConflittoRichiestaException.class));
        return esiti.stream().filter(ConflittoRichiestaException.class::isInstance).toList();
    }
}
