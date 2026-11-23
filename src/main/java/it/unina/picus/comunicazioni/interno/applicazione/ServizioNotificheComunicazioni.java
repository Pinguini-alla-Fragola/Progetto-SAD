package it.unina.picus.comunicazioni.interno.applicazione;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.RegoleAccessoComunicazione;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/** Conta per l'avviso in app le comunicazioni che aspettano la conferma del destinatario. */
@Service
@Transactional(readOnly = true)
class ServizioNotificheComunicazioni implements NotificheComunicazioni {

    private final ArchivioComunicazioni archivio;

    ServizioNotificheComunicazioni(ArchivioComunicazioni archivio) {
        this.archivio = archivio;
    }

    @Override
    public long daConfermare(IdentitaAutenticata attore) {
        RegoleAccessoComunicazione.verificaPossibileDestinatario(attore);
        return archivio.contaDaConfermare(attore.accountId());
    }
}
