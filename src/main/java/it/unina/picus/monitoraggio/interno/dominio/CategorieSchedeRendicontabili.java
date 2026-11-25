package it.unina.picus.monitoraggio.interno.dominio;

import java.util.Optional;
import java.util.Set;

/**
 * Porta di lettura della configurazione globale corrente del Banco per le categorie rendicontabili
 * della scheda personale TM-OP-01 (#181, #41).
 */
public interface CategorieSchedeRendicontabili {

    /**
     * Legge in un'unica interrogazione la configurazione globale corrente dei codici di categoria
     * ammessi nel riepilogo, valida per ogni trimestre e applicata anche alle schede storiche
     * mantenendo inalterate le schede inviate. Restituisce {@link Optional#empty()} esclusivamente
     * quando non esiste ancora alcuna configurazione registrata dal Banco per il tracciato
     * TM-OP-01 (compatibilità con la bozza provvisoria v0.1); quando almeno una categoria risulta
     * censita restituisce l'insieme (eventualmente vuoto se tutte disattivate) dei soli codici attivi.
     *
     * @return insieme immutabile dei codici attivi ammessi, oppure vuoto se la configurazione è assente
     */
    Optional<Set<String>> codiciAmmessi();
}
