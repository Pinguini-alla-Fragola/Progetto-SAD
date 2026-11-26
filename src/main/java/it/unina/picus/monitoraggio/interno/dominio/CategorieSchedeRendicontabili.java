package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.util.UUID;

/**
 * Porta di lettura per trimestre delle configurazioni di categoria rendicontabili
 * della scheda personale TM-OP-01 (#181, #41).
 */
public interface CategorieSchedeRendicontabili {

    /** Identificativo fisso della versione esplicita associata in migrazione ai trimestri già rendicontati con {@code v0.1}. */
    UUID VERSIONE_LEGACY_V01_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Legge la configurazione effettivamente pubblicata o migrata per il trimestre richiesto
     * del tracciato TM-OP-01.
     * Distingue una configurazione {@link ConfigurazioneTrimestre.Pubblicata}, un'associazione
     * storica {@link ConfigurazioneTrimestre.LegacyProvvisoria} riservata ai soli periodi già
     * rendicontati con {@code v0.1} prima della migrazione e un periodo
     * {@link ConfigurazioneTrimestre.NonConfigurata} ancora privo di pubblicazione (senza mai
     * creare nuove associazioni {@code LEGACY} a runtime).
     *
     * @param trimestre anno e numero del trimestre da risolvere
     * @return stato della configurazione per il periodo richiesto
     */
    ConfigurazioneTrimestre configurazionePer(Trimestre trimestre);
}
