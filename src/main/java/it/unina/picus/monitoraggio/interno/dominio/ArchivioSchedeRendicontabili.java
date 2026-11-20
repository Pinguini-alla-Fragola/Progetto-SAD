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
     * Ogni progressivo compare una sola volta per operatore e trimestre.
     *
     * @param trimestre anno e trimestre da cui leggere gli invii
     * @return versioni accettate, eventualmente una lista vuota
     */
    List<VersioneInviata> elencaVersioniInviate(Trimestre trimestre);

    /**
     * Dati di un invio accettato, proiettati per il riepilogo.
     *
     * @param sintesi riferimento alla scheda, con titolare e periodo. L'identificativo della
     *                scheda è stabile tra le versioni. L'istante di invio appartiene alla
     *                versione accettata rappresentata, anche in caso di correzione
     * @param numeroVersione progressivo positivo e univoco per operatore e trimestre, crescente
     *                       a ogni correzione accettata
     * @param conteggiDichiarati valori non negativi dichiarati per codice di categoria ammesso
     *                          dal tracciato, incluso lo zero
     * @param categorieTracciato codici stabili delle categorie di conteggio ammesse dal tracciato
     *                            applicato all'invio
     */
    record VersioneInviata(
            SchedaSintesi sintesi,
            long numeroVersione,
            Map<String, Long> conteggiDichiarati,
            Set<String> categorieTracciato) {

        /**
         * Valida la proiezione e acquisisce copie immutabili dei conteggi e delle categorie.
         *
         * @param sintesi riferimento completo alla scheda e all'istante di invio della versione
         * @param numeroVersione progressivo positivo della versione accettata
         * @param conteggiDichiarati valori dichiarati per codice di categoria
         * @param categorieTracciato categorie di conteggio ammesse dal tracciato dell'invio
         * @throws NullPointerException se un dato richiesto è mancante
         * @throws IllegalArgumentException se il progressivo non è positivo, un conteggio è
         *                                  negativo o una categoria dichiarata è fuori tracciato
         */
        public VersioneInviata {
            Objects.requireNonNull(sintesi, "sintesi");
            Objects.requireNonNull(sintesi.id(), "sintesi.id");
            Objects.requireNonNull(sintesi.operatoreId(), "sintesi.operatoreId");
            Objects.requireNonNull(sintesi.trimestre(), "sintesi.trimestre");
            Objects.requireNonNull(sintesi.inviataIl(), "sintesi.inviataIl");
            if (numeroVersione < 1) {
                throw new IllegalArgumentException("Il numero di versione deve essere positivo");
            }
            conteggiDichiarati = Map.copyOf(conteggiDichiarati);
            categorieTracciato = Set.copyOf(categorieTracciato);
            if (!categorieTracciato.containsAll(conteggiDichiarati.keySet())) {
                throw new IllegalArgumentException("I conteggi contengono categorie fuori tracciato");
            }
            if (conteggiDichiarati.values().stream().anyMatch(valore -> valore < 0)) {
                throw new IllegalArgumentException("I conteggi dichiarati devono essere non negativi");
            }
        }
    }
}
