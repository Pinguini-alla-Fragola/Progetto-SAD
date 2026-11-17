package it.unina.picus.richieste.api;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Governa il ciclo delle richieste, i contatti di disponibilità e gli accordi.
 *
 * <p>Ogni operazione riceve l'identità verificata dal server e controlla il ruolo e il legame
 * dell'attore con la pratica. Il modulo verifica stato e permessi prima di scrivere. Una
 * transizione aggiorna atomicamente lo stato e i dati a cui è legata, anche con comandi concorrenti.
 * I dinieghi e i dati non validi lasciano invariata la pratica.</p>
 *
 * @see StatoRichiesta
 */
public interface RichiesteApi extends InvioRichiesteApi, CoordinamentoRichiesteApi {

    /**
     * Registra una richiesta della OPT autenticata. Il modulo attribuisce ID, data, titolare
     * e stato iniziale {@link StatoRichiesta#CREATA}. Un ritento dello stesso invio della stessa OPT
     * con uguale riferimento e dati restituisce la richiesta già registrata.
     *
     * @param attore identità verificata della OPT richiedente
     * @param dati misura, persona o nucleo, comune ed eventuale descrizione
     * @param riferimentoInvio identificativo del tentativo logico, oppure {@code null}
     *                        se il chiamante non richiede il riconoscimento dei ritenti
     * @return richiesta presente negli elenchi della OPT e del Banco
     * @throws SecurityException se l'attore non è una OPT autorizzata
     * @throws IllegalArgumentException se i dati sono incompleti, la misura non è richiedibile
     *                                  o il comune è sconosciuto
     * @throws IllegalStateException se il riferimento di invio della OPT è già associato a dati diversi
     */
    RichiestaSintesi invia(IdentitaAutenticata attore, DatiInvio dati, UUID riferimentoInvio);

    /**
     * Elenca le richieste inviate dalla OPT autenticata.
     *
     * @param attore identità verificata della OPT titolare
     * @return richieste proprie, eventualmente nessuna
     * @throws SecurityException se l'attore non è una OPT autorizzata
     */
    List<RichiestaSintesi> elencoRichiesteOpt(IdentitaAutenticata attore);

    /**
     * Elenca le richieste aperte in ordine cronologico di invio.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @return coda con richieste Creata, Presa in carico e In programmazione
     * @throws SecurityException se l'attore non è il Banco
     */
    List<RichiestaSintesi> codaBanco(IdentitaAutenticata attore);

    /**
     * Elenca per il Banco le richieste con un esito registrato.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @return richieste Confermata, Rifiutata e Annullata
     * @throws SecurityException se l'attore non è il Banco
     */
    List<RichiestaSintesi> archivioBanco(IdentitaAutenticata attore);

    /**
     * Elenca le pratiche assegnate all'account autenticato.
     *
     * @param attore identità verificata della OPT o dell'operatore
     * @return pratiche assegnate, incluse quelle con un esito registrato
     * @throws SecurityException se l'attore non ha un ruolo da erogatore
     */
    List<RichiestaSintesi> elencoAssegnazioni(IdentitaAutenticata attore);

    /**
     * Consulta la pratica con i soli dati visibili al Banco, alla OPT titolare o all'erogatore
     * assegnato. Una consultazione conserva lo stato corrente.
     *
     * @param attore identità verificata del richiedente
     * @param richiestaId identificativo della pratica
     * @return dettaglio filtrato per il richiedente
     * @throws SecurityException se l'attore non ha accesso alla pratica
     * @throws NoSuchElementException se la pratica non esiste
     */
    RichiestaDettaglio consultaRichiesta(IdentitaAutenticata attore, UUID richiestaId);

    /**
     * Registra la presa in carico esplicita del Banco per una richiesta Creata.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param richiestaId identificativo della richiesta in coda
     * @return richiesta passata a Presa in carico
     * @throws SecurityException se l'attore non è il Banco
     * @throws NoSuchElementException se la pratica non esiste
     * @throws IllegalStateException se la richiesta non è Creata
     */
    RichiestaSintesi prendiInCarico(IdentitaAutenticata attore, UUID richiestaId);

