package it.unina.picus.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import it.unina.picus.TestcontainersConfiguration;

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
 *   <li>I test usano soltanto i contratti pubblici dei moduli ({@code *.api}) e, per sessione e
 *       autenticazione, l'interfaccia HTTP. Le classi {@code interno} dei moduli non vengono usate.</li>
 *   <li>Le pratiche si portano negli stati richiesti con le stesse operazioni degli utenti (invio, presa
 *       in carico, ricerca, contatto, risposta, assegnazione, conferma). Il database si usa solo per
 *       creare gli account sintetici e simulare una revoca, perché Utenze non espone ancora un contratto
 *       di creazione; l'identità usata è sempre quella restituita dal server.</li>
 *   <li>{@code FORBIDDEN} corrisponde a {@link SecurityException} sui contratti e a {@code 403} via HTTP;
 *       {@code UNAUTHORIZED} corrisponde a {@code 401}.</li>
 *   <li>«Senza dati né modifiche»: l'operazione negata non restituisce valori e la pratica, riletta dal
 *       Banco, resta identica in stato e dati ({@link #assertNegatoSenzaModifiche}).</li>
 *   <li>Solo dati sintetici: nessun identificativo di assistiti, nessuna credenziale reale.</li>
 *   <li>Se un modulo non è ancora nel contesto Spring il test viene sospeso (assumption) e non fallito,
 *       così la suite si attiva da sola quando arrivano le implementazioni.</li>
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
 * di prova è un PostgreSQL 17 dedicato, avviato in Docker da {@link TestcontainersConfiguration}: serve
 * Docker attivo e i dati di prova non toccano il database di sviluppo.</p>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Tag("blackbox")
@Tag("US-28")
public abstract class AccessiBlackBoxTest {

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
        assertThrows(SecurityException.class, operazione, "atteso FORBIDDEN (SecurityException)");
        assertEquals(prima, scenario.istantanea(richiestaId), "la pratica deve restare invariata dopo il diniego");
    }

    /**
     * Verifica un diniego {@code FORBIDDEN} su un'operazione di sola lettura.
     *
     * @param operazione operazione che deve essere negata
     */
    protected static void assertNegato(Executable operazione) {
        assertThrows(SecurityException.class, operazione, "atteso FORBIDDEN (SecurityException)");
    }
}
