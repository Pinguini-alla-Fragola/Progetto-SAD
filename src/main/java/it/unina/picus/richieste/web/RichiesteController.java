package it.unina.picus.richieste.web;

import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Endpoint backend per invio e consultazione; non contiene viste o comandi di coordinamento. */
@RestController
@RequestMapping(value = "/api/richieste", produces = MediaType.APPLICATION_JSON_VALUE)
public class RichiesteController {
    private final InvioRichiesteApi richieste;
    public RichiesteController(InvioRichiesteApi richieste) {
        this.richieste = richieste;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RichiestaSintesi> invia(
            IdentitaAutenticata attore, @RequestBody CorpoJson corpo,
            @RequestHeader(value = "Idempotency-Key", required = false) String riferimento) {
        var risultato = richieste.invia(attore, InvioRichiestaJson.leggi(corpo.campi()), InvioRichiestaJson.riferimento(riferimento));
        return ResponseEntity.created(URI.create("/api/richieste/" + risultato.id())).body(risultato);
    }

    @GetMapping("/mie")
    public List<RichiestaSintesi> elencoRichiesteOpt(IdentitaAutenticata attore) {
        return richieste.elencoRichiesteOpt(attore);
    }

    @GetMapping("/coda")
    public List<RichiestaSintesi> codaBanco(IdentitaAutenticata attore) {
        return richieste.codaBanco(attore);
    }

    @GetMapping
    public List<RichiestaSintesi> elenco(IdentitaAutenticata attore) {
        return attore.ruolo() == RuoloUtente.BANCO ? richieste.codaBanco(attore) : richieste.elencoRichiesteOpt(attore);
    }

    @GetMapping("/{id}")
    public RichiestaDettaglio consultaRichiesta(IdentitaAutenticata attore, @PathVariable UUID id) {
        return richieste.consultaRichiesta(attore, id);
    }
}
