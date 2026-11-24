package it.unina.picus.utenze.interno.applicazione;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Politica comune per ripetere un'operazione di database fallita per un errore passeggero. Serve a chi, dopo aver
 * spedito un'email, deve ancora registrare l'esito: l'email è già partita e non si rispedisce, quindi si ripete
 * solo la registrazione, ogni volta in una nuova transazione.
 *
 * <p>Si riprova soltanto quando l'errore può passare da solo: guasti transitori o recuperabili dell'accesso ai dati,
 * come un deadlock, un timeout o una connessione persa, e l'impossibilità di aprire la transazione. Gli errori
 * permanenti e quelli di programmazione hanno un solo tentativo, perché ripeterli non li risolverebbe e terrebbe
 * occupato il thread. I tentativi sono al massimo {@value #TENTATIVI}, con una breve attesa crescente.</p>
 */
public final class RiprovaTransitoria {

    static final int TENTATIVI = 3;
    static final long ATTESA_MILLIS = 200;

    private static final Logger LOG = LoggerFactory.getLogger(RiprovaTransitoria.class);

    private RiprovaTransitoria() {
    }

    /**
     * Esegue l'azione e la ripete se fallisce per un errore transitorio. L'azione deve poter essere ripetuta senza
     * effetti doppi e aprire da sé la sua transazione.
     *
     * @param azione operazione da eseguire
     * @throws RuntimeException l'ultimo errore, se non è transitorio o i tentativi sono finiti
     */
    public static void esegui(Runnable azione) {
        Objects.requireNonNull(azione);
        for (int tentativo = 1; ; tentativo++) {
            try {
                azione.run();
                return;
            } catch (RuntimeException e) {
                if (!transitorio(e) || tentativo == TENTATIVI || !attendi(tentativo)) {
                    throw e;
                }
                LOG.warn("Operazione da ripetere dopo un errore transitorio (causa={}, tentativo {} di {})",
                        e.getClass().getSimpleName(), tentativo, TENTATIVI);
            }
        }
    }

    /** Vero se ripetere l'operazione può dare un esito diverso. */
    static boolean transitorio(RuntimeException e) {
        return e instanceof TransientDataAccessException
                || e instanceof RecoverableDataAccessException
                || e instanceof CannotCreateTransactionException;
    }

    /** @return falso se il thread è stato interrotto, per esempio alla chiusura dell'applicazione */
    private static boolean attendi(int tentativo) {
        try {
            Thread.sleep(ATTESA_MILLIS * tentativo);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
