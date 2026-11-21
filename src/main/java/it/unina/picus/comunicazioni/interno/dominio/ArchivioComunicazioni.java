package it.unina.picus.comunicazioni.interno.dominio;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dove si registrano e si leggono le comunicazioni. Il dominio la usa senza sapere come vengono salvati i dati
 * (vedi l'adattatore in {@code interno.persistenza}).
 */
public interface ArchivioComunicazioni {

    /**
     * Salva la comunicazione, i suoi destinatari e i suoi allegati, tutti insieme.
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

    /**
     * Elenca tutte le comunicazioni inviate, dalla più recente.
     *
     * @return le comunicazioni, senza il contenuto degli allegati
     */
    List<ComunicazioneSalvata> elencoInviate();

    /**
     * Elenca le comunicazioni ricevute da un account, dalla più recente, con la sua eventuale conferma.
     *
     * @param destinatarioId account destinatario
     * @return le comunicazioni ricevute
     */
    List<ComunicazioneSalvata> elencoRicevute(UUID destinatarioId);

    /**
     * Cerca una comunicazione.
     *
     * @param comunicazioneId comunicazione cercata
     * @param lettoreId account che la consulta, per indicare la sua conferma se è un destinatario
     * @return la comunicazione, se esiste
     */
    Optional<ComunicazioneSalvata> cerca(UUID comunicazioneId, UUID lettoreId);

    /**
     * Restituisce gli account registrati come destinatari al momento dell'invio.
     *
     * @param comunicazioneId comunicazione
     * @return gli account destinatari; vuoto se la comunicazione non esiste
     */
    Set<UUID> destinatari(UUID comunicazioneId);

    /**
     * Indica a quale comunicazione appartiene un allegato, senza leggerne il contenuto.
     *
     * @param allegatoId allegato
     * @return la comunicazione, se l'allegato esiste
     */
    Optional<UUID> comunicazioneDellAllegato(UUID allegatoId);

    /**
     * Legge un allegato con il suo contenuto.
     *
     * @param allegatoId allegato
     * @return l'allegato, se esiste
     */
    Optional<FileSalvato> allegato(UUID allegatoId);
}
