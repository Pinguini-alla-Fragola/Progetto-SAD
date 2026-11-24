package it.unina.picus.utenze.web;

import java.util.UUID;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/*
Base di <b>tutti</b> i test di integrazione: un solo PostgreSQL reale (Testcontainers) condiviso
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

    /** Valore massimo, escluso, di un codice fiscale numerico di undici cifre. */
    private static final long LIMITE_CODICE_FISCALE_NUMERICO = 100_000_000_000L;

    /**
     * Codice fiscale sintetico per un profilo OPT di prova, richiesto dalla registrazione (#44).
     * Una OPT è un'organizzazione, quindi il codice è nella forma numerica di undici cifre accettata
     * dalla registrazione. È ricavato da un UUID casuale: oggi la colonna non è unica, ma così i test
     * restano indipendenti anche se in futuro lo diventasse.
     *
     * @return codice fiscale numerico di undici cifre
     */
    public static String codiceFiscaleDiProva() {
        long cifre = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), LIMITE_CODICE_FISCALE_NUMERICO);
        return String.format("%011d", cifre);
    }
}