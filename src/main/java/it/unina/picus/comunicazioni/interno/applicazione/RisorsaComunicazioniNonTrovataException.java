package it.unina.picus.comunicazioni.interno.applicazione;

/** La comunicazione o l'allegato cercati non esistono. */
public class RisorsaComunicazioniNonTrovataException extends RuntimeException {

    /** Crea l'eccezione, senza riportare l'identificativo cercato. */
    public RisorsaComunicazioniNonTrovataException() {
        super("Comunicazione o allegato non trovati");
    }
}
