package it.unina.picus.utenze.web;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.utenze.interno.applicazione.ReinvioLinkSuRichiesta;

/**
 * Nuovo link di attivazione chiesto da chi possiede l'indirizzo, senza sessione (link scaduto o già usato).
 *
 * <p>La risposta è la stessa per ogni indirizzo scritto correttamente, sia che l'account esista e sia in
 * attesa, sia che non esista o sia già attivo o revocato: chi non è autenticato non deve poter scoprire
 * quali indirizzi sono registrati. Per lo stesso motivo tutto il lavoro sull'account (nuovo token e email)
 * avviene in secondo piano ({@link ReinvioLinkSuRichiesta}): la risposta ha gli stessi tempi per ogni indirizzo.</p>
 */
@RestController
@Profile("!skeleton")
class ReinvioAttivazioneController {

    static final String MESSAGGIO_CONFERMA =
            "Se l'indirizzo corrisponde a un account in attesa di attivazione, riceverai un nuovo link via email.";
    static final String MESSAGGIO_EMAIL_NON_VALIDA = "Inserisci un indirizzo email valido.";
    static final String MESSAGGIO_TROPPE_RICHIESTE =
            "Hai chiesto troppi link per questo indirizzo. Riprova tra qualche minuto.";

    private static final int LUNGHEZZA_MAX_EMAIL = 254;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** L'email resta un valore JSON generico fino al controllo del tipo: un numero o un booleano non diventano testo. */
    record RichiestaReinvio(Object email) {}

    private final ReinvioLinkSuRichiesta reinvio;
    private final LimitatoreReinvii limitatore;

    ReinvioAttivazioneController(ReinvioLinkSuRichiesta reinvio, LimitatoreReinvii limitatore) {
        this.reinvio = reinvio;
        this.limitatore = limitatore;
    }

    @PostMapping("/api/auth/attiva/reinvio")
    ResponseEntity<Map<String, Object>> richiediNuovoLink(@RequestBody RichiestaReinvio corpo) {
        if (!(corpo.email() instanceof String testo)) {
            return emailNonValida();
        }
        String email = testo.strip().toLowerCase(Locale.ROOT);
        if (email.length() > LUNGHEZZA_MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            return emailNonValida();
        }
        if (!limitatore.ammetti(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("messaggio", MESSAGGIO_TROPPE_RICHIESTE));
        }
        reinvio.richiedi(email);
        return ResponseEntity.ok(Map.of("messaggio", MESSAGGIO_CONFERMA));
    }

    private static ResponseEntity<Map<String, Object>> emailNonValida() {
        return ResponseEntity.badRequest().body(Map.of(
                "messaggio", MESSAGGIO_EMAIL_NON_VALIDA,
                "campi", Map.of("email", MESSAGGIO_EMAIL_NON_VALIDA)));
    }
}
