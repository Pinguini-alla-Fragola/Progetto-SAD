package it.unina.picus.monitoraggio.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.ConteggiOptApi.CategoriaVoce;
import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/** Endpoint backend per registrazione e consultazione dei conteggi trimestrali delle OPT. */
@RestController
@RequestMapping(value = "/api/monitoraggio", produces = MediaType.APPLICATION_JSON_VALUE)
class ConteggiOptController {

    private final ConteggiOptApi monitoraggio;

    ConteggiOptController(ConteggiOptApi monitoraggio) {
        this.monitoraggio = monitoraggio;
    }

    @GetMapping("/categorie")
    List<CategoriaVoce> categorieAttive() {
        return monitoraggio.elencoCategorieAttive();
    }

    @PostMapping(value = "/conteggi-opt", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> registraConteggi(@RequestBody Map<String, Object> corpo, IdentitaAutenticata attore) {
        var richiesta = RegistrazioneConteggiJson.leggi(corpo);
        monitoraggio.registraConteggiOpt(attore, richiesta.trimestre(), richiesta.voci());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/conteggi-opt")
    List<ConteggioJson> consultaConteggi(
            @RequestParam(required = false) UUID optId,
            @RequestParam int anno,
            @RequestParam int trimestre,
            @RequestParam(required = false) Integer annoFine,
            @RequestParam(required = false) Integer trimestreFine,
            IdentitaAutenticata attore) {

        if ((annoFine == null) != (trimestreFine == null)) {
            throw new DatiConteggiNonValidiException("Indica sia l'anno sia il trimestre finale.",
                    annoFine == null ? Map.of("annoFine", "Indica l'anno finale.")
                            : Map.of("trimestreFine", "Indica il trimestre finale."));
        }
        Trimestre da = new Trimestre(anno, trimestre);
        Trimestre a = annoFine == null ? da : new Trimestre(annoFine, trimestreFine);

        return monitoraggio.consultaConteggiOpt(attore, optId, da, a).stream()
                .map(ConteggioJson::da)
                .toList();
    }

    record ConteggioJson(UUID optId, VoceJson conteggio) {
        private static ConteggioJson da(ConteggioOpt risultato) {
            var voce = risultato.conteggio();
            long valore = voce.valore();
            // Conserva i numeri JSON già esatti e trasmette gli interi più grandi come testo decimale.
            Object valoreJson = valore > RegistrazioneConteggiJson.MASSIMO_INTERO_SICURO_JS
                    ? Long.toString(valore) : valore;
            return new ConteggioJson(risultato.optId(), new VoceJson(voce.misuraId(), voce.voce(), valoreJson));
        }
    }

    record VoceJson(long misuraId, String voce, Object valore) {}
}