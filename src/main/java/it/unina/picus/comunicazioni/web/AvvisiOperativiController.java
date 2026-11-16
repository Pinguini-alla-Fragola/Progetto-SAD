package it.unina.picus.comunicazioni.web;

import it.unina.picus.comunicazioni.api.AvvisiOperativiApi;
import it.unina.picus.comunicazioni.api.ComunicazioniApi.AvvisoOperativo;
import it.unina.picus.shared.AutenticazioneCorrente;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Casella degli avvisi del solo account corrente. */
@RestController
public class AvvisiOperativiController {
    private final AvvisiOperativiApi avvisi;
    private final AutenticazioneCorrente autenticazione;

    public AvvisiOperativiController(AvvisiOperativiApi avvisi, AutenticazioneCorrente autenticazione) {
        this.avvisi = avvisi;
        this.autenticazione = autenticazione;
    }

    @GetMapping("/api/avvisi")
    public List<AvvisoOperativo> elenco(Authentication authentication) {
        return avvisi.elencoAvvisiOperativi(autenticazione.richiedi(authentication));
    }
}
