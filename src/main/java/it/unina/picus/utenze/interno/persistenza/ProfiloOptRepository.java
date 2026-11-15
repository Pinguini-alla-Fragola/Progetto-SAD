package it.unina.picus.utenze.interno.persistenza;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfiloOptRepository extends JpaRepository<ProfiloOptJpa, UUID> {
}
