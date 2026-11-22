package it.unina.picus.richieste.interno.applicazione;

import it.unina.picus.comunicazioni.api.AvvisiAnnullamentoApi;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import it.unina.picus.richieste.interno.dominio.RegoleAccessoRichiesta;
import it.unina.picus.richieste.interno.dominio.RegoleAccessoRichiesta.Soggetti;
import it.unina.picus.richieste.interno.dominio.RichiestaRepository;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service Layer per registrare l'annullamento e consegnare gli avvisi nella stessa transazione. */
@Service
public class ServizioAnnullamentoRichieste {
    private final RichiestaRepository richieste;
    private final InvioRichiesteApi consultazione;
    private final UtenzeApi utenze;
    private final AvvisiAnnullamentoApi avvisi;

    /**
     * Collega il comando alle porte usate per leggere, aggiornare e notificare la richiesta.
     *
     * @param richieste accesso alla richiesta sotto lock
     * @param consultazione proiezione della richiesta per l'account autorizzato
     * @param utenze risoluzione dell'account di coordinamento del Banco
     * @param avvisi scrittura degli avvisi operativi
     */
    public ServizioAnnullamentoRichieste(RichiestaRepository richieste, InvioRichiesteApi consultazione,
            UtenzeApi utenze, AvvisiAnnullamentoApi avvisi) {
        this.richieste = richieste;
        this.consultazione = consultazione;
        this.utenze = utenze;
        this.avvisi = avvisi;
    }

    /**
     * Annulla la richiesta dopo aver verificato i permessi sulla pratica bloccata.
     * Il lock è condiviso con l'interpello, l'assegnazione e la risposta. Dopo l'annullamento
     * questi comandi ricevono un conflitto di stato e lo storico dei contatti resta consultabile.
     *
     * @param attore identità verificata dalla sessione
     * @param richiestaId identificativo della richiesta
     * @param motivo motivo facoltativo da Creata, ignorato in caso di ripetizione autorizzata
     * @return sintesi della richiesta Annullata
     * @throws SecurityException se l'attore non ha il permesso di annullare la richiesta
     * @throws RisorsaRichiesteNonTrovataException se la richiesta non esiste
     * @throws DatiRichiestaNonValidiException se il motivo richiesto manca o non è valido
     * @see RichiesteApi#annullaRichiesta(IdentitaAutenticata, UUID, String)
     */
    @Transactional
    public RichiestaSintesi annullaRichiesta(IdentitaAutenticata attore, UUID richiestaId, String motivo) {
        var richiesta = richieste.cercaPerAggiornamento(richiestaId)
                .orElseThrow(RisorsaRichiesteNonTrovataException::new);
        var soggetti = new Soggetti(richiesta.titolareId(), richiesta.erogatoreId());
        RegoleAccessoRichiesta.verificaAnnullamento(attore, soggetti);

        var annullata = richiesta.annulla(motivo);
        if (!annullata.equals(richiesta)) {
            richieste.aggiorna(annullata);
            avvisi.avvisaAnnullamento(richiestaId, destinatari(soggetti));
        }
        return consultazione.consultaRichiesta(attore, richiestaId).richiesta();
    }

    /** {@return account del Banco, della OPT titolare e dell'eventuale erogatore assegnato} */
    private Set<UUID> destinatari(Soggetti soggetti) {
        Set<UUID> destinatari = new HashSet<>();
        utenze.elencoAccountAttivi(RuoloUtente.BANCO).forEach(banco -> destinatari.add(banco.id()));
        destinatari.add(soggetti.titolareId());
        if (soggetti.erogatoreId() != null) {
            destinatari.add(soggetti.erogatoreId());
        }
        return destinatari;
    }
}
