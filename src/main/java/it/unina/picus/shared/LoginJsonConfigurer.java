package it.unina.picus.shared;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Collega il login JSON alla stessa autenticazione del form. Il filtro di Spring Security applica
 * il <em>Template Method</em> per gestire autenticazione, cambio di sessione e salvataggio del
 * contesto. Il convertitore e i gestori personalizzano il formato HTTP.
 */
final class LoginJsonConfigurer extends AbstractHttpConfigurer<LoginJsonConfigurer, HttpSecurity> {

    private static final String ERRORE_GENERICO = "Credenziali non valide";
    private final JsonMapper json;

    LoginJsonConfigurer(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void configure(HttpSecurity http) {
        var percorso = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login");
        var filtro = new AbstractAuthenticationProcessingFilter(
                percorso, http.getSharedObject(AuthenticationManager.class)) { };
        filtro.setAuthenticationConverter(this::leggiCredenziali);
        filtro.setSessionAuthenticationStrategy(http.getSharedObject(SessionAuthenticationStrategy.class));
        filtro.setSecurityContextRepository(http.getSharedObject(SecurityContextRepository.class));
        filtro.setAuthenticationSuccessHandler((request, response, authentication) ->
                response.setStatus(HttpStatus.NO_CONTENT.value()));
        filtro.setAuthenticationFailureHandler(this::scriviErrore);
        http.addFilterBefore(postProcess(filtro), UsernamePasswordAuthenticationFilter.class);
    }

    private Authentication leggiCredenziali(HttpServletRequest request) {
        try {
            String contentType = request.getContentType();
            if (contentType == null
                    || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) {
                throw new BadCredentialsException(ERRORE_GENERICO);
            }
            var credenziali = json.readValue(request.getInputStream(), Credenziali.class);
            if (credenziali == null) {
                throw new BadCredentialsException(ERRORE_GENERICO);
            }
            return UsernamePasswordAuthenticationToken.unauthenticated(credenziali.email(), credenziali.password());
        } catch (IOException | JacksonException | InvalidMediaTypeException e) {
            throw new BadCredentialsException(ERRORE_GENERICO, e);
        }
    }

    private void scriviErrore(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException errore) throws IOException {
        boolean bloccato = errore instanceof LockedException;
        response.setStatus(bloccato ? HttpStatus.TOO_MANY_REQUESTS.value() : HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), Map.of("errore",
                bloccato ? "Accesso temporaneamente bloccato. Riprova tra poco." : ERRORE_GENERICO));
    }

    private record Credenziali(String email, String password) {}
}
