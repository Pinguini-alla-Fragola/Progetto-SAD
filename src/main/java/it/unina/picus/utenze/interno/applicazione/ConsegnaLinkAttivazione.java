package it.unina.picus.utenze.interno.applicazione;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;

/**
 * Consegna all'utente il link di attivazione già salvato. Creazione e reinvio di OPT e Operatori passano da
 * qui e hanno gli stessi esiti: chi aggiunge un altro percorso che spedisce questa email, per esempio
 * un'importazione, deve usare questo componente invece di chiamare direttamente l'adattatore di posta.
 *
 * <p>La consegna avviene sempre dopo il commit della transazione che ha creato il token: se l'invio fallisce
 * l'account resta valido e il link si può reinviare.</p>
 */
@Component
@Profile("!skeleton")
public class ConsegnaLinkAttivazione {

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

    @Autowired
    ConsegnaLinkAttivazione(InvioEmail invioEmail) {
        this.invioEmail = Objects.requireNonNull(invioEmail);
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
}
