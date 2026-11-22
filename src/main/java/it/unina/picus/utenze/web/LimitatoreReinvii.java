package it.unina.picus.utenze.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limita le richieste di un nuovo link di attivazione fatte senza sessione: al massimo
 * {@value #MAX_RICHIESTE} ogni {@code FINESTRA} per indirizzo. Senza limite chiunque conosca l'email di
 * un account in attesa potrebbe riempirgli la casella di posta o rendere inutilizzabile ogni link appena
 * ricevuto, perché ogni nuovo link sostituisce il precedente.
 *
 * <p>La chiave è l'email digitata, esista o no l'account, così il rifiuto non rivela quali indirizzi sono
 * registrati. Lo stato è in memoria (MVP a istanza singola) e ha un tetto di {@value #MAX_CHIAVI} indirizzi:
 * a mappa piena si eliminano le chiavi scadute, e se non basta la richiesta nuova è rifiutata.</p>
 */
@Component
class LimitatoreReinvii {

    static final int MAX_RICHIESTE = 3;
    static final Duration FINESTRA = Duration.ofMinutes(5);
    static final int MAX_CHIAVI = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> richieste = new HashMap<>(); // protetta da this

    @Autowired
    LimitatoreReinvii(ObjectProvider<Clock> clock) {
        this(clock.getIfAvailable(Clock::systemUTC));
    }

    LimitatoreReinvii(Clock clock) {
        this.clock = clock;
    }

    /** @return false se la richiesta supera il limite e va rifiutata; altrimenti la registra e la ammette. */
    synchronized boolean ammetti(String chiave) {
        Instant ora = clock.instant();
        Instant limite = ora.minus(FINESTRA);
        Deque<Instant> coda = richieste.get(chiave);
        if (coda == null) {
            if (richieste.size() >= MAX_CHIAVI) {
                // ogni coda memorizzata contiene almeno una richiesta
                richieste.values().removeIf(q -> q.peekLast().isBefore(limite));
                if (richieste.size() >= MAX_CHIAVI) {
                    return false;
                }
            }
            coda = new ArrayDeque<>();
            richieste.put(chiave, coda);
        }
        while (!coda.isEmpty() && coda.peekFirst().isBefore(limite)) {
            coda.removeFirst();
        }
        if (coda.size() >= MAX_RICHIESTE) {
            return false;
        }
        coda.addLast(ora);
        return true;
    }
}
