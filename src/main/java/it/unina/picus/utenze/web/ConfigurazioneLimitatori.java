package it.unina.picus.utenze.web;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Soglie dei limitatori delle richieste senza sessione: una istanza per ciascun uso, con valori propri. */
@Configuration(proxyBeanMethods = false)
class ConfigurazioneLimitatori {

    static final String RECUPERO_PASSWORD = "limitatoreRecuperoPassword";

    static final int RECUPERI_MAX_RICHIESTE = 3;
    static final Duration RECUPERI_FINESTRA = Duration.ofMinutes(5);
    static final int MAX_INDIRIZZI_RICORDATI = 10_000;

    /** Al massimo 3 richieste di recupero ogni 5 minuti per indirizzo (soglia da validare con il PO). */
    @Bean(RECUPERO_PASSWORD)
    LimitatoreRichieste limitatoreRecuperoPassword(Clock clock) {
        return new LimitatoreRichieste(clock, RECUPERI_MAX_RICHIESTE, RECUPERI_FINESTRA, MAX_INDIRIZZI_RICORDATI);
    }
}
