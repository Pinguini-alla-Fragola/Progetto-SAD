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
 * Base neutra dei test black box: contesto Spring completo, PostgreSQL 17 in Docker, una fixture
 * {@link ScenarioAccessi} nuova per ogni test e verifiche comuni dei dinieghi.
 *
 * <p>Porta il solo tag {@code blackbox}: il tag della User Story si dichiara nella sottoclasse, perché
 * {@link Tag} è ereditato e una storia non deve eseguire i test di un'altra.</p>
 */
@SpringBootTest
@Tag("blackbox")
public abstract class BlackBoxPostgresTest {

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
    protected BlackBoxPostgresTest() {
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
