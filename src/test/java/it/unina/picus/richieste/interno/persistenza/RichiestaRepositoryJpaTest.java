package it.unina.picus.richieste.interno.persistenza;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final UUID PARI_MINORE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PARI_MAGGIORE = UUID.fromString("00000000-0000-0000-0000-000000000002");

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

        assertThat(jdbc.queryForObject("select count(*) from richieste", Integer.class)).isEqualTo(1);
        Map<String, Object> salvata = rigaPerInvio(riferimento);
        assertThat(esiti).allSatisfy(esito -> assertThat(esito)
                .isInstanceOfSatisfying(RichiestaSintesi.class, sintesi -> {
                    assertThat(sintesi.id()).isEqualTo(salvata.get("id"));
                    // Il comando vincente risponde con l'istante in memoria, PostgreSQL lo salva al microsecondo.
                    assertThat(Duration.between(((Timestamp) salvata.get("inviata_il")).toInstant(),
                            sintesi.inviataIl()).abs()).isLessThan(Duration.ofNanos(1_000));
                }));
        assertThat(salvata.get("tipo_destinatario")).isEqualTo("PERSONA");
        assertThat(salvata.get("description")).isEqualTo("Bisogno generico");
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

        // inParallelo conserva l'ordine: l'indice dell'esito riuscito indica i dati che devono essere salvati.
        boolean vinceIlPrimo = esiti.get(0) instanceof RichiestaSintesi;
        Object riuscito = esiti.get(vinceIlPrimo ? 0 : 1);
        assertThat(riuscito).isInstanceOf(RichiestaSintesi.class);
        assertThat(esiti.get(vinceIlPrimo ? 1 : 0)).isInstanceOf(ConflittoRichiestaException.class);
        assertThat(jdbc.queryForObject("select count(*) from richieste", Integer.class)).isEqualTo(1);

        Map<String, Object> salvata = rigaPerInvio(riferimento);
        assertThat(((RichiestaSintesi) riuscito).id()).isEqualTo(salvata.get("id"));
        assertThat(salvata.get("tipo_destinatario")).isEqualTo(vinceIlPrimo ? "PERSONA" : "NUCLEO");
        assertThat(salvata.get("titolare_id")).isEqualTo(titolare.accountId());
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
        // Due pratiche della stessa titolare con lo stesso istante e UUID fissi inseriti in ordine
        // inverso: senza lo spareggio per UUID l'ordine fisico non coinciderebbe con quello atteso.
        richiestaConId(PARI_MAGGIORE, titolare.accountId(), "PRESA_IN_CARICO", base);
        richiestaConId(PARI_MINORE, titolare.accountId(), "CREATA", base);
        UUID tardiva = dati.richiesta(titolare.accountId(), m01, "napoli", "CREATA", base.plusSeconds(60));
        UUID annullata = dati.richiesta(titolare.accountId(), m01, "napoli", "ANNULLATA", base.minusSeconds(60));
        UUID altrui = dati.richiesta(altraOpt, m01, "portici", "IN_PROGRAMMAZIONE", base.plusSeconds(30));

        assertThat(repository.codaBanco()).extracting(r -> r.id())
                .containsExactly(PARI_MINORE, PARI_MAGGIORE, altrui, tardiva);
        assertThat(repository.cercaPerTitolare(titolare.accountId())).extracting(r -> r.id())
                .containsExactly(annullata, PARI_MINORE, PARI_MAGGIORE, tardiva);
        assertThat(repository.cercaPerTitolare(altraOpt)).extracting(r -> r.id()).containsExactly(altrui);
    }

    private Map<String, Object> rigaPerInvio(UUID riferimento) {
        return jdbc.queryForMap("select * from richieste where titolare_id = ? and riferimento_invio = ?",
                titolare.accountId(), riferimento);
    }

    private void richiestaConId(UUID id, UUID titolareRichiesta, String stato, Instant inviataIl) {
        jdbc.update("""
                insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario, inviata_il, stato)
                values (?, ?, ?, 'napoli', 'PERSONA', ?, ?)
                """, id, titolareRichiesta, m01, Timestamp.from(inviataIl), stato);
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
