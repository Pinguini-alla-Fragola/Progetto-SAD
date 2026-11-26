package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta di gestione lato Banco delle bozze e pubblicazioni immutabili delle categorie
 * per i tracciati di monitoraggio ({@code TM-OP-01} e {@code TM-OPT-01}) (#41, #181).
 */
public interface ArchivioVersioniCategorie {

    /**
     * Crea una nuova versione in stato {@link StatoVersioneCategorie#BOZZA}.
     *
     * @param tracciato tracciato a cui appartiene la bozza
     * @param codiceVersione codice univoco della versione nel tracciato
     * @param descrizione descrizione leggibile della versione
     * @param voci elenco ordinato e non vuoto delle voci da configurare
     * @param creataIl istante di creazione
     * @return versione creata in stato {@code BOZZA}
     */
    VersioneCategorie creaBozza(
            TracciatoMonitoraggio tracciato,
            String codiceVersione,
            String descrizione,
            List<VoceCategoriaVersione> voci,
            Instant creataIl);

    /**
     * Aggiorna una versione esistente esclusivamente finché resta in stato {@code BOZZA}.
     *
     * @param versioneId identificativo della versione
     * @param descrizione nuova descrizione leggibile
     * @param voci nuovo elenco completo delle voci
     * @return versione aggiornata
     * @throws VersioneCategorieImmutabileException se la versione è già {@code PUBBLICATA} o {@code LEGACY}
     */
    VersioneCategorie aggiornaBozza(UUID versioneId, String descrizione, List<VoceCategoriaVersione> voci);

    /**
     * Pubblica (se ancora in {@code BOZZA}) e associa in modo immutabile la versione al trimestre indicato.
     * Più trimestri possono riferire la stessa versione pubblicata dello stesso tracciato.
     *
     * @param versioneId identificativo della versione da associare al trimestre
     * @param trimestre anno e numero del trimestre
     * @param istante istante di pubblicazione/associazione
     * @return configurazione pubblicata risultante per il trimestre
     * @throws ConfigurazioneTrimestreGiaPubblicataException se il trimestre ha già una configurazione assegnata
     * @throws VersioneCategorieImmutabileException se si tenta di pubblicare la versione {@code LEGACY}
     */
    ConfigurazioneTrimestre.Pubblicata pubblicaPerTrimestre(UUID versioneId, Trimestre trimestre, Instant istante);

    /**
     * Elenca le versioni censite per il tracciato indicato.
     *
     * @param tracciato tracciato da consultare
     * @return versioni con le rispettive voci, ordinate per creazione
     */
    List<VersioneCategorie> elencaVersioni(TracciatoMonitoraggio tracciato);

    /**
     * Recupera il dettaglio completo di una versione per identificativo.
     *
     * @param versioneId identificativo della versione
     * @return versione con le rispettive voci, se presente
     */
    Optional<VersioneCategorie> trovaVersione(UUID versioneId);

    /**
     * Legge la configurazione risolta per il tracciato e il trimestre indicati.
     *
     * @param tracciato tracciato da consultare
     * @param trimestre anno e numero del trimestre
     * @return {@code Pubblicata}, {@code LegacyProvvisoria} oppure {@code NonConfigurata}
     */
    ConfigurazioneTrimestre configurazionePer(TracciatoMonitoraggio tracciato, Trimestre trimestre);

    /**
     * Elenca tutte le associazioni trimestre -> versione per il tracciato indicato.
     *
     * @param tracciato tracciato da consultare
     * @return associazioni ordinate cronologicamente per anno e trimestre
     */
    List<AssegnazioneTrimestre> elencaAssegnazioni(TracciatoMonitoraggio tracciato);
}
