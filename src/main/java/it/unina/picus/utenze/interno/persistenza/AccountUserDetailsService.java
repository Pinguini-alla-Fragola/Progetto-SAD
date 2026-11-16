package it.unina.picus.utenze.interno.persistenza;

import jakarta.persistence.EntityManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Collega l'autenticazione Spring Security esistente agli account persistenti PICUS. */
@Service
@Transactional(readOnly = true)
public class AccountUserDetailsService implements UserDetailsService {
    private final EntityManager entityManager;

    public AccountUserDetailsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return entityManager.createQuery("select a from AccountJpa a where a.email = :email", AccountJpa.class)
                .setParameter("email", email)
                .getResultStream().findFirst().map(AccountJpa::credenziali)
                .orElseThrow(() -> new UsernameNotFoundException("Credenziali non valide"));
    }
}
