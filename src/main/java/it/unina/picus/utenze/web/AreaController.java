package it.unina.picus.utenze.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Punto di ingresso dopo il login: porta ciascun ruolo nella propria area. Le aree sono segnaposto
 * dello scheletro: i moduli di dominio vi aggiungeranno le proprie funzioni.
 */
@Controller
class AreaController {

    @GetMapping({"/", "/dashboard"})
    String home(IdentitaAutenticata identita) {
        return "redirect:" + switch (identita.ruolo()) {
            case BANCO -> "/banco";
            case OPT -> "/opt";
            case OPERATORE -> "/operatore";
        };
    }

    @GetMapping("/banco")
    String banco(Model model) {
        return area(model, "Banco", "Coordina richieste, offerte e monitoraggio del programma.");
    }

    @GetMapping("/opt")
    String opt(Model model) {
        return area(model, "OPT", "Gestisci le richieste e le offerte della tua organizzazione.");
    }

    @GetMapping("/operatore")
    String operatore(Model model) {
        return area(model, "Operatore", "Consulta e porta avanti le richieste che ti sono assegnate.");
    }

    private static String area(Model model, String ruolo, String descrizione) {
        model.addAttribute("ruolo", ruolo);
        model.addAttribute("descrizione", descrizione);
        return "utenze/area";
    }
}
