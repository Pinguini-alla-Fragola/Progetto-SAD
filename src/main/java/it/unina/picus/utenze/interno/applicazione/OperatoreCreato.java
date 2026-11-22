package it.unina.picus.utenze.interno.applicazione;

import java.util.UUID;

/** Esito della creazione: l'account nasce sempre in attesa di attivazione. */
public record OperatoreCreato(UUID id, String nomeVisualizzato, String email) {}
