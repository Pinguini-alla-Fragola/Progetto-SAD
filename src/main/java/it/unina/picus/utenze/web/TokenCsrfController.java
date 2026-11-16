package it.unina.picus.utenze.web;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fornisce al client il token CSRF associato alla sessione per le operazioni mutative. */
@RestController
class TokenCsrfController {

    /**
     * Espone il token CSRF nello stesso sito.
     *
     * @param token token della sessione corrente, fornito da Spring Security
     * @return token e nome dell'header richiesto
     */
    @GetMapping("/api/auth/csrf")
    CsrfToken token(CsrfToken token) {
        return token;
    }
}
