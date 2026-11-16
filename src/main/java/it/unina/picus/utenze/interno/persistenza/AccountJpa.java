package it.unina.picus.utenze.interno.persistenza;

import java.util.UUID;
import java.util.Optional;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "utenze_account")
public class AccountJpa {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String email;
    private String passwordHash;
    private String nome;
    private String ruolo;
    private String stato;

    protected AccountJpa() {
    }

    Optional<IdentitaAutenticata> identitaAttiva() {
        if (!"ATTIVO".equals(stato)) {
            return Optional.empty();
        }
        return Optional.of(new IdentitaAutenticata(id, RuoloUtente.valueOf(ruolo)));
    }

    UserDetails credenziali() {
        return User.withUsername(id.toString())
                .password("{bcrypt}" + passwordHash)
                .roles(ruolo)
                .disabled(!"ATTIVO".equals(stato))
                .build();
    }
}
