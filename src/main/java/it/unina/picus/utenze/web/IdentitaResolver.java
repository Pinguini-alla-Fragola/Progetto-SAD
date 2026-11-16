package it.unina.picus.utenze.web;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi;

/**
 * Permette ai controller di tutti i moduli di dichiarare un parametro {@link IdentitaAutenticata}:
 * l'attore è sempre quello della sessione, mai un valore inviato dal client.
 */
@Configuration
class IdentitaResolver implements WebMvcConfigurer, HandlerMethodArgumentResolver {
    static final String ATTRIBUTO_IDENTITA = IdentitaResolver.class.getName() + ".identita";
    private final UtenzeApi utenze;

    IdentitaResolver(UtenzeApi utenze) {
        this.utenze = utenze;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return IdentitaAutenticata.class.equals(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer contenitore,
            NativeWebRequest richiesta, WebDataBinderFactory binder) {
        Object verificata = richiesta.getAttribute(ATTRIBUTO_IDENTITA, RequestAttributes.SCOPE_REQUEST);
        if (verificata instanceof IdentitaAutenticata identita) {
            return identita;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            UUID accountId;
            try {
                accountId = UUID.fromString(auth.getName());
            } catch (IllegalArgumentException ex) {
                throw new AuthenticationCredentialsNotFoundException("Sessione non valida");
            }
            return utenze.verificaAccountSessione(accountId)
                    .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Sessione non valida"));
        }
        throw new AuthenticationCredentialsNotFoundException("Sessione non autenticata");
    }
}
