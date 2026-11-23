package it.unina.picus.monitoraggio.web;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
import it.unina.picus.monitoraggio.interno.applicazione.MonitoraggioOptService;
import it.unina.picus.monitoraggio.interno.applicazione.MonitoraggioOptService.CategoriaVoce;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

@RestController
@RequestMapping("/api/monitoraggio")
class ConteggiOptController {

    private final ConteggiOptApi monitoraggio;
    private final Optional<MonitoraggioOptService> monitoraggioDettaglio;

    ConteggiOptController(ConteggiOptApi monitoraggio, Optional<MonitoraggioOptService> monitoraggioDettaglio) {
        this.monitoraggio = monitoraggio;
        this.monitoraggioDettaglio = monitoraggioDettaglio;
    }

    record RichiestaRegistrazioneConteggi(Integer anno, Integer trimestre, List<VoceInput> voci) {}
    record VoceInput(Long misuraId, String voce, Long valore) {}

    @GetMapping("/categorie")
    List<CategoriaVoce> categorieAttive() {
        return monitoraggioDettaglio
                .map(MonitoraggioOptService::elencoCategorieAttive)
                .orElseGet(() -> List.of(
                        new CategoriaVoce("PERSONE_RAGGIUNTE", "Persone raggiunte nel trimestre"),
                        new CategoriaVoce("MINORI", "Minori raggiunti"),
                        new CategoriaVoce("ANZIANI", "Anziani raggiunti"),
                        new CategoriaVoce("NUCLEI_FAMILIARI", "Nuclei familiari raggiunti")));
    }

    @PostMapping("/conteggi-opt")
    ResponseEntity<Void> registraConteggi(@RequestBody RichiestaRegistrazioneConteggi body, IdentitaAutenticata attore) {
        if (body == null || body.anno() == null || body.trimestre() == null || body.voci() == null) {
            throw new DatiConteggiNonValidiException(
                    "Compila tutti i campi obbligatori.",
                    java.util.Map.of("voci", "Anno, trimestre e voci sono obbligatori."));
        }
        Trimestre periodo = new Trimestre(body.anno(), body.trimestre());
        List<VoceMisura> voci = body.voci().stream()
                .map(v -> {
                    if (v == null || v.misuraId() == null || v.valore() == null) {
                        throw new DatiConteggiNonValidiException(
                                "Valori mancanti o non validi.",
                                java.util.Map.of("voci", "Ogni riga deve avere misura, voce e un numero intero >= 0."));
                    }
                    return new VoceMisura(v.misuraId(), v.voce(), v.valore());
                })
                .toList();

        monitoraggio.registraConteggiOpt(attore, periodo, voci);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/conteggi-opt")
    List<ConteggioOpt> consultaConteggi(
            @RequestParam(required = false) UUID optId,
            @RequestParam int anno,
            @RequestParam int trimestre,
            @RequestParam(required = false) Integer annoFine,
            @RequestParam(required = false) Integer trimestreFine,
            IdentitaAutenticata attore) {

        Trimestre da = new Trimestre(anno, trimestre);
        Trimestre a = (annoFine != null && trimestreFine != null)
                ? new Trimestre(annoFine, trimestreFine)
                : da;

        return monitoraggio.consultaConteggiOpt(attore, optId, da, a);
    }
}