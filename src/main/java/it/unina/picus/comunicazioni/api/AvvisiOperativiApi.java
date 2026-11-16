package it.unina.picus.comunicazioni.api;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.AvvisoOperativo;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import java.util.List;
import java.util.UUID;

/** Avvisi di disponibilità in-app, indipendenti dalle comunicazioni generali del Banco. */
public interface AvvisiOperativiApi {
    /**
     * Registra un avviso idempotente nella transazione che crea il contatto.
     * @param richiestaId pratica associata
     * @param contattoId contatto da aprire per rispondere
     * @param destinatarioId unico account interpellato
     * @see ComunicazioniApi#avvisaDisponibilita(UUID, UUID, UUID)
     */
    void avvisaDisponibilita(UUID richiestaId, UUID contattoId, UUID destinatarioId);

    /**
     * Elenca gli avvisi del solo account autenticato.
     * @param attore identità verificata del destinatario
     * @return avvisi operativi, senza dati della pratica
     * @see ComunicazioniApi#elencoAvvisiOperativi(IdentitaAutenticata)
     */
    List<AvvisoOperativo> elencoAvvisiOperativi(IdentitaAutenticata attore);
}
