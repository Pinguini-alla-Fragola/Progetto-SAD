package it.unina.picus.comunicazioni.api;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Gestisce le comunicazioni del Banco, i riscontri dei destinatari e la consegna degli avvisi
 * operativi. Richieste rimane responsabile della pratica e delle azioni a cui puntano gli avvisi.
 */
public interface ComunicazioniApi {

    /**
     * Invia una comunicazione del Banco. Il modulo risolve e registra gli account destinatari
     * al momento dell'invio e rende disponibili nell'app la comunicazione e gli allegati.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param dati testo, selezione destinatari ed eventuali allegati
     * @return comunicazione registrata
     * @throws SecurityException se l'attore non è il Banco
     * @throws IllegalArgumentException se mancano testo o destinatari, la selezione è incoerente
     *                                  o un allegato non rispetta le regole di caricamento
     */
    Comunicazione inviaComunicazione(IdentitaAutenticata attore, DatiComunicazione dati);

    /**
     * Elenca per il Banco le comunicazioni inviate, consultabili anche dopo l'invio.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @return comunicazioni inviate, senza contenuto binario
     * @throws SecurityException se l'attore non è il Banco
     */
    List<Comunicazione> elencoInviate(IdentitaAutenticata attore);

    /**
     * Elenca le comunicazioni ricevute dall'account autenticato.
     *
     * @param attore identità verificata dell'account che consulta la propria casella
     * @return comunicazioni con eventuale riscontro dell'account, senza contenuto binario
     */
    List<Comunicazione> elencoRicevute(IdentitaAutenticata attore);

    /**
     * Consulta il testo e i metadati degli allegati. L'apertura lascia il riscontro invariato.
     *
     * @param attore identità verificata del Banco o dell'account destinatario
     * @param comunicazioneId identificativo della comunicazione
     * @return comunicazione visibile all'account
     * @throws SecurityException se l'attore non può leggere la comunicazione
     * @throws NoSuchElementException se la comunicazione non esiste
     */
    Comunicazione consultaComunicazione(IdentitaAutenticata attore, UUID comunicazioneId);

    /**
     * Scarica un allegato con gli stessi controlli di accesso della comunicazione.
     *
     * @param attore identità verificata del Banco o dell'account destinatario
     * @param allegatoId identificativo dell'allegato
     * @return file e relativi metadati
     * @throws SecurityException se l'attore non può leggere la comunicazione
     * @throws NoSuchElementException se l'allegato non esiste
     */
    FileAllegato scaricaAllegato(IdentitaAutenticata attore, UUID allegatoId);

    /**
     * Registra la conferma esplicita per l'account destinatario. Un nuovo tentativo dello stesso
     * account per la stessa comunicazione restituisce la data e l'ora del primo riscontro.
     *
     * @param attore identità verificata dell'account destinatario
     * @param comunicazioneId identificativo della comunicazione ricevuta
     * @return istante della prima conferma
     * @throws SecurityException se l'account non è un destinatario
     * @throws NoSuchElementException se la comunicazione non esiste
     */
    Instant confermaRicezione(IdentitaAutenticata attore, UUID comunicazioneId);

    /**
     * Elenca i riscontri per il Banco, inclusi i destinatari che devono ancora confermare.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param comunicazioneId identificativo della comunicazione inviata
     * @return una voce per account destinatario
     * @throws SecurityException se l'attore non è il Banco
     * @throws NoSuchElementException se la comunicazione non esiste
     */
    List<Riscontro> elencoRiscontri(IdentitaAutenticata attore, UUID comunicazioneId);

    /**
     * Registra l'avviso per un contatto di disponibilità acquisito da Richieste. Più chiamate
     * per lo stesso contatto e destinatario producono un solo avviso visibile nell'app.
     *
     * @param richiestaId riferimento alla pratica
     * @param contattoId riferimento al contatto da aprire per rispondere
     * @param destinatarioId unico account interpellato, determinato da Richieste
     */
    void avvisaDisponibilita(UUID richiestaId, UUID contattoId, UUID destinatarioId);

    /**
     * Registra gli avvisi per un annullamento acquisito da Richieste. Più chiamate per la stessa
     * richiesta producono un solo avviso di annullamento per account destinatario.
     *
     * @param richiestaId riferimento alla pratica annullata
     * @param destinatari account coinvolti individuati da Richieste
     */
    void avvisaAnnullamento(UUID richiestaId, Set<UUID> destinatari);

