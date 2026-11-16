package it.unina.picus.comunicazioni.web;

import it.unina.picus.comunicazioni.api.AvvisiOperativiApi;
import it.unina.picus.comunicazioni.api.ComunicazioniApi.AvvisoOperativo;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Casella degli avvisi del solo account corrente. */
@RestController
public class AvvisiOperativiController {
    private final AvvisiOperativiApi avvisi;
    public AvvisiOperativiController(AvvisiOperativiApi avvisi) {
        this.avvisi = avvisi;
    }

    @GetMapping("/api/avvisi")
    public List<AvvisoOperativo> elenco(IdentitaAutenticata attore) {
        return avvisi.elencoAvvisiOperativi(attore);
    }
}
