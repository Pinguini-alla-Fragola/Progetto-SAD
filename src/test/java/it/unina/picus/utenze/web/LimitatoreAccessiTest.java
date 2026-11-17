package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica la finestra, il blocco temporaneo e la registrazione concorrente dei tentativi (#25,
 * AC3). Oggetti reali e un orologio finto scritto a mano; nessuna dipendenza da Spring, tranne nel
 * gruppo {@link AttraversoIlLogin}, che verifica la scadenza del blocco con il login JSON reale.
 *
 * <p>I gruppi unitari ricavano gli istanti dalle costanti di {@link LimitatoreAccessi}, perché
 * verificano l'algoritmo. Il gruppo {@link AttraversoIlLogin} usa invece i valori fissati dalla #25
 * (cinque fallimenti in cinque minuti, blocco di sessanta secondi), scritti come numeri: se il
 * comportamento del login cambiasse, quei test devono fallire.
 *
 * <p>Tutti gli istanti sono ricavati dalle costanti di {@link LimitatoreAccessi}: se cambiano
 * limite, attesa o finestra, i test restano validi senza modifiche. I nomi mostrati non riportano
 * numeri per non diventare falsi quando le costanti cambiano.
 */
@DisplayName("Limitatore dei tentativi di accesso")
class LimitatoreAccessiTest {

    private static final Instant T0 = Instant.parse("2026-11-17T10:00:00Z");
    private static final int MAX = LimitatoreAccessi.MAX_FALLIMENTI;
    private static final Duration ATTESA = LimitatoreAccessi.ATTESA;
    private static final Duration FINESTRA = LimitatoreAccessi.FINESTRA;
    private static final Duration UN_MS = Duration.ofMillis(1);
    private static final String EMAIL = "a@picus.test";

    private OrologioFinto orologio;
    private LimitatoreAccessi limitatore;

    /** I test presuppongono questi rapporti fra le costanti: se saltano, meglio saperlo subito. */
    @BeforeAll
    static void verificaCostanti() {
        assertTrue(MAX >= 2, "servono almeno due tentativi ammessi");
        assertTrue(ATTESA.compareTo(Duration.ZERO) > 0, "l'attesa deve essere positiva");
        assertTrue(FINESTRA.compareTo(ATTESA.plusSeconds(MAX)) > 0,
                "la finestra deve contenere l'attesa più un tentativo al secondo");
    }

    @BeforeEach
    void prepara() {
        orologio = new OrologioFinto(T0);
        limitatore = new LimitatoreAccessi(orologio);
    }

    @Nested
    @DisplayName("Blocco temporaneo")
    class Blocco {

        @Test
        @DisplayName("I tentativi fino al limite sono ammessi, quello successivo è rifiutato")
        void oltreIlLimiteRifiutato() {
            ammettiN(EMAIL, MAX);
            assertFalse(limitatore.ammetti(EMAIL));
        }

        @Test
        @DisplayName("Il blocco si conta dall'ultimo tentativo registrato, non dal primo")
        void bloccoDallUltimoTentativo() {
            // Un tentativo al secondo: primo e ultimo cadono in istanti diversi.
            for (int i = 0; i < MAX; i++) {
                orologio.imposta(T0.plusSeconds(i));
                assertTrue(limitatore.ammetti(EMAIL), "tentativo " + (i + 1));
            }
            Instant ultimo = T0.plusSeconds(MAX - 1);

            // Un limitatore che contasse dal primo tentativo qui sarebbe già sbloccato.
            orologio.imposta(ultimo.plus(ATTESA).minus(UN_MS));
            assertFalse(limitatore.ammetti(EMAIL));

            orologio.imposta(ultimo.plus(ATTESA));
            assertTrue(limitatore.ammetti(EMAIL));
        }

