package it.unina.picus.shared;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fixture sintetiche scritte direttamente su PostgreSQL per i test di integrazione.
 *
 * <p>Usa solo dati inventati: nomi generici, email sul dominio riservato {@code picus.test} e
 * nessun identificativo di persone assistite. Le password non sono mai verificate nei test, che
 * simulano l'utente autenticato con l'UUID dell'account.</p>
 */
public final class DatiSintetici {

    /** Comune censito ma non adiacente ad alcun altro comune del catalogo. */
    public static final String COMUNE_LONTANO = "lontano";

    private final JdbcTemplate jdbc;

    public DatiSintetici(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Riporta le tabelle dinamiche allo stato iniziale delle migrazioni, catalogo compreso.
     *
     * <p>Agisce sull'intero database condiviso: usarla solo da classi annotate con
     * {@link IntegrazionePostgresql}, il cui lock JUnit impedisce che un'altra classe stia
     * preparando o leggendo dati nello stesso momento.</p>
     */
    public void svuota() {
        jdbc.update("delete from comunicazioni_avvisi_operativi");
        jdbc.update("delete from richieste_contatti_disponibilita");
        jdbc.update("delete from richieste");
        jdbc.update("delete from catalogo_offerte_opt");
        jdbc.update("delete from utenze_operatori_misure");
        jdbc.update("delete from utenze_profili_operatore");
        jdbc.update("delete from utenze_profili_opt");
        jdbc.update("delete from utenze_account");
        jdbc.update("update catalogo_misure set attiva = true, erogabile_a_distanza = false");
        jdbc.update("""
                insert into catalogo_comuni (codice, nome) values (?, 'Comune lontano')
                on conflict (codice) do nothing
                """, COMUNE_LONTANO);
    }

    public long misura(String codice) {
        return jdbc.queryForObject("select id from catalogo_misure where codice = ?", Long.class, codice);
    }

    public void abilitaADistanza(long misuraId) {
        jdbc.update("update catalogo_misure set erogabile_a_distanza = true where id = ?", misuraId);
    }

    public UUID banco(String nome) {
        return account(nome, "BANCO");
    }

    public UUID opt(String nome, String comune) {
        UUID id = account(nome, "OPT");
        jdbc.update("insert into utenze_profili_opt (account_id, codice_struttura, comune_codice) values (?, ?, ?)",
                id, "S-" + id.toString().replace("-", "").substring(0, 20), comune);
        return id;
    }

    public void offerta(UUID opt, long misuraId) {
        jdbc.update("insert into catalogo_offerte_opt (opt_id, misura_id, attiva) values (?, ?, true)", opt, misuraId);
    }

    public void ritiraOfferta(UUID opt, long misuraId) {
        jdbc.update("update catalogo_offerte_opt set attiva = false where opt_id = ? and misura_id = ?", opt, misuraId);
    }

    /**
     * Crea un operatore attivo con le sole misure indicate nel profilo.
     *
     * @param tipologia {@code INTERNO_BANCO} o {@code ESTERNO_PAGATO_BANCO}
     */
    public UUID operatore(String nome, String tipologia, String comune, boolean centroPicus, long... misure) {
        UUID id = account(nome, "OPERATORE");
        jdbc.update("""
                insert into utenze_profili_operatore (account_id, tipologia, comune_codice, centro_picus)
                values (?, ?, ?, ?)
                """, id, tipologia, comune, centroPicus);
        for (long misura : misure) {
            jdbc.update("insert into utenze_operatori_misure (account_id, misura_id) values (?, ?)", id, misura);
        }
        return id;
    }

    public void impostaStatoAccount(UUID account, String stato) {
        jdbc.update("update utenze_account set stato = ? where id = ?", stato, account);
    }

    /** Richiesta senza assegnazione né accordo, nello stato indicato. */
    public UUID richiesta(UUID titolare, long misuraId, String comune, String stato, Instant inviataIl) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario,
                                       description, inviata_il, stato)
                values (?, ?, ?, ?, 'PERSONA', 'Descrizione sintetica riservata', ?, ?)
                """, id, titolare, misuraId, comune, Timestamp.from(inviataIl), stato);
        return id;
    }

    /**
     * Appuntamento in presenza già concordato con l'erogatore: entra nel carico se è
     * {@code CONFERMATA} con data futura.
     *
     * @param stato {@code CONFERMATA} oppure {@code ANNULLATA}
     */
    public UUID appuntamento(UUID titolare, long misuraId, UUID erogatore, Instant dataOra, String stato) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario, inviata_il,
                                       stato, erogatore_id, erogatore_ambito, accordo_modalita, accordo_data_ora,
                                       accordo_luogo, accordo_nome_referente, accordo_cognome_referente,
                                       accordo_telefono_referente)
                values (?, ?, ?, 'napoli', 'PERSONA', ?, ?, ?, 'STESSO_COMUNE', 'IN_PRESENZA', ?,
                        'Sede di esempio', 'Nome', 'Cognome', '+39 000 0000000')
                """, id, titolare, misuraId, Timestamp.from(Instant.parse("2026-01-01T09:00:00Z")),
                stato, erogatore, Timestamp.from(dataOra));
        return id;
    }

    public String stato(UUID richiesta) {
        return jdbc.queryForObject("select stato from richieste where id = ?", String.class, richiesta);
    }

    public UUID erogatoreAssegnato(UUID richiesta) {
        return jdbc.queryForObject("select erogatore_id from richieste where id = ?", UUID.class, richiesta);
    }

    public String ambitoAssegnato(UUID richiesta) {
        return jdbc.queryForObject("select erogatore_ambito from richieste where id = ?", String.class, richiesta);
    }

    public int contatti(UUID richiesta) {
        return conta("select count(*) from richieste_contatti_disponibilita where richiesta_id = ?", richiesta);
    }

    public int contattiInAttesa(UUID richiesta) {
        return conta("select count(*) from richieste_contatti_disponibilita where richiesta_id = ? and esito = 'IN_ATTESA'",
                richiesta);
    }

    public String esitoContatto(UUID contatto) {
        return jdbc.queryForObject("select esito from richieste_contatti_disponibilita where id = ?", String.class,
                contatto);
    }

    public int avvisi(UUID richiesta) {
        return conta("select count(*) from comunicazioni_avvisi_operativi where richiesta_id = ?", richiesta);
    }

    /** Avvisi della pratica coerenti con il destinatario del contatto a cui si riferiscono. */
    public int avvisiCoerentiConIlContatto(UUID richiesta) {
        return conta("""
                select count(*) from comunicazioni_avvisi_operativi a
                join richieste_contatti_disponibilita c
                  on c.id = a.contatto_id and c.richiesta_id = a.richiesta_id and c.erogatore_id = a.destinatario_id
                where a.richiesta_id = ?
                """, richiesta);
    }

    private int conta(String sql, Object... parametri) {
        return jdbc.queryForObject(sql, Integer.class, parametri);
    }

    private UUID account(String nome, String ruolo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, 'accesso-non-previsto-nei-test', ?, ?, 'ATTIVO')
                """, id, id + "@picus.test", nome, ruolo);
        return id;
    }
}
