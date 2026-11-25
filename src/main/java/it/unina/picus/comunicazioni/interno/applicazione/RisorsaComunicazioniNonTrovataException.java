package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.NoSuchElementException;

/** La comunicazione o l'allegato cercati non esistono. Rispetta il tipo previsto dal contratto del modulo. */
public class RisorsaComunicazioniNonTrovataException extends NoSuchElementException {

    /** Crea l'eccezione, senza riportare l'identificativo cercato. */
    public RisorsaComunicazioniNonTrovataException() {
        super("Comunicazione o allegato non trovati");
    }
}
