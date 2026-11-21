package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.ComunicazioneSalvata;
import it.unina.picus.comunicazioni.interno.dominio.FileSalvato;
import it.unina.picus.comunicazioni.interno.dominio.RegoleAccessoComunicazione;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Restituisce le comunicazioni a chi può vederle. Il permesso si controlla prima di cercare la comunicazione:
 * chi non è autorizzato riceve sempre lo stesso diniego, così non scopre se una comunicazione esiste.
 */
@Service
@Transactional(readOnly = true)
class ServizioConsultazioneComunicazioni implements ConsultazioneComunicazioni {

    private final ArchivioComunicazioni archivio;

    ServizioConsultazioneComunicazioni(ArchivioComunicazioni archivio) {
        this.archivio = archivio;
    }

    @Override
    public List<ComunicazioneSalvata> inviate(IdentitaAutenticata attore) {
        RegoleAccessoComunicazione.verificaBanco(attore);
        return archivio.elencoInviate();
    }

    @Override
    public List<ComunicazioneSalvata> ricevute(IdentitaAutenticata attore) {
        RegoleAccessoComunicazione.verificaPossibileDestinatario(attore);
        return archivio.elencoRicevute(attore.accountId());
    }

    @Override
    public ComunicazioneSalvata consulta(IdentitaAutenticata attore, UUID comunicazioneId) {
        RegoleAccessoComunicazione.verificaConsultazione(attore, archivio.destinatari(comunicazioneId));
        return archivio.cerca(comunicazioneId, attore.accountId())
                .orElseThrow(RisorsaComunicazioniNonTrovataException::new);
    }

    @Override
    public FileSalvato scarica(IdentitaAutenticata attore, UUID allegatoId) {
        Set<UUID> destinatari = archivio.comunicazioneDellAllegato(allegatoId)
                .map(archivio::destinatari).orElse(Set.of());
        RegoleAccessoComunicazione.verificaConsultazione(attore, destinatari);
        return archivio.allegato(allegatoId).orElseThrow(RisorsaComunicazioniNonTrovataException::new);
    }
}
