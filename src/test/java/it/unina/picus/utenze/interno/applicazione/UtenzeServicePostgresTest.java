package it.unina.picus.utenze.interno.applicazione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.unina.picus.utenze.web.IntegrazionePostgresBase;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/*
Verifica delle credenziali e delle sessioni nel modulo Utenze (#25, #104) su PostgreSQL reale,
senza mock: il bean {@link UtenzeApi} è {@code UtenzeService} con il repository JPA vero.
Riprende i casi del vecchio {@code UtenzeServiceTest} (commit {@code f404896}), adattati al backend attuale.
Gli hash degli account di prova usano BCrypt a costo 4 per velocità: il servizio li aggiorna al
costo di produzione al primo accesso riuscito, e un test lo verifica.
*/
@DisplayName("Verifica delle credenziali nel modulo Utenze su PostgreSQL")
class UtenzeServicePostgresTest extends IntegrazionePostgresBase {

    private static final String PASSWORD = "Password-di-prova-1";
    private static final BCryptPasswordEncoder BCRYPT_VELOCE = new BCryptPasswordEncoder(4);

    @Autowired
    private UtenzeApi utenze;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;

    private record Account(UUID id, String email) { }

    // credenziali

    @Test
    @DisplayName("Credenziali corrette: identità con ID e ruolo, anche con email con spazi e maiuscole")
    void credenzialiCorrette() {
        Account account = nuovoAccount("BANCO", "ATTIVO", PASSWORD);

        Optional<IdentitaAutenticata> esito =
                utenze.verificaCredenziali("  " + account.email().toUpperCase(Locale.ROOT) + " ", PASSWORD);

        assertTrue(esito.isPresent());
        assertEquals(account.id(), esito.get().accountId());
        assertEquals(RuoloUtente.BANCO, esito.get().ruolo());
    }

    @Test
    @DisplayName("Password errata o con maiuscole diverse: nessuna identità")
    void passwordErrata() {
        Account account = nuovoAccount("OPT", "ATTIVO", PASSWORD);

        assertTrue(utenze.verificaCredenziali(account.email(), "sbagliata").isEmpty());
        assertTrue(utenze.verificaCredenziali(account.email(), PASSWORD.toUpperCase(Locale.ROOT)).isEmpty());
    }

    @ParameterizedTest(name = "account {0} con password corretta: nessuna identità")
    @ValueSource(strings = {"REVOCATO", "IN_ATTESA_DI_ATTIVAZIONE"})
    void accountNonAttivo(String stato) {
        Account account = nuovoAccount("OPT", stato, PASSWORD);

        assertTrue(utenze.verificaCredenziali(account.email(), PASSWORD).isEmpty());
    }

    @Test
    @DisplayName("Email sconosciuta, vuota, troppo lunga o password assente: nessuna identità e nessuna eccezione")
    void inputNonValidi() {
        Account account = nuovoAccount("OPT", "ATTIVO", PASSWORD);

        assertTrue(utenze.verificaCredenziali("sconosciuta-" + UUID.randomUUID() + "@picus.test", PASSWORD).isEmpty());
        assertTrue(utenze.verificaCredenziali(null, PASSWORD).isEmpty());
        assertTrue(utenze.verificaCredenziali("   ", PASSWORD).isEmpty());
        assertTrue(utenze.verificaCredenziali("a".repeat(250) + "@picus.test", PASSWORD).isEmpty());
        assertTrue(utenze.verificaCredenziali(account.email(), null).isEmpty());
        assertTrue(utenze.verificaCredenziali(account.email(), "").isEmpty());
    }

    // limite di 72 byte di BCrypt

    @Test
    @DisplayName("Password di esattamente 72 byte accettata")
    void passwordDi72Byte() {
        String password = "a".repeat(UtenzeService.LUNGHEZZA_MAX_PASSWORD_BYTE);
        Account account = nuovoAccount("OPT", "ATTIVO", password);

        assertTrue(utenze.verificaCredenziali(account.email(), password).isPresent());
    }

