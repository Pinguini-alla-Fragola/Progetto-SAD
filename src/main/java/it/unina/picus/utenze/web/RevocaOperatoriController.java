package it.unina.picus.utenze.web;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.utenze.api.OperazioneUtenzaNonConsentitaException;
import it.unina.picus.utenze.api.UtenzaNonTrovataException;
import it.unina.picus.utenze.api.UtenzeApi;

@RestController
@RequestMapping("/api/operatori")
class RevocaOperatoriController {

    private final UtenzeApi utenze;

    RevocaOperatoriController(UtenzeApi utenze) {
        this.utenze = utenze;
    }

    @PostMapping("/{id}/revoca")
    ResponseEntity<?> revocaOperatore(@PathVariable UUID id) {
        try {
            utenze.revocaUtenzaOperatore(id);
            return ResponseEntity.ok(Map.of(
                    "accountId", id,
                    "stato", UtenzeApi.StatoUtenza.REVOCATO.name(),
                    "messaggio", "Accesso dell'operatore revocato."
            ));
        } catch (UtenzaNonTrovataException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("messaggio", ex.getMessage()));
        } catch (OperazioneUtenzaNonConsentitaException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("messaggio", ex.getMessage()));
        }
    }
}
