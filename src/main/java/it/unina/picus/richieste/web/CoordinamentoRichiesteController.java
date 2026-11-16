package it.unina.picus.richieste.web;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.CoordinamentoRichiesteApi;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.shared.AutenticazioneCorrente;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Adattatore REST dei comandi del Banco, senza logica di presentazione. */
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CoordinamentoRichiesteController {
    private final CoordinamentoRichiesteApi coordinamento;
    private final AutenticazioneCorrente autenticazione;

    public CoordinamentoRichiesteController(CoordinamentoRichiesteApi coordinamento, AutenticazioneCorrente autenticazione) {
        this.coordinamento = coordinamento;
        this.autenticazione = autenticazione;
    }

    @PostMapping("/richieste/{id}/presa-in-carico")
    public RichiestaSintesi prendiInCarico(Authentication authentication, @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> corpo) {
        var attore = autenticazione.richiedi(authentication);
        verificaCorpoVuoto(corpo);
        return coordinamento.prendiInCarico(attore, id);
    }

    @GetMapping("/richieste/{id}/candidati")
    public List<Candidato> candidati(Authentication authentication, @PathVariable UUID id) {
        return coordinamento.cercaErogatoriOrdinati(autenticazione.richiedi(authentication), id);
    }

    @PostMapping(value = "/richieste/{id}/contatti", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ContattoDisponibilita> interpella(Authentication authentication, @PathVariable UUID id,
            @RequestBody Map<String, Object> corpo) {
        var attore = autenticazione.richiedi(authentication);
        if (corpo == null || !corpo.keySet().equals(Set.of("erogatoreId", "ambito"))) {
            throw nonValido("contatto", "Indica soltanto erogatoreId e ambito");
        }
        UUID erogatoreId;
        try {
            if (!(corpo.get("erogatoreId") instanceof String testo)) {
                throw new IllegalArgumentException();
            }
            erogatoreId = UUID.fromString(testo);
            if (!erogatoreId.toString().equalsIgnoreCase(testo)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw nonValido("erogatoreId", "Seleziona un account erogatore valido");
        }
        AmbitoErogazione ambito;
        try {
            if (!(corpo.get("ambito") instanceof String testo)) {
                throw new IllegalArgumentException();
            }
            ambito = AmbitoErogazione.valueOf(testo);
        } catch (IllegalArgumentException ex) {
            throw nonValido("ambito", "Seleziona un ambito di erogazione valido");
        }
        var contatto = coordinamento.interpellaErogatore(attore, id, erogatoreId, ambito);
        return ResponseEntity.created(URI.create("/api/contatti-disponibilita/" + contatto.id())).body(contatto);
    }

    @PostMapping("/contatti-disponibilita/{id}/assegnazione")
    public RichiestaSintesi assegna(Authentication authentication, @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> corpo) {
        var attore = autenticazione.richiedi(authentication);
        verificaCorpoVuoto(corpo);
        return coordinamento.assegnaErogatore(attore, id);
    }

    private static void verificaCorpoVuoto(Map<String, Object> corpo) {
        if (corpo != null && !corpo.isEmpty()) {
            throw nonValido("comando", "Questo comando non prevede campi aggiuntivi");
        }
    }

    private static DatiRichiestaNonValidiException nonValido(String campo, String messaggio) {
        return new DatiRichiestaNonValidiException(Map.of(campo, messaggio));
    }
}
