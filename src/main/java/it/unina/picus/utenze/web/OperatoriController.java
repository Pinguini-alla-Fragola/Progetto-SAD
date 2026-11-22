package it.unina.picus.utenze.web;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
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
import it.unina.picus.utenze.interno.dominio.CampoOperatore;

/** Riceve la richiesta con cui il Banco crea l'account personale di un Operatore. */
@RestController
@Profile("!skeleton")
@RequestMapping(value = "/api/utenze/operatori", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperatoriController {

    /**
     * Campi del modulo così come li invia il browser. Restano valori JSON generici fino al controllo del tipo:
     * convertiti subito, un booleano diventerebbe il testo "true" e un numero come {@code 1.5} verrebbe troncato.
     */
    public record NuovoOperatoreJson(Object nome, Object cognome, Object codiceFiscale, Object email,
            Object telefono, Object comuneCodice, Object tipologia, List<Object> misure, Object centroPicus) {

        ComandoCreaOperatore comando() {
            CampiJson campi = new CampiJson();
            ComandoCreaOperatore comando = new ComandoCreaOperatore(
                    campi.testo(nome, CampoOperatore.NOME),
                    campi.testo(cognome, CampoOperatore.COGNOME),
                    campi.testo(codiceFiscale, CampoOperatore.CODICE_FISCALE),
                    campi.testo(email, CampoOperatore.EMAIL),
                    campi.testo(telefono, CampoOperatore.TELEFONO),
                    campi.testo(comuneCodice, CampoOperatore.COMUNE),
                    campi.testo(tipologia, CampoOperatore.TIPOLOGIA),
                    campi.identificativi(misure, CampoOperatore.MISURE,
                            "Seleziona le misure tramite il loro identificativo numerico"),
                    campi.scelta(centroPicus, CampoOperatore.CENTRO_PICUS));
            campi.verifica();
            return comando;
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

    /**
     * Crea l'Operatore e risponde {@code 201}. Non indica un indirizzo della risorsa, perché in questa fase non
     * esiste ancora una pagina che mostri il singolo Operatore.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OperatoreCreatoJson> crea(IdentitaAutenticata attore,
            @RequestBody NuovoOperatoreJson corpo) {
        OperatoreCreato creato = creazione.crea(attore, corpo.comando());
        return ResponseEntity.status(HttpStatus.CREATED).body(OperatoreCreatoJson.da(creato));
    }
}
