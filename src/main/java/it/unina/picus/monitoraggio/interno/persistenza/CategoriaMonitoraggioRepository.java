package it.unina.picus.monitoraggio.interno.persistenza;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaMonitoraggioRepository extends JpaRepository<CategoriaMonitoraggioJpa, String> {
    List<CategoriaMonitoraggioJpa> findByAttivaTrueOrderByCodiceAsc();
}