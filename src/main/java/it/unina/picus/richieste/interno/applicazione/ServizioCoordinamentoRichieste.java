package it.unina.picus.richieste.interno.applicazione;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import it.unina.picus.catalogo.api.RicercaErogatoriApi;
import it.unina.picus.comunicazioni.api.AvvisiOperativiApi;
import it.unina.picus.richieste.api.CoordinamentoRichiesteApi;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.interno.dominio.OrdinamentoErogatori;
import it.unina.picus.richieste.interno.persistenza.ContattoDisponibilitaJpa;
import it.unina.picus.richieste.interno.persistenza.ContattoDisponibilitaRepository;
import it.unina.picus.richieste.interno.persistenza.RichiestaJpa;
import it.unina.picus.richieste.interno.persistenza.RichiestaRepository;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Service Layer: i comandi condividono il lock della pratica con le risposte degli erogatori. */
@Service
@Transactional(readOnly = true)
public class ServizioCoordinamentoRichieste implements CoordinamentoRichiesteApi {
    private final RichiestaRepository richieste;
    private final ContattoDisponibilitaRepository contatti;
    private final RicercaErogatoriApi catalogo;
    private final InvioRichiesteApi consultazione;
    private final AvvisiOperativiApi avvisi;
    private final Clock clock;

    public ServizioCoordinamentoRichieste(RichiestaRepository richieste, ContattoDisponibilitaRepository contatti,
            RicercaErogatoriApi catalogo, InvioRichiesteApi consultazione, AvvisiOperativiApi avvisi, Clock clock) {
        this.richieste = richieste;
        this.contatti = contatti;
        this.catalogo = catalogo;
        this.consultazione = consultazione;
        this.avvisi = avvisi;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RichiestaSintesi prendiInCarico(IdentitaAutenticata attore, UUID richiestaId) {
        verificaBanco(attore);
        var richiesta = blocca(richiestaId);
        richiesta.prendiInCarico();
        return consultazione.consultaRichiesta(attore, richiestaId).richiesta();
    }

    @Override
    public List<Candidato> cercaErogatoriOrdinati(IdentitaAutenticata attore, UUID richiestaId) {
        verificaBanco(attore);
        var richiesta = richieste.findById(richiestaId).orElseThrow(() -> new NoSuchElementException("Richiesta non trovata"));
        richiesta.verificaPresaInCarico();
        Map<UUID, Integer> carichi = new HashMap<>();
        richieste.carichi(StatoRichiesta.CONFERMATA, clock.instant())
                .forEach(c -> carichi.put(c.getErogatoreId(), Math.toIntExact(c.getNumero())));
        return OrdinamentoErogatori.ordina(compatibili(richiesta), carichi);
    }

    @Override
    @Transactional
    public ContattoDisponibilita interpellaErogatore(IdentitaAutenticata attore, UUID richiestaId,
            UUID erogatoreId, AmbitoErogazione ambito) {
        verificaBanco(attore);
        if (erogatoreId == null || ambito == null) {
            throw new IllegalArgumentException("Erogatore e ambito obbligatori");
        }
        var richiesta = blocca(richiestaId);
        richiesta.verificaPresaInCarico();
        verificaAssenzaContattiInAttesa(richiestaId);
        var opzioni = compatibili(richiesta);
        if (opzioni.stream().noneMatch(o -> o.accountId().equals(erogatoreId) && o.ambito() == ambito)) {
            throw new IllegalArgumentException("Erogatore non compatibile con la richiesta");
        }
        if (ambito == AmbitoErogazione.CENTRO_PICUS_A_DISTANZA) {
            verificaEsaurimentoLocale(richiestaId, opzioni);
        }
        String nomeMisura = consultazione.consultaRichiesta(attore, richiestaId).richiesta().nomeMisura();
        var contatto = contatti.saveAndFlush(ContattoDisponibilitaJpa.crea(
                richiesta, erogatoreId, ambito, nomeMisura, clock.instant()));
        avvisi.avvisaDisponibilita(richiestaId, contatto.getId(), erogatoreId);
        return aDto(contatto);
    }

    @Override
    @Transactional
    public RichiestaSintesi assegnaErogatore(IdentitaAutenticata attore, UUID contattoId) {
        verificaBanco(attore);
        var riferimento = contatti.cercaRiferimento(contattoId)
                .orElseThrow(() -> new NoSuchElementException("Contatto non trovato"));
        var richiesta = blocca(riferimento.getRichiestaId());
        richiesta.verificaPresaInCarico();
        var contatto = contatti.findById(contattoId).orElseThrow(() -> new NoSuchElementException("Contatto non trovato"));
        if (contatto.getEsito() != EsitoContatto.DISPONIBILE) {
            throw new IllegalStateException("Il contatto non attesta disponibilità");
        }
        var ultimo = ultimiContatti(richiesta.getId())
                .get(new Opzione(contatto.getErogatoreId(), contatto.getAmbito()));
        if (ultimo == null || !ultimo.getId().equals(contattoId)) {
            throw new IllegalStateException("La risposta è stata superata da un contatto successivo");
        }
        verificaAssenzaContattiInAttesa(richiesta.getId());
        if (compatibili(richiesta).stream().noneMatch(o -> o.accountId().equals(contatto.getErogatoreId())
                && o.ambito() == contatto.getAmbito())) {
            throw new IllegalStateException("L'erogatore non è più abilitato per questa opzione");
        }
        richiesta.assegna(contatto.getErogatoreId(), contatto.getAmbito());
        return consultazione.consultaRichiesta(attore, richiesta.getId()).richiesta();
    }

    private List<ErogatoreCompatibile> compatibili(RichiestaJpa richiesta) {
        return catalogo.cercaErogatoriCompatibili(richiesta.getMisuraId(), richiesta.getComuneCodice());
    }

    private void verificaEsaurimentoLocale(UUID richiestaId, List<ErogatoreCompatibile> opzioni) {
        var ultimi = ultimiContatti(richiestaId);
        boolean localeNonDeclinato = opzioni.stream()
                .filter(o -> o.ambito() != AmbitoErogazione.CENTRO_PICUS_A_DISTANZA)
                .anyMatch(o -> {
                    var ultimo = ultimi.get(new Opzione(o.accountId(), o.ambito()));
                    return ultimo == null || ultimo.getEsito() != EsitoContatto.NON_DISPONIBILE;
                });
        if (localeNonDeclinato) {
            throw new IllegalStateException("Valutare prima la disponibilità degli erogatori locali");
        }
    }

    private Map<Opzione, ContattoDisponibilitaJpa> ultimiContatti(UUID richiestaId) {
        Map<Opzione, ContattoDisponibilitaJpa> ultimi = new HashMap<>();
        contatti.findAllByRichiesta_IdOrderByOrdineInvioAsc(richiestaId)
                .forEach(c -> ultimi.put(new Opzione(c.getErogatoreId(), c.getAmbito()), c));
        return ultimi;
    }

    private void verificaAssenzaContattiInAttesa(UUID richiestaId) {
        if (contatti.existsByRichiesta_IdAndEsito(richiestaId, EsitoContatto.IN_ATTESA.name())) {
            throw new IllegalStateException("Un contatto è ancora in attesa di risposta");
        }
    }

    private RichiestaJpa blocca(UUID richiestaId) {
        return richieste.cercaPerAggiornamento(richiestaId)
                .orElseThrow(() -> new NoSuchElementException("Richiesta non trovata"));
    }

    private static void verificaBanco(IdentitaAutenticata attore) {
        if (attore == null || attore.accountId() == null || attore.ruolo() != RuoloUtente.BANCO) {
            throw new SecurityException("Operazione riservata al Banco");
        }
    }

    private static ContattoDisponibilita aDto(ContattoDisponibilitaJpa c) {
        return new ContattoDisponibilita(c.getId(), c.getRichiesta().getId(),
                c.getErogatoreId(), c.getAmbito(), c.getEsito());
    }

    private record Opzione(UUID accountId, AmbitoErogazione ambito) {}
}