        @Test
        @DisplayName("Un tentativo rifiutato non viene registrato e non prolunga il blocco")
        void tentativoRifiutatoNonProlungaIlBlocco() {
            ammettiN(EMAIL, MAX);

            orologio.imposta(T0.plus(ATTESA.dividedBy(2)));
            assertFalse(limitatore.ammetti(EMAIL));

            // Se il rifiuto fosse registrato, il blocco durerebbe fino a T0 + 1,5 × ATTESA.
            orologio.imposta(T0.plus(ATTESA));
            assertTrue(limitatore.ammetti(EMAIL));
        }
    }

    @Nested
    @DisplayName("Finestra di osservazione")
    class Finestra {

        @Test
        @DisplayName("Un tentativo un millisecondo prima della fine della finestra conta ancora")
        void tentativoAncoraNellaFinestra() {
            ammettiN(EMAIL, MAX - 1);

            orologio.imposta(T0.plus(FINESTRA).minus(UN_MS));
            assertTrue(limitatore.ammetti(EMAIL));   // raggiunge il limite insieme a quelli di T0
            assertFalse(limitatore.ammetti(EMAIL));
        }

        @Test
        @DisplayName("Un tentativo vecchio esattamente quanto la finestra conta ancora")
        void confineDellaFinestraIncluso() {
            ammettiN(EMAIL, MAX - 1);

            orologio.imposta(T0.plus(FINESTRA));
            assertTrue(limitatore.ammetti(EMAIL));   // quelli di T0 sono ancora nella finestra
            assertFalse(limitatore.ammetti(EMAIL));
        }

        @Test
        @DisplayName("I tentativi più vecchi della finestra non contano più")
        void tentativiFuoriFinestraDimenticati() {
            ammettiN(EMAIL, MAX - 1);                       // tutti a T0

            Instant ultimoUtile = T0.plus(FINESTRA).minus(ATTESA);
            orologio.imposta(ultimoUtile);
            assertTrue(limitatore.ammetti(EMAIL));          // raggiunge il limite
            orologio.imposta(ultimoUtile.plus(ATTESA.dividedBy(2)));
            assertFalse(limitatore.ammetti(EMAIL));

            // Escono i tentativi di T0 e il blocco è finito: resta solo quello di ultimoUtile,
            // quindi ne servono MAX - 1 per tornare al limite.
            orologio.imposta(T0.plus(FINESTRA).plus(UN_MS));
            for (int i = 0; i < MAX - 1; i++) {
                assertTrue(limitatore.ammetti(EMAIL), "tentativo " + (i + 1) + " dopo la finestra");
            }
            assertFalse(limitatore.ammetti(EMAIL));
        }
    }

    @Nested
    @DisplayName("Chiave e contatori")
    class Chiavi {

        @Test
        @DisplayName("Ogni email ha il proprio contatore")
        void contatoriIndipendenti() {
            ammettiN(EMAIL, MAX);
            assertFalse(limitatore.ammetti(EMAIL));
            assertTrue(limitatore.ammetti("b@picus.test"));
        }

        @Test
        @DisplayName("La chiave normalizza spazi e maiuscole, gestisce null e tronca alla lunghezza massima di un'email")
        void normalizzazioneDellaChiave() {
            assertEquals("a@picus.test", LimitatoreAccessi.chiave("  A@Picus.TEST "));
            assertEquals("", LimitatoreAccessi.chiave(null));
            assertEquals(254, LimitatoreAccessi.chiave("a".repeat(300)).length());
        }

        @Test
        @DisplayName("Varianti della stessa email, normalizzate con chiave(), consumano lo stesso contatore")
        void variantiDellaStessaEmail() {
            // Il limitatore riceve chiavi già normalizzate: è AutenticazionePicus a chiamare chiave().
            ammettiN(LimitatoreAccessi.chiave(EMAIL), MAX - 1);
            assertTrue(limitatore.ammetti(LimitatoreAccessi.chiave("  A@PICUS.test ")));
            assertFalse(limitatore.ammetti(LimitatoreAccessi.chiave("a@Picus.Test")));
        }

