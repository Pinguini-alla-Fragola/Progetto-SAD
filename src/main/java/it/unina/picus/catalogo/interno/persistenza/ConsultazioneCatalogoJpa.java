package it.unina.picus.catalogo.interno.persistenza;

import it.unina.picus.catalogo.api.CatalogoApi.Comune;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Adattatore di lettura: restituisce DTO senza esporre le entità JPA. */
@Repository
@Transactional(readOnly = true)
public class ConsultazioneCatalogoJpa implements ConsultazioneCatalogoApi {
    private final EntityManager entityManager;

    public ConsultazioneCatalogoJpa(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Misura> elencoMisureAttive() {
        return entityManager.createQuery(
                "select m from MisuraJpa m where m.attiva = true order by m.codice", MisuraJpa.class)
                .getResultList().stream().map(MisuraJpa::aDto).toList();
    }

    @Override
    public Optional<Misura> cercaMisura(long misuraId) {
        return Optional.ofNullable(entityManager.find(MisuraJpa.class, misuraId)).map(MisuraJpa::aDto);
    }

    @Override
    public List<Comune> elencoComuni() {
        return entityManager.createQuery(
                "select c from ComuneJpa c order by lower(c.nome), c.siglaProvincia, c.codice", ComuneJpa.class)
                .getResultList().stream().map(ComuneJpa::aDto).toList();
    }

    @Override
    public Optional<Comune> cercaComune(String codice) {
        if (codice == null || codice.isBlank()) {
            return Optional.empty();
        }
        String pulito = codice.strip();
        ComuneJpa diretto = entityManager.find(ComuneJpa.class, pulito);
        if (diretto != null) {
            return Optional.of(diretto.aDto());
        }
        if (pulito.length() == 6 && pulito.chars().allMatch(Character::isDigit)) {
            return entityManager.createQuery(
                    "select c from ComuneJpa c where c.codiceIstat = :codiceIstat", ComuneJpa.class)
                    .setParameter("codiceIstat", pulito)
                    .getResultStream()
                    .findFirst()
                    .map(ComuneJpa::aDto);
        }
        return Optional.empty();
    }
}
