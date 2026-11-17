package it.unina.picus.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.TestAbortedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;

/**
 * Base comune dei test black box della User Story
 * <a href="https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/28">#28 Accesso ai dati in base
 * al ruolo e alle assegnazioni</a>.
 *
 * <h2>Fonti</h2>
 * <p>Gli esiti attesi vengono dai criteri di accettazione di #28 e dai contratti {@code *Api} dei moduli
 * (PR #86). Nei nomi dei test {@code CA-nn} indica l'n-esimo criterio nell'ordine in cui compare nella
 * issue: CA-01 OPT titolare ed estranea, CA-02 interpellato, CA-03 assegnato, CA-04 scheda personale,
 * CA-05 Banco autorizzato, CA-06 azioni riservate al Banco, CA-07 comunicazioni, CA-08 sessione non
 * valida, CA-09 parametri alterati, CA-10…CA-15 annullamento.</p>
 *
 * <h2>Approccio</h2>
 * <ul>
 *   <li>I test usano soltanto le capacità pubbliche dei moduli ({@code *.api}, elencate in {@link Capacita})
 *       e, dove una funzione consegnata non ha un contratto, gli endpoint HTTP reali
 *       ({@link ContattiDisponibilitaHttp}). Le classi {@code interno} dei moduli non vengono usate.</li>
 *   <li>Le pratiche si portano negli stati richiesti con le stesse operazioni degli utenti (invio, presa
 *       in carico, ricerca, contatto, risposta, assegnazione, conferma, rifiuto). Il database si usa solo per
 *       preparare ciò che nessuna capacità consegnata permette ancora di creare: account e profili, una
 *       misura dedicata al test, offerta delle OPT, abilitazione degli operatori e revoca. L'identità usata
 *       è sempre quella restituita dal server.</li>
 *   <li>{@code FORBIDDEN} corrisponde a {@link SecurityException} sui contratti e a {@code 403} via HTTP;
 *       {@code UNAUTHORIZED} corrisponde a {@code 401}.</li>
 *   <li>«Senza dati né modifiche»: l'operazione negata non restituisce valori e la pratica, riletta dal
 *       Banco, resta identica in stato e dati ({@link #assertNegatoSenzaModifiche}).</li>
 *   <li>Solo dati sintetici: nessun identificativo di assistiti, nessuna credenziale reale.</li>
 *   <li>Tre esiti distinti: eseguito; <b>sospeso</b> se usa una capacità non ancora consegnata o un endpoint
 *       non ancora esposto; <b>fallito</b> se una capacità consegnata manca, se un erogatore abilitato dalla
 *       fixture non compare tra i candidati o se il comportamento viola la #28.</li>
 * </ul>
 *
 * <h2>Tecniche di progettazione</h2>
 * <ul>
 *   <li><b>Classi di equivalenza sugli attori</b>: un rappresentante per relazione con la risorsa
 *       (titolare, estranea, interpellato, assegnato, collega, Banco) e per tipologia di erogatore.</li>
 *   <li><b>Matrice dei permessi</b>: ruolo × operazione per le funzioni del Banco e per le comunicazioni.</li>
 *   <li><b>Tabella decisionale</b> per l'annullamento: attore × stato × motivo.</li>
 *   <li><b>Combinazioni complete</b> invece del 2-wise, perché i domini sono piccoli (al più 3 × 5).</li>
 *   <li><b>Transizioni di stato</b>: annullamento da ogni stato e ripetizione da Annullata.</li>
 *   <li><b>Valori limite</b> sul motivo: Creata come confine; {@code null}, vuoto, soli spazi.</li>
 *   <li><b>Test negativi e di sicurezza</b>: identificativi altrui, parametri alterati, sessione non valida.</li>
 *   <li><b>Controlli positivi</b> accanto ai dinieghi, perché un sistema che nega tutto non superi la suite.</li>
 * </ul>
 *
 * <h2>Esecuzione</h2>
 * <p>{@code ./mvnw test -Dtest='*AccessiTest'}; tag JUnit {@code blackbox} e {@code US-28}. Il database
 * di prova è il PostgreSQL 17 di {@link #POSTGRES}, dichiarato qui come nel test della #110: serve
 * Docker attivo e i dati di prova non toccano il database di sviluppo.</p>
 */
@SpringBootTest
@Tag("blackbox")
@Tag("US-28")
public abstract class AccessiBlackBoxTest {

    /**
     * PostgreSQL 17 usa e getta, condiviso da tutte le classi della suite. Viene avviato una sola volta
     * (contenitore singleton) e non ad ogni classe, perché le classi condividono lo stesso contesto
     * Spring; Testcontainers lo elimina a fine esecuzione. {@link ServiceConnection} imposta la
     * datasource del contesto.
     */
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    /** Costruttore per le sottoclassi di test; il contesto Spring è iniettato da JUnit. */
    protected AccessiBlackBoxTest() {
    }

    @Autowired
    private ApplicationContext contesto;

    /** Fixture con gli account e le pratiche del singolo test. */
    protected ScenarioAccessi scenario;

    /** Crea una fixture nuova per ogni test, così account e pratiche non sono condivisi. */
    @BeforeEach
    protected void preparaScenario() {
        scenario = new ScenarioAccessi(contesto);
    }

    /**
     * Verifica un diniego {@code FORBIDDEN}: l'operazione non restituisce dati e la pratica,
     * riletta dal Banco, resta identica in stato e dati.
     *
     * @param richiestaId pratica da confrontare prima e dopo il tentativo
     * @param operazione operazione che deve essere negata
     */
    protected void assertNegatoSenzaModifiche(UUID richiestaId, Executable operazione) {
        RichiestaDettaglio prima = scenario.istantanea(richiestaId);
        assertNegato(operazione);
        assertEquals(prima, scenario.istantanea(richiestaId), "la pratica deve restare invariata dopo il diniego");
    }

    /**
     * Verifica un diniego {@code FORBIDDEN} su un'operazione di sola lettura.
     *
     * <p>Se l'operazione usa una capacità non ancora consegnata, la sospensione del test
     * ({@link TestAbortedException}) viene propagata e non scambiata per un esito errato.</p>
     *
     * @param operazione operazione che deve essere negata
     */
    protected static void assertNegato(Executable operazione) {
        try {
            operazione.execute();
        } catch (TestAbortedException sospeso) {
            throw sospeso;
        } catch (SecurityException atteso) {
            return;
        } catch (Throwable altro) {
            fail("atteso FORBIDDEN (SecurityException), ricevuto " + altro, altro);
        }
        fail("atteso FORBIDDEN (SecurityException), l'operazione è stata eseguita");
    }
}
