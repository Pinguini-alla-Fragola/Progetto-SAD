package it.unina.picus.utenze.web;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import it.unina.picus.utenze.interno.applicazione.RecuperoPasswordService;
import it.unina.picus.utenze.interno.applicazione.RichiestaRecuperoPassword;

/**
 * Recupero autonomo della password (US #33): pagina di richiesta e di cambio password, e richiesta del link.
 *
 * <p>La richiesta risponde allo stesso modo per ogni indirizzo scritto correttamente, sia che l'account sia
 * attivo, sia che non esista, sia in attesa di attivazione o revocato: chi non è autenticato non deve poter
 * scoprire quali indirizzi sono registrati. Per lo stesso motivo tutto il lavoro sull'account avviene in
 * secondo piano ({@link RichiestaRecuperoPassword}) e il numero di richieste per indirizzo è limitato.</p>
 */
@Controller
class RecuperoPasswordController {

    static final String MESSAGGIO_CONFERMA =
            "Se l'indirizzo corrisponde a un account attivo, riceverai un link per scegliere una nuova password. "
            + "Il link vale 15 minuti.";
    static final String MESSAGGIO_TROPPE_RICHIESTE =
            "Hai fatto troppe richieste per questo indirizzo. Riprova tra qualche minuto.";
    static final String MESSAGGIO_NON_DISPONIBILE = "Funzionalità non disponibile nel profilo skeleton";

    /** L'email resta un valore JSON generico fino al controllo del tipo: un numero o un booleano non diventano testo. */
    record RichiestaRecupero(Object email) {}

    private final Optional<RecuperoPasswordService> recupero;
    private final Optional<RichiestaRecuperoPassword> richiesta;
    private final LimitatoreRichieste limitatore;

    RecuperoPasswordController(Optional<RecuperoPasswordService> recupero,
            Optional<RichiestaRecuperoPassword> richiesta,
            @Qualifier(ConfigurazioneLimitatori.RECUPERO_PASSWORD) LimitatoreRichieste limitatore) {
        this.recupero = recupero;
        this.richiesta = richiesta;
        this.limitatore = limitatore;
    }

    /**
     * Senza link mostra il modulo di richiesta. Con un link valido mostra il cambio password; con un link scaduto,
     * già usato o inventato invita a ripetere la richiesta, senza dire quale dei casi sia.
     */
    @GetMapping("/recupera")
    String mostraPagina(@RequestParam(name = "token", required = false) String token, Model modello) {
        boolean conLink = token != null && !token.isBlank();
        boolean linkValido = conLink && recupero.map(s -> s.isLinkValido(token)).orElse(false);
        modello.addAttribute("cambioPassword", linkValido);
        modello.addAttribute("linkNonValido", conLink && !linkValido);
        modello.addAttribute("token", linkValido ? token : null);
        return "recupero";
    }

    @PostMapping("/api/auth/recupera")
    @ResponseBody
    ResponseEntity<Map<String, Object>> richiediLink(@RequestBody RichiestaRecupero corpo) {
        if (richiesta.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("messaggio", MESSAGGIO_NON_DISPONIBILE));
        }
        Optional<String> email = EmailDigitata.leggi(corpo.email());
        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "messaggio", EmailDigitata.MESSAGGIO_NON_VALIDA,
                    "campi", Map.of("email", EmailDigitata.MESSAGGIO_NON_VALIDA)));
        }
        if (!limitatore.ammetti(email.get())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("messaggio", MESSAGGIO_TROPPE_RICHIESTE));
        }
        richiesta.get().richiedi(email.get());
        return ResponseEntity.ok(Map.of("messaggio", MESSAGGIO_CONFERMA));
    }
}
