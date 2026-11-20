package it.unina.picus.comunicazioni.interno.dominio;

import java.util.Set;
import java.util.UUID;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;

/**
 * Comunicazione che il Banco vuole inviare, con i campi già ripuliti. Un testo vuoto vale {@code null}, come un
 * tipo di destinatari sconosciuto; {@code gruppoLeggibile} è falso se un account del gruppo non è riconoscibile.
 *
 * @param titolo oggetto facoltativo
 * @param contenuto testo della comunicazione
 * @param tipo tipo di destinatari scelto
 * @param gruppo account scelti dal Banco, senza ripetizioni
 * @param gruppoLeggibile falso se almeno un identificativo del gruppo non è valido
 */
public record BozzaComunicazione(String titolo, String contenuto, TipoDestinatari tipo, Set<UUID> gruppo,
        boolean gruppoLeggibile) {

    public BozzaComunicazione {
        gruppo = Set.copyOf(gruppo);
    }
}
