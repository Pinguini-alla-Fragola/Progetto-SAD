package it.unina.picus.utenze.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
 *
 * <p>Le email distinte memorizzate non superano mai {@value #MAX_CHIAVI}: a mappa piena si eliminano
 * prima le chiavi scadute; se non basta, l'email nuova è rifiutata finché non si libera spazio, così
 * un'ondata di indirizzi inventati non svuota il contatore di un account già sotto tentativo.</p>
 *
 * <p>Conteggio, pulizia e rimozione avvengono sotto un unico lock: una pulizia non può togliere la
 * coda di una chiave mentre un tentativo sta per registrarvisi. Il costo è trascurabile, perché le
 * code hanno al massimo {@value #MAX_FALLIMENTI} elementi e la verifica BCrypt avviene fuori dal lock.</p>
 */
@Component
class LimitatoreAccessi {

    static final int MAX_FALLIMENTI = 5;
    static final Duration FINESTRA = Duration.ofMinutes(5);
    static final Duration ATTESA = Duration.ofSeconds(60);
    static final int MAX_CHIAVI = 10_000;
    private static final int LUNGHEZZA_MAX_CHIAVE = 254;

    private final Clock clock;
    private final Map<String, Deque<Instant>> tentativi = new HashMap<>(); // protetta da this

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
    synchronized boolean ammetti(String chiave) {
        Instant ora = clock.instant();
        Instant limite = ora.minus(FINESTRA);
        Deque<Instant> d = tentativi.get(chiave);
        if (d == null) {
            if (tentativi.size() >= MAX_CHIAVI) {
                // ogni coda memorizzata contiene almeno un tentativo
                tentativi.values().removeIf(q -> q.peekLast().isBefore(limite));
                if (tentativi.size() >= MAX_CHIAVI) {
                    return false;
                }
            }
            d = new ArrayDeque<>();
            tentativi.put(chiave, d);
        }
        while (!d.isEmpty() && d.peekFirst().isBefore(limite)) {
            d.removeFirst();
        }
        if (d.size() >= MAX_FALLIMENTI && ora.isBefore(d.peekLast().plus(ATTESA))) {
            return false;
        }
        d.addLast(ora);
        return true;
    }

    /** Accesso riuscito: i tentativi precedenti non contano più. */
    synchronized void azzera(String chiave) {
        tentativi.remove(chiave);
    }

    /** Solo per i test. */
    synchronized int numeroChiavi() {
        return tentativi.size();
    }

    /** Solo per i test. */
    synchronized void azzeraTutto() {
        tentativi.clear();
    }
}
