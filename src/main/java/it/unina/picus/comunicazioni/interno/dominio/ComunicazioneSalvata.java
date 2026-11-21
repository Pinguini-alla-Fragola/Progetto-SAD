package it.unina.picus.comunicazioni.interno.dominio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;

/**
 * Comunicazione già inviata, come la vede chi la consulta.
 *
 * @param id identificativo della comunicazione
 * @param titolo oggetto, se indicato
 * @param contenuto testo della comunicazione
 * @param tipo tipo di destinatari scelto
 * @param inviataIl istante dell'invio
 * @param allegati allegati, senza il loro contenuto
 * @param destinatari numero di account che l'hanno ricevuta
 * @param confermataIl quando chi la consulta ne ha confermato la ricezione; vuoto se non l'ha ancora fatto
 *                     o se chi la consulta non è un destinatario
 */
public record ComunicazioneSalvata(UUID id, String titolo, String contenuto, TipoDestinatari tipo, Instant inviataIl,
        List<InfoAllegato> allegati, int destinatari, Instant confermataIl) {

    public ComunicazioneSalvata {
        allegati = List.copyOf(allegati);
    }
}
