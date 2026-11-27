package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Porta di lettura delle schede registrate dal caso d'uso di invio (#58).
 * Espone solo i dati necessari alla rendicontazione trimestrale.
 */
public interface ArchivioSchedeRendicontabili {

    /**
     * Legge gli invii accettati per il periodo indicato, da uno stato coerente dell'archivio.
     * L'archivio del primo invio (#58) restituisce al più una scheda per operatore e trimestre,
     * con progressivo 1, anche se l'operatore è stato revocato dopo l'invio. Bozze e tentativi
     * respinti sono esclusi. Le regole per le correzioni sono definite nella #41 e il relativo
     * flusso è distinto dal primo invio.
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
     * @param conteggiDichiarati valori non negativi dichiarati nell'invio per codice di categoria
     *                          sintatticamente valido, incluso lo zero. La selezione delle categorie
     *                          configurate dal Banco avviene nel caso d'uso di riepilogo
     */
    record VersioneInviata(
            SchedaSintesi sintesi,
            long numeroVersione,
            Map<String, Long> conteggiDichiarati) {

        /**
         * Valida la proiezione e acquisisce una copia immutabile dei conteggi dichiarati.
         *
         * @param sintesi riferimento completo alla scheda e all'istante di invio della versione
         * @param numeroVersione progressivo positivo della versione accettata
         * @param conteggiDichiarati valori dichiarati per codice di categoria
         * @throws NullPointerException se un dato richiesto è mancante
         * @throws IllegalArgumentException se il progressivo non è positivo, un codice è malformato
         *                                  o un conteggio è negativo
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
            if (!conteggiDichiarati.keySet().stream().allMatch(TracciatoSchedaOperatore::codiceCategoriaValido)) {
                throw new IllegalArgumentException("I conteggi contengono codici di categoria non validi");
            }
            if (conteggiDichiarati.values().stream().anyMatch(valore -> valore < 0)) {
                throw new IllegalArgumentException("I conteggi dichiarati devono essere non negativi");
            }
        }
    }
}
