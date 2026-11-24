package it.unina.picus.utenze.web;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import it.unina.picus.utenze.interno.applicazione.LinkRecuperoNonValidoException;
import it.unina.picus.utenze.interno.applicazione.PasswordNonValidaException;
import it.unina.picus.utenze.interno.applicazione.RecuperoPasswordService;
import it.unina.picus.utenze.interno.applicazione.RichiestaRecuperoPassword;

/**
 * Recupero autonomo della password (US #33): pagina di richiesta e di cambio password, richiesta del link e
 * scelta della nuova password con il link.
 *
 * <p>La richiesta risponde allo stesso modo per ogni indirizzo scritto correttamente, sia che l'account sia
 * attivo, sia che non esista, sia in attesa di attivazione o revocato: chi non è autenticato non deve poter
 * scoprire quali indirizzi sono registrati. Per lo stesso motivo tutto il lavoro sull'account avviene in
 * secondo piano ({@link RichiestaRecuperoPassword}) e il numero di richieste per indirizzo è limitato. Se la
 * coda del lavoro in secondo piano è piena rispondono {@code 503} tutti gli indirizzi, allo stesso modo.</p>
 *
 * <p>La scelta della nuova password non ha un limite di richieste: il link è un valore casuale di 256 bit e il
 * lavoro costoso, l'hash della password, parte solo dopo aver verificato che il link sia valido.</p>
 */
@Controller
class RecuperoPasswordController {

    static final String MESSAGGIO_CONFERMA =
            "Se l'indirizzo corrisponde a un account attivo, riceverai un link per scegliere una nuova password. "
            + "Il link vale 15 minuti.";
    static final String MESSAGGIO_TROPPE_RICHIESTE =
            "Hai fatto troppe richieste per questo indirizzo. Riprova tra qualche minuto.";
    static final String MESSAGGIO_SERVIZIO_OCCUPATO =
            "Il servizio è momentaneamente occupato. Riprova tra qualche minuto.";
    static final String MESSAGGIO_NON_DISPONIBILE = "Funzionalità non disponibile nel profilo skeleton";
    static final String MESSAGGIO_FORMATO_NON_VALIDO = "Formato dei dati non valido.";
    static final String MESSAGGIO_PASSWORD_CAMBIATA = "Password cambiata. Ora puoi accedere con la nuova password.";

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

    /**
     * Corpo della scelta della nuova password. Token e password restano valori JSON generici fino al controllo del
     * tipo, e la password non compare mai nel testo del record, così non finisce nei log.
     */
    record RichiestaCambioPassword(Object token, Object nuovaPassword) {
        @Override
        public String toString() {
            return "RichiestaCambioPassword[...]";
        }
    }

    /**
     * Sceglie la nuova password con il link di recupero. Un link non utilizzabile risponde {@code 410} con
     * l'invito a ripetere la richiesta; una password da correggere risponde {@code 400} con il campo, e il link
     * resta valido per riprovare.
     */
    @PostMapping("/api/auth/recupera/conferma")
    @ResponseBody
    ResponseEntity<Map<String, Object>> cambiaPassword(@RequestBody RichiestaCambioPassword corpo) {
        if (recupero.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("messaggio", MESSAGGIO_NON_DISPONIBILE));
        }
        if (!(corpo.token() instanceof String token)) {
            return ResponseEntity.badRequest().body(Map.of("messaggio", MESSAGGIO_FORMATO_NON_VALIDO));
        }
        if (!(corpo.nuovaPassword() instanceof String nuovaPassword)) {
            return passwordDaCorreggere("Inserisci la nuova password.");
        }
        recupero.get().cambiaPassword(token, nuovaPassword);
        return ResponseEntity.ok(Map.of("messaggio", MESSAGGIO_PASSWORD_CAMBIATA));
    }

    @ExceptionHandler(LinkRecuperoNonValidoException.class)
    @ResponseBody
    ResponseEntity<Map<String, Object>> linkNonValido(LinkRecuperoNonValidoException eccezione) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of("messaggio", eccezione.getMessage()));
    }

    @ExceptionHandler(PasswordNonValidaException.class)
    @ResponseBody
    ResponseEntity<Map<String, Object>> passwordNonValida(PasswordNonValidaException eccezione) {
        return passwordDaCorreggere(eccezione.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> passwordDaCorreggere(String messaggio) {
        return ResponseEntity.badRequest().body(Map.of(
                "messaggio", messaggio,
                "campi", Map.of("nuovaPassword", messaggio)));
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
        Optional<LimitatoreRichieste.Ammissione> ammissione = limitatore.ammetti(email.get());
        if (ammissione.isEmpty()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("messaggio", MESSAGGIO_TROPPE_RICHIESTE));
        }
        if (!richiesta.get().richiedi(email.get())) {
            // La richiesta non è stata presa in carico: si restituisce proprio il suo posto nel limite.
            limitatore.rilascia(ammissione.get());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("messaggio", MESSAGGIO_SERVIZIO_OCCUPATO));
        }
        return ResponseEntity.ok(Map.of("messaggio", MESSAGGIO_CONFERMA));
    }
}
