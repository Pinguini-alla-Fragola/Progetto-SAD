package it.unina.picus.utenze.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.ReinvioLinkSuRichiesta;

/**
 * Strumenti comuni ai test della #154 (storia #27): modulo di creazione, chiamate HTTP, link ricevuti
 * per email e letture dirette su PostgreSQL.
 *
 * <p>Il link in chiaro esiste solo nell'email: i test lo leggono dagli argomenti passati a
 * {@link InvioEmail}, che nelle classi della #154 è un {@code @MockitoSpyBean} dichiarato sempre allo
 * stesso modo, così tutte condividono un unico contesto Spring. Nel database c'è solo l'impronta
 * SHA-256, che qui si ricalcola per collegare il link alla riga salvata.</p>
 *
 * <p>Tutti i dati sono inventati: email sul dominio riservato {@code picus.test} e codici fiscali
 * sintetici con carattere di controllo corretto.</p>
 */
public final class OperatoriDiProva {

    public static final String INTERNO = "INTERNO_BANCO";
    public static final String ESTERNO = "ESTERNO_PAGATO_BANCO";
    public static final String NOME = "Nome";
    public static final String COGNOME = "Cognome";
    public static final String NOME_VISUALIZZATO = "Nome Cognome";
    public static final String TELEFONO = "+39 081 0000154";
    public static final String COMUNE = "napoli";
    public static final String PASSWORD = "Password-di-prova-154";

    /** Codici fiscali inventati, tutti con carattere di controllo corretto. */
    public static final String CF_A = "PRVOPR80A01F839C";
    public static final String CF_B = "TSTPRV85B41F839K";
    public static final String CF_C = "SNTDTI90C10F839K";
    public static final String CF_D = "FTZNMO75D50F839B";
    public static final String CF_E = "PRVCNC88E15F839W";
    public static final String CF_F = "TSTDUE70H60F839C";

    public static final String EMAIL_IN_USO = "Questo indirizzo email è già in uso";
    public static final String EMAIL_DI_ACCOUNT_REVOCATO =
            "Questo indirizzo appartiene a un account revocato: si può riutilizzare solo riattivando la stessa utenza";
    public static final String CODICE_FISCALE_IN_USO = "Esiste già un operatore con questo codice fiscale";
    public static final String MESSAGGIO_NUOVO_LINK =
            "Se l'indirizzo corrisponde a un account in attesa di attivazione, riceverai un nuovo link via email.";
    public static final Duration VALIDITA_LINK = Duration.ofHours(72);

    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(15);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;

    public OperatoriDiProva(MockMvc mvc, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    /** Operatore creato via HTTP, con il link ricevuto alla creazione. */
    public record OperatoreDiProva(UUID id, String email, String link) {}

    // modulo e chiamate HTTP

    /** Modulo valido e modificabile, con i campi nell'ordine del browser. */
    public static Map<String, Object> modulo(String email, String codiceFiscale, String tipologia, long... misure) {
        Map<String, Object> modulo = new LinkedHashMap<>();
        modulo.put("nome", NOME);
        modulo.put("cognome", COGNOME);
        modulo.put("codiceFiscale", codiceFiscale);
        modulo.put("email", email);
        modulo.put("telefono", TELEFONO);
        modulo.put("comuneCodice", COMUNE);
        modulo.put("tipologia", tipologia);
        List<Object> elenco = new ArrayList<>();
        for (long misura : misure) {
            elenco.add(misura);
        }
        modulo.put("misure", elenco);
        modulo.put("centroPicus", false);
        return modulo;
    }

    public static String json(Object valore) {
        return JSON.writeValueAsString(valore);
    }

    /** Creazione da parte di un account Banco presente nel database, con token CSRF valido. */
    public ResultActions crea(UUID banco, Object corpo) throws Exception {
        return creaCome(banco, "BANCO", corpo);
    }

    /** Creazione con un utente simulato del ruolo indicato, che ha un account con lo stesso ruolo nel database. */
    public ResultActions creaCome(UUID account, String ruolo, Object corpo) throws Exception {
        return mvc.perform(conCorpo(post("/api/utenze/operatori"), corpo)
                .with(user(account.toString()).roles(ruolo))
                .with(csrf()));
    }

    /** Crea un Operatore valido, verifica il {@code 201} e restituisce l'id e il link consegnato. */
    public OperatoreDiProva creaValido(UUID banco, InvioEmail posta, String email, String codiceFiscale,
            String tipologia, long... misure) throws Exception {
        String corpo = crea(banco, modulo(email, codiceFiscale, tipologia, misure))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(corpo, "$.id"));
        return new OperatoreDiProva(id, email, ultimoLink(posta, email, 1));
    }

