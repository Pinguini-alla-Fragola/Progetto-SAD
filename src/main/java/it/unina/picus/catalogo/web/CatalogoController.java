package it.unina.picus.catalogo.web;

import it.unina.picus.catalogo.api.CatalogoApi.Comune;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.shared.AutenticazioneCorrente;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Dati selezionabili dal modulo di invio, senza logica di presentazione. */
@RestController
@RequestMapping(value = "/api/catalogo", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogoController {
    private final ConsultazioneCatalogoApi catalogo;
    private final AutenticazioneCorrente autenticazione;

    public CatalogoController(ConsultazioneCatalogoApi catalogo, AutenticazioneCorrente autenticazione) {
        this.catalogo = catalogo;
        this.autenticazione = autenticazione;
    }

    @GetMapping("/misure")
    public List<Misura> elencoMisureAttive(Authentication authentication) {
        autenticazione.richiedi(authentication);
        return catalogo.elencoMisureAttive();
    }

    @GetMapping("/comuni")
    public List<Comune> elencoComuni(Authentication authentication) {
        autenticazione.richiedi(authentication);
        return catalogo.elencoComuni();
    }
}
