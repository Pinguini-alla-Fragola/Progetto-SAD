package it.unina.picus.utenze.interno.applicazione;

public class DatiOptNonValidiException extends RuntimeException {
    private final String campo;

    public DatiOptNonValidiException(String campo, String messaggio) {
        super(messaggio);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}