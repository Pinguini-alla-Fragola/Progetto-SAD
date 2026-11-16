package it.unina.picus;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL usato dai test di integrazione: un container {@code postgres:17} avviato da
 * Testcontainers e collegato al datasource di Spring. Richiede Docker in esecuzione.
 *
 * <p>Il container è un bean: le classi annotate con {@link TestIntegrazionePostgres}
 * condividono lo stesso contesto Spring e quindi lo stesso database, avviato una sola volta.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainerPostgres {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}
