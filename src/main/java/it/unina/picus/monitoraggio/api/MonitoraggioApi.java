package it.unina.picus.monitoraggio.api;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Raccoglie i conteggi trimestrali delle OPT e le schede personali degli operatori del Banco.
 * I dati descrivono attività dichiarate e sono indipendenti dal ciclo della singola richiesta.
 */
public interface MonitoraggioApi extends SchedeOperatoreApi {

    /**
     * Registra i conteggi della OPT autenticata per il trimestre. Lo stesso valore ritrasmesso
     * per OPT, trimestre, misura e voce compare una sola volta nei riepiloghi. Un valore diverso
     * può sostituirlo secondo le regole vigenti per le correzioni.
     *
     * @param attore identità verificata della OPT titolare
     * @param trimestre trimestre a cui si riferiscono le attività
     * @param conteggi voci dichiarate per le misure svolte
     * @throws SecurityException se l'attore non è una OPT autorizzata
     * @throws IllegalArgumentException se le voci o i riferimenti alle misure sono invalidi
     * @throws IllegalStateException se il periodo o il dato esistente non consentono l'aggiornamento
     */
    void registraConteggiOpt(IdentitaAutenticata attore, Trimestre trimestre, List<VoceMisura> conteggi);

    /**
     * Consulta i conteggi dichiarati, per un singolo trimestre o cumulati sui trimestri inclusi.
     * Il cumulativo somma la stessa voce per OPT e misura. Una voce mai dichiarata nel periodo è
     * assente, mentre uno zero dichiarato è un valore. Le categorie sovrapponibili restano distinte.
     *
     * @param attore identità verificata della OPT titolare o del Banco autorizzato
     * @param optId account della OPT da consultare oppure {@code null} per tutte le OPT, riservato al Banco
     * @param da primo trimestre incluso nel periodo
     * @param a ultimo trimestre incluso, uguale a {@code da} per la vista trimestrale
     * @return voci con un valore dichiarato nel periodo, eventualmente nessuna
     * @throws SecurityException se l'attore non è autorizzato alla vista richiesta
     * @throws IllegalArgumentException se il periodo è invertito
     * @throws NoSuchElementException se l'account OPT richiesto non esiste
     */
    List<ConteggioOpt> consultaConteggiOpt(IdentitaAutenticata attore, UUID optId, Trimestre da, Trimestre a);

    /** {@inheritDoc} */
    @Override
    UUID inviaSchedaOperatore(IdentitaAutenticata attore, Trimestre trimestre, DatiSchedaOperatore dati);

    /** {@inheritDoc} */
    @Override
    List<SchedaSintesi> elencoSchedeOperatore(IdentitaAutenticata attore);

    /** {@inheritDoc} */
    @Override
    SchedaOperatore consultaSchedaOperatore(IdentitaAutenticata attore, UUID schedaId);

    /**
     * Riepiloga per il Banco le schede ricevute nel trimestre selezionato. Le voci aggregate
     * rispettano la configurazione associata al trimestre stesso: una versione immutabile pubblicata
     * dal Banco per il tracciato TM-OP-01 ammette esclusivamente i codici pubblicati per quel
     * periodo (modifiche o disattivazioni pubblicate per trimestri successivi non alterano questo
     * riepilogo), l'associazione esplicita storica {@code v0.1} conserva i codici sintatticamente
     * validi del tracciato provvisorio, mentre un trimestre non configurato non attiva alcun
     * fallback permissivo. Le categorie sovrapponibili restano distinte. Uno zero dichiarato su una
     * voce ammessa è incluso nei totali, mentre una voce mai dichiarata o non ammessa è assente.
     * Le schede ricevute restano nell'elenco. Per ogni operatore si usa l'ultima versione
     * accettata nel periodo.
     *
     * @param attore identità verificata del Banco autorizzato alla rendicontazione
     * @param trimestre singolo trimestre da riepilogare
     * @return riferimenti alle schede ricevute e totali per voce, anche in assenza di invii
     * @throws SecurityException se l'attore non è autorizzato al riepilogo
     * @throws NullPointerException se manca il trimestre
     */
    RiepilogoTrimestre riepilogaSchede(IdentitaAutenticata attore, Trimestre trimestre);

