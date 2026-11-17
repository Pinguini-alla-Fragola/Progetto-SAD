package it.unina.picus;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dati sintetici inseriti direttamente nel database per i test di integrazione.
 *
 * Coprono ciò che l'applicazione non permette ancora di creare dalle sue operazioni:
 * account, profili, offerte e misure. Richieste e contatti nascono invece dal flusso reale
 * (invio, presa in carico, contatto); i metodi che li inseriscono direttamente servono solo
 * alla prova della migrazione. Ogni metodo genera identificativi nuovi, quindi i test non
 * dipendono l'uno dall'altro né dall'ordine di esecuzione.
 */
public final class DatiDiProva {

    /** Nome delle misure create da {@link #creaMisura()}. */
    public static final String NOME_MISURA_DI_PROVA = "Misura di prova";

    private final JdbcTemplate jdbc;
    private final String schema;

    /** Dati nello schema predefinito dell'applicazione. */
    public DatiDiProva(JdbcTemplate jdbc) {
        this(jdbc, "");
    }

    /**
     * @param schema prefisso dello schema con il punto finale (per esempio {@code "verifica_v6."}),
     *               oppure stringa vuota per lo schema predefinito
     */
    public DatiDiProva(JdbcTemplate jdbc, String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    /** Account attivo senza profilo. Il ruolo è BANCO, OPT oppure OPERATORE. */
    public UUID creaAccount(String ruolo, String nome) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into " + schema + "utenze_account (id, email, password_hash, nome, ruolo, stato)"
                        + " values (?, ?, 'accesso-non-previsto-nei-test', ?, ?, 'ATTIVO')",
                id, emailDi(id), nome, ruolo);
        return id;
    }

    /** OPT con profilo organizzativo nel comune di Napoli: può essere titolare di una richiesta. */
    public UUID creaOptConProfilo(String nome) {
        UUID id = creaAccount("OPT", nome);
        jdbc.update(
                "insert into " + schema + "utenze_profili_opt (account_id, codice_struttura, comune_codice)"
                        + " values (?, ?, 'napoli')",
                id, "T-" + id.toString().substring(0, 8));
        return id;
    }

    /** Indirizzo con cui l'account di prova accede: deriva dall'identificativo. */
    public static String emailDi(UUID account) {
        return account + "@picus.test";
    }

    /** Misura attiva nuova: i candidati della richiesta sono solo quelli creati dal test. */
    public long creaMisura() {
        return creaMisura(false);
    }

    /** Misura attiva nuova erogabile anche a distanza: ammette l'opzione del Centro PICUS. */
    public long creaMisuraADistanza() {
        return creaMisura(true);
    }

    private long creaMisura(boolean erogabileADistanza) {
        String codice = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        return jdbc.queryForObject(
                "insert into " + schema + "catalogo_misure (codice, nome, descrizione, erogabile_a_distanza)"
                        + " values (?, '" + NOME_MISURA_DI_PROVA + "', 'Misura sintetica per i test', ?) returning id",
                Long.class, codice, erogabileADistanza);
    }

    /** Cambia l'erogabilità a distanza di una misura, come farebbe il Banco aggiornando il catalogo. */
    public void impostaErogabileADistanza(long misuraId, boolean erogabile) {
        jdbc.update("update " + schema + "catalogo_misure set erogabile_a_distanza = ? where id = ?",
                erogabile, misuraId);
    }

    /** Comune nuovo senza adiacenze: chi vi risiede non è un'opzione locale per le richieste di Napoli. */
    public String creaComuneIsolato() {
        String codice = "isolato-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into " + schema + "catalogo_comuni (codice, nome) values (?, 'Comune isolato di prova')",
                codice);
        return codice;
    }

    /** OPT di Napoli che dichiara di erogare la misura: compare fra i candidati dello stesso comune. */
    public UUID creaOptErogatrice(String nome, long misuraId) {
        UUID id = creaOptConProfilo(nome);
        jdbc.update(
                "insert into " + schema + "catalogo_offerte_opt (opt_id, misura_id) values (?, ?)", id, misuraId);
        return id;
    }

    /** Operatore interno del Banco con territorio Napoli, senza misure nel profilo. */
    public UUID creaOperatore(String nome) {
        UUID id = creaAccount("OPERATORE", nome);
        jdbc.update(
                "insert into " + schema + "utenze_profili_operatore (account_id, tipologia, comune_codice)"
                        + " values (?, 'INTERNO_BANCO', 'napoli')",
                id);
        return id;
    }

    /** Operatore interno del Banco abilitato alla misura: compare fra i candidati dello stesso comune. */
    public UUID creaOperatoreErogatore(String nome, long misuraId) {
        UUID id = creaOperatore(nome);
        jdbc.update(
                "insert into " + schema + "utenze_operatori_misure (account_id, misura_id) values (?, ?)",
                id, misuraId);
        return id;
    }

    /**
     * Operatore del Centro PICUS nel comune indicato, abilitato alla misura. Se il comune è isolato
     * e la misura è erogabile a distanza, l'unica opzione che offre è {@code CENTRO_PICUS_A_DISTANZA}.
     */
    public UUID creaOperatoreCentroPicus(String nome, long misuraId, String comune) {
        UUID id = creaAccount("OPERATORE", nome);
        jdbc.update(
                "insert into " + schema + "utenze_profili_operatore (account_id, tipologia, comune_codice, centro_picus)"
                        + " values (?, 'INTERNO_BANCO', ?, true)",
                id, comune);
        jdbc.update(
                "insert into " + schema + "utenze_operatori_misure (account_id, misura_id) values (?, ?)",
                id, misuraId);
        return id;
    }

    /** Revoca l'accesso dell'account, come farebbe il Banco. */
    public void revoca(UUID account) {
        jdbc.update("update " + schema + "utenze_account set stato = 'REVOCATO' where id = ?", account);
    }

    /** Richiesta per una persona a Napoli sulla misura M01, nello stato indicato. */
    public UUID creaRichiesta(UUID titolare, String stato, String description) {
        Long misuraId = jdbc.queryForObject(
                "select id from " + schema + "catalogo_misure where codice = 'M01'", Long.class);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into " + schema + "richieste"
                        + " (id, titolare_id, misura_id, comune_codice, tipo_destinatario, description, inviata_il, stato)"
                        + " values (?, ?, ?, 'napoli', 'PERSONA', ?, now(), ?)",
                id, titolare, misuraId, description, stato);
        return id;
    }

    /** Contatto in attesa per l'erogatore, inviato il numero di minuti fa indicato. */
    public UUID creaContatto(UUID richiestaId, UUID erogatore, int minutiFa) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into " + schema + "richieste_contatti_disponibilita"
                        + " (id, richiesta_id, erogatore_id, ambito, nome_misura, inviato_il)"
                        + " values (?, ?, ?, 'STESSO_COMUNE', 'Accoglienza e ascolto', now() - (? * interval '1 minute'))",
                id, richiestaId, erogatore, minutiFa);
        return id;
    }

    /**
     * Forza lo stato della richiesta, per provare la difesa del server su stati che il flusso non
     * permette di raggiungere. Per {@code CONFERMATA} completa anche erogatore e accordo, che il
     * vincolo della V14 rende obbligatori: come erogatore usa l'ultimo contattato.
     */
    public void impostaStato(UUID richiestaId, String stato) {
        if (!"CONFERMATA".equals(stato)) {
            jdbc.update("update " + schema + "richieste set stato = ? where id = ?", stato, richiestaId);
            return;
        }
        jdbc.update(
                "update " + schema + "richieste r set stato = 'CONFERMATA',"
                        + " erogatore_id = coalesce(r.erogatore_id, (select c.erogatore_id from " + schema
                        + "richieste_contatti_disponibilita c where c.richiesta_id = r.id"
                        + " order by c.inviato_il desc limit 1)),"
                        + " erogatore_ambito = coalesce(r.erogatore_ambito, 'STESSO_COMUNE'),"
                        + " accordo_modalita = 'IN_PRESENZA', accordo_data_ora = now() + interval '30 days',"
                        + " accordo_luogo = 'Sede di prova', accordo_nome_referente = 'Referente',"
                        + " accordo_cognome_referente = 'Sintetico', accordo_telefono_referente = '0810000000',"
                        + " accordo_link = null"
                        + " where r.id = ?",
                richiestaId);
    }

    public String esitoContatto(UUID contattoId) {
        return jdbc.queryForObject(
                "select esito from " + schema + "richieste_contatti_disponibilita where id = ?",
                String.class, contattoId);
    }

    public String statoRichiesta(UUID richiestaId) {
        return jdbc.queryForObject(
                "select stato from " + schema + "richieste where id = ?", String.class, richiestaId);
    }

    /**
     * Stato e colonne dell'accordo come sono salvati, per verificare che un comando respinto non
     * abbia lasciato scritture parziali.
     */
    public Map<String, Object> accordoPersistito(UUID richiestaId) {
        return jdbc.queryForMap(
                "select stato, accordo_modalita, accordo_data_ora, accordo_luogo, accordo_nome_referente,"
                        + " accordo_cognome_referente, accordo_telefono_referente, accordo_link"
                        + " from " + schema + "richieste where id = ?",
                richiestaId);
    }

    /** Erogatore assegnato alla richiesta, oppure {@code null} se non è stato assegnato nessuno. */
    public UUID erogatoreAssegnato(UUID richiestaId) {
        return jdbc.queryForObject(
                "select erogatore_id from " + schema + "richieste where id = ?", UUID.class, richiestaId);
    }
}