    /**
     * Presenta le opzioni compatibili di una richiesta Presa in carico. Ordina prima per
     * prossimità, poi a pari prossimità per precedenza degli operatori del Banco sulle OPT e
     * infine per minor carico. Il Centro PICUS a distanza è l'alternativa alle opzioni locali
     * quando la misura lo consente.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param richiestaId identificativo della pratica in lavorazione
     * @return candidati ordinati, eventualmente nessuno
     * @throws SecurityException se l'attore non è il Banco
     * @throws NoSuchElementException se la pratica non esiste
     * @throws IllegalStateException se lo stato non consente la ricerca
     */
    List<Candidato> cercaErogatoriOrdinati(IdentitaAutenticata attore, UUID richiestaId);

    /**
     * Registra un contatto di disponibilità e fa avvisare il solo account interpellato.
     * La richiesta resta Presa in carico e può avere al più un contatto in attesa di risposta.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param richiestaId identificativo della pratica
     * @param erogatoreId account dell'erogatore scelto
     * @param ambito opzione compatibile per cui si chiede disponibilità
     * @return contatto creato in attesa, con identificativo proprio
     * @throws SecurityException se l'attore non è il Banco
     * @throws NoSuchElementException se la pratica non esiste
     * @throws IllegalArgumentException se l'opzione scelta non è compatibile
     * @throws IllegalStateException se la richiesta non è Presa in carico o un contatto è già in attesa
     */
    ContattoDisponibilita interpellaErogatore(
            IdentitaAutenticata attore, UUID richiestaId, UUID erogatoreId, AmbitoErogazione ambito);

    /**
     * Elenca per il Banco i contatti di una richiesta, comprese le risposte già acquisite.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param richiestaId identificativo della pratica
     * @return storico dei contatti in ordine di invio, eventualmente vuoto
     * @throws SecurityException se l'attore non è il Banco
     * @throws NoSuchElementException se la pratica non esiste
     */
    List<ContattoDisponibilita> elencoContatti(IdentitaAutenticata attore, UUID richiestaId);

    /**
     * Fornisce al solo account interpellato i dati minimi per valutare la disponibilità.
     *
     * @param attore identità verificata della OPT o dell'operatore destinatario
     * @param contattoId identificativo del contatto ricevuto
     * @return contatto e informazioni sintetiche sulla misura richiesta
     * @throws SecurityException se il contatto è indirizzato a un altro account
     * @throws NoSuchElementException se il contatto non esiste
     */
    DettaglioContatto consultaContatto(IdentitaAutenticata attore, UUID contattoId);

    /**
     * Registra la risposta dell'account interpellato a quello specifico contatto. La risposta
     * è visibile al Banco e la richiesta resta Presa in carico.
     *
     * @param attore identità verificata del destinatario del contatto
     * @param contattoId identificativo del contatto a cui si risponde
     * @param disponibile indica se l'erogatore accetta di proseguire il coordinamento
     * @return contatto con la risposta registrata
     * @throws SecurityException se il contatto è indirizzato a un altro account
     * @throws NoSuchElementException se il contatto non esiste
     * @throws IllegalStateException se il contatto non è in attesa o la richiesta non è Presa in carico
     */
    ContattoDisponibilita rispondiDisponibilita(IdentitaAutenticata attore, UUID contattoId, boolean disponibile);

    /**
     * Assegna la richiesta Presa in carico all'erogatore che ha risposto positivamente
     * al contatto. Il passaggio a In programmazione registra l'assegnazione nello stesso esito.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param contattoId identificativo del contatto con l'erogatore disponibile
     * @return richiesta passata a In programmazione
     * @throws SecurityException se l'attore non è il Banco
     * @throws NoSuchElementException se il contatto non esiste
     * @throws IllegalStateException se la richiesta non è Presa in carico,
     *                               il contatto non attesta disponibilità o l'erogatore non è più abilitato
     */
    RichiestaSintesi assegnaErogatore(IdentitaAutenticata attore, UUID contattoId);