    /**
     * Identificativo di un trimestre di rendicontazione.
     *
     * @param anno anno di riferimento
     * @param numero numero del trimestre, da 1 a 4
     */
    record Trimestre(int anno, int numero) {
        /**
         * Crea un riferimento a un trimestre.
         *
         * @param anno anno di riferimento
         * @param numero numero del trimestre, da 1 a 4
         * @throws IllegalArgumentException se l'anno non è positivo o il trimestre è fuori intervallo
         */
        public Trimestre {
            if (anno < 1 || numero < 1 || numero > 4) {
                throw new IllegalArgumentException("Anno o trimestre non valido");
            }
        }
    }

    /**
     * Valore dichiarato o cumulato per una voce di conteggio e una misura.
     *
     * @param misuraId identificativo stabile della misura
     * @param voce codice della voce nel tracciato applicabile
     * @param valore conteggio per la voce, incluso zero se dichiarato
     */
    record VoceMisura(long misuraId, String voce, long valore) {}

    /**
     * Conteggio di un'organizzazione nel periodo consultato.
     *
     * @param optId account dell'organizzazione
     * @param conteggio valore per una voce e una misura
     */
    record ConteggioOpt(UUID optId, VoceMisura conteggio) {}

    /**
     * Dati dichiarati per la scheda personale TM-OP-01, inviata senza firma.
     *
     * @param nome nome dell'operatore
     * @param cognome cognome dell'operatore
     * @param luogoNascita luogo di nascita
     * @param dataNascita data di nascita
     * @param codiceFiscale codice fiscale dell'operatore
     * @param qualifica qualifica professionale
     * @param tipologiaContratto tipologia di rapporto
     * @param areaLivello area o livello contrattuale
     * @param ore ore dichiarate nel trimestre
     * @param costoOrarioLordo costo orario lordo
     * @param conteggi conteggi aggregati per voce del tracciato
     */
    record DatiSchedaOperatore(
            String nome,
            String cognome,
            String luogoNascita,
            LocalDate dataNascita,
            String codiceFiscale,
            String qualifica,
            String tipologiaContratto,
            String areaLivello,
            BigDecimal ore,
            BigDecimal costoOrarioLordo,
            Map<String, Long> conteggi) {
        /** Crea una copia immutabile dei conteggi dichiarati. */
        public DatiSchedaOperatore {
            conteggi = Map.copyOf(conteggi);
        }
    }

    /**
     * Riferimento a una scheda in un elenco o riepilogo.
     *
     * @param id identificativo della scheda
     * @param operatoreId account personale del titolare
     * @param trimestre trimestre dichiarato
     * @param inviataIl istante di invio della scheda ricevuta
     */
    record SchedaSintesi(UUID id, UUID operatoreId, Trimestre trimestre, Instant inviataIl) {}

    /**
     * Scheda personale consultabile nei casi d'uso autorizzati.
     *
     * @param sintesi riferimento e periodo della scheda
     * @param dati dati personali, professionali e aggregati dichiarati
     */
    record SchedaOperatore(SchedaSintesi sintesi, DatiSchedaOperatore dati) {}

    /**
     * Dati usati dal Banco per la rendicontazione trimestrale.
     *
     * @param schede riferimenti alle schede ricevute usate per il riepilogo
     * @param conteggi totali esatti per voce, senza stima di persone uniche. Uno zero
     *                 dichiarato è incluso e una voce mai dichiarata è assente
     */
    record RiepilogoTrimestre(List<SchedaSintesi> schede, Map<String, BigInteger> conteggi) {
        /** Crea copie immutabili dell'elenco delle schede e dei totali. */
        public RiepilogoTrimestre {
            schede = List.copyOf(schede);
            conteggi = Map.copyOf(conteggi);
        }
    }
}
