package it.unina.picus.utenze.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
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
 * registrati. Lo stato è in memoria (MVP a istanza singola) e ricorda al massimo {@value #MAX_CHIAVI}
 * indirizzi: a mappa piena si dimentica quello usato meno di recente. Una richiesta nuova non viene mai
 * rifiutata per mancanza di spazio, altrimenti chi invia molti indirizzi inventati farebbe rispondere
 * {@code 429} a tutti gli utenti reali. Il prezzo è che, con {@value #MAX_CHIAVI} richieste ad altri
 * indirizzi, il contatore di un indirizzo si azzera: costo molto più alto del vantaggio ottenuto.</p>
 */
@Component
class LimitatoreReinvii {

    static final int MAX_RICHIESTE = 3;
    static final Duration FINESTRA = Duration.ofMinutes(5);
    static final int MAX_CHIAVI = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> richieste; // protetta da this

    @Autowired
    LimitatoreReinvii(ObjectProvider<Clock> clock) {
        this(clock.getIfAvailable(Clock::systemUTC), MAX_CHIAVI);
    }

    LimitatoreReinvii(Clock clock) {
        this(clock, MAX_CHIAVI);
    }

    /** Il tetto è un parametro solo perché i test possano riempire la mappa senza diecimila richieste. */
    LimitatoreReinvii(Clock clock, int maxChiavi) {
        this.clock = clock;
        // ordine di accesso: la chiave dimenticata a mappa piena è quella usata meno di recente
        this.richieste = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> piuVecchia) {
                return size() > maxChiavi;
            }
        };
    }

    /** @return false se la richiesta supera il limite e va rifiutata; altrimenti la registra e la ammette. */
    synchronized boolean ammetti(String chiave) {
        Instant ora = clock.instant();
        Instant limite = ora.minus(FINESTRA);
        Deque<Instant> coda = richieste.computeIfAbsent(chiave, k -> new ArrayDeque<>());
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
