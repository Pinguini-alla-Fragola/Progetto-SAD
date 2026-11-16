package it.unina.picus.utenze.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/** Reindirizza l'account autenticato all'area riservata al suo ruolo. */
@Controller
class AreaController {

    /** Sceglie il percorso di destinazione a partire dall'identità autenticata. */
    @GetMapping({"/", "/dashboard"})
    String home(IdentitaAutenticata identita) {
        return "redirect:" + switch (identita.ruolo()) {
            case BANCO -> "/banco/dashboard";
            case OPT -> "/opt/dashboard";
            case OPERATORE -> "/operatore/dashboard";
        };
    }
}
