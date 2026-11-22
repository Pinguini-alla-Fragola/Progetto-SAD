package it.unina.picus.utenze.interno.applicazione;

public class TokenAttivazioneNonValidoException extends RuntimeException {
    public TokenAttivazioneNonValidoException(String messaggio) {
        super(messaggio);
    }
}