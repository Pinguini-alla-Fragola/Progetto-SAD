package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.UUID;

/**
 * Account che il Banco può scegliere per un invio a un gruppo.
 *
 * @param id identificativo dell'account
 * @param nome nome mostrato nell'elenco
 */
public record AccountSelezionabile(UUID id, String nome) {}
