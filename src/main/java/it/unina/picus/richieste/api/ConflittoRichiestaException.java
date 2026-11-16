package it.unina.picus.richieste.api;

/** Comando applicativo incompatibile con lo stato o con un invio già registrato. */
public class ConflittoRichiestaException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /**
     * Crea il conflitto con un messaggio controllato.
     * @param messaggio motivo privo dei dati ricevuti
     */
    public ConflittoRichiestaException(String messaggio) {
        super(messaggio);
    }
}
