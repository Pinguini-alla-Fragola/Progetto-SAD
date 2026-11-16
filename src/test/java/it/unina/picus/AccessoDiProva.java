package it.unina.picus;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fa accedere gli account di {@link DatiDiProva} con il login reale dell'applicazione.
 *
 * La password è generata a ogni esecuzione e non compare nel codice. Il suo hash è calcolato
 * una sola volta con il {@link PasswordEncoder} dell'applicazione, perché BCrypt è lento di
 * proposito, e viene assegnato all'account subito prima dell'accesso.
 */
public final class AccessoDiProva {

    private static final String PASSWORD = "prova-" + UUID.randomUUID();
    private static String hashCondiviso;

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AccessoDiProva(MockMvc mvc, JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    /** Nuova sessione autenticata per l'account: ogni chiamata esegue un login distinto. */
    public SessioneDiProva accedi(UUID account) throws Exception {
        jdbc.update("update utenze_account set password_hash = ? where id = ?", hash(), account);
        return SessioneDiProva.accedi(mvc, DatiDiProva.emailDi(account), PASSWORD);
    }

    private synchronized String hash() {
        if (hashCondiviso == null) {
            hashCondiviso = encoder.encode(PASSWORD);
        }
        return hashCondiviso;
    }
}
