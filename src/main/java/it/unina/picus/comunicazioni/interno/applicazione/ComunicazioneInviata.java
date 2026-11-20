package it.unina.picus.comunicazioni.interno.applicazione;

import java.time.Instant;
import java.util.UUID;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;

/**
 * Esito dell'invio.
 *
 * @param id identificativo della comunicazione
 * @param titolo oggetto, se indicato
 * @param inviataIl istante dell'invio
 * @param tipo tipo di destinatari scelto
 * @param destinatari numero di account che l'hanno ricevuta
 */
public record ComunicazioneInviata(UUID id, String titolo, Instant inviataIl, TipoDestinatari tipo, int destinatari) {}