        @Test
        @DisplayName("Un'email null diventa la chiave vuota e non provoca eccezioni")
        void emailNull() {
            String chiave = LimitatoreAccessi.chiave(null);
            assertDoesNotThrow(() -> limitatore.ammetti(chiave));
            assertDoesNotThrow(() -> limitatore.azzera(chiave));
        }
    }

    @Nested
    @DisplayName("Azzeramento dopo un accesso riuscito")
    class Azzeramento {

        @Test
        @DisplayName("Un accesso riuscito azzera i tentativi precedenti")
        void azzeraCancellaITentativi() {
            ammettiN(EMAIL, MAX);
            limitatore.azzera(EMAIL);

            ammettiN(EMAIL, MAX);
            assertFalse(limitatore.ammetti(EMAIL));
        }

        @Test
        @DisplayName("Una variante normalizzata dell'email azzera il contatore della stessa chiave")
        void azzeraNormalizzaLaChiave() {
            ammettiN(EMAIL, MAX);
            limitatore.azzera(LimitatoreAccessi.chiave("  A@Picus.Test "));

            assertTrue(limitatore.ammetti(EMAIL));
        }

        @Test
        @DisplayName("Azzerare un'email non toglie il blocco alle altre")
        void azzeraSoloLaPropriaChiave() {
            ammettiN(EMAIL, MAX);
            limitatore.azzera("b@picus.test");

            assertFalse(limitatore.ammetti(EMAIL));
        }
    }

    @Nested
    @DisplayName("Limite di memoria")
    class LimiteDiMemoria {

        @Test
        @DisplayName("A mappa piena un'email nuova è rifiutata, finché le chiavi scadute non si liberano")
        void numeroMassimoDiChiavi() {
            riempiMappa(LimitatoreAccessi.MAX_CHIAVI, "k");
            assertEquals(LimitatoreAccessi.MAX_CHIAVI, limitatore.numeroChiavi());

            assertFalse(limitatore.ammetti("nuova@picus.test"));
            assertEquals(LimitatoreAccessi.MAX_CHIAVI, limitatore.numeroChiavi());

            // Scadute tutte le chiavi, la mappa deve tornare a contenerne di nuovo il massimo...
            orologio.imposta(T0.plus(FINESTRA).plus(UN_MS));
            riempiMappa(LimitatoreAccessi.MAX_CHIAVI, "n");
            // ...e il limite continua a valere.
            assertFalse(limitatore.ammetti("ancora@picus.test"));
        }

        @Test
        @DisplayName("Un'ondata di email inventate non svuota il contatore di un account già sotto tentativo")
        void ondataDiEmailNonCancellaIlContatore() {
            ammettiN("vittima@picus.test", MAX - 1);
            riempiMappa(LimitatoreAccessi.MAX_CHIAVI - 1, "k");
            assertEquals(LimitatoreAccessi.MAX_CHIAVI, limitatore.numeroChiavi());

            assertFalse(limitatore.ammetti("inventata@picus.test"));

            assertTrue(limitatore.ammetti("vittima@picus.test"));   // raggiunge il limite
            assertFalse(limitatore.ammetti("vittima@picus.test"));  // il contatore era intatto
        }
    }

    /**
     * Esecuzioni concorrenti. Passare non dimostra l'assenza di race condition, ma un'implementazione
     * non atomica (leggi-controlla-scrivi separati) qui fallisce quasi sempre.
     */
    @Nested
    @DisplayName("Concorrenza")
    class Concorrenza {

        @Test
        @DisplayName("Tentativi concorrenti sulla stessa email: ne vengono ammessi esattamente quanti il limite")
        void concorrenzaSullaStessaChiave() throws Exception {
            List<Callable<Boolean>> compiti = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                compiti.add(() -> limitatore.ammetti(EMAIL));
            }

