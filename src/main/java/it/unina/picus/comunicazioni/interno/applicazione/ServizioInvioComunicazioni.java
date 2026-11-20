package it.unina.picus.comunicazioni.interno.applicazione;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.BozzaComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.Errori;
import it.unina.picus.comunicazioni.interno.dominio.NuovaComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.RegoleAccessoComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.ValidatoreComunicazione;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/** Coordina i passi dell'invio: permesso, pulizia dei dati, controlli, scelta dei destinatari e registrazione. */
@Service
@Transactional
class ServizioInvioComunicazioni implements InvioComunicazioni {

    private final ValidatoreComunicazione validatore;
    private final DestinatariComunicazione destinatari;
    private final ArchivioComunicazioni archivio;
    private final Clock clock;

    ServizioInvioComunicazioni(ValidatoreComunicazione validatore, DestinatariComunicazione destinatari,
            ArchivioComunicazioni archivio, Clock clock) {
        this.validatore = validatore;
        this.destinatari = destinatari;
        this.archivio = archivio;
        this.clock = clock;
    }

    @Override
    public ComunicazioneInviata invia(IdentitaAutenticata attore, ComandoInvioComunicazione comando) {
        RegoleAccessoComunicazione.verificaBanco(attore);
        BozzaComunicazione bozza = NormalizzatoreComunicazione.normalizza(comando);
        Errori errori = validatore.verifica(bozza);
        // I destinatari si cercano solo se i campi sono già corretti: con un tipo sconosciuto non c'è chi cercare.
        Set<UUID> account = errori.vuoto() ? destinatari.risolvi(bozza, errori) : Set.of();
        if (!errori.vuoto()) {
            throw new DatiComunicazioneNonValidiException(errori.perCampo());
        }
        NuovaComunicazione comunicazione = new NuovaComunicazione(UUID.randomUUID(), attore.accountId(),
                bozza.titolo(), bozza.contenuto(), bozza.tipo(), clock.instant(), account);
        archivio.registra(comunicazione);
        return new ComunicazioneInviata(comunicazione.id(), comunicazione.titolo(), comunicazione.inviataIl(),
                comunicazione.tipo(), comunicazione.destinatari().size());
    }
}
