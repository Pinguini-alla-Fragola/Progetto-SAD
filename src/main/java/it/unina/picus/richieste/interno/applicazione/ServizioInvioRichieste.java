package it.unina.picus.richieste.interno.applicazione;

import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.catalogo.api.SelezioneMisureApi;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.interno.dominio.RichiestaDiMisura;
import it.unina.picus.richieste.interno.dominio.RichiestaRepository;
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
import java.util.Optional;
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
    private final SelezioneMisureApi selezioneMisure;
    private final Clock clock;

    /**
     * Crea il caso d'uso di invio e consultazione delle richieste.
     *
     * @param repository persistenza delle richieste
     * @param catalogo consultazione storica e attiva di misure e comuni
     * @param selezioneMisure verifica transazionale sotto lock condiviso della misura selezionata
     * @param clock sorgente dell'istante di invio
     */
    public ServizioInvioRichieste(
            RichiestaRepository repository,
            ConsultazioneCatalogoApi catalogo,
            SelezioneMisureApi selezioneMisure,
            Clock clock) {
        this.repository = repository;
        this.catalogo = catalogo;
        this.selezioneMisure = selezioneMisure;
        this.clock = clock;
    }

    /**
     * Convalida i riferimenti del nuovo invio, acquisisce un lock condiviso sulla misura fino al
     * termine della transazione tramite {@link SelezioneMisureApi#bloccaMisuraAttiva(long)} e
     * registra la richiesta. Un ritento con la stessa chiave di idempotenza restituisce l'invio
     * originario anche se la misura è stata successivamente disattivata.
     *
     * @param attore identità verificata della OPT richiedente
     * @param dati dati della richiesta da registrare
     * @param riferimentoInvio chiave opzionale di idempotenza
     * @return sintesi della richiesta registrata o recuperata
     * @throws it.unina.picus.shared.AccessoNegatoException se l'attore non è una OPT autorizzata
     * @throws DatiRichiestaNonValidiException se i dati non sono validi o la misura non è attiva
     * @throws ConflittoRichiestaException se la chiave di idempotenza è già associata a dati diversi
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
            var ritento = recuperaRitentoConcorrente(attore.accountId(), riferimentoInvio, normalizzati);
            if (ritento.isPresent()) {
                return ritento.get();
            }
            errori.put("misuraId", ERRORE_MISURA);
        }
        var comuneCatalogo = catalogo.cercaComune(normalizzati.comuneCodice());
        if (comuneCatalogo.isEmpty()) {
            errori.put("comuneCodice", "Seleziona un comune del catalogo");
        } else if (!comuneCatalogo.get().codice().equals(normalizzati.comuneCodice())) {
            normalizzati = new DatiInvio(normalizzati.misuraId(), normalizzati.tipoDestinatario(),
                    comuneCatalogo.get().codice(), normalizzati.description());
        }
        if (!errori.isEmpty()) {
            throw new DatiRichiestaNonValidiException(errori);
        }

        if (!selezioneMisure.bloccaMisuraAttiva(normalizzati.misuraId())) {
            return recuperaRitentoConcorrente(attore.accountId(), riferimentoInvio, normalizzati)
                    .orElseThrow(() -> new DatiRichiestaNonValidiException(Map.of("misuraId", ERRORE_MISURA)));
        }

        var nuova = RichiestaDiMisura.inviata(attore.accountId(), normalizzati, clock.instant(), riferimentoInvio);
        return confermaInvio(repository.registra(nuova), normalizzati);
    }

    private Optional<RichiestaSintesi> recuperaRitentoConcorrente(
            UUID accountId, UUID riferimentoInvio, DatiInvio normalizzati) {
        if (riferimentoInvio == null) {
            return Optional.empty();
        }
        // Un invio concorrente può avere concluso prima della disattivazione o del lock.
        return repository.cercaPerInvio(accountId, riferimentoInvio)
                .map(concorrente -> confermaInvio(concorrente, normalizzati));
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
