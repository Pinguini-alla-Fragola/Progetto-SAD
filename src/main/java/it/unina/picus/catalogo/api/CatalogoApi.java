package it.unina.picus.catalogo.api;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Fornisce i riferimenti alle misure, ai comuni e all'offerta territoriale.
 * I comandi di modifica devono verificare sul server il ruolo e, per l'offerta, la titolarità.
 */
public interface CatalogoApi extends ConsultazioneCatalogoApi, RicercaErogatoriApi, GestioneOffertaOptApi {

    /**
     * Elenca le misure selezionabili per una nuova richiesta o dichiarazione di offerta.
     *
     * @return misure attive, eventualmente nessuna
     */
    List<Misura> elencoMisureAttive();

    /**
     * Elenca per il Banco tutte le misure, incluse quelle disattivate.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @return misure di catalogo con lo stato corrente
     * @throws SecurityException se l'attore non è il Banco
     */
    List<Misura> elencoMisure(IdentitaAutenticata attore);

    /**
     * Risolve il riferimento a una misura anche quando è stata disattivata.
     *
     * @param misuraId identificativo stabile della misura
     * @return misura e stato corrente, oppure vuoto se l'identificativo è sconosciuto
     */
    Optional<Misura> cercaMisura(long misuraId);

    /**
     * Elenca i comuni utilizzabili nei moduli di PICUS.
     *
     * @return comuni censiti nel catalogo
     */
    List<Comune> elencoComuni();

    /**
     * Verifica e risolve un codice comune.
     *
     * @param codice codice del comune usato nel catalogo
     * @return comune corrispondente, oppure vuoto se il codice è sconosciuto
     */
    Optional<Comune> cercaComune(String codice);

    /**
     * Combina le offerte delle OPT con i profili degli operatori attivi che erogano la misura.
     * Considera lo stesso comune e i limitrofi, e include l'opzione Centro PICUS a distanza
     * se la misura la consente. Un account compatibile in più modalità può comparire per ciascuna
     * opzione. Richieste determina l'ordine finale e verifica la disponibilità per la singola pratica.
     *
     * @param misuraId identificativo della misura richiesta
     * @param comuneCodice comune indicato nella richiesta
     * @return opzioni compatibili, oppure una lista vuota se non ce ne sono
     * @throws NoSuchElementException se la misura o il comune sono sconosciuti
     */
    List<ErogatoreCompatibile> cercaErogatoriCompatibili(long misuraId, String comuneCodice);

    /**
     * Crea una nuova misura attiva.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param dati dati di catalogo della nuova misura
     * @return misura registrata con il suo identificativo
     * @throws SecurityException se l'attore non è autorizzato
     * @throws IllegalArgumentException se i dati sono incompleti o il codice è già in uso
     */
    Misura creaMisura(IdentitaAutenticata attore, DatiMisura dati);

    /**
     * Aggiorna i dati o lo stato di una misura mantenendo il riferimento usato nello storico.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param misuraId identificativo della misura esistente
     * @param dati dati di catalogo aggiornati
     * @param attiva indica se la misura è selezionabile per nuove richieste e offerte
     * @return misura aggiornata
     * @throws SecurityException se l'attore non è autorizzato
     * @throws IllegalArgumentException se i dati sono incompleti o il codice è già in uso su un'altra misura
     * @throws NoSuchElementException se la misura è sconosciuta
     */
    Misura aggiornaMisura(IdentitaAutenticata attore, long misuraId, DatiMisura dati, boolean attiva);

    /** {@inheritDoc} */
    @Override
    List<OffertaOpt> elencoOffertaOpt(IdentitaAutenticata attore, UUID optId);

    /** {@inheritDoc} */
    @Override
    void registraOffertaOpt(IdentitaAutenticata attore, long misuraId, boolean tramiteProfessionista);

    /** {@inheritDoc} */
    @Override
    void ritiraOffertaOpt(IdentitaAutenticata attore, long misuraId);

