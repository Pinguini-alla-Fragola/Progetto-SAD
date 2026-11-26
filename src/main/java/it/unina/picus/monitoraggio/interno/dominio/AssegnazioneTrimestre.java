package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Riferimento alla configurazione pubblicata per un singolo trimestre (#41, #181).
 *
 * @param tracciato tracciato configurato
 * @param trimestre anno e numero del trimestre
 * @param versioneId identificativo della versione immutabile associata
 * @param codiceVersione codice della versione associata
 * @param stato stato della versione associata ({@code PUBBLICATA} o {@code LEGACY})
 * @param assegnataIl istante di associazione al trimestre
 */
public record AssegnazioneTrimestre(
        TracciatoMonitoraggio tracciato,
        Trimestre trimestre,
        UUID versioneId,
        String codiceVersione,
        StatoVersioneCategorie stato,
        Instant assegnataIl) {

    public AssegnazioneTrimestre {
        Objects.requireNonNull(tracciato, "tracciato");
        Objects.requireNonNull(trimestre, "trimestre");
        Objects.requireNonNull(versioneId, "versioneId");
        Objects.requireNonNull(codiceVersione, "codiceVersione");
        Objects.requireNonNull(stato, "stato");
        Objects.requireNonNull(assegnataIl, "assegnataIl");
    }
}
