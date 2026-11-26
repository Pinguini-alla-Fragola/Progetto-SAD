package it.unina.picus.comunicazioni.interno.persistenza;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni.RicezioneDestinatario;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

/**
 * Test black box di {@link ArchivioComunicazioni#ricezioni} su PostgreSQL (#145, codice della #144 nella
 * PR #193), ricavati dal Javadoc della porta.
 *
 * Dal Javadoc: vuoto se la comunicazione manca; altrimenti l'elenco, anche vuoto, dello stato di ricezione di
 * tutti i destinatari registrati all'invio, con l'istante della prima conferma oppure null se in attesa.
 *
 * Le comunicazioni sono scritte direttamente nelle tabelle della V18, perché l'oggetto del test è la sola
 * lettura dei riscontri.
 */
@IntegrazionePostgresql
@Tag("issue-145")
@DisplayName("#145 Lettura dei riscontri dall'archivio su PostgreSQL")
class ArchivioRicezioniJpaTest {

    @Autowired
    private ArchivioComunicazioni archivio;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transazioni;

    private UUID banco;
    private UUID optA;
    private UUID optB;
    private UUID operatore;

    @BeforeEach
    void prepara() {
        svuotaDati();
        DatiSintetici dati = new DatiSintetici(jdbc);
        banco = dati.banco("Banco di prova");
        optA = dati.opt("OPT A", "napoli");
        optB = dati.opt("OPT B", "napoli");
        operatore = dati.operatore("Operatore", "INTERNO_BANCO", "napoli", false);
    }

    @AfterEach
    void svuotaDati() {
        new TransactionTemplate(transazioni).executeWithoutResult(stato -> new DatiSintetici(jdbc).svuota());
    }

    @Test
    @DisplayName("Comunicazione inesistente: risultato vuoto")
    void comunicazioneInesistente() {
        assertThat(archivio.ricezioni(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Comunicazione senza destinatari: elenco presente ma vuoto, distinto dall'assenza")
    void senzaDestinatari() {
        UUID comunicazione = comunicazione();

        assertThat(archivio.ricezioni(comunicazione)).hasValueSatisfying(elenco -> assertThat(elenco).isEmpty());
    }

    @Test
    @DisplayName("AC2 - destinatari tutti in attesa subito dopo l'invio")
    void tuttiInAttesa() {
        UUID comunicazione = comunicazione(optA, optB, operatore);

        assertThat(archivio.ricezioni(comunicazione)).hasValueSatisfying(elenco -> assertThat(elenco)
                .containsExactlyInAnyOrder(
                        new RicezioneDestinatario(optA, null),
                        new RicezioneDestinatario(optB, null),
                        new RicezioneDestinatario(operatore, null)));
    }

    @Test
    @DisplayName("AC2 - un destinatario confermato con il suo istante e gli altri ancora in attesa")
    void confermatoEInAttesa() {
        UUID comunicazione = comunicazione(optA, optB);
        Instant istante = conferma(comunicazione, optA, Instant.parse("2026-11-22T10:15:30.123456Z"));

        assertThat(archivio.ricezioni(comunicazione)).hasValueSatisfying(elenco -> assertThat(elenco)
                .containsExactlyInAnyOrder(
                        new RicezioneDestinatario(optA, istante),
                        new RicezioneDestinatario(optB, null)));
    }

    @Test
    @DisplayName("Isolamento: i destinatari e le conferme di un'altra comunicazione non compaiono")
    void isolamentoFraComunicazioni() {
        UUID comunicazione = comunicazione(optA);
        UUID altra = comunicazione(optA, optB, operatore);
        conferma(altra, optA, Instant.parse("2026-11-22T09:00:00Z"));

        assertThat(archivio.ricezioni(comunicazione)).hasValueSatisfying(elenco -> assertThat(elenco)
                .containsExactly(new RicezioneDestinatario(optA, null)));
    }

    @Test
    @DisplayName("Un destinatario revocato dopo l'invio resta fra i riscontri con il suo stato")
    void destinatarioRevocato() {
        UUID comunicazione = comunicazione(optA, operatore);
        Instant istante = conferma(comunicazione, operatore, Instant.parse("2026-11-22T11:00:00Z"));
        new DatiSintetici(jdbc).impostaStatoAccount(operatore, "REVOCATO");

        assertThat(archivio.ricezioni(comunicazione)).hasValueSatisfying(elenco -> assertThat(elenco)
                .containsExactlyInAnyOrder(
                        new RicezioneDestinatario(optA, null),
                        new RicezioneDestinatario(operatore, istante)));
    }

    @Test
    @DisplayName("La lettura non modifica i riscontri salvati")
    void letturaSenzaEffetti() {
        UUID comunicazione = comunicazione(optA, optB);
        Instant istante = conferma(comunicazione, optB, Instant.parse("2026-11-22T12:00:00Z"));

        archivio.ricezioni(comunicazione);
        archivio.ricezioni(comunicazione);

        assertThat(confermataIl(comunicazione, optA)).isNull();
        assertThat(confermataIl(comunicazione, optB)).isEqualTo(istante);
    }

    // ------------------------------------------------------------------ supporto

    private UUID comunicazione(UUID... destinatari) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into comunicazioni (id, mittente_id, titolo, testo, tipo_destinatari, inviata_il)
                values (?, ?, 'Avviso di prova', 'Testo sintetico', 'GRUPPO_OPT', ?)
                """, id, banco, Timestamp.from(Instant.parse("2026-11-22T08:00:00Z")));
        for (UUID destinatario : destinatari) {
            jdbc.update("insert into comunicazioni_destinatari (comunicazione_id, destinatario_id) values (?, ?)",
                    id, destinatario);
        }
        return id;
    }

    /** Scrive la conferma e restituisce l'istante come salvato dal database. */
    private Instant conferma(UUID comunicazione, UUID destinatario, Instant istante) {
        jdbc.update("""
                update comunicazioni_destinatari set confermata_il = ?
                where comunicazione_id = ? and destinatario_id = ?
                """, Timestamp.from(istante), comunicazione, destinatario);
        return confermataIl(comunicazione, destinatario);
    }

    private Instant confermataIl(UUID comunicazione, UUID destinatario) {
        List<OffsetDateTime> valori = jdbc.queryForList("""
                select confermata_il from comunicazioni_destinatari
                where comunicazione_id = ? and destinatario_id = ?
                """, OffsetDateTime.class, comunicazione, destinatario);
        return valori.isEmpty() || valori.getFirst() == null ? null : valori.getFirst().toInstant();
    }
}