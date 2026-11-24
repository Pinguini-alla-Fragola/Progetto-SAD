package it.unina.picus.shared;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import tools.jackson.databind.json.JsonMapper;

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

    private static final PathPatternRequestMatcher USCITA_API =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout");
    private static final PathPatternRequestMatcher USCITA_FORM =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/logout");

    /** Messaggio generico di attesa solo per il blocco temporaneo, messaggio generico di errore per il resto. */
    private static final AuthenticationFailureHandler ESITO_NEGATIVO = (request, response, eccezione) ->
            response.sendRedirect(request.getContextPath()
                    + (eccezione instanceof LockedException ? "/login?attesa" : "/login?error"));

    /**
     * Senza autenticazione valida: le pagine HTML rimandano al login (302), le API ricevono 401
     * senza redirect (vedi {@link RichiestaApi}).
     */
    private static final AuthenticationEntryPoint NON_AUTENTICATO = (request, response, eccezione) -> {
        if (RichiestaApi.e(request)) {
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED).commence(request, response, eccezione);
        } else {
            new LoginUrlAuthenticationEntryPoint("/login").commence(request, response, eccezione);
        }
    };

    /**
     * Accesso negato dopo il controllo CSRF. Senza sessione valida una API riceve 401 anche se manca il
     * token CSRF (che senza sessione non può esistere); con sessione valida il token mancante o un
     * ruolo non abilitato danno 403, come per le pagine HTML.
     */
    private static final AccessDeniedHandler ACCESSO_NEGATO = (request, response, eccezione) -> {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean senzaSessione = auth == null || auth instanceof AnonymousAuthenticationToken
                || !auth.isAuthenticated();
        if (RichiestaApi.e(request)) {
            response.setStatus((senzaSessione ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN).value());
        } else {
            new AccessDeniedHandlerImpl().handle(request, response, eccezione);
        }
    };

    /** Il controllo sessione vive solo nella catena di sicurezza: niente seconda registrazione come filtro servlet. */
    @Bean
    FilterRegistrationBean<ControlloSessione> controlloSessioneSoloNellaCatena(ControlloSessione controllo) {
        FilterRegistrationBean<ControlloSessione> registrazione = new FilterRegistrationBean<>(controllo);
        registrazione.setEnabled(false);
        return registrazione;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ControlloSessione controlloSessione,
                                            JsonMapper json) throws Exception {
        http
                // subito dopo il caricamento della sessione: prima di CSRF e dei permessi di ruolo
                .addFilterAfter(controlloSessione, SecurityContextHolderFilter.class)
                .sessionManagement(Customizer.withDefaults())
                .with(new LoginJsonConfigurer(json), Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/recupera", "/attiva").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/attiva", "/api/auth/attiva/**", "/api/auth/recupera").permitAll()
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/img/**", "/fonts/**", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/opt", "/api/opt/*/revoca", "/api/opt/*/reinvia-invito", "/api/operatori/*/revoca").hasRole("BANCO")
                        .requestMatchers(HttpMethod.POST, "/api/richieste").hasRole("OPT")
                        .requestMatchers(HttpMethod.GET, "/api/richieste/mie").hasRole("OPT")
                        .requestMatchers(HttpMethod.POST, "/api/opt/importazioni").hasRole("BANCO")
                        .requestMatchers(HttpMethod.GET, "/api/contatti-disponibilita/*").hasAnyRole("OPT", "OPERATORE")
                        .requestMatchers(HttpMethod.POST, "/api/contatti-disponibilita/*/risposta").hasAnyRole("OPT", "OPERATORE")
                        .requestMatchers(HttpMethod.POST, "/api/richieste/*/presa-in-carico",
                                "/api/richieste/*/contatti", "/api/contatti-disponibilita/*/assegnazione",
                                "/api/richieste/*/conferma", "/api/richieste/*/rifiuta").hasRole("BANCO")
                        .requestMatchers(HttpMethod.GET, "/api/richieste/coda", "/api/richieste/*/candidati",
                                "/api/richieste/*/contatti", "/api/richieste/*/contatti/*").hasRole("BANCO")
                        // Monitoraggio conteggi trimestrali OPT (#43)
                        .requestMatchers(HttpMethod.POST, "/api/monitoraggio/conteggi-opt").hasRole("OPT")
                        .requestMatchers(HttpMethod.GET, "/api/monitoraggio/conteggi-opt").hasAnyRole("OPT", "BANCO")
                        .requestMatchers(HttpMethod.GET, "/api/monitoraggio/categorie").hasAnyRole("OPT", "BANCO")
                        .requestMatchers(HttpMethod.POST, "/api/utenze/operatori").hasRole("BANCO")
                        .requestMatchers(HttpMethod.GET, "/api/catalogo/offerte-opt/*").hasAnyRole("OPT", "BANCO")
                        .requestMatchers(HttpMethod.HEAD, "/api/catalogo/offerte-opt/*").hasAnyRole("OPT", "BANCO")
                        .requestMatchers("/api/catalogo/offerte-opt/misure/*").hasRole("OPT")
                        .requestMatchers(HttpMethod.GET, "/api/catalogo/misure/tutte").hasRole("BANCO")
                        .requestMatchers(HttpMethod.POST, "/api/catalogo/misure",
                                "/api/catalogo/misure/*/disattivazione").hasRole("BANCO")
                        .requestMatchers(HttpMethod.PUT, "/api/catalogo/misure/*").hasRole("BANCO")
                        // Comunicazioni: il ruolo si controlla prima di leggere il modulo, allegati compresi.
                        .requestMatchers(HttpMethod.POST, "/api/comunicazioni").hasRole("BANCO")
                        .requestMatchers(HttpMethod.GET, "/api/comunicazioni/inviate",
                                "/api/comunicazioni/destinatari").hasRole("BANCO")
                        .requestMatchers(HttpMethod.GET, "/api/comunicazioni/ricevute").hasAnyRole("OPT", "OPERATORE")
                        // ogni ruolo vede solo la propria area
                        .requestMatchers("/banco/**").hasRole("BANCO")
                        .requestMatchers("/opt/**").hasRole("OPT")
                        .requestMatchers("/operatore/**").hasRole("OPERATORE")
                        .requestMatchers("/api/monitoraggio/schede-operatore",
                                "/api/monitoraggio/schede-operatore/modulo",
                                "/api/monitoraggio/schede-operatore/mie").hasRole("OPERATORE")
                        .requestMatchers("/api/monitoraggio/schede-operatore/ricevute",
                                "/api/monitoraggio/schede-operatore/ricevute/riepilogo").hasRole("BANCO")
                        .requestMatchers("/api/monitoraggio/schede-operatore/*").hasAnyRole("BANCO", "OPERATORE")
                        .anyRequest().authenticated())
                .exceptionHandling(eccezioni -> eccezioni
                        .authenticationEntryPoint(NON_AUTENTICATO)
                        .accessDeniedHandler(ACCESSO_NEGATO))
                .formLogin(form -> form
                        .loginPage("/login")
                        .failureHandler(ESITO_NEGATIVO)
                        // false: dopo il login torna alla pagina richiesta in origine, altrimenti "/"
                        .defaultSuccessUrl("/", false)
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(new OrRequestMatcher(USCITA_API, USCITA_FORM))
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (USCITA_API.matches(request)) {
                                response.setStatus(HttpStatus.NO_CONTENT.value());
                            } else {
                                response.sendRedirect(request.getContextPath() + "/login?logout");
                            }
                        })
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