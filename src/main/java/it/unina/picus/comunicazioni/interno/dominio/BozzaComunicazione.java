package it.unina.picus.comunicazioni.interno.dominio;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;

/**
 * Comunicazione che il Banco vuole inviare, con i campi già ripuliti. Un testo vuoto vale {@code null}, come un
 * tipo di destinatari sconosciuto; {@code letturaGruppo} dice se gli account del gruppo sono stati letti.
 *
 * @param titolo oggetto facoltativo
 * @param contenuto testo della comunicazione
 * @param tipo tipo di destinatari scelto
 * @param gruppo account scelti dal Banco, senza ripetizioni
 * @param letturaGruppo esito della lettura del gruppo
 * @param allegati file allegati, ancora da controllare
 */
public record BozzaComunicazione(String titolo, String contenuto, TipoDestinatari tipo, Set<UUID> gruppo,
        LetturaGruppo letturaGruppo, List<AllegatoRicevuto> allegati) {

    public BozzaComunicazione {
        gruppo = Set.copyOf(gruppo);
        Objects.requireNonNull(letturaGruppo, "letturaGruppo");
        allegati = List.copyOf(allegati);
    }
}
