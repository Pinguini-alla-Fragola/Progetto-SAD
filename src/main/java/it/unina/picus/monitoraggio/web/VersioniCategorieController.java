package it.unina.picus.monitoraggio.web;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.applicazione.ServizioVersioniCategorie;
import it.unina.picus.monitoraggio.interno.applicazione.ServizioVersioniCategorie.VoceDraft;
import it.unina.picus.monitoraggio.interno.dominio.AssegnazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestreGiaPubblicataException;
import it.unina.picus.monitoraggio.interno.dominio.DatiSchedaNonValidiException;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoMonitoraggio;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorieImmutabileException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API HTTP riservata al Banco per predisporre bozze, validare e pubblicare versioni immutabili
 * delle categorie per trimestre su {@code TM-OP-01} e {@code TM-OPT-01} (#41, #181).
 */
@RestController
@Profile("!skeleton")
@RequestMapping(value = VersioniCategorieController.PERCORSO, produces = MediaType.APPLICATION_JSON_VALUE)
class VersioniCategorieController {

    static final String PERCORSO = "/api/monitoraggio/categorie-tracciati";

    private final ServizioVersioniCategorie servizio;

    VersioniCategorieController(ServizioVersioniCategorie servizio) {
        this.servizio = servizio;
    }

    /** Elenca le versioni (bozze, pubblicate o legacy) del tracciato indicato. */
    @GetMapping("/versioni")
    List<VersioneCategorie> elencaVersioni(
            IdentitaAutenticata attore,
            @RequestParam(defaultValue = "TM-OP-01") String tracciato) {
        return servizio.elencaVersioni(attore, TracciatoMonitoraggio.daCodice(tracciato));
    }

    /** Restituisce il dettaglio di una versione delle categorie. */
    @GetMapping("/versioni/{id}")
    VersioneCategorie dettaglioVersione(IdentitaAutenticata attore, @PathVariable UUID id) {
        return servizio.dettaglioVersione(attore, id);
    }

    /** Crea una nuova bozza di categorie per il tracciato scelto. */
    @PostMapping(value = "/versioni", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<VersioneCategorie> creaBozza(
            IdentitaAutenticata attore,
            @RequestBody CreaBozzaRichiesta corpo) {
        TracciatoMonitoraggio tracciato = TracciatoMonitoraggio.daCodice(corpo.tracciato());
        VersioneCategorie creata = servizio.creaBozza(
                attore, tracciato, corpo.codiceVersione(), corpo.descrizione(), corpo.voci());
        return ResponseEntity.created(URI.create(PERCORSO + "/versioni/" + creata.id())).body(creata);
    }

    /** Aggiorna una versione ancora in stato di bozza; rifiuta versioni pubblicate o legacy. */
    @PutMapping(value = "/versioni/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    VersioneCategorie aggiornaBozza(
            IdentitaAutenticata attore,
            @PathVariable UUID id,
            @RequestBody AggiornaBozzaRichiesta corpo) {
        return servizio.aggiornaBozza(attore, id, corpo.descrizione(), corpo.voci());
    }

    /** Pubblica e associa in modo immutabile la versione al trimestre indicato. */
    @PostMapping(value = "/versioni/{id}/pubblicazione", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AssegnazioneTrimestre> pubblica(
            IdentitaAutenticata attore,
            @PathVariable UUID id,
            @RequestBody PubblicazioneRichiesta corpo) {
        ConfigurazioneTrimestre.Pubblicata pubblicata = servizio.pubblicaPerTrimestre(
                attore, id, new Trimestre(corpo.anno(), corpo.trimestre()));
        AssegnazioneTrimestre risposta = new AssegnazioneTrimestre(
                pubblicata.versione().tracciato(),
                pubblicata.trimestre(),
                pubblicata.versione().id(),
                pubblicata.versione().codiceVersione(),
                pubblicata.versione().stato(),
                pubblicata.assegnataIl());
        return ResponseEntity.status(HttpStatus.CREATED).body(risposta);
    }

    /** Elenca le configurazioni assegnate ai trimestri per il tracciato indicato. */
    @GetMapping("/trimestri")
    List<AssegnazioneTrimestre> elencaAssegnazioni(
            IdentitaAutenticata attore,
            @RequestParam(defaultValue = "TM-OP-01") String tracciato) {
        return servizio.elencaAssegnazioni(attore, TracciatoMonitoraggio.daCodice(tracciato));
    }

    /** Legge la configurazione risolta per il singolo trimestre e tracciato. */
    @GetMapping("/trimestri/{anno}/{trimestre}")
    ConfigurazioneTrimestreJson configurazioneTrimestre(
            IdentitaAutenticata attore,
            @PathVariable int anno,
            @PathVariable int trimestre,
            @RequestParam(defaultValue = "TM-OP-01") String tracciato) {
        ConfigurazioneTrimestre conf = servizio.configurazioneTrimestre(
                attore, TracciatoMonitoraggio.daCodice(tracciato), new Trimestre(anno, trimestre));
        return switch (conf) {
            case ConfigurazioneTrimestre.Pubblicata p -> new ConfigurazioneTrimestreJson(
                    p.trimestre(), "PUBBLICATA", p.versione(), p.assegnataIl());
            case ConfigurazioneTrimestre.LegacyProvvisoria l -> new ConfigurazioneTrimestreJson(
                    l.trimestre(), "LEGACY", l.versione(), l.assegnataIl());
            case ConfigurazioneTrimestre.NonConfigurata n -> new ConfigurazioneTrimestreJson(
                    n.trimestre(), "NON_CONFIGURATA", null, null);
        };
    }

    @ExceptionHandler(DatiSchedaNonValidiException.class)
    ResponseEntity<ErroriMonitoraggio.Errore> datiNonValidi(DatiSchedaNonValidiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroriMonitoraggio.Errore("BAD_REQUEST", "Controlla i dati della configurazione", ex.campi()));
    }

    @ExceptionHandler({ConfigurazioneTrimestreGiaPubblicataException.class, VersioneCategorieImmutabileException.class})
    ResponseEntity<ErroriMonitoraggio.Errore> conflittoConfigurazione(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErroriMonitoraggio.Errore("CONFLICT", ex.getMessage(), Map.of()));
    }

    record CreaBozzaRichiesta(String tracciato, String codiceVersione, String descrizione, List<VoceDraft> voci) {}

    record AggiornaBozzaRichiesta(String descrizione, List<VoceDraft> voci) {}

    record PubblicazioneRichiesta(int anno, int trimestre) {}

    record ConfigurazioneTrimestreJson(
            Trimestre trimestre,
            String statoConfigurazione,
            VersioneCategorie versione,
            Instant assegnataIl) {}
}
