package it.unina.picus.comunicazioni.web;

import java.util.Map;

import org.apache.tomcat.util.http.fileupload.impl.FileCountLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import it.unina.picus.comunicazioni.interno.applicazione.DatiComunicazioneNonValidiException;
import it.unina.picus.comunicazioni.interno.applicazione.RisorsaComunicazioniNonTrovataException;
import it.unina.picus.comunicazioni.interno.dominio.CampoComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.RegolaAllegati;

/**
 * Trasforma gli errori delle comunicazioni in risposte con messaggi controllati, senza mai i valori inseriti.
 * Il JSON illeggibile è già gestito per questo package dai gestori comuni.
 */
@RestControllerAdvice(assignableTypes = {ComunicazioniController.class, ConsultazioneComunicazioniController.class})
public class ErroriComunicazioni {

    private static final String CONTROLLA_CAMPI = "Controlla i campi della comunicazione";

    /**
     * Stessa forma degli errori degli altri moduli: elenca i campi da correggere.
     *
     * @param codice stato HTTP
     * @param messaggio messaggio generale
     * @param campi campi da correggere con il loro messaggio
     */
    public record Errore(String codice, String messaggio, Map<String, String> campi) {}

    @ExceptionHandler(DatiComunicazioneNonValidiException.class)
    public ResponseEntity<Errore> datiNonValidi(DatiComunicazioneNonValidiException ex) {
        return risposta(HttpStatus.BAD_REQUEST, CONTROLLA_CAMPI, ex.campi());
    }

    /**
     * Il server ferma la richiesta mentre arriva, prima delle regole, se supera uno dei limiti del modulo. Le parti
     * sono al massimo una per i campi più una per file: troppe parti vogliono dire troppi file. Ogni altro limite
     * superato riguarda la dimensione dei file.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Errore> moduloTroppoGrande(MultipartException ex) {
        String messaggio = causataDa(ex, FileCountLimitExceededException.class)
                ? "Si possono allegare al massimo " + RegolaAllegati.MASSIMO_ALLEGATI + " file"
                : "Ogni allegato può pesare al massimo 5 MB";
        return risposta(HttpStatus.BAD_REQUEST, CONTROLLA_CAMPI, Map.of(CampoComunicazione.ALLEGATI, messaggio));
    }

    /** Manca la parte con i campi del modulo: la pagina la manda sempre, quindi la richiesta non viene da lì. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Errore> parteMancante() {
        return risposta(HttpStatus.BAD_REQUEST, "Richiesta non valida", Map.of());
    }

    @ExceptionHandler(RisorsaComunicazioniNonTrovataException.class)
    public ResponseEntity<Errore> nonTrovata() {
        return risposta(HttpStatus.NOT_FOUND, "Comunicazione o allegato non trovati", Map.of());
    }

    private static boolean causataDa(Throwable errore, Class<? extends Throwable> tipo) {
        for (Throwable causa = errore; causa != null; causa = causa.getCause()) {
            if (tipo.isInstance(causa)) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<Errore> risposta(HttpStatus stato, String messaggio, Map<String, String> campi) {
        return ResponseEntity.status(stato).body(new Errore(stato.name(), messaggio, campi));
    }
}
