package it.unina.picus.comunicazioni.interno.applicazione;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.comunicazioni.interno.dominio.AllegatoRicevuto;
import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.BozzaComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.CampoComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.Errori;
import it.unina.picus.comunicazioni.interno.dominio.NuovaComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.NuovoAllegato;
import it.unina.picus.comunicazioni.interno.dominio.PlateaDestinatari;
import it.unina.picus.comunicazioni.interno.dominio.RegoleAccessoComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.TipoAllegato;
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
                bozza.titolo(), bozza.contenuto(), bozza.tipo(), clock.instant(), account, allegati(bozza));
        archivio.registra(comunicazione);
        return new ComunicazioneInviata(comunicazione.id(), comunicazione.titolo(), comunicazione.inviataIl(),
                comunicazione.tipo(), comunicazione.destinatari().size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSelezionabile> selezionabili(IdentitaAutenticata attore, String destinatariTipo) {
        RegoleAccessoComunicazione.verificaBanco(attore);
        TipoDestinatari tipo = NormalizzatoreComunicazione.tipo(destinatariTipo);
        if (tipo == null) {
            throw new DatiComunicazioneNonValidiException(
                    Map.of(CampoComunicazione.DESTINATARI_TIPO, "Scegli a chi inviare la comunicazione"));
        }
        return destinatari.attivi(PlateaDestinatari.di(tipo)).stream()
                .map(account -> new AccountSelezionabile(account.id(), account.nomeVisualizzato())).toList();
    }

    /** Gli allegati sono già stati controllati dalle regole: qui si fissa il tipo riconosciuto dal contenuto. */
    private static List<NuovoAllegato> allegati(BozzaComunicazione bozza) {
        return bozza.allegati().stream().map(ServizioInvioComunicazioni::nuovoAllegato).toList();
    }

    private static NuovoAllegato nuovoAllegato(AllegatoRicevuto allegato) {
        byte[] contenuto = allegato.contenuto();
        TipoAllegato tipo = TipoAllegato.riconosci(contenuto).orElseThrow();
        return new NuovoAllegato(UUID.randomUUID(), allegato.nomeFile(), tipo, contenuto);
    }
}
