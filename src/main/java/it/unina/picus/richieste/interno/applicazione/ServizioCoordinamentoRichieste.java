package it.unina.picus.richieste.interno.applicazione;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.catalogo.api.RicercaErogatoriApi;
import it.unina.picus.comunicazioni.api.AvvisiOperativiApi;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.CoordinamentoRichiesteApi;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.AccordoADistanza;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.interno.dominio.ContattiRepository;
import it.unina.picus.richieste.interno.dominio.OrdinamentoErogatori;
import it.unina.picus.richieste.interno.dominio.RichiestaDiMisura;
import it.unina.picus.richieste.interno.dominio.RichiestaRepository;
import it.unina.picus.shared.ControlloAccesso;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Service Layer: i comandi condividono il lock della pratica con le risposte degli erogatori. */
@Service
@Transactional(readOnly = true)
public class ServizioCoordinamentoRichieste implements CoordinamentoRichiesteApi {
    private final RichiestaRepository richieste;
    private final ContattiRepository contatti;
    private final RicercaErogatoriApi catalogo;
    private final ConsultazioneCatalogoApi misure;
    private final InvioRichiesteApi consultazione;
    private final AvvisiOperativiApi avvisi;
    private final Clock clock;

    /**
     * Collega il coordinamento alle porte di persistenza e degli altri moduli.
     *
     * @param richieste accesso alle pratiche e al loro lock
     * @param contatti accesso alle richieste di disponibilità
     * @param catalogo ricerca di erogatori compatibili
     * @param misure consultazione delle modalità di erogazione
     * @param consultazione lettura del dettaglio secondo le regole di visibilità
     * @param avvisi registrazione degli avvisi operativi
     * @param clock orologio dei contatti
     */
    public ServizioCoordinamentoRichieste(RichiestaRepository richieste, ContattiRepository contatti,
            RicercaErogatoriApi catalogo, ConsultazioneCatalogoApi misure, InvioRichiesteApi consultazione,
            AvvisiOperativiApi avvisi, Clock clock) {
        this.richieste = richieste;
        this.contatti = contatti;
        this.catalogo = catalogo;
        this.misure = misure;
        this.consultazione = consultazione;
        this.avvisi = avvisi;
        this.clock = clock;
    }

    @Override
    public ContattoDisponibilita consultaContattoBanco(IdentitaAutenticata attore, UUID richiestaId, UUID contattoId) {
        verificaBanco(attore);
        return contatti.cerca(contattoId).filter(c -> c.richiestaId().equals(richiestaId))
                .orElseThrow(RisorsaRichiesteNonTrovataException::new);
    }

    @Override
    @Transactional
    public RichiestaSintesi prendiInCarico(IdentitaAutenticata attore, UUID richiestaId) {
        verificaBanco(attore);
        richieste.aggiorna(blocca(richiestaId).prendiInCarico());
        return consultazione.consultaRichiesta(attore, richiestaId).richiesta();
    }

    @Override
    public List<Candidato> cercaErogatoriOrdinati(IdentitaAutenticata attore, UUID richiestaId) {
        verificaBanco(attore);
        var richiesta = richieste.cercaPerId(richiestaId).orElseThrow(RisorsaRichiesteNonTrovataException::new);
        richiesta.verificaPresaInCarico();
        return OrdinamentoErogatori.ordina(compatibili(richiesta), richieste.carichiConfermati(clock.instant()));
    }

