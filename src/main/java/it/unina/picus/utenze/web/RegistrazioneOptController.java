package it.unina.picus.utenze.web;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.utenze.api.OperazioneUtenzaNonConsentitaException;
import it.unina.picus.utenze.api.UtenzaNonTrovataException;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;
import it.unina.picus.utenze.interno.applicazione.ConsegnaLinkAttivazione;
import it.unina.picus.utenze.interno.applicazione.ConsegnaLinkAttivazione.Esito;
import it.unina.picus.utenze.interno.applicazione.DatiOptNonValidiException;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService;
import it.unina.picus.utenze.interno.applicazione.RiprovaTransitoria;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.ComandoRegistrazioneOpt;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.EsitoRegistrazioneOpt;

@RestController
@RequestMapping("/api/opt")
class RegistrazioneOptController {

    private final Optional<RegistrazioneOptService> registrazioneService;
    private final Optional<ConsegnaLinkAttivazione> consegna;
    private final UtenzeApi utenze;

    RegistrazioneOptController(
            Optional<RegistrazioneOptService> registrazioneService,
            Optional<ConsegnaLinkAttivazione> consegna,
            UtenzeApi utenze) {
        this.registrazioneService = registrazioneService;
        this.consegna = consegna;
        this.utenze = utenze;
    }

    record RichiestaCreazioneOpt(
            Object codiceStruttura,
            Object nome,
            Object comune,
            Object comuneCodice,
            Object codiceFiscale,
            Object email
    ) {
        String comuneRisolto() {
            String c = soloStringa(comune, "comune");
            return c != null && !c.isBlank() ? c : soloStringa(comuneCodice, "comune");
        }

        static String soloStringa(Object valore, String campo) {
            if (valore == null) {
                return null;
            }
            if (!(valore instanceof String s)) {
                throw new DatiOptNonValidiException(campo, "Formato del campo non valido: è richiesto un valore testuale.");
            }
            return s;
        }
    }

    @PostMapping
    ResponseEntity<?> creaOpt(@RequestBody RichiestaCreazioneOpt body) {
        if (registrazioneService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("messaggio", "Funzionalità non disponibile nel profilo skeleton"));
        }

        try {
            EsitoRegistrazioneOpt esitoReg = registrazioneService.get().registraOpt(new ComandoRegistrazioneOpt(
                    RichiestaCreazioneOpt.soloStringa(body.codiceStruttura(), "codiceStruttura"),
                    RichiestaCreazioneOpt.soloStringa(body.nome(), "nome"),
                    body.comuneRisolto(),
                    RichiestaCreazioneOpt.soloStringa(body.codiceFiscale(), "codiceFiscale"),
                    RichiestaCreazioneOpt.soloStringa(body.email(), "email")
            ));

            Esito esito = consegnaLink(esitoReg.datiInvio());

            return ResponseEntity.created(URI.create("/api/opt/" + esitoReg.accountId()))
                    .body(Map.of(
                            "accountId", esitoReg.accountId(),
                            "invitoInviato", esito == Esito.INVIATO,
                            "erroreInvio", esito == Esito.NON_RIUSCITO,
                            "messaggio", MessaggiInvito.allaCreazione(esito, "Organizzazione partner registrata")
                    ));

        } catch (DatiOptNonValidiException ex) {
            String campoForm = "comuneCodice".equals(ex.getCampo()) ? "comune" : ex.getCampo();
            return ResponseEntity.badRequest().body(Map.of(
                    "messaggio", ex.getMessage(),
                    "campo", campoForm,
                    "errore", ex.getMessage(),
                    "campi", Map.of(campoForm, ex.getMessage())
            ));
        }
    }

    @PostMapping("/{id}/reinvia-invito")
    ResponseEntity<?> reinviaInvito(@PathVariable UUID id) {
        if (registrazioneService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("messaggio", "Funzionalità non disponibile nel profilo skeleton"));
        }
        try {
            var servizio = registrazioneService.get();
            DatiInvioAttivazione datiInvio = servizio.preparaReinvio(id);
            Esito esito = consegnaLink(datiInvio);
            // la email è già partita: se la registrazione dell'esito ha un errore transitorio si ripete solo quella
            RiprovaTransitoria.esegui(() -> servizio.concludeReinvio(id, datiInvio, esito != Esito.NON_RIUSCITO));
            if (esito == Esito.NON_RIUSCITO) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "accountId", id,
                        "invitoInviato", false,
                        "erroreInvio", true,
                        "messaggio", MessaggiInvito.REINVIO_NON_RIUSCITO
                ));
            }
            return ResponseEntity.ok(Map.of(
                    "accountId", id,
                    "invitoInviato", esito == Esito.INVIATO,
                    "erroreInvio", false,
                    "messaggio", MessaggiInvito.alReinvio(esito)
            ));
        } catch (UtenzaNonTrovataException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("messaggio", ex.getMessage()));
        } catch (OperazioneUtenzaNonConsentitaException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("messaggio", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/revoca")
    ResponseEntity<?> revocaOpt(@PathVariable UUID id) {
        try {
            utenze.revocaUtenzaOpt(id);
            return ResponseEntity.ok(Map.of(
                    "accountId", id,
                    "stato", UtenzeApi.StatoUtenza.REVOCATO.name(),
                    "messaggio", "Accesso dell'organizzazione partner revocato."
            ));
        } catch (UtenzaNonTrovataException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("messaggio", ex.getMessage()));
        } catch (OperazioneUtenzaNonConsentitaException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("messaggio", ex.getMessage()));
        }
    }

    /** Spedisce il link; senza servizio di consegna (profilo skeleton) l'invito resta solo generato. */
    private Esito consegnaLink(DatiInvioAttivazione datiInvio) {
        return consegna.map(c -> c.consegna(datiInvio)).orElse(Esito.SIMULATO);
    }
}
