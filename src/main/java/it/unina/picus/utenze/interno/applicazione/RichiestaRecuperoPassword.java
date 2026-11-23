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

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.RecuperoPasswordService.DatiInvioRecupero;

/**
 * Richiesta di recupero della password, fatta senza sessione.
 *
 * <p>La richiesta si limita ad accodare il lavoro e ritorna subito: nuovo link, invio dell'email e sostituzione
 * del link precedente avvengono in secondo piano. Così la risposta ha gli stessi tempi per ogni indirizzo, sia che
 * abbia un account attivo sia che non esista, sia in attesa di attivazione o revocato, e chi non è autenticato non
 * può scoprire quali indirizzi sono registrati.</p>
 */
@Component
@Profile("!skeleton")
public class RichiestaRecuperoPassword {

    private static final Logger LOG = LoggerFactory.getLogger(RichiestaRecuperoPassword.class);

    private final RecuperoPasswordService recupero;
    private final InvioEmail invioEmail;
    private final Executor esecutore;

    @Autowired
    RichiestaRecuperoPassword(RecuperoPasswordService recupero, InvioEmail invioEmail,
            @Qualifier("applicationTaskExecutor") Executor esecutore) {
        this.recupero = Objects.requireNonNull(recupero);
        this.invioEmail = Objects.requireNonNull(invioEmail);
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
            LOG.warn("Richiesta di recupero password scartata: troppe elaborazioni in corso");
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
}