            assertEquals(MAX, contaAmmessi(eseguiInParallelo(compiti)));
            assertEquals(1, limitatore.numeroChiavi());
        }

        @Test
        @DisplayName("Tentativi concorrenti su email diverse non si influenzano")
        void concorrenzaSuChiaviDiverse() throws Exception {
            int chiavi = 10;
            int tentativiPerChiave = MAX * 2;
            List<Callable<Boolean>> compiti = new ArrayList<>();
            for (int k = 0; k < chiavi; k++) {
                String chiave = "utente" + k + "@picus.test";
                for (int i = 0; i < tentativiPerChiave; i++) {
                    compiti.add(() -> limitatore.ammetti(chiave));
                }
            }

            assertEquals((long) chiavi * MAX, contaAmmessi(eseguiInParallelo(compiti)));
            assertEquals(chiavi, limitatore.numeroChiavi());
        }
    }

    /**
     * Scadenza del blocco attraverso il login JSON reale, con PostgreSQL e la catena di sicurezza. Il
     * limitatore riceve l'orologio quando nasce il contesto, quindi un orologio finto va registrato nel
     * contesto stesso ({@link OrologioDelContesto}): Spring crea per questo gruppo un contesto separato
     * da quello condiviso dagli altri test di integrazione, sullo stesso container.
     */
    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @Import(OrologioDelContesto.class)
    @DisplayName("Scadenza del blocco attraverso il login su PostgreSQL")
    class AttraversoIlLogin extends IntegrazionePostgresBase {

        private static final String PASSWORD = "Password-di-prova-1";

        // Valori della #25 (AC3), non letti dal codice di produzione.
        private static final int FALLIMENTI_AMMESSI = 5;
        private static final Duration FINESTRA_DELLA_STORIA = Duration.ofMinutes(5);
        private static final Duration ATTESA_DELLA_STORIA = Duration.ofSeconds(60);

        @Autowired
        private MockMvc mvc;
        @Autowired
        private JdbcTemplate jdbc;
        @Autowired
        private LimitatoreAccessi limitatoreDelContesto;
        @Autowired
        private OrologioFinto orologioDelContesto;

        private String email;

        @BeforeEach
        void preparaAccount() {
            orologioDelContesto.imposta(T0);
            limitatoreDelContesto.azzeraTutto();
            UUID id = UUID.randomUUID();
            email = "t-" + id + "@picus.test";
            jdbc.update("""
                    insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                    values (?, ?, ?, ?, ?, ?)
                    """, id, email, new BCryptPasswordEncoder(4).encode(PASSWORD), "Account di prova", "OPT", "ATTIVO");
        }

        @Test
        @DisplayName("Il login torna possibile esattamente dopo l'attesa e i tentativi rifiutati non la prolungano")
        void bloccoScadeDopoLAttesa() throws Exception {
            for (int i = 0; i < FALLIMENTI_AMMESSI; i++) {
                assertEquals(401, statoLogin("sbagliata"), "tentativo " + (i + 1));
            }
            assertEquals(429, statoLogin(PASSWORD), "sesto tentativo, subito dopo i cinque fallimenti");

            orologioDelContesto.imposta(T0.plus(ATTESA_DELLA_STORIA.dividedBy(2)));
            assertEquals(429, statoLogin(PASSWORD), "a metà dell'attesa");

            orologioDelContesto.imposta(T0.plus(ATTESA_DELLA_STORIA).minus(UN_MS));
            assertEquals(429, statoLogin(PASSWORD), "un millisecondo prima della fine dell'attesa");

            // Se i rifiuti fossero registrati, il blocco durerebbe ancora.
            orologioDelContesto.imposta(T0.plus(ATTESA_DELLA_STORIA));
            assertEquals(204, statoLogin(PASSWORD), "alla fine dell'attesa");
        }

        @Test
        @DisplayName("Un fallimento di 5 minuti meno 1 ms prima conta ancora: il tentativo successivo è bloccato")
        void fallimentiContanoPerTuttaLaFinestra() throws Exception {
            for (int i = 0; i < FALLIMENTI_AMMESSI - 1; i++) {
                assertEquals(401, statoLogin("sbagliata"), "tentativo " + (i + 1) + " a T0");
            }

            // Con una finestra più corta di 5 minuti i fallimenti di T0 sarebbero già scaduti e qui
            // non arriverebbe il 429.
            orologioDelContesto.imposta(T0.plus(FINESTRA_DELLA_STORIA).minus(UN_MS));
            assertEquals(401, statoLogin("sbagliata"), "quinto fallimento, ancora nella finestra");
            assertEquals(429, statoLogin(PASSWORD), "tentativo subito dopo il quinto fallimento");
        }

        @Test
        @DisplayName("I tentativi falliti più vecchi della finestra non contano più per il blocco")
        void fallimentiFuoriFinestraDimenticati() throws Exception {
            for (int i = 0; i < FALLIMENTI_AMMESSI - 1; i++) {
                assertEquals(401, statoLogin("sbagliata"), "tentativo " + (i + 1) + " a T0");
            }

            // Se quelli di T0 contassero ancora, qui il limite verrebbe superato e arriverebbe un 429.
            orologioDelContesto.imposta(T0.plus(FINESTRA_DELLA_STORIA).plus(UN_MS));
            for (int i = 0; i < FALLIMENTI_AMMESSI - 1; i++) {
                assertEquals(401, statoLogin("sbagliata"), "tentativo " + (i + 1) + " dopo la finestra");
            }
            assertEquals(204, statoLogin(PASSWORD));
        }

        private int statoLogin(String password) throws Exception {
            return mvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                    .andReturn().getResponse().getStatus();
        }
    }

    /** Orologio finto del contesto Spring: prevale su quello di {@code ConfigurazioneTempo}. */
    @TestConfiguration(proxyBeanMethods = false)
    static class OrologioDelContesto {

        @Bean
        @Primary
        OrologioFinto orologioFinto() {
            return new OrologioFinto(T0);
        }
    }

    // ---------------------------------------------------------------- supporto

    private void ammettiN(String chiave, int n) {
        for (int i = 0; i < n; i++) {
            assertTrue(limitatore.ammetti(chiave), "tentativo " + (i + 1) + " di " + n);
        }
    }

    private void riempiMappa(int quante, String prefisso) {
        for (int i = 0; i < quante; i++) {
            assertTrue(limitatore.ammetti(prefisso + i), "chiave " + prefisso + i);
        }
    }

    private static long contaAmmessi(List<Boolean> esiti) {
        return esiti.stream().filter(Boolean::booleanValue).count();
    }

    /** Fa partire tutti i compiti nello stesso istante e ne raccoglie gli esiti. */
    private static List<Boolean> eseguiInParallelo(List<Callable<Boolean>> compiti) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(compiti.size());
        try {
            CountDownLatch pronti = new CountDownLatch(compiti.size());
            CountDownLatch via = new CountDownLatch(1);
            List<Future<Boolean>> futuri = new ArrayList<>();
            for (Callable<Boolean> compito : compiti) {
                futuri.add(pool.submit(() -> {
                    pronti.countDown();
                    via.await();
                    return compito.call();
                }));
            }
            assertTrue(pronti.await(10, TimeUnit.SECONDS), "i thread non sono partiti in tempo");
            via.countDown();
            List<Boolean> esiti = new ArrayList<>();
            for (Future<Boolean> futuro : futuri) {
                esiti.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return esiti;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Fake manuale dell'orologio: l'istante cambia solo quando il test lo decide. */
    private static final class OrologioFinto extends Clock {
        private volatile Instant istante;

        OrologioFinto(Instant iniziale) {
            this.istante = iniziale;
        }

        void imposta(Instant nuovo) {
            this.istante = nuovo;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return istante;
        }
    }
}