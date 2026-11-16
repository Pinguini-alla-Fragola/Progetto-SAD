package it.unina.picus.richieste.interno.applicazione;

import it.unina.picus.comunicazioni.api.DestinatariAvvisi;
import it.unina.picus.richieste.interno.dominio.ContattiRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fornisce a Comunicazioni i soli riferimenti necessari alla consegna. */
@Component
@Transactional(readOnly = true)
class DestinatariDisponibilita implements DestinatariAvvisi {
    private final ContattiRepository contatti;

    DestinatariDisponibilita(ContattiRepository contatti) {
        this.contatti = contatti;
    }

    @Override
    public boolean corrisponde(UUID richiestaId, UUID contattoId, UUID destinatarioId) {
        return contatti.cerca(contattoId).filter(c -> c.richiestaId().equals(richiestaId)
                && c.erogatoreId().equals(destinatarioId)).isPresent();
    }
}
