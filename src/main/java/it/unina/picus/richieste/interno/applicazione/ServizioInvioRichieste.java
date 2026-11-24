package it.unina.picus.richieste.interno.applicazione;

import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.interno.dominio.RichiestaDiMisura;
import it.unina.picus.richieste.interno.dominio.RichiestaRepository;
import it.unina.picus.richieste.interno.dominio.RichiestaRepository.MisuraNonSelezionabileException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import it.unina.picus.shared.ControlloAccesso;
import java.util.UUID;

/** Casi d'uso della #48, senza comandi di transizione appartenenti al coordinamento. */
@Service
@Transactional(readOnly = true)
public class ServizioInvioRichieste implements InvioRichiesteApi {
    private static final String ERRORE_MISURA = "Seleziona una misura disponibile";

    private final RichiestaRepository repository;
    private final ConsultazioneCatalogoApi catalogo;
    private final Clock clock;

    public ServizioInvioRichieste(RichiestaRepository repository, ConsultazioneCatalogoApi catalogo, Clock clock) {
        this.repository = repository;
        this.catalogo = catalogo;
        this.clock = clock;
    }

    /**
     * Convalida i riferimenti del nuovo invio e registra la richiesta nella stessa transazione.
     * Un ritento di un invio già registrato può ancora leggere la misura dallo storico.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RichiestaSintesi invia(IdentitaAutenticata attore, DatiInvio dati, UUID riferimentoInvio) {
        richiediRuolo(attore, RuoloUtente.OPT);
        DatiInvio normalizzati = validaENormalizza(dati);
        var precedente = repository.cercaPerInvio(attore.accountId(), riferimentoInvio);
        if (precedente.isPresent()) {
            return confermaInvio(precedente.get(), normalizzati);
        }

        Map<String, String> errori = new LinkedHashMap<>();
        if (catalogo.cercaMisura(normalizzati.misuraId()).filter(Misura::attiva).isEmpty()) {
            if (riferimentoInvio != null) {
                // Un invio concorrente può avere concluso prima della disattivazione.
                var concorrente = repository.cercaPerInvio(attore.accountId(), riferimentoInvio);
                if (concorrente.isPresent()) {
                    return confermaInvio(concorrente.get(), normalizzati);
                }
            }
            errori.put("misuraId", ERRORE_MISURA);
        }
        if (catalogo.cercaComune(normalizzati.comuneCodice()).isEmpty()) {
            errori.put("comuneCodice", "Seleziona un comune del catalogo");
        }
        if (!errori.isEmpty()) {
            throw new DatiRichiestaNonValidiException(errori);
        }

        var nuova = RichiestaDiMisura.inviata(attore.accountId(), normalizzati, clock.instant(), riferimentoInvio);
        try {
            return confermaInvio(repository.registra(nuova), normalizzati);
        } catch (MisuraNonSelezionabileException e) {
            throw new DatiRichiestaNonValidiException(Map.of("misuraId", ERRORE_MISURA));
        }
    }

    private RichiestaSintesi confermaInvio(RichiestaDiMisura richiesta, DatiInvio dati) {
        if (!richiesta.stessoInvio(dati)) {
            throw new ConflittoRichiestaException("Il riferimento di invio è già associato a dati diversi");
        }
        return sintesi(richiesta, new HashMap<>());
    }

    @Override
    public List<RichiestaSintesi> elencoRichiesteOpt(IdentitaAutenticata attore) {
        richiediRuolo(attore, RuoloUtente.OPT);
        return sintesi(repository.cercaPerTitolare(attore.accountId()));
    }

    @Override
    public List<RichiestaSintesi> codaBanco(IdentitaAutenticata attore) {
        richiediRuolo(attore, RuoloUtente.BANCO);
        return sintesi(repository.codaBanco());
    }

    @Override
    public RichiestaDettaglio consultaRichiesta(IdentitaAutenticata attore, UUID richiestaId) {
        richiediIdentita(attore);
        if (richiestaId == null) {
            throw new DatiRichiestaNonValidiException(Map.of("richiestaId", "Identificativo obbligatorio"));
        }
        var richiesta = repository.cercaPerId(richiestaId)
                .orElseThrow(() -> new RisorsaRichiesteNonTrovataException());
        boolean banco = attore.ruolo() == RuoloUtente.BANCO;
        boolean titolare = attore.ruolo() == RuoloUtente.OPT
                && attore.accountId().equals(richiesta.titolareId());
        boolean assegnato = (attore.ruolo() == RuoloUtente.OPT || attore.ruolo() == RuoloUtente.OPERATORE)
                && attore.accountId().equals(richiesta.erogatoreId());
        ControlloAccesso.richiedi(banco || titolare || assegnato, "Accesso alla richiesta non consentito");
        return new RichiestaDettaglio(sintesi(richiesta, new HashMap<>()),
                banco || titolare ? richiesta.titolareId() : null,
                richiesta.tipoDestinatario(), richiesta.comuneCodice(),
                banco || titolare ? richiesta.description() : null,
                richiesta.erogatoreId(), richiesta.accordo());
    }

    private List<RichiestaSintesi> sintesi(List<RichiestaDiMisura> richieste) {
        Map<Long, String> nomiMisure = new HashMap<>();
        return richieste.stream().map(r -> sintesi(r, nomiMisure)).toList();
    }

    private RichiestaSintesi sintesi(RichiestaDiMisura richiesta, Map<Long, String> nomiMisure) {
        String nomeMisura = nomiMisure.computeIfAbsent(richiesta.misuraId(), id -> catalogo.cercaMisura(id)
                .orElseThrow(() -> new IllegalStateException("Riferimento di catalogo non disponibile"))
                .dati().nome());
        return new RichiestaSintesi(richiesta.id(), richiesta.inviataIl(), richiesta.misuraId(),
                nomeMisura, richiesta.stato());
    }

    private static DatiInvio validaENormalizza(DatiInvio dati) {
        Map<String, String> errori = new LinkedHashMap<>();
        if (dati == null || dati.misuraId() <= 0) {
            errori.put("misuraId", ERRORE_MISURA);
        }
        if (dati == null || dati.tipoDestinatario() == null) {
            errori.put("tipoDestinatario", "Seleziona PERSONA o NUCLEO");
        }
        String comune = dati == null || dati.comuneCodice() == null ? "" : dati.comuneCodice().strip();
        if (comune.isEmpty() || comune.length() > 50
                || comune.codePoints().anyMatch(Character::isISOControl)) {
            errori.put("comuneCodice", "Seleziona un comune del catalogo");
        }
        String descrizione = dati == null || dati.description() == null ? null : dati.description().strip();
        if (descrizione != null && (descrizione.length() > 1000 || descrizione.codePoints()
                .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))) {
            errori.put("description", "Usa testo semplice, fino a 1000 caratteri, senza identificativi degli assistiti");
        }
        if (!errori.isEmpty()) {
            throw new DatiRichiestaNonValidiException(errori);
        }
        return new DatiInvio(dati.misuraId(), dati.tipoDestinatario(), comune,
                descrizione == null || descrizione.isEmpty() ? null : descrizione);
    }

    private static void richiediRuolo(IdentitaAutenticata attore, RuoloUtente ruolo) {
        richiediIdentita(attore);
        ControlloAccesso.richiedi(attore.ruolo() == ruolo, "Ruolo non autorizzato per questa operazione");
    }

    private static void richiediIdentita(IdentitaAutenticata attore) {
        ControlloAccesso.richiedi(attore != null && attore.accountId() != null && attore.ruolo() != null,
                "Identità verificata obbligatoria");
    }
}
