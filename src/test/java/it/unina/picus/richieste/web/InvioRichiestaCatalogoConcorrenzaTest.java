package it.unina.picus.richieste.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.catalogo.interno.applicazione.ServizioGestioneCatalogo;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Prova permanente su PostgreSQL della contesa tra disattivazione misura nel catalogo (#163/#164)
 * e nuovo invio di richiesta OPT tramite {@link InvioRichiesteApi#invia}, verificando entrambi gli
 * ordini di acquisizione tramite {@code pg_blocking_pids}, conservazione dello storico e
 * idempotenza HTTP/applicativa dopo la disattivazione.
 */
@IntegrazionePostgresql
@DisplayName("#164 Contesa deterministica tra invio richiesta e disattivazione misura su PostgreSQL")
class InvioRichiestaCatalogoConcorrenzaTest {

    private static final List<String> CODICI_INIZIALI = List.of(
            "M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10", "M11", "M12");

    @Autowired
    private InvioRichiesteApi invio;

    @Autowired
    private ServizioGestioneCatalogo gestione;

    @Autowired
    private ConsultazioneCatalogoApi consultazione;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    private DatiSintetici dati;
    private IdentitaAutenticata banco;
    private IdentitaAutenticata opt;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        ripristina();
        banco = new IdentitaAutenticata(dati.banco("Banco contesa #164"), RuoloUtente.BANCO);
        opt = new IdentitaAutenticata(dati.opt("OPT contesa #164", "napoli"), RuoloUtente.OPT);
    }

    @AfterEach
    void ripulisci() {
        ripristina();
    }

    @Test
    @DisplayName("Disattivazione esplicita e con PUT acquisiscono il lock prima dell'invio e bloccano nuove selezioni")
    void disattivazionePrimaBloccaNuovoInvio() throws Exception {
        verificaDisattivazionePrima(true);
        ripristina();
        banco = new IdentitaAutenticata(dati.banco("Banco contesa #164 PUT"), RuoloUtente.BANCO);
        opt = new IdentitaAutenticata(dati.opt("OPT contesa #164 PUT", "napoli"), RuoloUtente.OPT);
        verificaDisattivazionePrima(false);
    }

    @Test
    @DisplayName("Invio con lock acquisito prima completa, resta storico e mantiene idempotenza dopo disattivazione")
    void invioPrimaCompletaERestaIdempotenteDopoDisattivazione() throws Exception {
        long misuraId = gestione.creaMisura(banco, new DatiMisura("T164B", "Misura storica", "Descrizione", false)).id();
        UUID chiave = UUID.randomUUID();
        var payload = new DatiInvio(misuraId, TipoDestinatario.PERSONA, "napoli", "nota concorrente #164");

        var risultato = eseguiConSecondoInCoda(
                () -> invio.invia(opt, payload, chiave),
                () -> gestione.disattivaMisura(banco, misuraId));

        RichiestaSintesi creata = risultato.primo();
        Misura disattivata = risultato.secondo();
        assertThat(creata.misuraId()).isEqualTo(misuraId);
        assertThat(disattivata.attiva()).isFalse();
        assertThat(misuraAttiva(misuraId)).isFalse();
        assertThat(richiesteDellaOpt()).isEqualTo(1);

        // Storico consultabile nonostante la disattivazione successiva della misura.
        assertThat(invio.consultaRichiesta(opt, creata.id()).richiesta().id()).isEqualTo(creata.id());
        assertThat(consultazione.cercaMisura(misuraId)).isPresent().get()
                .satisfies(m -> assertThat(m.attiva()).isFalse());

        // Ritento idempotente con stesso payload (servizio e HTTP): restituisce lo stesso invio.
        assertThat(invio.invia(opt, payload, chiave).id()).isEqualTo(creata.id());
        mvc.perform(post("/api/richieste")
                        .with(user(opt.accountId().toString()).roles("OPT"))
                        .with(csrf())
                        .header("Idempotency-Key", chiave.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"misuraId":%d,"tipoDestinatario":"PERSONA","comuneCodice":"napoli","description":"nota concorrente #164"}
                                """.formatted(misuraId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(creata.id().toString()));
        assertThat(richiesteDellaOpt()).isEqualTo(1);

        // Ritento con stessa chiave e payload diverso (servizio e HTTP): conflitto 409.
        assertThatThrownBy(() -> invio.invia(opt,
                new DatiInvio(misuraId, TipoDestinatario.NUCLEO, "napoli", "nota concorrente #164"), chiave))
                .isInstanceOf(ConflittoRichiestaException.class);
        mvc.perform(post("/api/richieste")
                        .with(user(opt.accountId().toString()).roles("OPT"))
                        .with(csrf())
                        .header("Idempotency-Key", chiave.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"misuraId":%d,"tipoDestinatario":"NUCLEO","comuneCodice":"napoli","description":"nota diversa"}
                                """.formatted(misuraId)))
                .andExpect(status().isConflict());
        assertThat(richiesteDellaOpt()).isEqualTo(1);

        // Nuova richiesta su misura ormai disattivata (nuova chiave): rifiutata sul campo misuraId.
        mvc.perform(post("/api/richieste")
                        .with(user(opt.accountId().toString()).roles("OPT"))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"misuraId":%d,"tipoDestinatario":"PERSONA","comuneCodice":"napoli","description":"nuova dopo disattivazione"}
                                """.formatted(misuraId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.misuraId").isString());
        assertThat(richiesteDellaOpt()).isEqualTo(1);
    }

    private void verificaDisattivazionePrima(boolean disattivazioneEsplicita) throws Exception {
        long misuraId = gestione.creaMisura(banco, new DatiMisura("T164A", "Misura contesa", "Descrizione", false)).id();

        var risultato = eseguiConSecondoInCoda(
                () -> disattivazioneEsplicita
                        ? gestione.disattivaMisura(banco, misuraId)
                        : gestione.aggiornaMisura(banco, misuraId,
                                new DatiMisura("T164A", "Disattivata con PUT", "Descrizione", false), false),
                () -> {
                    try {
                        return invio.invia(opt, new DatiInvio(misuraId, TipoDestinatario.PERSONA, "napoli", null),
                                UUID.randomUUID());
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                });

        assertThat(risultato.primo().attiva()).isFalse();
        assertThat(misuraAttiva(misuraId)).isFalse();
        assertThat(risultato.secondo()).isExactlyInstanceOf(DatiRichiestaNonValidiException.class);
        assertThat(((DatiRichiestaNonValidiException) risultato.secondo()).campi()).containsOnlyKeys("misuraId");
        assertThat(richiesteDellaOpt()).isZero();
    }

    private record RisultatoContesa<F, S>(F primo, S secondo) {}

    private <F, S> RisultatoContesa<F, S> eseguiConSecondoInCoda(Callable<F> primoInTransazione, Callable<S> secondo)
            throws Exception {
        CountDownLatch lockAcquisito = new CountDownLatch(1);
        CountDownLatch rilasciaPrimo = new CountDownLatch(1);
        AtomicInteger pidPrimo = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<F> futuroPrimo = null;
        Future<S> futuroSecondo = null;
        try {
            futuroPrimo = pool.submit(() -> new TransactionTemplate(txManager).execute(status -> {
                F esitoPrimo;
                try {
                    esitoPrimo = primoInTransazione.call();
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                pidPrimo.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                lockAcquisito.countDown();
                try {
                    if (!rilasciaPrimo.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timeout attesa rilascio lock del primo worker");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return esitoPrimo;
            }));

            try {
                assertThat(lockAcquisito.await(10, TimeUnit.SECONDS))
                        .as("Il primo worker doveva acquisire il lock e segnalare il proprio pid")
                        .isTrue();
                futuroSecondo = pool.submit(secondo);
                attendiInCoda(pidPrimo.get());
            } finally {
                rilasciaPrimo.countDown();
            }

            return new RisultatoContesa<>(
                    futuroPrimo.get(15, TimeUnit.SECONDS),
                    futuroSecondo.get(15, TimeUnit.SECONDS));
        } finally {
            rilasciaPrimo.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(15, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    private void attendiInCoda(int pidBloccante) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int inCoda = 0;
        while (System.nanoTime() < limite) {
            inCoda = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity where ? = any(pg_blocking_pids(pid))
                    """, Integer.class, pidBloccante);
            if (inCoda >= 1) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Atteso almeno 1 worker in coda su pg_blocking_pids per pid "
                + pidBloccante + ", trovati " + inCoda);
    }

    private boolean misuraAttiva(long misuraId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select attiva from catalogo_misure where id = ?", Boolean.class, misuraId));
    }

    private int richiesteDellaOpt() {
        return jdbc.queryForObject(
                "select count(*) from richieste where titolare_id = ?", Integer.class, opt.accountId());
    }

    private void ripristina() {
        dati.svuota();
        jdbc.update("delete from catalogo_misure where codice not in ("
                + String.join(",", CODICI_INIZIALI.stream().map(c -> "'" + c + "'").toList()) + ")");
    }
}
