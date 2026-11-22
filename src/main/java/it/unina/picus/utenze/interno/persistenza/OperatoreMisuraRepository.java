package it.unina.picus.utenze.interno.persistenza;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatoreMisuraRepository extends JpaRepository<OperatoreMisuraJpa, OperatoreMisuraJpa.Chiave> {
}
