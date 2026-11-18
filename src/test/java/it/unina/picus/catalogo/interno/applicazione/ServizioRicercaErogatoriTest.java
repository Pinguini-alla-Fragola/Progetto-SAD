package it.unina.picus.catalogo.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import it.unina.picus.catalogo.api.RicercaErogatoriApi;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * Compatibilità dei candidati con dati sintetici su PostgreSQL (criterio 2 della #102):
 * offerta OPT e competenze degli operatori, account attivi, prossimità e opzione remota.
 * L'ordinamento finale è verificato nei test di Richieste.
 *
 * <p>Catalogo di base delle migrazioni: Napoli è limitrofa a Portici e a Pozzuoli; il comune
 * sintetico {@value DatiSintetici#COMUNE_LONTANO} non è limitrofo ad alcuno.</p>
 */
@IntegrazionePostgresql
@DisplayName("Ricerca degli erogatori compatibili su PostgreSQL")
class ServizioRicercaErogatoriTest {

    @Autowired
    private RicercaErogatoriApi ricerca;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private long m01;
    private long m02;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        m01 = dati.misura("M01");
        m02 = dati.misura("M02");
    }

    @Test
    @DisplayName("Una OPT è candidata solo con offerta attiva per la misura e account attivo")
    void compatibilitaOfferteOpt() {
        UUID conOfferta = dati.opt("OPT con offerta", "napoli");
        dati.offerta(conOfferta, m01);
        UUID senzaOfferta = dati.opt("OPT senza offerta", "napoli");
        UUID altraMisura = dati.opt("OPT altra misura", "napoli");
        dati.offerta(altraMisura, m02);
        UUID ritirata = dati.opt("OPT offerta ritirata", "napoli");
        dati.offerta(ritirata, m01);
        dati.ritiraOfferta(ritirata, m01);
        UUID revocata = dati.opt("OPT revocata", "napoli");
        dati.offerta(revocata, m01);
        dati.impostaStatoAccount(revocata, "REVOCATO");
        UUID nonAttivata = dati.opt("OPT non attivata", "napoli");
        dati.offerta(nonAttivata, m01);
        dati.impostaStatoAccount(nonAttivata, "IN_ATTESA_DI_ATTIVAZIONE");

        var opzioni = ricerca.cercaErogatoriCompatibili(m01, "napoli");

        assertThat(opzioni).extracting(ErogatoreCompatibile::accountId, ErogatoreCompatibile::tipo,
                ErogatoreCompatibile::ambito)
                .containsExactly(tuple(conOfferta, TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE));
        assertThat(opzioni).extracting(ErogatoreCompatibile::accountId)
                .doesNotContain(senzaOfferta, altraMisura, ritirata, revocata, nonAttivata);
    }

    @Test
    @DisplayName("Un operatore è candidato solo per le misure del proprio profilo e con account attivo")
    void compatibilitaProfiliOperatore() {
        UUID interno = dati.operatore("Operatore interno", "INTERNO_BANCO", "napoli", false, m01, m02);
        UUID esterno = dati.operatore("Operatore esterno", "ESTERNO_PAGATO_BANCO", "napoli", false, m01);
        UUID altraMisura = dati.operatore("Operatore altra misura", "INTERNO_BANCO", "napoli", false, m02);
        UUID senzaMisure = dati.operatore("Operatore senza misure", "INTERNO_BANCO", "napoli", false);
        UUID revocato = dati.operatore("Operatore revocato", "INTERNO_BANCO", "napoli", false, m01);
        dati.impostaStatoAccount(revocato, "REVOCATO");

        var opzioni = ricerca.cercaErogatoriCompatibili(m01, "napoli");

        assertThat(opzioni).extracting(ErogatoreCompatibile::accountId, ErogatoreCompatibile::tipo)
                .containsExactlyInAnyOrder(
                        tuple(interno, TipoErogatore.INTERNO_BANCO),
                        tuple(esterno, TipoErogatore.ESTERNO_PAGATO_BANCO));
        assertThat(opzioni).extracting(ErogatoreCompatibile::accountId)
                .doesNotContain(altraMisura, senzaMisure, revocato);
    }

    @Test
    @DisplayName("La prossimità distingue stesso comune e comuni limitrofi ed esclude i comuni non confinanti")
    void prossimita() {
        UUID napoli = dati.opt("OPT Napoli", "napoli");
        UUID portici = dati.opt("OPT Portici", "portici");
        UUID pozzuoli = dati.operatore("Operatore Pozzuoli", "INTERNO_BANCO", "pozzuoli", false, m01);
        UUID lontano = dati.opt("OPT lontana", DatiSintetici.COMUNE_LONTANO);
        for (UUID opt : new UUID[] {napoli, portici, lontano}) {
            dati.offerta(opt, m01);
        }

        var daNapoli = ricerca.cercaErogatoriCompatibili(m01, "napoli");
        var daPortici = ricerca.cercaErogatoriCompatibili(m01, "portici");

        assertThat(daNapoli).extracting(ErogatoreCompatibile::accountId, ErogatoreCompatibile::ambito)
                .containsExactlyInAnyOrder(
                        tuple(napoli, AmbitoErogazione.STESSO_COMUNE),
                        tuple(portici, AmbitoErogazione.COMUNE_LIMITROFO),
                        tuple(pozzuoli, AmbitoErogazione.COMUNE_LIMITROFO));
        assertThat(daPortici).as("Portici e Pozzuoli non sono confinanti")
                .extracting(ErogatoreCompatibile::accountId, ErogatoreCompatibile::ambito)
                .containsExactlyInAnyOrder(
                        tuple(portici, AmbitoErogazione.STESSO_COMUNE),
                        tuple(napoli, AmbitoErogazione.COMUNE_LIMITROFO));
    }

    @Test
    @DisplayName("Remoto consentito: un operatore del Centro PICUS compare anche come opzione a distanza")
    void remotoConsentito() {
        dati.abilitaADistanza(m01);
        UUID centroLocale = dati.operatore("Centro PICUS Napoli", "INTERNO_BANCO", "napoli", true, m01);
        UUID centroLontano = dati.operatore("Centro PICUS lontano", "INTERNO_BANCO", DatiSintetici.COMUNE_LONTANO,
                true, m01);
        UUID nonCentro = dati.operatore("Operatore lontano", "INTERNO_BANCO", DatiSintetici.COMUNE_LONTANO, false, m01);

        var opzioni = ricerca.cercaErogatoriCompatibili(m01, "napoli");

        assertThat(opzioni).extracting(ErogatoreCompatibile::accountId, ErogatoreCompatibile::ambito)
                .containsExactlyInAnyOrder(
                        tuple(centroLocale, AmbitoErogazione.STESSO_COMUNE),
                        tuple(centroLocale, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA),
                        tuple(centroLontano, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA));
        assertThat(opzioni).extracting(ErogatoreCompatibile::accountId).doesNotContain(nonCentro);
    }

    @Test
    @DisplayName("Remoto escluso: senza abilitazione della misura nessuna opzione a distanza")
    void remotoEscluso() {
        UUID centroLocale = dati.operatore("Centro PICUS Napoli", "INTERNO_BANCO", "napoli", true, m01);
        dati.operatore("Centro PICUS lontano", "INTERNO_BANCO", DatiSintetici.COMUNE_LONTANO, true, m01);

        var opzioni = ricerca.cercaErogatoriCompatibili(m01, "napoli");

        assertThat(opzioni).extracting(ErogatoreCompatibile::accountId, ErogatoreCompatibile::ambito)
                .containsExactly(tuple(centroLocale, AmbitoErogazione.STESSO_COMUNE));
    }

    @Test
    @DisplayName("Senza offerte né competenze il risultato è vuoto")
    void nessunCandidato() {
        UUID opt = dati.opt("OPT con altra misura", "napoli");
        dati.offerta(opt, m02);
        dati.operatore("Operatore con altra misura", "INTERNO_BANCO", "napoli", true, m02);

        assertThat(ricerca.cercaErogatoriCompatibili(m01, "napoli")).isEmpty();
    }

    @Test
    @DisplayName("Misura o comune sconosciuti non producono candidati ma un errore")
    void riferimentiSconosciuti() {
        assertThatThrownBy(() -> ricerca.cercaErogatoriCompatibili(999_999L, "napoli"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> ricerca.cercaErogatoriCompatibili(m01, "comune-inesistente"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
