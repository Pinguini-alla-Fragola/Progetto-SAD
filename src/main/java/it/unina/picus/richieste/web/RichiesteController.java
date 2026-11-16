package it.unina.picus.richieste.web;

import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.shared.AutenticazioneCorrente;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Endpoint backend per invio e consultazione; non contiene viste o comandi di coordinamento. */
@RestController
@RequestMapping(value = "/api/richieste", produces = MediaType.APPLICATION_JSON_VALUE)
public class RichiesteController {
    private final InvioRichiesteApi richieste;
    private final AutenticazioneCorrente autenticazione;

    public RichiesteController(InvioRichiesteApi richieste, AutenticazioneCorrente autenticazione) {
        this.richieste = richieste;
        this.autenticazione = autenticazione;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RichiestaSintesi> invia(
            Authentication authentication, @RequestBody Map<String, Object> corpo,
            @RequestHeader(value = "Idempotency-Key", required = false) String riferimento) {
        var attore = autenticazione.richiedi(authentication);
        if (attore.ruolo() != RuoloUtente.OPT) {
            throw new SecurityException("Invio riservato alle OPT");
        }
        var risultato = richieste.invia(attore, InvioRichiestaJson.leggi(corpo), InvioRichiestaJson.riferimento(riferimento));
        return ResponseEntity.created(URI.create("/api/richieste/" + risultato.id())).body(risultato);
    }

    @GetMapping("/mie")
    public List<RichiestaSintesi> elencoRichiesteOpt(Authentication authentication) {
        return richieste.elencoRichiesteOpt(autenticazione.richiedi(authentication));
    }

    @GetMapping("/coda")
    public List<RichiestaSintesi> codaBanco(Authentication authentication) {
        return richieste.codaBanco(autenticazione.richiedi(authentication));
    }

    @GetMapping
    public List<RichiestaSintesi> elenco(Authentication authentication) {
        var attore = autenticazione.richiedi(authentication);
        return attore.ruolo() == RuoloUtente.BANCO ? richieste.codaBanco(attore) : richieste.elencoRichiesteOpt(attore);
    }

    @GetMapping("/{id}")
    public RichiestaDettaglio consultaRichiesta(Authentication authentication, @PathVariable UUID id) {
        return richieste.consultaRichiesta(autenticazione.richiedi(authentication), id);
    }

    /** Espone il token della protezione CSRF già attiva per gli invii con sessione. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(Authentication authentication, CsrfToken token) {
        autenticazione.richiedi(authentication);
        return Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName(), "token", token.getToken());
    }
}
