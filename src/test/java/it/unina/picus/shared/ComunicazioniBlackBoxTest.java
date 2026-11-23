package it.unina.picus.shared;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base comune dei test black box della User Story
 * <a href="https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/55">#55 Invio di comunicazioni a OPT e
 * Operatori</a>, sotto-issue di test
 * <a href="https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/142">#142</a>.
 *
 * <h2>Fonti</h2>
 * <p>Gli esiti attesi vengono dai criteri di accettazione di #55 e dalle rotte HTTP consegnate con #140 e #141
 * ({@code /api/comunicazioni}). Nei nomi dei test {@code CA-nn} indica l'n-esimo criterio nell'ordine in cui
 * compare nella issue: CA-01 invio a tutte le OPT o a un gruppo, CA-02 invio a tutti gli operatori interni ed
 * esterni pagati dal Banco o a un gruppo, CA-03 allegato ammesso consultato dai destinatari, CA-04 invio senza
 * testo o senza destinatari, CA-05 diniego a un account esterno al gruppo, CA-06 avviso di scadenza della scheda
 * trimestrale sullo stesso canale. I formati di richiesta, risposta ed errore sono quelli dei controller di
 * {@code it.unina.picus.comunicazioni.web}, letti e non inventati; il modello dell'avviso di scadenza è quello
 * della pagina del Banco ({@code static/js/comunicazioni.js}).</p>
 *
 * <h2>Approccio</h2>
 * <ul>
 *   <li>I test passano soltanto dalle rotte HTTP reali, con la catena di Spring Security attiva, il login JSON e
 *       il token CSRF ({@link ClientHttp}). Il contratto {@code ComunicazioniApi} non ha ancora
 *       un'implementazione e non viene usato; {@link Capacita} non cambia.</li>
 *   <li>Il database si usa solo per creare account e profili con {@link ScenarioAccessi}, nuovi per ogni test;
 *       comunicazioni e allegati nascono e si leggono solo attraverso le API.</li>
 *   <li>Il database è condiviso tra i test: nessun confronto su valori assoluti condivisi. Le viste si leggono
 *       senza ricerca né filtri, prima pagina ordinata dalla più recente; ogni test usa account appena creati, un
 *       {@linkplain #marcatore() marcatore} univoco nel testo e confronta {@code totale} e contatori prima e
 *       dopo l'operazione. Le classi non vengono eseguite in parallelo. La ricerca per testo delle viste (#141)
 *       non è un criterio della #55 e non viene usata.</li>
 *   <li>«Notificata in app» si osserva come l'id presente nella prima pagina delle ricevute del destinatario e il
 *       contatore {@code /da-confermare} aumentato di uno; «non effettua l'invio» come nessun elemento con il
 *       marcatore nella prima pagina delle inviate del Banco e ricevute e contatori invariati per i possibili
 *       destinatari creati dal test.</li>
 *   <li>{@code FORBIDDEN} corrisponde a {@code 403} a corpo vuoto, come lo produce {@code GestioneRifiuti}.</li>
 *   <li>Solo dati sintetici: account, testi e file generati dal test.</li>
 * </ul>
 *
 * <h2>Tecniche di progettazione</h2>
 * <ul>
 *   <li><b>Classi di equivalenza</b> sui tipi di destinatari, sugli attori (OPT, operatore interno, operatore
 *       esterno pagato dal Banco, dentro e fuori dal gruppo) e sui tipi di file (ammessi e rifiutati).</li>
 *   <li><b>Matrice</b> tipo di destinatari × attore e tipo × modalità di invio (JSON e multipart).</li>
 *   <li><b>Tabella decisionale</b> sui dati incompleti: testo × tipo di destinatari × gruppo.</li>
 *   <li><b>Valori limite</b>: 5 e 6 file, 5 MB e 5 MB + 1 byte, file vuoto, nome di 201 caratteri.</li>
 *   <li><b>Test negativi con controlli positivi</b>: ogni diniego o rifiuto è affiancato dal caso accettato,
 *       perché un sistema che nega tutto non superi la suite.</li>
 * </ul>
 *
 * <h2>Esecuzione</h2>
 * <p>{@code ./mvnw test -Dgroups=US-55}; tag JUnit {@code blackbox} e {@code US-55}. Il database di prova è il
 * PostgreSQL 17 di {@link #POSTGRES}: serve Docker attivo e i dati di prova non toccano il database di
 * sviluppo. MockMvc non applica i limiti multipart di {@code application.yml}: il limite di 5 MB osservato è
 * quello della regola di dominio sugli allegati.</p>
 */
@SpringBootTest
@Tag("blackbox")
@Tag("US-55")
public abstract class ComunicazioniBlackBoxTest {

    /**
     * PostgreSQL 17 usa e getta, condiviso da tutte le classi della suite e avviato una sola volta.
     * {@link ServiceConnection} imposta la datasource del contesto.
     */
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    /** Costruttore per le sottoclassi di test; il contesto Spring è iniettato da JUnit. */
    protected ComunicazioniBlackBoxTest() {
    }

    @Autowired
    private ApplicationContext contesto;

    @Autowired
    private WebApplicationContext web;

    /** Fixture con gli account del singolo test. */
    protected ScenarioAccessi scenario;

    /** Client HTTP con la catena di sicurezza attiva. */
    protected ClientHttp http;

    /** Crea fixture e client nuovi per ogni test, così account e sessioni non sono condivisi. */
    @BeforeEach
    protected void preparaScenario() {
        scenario = new ScenarioAccessi(contesto);
        http = new ClientHttp(web);
    }

    /**
     * Marcatore univoco da inserire in titolo, testo o nomi dei file, per riconoscere le comunicazioni del test
     * tra gli elementi delle viste e per controllare che un errore non ripeta i valori inseriti.
     *
     * @return testo sintetico mai usato prima
     */
    protected static String marcatore() {
        return "t55m" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
