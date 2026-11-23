package it.unina.picus.utenze.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Limita le richieste senza sessione che mandano una email, per esempio il recupero della password: al massimo
 * {@code maxRichieste} per chiave in ogni {@code finestra}. Senza limite chiunque conosca un indirizzo potrebbe
 * riempire la casella di posta di un utente o rendere inutilizzabile ogni link appena ricevuto, perché un nuovo
 * link sostituisce il precedente.
 *
 * <p>La chiave è l'email digitata, esista o no l'account, così il rifiuto non rivela quali indirizzi sono
 * registrati. Lo stato è in memoria (MVP a istanza singola) e ricorda al massimo {@code maxChiavi} indirizzi: a
 * mappa piena si dimentica quello usato meno di recente. Una richiesta nuova non viene mai rifiutata per mancanza
 * di spazio, altrimenti chi invia molti indirizzi inventati farebbe rispondere {@code 429} a tutti gli utenti
 * reali. Il prezzo è che, con {@code maxChiavi} richieste ad altri indirizzi, il contatore di un indirizzo si
 * azzera: costo molto più alto del vantaggio ottenuto.</p>
 *
 * <p>Ogni uso della classe ha la sua istanza, con soglie proprie: non è un componente Spring unico.</p>
 */
final class LimitatoreRichieste {

    private final Clock clock;
    private final int maxRichieste;
    private final Duration finestra;
    private final Map<String, Deque<Instant>> richieste; // protetta da this

    LimitatoreRichieste(Clock clock, int maxRichieste, Duration finestra, int maxChiavi) {
        this.clock = clock;
        this.maxRichieste = maxRichieste;
        this.finestra = finestra;
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
        Instant limite = ora.minus(finestra);
        Deque<Instant> coda = richieste.computeIfAbsent(chiave, k -> new ArrayDeque<>());
        while (!coda.isEmpty() && coda.peekFirst().isBefore(limite)) {
            coda.removeFirst();
        }
        if (coda.size() >= maxRichieste) {
            return false;
        }
        coda.addLast(ora);
        return true;
    }
}
