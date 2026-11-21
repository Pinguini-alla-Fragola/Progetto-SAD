package it.unina.picus.comunicazioni.interno.dominio;

import java.util.UUID;

/**
 * Dati di un allegato da mostrare in un elenco, senza il contenuto del file.
 *
 * @param id identificativo dell'allegato
 * @param nomeFile nome del file
 * @param tipo tipo del file
 * @param dimensioneByte dimensione in byte
 */
public record InfoAllegato(UUID id, String nomeFile, TipoAllegato tipo, long dimensioneByte) {}
