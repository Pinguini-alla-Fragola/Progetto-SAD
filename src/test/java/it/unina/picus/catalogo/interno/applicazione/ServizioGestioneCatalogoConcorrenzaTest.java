package it.unina.picus.catalogo.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.interno.dominio.CodiceMisuraDuplicatoException;
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
 * Contese deterministiche sul catalogo su PostgreSQL reale (#165, US #46 e #164).
 *
 * <p>Ogni prova fissa l'ordine dei comandi invece di affidarsi al caso. Il primo comando gira in una
 * transazione che resta aperta dopo aver preso i propri lock; il secondo parte solo allora e il test
 * aspetta di vederlo <b>in coda dietro al processo PostgreSQL del primo</b>
 * ({@code pg_blocking_pids}), controllando anche su quale istruzione è fermo. Solo a quel punto la
 * prima transazione viene confermata (o annullata) e si verificano l'esito di entrambi e le righe
 * salvate. Se il secondo comando non risulta in coda entro 10 secondi il test fallisce: con comandi di
 * fatto seriali le asserzioni passerebbero senza aver provato il lock.</p>
 *
 * <p>Per l'invio con {@code Idempotency-Key} sono provati i tre rami di
 * {@link InvioRichiesteApi#invia} che recuperano un invio concorrente: l'inserimento che trova la
 * stessa chiave già confermata, la rilettura dopo il lock della misura e la rilettura dopo il controllo
 * senza lock; ciascuno con gli stessi dati (stessa richiesta) e con dati diversi (conflitto 409).</p>
 *
 * <p>Il pool dei thread è chiuso con try-with-resources: {@code close()} aspetta la fine di entrambi i
 * comandi, anche se un'attesa fallisce, quindi la pulizia del database in {@code @AfterEach} non parte
 * mai con una transazione ancora in corso. {@link DatiSintetici#svuota()} da sola non cancella le
 * misure create dai test: prima e dopo ogni test sono eliminate quelle diverse dalle dodici iniziali.</p>
 */
@IntegrazionePostgresql
@DisplayName("#165 Contese deterministiche sul catalogo su PostgreSQL")
class ServizioGestioneCatalogoConcorrenzaTest {

    private static final List<String> CODICI_INIZIALI = List.of(
            "M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10", "M11", "M12");
    private static final UUID CHIAVE = UUID.fromString("00000000-0000-4000-8000-000000000165");
    private static final long ATTESA_SECONDI = 10;

    @Autowired
    private ServizioGestioneCatalogo gestione;

    @Autowired
    private ServizioOffertaOpt offerta;

    @Autowired
    private InvioRichiesteApi invio;

    @Autowired
    private PlatformTransactionManager transazioni;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private IdentitaAutenticata banco;
    private IdentitaAutenticata opt;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        ripristinaCatalogo();
        banco = new IdentitaAutenticata(dati.banco("Banco di prova concorrenza"), RuoloUtente.BANCO);
        opt = new IdentitaAutenticata(dati.opt("OPT di prova concorrenza", "napoli"), RuoloUtente.OPT);
    }

    @AfterEach
    void ripulisci() {
        ripristinaCatalogo();
    }

    @Nested
    @DisplayName("Creazione con lo stesso codice (indice univoco su lower(codice))")
    class StessoCodice {

        @Test
        @DisplayName("Prima creazione confermata: la seconda, in coda sull'indice, riceve il conflitto sul codice")
        void primaConfermata() throws Exception {
            var esito = contesa(
                    () -> gestione.creaMisura(banco, new DatiMisura("T01", "Nome alfa", "Descrizione", false)),
                    true,
                    inserimentoIn("catalogo_misure"),
                    () -> gestione.creaMisura(banco, new DatiMisura("t01", "Nome beta", "Descrizione", true)));

            assertThat(esito.secondo()).isExactlyInstanceOf(CodiceMisuraDuplicatoException.class);
            assertThat(misureDiProva()).containsExactly(esito.primo().id() + "|T01|Nome alfa|Descrizione|false|true");
        }

        @Test
        @DisplayName("Prima creazione annullata: la seconda, in coda sull'indice, viene salvata")
        void primaAnnullata() throws Exception {
            var esito = contesa(
                    () -> gestione.creaMisura(banco, new DatiMisura("T01", "Nome alfa", "Descrizione", false)),
                    false,
                    inserimentoIn("catalogo_misure"),
                    () -> gestione.creaMisura(banco, new DatiMisura("t01", "Nome beta", "Descrizione", true)));

            Misura salvata = (Misura) esito.secondo();
            assertThat(misureDiProva()).containsExactly(salvata.id() + "|t01|Nome beta|Descrizione|true|true");
        }
    }

    @Nested
    @DisplayName("Modifica e disattivazione della stessa misura")
    class ModificaEDisattivazione {

        @Test
        @DisplayName("Modifica per prima: la disattivazione in coda vede i nuovi dati e ha l'ultima parola")
        void modificaPrima() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> gestione.aggiornaMisura(banco, id, new DatiMisura("T01", "Nome corretto", "Descrizione", true),
                            true),
                    true,
                    letturaBloccanteDi("catalogo_misure"),
                    () -> gestione.disattivaMisura(banco, id));

            Misura disattivata = (Misura) esito.secondo();
            assertThat(disattivata.attiva()).isFalse();
            assertThat(disattivata.dati().nome()).isEqualTo("Nome corretto");
            assertThat(rigaMisura(id)).isEqualTo("T01|Nome corretto|Descrizione|true|false");
        }

        @Test
        @DisplayName("Disattivazione per prima: la modifica in coda con attiva a true riattiva la misura")
        void disattivazionePrima() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> gestione.disattivaMisura(banco, id),
                    true,
                    letturaBloccanteDi("catalogo_misure"),
                    () -> gestione.aggiornaMisura(banco, id, new DatiMisura("T01", "Nome corretto", "Descrizione", true),
                            true));

            assertThat(esito.primo().dati().nome()).isEqualTo("Nome iniziale");
            assertThat(((Misura) esito.secondo()).attiva()).isTrue();
            assertThat(rigaMisura(id)).isEqualTo("T01|Nome corretto|Descrizione|true|true");
        }
    }

    @Nested
    @DisplayName("Nuova richiesta e disattivazione della misura")
    class InvioEDisattivazione {

        @Test
        @DisplayName("Invio per primo: la richiesta resta registrata e la disattivazione in coda si applica dopo")
        void invioPrimo() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> invio.invia(opt, invioPersona(id), CHIAVE),
                    true,
                    letturaBloccanteDi("catalogo_misure"),
                    () -> gestione.disattivaMisura(banco, id));

            assertThat(((Misura) esito.secondo()).attiva()).isFalse();
            assertThat(rigaMisura(id)).endsWith("|false");
            assertThat(richiesteDellaOpt()).containsExactly(esito.primo().id() + "|" + id + "|PERSONA");
        }

        @Test
        @DisplayName("Disattivazione per prima: l'invio in coda sul lock della misura è rifiutato e non salva nulla")
        void disattivazionePrima() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> gestione.disattivaMisura(banco, id),
                    true,
                    letturaBloccanteDi("catalogo_misure"),
                    () -> invio.invia(opt, invioPersona(id), CHIAVE));

            assertThat(esito.secondo()).isExactlyInstanceOf(DatiRichiestaNonValidiException.class);
            assertThat(((DatiRichiestaNonValidiException) esito.secondo()).campi()).containsOnlyKeys("misuraId");
            assertThat(richiesteDellaOpt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Dichiarazione di offerta e disattivazione della misura")
    class OffertaEDisattivazione {

        @Test
        @DisplayName("Offerta per prima: la dichiarazione resta salvata e la disattivazione in coda si applica dopo")
        void offertaPrima() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> {
                        offerta.registraOffertaOpt(opt, id, true);
                        return "registrata";
                    },
                    true,
                    letturaBloccanteDi("catalogo_misure"),
                    () -> gestione.disattivaMisura(banco, id));

            assertThat(((Misura) esito.secondo()).attiva()).isFalse();
            assertThat(rigaMisura(id)).endsWith("|false");
            assertThat(righeOfferta()).containsExactly(id + ":true:true");
        }

        @Test
        @DisplayName("Disattivazione per prima: l'offerta in coda sul lock della misura è rifiutata e non salva nulla")
        void disattivazionePrima() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> gestione.disattivaMisura(banco, id),
                    true,
                    letturaBloccanteDi("catalogo_misure"),
                    () -> {
                        offerta.registraOffertaOpt(opt, id, true);
                        return "registrata";
                    });

            assertThat(esito.secondo()).isExactlyInstanceOf(MisuraNonOffribileException.class);
            assertThat(righeOfferta()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ritento concorrente con la stessa Idempotency-Key")
    class RitentoConcorrente {

        /**
         * Misura attiva: il ritento supera il lock condiviso della misura e resta in coda sull'inserimento
         * della stessa chiave; quando il primo invio è confermato, l'inserimento non ha effetto e il
         * repository rilegge la richiesta registrata.
         */
        @Test
        @DisplayName("Ramo dell'inserimento: con gli stessi dati restituisce la richiesta già registrata")
        void inserimentoStessiDati() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> invio.invia(opt, invioPersona(id), CHIAVE),
                    true,
                    inserimentoIn("richieste"),
                    () -> invio.invia(opt, invioPersona(id), CHIAVE));

            assertThat(((RichiestaSintesi) esito.secondo()).id()).isEqualTo(esito.primo().id());
            assertThat(richiesteDellaOpt()).containsExactly(esito.primo().id() + "|" + id + "|PERSONA");
        }

        @Test
        @DisplayName("Ramo dell'inserimento: con dati diversi risponde con il conflitto e non salva nulla")
        void inserimentoDatiDiversi() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> invio.invia(opt, invioPersona(id), CHIAVE),
                    true,
                    inserimentoIn("richieste"),
                    () -> invio.invia(opt, invioNucleo(id), CHIAVE));

            assertThat(esito.secondo()).isExactlyInstanceOf(ConflittoRichiestaException.class);
            assertThat(richiesteDellaOpt()).containsExactly(esito.primo().id() + "|" + id + "|PERSONA");
        }

        /**
         * Il primo comando registra l'invio e disattiva la misura nella stessa transazione. Il ritento
         * vede ancora la misura attiva, si ferma sul lock della misura e, ottenuto il lock, la trova
         * disattivata: rilegge allora l'invio confermato nel frattempo.
         */
        @Test
        @DisplayName("Ramo dopo il lock: con gli stessi dati restituisce la richiesta già registrata")
        void dopoIlLockStessiDati() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> registraEDisattiva(id),
                    true,
                    letturaBloccanteDi("catalogo_misure"),
                    () -> invio.invia(opt, invioPersona(id), CHIAVE));

            assertThat(((RichiestaSintesi) esito.secondo()).id()).isEqualTo(esito.primo().id());
            assertThat(rigaMisura(id)).endsWith("|false");
            assertThat(richiesteDellaOpt()).containsExactly(esito.primo().id() + "|" + id + "|PERSONA");
        }

        @Test
        @DisplayName("Ramo dopo il lock: con dati diversi risponde con il conflitto e non salva nulla")
        void dopoIlLockDatiDiversi() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> registraEDisattiva(id),
                    true,
                    letturaBloccanteDi("catalogo_misure"),
                    () -> invio.invia(opt, invioNucleo(id), CHIAVE));

            assertThat(esito.secondo()).isExactlyInstanceOf(ConflittoRichiestaException.class);
            assertThat(richiesteDellaOpt()).containsExactly(esito.primo().id() + "|" + id + "|PERSONA");
        }

        /**
         * Il primo comando registra l'invio, disattiva la misura e tiene il lock esclusivo della tabella
         * del catalogo. Il ritento non trova ancora l'invio (non confermato) e si ferma sulla lettura senza
         * lock della misura; quando la riprende la trova disattivata e rilegge l'invio confermato.
         */
        @Test
        @DisplayName("Ramo del controllo senza lock: con gli stessi dati restituisce la richiesta già registrata")
        void controlloSenzaLockStessiDati() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> registraDisattivaETieneIlCatalogo(id),
                    true,
                    letturaSenzaLockDi("catalogo_misure"),
                    () -> invio.invia(opt, invioPersona(id), CHIAVE));

            assertThat(((RichiestaSintesi) esito.secondo()).id()).isEqualTo(esito.primo().id());
            assertThat(rigaMisura(id)).endsWith("|false");
            assertThat(richiesteDellaOpt()).containsExactly(esito.primo().id() + "|" + id + "|PERSONA");
        }

        @Test
        @DisplayName("Ramo del controllo senza lock: con dati diversi risponde con il conflitto e non salva nulla")
        void controlloSenzaLockDatiDiversi() throws Exception {
            long id = creaMisura("T01");

            var esito = contesa(
                    () -> registraDisattivaETieneIlCatalogo(id),
                    true,
                    letturaSenzaLockDi("catalogo_misure"),
                    () -> invio.invia(opt, invioNucleo(id), CHIAVE));

            assertThat(esito.secondo()).isExactlyInstanceOf(ConflittoRichiestaException.class);
            assertThat(richiesteDellaOpt()).containsExactly(esito.primo().id() + "|" + id + "|PERSONA");
        }

        private RichiestaSintesi registraEDisattiva(long id) {
            RichiestaSintesi registrata = invio.invia(opt, invioPersona(id), CHIAVE);
            gestione.disattivaMisura(banco, id);
            return registrata;
        }

        private RichiestaSintesi registraDisattivaETieneIlCatalogo(long id) {
            RichiestaSintesi registrata = registraEDisattiva(id);
            jdbc.execute("lock table catalogo_misure in access exclusive mode");
            return registrata;
        }
    }

    // ---- contesa con ordine fissato -----------------------------------------------------------

    /** Esito del comando che tiene il lock e del comando in coda (risultato o eccezione applicativa). */
    private record Contesa<P>(P primo, Object secondo) {
    }

    /**
     * Esegue {@code primo} in una transazione che resta aperta, avvia {@code secondo} e aspetta di
     * vederlo in coda dietro al processo PostgreSQL del primo, fermo su un'istruzione che soddisfa
     * {@code attesa}. Poi conferma o annulla la prima transazione e restituisce i due esiti.
     *
     * @param primo comando che prende i lock; un'eccezione fa fallire il test
     * @param conferma {@code true} per confermare la prima transazione, {@code false} per annullarla
     * @param attesa istruzione su cui il secondo comando deve risultare fermo
     * @param secondo comando in coda; un'eccezione applicativa diventa il suo esito
     */
    private <P> Contesa<P> contesa(Callable<P> primo, boolean conferma, Attesa attesa, Callable<?> secondo)
            throws Exception {
        CountDownLatch lockPresi = new CountDownLatch(1);
        CountDownLatch rilascia = new CountDownLatch(1);
        AtomicInteger processoDelPrimo = new AtomicInteger();

        // close() aspetta la fine di entrambi i comandi su ogni uscita, anche se un'attesa fallisce.
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<P> futuroPrimo = pool.submit(() -> new TransactionTemplate(transazioni).execute(stato -> {
                P esito = chiama(primo);
                processoDelPrimo.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                lockPresi.countDown();
                aspetta(rilascia);
                if (!conferma) {
                    stato.setRollbackOnly();
                }
                return esito;
            }));
            Future<Object> futuroSecondo;
            try {
                if (!lockPresi.await(ATTESA_SECONDI, TimeUnit.SECONDS)) {
                    throw new AssertionError("Il primo comando non ha preso i lock entro " + ATTESA_SECONDI
                            + " secondi" + causa(futuroPrimo));
                }
                futuroSecondo = pool.submit(() -> esitoDi(secondo));
                attendiInCoda(processoDelPrimo.get(), attesa);
            } finally {
                rilascia.countDown();
            }
            return new Contesa<>(futuroPrimo.get(30, TimeUnit.SECONDS), futuroSecondo.get(30, TimeUnit.SECONDS));
        }
    }

    /**
     * Aspetta che un processo sia bloccato da quello del primo comando su un'istruzione che soddisfa
     * l'attesa indicata; fallisce dopo {@value #ATTESA_SECONDI} secondi elencando le istruzioni in coda.
     */
    private void attendiInCoda(int processoBloccante, Attesa attesa) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ATTESA_SECONDI);
        List<String> inCoda = List.of();
        while (System.nanoTime() < limite) {
            inCoda = jdbc.queryForList("""
                    select query from pg_stat_activity where ? = any(pg_blocking_pids(pid))
                    """, String.class, processoBloccante);
            if (inCoda.stream().map(q -> q.toLowerCase(Locale.ROOT)).anyMatch(attesa.istruzione())) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Il secondo comando non risulta in coda (" + attesa.descrizione()
                + ") dietro al processo " + processoBloccante + "; istruzioni in coda: " + inCoda);
    }

    /** Istruzione SQL su cui il secondo comando deve fermarsi, con la descrizione per il messaggio d'errore. */
    private record Attesa(String descrizione, Predicate<String> istruzione) {
    }

    private static Attesa inserimentoIn(String tabella) {
        return new Attesa("inserimento in " + tabella, q -> q.contains("insert into " + tabella));
    }

    /** Lettura con {@code FOR SHARE} o {@code FOR UPDATE} di una riga della tabella. */
    private static Attesa letturaBloccanteDi(String tabella) {
        return new Attesa("lettura con lock di " + tabella,
                q -> q.contains(tabella) && !q.contains("insert") && (q.contains(" share") || q.contains(" update")));
    }

    /** Lettura semplice della tabella, ferma solo per il lock esclusivo sulla tabella intera. */
    private static Attesa letturaSenzaLockDi(String tabella) {
        return new Attesa("lettura senza lock di " + tabella,
                q -> q.contains(tabella) && q.strip().startsWith("select") && !q.contains(" share") && !q.contains(" update"));
    }

    private static <T> T chiama(Callable<T> comando) {
        try {
            return comando.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Risultato del comando oppure l'eccezione applicativa che ha sollevato. */
    private static Object esitoDi(Callable<?> comando) throws Exception {
        try {
            return comando.call();
        } catch (RuntimeException ex) {
            return ex;
        }
    }

    private static void aspetta(CountDownLatch segnale) {
        try {
            if (!segnale.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Il test non ha rilasciato il primo comando entro 30 secondi");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static String causa(Future<?> futuro) throws InterruptedException {
        try {
            futuro.get(1, TimeUnit.SECONDS);
            return "";
        } catch (ExecutionException ex) {
            return ": " + ex.getCause();
        } catch (TimeoutException ex) {
            return "";
        }
    }

    // ---- dati ----------------------------------------------------------------------------------

    private long creaMisura(String codice) {
        return gestione.creaMisura(banco, new DatiMisura(codice, "Nome iniziale", "Descrizione", false)).id();
    }

    private static DatiInvio invioPersona(long misura) {
        return new DatiInvio(misura, TipoDestinatario.PERSONA, "napoli", "Invio di prova");
    }

    private static DatiInvio invioNucleo(long misura) {
        return new DatiInvio(misura, TipoDestinatario.NUCLEO, "napoli", "Invio di prova");
    }

    /** Riga della misura come {@code codice|nome|descrizione|erogabileADistanza|attiva}. */
    private String rigaMisura(long id) {
        return jdbc.queryForObject("""
                select codice, nome, descrizione, erogabile_a_distanza, attiva from catalogo_misure where id = ?
                """, (rs, n) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|"
                + rs.getBoolean(4) + "|" + rs.getBoolean(5), id);
    }

    /** Misure diverse dalle dodici iniziali come {@code id|codice|nome|descrizione|distanza|attiva}. */
    private List<String> misureDiProva() {
        return jdbc.query("select id, codice, nome, descrizione, erogabile_a_distanza, attiva from catalogo_misure "
                + "where codice not in (" + elencoIniziali() + ") order by id",
                (rs, n) -> rs.getLong(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|" + rs.getString(4)
                        + "|" + rs.getBoolean(5) + "|" + rs.getBoolean(6));
    }

    /** Richieste della OPT come {@code id|misura|tipoDestinatario}, ordinate per id. */
    private List<String> richiesteDellaOpt() {
        return new ArrayList<>(jdbc.query("""
                select id, misura_id, tipo_destinatario from richieste where titolare_id = ? order by id
                """, (rs, n) -> rs.getString(1) + "|" + rs.getLong(2) + "|" + rs.getString(3), opt.accountId()));
    }

    /** Righe dell'offerta della OPT come {@code misura:tramiteProfessionista:attiva}, ordinate per misura. */
    private List<String> righeOfferta() {
        return jdbc.query("""
                select misura_id, tramite_professionista, attiva from catalogo_offerte_opt
                where opt_id = ? order by misura_id
                """, (rs, n) -> rs.getLong(1) + ":" + rs.getBoolean(2) + ":" + rs.getBoolean(3), opt.accountId());
    }

    private static String elencoIniziali() {
        return String.join(",", CODICI_INIZIALI.stream().map(c -> "'" + c + "'").toList());
    }

    /** Svuota i dati dinamici e riporta il catalogo alle dodici misure delle migrazioni. */
    private void ripristinaCatalogo() {
        dati.svuota();
        jdbc.update("delete from catalogo_misure where codice not in (" + elencoIniziali() + ")");
    }
}
