package it.unina.picus.shared;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL 17 avviato in Docker con Testcontainers per i test di integrazione.
 *
 * <p>I test di coordinamento non possono girare su H2: indice parziale del contatto in attesa,
 * lock pessimistici e vincoli delle migrazioni sono specifici di PostgreSQL. Il container è un
 * bean del contesto di prova: le classi annotate con {@link IntegrazionePostgresql} condividono
 * lo stesso contesto in cache e quindi un unico container per l'intera esecuzione.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresqlDiProva {

    /** Immagine allineata a {@code docker-compose.yml}. */
    public static final String IMMAGINE = "postgres:17";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresql() {
        return new PostgreSQLContainer(IMMAGINE);
    }
}
