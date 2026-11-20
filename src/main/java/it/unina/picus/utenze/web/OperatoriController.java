package it.unina.picus.utenze.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.interno.applicazione.ComandoCreaOperatore;
import it.unina.picus.utenze.interno.applicazione.CreazioneOperatori;
import it.unina.picus.utenze.interno.applicazione.OperatoreCreato;

/** Riceve la richiesta con cui il Banco crea l'account personale di un Operatore. */
@RestController
@Profile("!skeleton")
@RequestMapping(value = "/api/utenze/operatori", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperatoriController {

    /** Campi del modulo così come li invia il browser. */
    public record NuovoOperatoreJson(String nome, String cognome, String codiceFiscale, String email,
            String telefono, String comuneCodice, String tipologia, List<Long> misure, boolean centroPicus) {

        ComandoCreaOperatore comando() {
            return new ComandoCreaOperatore(nome, cognome, codiceFiscale, email, telefono, comuneCodice,
                    tipologia, misure, centroPicus);
        }
    }

    /** Ciò che il browser riceve dopo la creazione. */
    public record OperatoreCreatoJson(UUID id, String nomeVisualizzato, String email, String stato) {

        static OperatoreCreatoJson da(OperatoreCreato creato) {
            return new OperatoreCreatoJson(creato.id(), creato.nomeVisualizzato(), creato.email(),
                    StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name());
        }
    }

    private final CreazioneOperatori creazione;

    public OperatoriController(CreazioneOperatori creazione) {
        this.creazione = creazione;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OperatoreCreatoJson> crea(IdentitaAutenticata attore,
            @RequestBody NuovoOperatoreJson corpo) {
        OperatoreCreato creato = creazione.crea(attore, corpo.comando());
        return ResponseEntity.created(URI.create("/api/utenze/operatori/" + creato.id()))
                .body(OperatoreCreatoJson.da(creato));
    }
}
