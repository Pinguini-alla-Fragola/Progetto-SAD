package it.unina.picus.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Contesto completo con catena di sicurezza attiva, MockMvc e PostgreSQL 17 in Docker.
 *
 * <p>Tutte le classi che usano questa annotazione hanno la stessa configurazione, quindi Spring
 * riusa un solo contesto e un solo container. Richiede Docker in esecuzione.</p>
 *
 * <p>Il database del container è condiviso e {@link DatiSintetici#svuota()} cancella i dati di
 * tutti i test. Per questo l'annotazione dichiara il lock esclusivo {@value #DATABASE_CONDIVISO}:
 * anche con l'esecuzione parallela di JUnit, le classi che lo condividono non girano mai insieme
 * e i loro test vengono eseguiti uno alla volta. Le classi che avviano un proprio container non
 * ne hanno bisogno.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresqlDiProva.class)
@ResourceLock(IntegrazionePostgresql.DATABASE_CONDIVISO)
public @interface IntegrazionePostgresql {

    /** Nome del lock JUnit che serializza l'accesso al database PostgreSQL condiviso. */
    String DATABASE_CONDIVISO = "it.unina.picus.postgresql-condiviso";
}
