package it.unina.picus.utenze.web;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService;
import it.unina.picus.utenze.interno.applicazione.TokenAttivazioneNonValidoException;

@Controller
class AttivazioneController {

    private final Optional<AttivazioneUtenzaService> attivazioneService;

    AttivazioneController(Optional<AttivazioneUtenzaService> attivazioneService) {
        this.attivazioneService = attivazioneService;
    }

    @GetMapping("/attiva")
    String mostraPaginaAttivazione(@RequestParam(name = "token", required = false) String token, Model modello) {
        boolean valido = token != null && attivazioneService
                .map(s -> s.isTokenValido(token))
                .orElse(false);

        modello.addAttribute("attivazione", true);
        modello.addAttribute("valido", valido);
        modello.addAttribute("token", token);
        modello.addAttribute("endpoint", "/api/auth/attiva");
        return "credenziali";
    }

    record RichiestaAttivazione(String token, String nuovaPassword) {}

    @PostMapping("/api/auth/attiva/conferma")
    @ResponseBody
    ResponseEntity<?> confermaAttivazione(@RequestBody RichiestaAttivazione body) {
        if (attivazioneService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("messaggio", "Funzionalità non disponibile nel profilo skeleton"));
        }

        try {
            attivazioneService.get().attivaAccount(body.token(), body.nuovaPassword());
            return ResponseEntity.ok(Map.of(
                    "messaggio", "Account attivato con successo. Ora puoi accedere da Accedi."
            ));
        } catch (TokenAttivazioneNonValidoException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("messaggio", ex.getMessage()));
        }
    }
}