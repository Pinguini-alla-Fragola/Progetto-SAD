package it.unina.picus.utenze.interno.applicazione;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.LinkAttivazioneConsegnato;

/**
 * Toglie dalla coda dell'importazione CSV l'invito originale di un account solo quando un nuovo link gli è stato
 * consegnato davvero. Se il nuovo invio fosse fallito l'evento non sarebbe partito e l'invito resterebbe in coda.
 *
 * <p>Gira dopo il commit, in una transazione propria: il rilascio della coda non tiene così il lock dell'account,
 * e la coda e il reinvio non si bloccano a vicenda (la coda prende prima la propria riga e poi l'account).</p>
 */
@Component
@Profile("!skeleton")
class RimozioneInvitoCsvDopoConsegna {

    private final CodaInvitiImportazioneOpt coda;

    RimozioneInvitoCsvDopoConsegna(CodaInvitiImportazioneOpt coda) {
        this.coda = coda;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void alLinkConsegnato(LinkAttivazioneConsegnato evento) {
        coda.rimuovi(evento.accountId());
    }
}
