package it.unina.picus.monitoraggio.api;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Contratto applicativo per la registrazione e la consultazione dei conteggi trimestrali delle OPT.
 */
public interface ConteggiOptApi {

    /**
     * Voce del tracciato di monitoraggio attiva per la rendicontazione.
     *
     * @param codice codice univoco della categoria di conteggio
     * @param descrizione descrizione leggibile della categoria
     */
    record CategoriaVoce(String codice, String descrizione) {}

    /**
     * Restituisce l'elenco ordinato delle categorie di conteggio attive.
     *
     * @return elenco delle categorie attive disponibili per la dichiarazione
     */
    List<CategoriaVoce> elencoCategorieAttive();

    /**
     * Registra i conteggi della OPT autenticata per il trimestre indicato.
     *
     * @param attore identità verificata della OPT titolare
     * @param trimestre periodo trimestrale di riferimento
     * @param conteggi elenco delle voci dichiarate per le misure svolte
     * @throws SecurityException se l'attore non è una OPT autorizzata
     * @throws DatiConteggiNonValidiException se il periodo o le voci non rispettano i vincoli del tracciato
     * @throws IllegalStateException se la modifica di un valore esistente non è consentita
     */
    void registraConteggiOpt(IdentitaAutenticata attore, Trimestre trimestre, List<VoceMisura> conteggi);

    /**
     * Consulta i conteggi dichiarati nel periodo compreso tra {@code da} e {@code a},
     * cumulando i valori per OPT, misura e voce.
     *
     * @param attore identità verificata della OPT titolare o del Banco autorizzato
     * @param optId identificativo dell'account OPT oppure {@code null} per tutte le OPT (solo Banco)
     * @param da primo trimestre incluso nell'intervallo di ricerca
     * @param a ultimo trimestre incluso nell'intervallo di ricerca
     * @return elenco dei conteggi aggregati nel periodo selezionato
     * @throws SecurityException se l'attore non è autorizzato alla consultazione richiesta
     * @throws IllegalArgumentException se gli estremi del periodo non sono validi o sono invertiti
     * @throws NoSuchElementException se l'account OPT specificato non esiste
     */
    List<ConteggioOpt> consultaConteggiOpt(IdentitaAutenticata attore, UUID optId, Trimestre da, Trimestre a);
}