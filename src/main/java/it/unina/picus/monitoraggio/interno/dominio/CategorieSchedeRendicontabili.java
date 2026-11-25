package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.util.Optional;
import java.util.Set;

/**
 * Porta di lettura della configurazione del Banco per le categorie rendicontabili
 * della scheda personale TM-OP-01 (#181, #41).
 */
public interface CategorieSchedeRendicontabili {

    /**
     * Legge in un'unica interrogazione i codici di categoria ammessi nel riepilogo del trimestre.
     * Restituisce {@link Optional#empty()} esclusivamente quando non esiste ancora alcuna
     * configurazione registrata dal Banco per il tracciato TM-OP-01 (compatibilità con la bozza
     * provvisoria v0.1); quando almeno una categoria risulta censita restituisce l'insieme
     * (eventualmente vuoto se tutte disattivate) dei soli codici attivi.
     *
     * @param trimestre periodo richiesto per la rendicontazione
     * @return insieme immutabile dei codici ammessi, oppure vuoto se la configurazione è assente
     */
    Optional<Set<String>> codiciAmmessi(Trimestre trimestre);
}
