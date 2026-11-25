package it.unina.picus.catalogo.api;

/** Verifica transazionale della selezionabilità di una misura per una nuova richiesta o offerta. */
public interface SelezioneMisureApi {
    /**
     * Rilegge la misura sotto lock condiviso ({@code PESSIMISTIC_READ}) fino al commit o rollback
     * della transazione di scrittura chiamante. Richiede una transazione di scrittura già aperta
     * (acquisisce il lock sulla sola riga di {@code catalogo_misure}). Una disattivazione concorrente
     * ({@code PESSIMISTIC_WRITE}) deve completarsi prima della verifica oppure attendere il
     * completamento dell'operazione che ha selezionato la misura.
     *
     * @param misuraId identificativo stabile della misura
     * @return {@code true} se la misura esiste ed è attiva, {@code false} altrimenti
     * @see ConsultazioneCatalogoApi#cercaMisura(long)
     */
    boolean bloccaMisuraAttiva(long misuraId);
}
