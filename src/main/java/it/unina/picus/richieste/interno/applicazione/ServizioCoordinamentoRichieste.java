package it.unina.picus.richieste.interno.applicazione;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import it.unina.picus.catalogo.api.RicercaErogatoriApi;
import it.unina.picus.comunicazioni.api.AvvisiOperativiApi;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.CoordinamentoRichiesteApi;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
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
    private final InvioRichiesteApi consultazione;
    private final AvvisiOperativiApi avvisi;
    private final Clock clock;

    public ServizioCoordinamentoRichieste(RichiestaRepository richieste, ContattiRepository contatti,
            RicercaErogatoriApi catalogo, InvioRichiesteApi consultazione, AvvisiOperativiApi avvisi, Clock clock) {
        this.richieste = richieste;
        this.contatti = contatti;
        this.catalogo = catalogo;
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

    private List<ErogatoreCompatibile> compatibili(RichiestaDiMisura richiesta) {
        return catalogo.cercaErogatoriCompatibili(richiesta.misuraId(), richiesta.comuneCodice());
    }

    private void verificaEsaurimentoLocale(UUID richiestaId, List<ErogatoreCompatibile> opzioni) {
        var ultimi = ultimiContatti(richiestaId);
        boolean localeNonDeclinato = opzioni.stream()
                .filter(o -> o.ambito() != AmbitoErogazione.CENTRO_PICUS_A_DISTANZA)
                .anyMatch(o -> {
                    var ultimo = ultimi.get(new Opzione(o.accountId(), o.ambito()));
                    return ultimo == null || ultimo.esito() != EsitoContatto.NON_DISPONIBILE;
                });
        if (localeNonDeclinato) {
            throw new ConflittoRichiestaException("Valutare prima la disponibilità degli erogatori locali");
        }
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
