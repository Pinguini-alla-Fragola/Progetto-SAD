package it.unina.picus.richieste.interno.applicazione;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import it.unina.picus.shared.ControlloAccesso;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettaglioContatto;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.interno.dominio.StatoContattoIncompatibileException;
import it.unina.picus.richieste.interno.persistenza.ContattoDisponibilitaJpa;
import it.unina.picus.richieste.interno.persistenza.ContattoDisponibilitaRepository;
import it.unina.picus.richieste.interno.persistenza.RichiestaRepository;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Coordina la consultazione e la risposta per un contatto di disponibilità già aperto.
 *
 * <p>Applica il pattern <em>Service Layer</em>. Concentra i controlli di accesso e i
 * confini delle transazioni dei casi d'uso e usa i repository per accedere ai dati.
 */
@Service
@Transactional(readOnly = true)
public class DisponibilitaService {

    private final RichiestaRepository richieste;
    private final ContattoDisponibilitaRepository contatti;

    /**
     * Costruisce il servizio con i repository usati dai casi d'uso di disponibilità.
     *
     * @param richieste repository delle richieste di misura
     * @param contatti repository dei contatti di disponibilità
     */
    public DisponibilitaService(RichiestaRepository richieste, ContattoDisponibilitaRepository contatti) {
        this.richieste = richieste;
        this.contatti = contatti;
    }

    /**
     * Fornisce all'erogatore destinatario solo le informazioni necessarie per valutare il contatto.
     *
     * @param attore identità verificata della OPT o dell'operatore destinatario
     * @param contattoId identificativo del contatto da consultare
     * @return contatto e dati essenziali della richiesta
     * @throws SecurityException se l'attore non è un erogatore o non è il destinatario
     * @throws NoSuchElementException se il contatto non esiste
     * @see RichiesteApi#consultaContatto(IdentitaAutenticata, UUID)
     */
    public DettaglioContatto consultaContatto(IdentitaAutenticata attore, UUID contattoId) {
        verificaRuoloErogatore(attore);
        var contatto = contatti.findOneById(contattoId)
                .orElseThrow(RisorsaRichiesteNonTrovataException::new);
        verificaDestinatario(attore, contatto.getErogatoreId());

        var richiesta = contatto.getRichiesta();
        return new DettaglioContatto(aDto(contatto), richiesta.getMisuraId(), contatto.getNomeMisura(),
                richiesta.getTipoDestinatario(), richiesta.getComuneCodice());
    }

    /**
     * Registra la risposta del destinatario mantenendo la richiesta in presa in carico.
     *
     * <p>Dopo aver verificato il destinatario, acquisisce un lock pessimistico sulla richiesta.
     * Legge l'esito del contatto sotto lock, così soltanto un invio concorrente può registrare
     * la risposta.
     *
     * @param attore identità verificata della OPT o dell'operatore destinatario
     * @param contattoId identificativo del contatto a cui si risponde
     * @param disponibile {@code true} per confermare, {@code false} per rifiutare
     * @return contatto con l'esito registrato
     * @throws SecurityException se l'attore non è un erogatore o non è il destinatario
     * @throws NoSuchElementException se il contatto o la richiesta non esistono
     * @throws StatoContattoIncompatibileException se il contatto non è in attesa o la richiesta
     *         non è in presa in carico
     * @see RichiesteApi#rispondiDisponibilita(IdentitaAutenticata, UUID, boolean)
     */
    @Transactional
    public ContattoDisponibilita rispondiDisponibilita(IdentitaAutenticata attore, UUID contattoId,
            boolean disponibile) {
        verificaRuoloErogatore(attore);
        var riferimento = contatti.cercaRiferimento(contattoId)
                .orElseThrow(RisorsaRichiesteNonTrovataException::new);
        verificaDestinatario(attore, riferimento.getErogatoreId());

        var richiesta = richieste.cercaPerAggiornamento(riferimento.getRichiestaId())
                .orElseThrow(RisorsaRichiesteNonTrovataException::new);
        if (richiesta.getStato() != StatoRichiesta.PRESA_IN_CARICO) {
            throw new StatoContattoIncompatibileException("La richiesta non è in presa in carico");
        }

        var contatto = contatti.findById(contattoId)
                .orElseThrow(RisorsaRichiesteNonTrovataException::new);
        contatto.registraRisposta(disponibile);
        return aDto(contatto);
    }

    /**
     * Elenca per il Banco i contatti della richiesta e i rispettivi esiti.
     *
     * @param attore identità verificata del Banco
     * @param richiestaId identificativo della richiesta
     * @return contatti in ordine di invio, eventualmente nessuno
     * @throws SecurityException se l'attore non è il Banco
     * @throws NoSuchElementException se la richiesta non esiste
     * @see RichiesteApi#elencoContatti(IdentitaAutenticata, UUID)
     */
    public List<ContattoDisponibilita> elencoContatti(IdentitaAutenticata attore, UUID richiestaId) {
        ControlloAccesso.richiedi(attore != null && attore.accountId() != null && attore.ruolo() == RuoloUtente.BANCO,
                "Lo storico dei contatti è riservato al Banco");
        if (!richieste.existsById(richiestaId)) {
            throw new RisorsaRichiesteNonTrovataException();
        }
        return contatti.findAllByRichiesta_IdOrderByOrdineInvioAsc(richiestaId).stream()
                .map(DisponibilitaService::aDto)
                .toList();
    }

    private static void verificaRuoloErogatore(IdentitaAutenticata attore) {
        ControlloAccesso.richiedi(attore != null && attore.accountId() != null
                && (attore.ruolo() == RuoloUtente.OPT || attore.ruolo() == RuoloUtente.OPERATORE),
                "Operazione riservata agli erogatori");
    }

    private static void verificaDestinatario(IdentitaAutenticata attore, UUID erogatoreId) {
        ControlloAccesso.richiedi(attore.accountId().equals(erogatoreId), "Contatto indirizzato a un altro account");
    }

    private static ContattoDisponibilita aDto(ContattoDisponibilitaJpa contatto) {
        return new ContattoDisponibilita(contatto.getId(), contatto.getRichiesta().getId(),
                contatto.getErogatoreId(), contatto.getAmbito(), contatto.getEsito());
    }
}