    @Override
    @Transactional
    public ContattoDisponibilita interpellaErogatore(IdentitaAutenticata attore, UUID richiestaId,
            UUID erogatoreId, AmbitoErogazione ambito) {
        verificaBanco(attore);
        if (erogatoreId == null || ambito == null) {
            throw new DatiRichiestaNonValidiException(Map.of("contatto", "Erogatore e ambito obbligatori"));
        }
        var richiesta = blocca(richiestaId);
        richiesta.verificaPresaInCarico();
        verificaAssenzaContattiInAttesa(richiestaId);
        var opzioni = compatibili(richiesta);
        if (opzioni.stream().noneMatch(o -> o.accountId().equals(erogatoreId) && o.ambito() == ambito)) {
            throw new DatiRichiestaNonValidiException(Map.of("contatto", "Erogatore non compatibile con la richiesta"));
        }
        if (ambito == AmbitoErogazione.CENTRO_PICUS_A_DISTANZA) {
            verificaEsaurimentoLocale(richiestaId, opzioni);
        }
        String nomeMisura = consultazione.consultaRichiesta(attore, richiestaId).richiesta().nomeMisura();
        var contatto = contatti.registra(richiestaId, erogatoreId, ambito, nomeMisura, clock.instant());
        avvisi.avvisaDisponibilita(richiestaId, contatto.id(), erogatoreId);
        return contatto;
    }

    @Override
    @Transactional
    public RichiestaSintesi assegnaErogatore(IdentitaAutenticata attore, UUID contattoId) {
        verificaBanco(attore);
        var richiestaId = contatti.richiestaDelContatto(contattoId).orElseThrow(RisorsaRichiesteNonTrovataException::new);
        var richiesta = blocca(richiestaId);
        richiesta.verificaPresaInCarico();
        var contatto = contatti.cerca(contattoId).orElseThrow(RisorsaRichiesteNonTrovataException::new);
        if (contatto.esito() != EsitoContatto.DISPONIBILE) {
            throw new ConflittoRichiestaException("Il contatto non attesta disponibilità");
        }
        var ultimo = ultimiContatti(richiestaId).get(new Opzione(contatto.erogatoreId(), contatto.ambito()));
        if (ultimo == null || !ultimo.id().equals(contattoId)) {
            throw new ConflittoRichiestaException("La risposta è stata superata da un contatto successivo");
        }
        verificaAssenzaContattiInAttesa(richiestaId);
        if (!catalogo.bloccaCompatibilita(richiesta.misuraId(), richiesta.comuneCodice(),
                contatto.erogatoreId(), contatto.ambito())) {
            throw new ConflittoRichiestaException("L'erogatore non è più abilitato per questa opzione");
        }
        richieste.aggiorna(richiesta.assegna(contatto.erogatoreId(), contatto.ambito()));
        return consultazione.consultaRichiesta(attore, richiestaId).richiesta();
    }

    /**
     * Conferma l'accordo secondo l'ambito di assegnazione. Il lock della pratica
     * serializza i comandi e consente di salvare stato e accordo insieme.
     *
     * @param attore identità verificata del Banco
     * @param richiestaId identificativo della richiesta in programmazione
     * @param accordo dettagli concordati per la modalità assegnata
     * @return dettaglio della richiesta confermata
     * @throws SecurityException se l'attore non è il Banco
     * @throws RisorsaRichiesteNonTrovataException se la richiesta non esiste
     * @throws DatiRichiestaNonValidiException se i dettagli sono invalidi o la modalità è incompatibile
     * @throws ConflittoRichiestaException se stato o assegnazione impediscono la conferma
     * @see RichiesteApi#confermaAccordo
     */
    @Transactional
    public RichiestaDettaglio confermaAccordo(IdentitaAutenticata attore, UUID richiestaId,
            DettagliAccordo accordo) {
        verificaBanco(attore);
        var confermata = blocca(richiestaId).conferma(accordo);
        if (confermata.accordo() instanceof AccordoADistanza
                && misure.cercaMisura(confermata.misuraId()).filter(m -> m.dati().erogabileADistanza()).isEmpty()) {
            throw new DatiRichiestaNonValidiException(Map.of("modalita", "La misura non è erogabile a distanza"));
        }
        richieste.aggiorna(confermata);
        return consultazione.consultaRichiesta(attore, richiestaId);
    }

