package it.unina.picus.monitoraggio.interno.persistenza;

import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoSchedaOperatore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adattatore JPA della fonte globale autorizzata del Banco per le categorie rendicontabili
 * delle schede personali TM-OP-01.
 */
@Repository
@Profile("!skeleton")
public class CategorieSchedeJpa implements CategorieSchedeRendicontabili {

    private final EntityManager entita;

    /**
     * Collega l'adattatore al contesto di persistenza condiviso.
     *
     * @param entita gestore JPA
     */
    public CategorieSchedeJpa(EntityManager entita) {
        this.entita = entita;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<Set<String>> codiciAmmessi() {
        List<Tuple> righe = entita.createQuery(
                        "select c.codice as codice, c.attiva as attiva from CategoriaSchedaJpa c", Tuple.class)
                .getResultList();
        if (righe.isEmpty()) {
            return Optional.empty();
        }
        Set<String> attivi = new HashSet<>();
        for (Tuple riga : righe) {
            if (Boolean.TRUE.equals(riga.get("attiva", Boolean.class))) {
                String codice = riga.get("codice", String.class);
                if (TracciatoSchedaOperatore.codiceCategoriaValido(codice)) {
                    attivi.add(codice);
                }
            }
        }
        return Optional.of(Set.copyOf(attivi));
    }
}
