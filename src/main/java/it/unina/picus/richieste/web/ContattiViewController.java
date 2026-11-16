package it.unina.picus.richieste.web;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Presenta le viste dei contatti di disponibilità. Gli endpoint di Richieste verificano
 * destinatario, permessi ed esito prima di restituire dati o registrare risposte.
 */
@Controller
class ContattiViewController {

    private final ObjectProvider<RequestMappingHandlerMapping> rotte;

    ContattiViewController(@Qualifier("requestMappingHandlerMapping")
            ObjectProvider<RequestMappingHandlerMapping> rotte) {
        this.rotte = rotte;
    }

    /** Mostra alla OPT la vista di invio con i controlli disattivati. */
    @GetMapping("/richieste/nuova")
    String nuova(IdentitaAutenticata identita) {
        if (identita.ruolo() != RuoloUtente.OPT) {
            throw new AccessDeniedException("Invio riservato alle OPT");
        }
        return "richieste/nuova";
    }

    /** Mostra la struttura del dettaglio in attesa del servizio di consultazione. */
    @GetMapping("/richieste/{richiestaId}")
    String dettaglio(@PathVariable UUID richiestaId, Model modello) {
        modello.addAttribute("richiestaId", richiestaId);
        modello.addAttribute("dashboard", "/dashboard");
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

    private boolean endpointRegistrato(String percorso) {
        return rotte.getObject().getHandlerMethods().keySet().stream()
                .anyMatch(mapping -> mapping.getPatternValues().contains(percorso));
    }
}
