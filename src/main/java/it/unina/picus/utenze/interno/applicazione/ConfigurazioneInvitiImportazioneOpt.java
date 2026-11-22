package it.unina.picus.utenze.interno.applicazione;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@Profile("!skeleton")
class ConfigurazioneInvitiImportazioneOpt {

    @Bean(defaultCandidate = false)
    ThreadPoolTaskExecutor esecutoreInvitiImportazioneOpt() {
        var esecutore = new ThreadPoolTaskExecutor();
        esecutore.setCorePoolSize(2);
        esecutore.setMaxPoolSize(2);
        esecutore.setQueueCapacity(100);
        esecutore.setThreadNamePrefix("inviti-csv-opt-");
        return esecutore;
    }
}
