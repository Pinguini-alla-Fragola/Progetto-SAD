package it.unina.picus.catalogo.web;

import it.unina.picus.catalogo.api.CatalogoApi.Comune;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Dati selezionabili dal modulo di invio, senza logica di presentazione. */
@RestController
@RequestMapping(value = "/api/catalogo", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogoController {
    private final ConsultazioneCatalogoApi catalogo;
    public CatalogoController(ConsultazioneCatalogoApi catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/misure")
    public List<Misura> elencoMisureAttive(IdentitaAutenticata attore) {
        return catalogo.elencoMisureAttive();
    }

    @GetMapping("/comuni")
    public List<Comune> elencoComuni(IdentitaAutenticata attore) {
        return catalogo.elencoComuni();
    }
}
