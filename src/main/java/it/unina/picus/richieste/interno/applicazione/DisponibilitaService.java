package it.unina.picus.richieste.interno.applicazione;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettaglioContatto;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.interno.dominio.StatoContattoIncompatibileException;
import it.unina.picus.richieste.interno.persistenza.ContattoDisponibilitaJpa;
import it.unina.picus.richieste.interno.persistenza.ContattoDisponibilitaRepository;
import it.unina.picus.richieste.interno.persistenza.RichiestaRepository;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/** Casi d'uso di consultazione e risposta per un contatto di disponibilità già aperto. */
@Service
@Transactional(readOnly = true)
public class DisponibilitaService {

    private final RichiestaRepository richieste;
    private final ContattoDisponibilitaRepository contatti;

    public DisponibilitaService(RichiestaRepository richieste, ContattoDisponibilitaRepository contatti) {
        this.richieste = richieste;
        this.contatti = contatti;
    }

    /** @see RichiesteApi#consultaContatto(IdentitaAutenticata, UUID) */
    public DettaglioContatto consultaContatto(IdentitaAutenticata attore, UUID contattoId) {
        verificaRuoloErogatore(attore);
        var contatto = contatti.findOneById(contattoId)
                .orElseThrow(() -> new NoSuchElementException("Contatto inesistente"));
        verificaDestinatario(attore, contatto.getErogatoreId());

        var richiesta = contatto.getRichiesta();
        return new DettaglioContatto(aDto(contatto), richiesta.getMisuraId(), contatto.getNomeMisura(),
                richiesta.getTipoDestinatario(), richiesta.getComuneCodice());
    }

    /**
     * Blocca la pratica prima di leggere l'esito, così una sola transazione può rispondere.
     *
     * @see RichiesteApi#rispondiDisponibilita(IdentitaAutenticata, UUID, boolean)
     */
    @Transactional
    public ContattoDisponibilita rispondiDisponibilita(IdentitaAutenticata attore, UUID contattoId,
            boolean disponibile) {
        verificaRuoloErogatore(attore);
        var riferimento = contatti.cercaRiferimento(contattoId)
                .orElseThrow(() -> new NoSuchElementException("Contatto inesistente"));
        verificaDestinatario(attore, riferimento.getErogatoreId());

        var richiesta = richieste.cercaPerAggiornamento(riferimento.getRichiestaId())
                .orElseThrow(() -> new NoSuchElementException("Richiesta inesistente"));
        if (richiesta.getStato() != StatoRichiesta.PRESA_IN_CARICO) {
            throw new StatoContattoIncompatibileException("La richiesta non è in presa in carico");
        }

        var contatto = contatti.findById(contattoId)
                .orElseThrow(() -> new NoSuchElementException("Contatto inesistente"));
        contatto.registraRisposta(disponibile);
        return aDto(contatto);
    }

    /** @see RichiesteApi#elencoContatti(IdentitaAutenticata, UUID) */
    public List<ContattoDisponibilita> elencoContatti(IdentitaAutenticata attore, UUID richiestaId) {
        if (attore == null || attore.accountId() == null || attore.ruolo() != RuoloUtente.BANCO) {
            throw new SecurityException("Lo storico dei contatti è riservato al Banco");
        }
        if (!richieste.existsById(richiestaId)) {
            throw new NoSuchElementException("Richiesta inesistente");
        }
        return contatti.findAllByRichiesta_IdOrderByInviatoIlAscIdAsc(richiestaId).stream()
                .map(DisponibilitaService::aDto)
                .toList();
    }

    private static void verificaRuoloErogatore(IdentitaAutenticata attore) {
        if (attore == null || attore.accountId() == null
                || (attore.ruolo() != RuoloUtente.OPT && attore.ruolo() != RuoloUtente.OPERATORE)) {
            throw new SecurityException("Operazione riservata agli erogatori");
        }
    }

    private static void verificaDestinatario(IdentitaAutenticata attore, UUID erogatoreId) {
        if (!attore.accountId().equals(erogatoreId)) {
            throw new SecurityException("Contatto indirizzato a un altro account");
        }
    }

    private static ContattoDisponibilita aDto(ContattoDisponibilitaJpa contatto) {
        return new ContattoDisponibilita(contatto.getId(), contatto.getRichiesta().getId(),
                contatto.getErogatoreId(), contatto.getAmbito(), contatto.getEsito());
    }
}
