package it.unina.picus.utenze.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
 * <p>Ogni richiesta ammessa riceve una {@link Ammissione} con un numero proprio: se poi non viene presa in
 * carico si rilascia esattamente quella, senza toccare le richieste ammesse dopo di lei.</p>
 *
 * <p>Ogni uso della classe ha la sua istanza, con soglie proprie: non è un componente Spring unico.</p>
 */
final class LimitatoreRichieste {

    /**
     * Prenotazione di un posto nel limite.
     *
     * @param chiave indirizzo a cui appartiene
     * @param numero numero assegnato all'ammissione, unico per questo limitatore
     */
    record Ammissione(String chiave, long numero) {}

    private record Richiesta(long numero, Instant istante) {}

    private final Clock clock;
    private final int maxRichieste;
    private final Duration finestra;
    private final Map<String, Deque<Richiesta>> richieste; // protetta da this
    private long ultimoNumero; // protetto da this

    LimitatoreRichieste(Clock clock, int maxRichieste, Duration finestra, int maxChiavi) {
        this.clock = clock;
        this.maxRichieste = maxRichieste;
        this.finestra = finestra;
        // ordine di accesso: la chiave dimenticata a mappa piena è quella usata meno di recente
        this.richieste = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Richiesta>> piuVecchia) {
                return size() > maxChiavi;
            }
        };
    }

    /**
     * Prova a riservare un posto per la chiave.
     *
     * @return la prenotazione, oppure vuoto se la richiesta supera il limite e va rifiutata
     */
    synchronized Optional<Ammissione> ammetti(String chiave) {
        Instant ora = clock.instant();
        Instant limite = ora.minus(finestra);
        Deque<Richiesta> coda = richieste.computeIfAbsent(chiave, k -> new ArrayDeque<>());
        while (!coda.isEmpty() && coda.peekFirst().istante().isBefore(limite)) {
            coda.removeFirst();
        }
        if (coda.size() >= maxRichieste) {
            return Optional.empty();
        }
        Richiesta richiesta = new Richiesta(++ultimoNumero, ora);
        coda.addLast(richiesta);
        return Optional.of(new Ammissione(chiave, richiesta.numero()));
    }

    /** Restituisce il posto di una richiesta che non è stata presa in carico; le altre ammissioni restano. */
    synchronized void rilascia(Ammissione ammissione) {
        Deque<Richiesta> coda = richieste.get(ammissione.chiave());
        if (coda != null) {
            coda.removeIf(richiesta -> richiesta.numero() == ammissione.numero());
        }
    }
}
