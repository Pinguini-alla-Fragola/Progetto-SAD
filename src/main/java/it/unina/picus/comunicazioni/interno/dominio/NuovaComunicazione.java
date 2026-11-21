package it.unina.picus.comunicazioni.interno.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;

/**
 * Comunicazione pronta da registrare, con gli account che la ricevono e i suoi allegati. Gli account sono quelli
 * scelti al momento dell'invio: un account creato dopo non riceve le comunicazioni già inviate.
 *
 * @param id identificativo della comunicazione
 * @param mittenteId account del Banco che la invia
 * @param titolo oggetto facoltativo
 * @param contenuto testo della comunicazione
 * @param tipo tipo di destinatari scelto
 * @param inviataIl istante dell'invio
 * @param destinatari account che la ricevono, almeno uno
 * @param allegati allegati già controllati
 */
public record NuovaComunicazione(UUID id, UUID mittenteId, String titolo, String contenuto, TipoDestinatari tipo,
        Instant inviataIl, Set<UUID> destinatari, List<NuovoAllegato> allegati) {

    public NuovaComunicazione {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mittenteId, "mittenteId");
        Objects.requireNonNull(contenuto, "contenuto");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(inviataIl, "inviataIl");
        destinatari = Set.copyOf(destinatari);
        if (destinatari.isEmpty()) {
            throw new IllegalArgumentException("Una comunicazione ha almeno un destinatario");
        }
        allegati = List.copyOf(allegati);
    }
}
