package it.unina.picus.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Sorgente dell'istante di invio, sostituibile senza dipendere dall'orologio statico. */
@Configuration(proxyBeanMethods = false)
public class ConfigurazioneTempo {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
