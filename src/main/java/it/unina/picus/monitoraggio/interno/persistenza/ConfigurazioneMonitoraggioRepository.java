package it.unina.picus.monitoraggio.interno.persistenza;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigurazioneMonitoraggioRepository extends JpaRepository<ConfigurazioneMonitoraggioJpa, Short> {
}