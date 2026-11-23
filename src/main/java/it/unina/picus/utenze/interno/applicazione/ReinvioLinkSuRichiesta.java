package it.unina.picus.utenze.interno.applicazione;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.LinkPreparato;

/**
 * Nuovo link di attivazione chiesto da chi possiede l'indirizzo, senza sessione.
 *
 * <p>La richiesta si limita ad accodare il lavoro e ritorna subito: nuovo token, invio dell'email e
 * sostituzione del link precedente avvengono in secondo piano. Così la risposta ha gli stessi tempi per ogni
 * indirizzo, sia che abbia un account in attesa sia che non esista, sia già attivo o revocato, e chi non è
 * autenticato non può scoprire quali indirizzi sono registrati.</p>
 *
 * <p>Se la posta rifiuta l'email, il nuovo link viene scartato e restano validi quelli di prima; se la accetta,
 * il nuovo link sostituisce i precedenti ({@link AttivazioneUtenzaService#preparaTokenPerEmail}).</p>
 */
@Component
@Profile("!skeleton")
public class ReinvioLinkSuRichiesta {

    private static final Logger LOG = LoggerFactory.getLogger(ReinvioLinkSuRichiesta.class);

    private final AttivazioneUtenzaService attivazione;
    private final ConsegnaLinkAttivazione consegna;
    private final Executor esecutore;

    @Autowired
    ReinvioLinkSuRichiesta(AttivazioneUtenzaService attivazione, ConsegnaLinkAttivazione consegna,
            @Qualifier("applicationTaskExecutor") Executor esecutore) {
        this.attivazione = Objects.requireNonNull(attivazione);
        this.consegna = Objects.requireNonNull(consegna);
        this.esecutore = Objects.requireNonNull(esecutore);
    }

    /**
     * Accoda la richiesta e ritorna senza attendere l'esito. Non dice mai se l'indirizzo era idoneo.
     *
     * @param email indirizzo digitato dall'utente, già controllato nella forma
     */
    public void richiedi(String email) {
        try {
            esecutore.execute(() -> elabora(email));
        } catch (RejectedExecutionException e) {
            LOG.warn("Richiesta di un nuovo link di attivazione scartata: troppe elaborazioni in corso");
        }
    }

    /** L'indirizzo non compare nei log: dire quale è stato scartato lo rivelerebbe a chi legge i log. */
    private void elabora(String email) {
        try {
            attivazione.preparaTokenPerEmail(email).ifPresent(this::consegna);
        } catch (RuntimeException e) {
            LOG.warn("Richiesta di un nuovo link di attivazione non elaborata (causa={})", e.getClass().getSimpleName());
        }
    }

    /** Il nuovo link sostituisce il precedente solo se la posta lo ha accettato; la modalità demo vale come accettato. */
    private void consegna(LinkPreparato link) {
        boolean accettato = consegna.consegna(link.dati()) != ConsegnaLinkAttivazione.Esito.NON_RIUSCITO;
        if (!accettato) {
            LOG.warn("Invio del nuovo link di attivazione non riuscito: restano validi i link già inviati");
        }
        try {
            if (accettato) {
                attivazione.confermaConsegna(link.idToken());
            } else {
                attivazione.scartaToken(link.idToken());
            }
        } catch (RuntimeException e) {
            LOG.warn("Aggiornamento dei link di attivazione non riuscito dopo l'invio (causa={})",
                    e.getClass().getSimpleName());
        }
    }
}
