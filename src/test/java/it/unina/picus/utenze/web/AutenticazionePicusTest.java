package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/* Verifica di {@link AutenticazionePicus} senza Spring e senza database (#25, #104): il provider deve
consultare il limitatore prima delle credenziali, azzerarlo dopo un accesso riuscito e costruire
un'identità di sessione senza credenziali. Oggetti reali ({@link LimitatoreAccessi} con orologio
fisso) e un fake di {@link UtenzeApi} scritto a mano.*/

@DisplayName("Provider di autenticazione PICUS")
class AutenticazionePicusTest {

    private static final String PASSWORD = "Password-di-prova-1";
    private static final String EMAIL = "opt@picus.test";
    private static final int MAX = LimitatoreAccessi.MAX_FALLIMENTI;

    private UtenzeFinte utenze;
    private AutenticazionePicus provider;

    @BeforeEach
    void prepara() {
        utenze = new UtenzeFinte(PASSWORD);
        Clock fermo = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        provider = new AutenticazionePicus(utenze, new LimitatoreAccessi(fermo));
    }

    // accesso riuscito

    @ParameterizedTest(name = "{0}: identità di sessione con ruolo e senza credenziali")
    @EnumSource(RuoloUtente.class)
    void accessoRiuscitoCostruisceLIdentitaDiSessione(RuoloUtente ruolo) {
        UtenzeApi.IdentitaAutenticata attesa = utenze.aggiungi(EMAIL, ruolo);
        UsernamePasswordAuthenticationToken richiesta = UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, PASSWORD);
        richiesta.setDetails("dettagli-della-richiesta");

        Authentication esito = provider.authenticate(richiesta);