    /**
     * Dati aggiornabili di una misura.
     *
     * @param codice codice della misura nel catalogo
     * @param nome denominazione mostrata agli utenti
     * @param descrizione descrizione della misura
     * @param erogabileADistanza indica se la misura ammette l'erogazione a distanza
     */
    record DatiMisura(String codice, String nome, String descrizione, boolean erogabileADistanza) {}

    /**
     * Misura di catalogo selezionabile quando attiva e risolvibile anche per le consultazioni storiche.
     *
     * @param id identificativo stabile della misura
     * @param dati dati di catalogo della misura
     * @param attiva indica se la misura è attualmente selezionabile
     */
    record Misura(long id, DatiMisura dati, boolean attiva) {}

    /**
     * Comune censito da Catalogo.
     *
     * @param codice identificativo usato nelle richieste e nei profili
     * @param nome denominazione visualizzata
     * @param codiceIstat codice statistico ISTAT a sei cifre con eventuali zeri iniziali, oppure {@code null}
     * @param provincia denominazione della provincia o città metropolitana, oppure {@code null}
     * @param siglaProvincia sigla automobilistica a due lettere, oppure {@code null}
     * @param cittaMetropolitana indica se l'ente sovracomunale è una città metropolitana
     */
    record Comune(
            String codice,
            String nome,
            String codiceIstat,
            String provincia,
            String siglaProvincia,
            boolean cittaMetropolitana) {

        /**
         * Costruttore essenziale per i riferimenti privi di metadati provinciali.
         *
         * @param codice identificativo usato nelle richieste e nei profili
         * @param nome denominazione visualizzata
         */
        public Comune(String codice, String nome) {
            this(codice, nome, null, null, null, false);
        }

        /**
         * Restituisce la descrizione della provincia o città metropolitana per distinguere eventuali omonimie.
         *
         * @return descrizione territoriale formattata oppure stringa vuota se assente
         */
        public String descrizioneTerritoriale() {
            if (provincia == null || provincia.isBlank()) {
                return "";
            }
            String prefisso = cittaMetropolitana ? "Città metropolitana di " : "Provincia di ";
            return siglaProvincia != null && !siglaProvincia.isBlank()
                    ? prefisso + provincia + " (" + siglaProvincia + ")"
                    : prefisso + provincia;
        }

        /**
         * Restituisce la denominazione completa del comune affiancata dal riferimento provinciale.
         *
         * @return nome del comune con provincia o città metropolitana quando disponibile
         */
        public String etichettaCompleta() {
            String territorio = descrizioneTerritoriale();
            return territorio.isEmpty() ? nome : nome + " — " + territorio;
        }
    }

    /**
     * Opzione di erogazione compatibile con la misura e il territorio.
     *
     * @param accountId account organizzativo OPT o personale dell'operatore
     * @param nomeVisualizzato nome dell'erogatore mostrato al Banco
     * @param tipo tipologia funzionale dell'erogatore
     * @param comuneCodice comune o territorio di riferimento dell'erogatore
     * @param ambito relazione territoriale o opzione di erogazione a distanza
     */
    record ErogatoreCompatibile(
            UUID accountId, String nomeVisualizzato, TipoErogatore tipo, String comuneCodice, AmbitoErogazione ambito) {}

    /**
     * Dichiarazione di offerta riferita a una misura del catalogo.
     *
     * @param misuraId identificativo stabile della misura offerta
     * @param tramiteProfessionista indica se la OPT si avvale di un professionista esterno
     */
    record OffertaOpt(long misuraId, boolean tramiteProfessionista) {}

    /** Opzioni di ricerca compatibili con il comune della richiesta. */
    enum AmbitoErogazione {
        /** Erogatore nello stesso comune. */
        STESSO_COMUNE,
        /** Erogatore in un comune confinante. */
        COMUNE_LIMITROFO,
        /** Centro PICUS in modalità a distanza. */
        CENTRO_PICUS_A_DISTANZA
    }
}
