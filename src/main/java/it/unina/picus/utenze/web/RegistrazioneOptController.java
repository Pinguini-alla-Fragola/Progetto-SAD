package it.unina.picus.utenze.web;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.utenze.interno.applicazione.DatiOptNonValidiException;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.ComandoRegistrazioneOpt;

@RestController
@RequestMapping("/api/opt")
class RegistrazioneOptController {

    private final Optional<RegistrazioneOptService> registrazioneService;

    RegistrazioneOptController(Optional<RegistrazioneOptService> registrazioneService) {
        this.registrazioneService = registrazioneService;
    }

    record RichiestaCreazioneOpt(
            String codiceStruttura,
            String nome,
            String comune,
            String comuneCodice,
            String codiceFiscale,
            String email
    ) {
        String comuneRisolto() {
            return comune != null && !comune.isBlank() ? comune : comuneCodice;
        }
    }

    @PostMapping
    ResponseEntity<?> creaOpt(@RequestBody RichiestaCreazioneOpt body) {
        if (registrazioneService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("messaggio", "Funzionalità non disponibile nel profilo skeleton"));
        }

        try {
            UUID id = registrazioneService.get().registraOpt(new ComandoRegistrazioneOpt(
                    body.codiceStruttura(),
                    body.nome(),
                    body.comuneRisolto(),
                    body.codiceFiscale(),
                    body.email()
            ));
            return ResponseEntity.created(URI.create("/api/opt/" + id))
                    .body(Map.of(
                            "accountId", id,
                            "messaggio", "Organizzazione partner registrata. Email di attivazione inviata."
                    ));

        } catch (DatiOptNonValidiException ex) {
            String campoForm = "comuneCodice".equals(ex.getCampo()) ? "comune" : ex.getCampo();
            return ResponseEntity.badRequest().body(Map.of(
                    "messaggio", ex.getMessage(),
                    "campo", campoForm,
                    "errore", ex.getMessage(),
                    "campi", Map.of(campoForm, ex.getMessage())
            ));
        }
    }
}