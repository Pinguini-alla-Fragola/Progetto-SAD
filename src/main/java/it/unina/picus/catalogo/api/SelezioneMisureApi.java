package it.unina.picus.catalogo.api;

/** Verifica transazionale della selezionabilità di una misura per una nuova richiesta o offerta. */
public interface SelezioneMisureApi {
    /**
     * Rilegge la misura sotto lock condiviso fino al termine della transazione di scrittura chiamante.
     * Una disattivazione concorrente deve completarsi prima della verifica oppure attendere
     * il completamento dell'operazione che ha selezionato la misura.
     *
     * @param misuraId identificativo stabile della misura
     * @return vero se la misura esiste ed è attiva
     * @see ConsultazioneCatalogoApi#cercaMisura(long)
     */
    boolean bloccaMisuraAttiva(long misuraId);
}
