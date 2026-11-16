package it.unina.picus;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dati sintetici inseriti direttamente nel database per i test di integrazione.
 *
 * <p>Servono finché invio, presa in carico e contatto del candidato (#48, #49) non sono
 * disponibili come operazioni dell'applicazione. Ogni metodo genera identificativi nuovi,
 * quindi i test non dipendono l'uno dall'altro né dall'ordine di esecuzione.</p>
 */
public final class DatiDiProva {

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
                id, id + "@picus.test", nome, ruolo);
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

    public void impostaStato(UUID richiestaId, String stato) {
        jdbc.update("update " + schema + "richieste set stato = ? where id = ?", stato, richiestaId);
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

    /** Erogatore assegnato alla richiesta, oppure {@code null} se non è stato assegnato nessuno. */
    public UUID erogatoreAssegnato(UUID richiestaId) {
        return jdbc.queryForObject(
                "select erogatore_id from " + schema + "richieste where id = ?", UUID.class, richiestaId);
    }
}
