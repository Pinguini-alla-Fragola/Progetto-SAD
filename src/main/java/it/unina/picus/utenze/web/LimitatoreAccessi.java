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
 *
 * <p>Le email distinte memorizzate non superano mai {@value #MAX_CHIAVI}: a mappa piena si eliminano
 * prima le chiavi scadute; se non basta, l'email nuova è rifiutata finché non si libera spazio, così
 * un'ondata di indirizzi inventati non svuota il contatore di un account già sotto tentativo.</p>
 */
@Component
class LimitatoreAccessi {

    static final int MAX_FALLIMENTI = 5;
    static final Duration FINESTRA = Duration.ofMinutes(5);
    static final Duration ATTESA = Duration.ofSeconds(60);
    static final int MAX_CHIAVI = 10_000;
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
        Deque<Instant> d = coda(chiave, ora);
        if (d == null) {
            return false;
        }
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

    /** @return la coda della chiave, creata se serve; null se la mappa è piena di chiavi ancora attive. */
    private Deque<Instant> coda(String chiave, Instant ora) {
        Deque<Instant> esistente = tentativi.get(chiave);
        if (esistente != null) {
            return esistente;
        }
        synchronized (tentativi) { // la creazione è seriale: il limite non viene superato in parallelo
            esistente = tentativi.get(chiave);
            if (esistente != null) {
                return esistente;
            }
            if (tentativi.size() >= MAX_CHIAVI) {
                Instant limite = ora.minus(FINESTRA);
                tentativi.values().removeIf(q -> {
                    synchronized (q) {
                        return q.peekLast() != null && q.peekLast().isBefore(limite);
                    }
                });
                if (tentativi.size() >= MAX_CHIAVI) {
                    return null;
                }
            }
            Deque<Instant> nuova = new ArrayDeque<>();
            tentativi.put(chiave, nuova);
            return nuova;
        }
    }

    /** Accesso riuscito: i tentativi precedenti non contano più. */
    void azzera(String chiave) {
        tentativi.remove(chiave);
    }

    /** Solo per i test. */
    int numeroChiavi() {
        return tentativi.size();
    }

    /** Solo per i test. */
    void azzeraTutto() {
        tentativi.clear();
    }
}
