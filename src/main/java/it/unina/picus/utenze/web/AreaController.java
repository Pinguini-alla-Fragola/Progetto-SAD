package it.unina.picus.utenze.web;

import java.util.Locale;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/** Adattatore MVC per il login e le aree riservate dei tre ruoli. */
@Controller
class AreaController {

    private final UtenzeApi utenze;

    AreaController(UtenzeApi utenze) {
        this.utenze = utenze;
    }

    @GetMapping("/login")
    String login(@AuthenticationPrincipal PrincipalePicus principale) {
        return principale == null ? "login" : "redirect:/";
    }

    @GetMapping({"/", "/dashboard"})
    String home(IdentitaAutenticata identita) {
        return "redirect:/" + vistaArea(identita.ruolo());
    }

    @GetMapping({"/banco/dashboard", "/opt/dashboard", "/operatore/dashboard"})
    String dashboard(IdentitaAutenticata identita, Model modello) {
        var account = utenze.cercaAccount(identita.accountId())
                .orElseThrow(() -> new AccessDeniedException("Account non disponibile"));
        modello.addAttribute("nomeAccount", account.nomeVisualizzato());

        if (identita.ruolo() == RuoloUtente.BANCO) {
            modello.addAttribute("optAttive", utenze.elencoAccountAttivi(RuoloUtente.OPT));
            modello.addAttribute("operatoriAttivi", utenze.elencoAccountAttivi(RuoloUtente.OPERATORE));
        }
        return vistaArea(identita.ruolo());
    }

    private static String vistaArea(RuoloUtente ruolo) {
        return ruolo.name().toLowerCase(Locale.ROOT) + "/dashboard";
    }
}
