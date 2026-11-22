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
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;

/**
 * Consegna all'utente il link di attivazione già salvato. È l'unico punto che spedisce l'email di
 * attivazione: creazione e reinvio di OPT e Operatori passano da qui e hanno gli stessi esiti.
 *
 * <p>La consegna avviene sempre dopo il commit della transazione che ha creato il token: se l'invio fallisce
 * l'account resta valido e il link si può reinviare.</p>
 */
@Component
@Profile("!skeleton")
public class ConsegnaLinkAttivazione {

    private static final Logger LOG = LoggerFactory.getLogger(ConsegnaLinkAttivazione.class);

    /** Risultato di un tentativo di consegna. */
    public enum Esito {
        /** Email spedita al server di posta. */
        INVIATO,
        /** Modalità demo: il link è stato generato ma nessuna email è partita. */
        SIMULATO,
        /** Il server di posta ha rifiutato o non ha risposto. */
        NON_RIUSCITO
    }

    private final InvioEmail invioEmail;
    private final Executor esecutore;

    @Autowired
    ConsegnaLinkAttivazione(InvioEmail invioEmail, @Qualifier("applicationTaskExecutor") Executor esecutore) {
        this.invioEmail = Objects.requireNonNull(invioEmail);
        this.esecutore = Objects.requireNonNull(esecutore);
    }

    /**
     * Spedisce il link e attende l'esito, per chi deve dire al Banco se l'invio è riuscito.
     *
     * @param dati destinatario e token da consegnare
     * @return esito dell'invio; non lancia mai errori di posta
     */
    public Esito consegna(DatiInvioAttivazione dati) {
        try {
            invioEmail.inviaLinkAttivazione(dati.emailDestinatario(), dati.nomeDestinatario(), dati.tokenInChiaro());
        } catch (RuntimeException e) {
            return Esito.NON_RIUSCITO;
        }
        return invioEmail.isSimulato() ? Esito.SIMULATO : Esito.INVIATO;
    }

    /**
     * Spedisce il link senza far attendere chi risponde all'utente. Serve alle richieste senza sessione: se
     * la risposta aspettasse la posta, i tempi rivelerebbero quali indirizzi hanno un account in attesa.
     *
     * @param dati destinatario e token da consegnare
     */
    public void consegnaSenzaAttendere(DatiInvioAttivazione dati) {
        try {
            esecutore.execute(() -> {
                if (consegna(dati) == Esito.NON_RIUSCITO) {
                    LOG.warn("Reinvio del link di attivazione non riuscito: l'utente può chiederlo di nuovo");
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.warn("Reinvio del link di attivazione scartato: troppe consegne in corso");
        }
    }
}