    /**
     * Elenca gli avvisi operativi destinati all'account, senza esporre i dati della pratica.
     *
     * @param attore identità verificata dell'account destinatario
     * @return avvisi visibili al destinatario, eventualmente nessuno
     */
    List<AvvisoOperativo> elencoAvvisiOperativi(IdentitaAutenticata attore);

    /**
     * Dati di invio della comunicazione e selezione dei destinatari.
     *
     * @param titolo titolo della comunicazione
     * @param testo testo da inviare
     * @param tipo categoria di destinatari e ampiezza della selezione
     * @param gruppo account selezionati per un invio a gruppo, insieme vuoto per un invio all'intera categoria
     * @param allegati file da inviare, eventualmente nessuno
     */
    record DatiComunicazione(
            String titolo, String testo, TipoDestinatari tipo, Set<UUID> gruppo, List<FileAllegato> allegati) {
        /** Crea una copia immutabile della selezione e della lista degli allegati. */
        public DatiComunicazione {
            gruppo = Set.copyOf(gruppo);
            allegati = List.copyOf(allegati);
        }
    }

    /**
     * File allegato per l'invio o lo scaricamento.
     *
     * @param nomeFile nome del file
     * @param tipoMime tipo MIME del contenuto
     * @param contenuto byte del file, copiati all'ingresso e alla lettura
     */
    record FileAllegato(String nomeFile, String tipoMime, byte[] contenuto) {
        /** Conserva una copia dei byte ricevuti. */
        public FileAllegato {
            contenuto = contenuto.clone();
        }

        /**
         * Restituisce una copia dei byte del file.
         *
         * @return contenuto del file
         */
        @Override
        public byte[] contenuto() {
            return contenuto.clone();
        }
    }

    /**
     * Metadati di un allegato ottenibili senza scaricare il file.
     *
     * @param id identificativo dell'allegato
     * @param nomeFile nome visualizzato
     * @param tipoMime tipo MIME del contenuto
     * @param dimensioneByte dimensione in byte
     */
    record Allegato(UUID id, String nomeFile, String tipoMime, long dimensioneByte) {}

    /**
     * Dati di una comunicazione visibile al Banco o all'account destinatario.
     *
     * @param id identificativo della comunicazione
     * @param titolo titolo visualizzato
     * @param testo testo della comunicazione
     * @param inviataIl istante dell'invio
     * @param allegati metadati degli allegati, eventualmente nessuno
     * @param confermataIl istante del riscontro dell'account che consulta, oppure {@code null}
     */
    record Comunicazione(
            UUID id, String titolo, String testo, Instant inviataIl, List<Allegato> allegati, Instant confermataIl) {
        /** Crea una copia immutabile della lista degli allegati. */
        public Comunicazione {
            allegati = List.copyOf(allegati);
        }
    }

    /**
     * Riscontro di un account destinatario, anche se ancora in attesa.
     *
     * @param destinatarioId account destinatario
     * @param nomeVisualizzato nome dell'organizzazione o dell'operatore
     * @param confermataIl istante della prima conferma, oppure {@code null} in assenza di riscontro
     */
    record Riscontro(UUID destinatarioId, String nomeVisualizzato, Instant confermataIl) {}

    /**
     * Avviso di un evento operativo con i riferimenti da usare nel modulo Richieste.
     *
     * @param id identificativo dell'avviso
     * @param tipo evento che ha generato l'avviso
     * @param richiestaId riferimento alla richiesta
     * @param contattoId riferimento al contatto per gli avvisi di disponibilità, altrimenti {@code null}
     * @param registratoIl istante di registrazione dell'avviso
     */
    record AvvisoOperativo(UUID id, TipoAvviso tipo, UUID richiestaId, UUID contattoId, Instant registratoIl) {}

    /** Selezioni previste per una comunicazione del Banco. */
    enum TipoDestinatari {
        /** Tutti gli account organizzativi OPT attivi. */
        TUTTE_LE_OPT,
        /** Account organizzativi OPT selezionati dal Banco. */
        GRUPPO_OPT,
        /** Tutti gli account personali degli operatori attivi. */
        TUTTI_GLI_OPERATORI,
        /** Account personali degli operatori selezionati dal Banco. */
        GRUPPO_OPERATORI
    }

    /** Eventi operativi della pratica comunicati nell'app. */
    enum TipoAvviso {
        /** Disponibilità da valutare per il contatto ricevuto. */
        RICHIESTA_DISPONIBILITA,
        /** Annullamento della richiesta. */
        RICHIESTA_ANNULLATA
    }
}
