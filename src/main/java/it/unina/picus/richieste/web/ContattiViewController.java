package it.unina.picus.richieste.web;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Presenta le viste dei contatti di disponibilità. Gli endpoint di Richieste verificano
 * destinatario, permessi ed esito prima di restituire dati o registrare risposte.
 */
@Controller
class ContattiViewController {

    @GetMapping("/richieste/contatti/{contattoId}")
    String risposta(@PathVariable UUID contattoId, IdentitaAutenticata identita, Model modello) {
        if (identita.ruolo() == RuoloUtente.BANCO) {
            throw new AccessDeniedException("Consultazione riservata all'erogatore");
        }
        modello.addAttribute("contattoId", contattoId);
        return "richieste/rispondi";
    }

    @GetMapping("/banco/richieste/{richiestaId}/contatti")
    String storico(@PathVariable UUID richiestaId, Model modello) {
        modello.addAttribute("richiestaId", richiestaId);
        return "richieste/contatti";
    }
}
