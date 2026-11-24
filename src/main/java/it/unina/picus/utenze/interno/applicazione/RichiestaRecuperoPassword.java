package it.unina.picus.utenze.interno.applicazione;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.RecuperoPasswordService.DatiInvioRecupero;

import jakarta.annotation.PreDestroy;

/**
 * Richiesta di recupero della password, fatta senza sessione.
 *
 * <p>La richiesta si limita ad accodare il lavoro e ritorna subito: nuovo link, invio dell'email e sostituzione
 * del link precedente avvengono in secondo piano. Così la risposta ha gli stessi tempi per ogni indirizzo, sia
 * che abbia un account attivo sia che non esista, sia in attesa di attivazione o revocato, e chi non è
 * autenticato non può scoprire quali indirizzi sono registrati.</p>
 *
 * <p>Il lavoro gira su un esecutore proprio, con pochi thread e una coda limitata: un server di posta lento non
 * può far crescere la coda senza limite né ritardare le altre attività dell'applicazione. A coda piena la
 * richiesta è rifiutata e la risposta è la stessa per ogni indirizzo, perché dipende dal carico e non dall'account.
 * Ogni rifiuto è contato e scritto nel log.</p>
 *
 * <p>Se la posta rifiuta l'email, il nuovo link viene scartato e restano validi quelli di prima; se la accetta,
 * il nuovo link sostituisce i precedenti ({@link RecuperoPasswordService}).</p>
 */
@Component
@Profile("!skeleton")
public class RichiestaRecuperoPassword {

    private static final Logger LOG = LoggerFactory.getLogger(RichiestaRecuperoPassword.class);
    private static final long ATTESA_CHIUSURA_SECONDI = 10;

    private final RecuperoPasswordService recupero;
    private final InvioEmail invioEmail;
    private final Executor esecutore;
    private final AtomicLong scartate = new AtomicLong();

    @Autowired
    RichiestaRecuperoPassword(RecuperoPasswordService recupero, InvioEmail invioEmail,
            @Value("${picus.utenze.recupero.esecutore.thread:2}") int thread,
            @Value("${picus.utenze.recupero.esecutore.coda:50}") int capacitaCoda) {
        this(recupero, invioEmail, nuovoEsecutore(thread, capacitaCoda));
    }

    /** Per chi vuole un esecutore diverso, per esempio uno sincrono che non accoda. */
    RichiestaRecuperoPassword(RecuperoPasswordService recupero, InvioEmail invioEmail, Executor esecutore) {
        this.recupero = Objects.requireNonNull(recupero);
        this.invioEmail = Objects.requireNonNull(invioEmail);
        this.esecutore = Objects.requireNonNull(esecutore);
    }

    /** Pochi thread fissi e coda a capacità fissa: a coda piena {@code execute} rifiuta il lavoro. */
    private static ExecutorService nuovoEsecutore(int thread, int capacitaCoda) {
        AtomicInteger numero = new AtomicInteger();
        return new ThreadPoolExecutor(thread, thread, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacitaCoda),
                lavoro -> {
                    Thread t = new Thread(lavoro, "recupero-password-" + numero.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Accoda la richiesta e ritorna senza attendere l'esito. Non dice mai se l'indirizzo era idoneo.
     *
     * @param email indirizzo digitato dall'utente, già controllato nella forma
     * @return falso se la coda è piena e la richiesta non è stata presa in carico, per qualsiasi indirizzo
     */
    public boolean richiedi(String email) {
        try {
            esecutore.execute(() -> elabora(email));
            return true;
        } catch (RejectedExecutionException e) {
            LOG.warn("Richiesta di recupero password rifiutata: coda piena (richieste rifiutate finora: {})",
                    scartate.incrementAndGet());
            return false;
        }
    }

    /** L'indirizzo non compare nei log: dire quale è stato scartato lo rivelerebbe a chi legge i log. */
    private void elabora(String email) {
        try {
            recupero.preparaLink(email).ifPresent(this::consegna);
        } catch (RuntimeException e) {
            LOG.warn("Richiesta di recupero password non elaborata (causa={})", e.getClass().getSimpleName());
        }
    }

    private void consegna(DatiInvioRecupero dati) {
        boolean accettata = false;
        try {
            invioEmail.inviaLinkResetPassword(dati.emailDestinatario(), dati.nomeDestinatario(), dati.tokenInChiaro());
            accettata = true;
        } catch (RuntimeException e) {
            LOG.warn("Invio del link di recupero password non riuscito: restano validi i link già inviati");
        }
        try {
            if (accettata) {
                recupero.confermaConsegna(dati.idLink());
            } else {
                recupero.scartaLink(dati.idLink());
            }
        } catch (RuntimeException e) {
            LOG.warn("Aggiornamento dei link di recupero non riuscito dopo l'invio (causa={})",
                    e.getClass().getSimpleName());
        }
    }

    /** Alla chiusura dell'applicazione lascia finire le richieste già accodate, per poco tempo. */
    @PreDestroy
    void chiudi() {
        if (esecutore instanceof ExecutorService servizio) {
            servizio.shutdown();
            try {
                if (!servizio.awaitTermination(ATTESA_CHIUSURA_SECONDI, TimeUnit.SECONDS)) {
                    servizio.shutdownNow();
                }
            } catch (InterruptedException e) {
                servizio.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
