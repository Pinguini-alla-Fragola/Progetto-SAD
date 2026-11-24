package it.unina.picus.comunicazioni.interno.dominio;

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
     * Restituisce una pagina delle comunicazioni inviate che rispettano il filtro, dalla più recente. Il filtro
     * sulla conferma di ricezione non vale per le inviate e viene ignorato.
     *
     * @param filtro ricerca e pagina
     * @return la pagina, senza il contenuto degli allegati
     */
    PaginaComunicazioni elencoInviate(FiltroComunicazioni filtro);

    /**
     * Restituisce una pagina delle comunicazioni ricevute da un account che rispettano il filtro, dalla più
     * recente, ciascuna con la conferma di quell'account.
     *
     * @param destinatarioId account destinatario
     * @param filtro ricerca e pagina
     * @return la pagina, senza il contenuto degli allegati
     */
    PaginaComunicazioni elencoRicevute(UUID destinatarioId, FiltroComunicazioni filtro);

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
