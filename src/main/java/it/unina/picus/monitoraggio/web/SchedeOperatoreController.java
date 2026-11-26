package it.unina.picus.monitoraggio.web;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.applicazione.ServizioSchedeOperatore;
import it.unina.picus.monitoraggio.interno.applicazione.ServizioSchedeOperatore.ConfigurazioneModulo;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestreAssenteException;
import it.unina.picus.monitoraggio.interno.dominio.DatiSchedaNonValidiException;
import it.unina.picus.monitoraggio.interno.dominio.RegoleAccessoScheda;
import it.unina.picus.monitoraggio.interno.dominio.SchedaGiaInviataException;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoSchedaOperatore;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoSchedaOperatore.Campo;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/** API HTTP del primo invio e della consultazione della scheda personale TM-OP-01. */
@RestController
@Profile("!skeleton")
@RequestMapping(value = SchedeOperatoreController.PERCORSO, produces = MediaType.APPLICATION_JSON_VALUE)
class SchedeOperatoreController {

    static final String PERCORSO = "/api/monitoraggio/schede-operatore";
    private final ServizioSchedeOperatore schede;
    private final LettoreSchedaJson lettore;

    /** Collega gli endpoint ai casi d'uso e al lettore del tracciato JSON. */
    SchedeOperatoreController(ServizioSchedeOperatore schede, LettoreSchedaJson lettore) {
        this.schede = schede;
        this.lettore = lettore;
    }

    /** Restituisce all'operatore i campi del modulo e la configurazione di categorie per il periodo scelto. */
    @GetMapping("/modulo")
    Modulo modulo(IdentitaAutenticata attore, @RequestParam(required = false) Integer anno,
                  @RequestParam(required = false) Integer trimestre) {
        RegoleAccessoScheda.verificaOperatore(attore);
        Trimestre scelto = periodo(anno, trimestre);
        ConfigurazioneModulo configurazione = schede.moduloPer(attore, scelto);
        return new Modulo(scelto, TracciatoSchedaOperatore.CODICE,
                TracciatoSchedaOperatore.VERSIONE, TracciatoSchedaOperatore.campi(), configurazione);
    }

    /** Invia la scheda e conferma con HTTP 201, riferimento alla risorsa e periodo registrato. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Ricevuta> invia(IdentitaAutenticata attore, @RequestParam(required = false) Integer anno,
                                   @RequestParam(required = false) Integer trimestre, InputStream corpo)
            throws IOException {
        RegoleAccessoScheda.verificaOperatore(attore);
        Trimestre periodo = periodo(anno, trimestre);
        UUID id = schede.inviaSchedaOperatore(attore, periodo, lettore.leggi(corpo));
        return ResponseEntity.created(URI.create(PERCORSO + "/" + id)).body(new Ricevuta(id, periodo));
    }

    /** Elenca le schede personali inviate dall'operatore autenticato. */
    @GetMapping("/mie")
    List<SchedaSintesi> mie(IdentitaAutenticata attore) {
        return schede.elencoSchedeOperatore(attore);
    }

    /** Elenca per il Banco le schede ricevute nel periodo selezionato. */
    @GetMapping("/ricevute")
    List<SchedaSintesi> ricevute(IdentitaAutenticata attore, @RequestParam(required = false) Integer anno,
                                @RequestParam(required = false) Integer trimestre) {
        return schede.elencoSchedeRicevute(attore, periodo(anno, trimestre));
    }

    /** Riepiloga per il Banco le schede ricevute e i conteggi dichiarati per categoria. */
    @GetMapping("/ricevute/riepilogo")
    RiepilogoRicevute riepilogo(IdentitaAutenticata attore, @RequestParam(required = false) Integer anno,
                              @RequestParam(required = false) Integer trimestre) {
        var riepilogo = schede.riepilogaSchede(attore, periodo(anno, trimestre));
        var totali = riepilogo.conteggi().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, voce -> voce.getValue().toString()));
        return new RiepilogoRicevute(riepilogo.schede(), totali);
    }

    /** Restituisce la scheda al titolare o al Banco autorizzato. */
    @GetMapping("/{id}")
    SchedaOperatore dettaglio(IdentitaAutenticata attore, @PathVariable UUID id) {
        return schede.consultaSchedaOperatore(attore, id);
    }

    /** Traduce i parametri di periodo in un riferimento valido, segnalando i campi da correggere. */
    private static Trimestre periodo(Integer anno, Integer trimestre) {
        Map<String, String> errori = new LinkedHashMap<>();
        if (anno == null || anno < 1) {
            errori.put("anno", "Indica un anno positivo");
        }
        if (trimestre == null || trimestre < 1 || trimestre > 4) {
            errori.put("trimestre", "Indica un trimestre da 1 a 4");
        }
        if (!errori.isEmpty()) {
            throw new DatiSchedaNonValidiException(errori);
        }
        return new Trimestre(anno, trimestre);
    }

    /** Espone i soli messaggi controllati della validazione. */
    @ExceptionHandler(DatiSchedaNonValidiException.class)
    ResponseEntity<Errore> datiNonValidi(DatiSchedaNonValidiException ex) {
        return errore(HttpStatus.BAD_REQUEST, "Controlla i campi della scheda", ex.campi());
    }

    /** Segnala un parametro di URL malformato senza restituirne il valore. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Errore> parametroNonValido(MethodArgumentTypeMismatchException ex) {
        return errore(HttpStatus.BAD_REQUEST, "Controlla i parametri",
                Map.of(ex.getName(), "Formato non valido"));
    }

    /** Segnala un invio già registrato nel periodo, senza modificare la scheda salvata. */
    @ExceptionHandler(SchedaGiaInviataException.class)
    ResponseEntity<Errore> giaInviata() {
        return errore(HttpStatus.CONFLICT, "Esiste già una scheda inviata per questo trimestre", Map.of());
    }

    /** Segnala che il Banco non ha ancora pubblicato la configurazione di categorie per il trimestre. */
    @ExceptionHandler(ConfigurazioneTrimestreAssenteException.class)
    ResponseEntity<Errore> configurazioneAssente(ConfigurazioneTrimestreAssenteException ex) {
        return errore(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    /** Segnala l'assenza della scheda richiesta. */
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Errore> nonTrovata() {
        return errore(HttpStatus.NOT_FOUND, "Scheda non trovata", Map.of());
    }

    private static ResponseEntity<Errore> errore(HttpStatus stato, String messaggio, Map<String, String> campi) {
        return ResponseEntity.status(stato).body(new Errore(stato.name(), messaggio, campi));
    }

    /** Metadati del tracciato e della configurazione di categorie applicabile per il periodo selezionato. */
    record Modulo(
            Trimestre trimestre,
            String tracciato,
            String versione,
            List<Campo> campi,
            ConfigurazioneModulo configurazioneCategorie) {}

    /** Conferma del primo invio accettato. */
    record Ricevuta(UUID id, Trimestre trimestre) {}

    /** Totali in stringhe decimali per preservarne la precisione anche nei client JavaScript. */
    record RiepilogoRicevute(List<SchedaSintesi> schede, Map<String, String> conteggi) {}

    /** Errore HTTP senza valori dichiarati dal compilatore. */
    record Errore(String codice, String messaggio, Map<String, String> campi) {}
}
