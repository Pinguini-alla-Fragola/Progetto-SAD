package it.unina.picus.richieste.web;

import java.util.UUID;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import it.unina.picus.richieste.api.RichiesteApi.AccordoADistanza;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;

/**
 * Presenta le viste dei contatti di disponibilità. Gli endpoint di Richieste verificano
 * destinatario, permessi ed esito prima di restituire dati o registrare risposte.
 */
@Controller
class ContattiViewController {

    private final ObjectProvider<RequestMappingHandlerMapping> rotte;
    private final InvioRichiesteApi richieste;
    private final ConsultazioneCatalogoApi catalogo;
    private final UtenzeApi utenze;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm", Locale.ITALIAN)
            .withZone(ZoneId.of("Europe/Rome"));

    ContattiViewController(@Qualifier("requestMappingHandlerMapping")
            ObjectProvider<RequestMappingHandlerMapping> rotte, InvioRichiesteApi richieste,
            ConsultazioneCatalogoApi catalogo, UtenzeApi utenze) {
        this.rotte = rotte;
        this.richieste = richieste;
        this.catalogo = catalogo;
        this.utenze = utenze;
    }

    /** Carica i valori di catalogo ammessi per l'invio della OPT. */
    @GetMapping("/richieste/nuova")
    String nuova(IdentitaAutenticata identita, Model modello) {
        if (identita.ruolo() != RuoloUtente.OPT) {
            throw new AccessDeniedException("Invio riservato alle OPT");
        }
        modello.addAttribute("misure", catalogo.elencoMisureAttive());
        modello.addAttribute("comuni", catalogo.elencoComuni());
        modello.addAttribute("utenteId", identita.accountId());
        return "richieste/nuova";
    }

    /** Consulta tramite il caso d'uso, che filtra i dati e controlla il legame con la pratica. */
    @GetMapping("/richieste/{richiestaId}")
    String dettaglio(@PathVariable UUID richiestaId, IdentitaAutenticata identita, Model modello) {
        var dettaglio = richieste.consultaRichiesta(identita, richiestaId);
        modello.addAttribute("richiestaId", richiestaId);
        modello.addAttribute("dashboard", "/dashboard");
        modello.addAttribute("dettaglio", dettaglio);
        modello.addAttribute("richiesta", dettaglio.richiesta());
        var comuneCatalogo = catalogo.cercaComune(dettaglio.comuneCodice());
        modello.addAttribute("comune", comuneCatalogo
                .map(c -> c.nome()).orElse(dettaglio.comuneCodice()));
        modello.addAttribute("comuneTerritorio", comuneCatalogo
                .map(c -> c.descrizioneTerritoriale()).orElse(""));
        modello.addAttribute("comuneIstat", comuneCatalogo
                .map(c -> c.codiceIstat() != null ? c.codiceIstat() : "").orElse(""));
        modello.addAttribute("dataInvio", DATA.format(dettaglio.richiesta().inviataIl()));
        modello.addAttribute("banco", identita.ruolo() == RuoloUtente.BANCO);
        if (dettaglio.titolareId() != null) {
            modello.addAttribute("nomeOptRichiedente", utenze.cercaAccount(dettaglio.titolareId())
                    .map(UtenzeApi.AccountSintetico::nomeVisualizzato).orElse("OPT non disponibile"));
        }
        modello.addAttribute("nomeErogatore", dettaglio.erogatoreAssegnatoId() == null ? null
                : utenze.cercaAccount(dettaglio.erogatoreAssegnatoId())
                    .map(UtenzeApi.AccountSintetico::nomeVisualizzato).orElse("Erogatore assegnato"));
        if (dettaglio.accordo() instanceof AccordoInPresenza accordo) {
            modello.addAttribute("accordoPresenza", accordo);
            modello.addAttribute("dataAccordo", DATA.format(accordo.dataOra()));
        } else if (dettaglio.accordo() instanceof AccordoADistanza accordo) {
            modello.addAttribute("accordoRemoto", accordo);
            modello.addAttribute("dataAccordo", DATA.format(accordo.dataOra()));
        }
        return "richieste/dettaglio";
    }

    @GetMapping("/richieste/contatti/{contattoId}")
    String risposta(@PathVariable UUID contattoId, IdentitaAutenticata identita, Model modello) {
        if (identita.ruolo() == RuoloUtente.BANCO) {
            throw new AccessDeniedException("Consultazione riservata all'erogatore");
        }
        modello.addAttribute("dashboard", "/dashboard");
        modello.addAttribute("contattoId", contattoId);
        modello.addAttribute("contattiDisponibili", endpointRegistrato("/api/contatti-disponibilita/{contattoId}"));
        return "richieste/rispondi";
    }

    @GetMapping("/banco/richieste/{richiestaId}/contatti")
    String storico(@PathVariable UUID richiestaId, Model modello) {
        modello.addAttribute("richiestaId", richiestaId);
        modello.addAttribute("contattiDisponibili", endpointRegistrato("/api/richieste/{richiestaId}/contatti"));
        return "richieste/contatti";
    }

    /** Le pagine mostrano un errore HTML; gli endpoint API conservano il proprio contratto JSON. */
    @ExceptionHandler(RisorsaRichiesteNonTrovataException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String richiestaNonTrovata() {
        return "error/404";
    }

    private boolean endpointRegistrato(String percorso) {
        return rotte.getObject().getHandlerMethods().keySet().stream()
                .anyMatch(mapping -> mapping.getPatternValues().contains(percorso));
    }
}
