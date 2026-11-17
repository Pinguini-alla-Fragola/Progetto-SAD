package it.unina.picus.utenze.web;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import it.unina.picus.shared.ControlloSessione;
import it.unina.picus.shared.RichiestaApi;
import it.unina.picus.utenze.api.UtenzeApi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * A ogni richiesta conferma che l'account della sessione esista ancora e sia attivo: una revoca
 * chiude la sessione alla richiesta successiva (302 al login per le pagine HTML, 401 per le API,
 * come in {@link RichiestaApi}). Fa parte della catena di Spring Security, subito dopo il caricamento
 * della sessione e prima di CSRF e dei permessi di ruolo: una sessione revocata riceve lo stesso esito
 * qualunque area o metodo la prima richiesta usi.
 */
@Component
class VerificaSessioneFilter extends OncePerRequestFilter implements ControlloSessione {

    private final UtenzeApi utenze;

    VerificaSessioneFilter(UtenzeApi utenze) {
        this.utenze = utenze;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        var corrente = auth != null && auth.getPrincipal() instanceof PrincipalePicus p
                ? utenze.verificaAccountSessione(p.accountId()) : null;
        if (corrente != null && corrente.isEmpty()) {
            SecurityContextHolder.clearContext();
            HttpSession sessione = request.getSession(false);
            if (sessione != null) {
                sessione.invalidate();
            }
            if (RichiestaApi.e(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // API: 401, nessun redirect
            } else {
                response.sendRedirect(request.getContextPath() + "/login?error");
            }
            return;
        }
        if (corrente != null) {
            var identita = corrente.orElseThrow();
            request.setAttribute(IdentitaResolver.ATTRIBUTO_IDENTITA, identita);
            // I matcher di sicurezza devono vedere il ruolo corrente prima del binding HTTP.
            var aggiornata = UsernamePasswordAuthenticationToken.authenticated(
                    new PrincipalePicus(identita.accountId(), identita.ruolo()), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + identita.ruolo().name())));
            aggiornata.setDetails(auth.getDetails());
            SecurityContextHolder.getContext().setAuthentication(aggiornata);
        }
        chain.doFilter(request, response);
    }
}
