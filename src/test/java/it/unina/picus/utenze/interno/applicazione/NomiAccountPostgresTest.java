package it.unina.picus.utenze.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
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

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.utenze.api.NomiAccountApi;

/**
 * Test black box di {@link NomiAccountApi#nomiVisualizzati} su PostgreSQL (#145, codice della #144 nella
 * PR #193), usato dalla vista dei riscontri del Banco.
 *
 * Dal Javadoc: legge in blocco il nome visualizzato degli account indicati, anche se revocati, e non
 * restituisce voci per gli account sconosciuti.
 */
@IntegrazionePostgresql
@Tag("issue-145")
@DisplayName("#145 Nomi visualizzati degli account destinatari su PostgreSQL")
class NomiAccountPostgresTest {

    @Autowired
    private NomiAccountApi nomiAccount;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transazioni;

    private DatiSintetici dati;
    private UUID opt;
    private UUID operatore;
    private UUID banco;

    @BeforeEach
    void prepara() {
        svuotaDati();
        dati = new DatiSintetici(jdbc);
        banco = dati.banco("Banco di prova");
        opt = dati.opt("OPT Centro Ascolto", "napoli");
        operatore = dati.operatore("Operatore Rossi", "ESTERNO_PAGATO_BANCO", "napoli", false);
    }

    @AfterEach
    void svuotaDati() {
        new TransactionTemplate(transazioni).executeWithoutResult(stato -> new DatiSintetici(jdbc).svuota());
    }

    @Test
    @DisplayName("Nomi di OPT e operatori richiesti insieme")
    void nomiInBlocco() {
        assertThat(nomiAccount.nomiVisualizzati(Set.of(opt, operatore)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(opt, "OPT Centro Ascolto", operatore, "Operatore Rossi"));
    }

    @Test
    @DisplayName("Solo gli account richiesti: gli altri esistenti non compaiono")
    void soloIRichiesti() {
        assertThat(nomiAccount.nomiVisualizzati(Set.of(opt))).containsOnlyKeys(opt);
    }

    @Test
    @DisplayName("Account revocato: il nome resta disponibile per lo storico dei riscontri")
    void accountRevocato() {
        dati.impostaStatoAccount(operatore, "REVOCATO");

        assertThat(nomiAccount.nomiVisualizzati(Set.of(operatore))).containsEntry(operatore, "Operatore Rossi");
    }

    @Test
    @DisplayName("Account sconosciuto: nessuna voce, gli altri nomi restano")
    void accountSconosciuto() {
        UUID sconosciuto = UUID.randomUUID();

        assertThat(nomiAccount.nomiVisualizzati(Set.of(opt, sconosciuto))).containsOnlyKeys(opt);
    }

    @Test
    @DisplayName("Insieme vuoto: mappa vuota")
    void insiemeVuoto() {
        assertThat(nomiAccount.nomiVisualizzati(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("Omonimi: due account con lo stesso nome restano due voci distinte")
    void omonimi() {
        UUID omonimo = dati.operatore("Operatore Rossi", "INTERNO_BANCO", "napoli", false);

        assertThat(nomiAccount.nomiVisualizzati(Set.of(operatore, omonimo)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(operatore, "Operatore Rossi", omonimo, "Operatore Rossi"));
    }

    @Test
    @DisplayName("Il Banco ha un nome come ogni altro account")
    void banco() {
        assertThat(nomiAccount.nomiVisualizzati(Set.of(banco))).containsEntry(banco, "Banco di prova");
    }
}