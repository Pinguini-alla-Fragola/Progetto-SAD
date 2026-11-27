package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.time.Instant;
import java.util.Objects;

/**
 * Configurazione delle categorie risolta per un singolo trimestre e tracciato (#181, #41).
 * Distingue esplicitamente: una configurazione {@link Pubblicata}, un periodo già rendicontato
 * associato alla versione {@link LegacyProvvisoria} {@code v0.1} in sede di migrazione, e un
 * periodo {@link NonConfigurata} privo di associazione.
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
     * Configurazione storica associata al tracciato provvisorio {@code v0.1} per i soli
     * periodi già rendicontati prima della migrazione.
     *
     * @param trimestre trimestre già rendicontato in {@code v0.1}
     * @param versione versione esplicita {@code LEGACY} {@code v0.1}
     * @param assegnataIl istante di associazione storica
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
     * Trimestre privo di associazione nella tabella delle configurazioni.
     *
     * @param trimestre trimestre non configurato
     * @param transitorioSenzaPubblicazioni {@code true} esclusivamente finché il Banco non ha
     *                                      ancora pubblicato alcuna versione per il tracciato
     */
    record NonConfigurata(Trimestre trimestre, boolean transitorioSenzaPubblicazioni)
            implements ConfigurazioneTrimestre {
        /** Valida il riferimento al trimestre non configurato. */
        public NonConfigurata {
            Objects.requireNonNull(trimestre, "trimestre");
        }
    }
}
