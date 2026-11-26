package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Porta di lettura e associazione storica delle configurazioni di categoria rendicontabili
 * per trimestre della scheda personale TM-OP-01 (#181, #41).
 */
public interface CategorieSchedeRendicontabili {

    /** Identificativo fisso della versione esplicita associata ai trimestri provvisori {@code v0.1}. */
    UUID VERSIONE_LEGACY_V01_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Legge in un'unica interrogazione la configurazione effettivamente pubblicata o associata
     * al trimestre richiesto per il tracciato TM-OP-01.
     * Distingue esplicitamente una configurazione {@link ConfigurazioneTrimestre.Pubblicata},
     * un'associazione {@link ConfigurazioneTrimestre.LegacyProvvisoria} a {@code v0.1} e un
     * periodo {@link ConfigurazioneTrimestre.NonConfigurata} ancora privo di associazione
     * (senza rendere mai implicitamente permissivo un database privo di righe).
     *
     * @param trimestre anno e numero del trimestre da risolvere
     * @return stato della configurazione per il periodo richiesto
     */
    ConfigurazioneTrimestre configurazionePer(Trimestre trimestre);

    /**
     * Registra un'associazione esplicita alla versione {@link #VERSIONE_LEGACY_V01_ID} per un
     * periodo {@code v0.1} ancora non censito e restituisce la configurazione risultante (o quella
     * già presente se nel frattempo è avvenuta una pubblicazione concorrente).
     *
     * @param trimestre anno e numero del trimestre legacy
     * @param istante istante dell'associazione esplicita
     * @return configurazione risultante per il trimestre
     */
    default ConfigurazioneTrimestre associaLegacySeAssente(Trimestre trimestre, Instant istante) {
        Objects.requireNonNull(trimestre, "trimestre");
        return configurazionePer(trimestre);
    }
}
