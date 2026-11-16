package it.unina.picus.utenze.web;

import java.io.IOException;

import org.springframework.security.core.Authentication;
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
        if (auth != null && auth.getPrincipal() instanceof PrincipalePicus p
                && utenze.verificaAccountSessione(p.accountId()).isEmpty()) {
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
        chain.doFilter(request, response);
    }
}
