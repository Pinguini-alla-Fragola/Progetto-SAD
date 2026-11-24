package it.unina.picus.utenze.interno.applicazione;

import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.LinkAttivazioneDaInviareEvent;

@Component
@Profile("!skeleton")
public class InvioLinkAttivazioneListener {

    private final InvioEmail invioEmail;

    public InvioLinkAttivazioneListener(InvioEmail invioEmail) {
        this.invioEmail = Objects.requireNonNull(invioEmail);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLinkAttivazioneDaInviare(LinkAttivazioneDaInviareEvent evento) {
        invioEmail.inviaLinkAttivazione(
                evento.emailDestinatario(),
                evento.nomeDestinatario(),
                evento.tokenInChiaro()
        );
    }
}