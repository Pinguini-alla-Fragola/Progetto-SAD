package it.unina.picus.utenze.interno.dominio;

/**
 * La registrazione è fallita perché, nello stesso momento, un'altra ha salvato lo stesso valore
 * in un campo che deve essere unico, come l'email o il codice fiscale.
 */
public class RegistrazioneInConflittoException extends RuntimeException {

    private final String campo;

    /**
     * Crea l'eccezione per il campo che ha causato il conflitto.
     *
     * @param campo nome del campo duplicato, tra quelli di {@link CampoOperatore}
     * @param causa errore del database che ha rivelato il conflitto
     */
    public RegistrazioneInConflittoException(String campo, Throwable causa) {
        super("Valore già registrato nel campo " + campo, causa);
        this.campo = campo;
    }

    /**
     * Indica quale campo è risultato duplicato.
     *
     * @return nome del campo, tra quelli di {@link CampoOperatore}
     */
    public String campo() {
        return campo;
    }
}
