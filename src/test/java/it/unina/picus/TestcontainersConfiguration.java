package it.unina.picus;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Database dei test: un PostgreSQL 17 in Docker avviato da Testcontainers, separato dal database di
 * sviluppo di {@code docker-compose.yml}.
 *
 * <p>Il contenitore non ha volumi: nasce vuoto, Flyway applica le stesse migrazioni dell'applicazione
 * ({@code db/migration} e {@code db/postgresql}) e viene eliminato a fine esecuzione. I dati di prova
 * non raggiungono quindi altri database.</p>
 *
 * <p>{@link ServiceConnection} imposta URL, utente e password della datasource: i test non leggono
 * {@code PICUS_DB_URL}, {@code PICUS_DB_USER} né {@code PICUS_DB_PASSWORD}. Le classi di test con la
 * stessa configurazione condividono il contesto Spring e quindi un solo contenitore.</p>
 *
 * <p>Richiede Docker attivo sulla macchina che esegue i test, sia in locale sia in CI.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Stessa immagine del servizio {@code picus-db} di {@code docker-compose.yml}. */
    public static final DockerImageName POSTGRES_17 = DockerImageName.parse("postgres:17");

    /** Costruttore usato da Spring per la configurazione di test. */
    public TestcontainersConfiguration() {
    }

    /**
     * Contenitore PostgreSQL usato come datasource dei test.
     *
     * @return contenitore PostgreSQL 17, avviato e fermato da Spring insieme al contesto
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_17);
    }
}
