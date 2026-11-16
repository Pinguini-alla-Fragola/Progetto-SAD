package it.unina.picus.utenze.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fornisce identità verificate e le informazioni minime degli account necessarie ai moduli PICUS.
 * Il chiamante applica i controlli di titolarità, assegnazione o ricezione sulla propria risorsa.
 */
public interface UtenzeApi extends SessioniApi {

    /**
     * Verifica le credenziali per l'apertura di una sessione di un account attivo.
     *
     * @param email indirizzo presentato per l'accesso
     * @param password credenziale da verificare
     * @return identità verificata oppure vuoto per credenziali errate o account non attivo
     */
    Optional<IdentitaAutenticata> verificaCredenziali(String email, String password);

    /**
     * Conferma l'identità associata a una sessione valida, verificando lo stato corrente dell'account.
     *
     * @param accountId identificativo ricavato dal server dalla sessione, dopo la sua verifica
     * @return identità corrente oppure vuoto se l'account non è più attivo o non esiste
     */
    Optional<IdentitaAutenticata> verificaAccountSessione(UUID accountId);

    /**
     * Cerca i dati minimi di un account anche per visualizzare un riferimento storico.
     *
     * @param accountId identificativo dell'account da consultare
     * @return account sintetico, oppure vuoto se l'identificativo è sconosciuto
     */
    Optional<AccountSintetico> cercaAccount(UUID accountId);

    /**
     * Elenca gli account attivi di un ruolo per risolvere una selezione di destinatari.
     *
     * @param ruolo categoria degli account da cercare
     * @return account attivi della categoria, eventualmente nessuno
     */
    List<AccountSintetico> elencoAccountAttivi(RuoloUtente ruolo);

    /**
     * Cerca il profilo di un soggetto abilitato a erogare, per esempio di una OPT con un'offerta.
     *
     * @param accountId identificativo dell'account organizzativo o personale
     * @return profilo minimo, oppure vuoto se l'account manca, non è attivo o non è un erogatore
     */
    Optional<ProfiloErogatore> cercaProfiloErogatoreAttivo(UUID accountId);

    /**
     * Elenca gli operatori attivi del Banco il cui profilo comprende la misura indicata.
     *
     * @param misuraId identificativo della misura di Catalogo
     * @return profili degli operatori compatibili con la misura, eventualmente nessuno
     */
    List<ProfiloErogatore> elencoOperatoriAttiviPerMisura(long misuraId);

    /**
     * Identità di chi esegue un caso d'uso. Il server la ricava dall'autenticazione o dalla sessione
     * verificata. Il client può identificare una risorsa, mentre l'attore è stabilito dal server.
     *
     * @param accountId identificativo dell'account autenticato
     * @param ruolo ruolo verificato
     */
    record IdentitaAutenticata(UUID accountId, RuoloUtente ruolo) {}

    /**
     * Riferimento minimo a un account, utilizzabile nelle selezioni e negli storici.
     *
     * @param id identificativo stabile dell'account
     * @param nomeVisualizzato nome utilizzato nelle liste
     * @param ruolo ruolo dell'account
     * @param stato stato corrente dell'utenza
     */
    record AccountSintetico(UUID id, String nomeVisualizzato, RuoloUtente ruolo, StatoUtenza stato) {}

    /**
     * Dati di profilo utilizzati dal Catalogo per individuare gli erogatori compatibili.
     *
     * @param accountId identificativo dell'OPT o dell'operatore
     * @param nomeVisualizzato nome dell'erogatore mostrato al Banco
     * @param tipo tipologia funzionale dell'erogatore
     * @param comuneCodice comune dell'OPT o territorio di riferimento dell'operatore
     * @param centroPicus indica l'appartenenza di un operatore al Centro PICUS
     */
    record ProfiloErogatore(
            UUID accountId, String nomeVisualizzato, TipoErogatore tipo, String comuneCodice, boolean centroPicus) {}

    /** Ruoli di accesso a PICUS. */
    enum RuoloUtente {
        /** Account di coordinamento del Banco. */
        BANCO,
        /** Account unico dell'organizzazione partner territoriale. */
        OPT,
        /** Account personale di un operatore interno o esterno pagato dal Banco. */
        OPERATORE
    }

    /** Stati di abilitazione dell'account, allineati alla persistenza di Utenze. */
    enum StatoUtenza {
        /** Utenza creata che deve ancora essere attivata. */
        IN_ATTESA_DI_ATTIVAZIONE,
        /** Utenza abilitata all'accesso. */
        ATTIVO,
        /** Utenza il cui accesso è stato revocato. */
        REVOCATO
    }

    /** Tipologie di erogatore rilevanti per la ricerca del Banco. */
    enum TipoErogatore {
        /** OPT, anche quando si avvale di un professionista. */
        OPT,
        /** Operatore interno del Banco. */
        INTERNO_BANCO,
        /** Operatore esterno pagato dal Banco. */
        ESTERNO_PAGATO_BANCO
    }
}
