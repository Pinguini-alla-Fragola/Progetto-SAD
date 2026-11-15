package it.unina.picus.utenze.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limita i tentativi di accesso per email: dopo {@value #MAX_FALLIMENTI} tentativi falliti in
 * {@code FINESTRA} il tentativo successivo, se arriva entro {@code ATTESA} dall'ultimo, è rifiutato.
 *
 * <p>La chiave è l'email digitata, esista o no l'account: così il messaggio di attesa non permette di
 * scoprire quali account esistono. Ogni tentativo viene registrato <em>prima</em> della verifica
 * (e cancellato se riesce), così richieste parallele non aggirano il limite. Lo stato è in memoria
 * (MVP a istanza singola): per più istanze va spostato in uno store condiviso.</p>
 */
@Component
class LimitatoreAccessi {

    static final int MAX_FALLIMENTI = 5;
    static final Duration FINESTRA = Duration.ofMinutes(5);
    static final Duration ATTESA = Duration.ofSeconds(60);
    private static final int MAX_CHIAVI = 10_000;
    private static final int LUNGHEZZA_MAX_CHIAVE = 254;

    private final Clock clock;
    private final Map<String, Deque<Instant>> tentativi = new ConcurrentHashMap<>();

    @Autowired
    LimitatoreAccessi(ObjectProvider<Clock> clock) {
        this(clock.getIfAvailable(Clock::systemUTC));
    }

    LimitatoreAccessi(Clock clock) {
        this.clock = clock;
    }

    static String chiave(String email) {
        String e = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return e.length() > LUNGHEZZA_MAX_CHIAVE ? e.substring(0, LUNGHEZZA_MAX_CHIAVE) : e;
    }

    /** @return false se il tentativo va rifiutato; altrimenti lo registra e lo ammette. */
    boolean ammetti(String chiave) {
        Instant ora = clock.instant();
        if (tentativi.size() > MAX_CHIAVI) {
            tentativi.values().removeIf(d -> d.isEmpty() || d.peekLast().isBefore(ora.minus(FINESTRA)));
        }
        Deque<Instant> d = tentativi.computeIfAbsent(chiave, k -> new ArrayDeque<>());
        synchronized (d) {
            while (!d.isEmpty() && d.peekFirst().isBefore(ora.minus(FINESTRA))) {
                d.removeFirst();
            }
            if (d.size() >= MAX_FALLIMENTI && ora.isBefore(d.peekLast().plus(ATTESA))) {
                return false;
            }
            d.addLast(ora);
            return true;
        }
    }

    /** Accesso riuscito: i tentativi precedenti non contano più. */
    void azzera(String chiave) {
        tentativi.remove(chiave);
    }

    /** Solo per i test. */
    void azzeraTutto() {
        tentativi.clear();
    }
}
