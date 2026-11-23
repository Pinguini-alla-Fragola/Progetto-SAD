package it.unina.picus.catalogo.web;

import it.unina.picus.catalogo.api.CatalogoApi.OffertaOpt;
import it.unina.picus.catalogo.api.GestioneOffertaOptApi;
import it.unina.picus.catalogo.interno.applicazione.MisuraNonOffribileException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Adattatore REST per consultare, dichiarare e ritirare le offerte delle OPT. */
@RestController
@RequestMapping(value = "/api/catalogo/offerte-opt", produces = MediaType.APPLICATION_JSON_VALUE)
public class OffertaOptController {
    private static final String TRAMITE_PROFESSIONISTA = "tramiteProfessionista";
    private final GestioneOffertaOptApi offerta;
    private final ObjectReader lettoreOfferta;

    /**
     * Collega l'adattatore REST ai casi d'uso dell'offerta.
     *
     * @param offerta servizio applicativo delle offerte OPT
     * @param json configurazione JSON dell'applicazione
     */
    public OffertaOptController(GestioneOffertaOptApi offerta, JsonMapper json) {
        this.offerta = offerta;
        this.lettoreOfferta = json.reader().with(
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /**
     * Legge l'offerta corrente dell'OPT indicata.
     *
     * @param attore identità verificata ricavata dalla sessione
     * @param optId account dell'OPT da consultare
     * @return dichiarazioni correnti visibili al Banco o al titolare
     */
    @GetMapping("/{optId}")
    public List<OffertaOpt> elenco(IdentitaAutenticata attore, @PathVariable UUID optId) {
        return offerta.elencoOffertaOpt(attore, optId);
    }

    /**
     * Dichiara o aggiorna la misura offerta dall'OPT autenticata.
     *
     * @param attore identità verificata ricavata dalla sessione
     * @param misuraId identificativo della misura da offrire
     * @param ingresso corpo JSON con il solo campo booleano {@code tramiteProfessionista}
     * @return stato 204 dopo il salvataggio o 400 se il corpo non è valido
     */
    @PutMapping(value = "/misure/{misuraId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ErroreOfferta> registra(IdentitaAutenticata attore, @PathVariable long misuraId,
            InputStream ingresso) {
        JsonNode corpo;
        try {
            corpo = lettoreOfferta.readTree(ingresso);
        } catch (JacksonException ex) {
            return erroreFormatoOfferta();
        }
        if (corpo == null || !corpo.isObject() || corpo.size() != 1
                || !corpo.path(TRAMITE_PROFESSIONISTA).isBoolean()) {
            return erroreFormatoOfferta();
        }
        offerta.registraOffertaOpt(attore, misuraId, corpo.path(TRAMITE_PROFESSIONISTA).booleanValue());
        return ResponseEntity.noContent().build();
    }

    /**
     * Ritira la dichiarazione corrente della OPT autenticata.
     *
     * @param attore identità verificata ricavata dalla sessione
     * @param misuraId identificativo della misura da ritirare
     */
    @DeleteMapping("/misure/{misuraId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ritira(IdentitaAutenticata attore, @PathVariable long misuraId) {
        offerta.ritiraOffertaOpt(attore, misuraId);
    }

    /** Traduce il rifiuto di una misura sconosciuta o disattivata in un errore sul campo. */
    @ExceptionHandler(MisuraNonOffribileException.class)
    ResponseEntity<ErroreOfferta> misuraNonOffribile(MisuraNonOffribileException ex) {
        return errore(HttpStatus.BAD_REQUEST, "Misura non selezionabile", Map.of("misuraId", ex.getMessage()));
    }

    /** Segnala una OPT sconosciuta o una dichiarazione corrente assente. */
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErroreOfferta> nonTrovata(NoSuchElementException ex) {
        return errore(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    private static ResponseEntity<ErroreOfferta> erroreFormatoOfferta() {
        return errore(HttpStatus.BAD_REQUEST, "Controlla i dati dell'offerta",
                Map.of(TRAMITE_PROFESSIONISTA, "Indica soltanto tramiteProfessionista come valore booleano"));
    }

    private static ResponseEntity<ErroreOfferta> errore(HttpStatus stato, String messaggio, Map<String, String> campi) {
        return ResponseEntity.status(stato).body(new ErroreOfferta(stato.name(), messaggio, campi));
    }

    /**
     * Errore restituito da un'operazione sull'offerta.
     *
     * @param codice codice testuale dello stato HTTP
     * @param messaggio descrizione dell'errore
     * @param campi eventuali errori specifici dei campi
     */
    public record ErroreOfferta(String codice, String messaggio, Map<String, String> campi) {}
}