    @Test
    @DisplayName("Oltre i 72 byte la password è respinta anche se i primi 72 coincidono")
    void passwordOltre72Byte() {
        String valida = "a".repeat(UtenzeService.LUNGHEZZA_MAX_PASSWORD_BYTE);
        Account account = nuovoAccount("OPT", "ATTIVO", valida);

        // BCrypt ignorerebbe il byte in più: il limite deve respingerla prima del confronto.
        assertTrue(utenze.verificaCredenziali(account.email(), valida + "b").isEmpty());
    }

    @Test
    @DisplayName("Il limite si misura in byte UTF-8, non in caratteri")
    void limiteInByteUtf8() {
        String multibyte = "è".repeat(36); // 36 caratteri, 72 byte
        Account account = nuovoAccount("OPT", "ATTIVO", multibyte);

        assertTrue(utenze.verificaCredenziali(account.email(), multibyte).isPresent());
        assertTrue(utenze.verificaCredenziali(account.email(), multibyte + "x").isEmpty());
        assertTrue(utenze.verificaCredenziali(account.email(), "è".repeat(37)).isEmpty());
    }

    // aggiornamento dell'hash

    @Test
    @DisplayName("Un accesso riuscito aggiorna un hash con costo inferiore a quello di produzione")
    void hashAggiornatoDopoAccessoRiuscito() {
        Account account = nuovoAccount("OPT", "ATTIVO", PASSWORD);
        String vecchio = hashDi(account);
        assertTrue(encoder.upgradeEncoding(vecchio), "premessa: l'hash di prova ha costo inferiore");

        assertTrue(utenze.verificaCredenziali(account.email(), PASSWORD).isPresent());

        String nuovo = hashDi(account);
        assertNotEquals(vecchio, nuovo);
        assertFalse(encoder.upgradeEncoding(nuovo), "il nuovo hash deve avere il costo di produzione");
        assertTrue(encoder.matches(PASSWORD, nuovo));
    }

    @Test
    @DisplayName("Un accesso fallito non modifica l'hash")
    void hashInvariatoDopoAccessoFallito() {
        Account account = nuovoAccount("OPT", "ATTIVO", PASSWORD);
        String vecchio = hashDi(account);

        assertTrue(utenze.verificaCredenziali(account.email(), "sbagliata").isEmpty());

        assertEquals(vecchio, hashDi(account));
    }

    // verifica della sessione

    @Test
    @DisplayName("Sessione di un account attivo: identità corrente; dopo la revoca: nessuna identità")
    void verificaAccountSessione() {
        Account account = nuovoAccount("OPERATORE", "ATTIVO", PASSWORD);

        Optional<IdentitaAutenticata> attivo = utenze.verificaAccountSessione(account.id());
        assertTrue(attivo.isPresent());
        assertEquals(RuoloUtente.OPERATORE, attivo.get().ruolo());

        jdbc.update("update utenze_account set stato = 'REVOCATO' where id = ?", account.id());

        assertTrue(utenze.verificaAccountSessione(account.id()).isEmpty());
    }

    @Test
    @DisplayName("Sessione con identificativo sconosciuto o assente: nessuna identità")
    void verificaAccountSessioneSconosciuto() {
        assertTrue(utenze.verificaAccountSessione(UUID.randomUUID()).isEmpty());
        assertTrue(utenze.verificaAccountSessione(null).isEmpty());
    }

    // supporto

    private Account nuovoAccount(String ruolo, String stato, String password) {
        UUID id = UUID.randomUUID();
        String email = "t-" + id + "@picus.test";
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, ?, ?, ?)
                """, id, email, BCRYPT_VELOCE.encode(password), "Account di prova", ruolo, stato);
        return new Account(id, email);
    }

    private String hashDi(Account account) {
        return jdbc.queryForObject("select password_hash from utenze_account where id = ?", String.class, account.id());
    }
}