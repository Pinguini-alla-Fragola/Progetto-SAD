package it.unina.picus.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Contesto completo con catena di sicurezza attiva, MockMvc e PostgreSQL 17 in Docker.
 *
 * <p>Tutte le classi che usano questa annotazione hanno la stessa configurazione, quindi Spring
 * riusa un solo contesto e un solo container. Richiede Docker in esecuzione.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresqlDiProva.class)
public @interface IntegrazionePostgresql {
}
