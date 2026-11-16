package it.unina.picus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Test di integrazione con l'applicazione completa, MockMvc e il PostgreSQL di
 * {@link ContainerPostgres}. Usare sempre questa annotazione, senza aggiungere proprietà
 * diverse classe per classe: così tutti i test riusano un solo contesto e un solo container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainerPostgres.class)
public @interface TestIntegrazionePostgres {
}
