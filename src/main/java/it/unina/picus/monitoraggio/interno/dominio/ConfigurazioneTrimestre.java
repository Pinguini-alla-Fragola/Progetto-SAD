package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.time.Instant;
import java.util.Objects;

/**
 * Configurazione delle categorie risolta per un singolo trimestre e tracciato (#181, #41).
 * Distingue esplicitamente: una configurazione {@link Pubblicata}, un periodo storico
 * associato alla versione {@link LegacyProvvisoria} {@code v0.1} e un periodo
 * {@link NonConfigurata} ancora privo di pubblicazione.
 */
public sealed interface ConfigurazioneTrimestre
        permits ConfigurazioneTrimestre.Pubblicata,
                ConfigurazioneTrimestre.LegacyProvvisoria,
                ConfigurazioneTrimestre.NonConfigurata {

    /**
     * Configurazione pubblicata e immutabile assegnata al trimestre.
     *
     * @param trimestre trimestre configurato
     * @param versione versione immutabile delle categorie pubblicata per il trimestre
     * @param assegnataIl istante di associazione al trimestre
     */
    record Pubblicata(Trimestre trimestre, VersioneCategorie versione, Instant assegnataIl)
            implements ConfigurazioneTrimestre {
        public Pubblicata {
            Objects.requireNonNull(trimestre, "trimestre");
            Objects.requireNonNull(versione, "versione");
            Objects.requireNonNull(assegnataIl, "assegnataIl");
            if (versione.stato() != StatoVersioneCategorie.PUBBLICATA) {
                throw new IllegalArgumentException("Una configurazione pubblicata richiede stato PUBBLICATA");
            }
        }
    }

    /**
     * Configurazione storica associata esplicitamente al tracciato provvisorio {@code v0.1}.
     *
     * @param trimestre trimestre associato
     * @param versione versione esplicita {@code LEGACY} {@code v0.1}
     * @param assegnataIl istante di associazione al tracciato provvisorio
     */
    record LegacyProvvisoria(Trimestre trimestre, VersioneCategorie versione, Instant assegnataIl)
            implements ConfigurazioneTrimestre {
        public LegacyProvvisoria {
            Objects.requireNonNull(trimestre, "trimestre");
            Objects.requireNonNull(versione, "versione");
            Objects.requireNonNull(assegnataIl, "assegnataIl");
            if (versione.stato() != StatoVersioneCategorie.LEGACY) {
                throw new IllegalArgumentException("Un'associazione provvisoria richiede stato LEGACY");
            }
        }
    }

    /**
     * Trimestre privo di associazione pubblicata o storica nella tabella delle configurazioni.
     *
     * @param trimestre trimestre non configurato
     */
    record NonConfigurata(Trimestre trimestre) implements ConfigurazioneTrimestre {
        public NonConfigurata {
            Objects.requireNonNull(trimestre, "trimestre");
        }
    }
}
