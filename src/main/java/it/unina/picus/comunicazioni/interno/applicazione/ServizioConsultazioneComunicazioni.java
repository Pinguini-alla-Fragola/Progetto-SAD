package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.Riscontro;
import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni.RicezioneDestinatario;
import it.unina.picus.comunicazioni.interno.dominio.ComunicazioneSalvata;
import it.unina.picus.comunicazioni.interno.dominio.FileSalvato;
import it.unina.picus.comunicazioni.interno.dominio.PaginaComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.RegoleAccessoComunicazione;
import it.unina.picus.utenze.api.NomiAccountApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Restituisce le comunicazioni a chi può vederle. Il permesso si controlla prima di cercare la comunicazione:
 * chi non è autorizzato riceve sempre lo stesso diniego, così non scopre se una comunicazione esiste.
 */
@Service
@Transactional(readOnly = true)
class ServizioConsultazioneComunicazioni implements ConsultazioneComunicazioni {

    private final ArchivioComunicazioni archivio;
    private final NomiAccountApi nomiAccount;

    ServizioConsultazioneComunicazioni(ArchivioComunicazioni archivio, NomiAccountApi nomiAccount) {
        this.archivio = archivio;
        this.nomiAccount = nomiAccount;
    }

    @Override
    public PaginaComunicazioni inviate(IdentitaAutenticata attore, RichiestaElenco richiesta) {
        RegoleAccessoComunicazione.verificaBanco(attore);
        return archivio.elencoInviate(NormalizzatoreFiltro.normalizza(richiesta));
    }

    @Override
    public PaginaComunicazioni ricevute(IdentitaAutenticata attore, RichiestaElenco richiesta) {
        RegoleAccessoComunicazione.verificaPossibileDestinatario(attore);
        return archivio.elencoRicevute(attore.accountId(), NormalizzatoreFiltro.normalizza(richiesta));
    }

    @Override
    public ComunicazioneSalvata consulta(IdentitaAutenticata attore, UUID comunicazioneId) {
        RegoleAccessoComunicazione.verificaConsultazione(attore, archivio.destinatari(comunicazioneId));
        return archivio.cerca(comunicazioneId, attore.accountId())
                .orElseThrow(RisorsaComunicazioniNonTrovataException::new);
    }

    /** {@inheritDoc} */
    @Override
    public List<Riscontro> elencoRiscontri(IdentitaAutenticata attore, UUID comunicazioneId) {
        RegoleAccessoComunicazione.verificaBanco(attore);
        List<RicezioneDestinatario> ricezioni = archivio.ricezioni(comunicazioneId)
                .orElseThrow(RisorsaComunicazioniNonTrovataException::new);
        Set<UUID> destinatari = ricezioni.stream().map(RicezioneDestinatario::destinatarioId)
                .collect(Collectors.toSet());
        Map<UUID, String> nomi = nomiAccount.nomiVisualizzati(destinatari);
        return ricezioni.stream().map(ricezione -> new Riscontro(ricezione.destinatarioId(),
                        nomi.getOrDefault(ricezione.destinatarioId(), "Destinatario non disponibile"),
                        ricezione.confermataIl()))
                .sorted(Comparator.comparing(Riscontro::nomeVisualizzato, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Riscontro::destinatarioId))
                .toList();
    }

    @Override
    public FileSalvato scarica(IdentitaAutenticata attore, UUID allegatoId) {
        Set<UUID> destinatari = archivio.comunicazioneDellAllegato(allegatoId)
                .map(archivio::destinatari).orElse(Set.of());
        RegoleAccessoComunicazione.verificaConsultazione(attore, destinatari);
        return archivio.allegato(allegatoId).orElseThrow(RisorsaComunicazioniNonTrovataException::new);
    }
}
