package it.unina.picus.utenze.interno.applicazione;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

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
 * come un deadlock, un timeout o una connessione persa. Quando Spring non riesce ad aprire la transazione
 * ({@link CannotCreateTransactionException}) il tipo da solo non basta, perché il wrapper contiene anche errori
 * permanenti, per esempio le credenziali del database rifiutate: si guarda la causa, e in dubbio non si riprova.
 * Gli errori permanenti e quelli di programmazione hanno un solo tentativo, perché ripeterli non li risolverebbe e
 * terrebbe occupato il thread. I tentativi sono al massimo 3, con una breve attesa crescente.</p>
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
        if (e instanceof TransientDataAccessException || e instanceof RecoverableDataAccessException) {
            return true;
        }
        return e instanceof CannotCreateTransactionException && causaRecuperabile(e);
    }

    /**
     * Guarda la catena delle cause di un errore di apertura della transazione. Decide lo stato SQL dell'errore più
     * profondo che ne ha uno, perché è quello restituito dal database: connessione (classe 08), transazione annullata
     * (40), risorse esaurite (53) o server che si spegne o non accetta ancora connessioni (57P01, 57P02, 57P03) sono
     * recuperabili; ogni altro stato, per esempio 28P01 con le credenziali rifiutate, non lo è. Senza stato SQL decide
     * il tipo: errori JDBC transitori o recuperabili, connessione rifiutata o scaduta.
     */
    private static boolean causaRecuperabile(Throwable errore) {
        Set<Throwable> visitate = Collections.newSetFromMap(new IdentityHashMap<>());
        String statoPiuProfondo = null;
        boolean tipoTransitorio = false;
        for (Throwable causa = errore.getCause(); causa != null && visitate.add(causa); causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                statoPiuProfondo = sql.getSQLState();
            }
            tipoTransitorio |= causa instanceof SQLTransientException
                    || causa instanceof SQLRecoverableException
                    || causa instanceof ConnectException
                    || causa instanceof SocketTimeoutException;
        }
        return statoPiuProfondo != null ? statoRecuperabile(statoPiuProfondo) : tipoTransitorio;
    }

    private static boolean statoRecuperabile(String stato) {
        return stato.startsWith("08") || stato.startsWith("40") || stato.startsWith("53")
                || "57P01".equals(stato) || "57P02".equals(stato) || "57P03".equals(stato);
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
