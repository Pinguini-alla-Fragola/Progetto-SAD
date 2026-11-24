package it.unina.picus.catalogo.interno.applicazione;

/** La misura selezionata non appartiene al catalogo attivo delle misure. */
public final class MisuraNonOffribileException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** Crea l'errore di validazione per una misura sconosciuta o disattivata. */
    public MisuraNonOffribileException() {
        super("Seleziona una misura attiva del catalogo");
    }
}
