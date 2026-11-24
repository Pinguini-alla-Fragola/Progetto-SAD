package it.unina.picus.utenze.interno.applicazione;

/** Indica che il file CSV delle OPT non può essere elaborato. */
public class CsvOptNonValidoException extends RuntimeException {

    /**
     * Descrive un errore che impedisce l'importazione dell'intero file.
     *
     * @param messaggio descrizione da restituire al Banco
     */
    public CsvOptNonValidoException(String messaggio) {
        super(messaggio);
    }

    /**
     * Conserva la causa tecnica senza includerla nel messaggio restituito al Banco.
     *
     * @param causa errore di lettura o di sintassi
     */
    public CsvOptNonValidoException(Throwable causa) {
        super("Il file deve essere un CSV UTF-8 leggibile.", causa);
    }
}
