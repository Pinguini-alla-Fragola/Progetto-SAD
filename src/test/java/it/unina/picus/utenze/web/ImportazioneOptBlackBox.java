package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.Capacita;
import it.unina.picus.shared.ClientHttp;
import it.unina.picus.shared.ClientHttp.Csrf;
import it.unina.picus.shared.InvioEmail;
import it.unina.picus.shared.PostgresqlDiProva;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.AccountAmministrativo;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Base dei test black box della User Story #24 "Importazione delle OPT da CSV" (task #151).
 *
 * <p>{@code CA-nn} è l'n-esimo criterio di accettazione della #24. Finché la capacità
 * {@link Capacita#IMPORTAZIONE_OPT} non è consegnata i casi sono sospesi prima di preparare i dati.</p>
 *
 * <p>Le importazioni passano solo dalla rotta HTTP. Il database riceve direttamente i soli dati preparatori e
 * si legge soltanto per ciò che {@link UtenzeApi} non espone: conteggi, codice fiscale e hash della password.</p>
 *
 * <p>Non si estende {@code AccessiBlackBoxTest}: il suo tag US-28 è ereditato e il suo database è condiviso con
 * altri contesti, i cui job potrebbero consegnare gli inviti al posto del doppio di {@link InvioEmail}. Il
 * database di {@link PostgresqlDiProva} appartiene invece a questo solo contesto. I dati non si ripuliscono:
 * ogni test usa valori nuovi e confronta sempre prima e dopo.</p>
 */
@SpringBootTest
@Import(PostgresqlDiProva.class)
@Tag("blackbox")
@Tag("US-24")
abstract class ImportazioneOptBlackBox {

    static final String IMPORTAZIONI = "/api/opt/importazioni";
    static final String INTESTAZIONE = "codice_struttura,nome_opt,comune,codice_fiscale,email";
    static final String COMUNE = "napoli";
    static final String ALTRO_COMUNE = "pozzuoli";

    /** Tempo massimo per la consegna asincrona di un invito. */
    private static final Duration ATTESA_INVIO = Duration.ofSeconds(8);
    /** Finestra in cui un invio non dovuto avrebbe il tempo di partire. */
    private static final Duration FINESTRA_DI_QUIETE = Duration.ofMillis(2500);

    /** Riga del CSV; i metodi {@code con…} ne cambiano un solo campo. */
    record Riga(String codice, String nome, String comune, String codiceFiscale, String email) {

        Riga conCodice(String valore) {
            return new Riga(valore, nome, comune, codiceFiscale, email);
        }

        Riga conNome(String valore) {
            return new Riga(codice, valore, comune, codiceFiscale, email);
        }

        Riga conComune(String valore) {
            return new Riga(codice, nome, valore, codiceFiscale, email);
        }

        Riga conCodiceFiscale(String valore) {
            return new Riga(codice, nome, comune, valore, email);
        }

        Riga conEmail(String valore) {
            return new Riga(codice, nome, comune, codiceFiscale, valore);
        }

        String csv() {
            return String.join(",", codice, nome, comune, codiceFiscale, email);
        }
    }

    /** Esito di una riga del report. */
    record EsitoRiga(String esito, List<String> motivi) {}

    /** Dati di un account riletti dopo un'operazione. */
    record Utenza(AccountAmministrativo dati, String codiceFiscale, String hashPassword) {}

    /** Stato completo delle utenze, per il confronto prima/dopo. */
    record Istantanea(Map<UUID, Utenza> utenze, long account, long profiliOpt) {}

    @Autowired
    private WebApplicationContext web;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    UtenzeApi utenze;
    @MockitoBean
    InvioEmail posta;

    ClientHttp http;
    ScenarioAccessi scenario;

    /** La sospensione precede qualunque dato preparatorio. */
    @BeforeEach
    void preparaImportazione() throws Exception {
        http = new ClientHttp(web);
        http.richiediEndpointDellaCapacita(Capacita.IMPORTAZIONE_OPT,
                post(IMPORTAZIONI).contentType(MediaType.MULTIPART_FORM_DATA).accept(MediaType.APPLICATION_JSON));
        scenario = new ScenarioAccessi(web);
    }

    // ------------------------------------------------------------------ dati sintetici

    /** Riga valida di una OPT mai vista. */
    static Riga nuovaRiga() {
        String unico = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new Riga("T24-" + unico.toUpperCase(Locale.ROOT), "OPT sintetica " + unico, COMUNE,
                codiceFiscaleSintetico(), "opt-" + unico + "@picus.test");
    }

    /** Codice fiscale numerico inventato, con la cifra di controllo coerente. */
    static String codiceFiscaleSintetico() {
        int numero = Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000_000);
        String base = "9" + String.format(Locale.ROOT, "%09d", numero);
        int somma = 0;
        for (int i = 0; i < base.length(); i++) {
            int cifra = base.charAt(i) - '0';
            int peso = i % 2 == 0 ? cifra : cifra * 2;
            somma += peso > 9 ? peso - 9 : peso;
        }
        return base + (10 - somma % 10) % 10;
    }

    /** OPT già registrata e attiva, con codice fiscale e password di prova. */
    Account optRegistrata() {
        Account opt = scenario.opt(COMUNE);
        // stesso formato maiuscolo con cui il Banco registra il codice struttura
        jdbc.update("update utenze_profili_opt set codice_fiscale = ?, codice_struttura = upper(codice_struttura) "
                + "where account_id = ?", codiceFiscaleSintetico(), opt.id());
        return opt;
    }

    void impostaStato(Account account, UtenzeApi.StatoUtenza stato) {
        jdbc.update("update utenze_account set stato = ? where id = ?", stato.name(), account.id());
    }

    /** Riga CSV con gli stessi dati della OPT già registrata. */
    Riga rigaDi(Account opt) {
        Utenza u = istantanea().utenze().get(opt.id());
        return new Riga(u.dati().riferimento(), u.dati().nomeVisualizzato(), u.dati().comuneCodice(),
                u.codiceFiscale(), u.dati().email());
    }

    static String csv(Riga... righe) {
        StringBuilder testo = new StringBuilder(INTESTAZIONE).append('\n');
        for (Riga riga : righe) {
            testo.append(riga.csv()).append('\n');
        }
        return testo.toString();
    }

    // ------------------------------------------------------------------ importazione

    /** Caricamento multipart del file nella sessione indicata. */
    static MockHttpServletRequestBuilder caricamento(MockHttpSession sessione, Csrf csrf, byte[] contenuto) {
        MockHttpServletRequestBuilder richiesta = post(IMPORTAZIONI);
        richiesta.session(sessione).header(csrf.header(), csrf.token())
                .contentType(MediaType.MULTIPART_FORM_DATA).accept(MediaType.APPLICATION_JSON)
                .with(costruita -> {
                    MockPart parte = new MockPart("file", "opt.csv", contenuto);
                    parte.getHeaders().setContentType(MediaType.parseMediaType("text/csv"));
                    costruita.addPart(parte);
                    return costruita;
                });
        return richiesta;
    }

    MockHttpServletResponse carica(Account attore, byte[] contenuto) throws Exception {
        MockHttpSession sessione = http.accedi(attore);
        return http.esegui(caricamento(sessione, http.csrf(sessione), contenuto));
    }

    /** Importazione riuscita del Banco: report per numero di riga (l'intestazione è la 1). */
    Map<Integer, EsitoRiga> importa(String csv) throws Exception {
        MockHttpServletResponse risposta = carica(scenario.banco(), csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(200, risposta.getStatus(), "un CSV leggibile deve produrre il report");
        List<Map<String, Object>> righe = JsonPath.read(risposta.getContentAsString(StandardCharsets.UTF_8), "$.righe");
        Map<Integer, EsitoRiga> report = new LinkedHashMap<>();
        for (Map<String, Object> riga : righe) {
            @SuppressWarnings("unchecked")
            List<String> motivi = (List<String>) riga.get("motivi");
            EsitoRiga doppia = report.put(((Number) riga.get("numeroRiga")).intValue(),
                    new EsitoRiga((String) riga.get("esito"), motivi));
            assertNull(doppia, "numero di riga ripetuto nel report");
        }
        return report;
    }

    static void assertEsito(String atteso, Map<Integer, EsitoRiga> report, int numero) {
        EsitoRiga riga = report.get(numero);
        assertNotNull(riga, () -> "riga " + numero + " assente dal report " + report);
        assertEquals(atteso, riga.esito(), () -> "esito della riga " + numero + ": " + riga);
    }

    static void assertScartata(Map<Integer, EsitoRiga> report, int numero) {
        assertEsito("SCARTATA", report, numero);
        assertSegnalata(report, numero);
    }

    /** La riga è nel report con almeno un motivo, senza fissarne il testo. */
    static void assertSegnalata(Map<Integer, EsitoRiga> report, int numero) {
        EsitoRiga riga = report.get(numero);
        assertNotNull(riga, () -> "riga " + numero + " assente dal report " + report);
        assertNotNull(riga.motivi(), () -> "riga " + numero + " senza motivi");
        assertFalse(riga.motivi().isEmpty(), () -> "riga " + numero + " senza motivi");
        assertTrue(riga.motivi().stream().noneMatch(String::isBlank), () -> "motivo vuoto alla riga " + numero);
    }

    // ------------------------------------------------------------------ riletture

    Istantanea istantanea() {
        Map<UUID, Utenza> tutte = new HashMap<>();
        for (RuoloUtente ruolo : RuoloUtente.values()) {
            for (AccountAmministrativo dati : utenze.elencoAccountPerRuolo(ruolo)) {
                String cf = jdbc.query("select codice_fiscale from utenze_profili_opt where account_id = ?",
                        rs -> rs.next() ? rs.getString(1) : null, dati.id());
                String hash = jdbc.queryForObject("select password_hash from utenze_account where id = ?",
                        String.class, dati.id());
                tutte.put(dati.id(), new Utenza(dati, cf, hash));
            }
        }
        return new Istantanea(tutte, conta("utenze_account"), conta("utenze_profili_opt"));
    }

    private long conta(String tabella) {
        return jdbc.queryForObject("select count(*) from " + tabella, Long.class);
    }

    /** Account OPT con il codice struttura indicato: al più uno. */
    AccountAmministrativo optConCodice(String codice) {
        List<AccountAmministrativo> trovate = utenze.elencoAccountPerRuolo(RuoloUtente.OPT).stream()
                .filter(a -> codice.equalsIgnoreCase(a.riferimento())).toList();
        assertTrue(trovate.size() <= 1, () -> "più account per la stessa OPT: " + trovate);
        return trovate.isEmpty() ? null : trovate.getFirst();
    }

    /** Verifica che, rispetto a prima, siano nate soltanto le OPT indicate e nulla sia cambiato nelle altre. */
    void assertSoloNuove(Istantanea prima, Riga... attese) {
        Istantanea dopo = istantanea();
        assertEquals(prima.account() + attese.length, dopo.account(), "numero di account");
        assertEquals(prima.profiliOpt() + attese.length, dopo.profiliOpt(), "numero di profili OPT");
        prima.utenze().forEach((id, utenza) ->
                assertEquals(utenza, dopo.utenze().get(id), "utenza esistente alterata"));
        for (Riga attesa : attese) {
            assertNotNull(optConCodice(attesa.codice()), () -> "OPT non importata: " + attesa);
        }
    }

    // ------------------------------------------------------------------ email

    /** Token dei link di attivazione inviati all'indirizzo, in ordine di invio. */
    List<String> tokenInviati(String email) {
        return Mockito.mockingDetails(posta).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("inviaLinkAttivazione"))
                .filter(i -> email.equalsIgnoreCase(i.getArgument(0)))
                .map(i -> i.<String>getArgument(2)).toList();
    }

    /** Attende l'invito di ogni nuova OPT e verifica che ne parta uno solo. */
    void attendiUnInvito(Riga... nuove) throws InterruptedException {
        long scadenza = System.nanoTime() + ATTESA_INVIO.toNanos();
        for (Riga nuova : nuove) {
            while (tokenInviati(nuova.email()).isEmpty() && System.nanoTime() < scadenza) {
                Thread.sleep(100);
            }
            assertEquals(1, tokenInviati(nuova.email()).size(), () -> "inviti inviati a " + nuova.email());
        }
    }

    /** Conteggio degli invii per destinatario. */
    Map<String, Integer> invii(List<String> destinatari) {
        Map<String, Integer> conteggio = new HashMap<>();
        destinatari.forEach(email -> conteggio.put(email, tokenInviati(email).size()));
        return conteggio;
    }

    /** Verifica che entro la finestra di quiete nessun altro invio raggiunga i destinatari. */
    void assertInviiInvariati(Map<String, Integer> prima) throws InterruptedException {
        Thread.sleep(FINESTRA_DI_QUIETE);
        assertEquals(prima, invii(List.copyOf(prima.keySet())), "nessun nuovo invio email atteso");
    }
}
