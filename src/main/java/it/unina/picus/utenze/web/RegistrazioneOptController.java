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

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.api.OperazioneUtenzaNonConsentitaException;
import it.unina.picus.utenze.api.UtenzaNonTrovataException;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;
import it.unina.picus.utenze.interno.applicazione.DatiOptNonValidiException;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.ComandoRegistrazioneOpt;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.EsitoRegistrazioneOpt;

@RestController
@RequestMapping("/api/opt")
class RegistrazioneOptController {

    private final Optional<RegistrazioneOptService> registrazioneService;
    private final Optional<AttivazioneUtenzaService> attivazioneService;
    private final Optional<InvioEmail> invioEmail;
    private final UtenzeApi utenze;

    RegistrazioneOptController(
            Optional<RegistrazioneOptService> registrazioneService,
            Optional<AttivazioneUtenzaService> attivazioneService,
            Optional<InvioEmail> invioEmail,
            UtenzeApi utenze) {
        this.registrazioneService = registrazioneService;
        this.attivazioneService = attivazioneService;
        this.invioEmail = invioEmail;
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

            EsitoInvio esitoInvio = tentaInvioAttivazione(esitoReg.datiInvio());

            return ResponseEntity.created(URI.create("/api/opt/" + esitoReg.accountId()))
                    .body(Map.of(
                            "accountId", esitoReg.accountId(),
                            "invitoInviato", esitoInvio.inviato(),
                            "erroreInvio", esitoInvio.errore(),
                            "messaggio", esitoInvio.messaggioCreazione()
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
        if (attivazioneService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("messaggio", "Funzionalità non disponibile nel profilo skeleton"));
        }
        try {
            DatiInvioAttivazione datiInvio = attivazioneService.get()
                    .rigeneraTokenPerAccount(id, UtenzeApi.RuoloUtente.OPT);
            EsitoInvio esitoInvio = tentaInvioAttivazione(datiInvio);
            if (esitoInvio.errore()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "accountId", id,
                        "invitoInviato", false,
                        "erroreInvio", true,
                        "messaggio", "Invio email di attivazione non riuscito. Verifica la configurazione SMTP e riprova."
                ));
            }
            return ResponseEntity.ok(Map.of(
                    "accountId", id,
                    "invitoInviato", esitoInvio.inviato(),
                    "erroreInvio", false,
                    "messaggio", esitoInvio.inviato()
                            ? "Nuovo link di attivazione inviato via email."
                            : "Nuovo link di attivazione generato in modalità simulata."
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

    private record EsitoInvio(boolean inviato, boolean errore, String messaggioCreazione) {}

    private EsitoInvio tentaInvioAttivazione(DatiInvioAttivazione datiInvio) {
        if (invioEmail.isEmpty()) {
            return new EsitoInvio(false, false,
                    "Organizzazione partner registrata (nessun servizio email configurato).");
        }
        InvioEmail servizio = invioEmail.get();
        try {
            servizio.inviaLinkAttivazione(
                    datiInvio.emailDestinatario(),
                    datiInvio.nomeDestinatario(),
                    datiInvio.tokenInChiaro()
            );
            if (servizio.isSimulato()) {
                return new EsitoInvio(false, false,
                        "Organizzazione partner registrata (modalità demo/simulata: nessun server SMTP configurato, email non inviata).");
            }
            return new EsitoInvio(true, false,
                    "Organizzazione partner registrata. Email di attivazione inviata.");
        } catch (RuntimeException ex) {
            return new EsitoInvio(false, true,
                    "Organizzazione partner registrata in attesa di attivazione, ma l'invio dell'email è fallito. Puoi reinviare l'invito dall'elenco Account.");
        }
    }
}