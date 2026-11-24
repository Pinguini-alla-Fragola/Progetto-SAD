package it.unina.picus.monitoraggio.web;

import java.math.BigInteger;
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
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.applicazione.ConsultazioneConteggiOpt;
import it.unina.picus.monitoraggio.interno.applicazione.ConsultazioneConteggiOpt.ConteggioEsatto;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/** Endpoint backend per registrazione e consultazione dei conteggi trimestrali delle OPT. */
@RestController
@RequestMapping(value = "/api/monitoraggio", produces = MediaType.APPLICATION_JSON_VALUE)
class ConteggiOptController {

    private static final BigInteger MASSIMO_INTERO_SICURO_JS =
            BigInteger.valueOf(RegistrazioneConteggiJson.MASSIMO_INTERO_SICURO_JS);

    private final ConteggiOptApi monitoraggio;
    private final ConsultazioneConteggiOpt consultazione;

    ConteggiOptController(ConteggiOptApi monitoraggio, ConsultazioneConteggiOpt consultazione) {
        this.monitoraggio = monitoraggio;
        this.consultazione = consultazione;
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

    /** Consulta il trimestre iniziale o l'intervallo inclusivo quando sono indicati entrambi i campi finali. */
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

        return consultazione.consultaConteggiEsatti(attore, optId, da, a).stream()
                .map(ConteggioJson::da)
                .toList();
    }

    record ConteggioJson(UUID optId, VoceJson conteggio) {
        /** Mantiene i numeri JSON esatti in JavaScript e trasmette i totali più grandi come testo decimale. */
        private static ConteggioJson da(ConteggioEsatto risultato) {
            BigInteger valore = risultato.valore();
            Object valoreJson = valore.compareTo(MASSIMO_INTERO_SICURO_JS) > 0
                    ? valore.toString() : valore.longValueExact();
            return new ConteggioJson(risultato.optId(), new VoceJson(risultato.misuraId(), risultato.voce(), valoreJson));
        }
    }

    record VoceJson(long misuraId, String voce, Object valore) {}
}