    /**
     * Conferma l'accordo con l'erogatore già assegnato. La richiesta diventa Confermata e i
     * dettagli completi e immutabili diventano consultabili nello stesso esito dalla OPT titolare
     * e dall'erogatore assegnato, oltre che dal Banco.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param richiestaId identificativo della pratica In programmazione
     * @param accordo dettagli concordati per la modalità prescelta
     * @return dettaglio della richiesta confermata
     * @throws SecurityException se l'attore non è il Banco
     * @throws NoSuchElementException se la pratica non esiste
     * @throws IllegalArgumentException se mancano dettagli o la modalità è incompatibile
     * @throws IllegalStateException se la richiesta non è In programmazione con un erogatore assegnato
     */
    RichiestaDettaglio confermaAccordo(IdentitaAutenticata attore, UUID richiestaId, DettagliAccordo accordo);

    /**
     * Registra il rifiuto definitivo deciso dal Banco quando mancano soluzioni praticabili.
     * La OPT titolare vede l'esito Rifiutata con il messaggio «Nessun erogatore disponibile».
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param richiestaId identificativo della pratica
     * @return richiesta passata a Rifiutata
     * @throws SecurityException se l'attore non è il Banco
     * @throws NoSuchElementException se la pratica non esiste
     * @throws IllegalStateException se la richiesta non è Presa in carico o In programmazione
     */
    RichiestaSintesi rifiutaRichiesta(IdentitaAutenticata attore, UUID richiestaId);

    /**
     * Annulla la richiesta in qualsiasi stato per il Banco, la OPT titolare o l'erogatore assegnato.
     * Conserva motivo e dettagli, chiude la ricerca e fa avvisare i soggetti coinvolti.
     * Una ripetizione autorizzata su una richiesta già Annullata restituisce l'esito esistente.
     *
     * @param attore identità verificata dell'account coinvolto
     * @param richiestaId identificativo della pratica
     * @param motivo motivo dell'annullamento, facoltativo solo partendo da Creata
     * @return richiesta in stato Annullata
     * @throws SecurityException se l'attore non è autorizzato sulla pratica
     * @throws NoSuchElementException se la pratica non esiste
     * @throws IllegalArgumentException se manca il motivo quando richiesto
     */
    RichiestaSintesi annullaRichiesta(IdentitaAutenticata attore, UUID richiestaId, String motivo);

    /**
     * Dati ammessi dal modulo di invio. L'ingresso web deve rifiutare campi identificativi
     * aggiuntivi senza riportarne i valori nelle risposte, nella persistenza o nei log.
     *
     * @param misuraId identificativo della misura attiva
     * @param tipoDestinatario distingue una persona da un nucleo
     * @param comuneCodice codice del comune della richiesta
     * @param description testo semplice senza identificativi degli assistiti, oppure {@code null}
     */
    record DatiInvio(long misuraId, TipoDestinatario tipoDestinatario, String comuneCodice, String description) {}

    /**
     * Riga della coda o degli elenchi delle richieste.
     *
     * @param id identificativo della richiesta assegnato dal sistema
     * @param inviataIl istante di invio
     * @param misuraId riferimento stabile alla misura
     * @param nomeMisura denominazione della misura
     * @param stato stato corrente della richiesta
     */
    record RichiestaSintesi(UUID id, Instant inviataIl, long misuraId, String nomeMisura, StatoRichiesta stato) {}

    /**
     * Dati della pratica visibili al Banco, alla OPT titolare o all'erogatore assegnato.
     *
     * @param richiesta dati di sintesi
     * @param titolareId account della OPT che ha inviato la richiesta, visibile a titolare e Banco,
     *                   oppure {@code null} quando la consultazione è dell'erogatore
     * @param tipoDestinatario persona o nucleo
     * @param comuneCodice comune del bisogno
     * @param description descrizione riservata a titolare e Banco, oppure {@code null} se assente o riservata
     * @param erogatoreAssegnatoId account assegnato, oppure {@code null} prima dell'assegnazione
     * @param accordo dettagli della conferma, se acquisiti, anche dopo annullamento, altrimenti {@code null}
     */
    record RichiestaDettaglio(
            RichiestaSintesi richiesta,
            UUID titolareId,
            TipoDestinatario tipoDestinatario,
            String comuneCodice,
            String description,
            UUID erogatoreAssegnatoId,
            DettagliAccordo accordo) {}

