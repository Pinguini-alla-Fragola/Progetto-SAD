package it.unina.picus.utenze.interno.persistenza;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfiloOperatoreRepository extends JpaRepository<ProfiloOperatoreJpa, UUID> {
}
