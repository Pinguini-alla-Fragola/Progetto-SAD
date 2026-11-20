package it.unina.picus.utenze.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import it.unina.picus.utenze.interno.applicazione.DatiOperatoreNonValidiException;
import it.unina.picus.utenze.interno.applicazione.OperatoreCreato;
import it.unina.picus.utenze.interno.dominio.CampoOperatore;

/** Riceve la richiesta con cui il Banco crea l'account personale di un Operatore. */
@RestController
@Profile("!skeleton")
@RequestMapping(value = "/api/utenze/operatori", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperatoriController {

    /**
     * Campi del modulo così come li invia il browser. Le misure restano valori JSON generici fino al controllo:
     * convertite subito in {@code Long}, un numero come {@code 1.5} verrebbe troncato in silenzio.
     */
    public record NuovoOperatoreJson(String nome, String cognome, String codiceFiscale, String email,
            String telefono, String comuneCodice, String tipologia, List<Object> misure, boolean centroPicus) {

        ComandoCreaOperatore comando() {
            return new ComandoCreaOperatore(nome, cognome, codiceFiscale, email, telefono, comuneCodice,
                    tipologia, identificativiMisure(misure), centroPicus);
        }

        /** Accetta soltanto numeri interi JSON: un numero con decimali, un testo o un valore vuoto vengono rifiutati. */
        private static List<Long> identificativiMisure(List<Object> valori) {
            if (valori == null) {
                return List.of();
            }
            List<Long> identificativi = new ArrayList<>();
            for (Object valore : valori) {
                if (!(valore instanceof Integer || valore instanceof Long)) {
                    throw new DatiOperatoreNonValidiException(Map.of(CampoOperatore.MISURE,
                            "Seleziona le misure tramite il loro identificativo numerico"));
                }
                identificativi.add(((Number) valore).longValue());
            }
            return identificativi;
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