    /**
     * Candidato presentato al Banco per una specifica opzione compatibile.
     *
     * @param erogatore opzione compatibile restituita da Catalogo
     * @param caricoAppuntamenti conteggio degli appuntamenti assegnati usato per l'ordinamento
     */
    record Candidato(ErogatoreCompatibile erogatore, int caricoAppuntamenti) {}

    /**
     * Contatto con un singolo erogatore e relativo esito.
     *
     * @param id identificativo specifico del contatto
     * @param richiestaId richiesta per cui si valuta la disponibilità
     * @param erogatoreId account interpellato
     * @param ambito opzione per cui è stato interpellato
     * @param esito esito del contatto, distinto dallo stato della richiesta
     */
    record ContattoDisponibilita(
            UUID id, UUID richiestaId, UUID erogatoreId, AmbitoErogazione ambito, EsitoContatto esito) {}

    /**
     * Proiezione minima della richiesta per verificare la disponibilità.
     *
     * @param contatto contatto ricevuto dall'account interpellato
     * @param misuraId riferimento alla misura
     * @param nomeMisura denominazione della misura
     * @param tipoDestinatario persona o nucleo
     * @param comuneCodice comune della richiesta
     */
    record DettaglioContatto(
            ContattoDisponibilita contatto,
            long misuraId,
            String nomeMisura,
            TipoDestinatario tipoDestinatario,
            String comuneCodice) {}

    /** Dettagli dell'accordo organizzati per modalità di erogazione. */
    sealed interface DettagliAccordo permits AccordoInPresenza, AccordoADistanza {}

    /**
     * Accordo per un appuntamento in presenza. Nome, cognome e telefono sono i soli dati del
     * referente mostrati alla OPT titolare per l'appuntamento.
     *
     * @param dataOra istante concordato
     * @param luogo luogo dell'appuntamento
     * @param nomeReferente nome del referente
     * @param cognomeReferente cognome del referente
     * @param telefonoReferente recapito telefonico del referente
     */
    record AccordoInPresenza(
            Instant dataOra, String luogo, String nomeReferente, String cognomeReferente, String telefonoReferente)
            implements DettagliAccordo {}

    /**
     * Accordo per un appuntamento a distanza.
     *
     * @param dataOra istante concordato
     * @param link collegamento per l'appuntamento
     */
    record AccordoADistanza(Instant dataOra, URI link) implements DettagliAccordo {}

    /** Stati funzionali dell'unico ciclo della richiesta. */
    enum StatoRichiesta {
        /** Ricevuta e in attesa della presa in carico del Banco. */
        CREATA,
        /** In lavorazione e in ricerca di un erogatore disponibile. */
        PRESA_IN_CARICO,
        /** Erogatore assegnato e dettagli in via di definizione. */
        IN_PROGRAMMAZIONE,
        /** Accordo confermato dal Banco. */
        CONFERMATA,
        /** Ricerca chiusa dal Banco per assenza di soluzioni praticabili. */
        RIFIUTATA,
        /** Richiesta chiusa da un account coinvolto e autorizzato. */
        ANNULLATA
    }

    /** Tipo di destinatario della misura, senza identificativi personali. */
    enum TipoDestinatario {
        /** Singola persona. */
        PERSONA,
        /** Nucleo familiare. */
        NUCLEO
    }

    /** Esiti del contatto di disponibilità. */
    enum EsitoContatto {
        /** Il contatto è stato inviato e nessuna risposta è stata acquisita. */
        IN_ATTESA,
        /** L'erogatore ha dato disponibilità. */
        DISPONIBILE,
        /** L'erogatore ha declinato il contatto. */
        NON_DISPONIBILE
    }
}