    public ResultActions paginaAttivazione(String token) throws Exception {
        MockHttpServletRequestBuilder richiesta = get("/attiva");
        if (token != null) {
            richiesta.param("token", token);
        }
        return mvc.perform(richiesta);
    }

    public ResultActions attiva(String token, String password) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("token", token);
        corpo.put("nuovaPassword", password);
        return mvc.perform(conCorpo(post("/api/auth/attiva/conferma"), corpo).with(csrf()));
    }

    /** Nuovo link chiesto senza sessione da chi possiede l'indirizzo. */
    public ResultActions richiediNuovoLink(Object email) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("email", email);
        return mvc.perform(conCorpo(post("/api/auth/attiva/reinvio"), corpo).with(csrf()));
    }

    /** Reinvio del link da parte di un utente simulato del ruolo indicato. */
    public ResultActions reinvioDelBanco(UUID account, String ruolo, UUID operatore) throws Exception {
        return mvc.perform(post("/api/operatori/" + operatore + "/reinvia-invito")
                .with(user(account.toString()).roles(ruolo))
                .with(csrf()));
    }

    public MvcResult accedi(String email, String password) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("email", email);
        corpo.put("password", password);
        return mvc.perform(conCorpo(post("/api/auth/login"), corpo).with(csrf())).andReturn();
    }

    /** Accesso reale con email e password: verifica il {@code 204} e restituisce la sessione autenticata. */
    public MockHttpSession sessioneDopoAccesso(String email, String password) throws Exception {
        MvcResult esito = accedi(email, password);
        assertThat(esito.getResponse().getStatus()).as("accesso di %s", email).isEqualTo(204);
        MockHttpSession sessione = (MockHttpSession) esito.getRequest().getSession(false);
        assertThat(sessione).isNotNull();
        return sessione;
    }

    private static MockHttpServletRequestBuilder conCorpo(MockHttpServletRequestBuilder richiesta, Object corpo) {
        return richiesta.contentType(MediaType.APPLICATION_JSON)
                .content(corpo instanceof String testo ? testo : json(corpo));
    }

    // email consegnate

    /**
     * Link consegnati a un indirizzo, nell'ordine di invio. Attende, per il lavoro in secondo piano, che ne
     * siano arrivati almeno {@code attesi}.
     */
    public static List<String> linkConsegnati(InvioEmail posta, String email, int attesi) {
        // Prima si attende e poi si cattura: con timeout() il captor registrerebbe i valori a ogni nuovo tentativo.
        verify(posta, timeout(ATTESA_MASSIMA.toMillis()).atLeast(attesi))
                .inviaLinkAttivazione(eq(email), anyString(), anyString());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(posta, atLeast(attesi)).inviaLinkAttivazione(eq(email), anyString(), token.capture());
        return token.getAllValues();
    }

    public static String ultimoLink(InvioEmail posta, String email, int attesi) {
        List<String> link = linkConsegnati(posta, email, attesi);
        return link.get(link.size() - 1);
    }

    /** Impronta SHA-256 in esadecimale, la stessa che il server salva al posto del link. */
    public static String impronta(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Attende la fine del lavoro in secondo piano dei nuovi link chiesti con l'email: ogni richiesta accodata finora,
     * compresa quella sotto test, è stata elaborata fino all'ultimo passo, tentativi della conclusione compresi.
     *
     * <p>Il segnale non usa i contatori dell'esecutore, che sono approssimati. Si accoda una sentinella per ogni
     * thread dell'esecutore e si attende che siano tutte in esecuzione nello stesso momento. La coda è FIFO e ogni
     * thread esegue un lavoro alla volta: quando tutti i thread sono occupati da una sentinella, nessuno sta più
     * eseguendo una richiesta accodata prima, quindi tutte sono concluse. Poi le sentinelle vengono rilasciate.</p>
     */
    public static void attendiReinviiConclusi(ReinvioLinkSuRichiesta reinvio) {
        // Il bersaglio va tipizzato come Object: un tipo generico farebbe scegliere getField(Class, String).
        Object bersaglio = AopTestUtils.getUltimateTargetObject(reinvio);
        Object campo = ReflectionTestUtils.getField(bersaglio, "esecutore");
        assertThat(campo).isInstanceOf(ThreadPoolExecutor.class);
        ThreadPoolExecutor esecutore = (ThreadPoolExecutor) campo;
        // Con un numero fisso di thread le sentinelle li occupano tutti; con un pool variabile il segnale non varrebbe.
        assertThat(esecutore.getCorePoolSize()).isEqualTo(esecutore.getMaximumPoolSize());
        int thread = esecutore.getMaximumPoolSize();
        CountDownLatch sentinelleInEsecuzione = new CountDownLatch(thread);
        CountDownLatch rilascio = new CountDownLatch(1);
        try {
            for (int i = 0; i < thread; i++) {
                esecutore.execute(() -> {
                    sentinelleInEsecuzione.countDown();
                    try {
                        rilascio.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(sentinelleInEsecuzione.await(ATTESA_MASSIMA.toMillis(), TimeUnit.MILLISECONDS))
                    .as("tutti i thread del reinvio occupati dalle sentinelle entro %s", ATTESA_MASSIMA)
                    .isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Attesa delle sentinelle interrotta");
        } finally {
            rilascio.countDown();
        }
    }

    /** Attende che la condizione diventi vera, per il lavoro che il server svolge in secondo piano. */
    public static void attendi(String descrizione, BooleanSupplier condizione) {
        Instant limite = Instant.now().plus(ATTESA_MASSIMA);
        while (!condizione.getAsBoolean()) {
            if (Instant.now().isAfter(limite)) {
                fail("Condizione non raggiunta entro %s: %s", ATTESA_MASSIMA, descrizione);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Attesa interrotta: %s", descrizione);
            }
        }
    }

    // letture e scritture dirette sul database

    public UUID accountPerEmail(String email) {
        return jdbc.queryForObject("select id from utenze_account where email = ?", UUID.class, email);
    }

    public Map<String, Object> account(UUID id) {
        return jdbc.queryForMap("select * from utenze_account where id = ?", id);
    }

    public String stato(UUID id) {
        return jdbc.queryForObject("select stato from utenze_account where id = ?", String.class, id);
    }

    public String passwordHash(UUID id) {
        return jdbc.queryForObject("select password_hash from utenze_account where id = ?", String.class, id);
    }

    public Map<String, Object> profilo(UUID id) {
        return jdbc.queryForMap("select * from utenze_profili_operatore where account_id = ?", id);
    }

    public List<Long> misure(UUID id) {
        return jdbc.queryForList(
                "select misura_id from utenze_operatori_misure where account_id = ? order by misura_id", Long.class, id);
    }

    /** Riga del link, cercata per impronta. */
    public Map<String, Object> token(String link) {
        return jdbc.queryForMap("select * from utenze_token_attivazione where token_hash = ?", impronta(link));
    }

    public boolean usato(String link) {
        return token(link).get("usato_il") != null;
    }

    /** Link dell'account non ancora usati, scaduti compresi. */
    public List<String> impronteAttive(UUID account) {
        return jdbc.queryForList("""
                select token_hash from utenze_token_attivazione
                where account_id = ? and usato_il is null order by ordine_emissione
                """, String.class, account);
    }

    public int tokenEmessi(UUID account) {
        return conta("select count(*) from utenze_token_attivazione where account_id = ?", account);
    }

    public void impostaScadenza(String link, Instant scadenza) {
        jdbc.update("update utenze_token_attivazione set scadenza = ? where token_hash = ?",
                Timestamp.from(scadenza), impronta(link));
    }

    /** Account con un'email precisa, inserito direttamente: serve per i duplicati e per gli stati iniziali. */
    public UUID inserisciAccount(String email, String ruolo, String stato) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, 'accesso-non-previsto-nei-test', 'Account esistente', ?, ?)
                """, id, email, ruolo, stato);
        return id;
    }

    public int conta(String sql, Object... parametri) {
        return jdbc.queryForObject(sql, Integer.class, parametri);
    }

    /** Nessun Operatore, profilo, misura o link salvato. */
    public void nessunOperatoreSalvato() {
        assertThat(conta("select count(*) from utenze_account where ruolo = 'OPERATORE'")).isZero();
        assertThat(conta("select count(*) from utenze_profili_operatore")).isZero();
        assertThat(conta("select count(*) from utenze_operatori_misure")).isZero();
        assertThat(conta("select count(*) from utenze_token_attivazione")).isZero();
    }
}
