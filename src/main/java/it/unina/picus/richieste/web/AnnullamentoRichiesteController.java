package it.unina.picus.richieste.web;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.interno.applicazione.ServizioAnnullamentoRichieste;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Adattatore REST del comando di annullamento per i soggetti coinvolti nella richiesta. */
@RestController
public class AnnullamentoRichiesteController {
    private final ServizioAnnullamentoRichieste annullamento;

    /**
     * Collega l'adattatore al caso d'uso transazionale.
     * @param annullamento servizio che applica le regole della richiesta
     */
    public AnnullamentoRichiesteController(ServizioAnnullamentoRichieste annullamento) {
        this.annullamento = annullamento;
    }

    /**
     * Registra l'annullamento o restituisce la sintesi dell'esito già registrato.
     *
     * @param attore identità verificata ricavata dalla sessione
     * @param id identificativo della pratica
     * @param corpo oggetto JSON facoltativo, con il solo campo testuale {@code motivo}
     * @return sintesi della richiesta Annullata
     */
    @PostMapping(value = "/api/richieste/{id}/annullamento", produces = MediaType.APPLICATION_JSON_VALUE)
    public RichiestaSintesi annulla(IdentitaAutenticata attore, @PathVariable UUID id,
            @RequestBody(required = false) CorpoJson corpo) {
        return annullamento.annullaRichiesta(attore, id, leggiMotivo(corpo));
    }

    /**
     * Legge il solo dato previsto dal comando, senza conversioni implicite.
     * @param corpo oggetto JSON ricevuto oppure {@code null} per una richiesta senza corpo
     * @return motivo ricevuto o {@code null} se omesso
     */
    private static String leggiMotivo(CorpoJson corpo) {
        if (corpo == null) {
            return null;
        }
        var campi = corpo.campi();
        if (campi == null || !Set.of("motivo").containsAll(campi.keySet())) {
            throw new DatiRichiestaNonValidiException(Map.of("comando", "Indica soltanto il campo motivo"));
        }
        Object motivo = campi.get("motivo");
        if (motivo == null || motivo instanceof String) {
            return (String) motivo;
        }
        throw new DatiRichiestaNonValidiException(Map.of("motivo", "Il motivo deve essere un testo"));
    }
}