    /**
     * Registra il rifiuto dopo aver acquisito il lock della pratica.
     * Durante la ricerca il sistema verifica l'esito più recente di ogni opzione
     * compatibile, compresa quella a distanza. Dopo l'assegnazione il Banco valuta
     * l'esito della trattativa tenuta fuori dal flusso dei contatti.
     *
     * @param attore identità verificata del Banco
     * @param richiestaId identificativo della richiesta da rifiutare
     * @return sintesi della richiesta rifiutata
     * @throws SecurityException se l'attore non è il Banco
     * @throws RisorsaRichiesteNonTrovataException se la richiesta non esiste
     * @throws ConflittoRichiestaException se lo stato è incompatibile, un contatto è in attesa
     *         o, durante la ricerca, resta un'opzione compatibile senza diniego
     * @see RichiesteApi#rifiutaRichiesta
     */
    @Transactional
    public RichiestaSintesi rifiutaRichiesta(IdentitaAutenticata attore, UUID richiestaId) {
        verificaBanco(attore);
        var richiesta = blocca(richiestaId);
        var rifiutata = richiesta.rifiuta();
        verificaAssenzaContattiInAttesa(richiestaId);
        if (richiesta.stato() == StatoRichiesta.PRESA_IN_CARICO
                && haOpzioniNonDeclinate(richiestaId, compatibili(richiesta))) {
            throw new ConflittoRichiestaException("Restano opzioni compatibili da valutare");
        }
        richieste.aggiorna(rifiutata);
        return consultazione.consultaRichiesta(attore, richiestaId).richiesta();
    }

    private List<ErogatoreCompatibile> compatibili(RichiestaDiMisura richiesta) {
        return catalogo.cercaErogatoriCompatibili(richiesta.misuraId(), richiesta.comuneCodice());
    }

    private void verificaEsaurimentoLocale(UUID richiestaId, List<ErogatoreCompatibile> opzioni) {
        var locali = opzioni.stream().filter(o -> o.ambito() != AmbitoErogazione.CENTRO_PICUS_A_DISTANZA).toList();
        if (haOpzioniNonDeclinate(richiestaId, locali)) {
            throw new ConflittoRichiestaException("Valutare prima la disponibilità degli erogatori locali");
        }
    }

    private boolean haOpzioniNonDeclinate(UUID richiestaId, List<ErogatoreCompatibile> opzioni) {
        if (opzioni.isEmpty()) {
            return false;
        }
        var ultimi = ultimiContatti(richiestaId);
        return opzioni.stream().anyMatch(opzione -> {
            var ultimo = ultimi.get(new Opzione(opzione.accountId(), opzione.ambito()));
            return ultimo == null || ultimo.esito() != EsitoContatto.NON_DISPONIBILE;
        });
    }

    private Map<Opzione, ContattoDisponibilita> ultimiContatti(UUID richiestaId) {
        Map<Opzione, ContattoDisponibilita> ultimi = new HashMap<>();
        contatti.storico(richiestaId).forEach(c -> ultimi.put(new Opzione(c.erogatoreId(), c.ambito()), c));
        return ultimi;
    }

    private void verificaAssenzaContattiInAttesa(UUID richiestaId) {
        if (contatti.inAttesa(richiestaId)) {
            throw new ConflittoRichiestaException("Un contatto è ancora in attesa di risposta");
        }
    }

    private RichiestaDiMisura blocca(UUID richiestaId) {
        return richieste.cercaPerAggiornamento(richiestaId).orElseThrow(RisorsaRichiesteNonTrovataException::new);
    }

    private static void verificaBanco(IdentitaAutenticata attore) {
        ControlloAccesso.richiedi(attore != null && attore.accountId() != null && attore.ruolo() == RuoloUtente.BANCO,
                "Operazione riservata al Banco");
    }

    private record Opzione(UUID accountId, AmbitoErogazione ambito) {}
}
