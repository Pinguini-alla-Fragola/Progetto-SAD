package it.unina.picus.utenze.web;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/*
Base di tutti i test di integrazione: un solo PostgreSQL reale (Testcontainers) condiviso
dalle classi della suite e avviato una volta sola. Lo schema è creato da Flyway come in
produzione; nessun database H2.
Il container non usa {@code @Container}, che lo fermerebbe e riavvierebbe a ogni classe
lasciando nella cache di Spring un contesto con la porta vecchia. Lo ferma Ryuk alla fine della JVM.
Le annotazioni di Spring stanno qui e non nelle sottoclassi: tutte le classi ottengono la
stessa configurazione e quindi riusano lo stesso contesto dalla cache, invece di avviarne uno
per classe. Una sottoclasse non dovrebbe aggiungere {@code @MockitoBean}, proprietà o profili
propri: ogni differenza crea un contesto in più.
*/
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrazionePostgresBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException(
                    "I test di integrazione richiedono Docker: avvia Docker Desktop e rilancia la build.");
        }
        POSTGRES.start();
    }

    /** Valore massimo di un codice fiscale numerico di undici cifre. */
    private static final long MASSIMO_CODICE_FISCALE_NUMERICO = 99_999_999_999L;

    /** Ultimo codice fiscale emesso nella suite; parte da zero, che la registrazione rifiuta. */
    private static final AtomicLong ULTIMO_CODICE_FISCALE = new AtomicLong();

    /*Codice fiscale sintetico per un profilo OPT di prova (colonna aggiunta dalla V17, #44).
    Nel database resta facoltativo, ma {@code RegistrazioneOptService} lo richiede: le fixture lo
    valorizzano così da rappresentare una OPT.
    Ogni valore rispetta le regole della registrazione: undici cifre ASCII ({@code [0-9]{11}}),
    formattate con {@link Locale#ROOT} così da non dipendere dal locale della JVM, e mai
    {@code 00000000000}, che il servizio rifiuta. Un contatore positivo condiviso dalla suite
    garantisce valori distinti, anche se in futuro la colonna diventasse unica.
    */
    protected static String codiceFiscaleDiProva() {
        long progressivo = ULTIMO_CODICE_FISCALE.incrementAndGet();
        if (progressivo > MASSIMO_CODICE_FISCALE_NUMERICO) {
            throw new IllegalStateException("Codici fiscali di prova esauriti.");
        }
        return String.format(Locale.ROOT, "%011d", progressivo);
    }
}