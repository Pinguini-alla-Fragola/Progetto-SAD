package it.unina.picus.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Configurazione tecnica della sicurezza web. Non importa nulla dai moduli applicativi:
 * il bean AuthenticationProvider (modulo utenze) viene individuato da Spring Security da solo.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "img-src 'self' data:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /** Messaggio generico di attesa solo per il blocco temporaneo, messaggio generico di errore per il resto. */
    private static final AuthenticationFailureHandler ESITO_NEGATIVO = (request, response, eccezione) ->
            response.sendRedirect(request.getContextPath()
                    + (eccezione instanceof LockedException ? "/login?attesa" : "/login?error"));

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                        // ogni ruolo vede solo la propria area
                        .requestMatchers("/banco/**").hasRole("BANCO")
                        .requestMatchers("/opt/**").hasRole("OPT")
                        .requestMatchers("/operatore/**").hasRole("OPERATORE")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .failureHandler(ESITO_NEGATIVO)
                        // false: dopo il login torna alla pagina richiesta in origine, altrimenti "/"
                        .defaultSuccessUrl("/", false)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout") // solo POST, protetto da CSRF
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicy.NO_REFERRER)));
        // CSRF attivo di default, session fixation: la sessione viene rigenerata dopo il login.
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
