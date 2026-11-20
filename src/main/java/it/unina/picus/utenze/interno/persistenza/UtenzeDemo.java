package it.unina.picus.utenze.interno.persistenza;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class UtenzeDemo implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final String password;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UtenzeDemo(JdbcTemplate jdbc, @Value("${PICUS_DEMO_PASSWORD}") String password) {
        this.jdbc = jdbc;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        creaAccount("banco@picus.test", "Banco Demo", "BANCO");
        creaOpt("opt-a@picus.test", "OPT A", "OPT-01", "napoli", "00000000001");
        creaOpt("opt-b@picus.test", "OPT B", "OPT-02", "pozzuoli", "00000000002");

        String operatore = "operatore@picus.test";
        creaAccount(operatore, "Operatore Demo", "OPERATORE");
        jdbc.update("""
                insert into utenze_profili_operatore (account_id, tipologia, comune_codice)
                select id, 'INTERNO_BANCO', 'napoli'
                from utenze_account where email = ? and ruolo = 'OPERATORE'
                on conflict (account_id) do nothing
                """, operatore);
    }

    private void creaOpt(String email, String nome, String codiceStruttura, String comune, String codiceFiscale) {
        creaAccount(email, nome, "OPT");
        jdbc.update("""
                insert into utenze_profili_opt (account_id, codice_struttura, comune_codice, codice_fiscale)
                select id, ?, ?, ?
                from utenze_account where email = ? and ruolo = 'OPT'
                on conflict (account_id) do nothing
                """, codiceStruttura, comune, codiceFiscale, email);
    }

    private void creaAccount(String email, String nome, String ruolo) {
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, ?, ?, 'ATTIVO')
                on conflict (email) do nothing
                """, UUID.randomUUID(), email, encoder.encode(password), nome, ruolo);
    }
}