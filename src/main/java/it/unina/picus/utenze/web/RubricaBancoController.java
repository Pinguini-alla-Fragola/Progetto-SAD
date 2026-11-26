package it.unina.picus.utenze.web;

import java.io.Serial;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.AccountAmministrativo;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.interno.dominio.RegoleAccessoUtenze;

/**
 * Espone al Banco l'elenco aggiornato di OPT o Operatori senza ricaricare la pagina,
 * omettendo recapiti email e altri dati personali superflui per le consultazioni.
 */
@RestController
@Profile("!skeleton")
@RequestMapping(value = "/api/utenze/rubrica", produces = MediaType.APPLICATION_JSON_VALUE)
class RubricaBancoController {

    private static final String MESSAGGIO_RUOLO = "Indica un ruolo valido (OPT o OPERATORE)";

    private final UtenzeApi utenze;

    RubricaBancoController(UtenzeApi utenze) {
        this.utenze = utenze;
    }

    /**
     * Restituisce gli account del ruolo richiesto ({@code OPT} o
     * {@code OPERATORE}) in qualsiasi stato, inclusi gli account revocati o in attesa.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param ruolo ruolo da consultare ({@code OPT} o {@code OPERATORE})
     * @return voci di rubrica prive di recapiti email
     */
    @GetMapping
    public List<VoceRubricaJson> elenca(IdentitaAutenticata attore, @RequestParam String ruolo) {
        RegoleAccessoUtenze.verificaBanco(attore);
        RuoloUtente target = switch (ruolo.strip().toUpperCase(Locale.ROOT)) {
            case "OPT" -> RuoloUtente.OPT;
            case "OPERATORE" -> RuoloUtente.OPERATORE;
            default -> throw new RuoloRubricaNonValidoException();
        };
        return utenze.elencoAccountPerRuolo(target).stream()
                .map(VoceRubricaJson::da)
                .toList();
    }

    @ExceptionHandler(RuoloRubricaNonValidoException.class)
    ResponseEntity<Map<String, Object>> ruoloNonValido() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "codice", HttpStatus.BAD_REQUEST.name(),
                        "messaggio", MESSAGGIO_RUOLO,
                        "campi", Map.of("ruolo", MESSAGGIO_RUOLO)));
    }

    private static final class RuoloRubricaNonValidoException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * Voce minima di rubrica per identificare OPT o Operatori e distinguere gli omonimi.
     *
     * @param id identificativo stabile dell'account
     * @param nome denominazione visualizzata
     * @param riferimento codice struttura per OPT o tipologia per Operatore
     * @param stato stato corrente dell'account
     */
    record VoceRubricaJson(UUID id, String nome, String riferimento, String stato) {
        static VoceRubricaJson da(AccountAmministrativo account) {
            return new VoceRubricaJson(account.id(), account.nomeVisualizzato(), account.riferimento(),
                    account.stato().name());
        }
    }
}