        assertTrue(esito.isAuthenticated());
        PrincipalePicus principale = assertInstanceOf(PrincipalePicus.class, esito.getPrincipal());
        assertEquals(attesa.accountId(), principale.accountId());
        assertEquals(ruolo, principale.ruolo());
        assertEquals(List.of("ROLE_" + ruolo.name()), autorita(esito));
        assertNull(esito.getCredentials(), "la password non deve restare nell'autenticazione");
        assertEquals("dettagli-della-richiesta", esito.getDetails());
    }

    @Test
    @DisplayName("Credenziali errate: BadCredentialsException")
    void credenzialiErrate() {
        utenze.aggiungi(EMAIL, RuoloUtente.OPT);

        assertThrows(BadCredentialsException.class, () -> accedi(EMAIL, "sbagliata"));
        assertThrows(BadCredentialsException.class, () -> accedi("sconosciuta@picus.test", PASSWORD));
    }

    @Test
    @DisplayName("Credenziale assente: arriva null al modulo Utenze e l'esito è un errore di credenziali")
    void credenzialeAssente() {
        utenze.aggiungi(EMAIL, RuoloUtente.OPT);

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, null)));
        assertEquals(1, utenze.chiamate);
        assertNull(utenze.ultimaPassword);
    }

    // limite sui tentativi

    @Test
    @DisplayName("Raggiunto il limite, il tentativo successivo è bloccato senza verificare le credenziali")
    void bloccoPrimaDellaVerifica() {
        utenze.aggiungi(EMAIL, RuoloUtente.OPT);
        for (int i = 0; i < MAX; i++) {
            assertThrows(BadCredentialsException.class, () -> accedi(EMAIL, "sbagliata"));
        }
        int chiamatePrimaDelBlocco = utenze.chiamate;

        assertThrows(LockedException.class, () -> accedi(EMAIL, PASSWORD));
        assertEquals(chiamatePrimaDelBlocco, utenze.chiamate,
                "con il blocco attivo il confronto della password (BCrypt) non deve essere eseguito");
    }

    @Test
    @DisplayName("Il blocco vale anche per un'email inesistente, così non rivela quali account esistono")
    void bloccoAncheSenzaAccount() {
        for (int i = 0; i < MAX; i++) {
            assertThrows(BadCredentialsException.class, () -> accedi("nessuno@picus.test", "x"));
        }

        assertThrows(LockedException.class, () -> accedi("nessuno@picus.test", "x"));
    }

    @Test
    @DisplayName("Varianti con spazi e maiuscole della stessa email consumano lo stesso contatore")
    void variantiDellaStessaEmail() {
        utenze.aggiungi(EMAIL, RuoloUtente.OPT);
        String[] varianti = {EMAIL, " OPT@picus.test", "Opt@Picus.Test ", "opt@PICUS.TEST", "  opt@picus.test  "};
        for (int i = 0; i < MAX; i++) {
            String variante = varianti[i % varianti.length];
            assertThrows(BadCredentialsException.class, () -> accedi(variante, "sbagliata"));
        }

        assertThrows(LockedException.class, () -> accedi(EMAIL, PASSWORD));
    }

    @Test
    @DisplayName("Un accesso riuscito azzera il contatore di quell'email")
    void accessoRiuscitoAzzeraIlContatore() {
        utenze.aggiungi(EMAIL, RuoloUtente.OPT);
        for (int i = 0; i < MAX - 1; i++) {
            assertThrows(BadCredentialsException.class, () -> accedi(EMAIL, "sbagliata"));
        }
        assertTrue(accedi(EMAIL, PASSWORD).isAuthenticated());

        for (int i = 0; i < MAX; i++) {
            assertThrows(BadCredentialsException.class, () -> accedi(EMAIL, "sbagliata"),
                    "tentativo " + (i + 1) + " dopo l'azzeramento");
        }
        assertThrows(LockedException.class, () -> accedi(EMAIL, PASSWORD));
    }

    @Test
    @DisplayName("Il blocco di un'email non impedisce l'accesso a un'altra")
    void bloccoLimitatoAllEmail() {
        utenze.aggiungi(EMAIL, RuoloUtente.OPT);
        utenze.aggiungi("banco@picus.test", RuoloUtente.BANCO);
        for (int i = 0; i < MAX; i++) {
            assertThrows(BadCredentialsException.class, () -> accedi(EMAIL, "sbagliata"));
        }
        assertThrows(LockedException.class, () -> accedi(EMAIL, PASSWORD));

        assertTrue(accedi("banco@picus.test", PASSWORD).isAuthenticated());
    }

    // tipi di autenticazione gestiti

    @Test
    @DisplayName("Gestisce solo le richieste con email e password")
    void supportaSoloEmailEPassword() {
        assertTrue(provider.supports(UsernamePasswordAuthenticationToken.class));
        assertFalse(provider.supports(AnonymousAuthenticationToken.class));
        assertFalse(provider.supports(TestingAuthenticationToken.class));
    }

    // supporto

    private Authentication accedi(String email, String password) {
        return provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    }

    private static List<String> autorita(Authentication autenticazione) {
        return autenticazione.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    /*
    Fake manuale del modulo Utenze: una sola password valida, account attivi indicizzati per email
    normalizzata come fa UtenzeService. Conta le chiamate a verificaCredenziali.
     */
    private static final class UtenzeFinte implements UtenzeApi {

        private final String passwordValida;
        private final Map<String, IdentitaAutenticata> attivi = new HashMap<>();
        int chiamate;
        String ultimaPassword;

        UtenzeFinte(String passwordValida) {
            this.passwordValida = passwordValida;
        }

        IdentitaAutenticata aggiungi(String email, RuoloUtente ruolo) {
            IdentitaAutenticata identita = new IdentitaAutenticata(UUID.randomUUID(), ruolo);
            attivi.put(normalizza(email), identita);
            return identita;
        }

        @Override
        public Optional<IdentitaAutenticata> verificaCredenziali(String email, String password) {
            chiamate++;
            ultimaPassword = password;
            if (email == null || !passwordValida.equals(password)) {
                return Optional.empty();
            }
            return Optional.ofNullable(attivi.get(normalizza(email)));
        }

        private static String normalizza(String email) {
            return email.strip().toLowerCase(Locale.ROOT);
        }

        @Override
        public Optional<IdentitaAutenticata> verificaAccountSessione(UUID accountId) {
            throw new UnsupportedOperationException("non usato dal provider");
        }

        @Override
        public Optional<AccountSintetico> cercaAccount(UUID accountId) {
            throw new UnsupportedOperationException("non usato dal provider");
        }

        @Override
        public List<AccountSintetico> elencoAccountAttivi(RuoloUtente ruolo) {
            throw new UnsupportedOperationException("non usato dal provider");
        }

        @Override
        public Optional<ProfiloErogatore> cercaProfiloErogatoreAttivo(UUID accountId) {
            throw new UnsupportedOperationException("non usato dal provider");
        }

        @Override
        public List<ProfiloErogatore> elencoOptAttive(Set<UUID> accountIds) {
            throw new UnsupportedOperationException("non usato dal provider");
        }

        @Override
        public Optional<ProfiloErogatore> bloccaProfiloPerMisura(UUID accountId, long misuraId) {
            throw new UnsupportedOperationException("non usato dal provider");
        }

        @Override
        public List<ProfiloErogatore> elencoOperatoriAttiviPerMisura(long misuraId) {
            throw new UnsupportedOperationException("non usato dal provider");
        }
    }
}
