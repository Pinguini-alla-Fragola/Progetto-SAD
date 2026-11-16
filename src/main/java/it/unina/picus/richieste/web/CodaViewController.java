package it.unina.picus.richieste.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.shared.ControlloAccesso;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/** Proiezione di presentazione della coda, riservata al Banco. */
@RestController
class CodaViewController {
    private final InvioRichiesteApi richieste;
    private final UtenzeApi utenze;
    private final ConsultazioneCatalogoApi catalogo;

    CodaViewController(InvioRichiesteApi richieste, UtenzeApi utenze, ConsultazioneCatalogoApi catalogo) {
        this.richieste = richieste;
        this.utenze = utenze;
        this.catalogo = catalogo;
    }

    /** Dati leggibili della coda senza estendere il DTO condiviso con gli erogatori. */
    @GetMapping(value = "/banco/richieste/coda", produces = MediaType.APPLICATION_JSON_VALUE)
    List<RigaCoda> coda(IdentitaAutenticata attore) {
        ControlloAccesso.richiedi(attore.ruolo() == RuoloUtente.BANCO, "Coda riservata al Banco");
        Map<UUID, String> nomi = new HashMap<>();
        Map<String, String> comuni = new HashMap<>();
        return richieste.codaBanco(attore).stream().map(richiesta -> {
            var dettaglio = richieste.consultaRichiesta(attore, richiesta.id());
            String opt = nomi.computeIfAbsent(dettaglio.titolareId(), id -> utenze.cercaAccount(id)
                    .map(UtenzeApi.AccountSintetico::nomeVisualizzato).orElse("OPT non disponibile"));
            String comune = comuni.computeIfAbsent(dettaglio.comuneCodice(), codice -> catalogo.cercaComune(codice)
                    .map(c -> c.nome()).orElse(codice));
            return new RigaCoda(richiesta, opt, comune);
        }).toList();
    }

    public record RigaCoda(RichiestaSintesi richiesta, String optRichiedente, String comune) {}
}
