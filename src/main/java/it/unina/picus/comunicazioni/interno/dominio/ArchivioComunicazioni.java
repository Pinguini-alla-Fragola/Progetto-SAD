package it.unina.picus.comunicazioni.interno.dominio;

import java.util.UUID;

/**
 * Dove si registrano le comunicazioni. Il dominio la usa senza sapere come vengono salvati i dati
 * (vedi l'adattatore in {@code interno.persistenza}).
 */
public interface ArchivioComunicazioni {

    /**
     * Salva la comunicazione e i suoi destinatari, tutti insieme.
     *
     * @param comunicazione la comunicazione già controllata
     */
    void registra(NuovaComunicazione comunicazione);

    /**
     * Conta le comunicazioni ricevute da un account e non ancora confermate.
     *
     * @param destinatarioId account destinatario
     * @return numero di comunicazioni in attesa della conferma di ricezione
     */
    long contaDaConfermare(UUID destinatarioId);
}
