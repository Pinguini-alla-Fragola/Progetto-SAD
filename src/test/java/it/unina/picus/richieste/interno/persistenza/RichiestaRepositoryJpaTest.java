package it.unina.picus.richieste.interno.persistenza;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;

import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.richieste.interno.dominio.RichiestaRepository;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Persistenza degli invii su PostgreSQL (#48, criteri 1 e 2 della #99): il vincolo univoco su
 * titolare e riferimento con {@code ON CONFLICT} riconosce i ritenti anche concorrenti, e gli
 * elenchi seguono l'ordine cronologico con l'UUID come criterio di parità.
 */
@IntegrazionePostgresql
@DisplayName("Repository delle richieste su PostgreSQL")
class RichiestaRepositoryJpaTest {

    @Autowired
    private InvioRichiesteApi invio;

    @Autowired
    private RichiestaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private long m01;
    private IdentitaAutenticata titolare;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        m01 = dati.misura("M01");
        titolare = new IdentitaAutenticata(dati.opt("OPT titolare", "napoli"), RuoloUtente.OPT);
    }

    @RepeatedTest(3)
    @DisplayName("Ritenti simultanei con lo stesso riferimento e dati creano una sola richiesta e restituiscono lo stesso id")
    void ritentiConcorrenti() throws Exception {
        UUID riferimento = UUID.randomUUID();
        var datiInvio = new DatiInvio(m01, TipoDestinatario.PERSONA, "napoli", "Bisogno generico");
        List<Callable<Object>> comandi = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            comandi.add(() -> invio.invia(titolare, datiInvio, riferimento));
        }

        var esiti = inParallelo(comandi);

        assertThat(esiti).allSatisfy(esito -> assertThat(esito).isInstanceOf(RichiestaSintesi.class));
        assertThat(esiti).extracting(esito -> ((RichiestaSintesi) esito).id()).containsOnly(
                ((RichiestaSintesi) esiti.getFirst()).id());
        assertThat(jdbc.queryForObject("select count(*) from richieste", Integer.class)).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("Ritenti simultanei con lo stesso riferimento e dati diversi: una richiesta, gli altri in conflitto")
    void riferimentoConcorrenteConDatiDiversi() throws Exception {
        UUID riferimento = UUID.randomUUID();
        var persona = new DatiInvio(m01, TipoDestinatario.PERSONA, "napoli", null);
        var nucleo = new DatiInvio(m01, TipoDestinatario.NUCLEO, "napoli", null);

        var esiti = inParallelo(List.of(
                () -> invio.invia(titolare, persona, riferimento),
                () -> invio.invia(titolare, nucleo, riferimento)));

        assertThat(esiti).filteredOn(RichiestaSintesi.class::isInstance).hasSize(1);
        assertThat(esiti).filteredOn(ConflittoRichiestaException.class::isInstance).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from richieste", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Invii senza riferimento con dati uguali restano richieste distinte")
    void inviiSenzaRiferimento() {
        var datiInvio = new DatiInvio(m01, TipoDestinatario.PERSONA, "napoli", null);

        var primo = invio.invia(titolare, datiInvio, null);
        var secondo = invio.invia(titolare, datiInvio, null);

        assertThat(primo.id()).isNotEqualTo(secondo.id());
        assertThat(jdbc.queryForObject("select count(*) from richieste", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("Coda ed elenco del titolare in ordine di invio e, a parità, di UUID; la coda esclude gli esiti")
    void ordinamentoPersistito() {
        Instant base = Instant.parse("2026-11-10T09:00:00Z");
        UUID altraOpt = dati.opt("OPT altra", "portici");
        UUID tardiva = dati.richiesta(titolare.accountId(), m01, "napoli", "CREATA", base.plusSeconds(60));
        UUID parA = dati.richiesta(titolare.accountId(), m01, "napoli", "PRESA_IN_CARICO", base);
        UUID parB = dati.richiesta(altraOpt, m01, "portici", "IN_PROGRAMMAZIONE", base);
        dati.richiesta(titolare.accountId(), m01, "napoli", "ANNULLATA", base.minusSeconds(60));
        List<UUID> pari = List.of(parA, parB).stream().sorted((a, b) -> a.toString().compareTo(b.toString())).toList();

        assertThat(repository.codaBanco()).extracting(r -> r.id())
                .containsExactly(pari.get(0), pari.get(1), tardiva);
        assertThat(repository.cercaPerTitolare(titolare.accountId())).extracting(r -> r.id())
                .hasSize(3).doesNotContain(parB).endsWith(tardiva);
    }

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
}
