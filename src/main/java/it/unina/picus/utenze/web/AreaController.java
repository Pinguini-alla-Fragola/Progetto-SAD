package it.unina.picus.utenze.web;

import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/** Adattatore MVC per il login e le aree riservate dei tre ruoli. */
@Controller
class AreaController {

    private final UtenzeApi utenze;
    private final Environment ambiente;

    AreaController(UtenzeApi utenze, Environment ambiente) {
        this.utenze = utenze;
        this.ambiente = ambiente;
    }

    @GetMapping("/login")
    String login(@AuthenticationPrincipal PrincipalePicus principale, Model modello) {
        if (principale != null) {
            return "redirect:/";
        }
        boolean skeleton = ambiente.acceptsProfiles(Profiles.of("skeleton"));
        modello.addAttribute("skeleton", skeleton);
        modello.addAttribute("demo", skeleton || ambiente.acceptsProfiles(Profiles.of("demo")));
        modello.addAttribute("emailOptDemo", skeleton ? "opt@picus.test" : "opt-a@picus.test");
        return "login";
    }

    /** Espone la vista delle credenziali mentre le azioni attendono il servizio dedicato. */
    @GetMapping("/{operazione:recupera|attiva}")
    String credenziali(@PathVariable String operazione, Model modello) {
        modello.addAttribute("attivazione", "attiva".equals(operazione));
        modello.addAttribute("valido", false);
        return "credenziali";
    }

    @GetMapping({"/", "/dashboard"})
    String home(IdentitaAutenticata identita) {
        return "redirect:/" + vistaArea(identita.ruolo());
    }

    @GetMapping({"/banco/dashboard", "/opt/dashboard", "/operatore/dashboard"})
    String dashboard(IdentitaAutenticata identita, Model modello) {
        var account = utenze.cercaAccount(identita.accountId())
                .orElseThrow(() -> new AccessDeniedException("Account non disponibile"));
        modello.addAttribute("utente", account);

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
