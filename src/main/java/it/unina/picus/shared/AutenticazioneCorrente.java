package it.unina.picus.shared;

import it.unina.picus.utenze.api.SessioniApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Traduce il principal verificato da Spring Security nell'identità corrente di PICUS. */
@Component
public class AutenticazioneCorrente {
    private final SessioniApi sessioni;

    public AutenticazioneCorrente(SessioniApi sessioni) {
        this.sessioni = sessioni;
    }

    public IdentitaAutenticata richiedi(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new SessioneNonValidaException();
        }
        UUID accountId;
        if (authentication.getPrincipal() instanceof IdentitaAutenticata identita) {
            accountId = identita.accountId();
        } else {
            try {
                accountId = UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ex) {
                throw new SessioneNonValidaException();
            }
        }
        // Il ruolo arriva dal database, anche quando il principal della sessione è precedente.
        return sessioni.verificaAccountSessione(accountId).orElseThrow(SessioneNonValidaException::new);
    }
}
