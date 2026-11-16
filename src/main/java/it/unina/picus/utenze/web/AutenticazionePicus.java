package it.unina.picus.utenze.web;

import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Collega il form di login di Spring Security alla verifica del modulo Utenze, applicando il limite
 * sui tentativi. Funziona con qualunque implementazione di {@link UtenzeApi} (reale o stub).
 */
@Component
class AutenticazionePicus implements AuthenticationProvider {

    private final UtenzeApi utenze;
    private final LimitatoreAccessi limitatore;

    AutenticazionePicus(UtenzeApi utenze, LimitatoreAccessi limitatore) {
        this.utenze = utenze;
        this.limitatore = limitatore;
    }

    @Override
    public Authentication authenticate(Authentication richiesta) throws AuthenticationException {
        String email = richiesta.getName();
        Object credenziale = richiesta.getCredentials();
        String password = credenziale == null ? null : credenziale.toString();

        String chiave = LimitatoreAccessi.chiave(email);
        if (!limitatore.ammetti(chiave)) {
            throw new LockedException("Troppi tentativi"); // gestita come messaggio generico di attesa
        }

        IdentitaAutenticata identita = utenze.verificaCredenziali(email, password)
                .orElseThrow(() -> new BadCredentialsException("Credenziali non valide"));
        limitatore.azzera(chiave);

        var autenticata = UsernamePasswordAuthenticationToken.authenticated(
                new PrincipalePicus(identita.accountId(), identita.ruolo()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + identita.ruolo().name())));
        autenticata.setDetails(richiesta.getDetails());
        return autenticata;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
