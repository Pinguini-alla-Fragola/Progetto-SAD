package it.unina.picus.richieste.web;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.interno.applicazione.ServizioCoordinamentoRichieste;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Adattatore REST dei comandi del Banco, senza logica di presentazione. */
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CoordinamentoRichiesteController {
    private final ServizioCoordinamentoRichieste coordinamento;

    /**
     * Collega l’adattatore REST ai casi d’uso del Banco.
     *
     * @param coordinamento servizio di coordinamento e registrazione dell'esito
     */
    public CoordinamentoRichiesteController(ServizioCoordinamentoRichieste coordinamento) {
        this.coordinamento = coordinamento;
    }

    @PostMapping("/richieste/{id}/presa-in-carico")
    public RichiestaSintesi prendiInCarico(IdentitaAutenticata attore, @PathVariable UUID id,
            @RequestBody(required = false) CorpoJson corpo) {
        verificaCorpoVuoto(corpo);
        return coordinamento.prendiInCarico(attore, id);
    }

    @GetMapping("/richieste/{id}/candidati")
    public List<Candidato> candidati(IdentitaAutenticata attore, @PathVariable UUID id) {
        return coordinamento.cercaErogatoriOrdinati(attore, id);
    }

    @PostMapping(value = "/richieste/{id}/contatti", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ContattoDisponibilita> interpella(IdentitaAutenticata attore, @PathVariable UUID id,
            @RequestBody CorpoJson ingresso) {
        var corpo = ingresso.campi();
        if (corpo == null || !corpo.keySet().equals(Set.of("erogatoreId", "ambito"))) {
            throw nonValido("contatto", "Indica soltanto erogatoreId e ambito");
        }
        UUID erogatoreId;
        try {
            if (!(corpo.get("erogatoreId") instanceof String testo)) {
                throw new IllegalArgumentException();
            }
            erogatoreId = UUID.fromString(testo);
            if (!erogatoreId.toString().equalsIgnoreCase(testo)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw nonValido("erogatoreId", "Seleziona un account erogatore valido");
        }
        AmbitoErogazione ambito;
        try {
            if (!(corpo.get("ambito") instanceof String testo)) {
                throw new IllegalArgumentException();
            }
            ambito = AmbitoErogazione.valueOf(testo);
        } catch (IllegalArgumentException ex) {
            throw nonValido("ambito", "Seleziona un ambito di erogazione valido");
        }
        var contatto = coordinamento.interpellaErogatore(attore, id, erogatoreId, ambito);
        return ResponseEntity.created(URI.create("/api/richieste/" + id + "/contatti/" + contatto.id())).body(contatto);
    }

    @GetMapping("/richieste/{id}/contatti/{contattoId}")
    public ContattoDisponibilita consultaContatto(IdentitaAutenticata attore, @PathVariable UUID id,
            @PathVariable UUID contattoId) {
        return coordinamento.consultaContattoBanco(attore, id, contattoId);
    }

    @PostMapping("/contatti-disponibilita/{id}/assegnazione")
    public RichiestaSintesi assegna(IdentitaAutenticata attore, @PathVariable UUID id,
            @RequestBody(required = false) CorpoJson corpo) {
        verificaCorpoVuoto(corpo);
        return coordinamento.assegnaErogatore(attore, id);
    }

    /**
     * Registra l’accordo concordato dal Banco e restituisce il dettaglio aggiornato.
     *
     * @param attore identità verificata ricavata dalla sessione
     * @param id identificativo della richiesta
     * @param corpo proprietà JSON specifiche della modalità
     * @return dettaglio della richiesta Confermata
     * @see RichiesteApi#confermaAccordo
     */
    @PostMapping(value = "/richieste/{id}/conferma", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RichiestaDettaglio conferma(IdentitaAutenticata attore, @PathVariable UUID id, @RequestBody CorpoJson corpo) {
        return coordinamento.confermaAccordo(attore, id, AccordoJson.leggi(corpo.campi()));
    }

    /**
     * Registra il rifiuto definitivo per la richiesta indicata.
     *
     * @param attore identità verificata ricavata dalla sessione
     * @param id identificativo della richiesta
     * @param corpo corpo assente o oggetto JSON vuoto
     * @return sintesi della richiesta Rifiutata
     * @see RichiesteApi#rifiutaRichiesta
     */
    @PostMapping("/richieste/{id}/rifiuta")
    public RichiestaSintesi rifiuta(IdentitaAutenticata attore, @PathVariable UUID id,
            @RequestBody(required = false) CorpoJson corpo) {
        verificaCorpoVuoto(corpo);
        return coordinamento.rifiutaRichiesta(attore, id);
    }

    private static void verificaCorpoVuoto(CorpoJson corpo) {
        if (corpo != null && corpo.campi() != null && !corpo.campi().isEmpty()) {
            throw nonValido("comando", "Questo comando non prevede campi aggiuntivi");
        }
    }

    private static DatiRichiestaNonValidiException nonValido(String campo, String messaggio) {
        return new DatiRichiestaNonValidiException(Map.of(campo, messaggio));
    }
}
