package it.unina.picus.comunicazioni.api;

import java.util.Set;
import java.util.UUID;

/** Porta di scrittura degli avvisi in-app prodotti da un annullamento. */
public interface AvvisiAnnullamentoApi {
    /**
     * Registra un solo avviso per richiesta e destinatario nella transazione chiamante.
     *
     * @param richiestaId identificativo della richiesta annullata
     * @param destinatari account coinvolti individuati dal modulo Richieste
     * @see ComunicazioniApi#avvisaAnnullamento(UUID, Set)
     */
    void avvisaAnnullamento(UUID richiestaId, Set<UUID> destinatari);
}
