package it.unina.picus.utenze.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Permette ai controller di tutti i moduli di dichiarare un parametro {@link IdentitaAutenticata}:
 * l'attore è sempre quello della sessione, mai un valore inviato dal client.
 */
@Configuration
class IdentitaResolver implements WebMvcConfigurer, HandlerMethodArgumentResolver {

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof PrincipalePicus p) {
            return new IdentitaAutenticata(p.accountId(), p.ruolo());
        }
        throw new AccessDeniedException("Sessione non autenticata");
    }
}
