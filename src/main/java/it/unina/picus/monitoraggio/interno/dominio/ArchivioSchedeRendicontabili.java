package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Porta di lettura delle schede registrate dal caso d'uso di invio (#58).
 * Espone solo i dati necessari alla rendicontazione trimestrale.
 */
public interface ArchivioSchedeRendicontabili {

    /**
     * Legge le versioni inviate e accettate per il periodo indicato, da uno stato coerente
     * dell'archivio. Sono comprese le correzioni ammesse e le schede già inviate da operatori
     * successivamente revocati. Bozze e tentativi di invio o di correzione respinti sono esclusi.
     *
     * @param trimestre anno e trimestre da cui leggere gli invii
     * @return versioni accettate, eventualmente una lista vuota
     */
    List<VersioneInviata> elencaVersioniInviate(Trimestre trimestre);

    /**
     * Dati di un invio accettato, proiettati per il riepilogo.
     *
     * @param sintesi riferimento all'invio, con titolare e periodo
     * @param numeroVersione progressivo positivo e univoco per operatore e trimestre, crescente
     *                       a ogni correzione accettata
     * @param conteggiDichiarati valori dichiarati per codice di categoria, incluso lo zero
     * @param categorieTracciato codici stabili delle categorie di conteggio ammesse dal tracciato
     *                            applicato all'invio
     */
    record VersioneInviata(
            SchedaSintesi sintesi,
            long numeroVersione,
            Map<String, Long> conteggiDichiarati,
            Set<String> categorieTracciato) {

        /** Valida il progressivo e acquisisce copie immutabili dei conteggi e delle categorie. */
        public VersioneInviata {
            Objects.requireNonNull(sintesi, "sintesi");
            if (numeroVersione < 1) {
                throw new IllegalArgumentException("Il numero di versione deve essere positivo");
            }
            conteggiDichiarati = Map.copyOf(conteggiDichiarati);
            categorieTracciato = Set.copyOf(categorieTracciato);
        }
    }
}
