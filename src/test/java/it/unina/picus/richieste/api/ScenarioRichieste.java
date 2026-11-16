package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import it.unina.picus.catalogo.api.CatalogoApi;
import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.comunicazioni.api.ComunicazioniApi;
import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.AccordoADistanza;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base comune dei test di accettazione black box del modulo Richieste.
 *
 * <p>I test esercitano il sistema soltanto attraverso i contratti pubblici
 * ({@code *.api}) pubblicati nel Javadoc: nessun riferimento a classi {@code interno}.
 * Lo stato di partenza di ogni scenario (Given) viene costruito con le stesse operazioni
 * pubbliche che userebbe un utente: invio, presa in carico, contatto, risposta, assegnazione.</p>
 *
 * <p>L'unica eccezione sono gli account di prova: {@code UtenzeApi} non espone la creazione
 * delle utenze, quindi vengono inseriti con SQL sullo schema {@code V1__schema_iniziale.sql},
 * come fa {@code UtenzeDemo}. Se lo schema delle utenze cambia, va adeguato solo
 * {@link #creaAccount(String, RuoloUtente)} e i due metodi che lo usano.</p>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:picus")
abstract class ScenarioRichieste {

    /** Attori usati nei test parametrizzati sui dinieghi. */
    enum Attore {
        BANCO,
        OPT_TITOLARE,
        OPT_EROGATRICE,
        OPT_EROGATRICE_ALTERNATIVA,
        OPT_ESTRANEA,
        OPERATORE_NON_COINVOLTO
    }

    @Autowired
    protected RichiesteApi richieste;

    @Autowired
    protected CatalogoApi catalogo;

    @Autowired
    protected UtenzeApi utenze;

    @Autowired
    protected ComunicazioniApi comunicazioni;

    @Autowired
    private JdbcTemplate jdbc;

    /** Account di coordinamento del Banco. */
    protected IdentitaAutenticata banco;

    /** OPT che invia la richiesta. */
    protected IdentitaAutenticata optTitolare;

    /** OPT che offre la misura nel comune della richiesta. */
    protected IdentitaAutenticata optErogatrice;

    /** Seconda OPT che offre la stessa misura nello stesso comune. */
    protected IdentitaAutenticata optErogatriceAlternativa;

    /** OPT senza alcun legame con la richiesta: non titolare, non interpellata, non assegnata. */
    protected IdentitaAutenticata optEstranea;

    /** Operatore del Banco attivo, mai interpellato né assegnato dagli scenari. */
    protected IdentitaAutenticata operatoreNonCoinvolto;

    /** Misura di prova, erogabile anche a distanza. */
    protected long misuraId;

    /** Comune della richiesta e degli erogatori di prova. */
    protected String comune;

    @BeforeEach
    void preparaAmbiente() {
        comune = catalogo.elencoComuni().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Il catalogo di prova non contiene comuni"))
                .codice();

        banco = utenze.elencoAccountAttivi(RuoloUtente.BANCO).stream()
                .findFirst()
                .map(account -> new IdentitaAutenticata(account.id(), RuoloUtente.BANCO))
                .orElseGet(() -> creaAccount("Banco di prova", RuoloUtente.BANCO));

        optTitolare = creaOpt("OPT titolare");
        optErogatrice = creaOpt("OPT erogatrice");
        optErogatriceAlternativa = creaOpt("OPT erogatrice alternativa");
        optEstranea = creaOpt("OPT estranea");
        operatoreNonCoinvolto = creaOperatore("Operatore non coinvolto");

        misuraId = catalogo.creaMisura(
                        banco,
                        new DatiMisura(codiceUnivoco("T"), "Misura di prova", "Misura sintetica per i test", true))
                .id();
        catalogo.registraOffertaOpt(optErogatrice, misuraId, false);
        catalogo.registraOffertaOpt(optErogatriceAlternativa, misuraId, false);
    }

    // ------------------------------------------------------------------ attori

    protected IdentitaAutenticata identita(Attore attore) {
        return switch (attore) {
            case BANCO -> banco;
            case OPT_TITOLARE -> optTitolare;
            case OPT_EROGATRICE -> optErogatrice;
            case OPT_EROGATRICE_ALTERNATIVA -> optErogatriceAlternativa;
            case OPT_ESTRANEA -> optEstranea;
            case OPERATORE_NON_COINVOLTO -> operatoreNonCoinvolto;
        };
    }

    /**
     * Cerca fra i candidati della richiesta un operatore del Banco (interno o esterno pagato).
     * Vuoto finché i dati di prova non contengono un operatore abilitato alla misura: i test
     * che ne dipendono vengono saltati con {@code assumeTrue}, non falliscono.
     */
    protected Optional<IdentitaAutenticata> operatoreCandidato(UUID richiestaId) {
        return richieste.cercaErogatoriOrdinati(banco, richiestaId).stream()
                .map(Candidato::erogatore)
                .filter(erogatore -> erogatore.tipo() != TipoErogatore.OPT)
                .findFirst()
                .map(erogatore -> new IdentitaAutenticata(erogatore.accountId(), RuoloUtente.OPERATORE));
    }

    // ------------------------------------------------------------------ Given

    /** Richiesta inviata dalla OPT titolare e presa in carico dal Banco. */
    protected UUID richiestaPresaInCarico() {
        UUID richiestaId = richieste
                .invia(optTitolare, new DatiInvio(misuraId, TipoDestinatario.PERSONA, comune, null), null)
                .id();
        richieste.prendiInCarico(banco, richiestaId);
        return richiestaId;
    }

    /** Il Banco interpella l'erogatore per l'opzione con cui compare fra i candidati. */
    protected ContattoDisponibilita contattoInAttesa(UUID richiestaId, IdentitaAutenticata erogatore) {
        AmbitoErogazione ambito = richieste.cercaErogatoriOrdinati(banco, richiestaId).stream()
                .map(Candidato::erogatore)
                .filter(candidato -> candidato.accountId().equals(erogatore.accountId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("L'erogatore di prova non è fra i candidati compatibili"))
                .ambito();
        return richieste.interpellaErogatore(banco, richiestaId, erogatore.accountId(), ambito);
    }

    /** Richiesta In programmazione: l'erogatore ha dato disponibilità e il Banco lo ha assegnato. */
    protected UUID richiestaInProgrammazione(IdentitaAutenticata erogatore) {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, erogatore);
        richieste.rispondiDisponibilita(erogatore, contatto.id(), true);
        richieste.assegnaErogatore(banco, contatto.id());
        return richiestaId;
    }

    // ------------------------------------------------------------------ dati di prova

    protected static Instant appuntamento() {
        return Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    protected static AccordoInPresenza accordoInPresenza() {
        return new AccordoInPresenza(appuntamento(), "Sede di prova, via Sintetica 1", "Nome", "Cognome", "0810000000");
    }

    protected static AccordoADistanza accordoADistanza() {
        return new AccordoADistanza(appuntamento(), URI.create("https://videochiamata.example/stanza-di-prova"));
    }

    // ------------------------------------------------------------------ Then ricorrenti

    protected StatoRichiesta statoVistoDalBanco(UUID richiestaId) {
        return richieste.consultaRichiesta(banco, richiestaId).richiesta().stato();
    }

    protected EsitoContatto esitoVistoDalBanco(UUID richiestaId, UUID contattoId) {
        return richieste.elencoContatti(banco, richiestaId).stream()
                .filter(contatto -> contatto.id().equals(contattoId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Il Banco non vede il contatto " + contattoId))
                .esito();
    }

    /** Verifica che la pratica sia rimasta nello stato atteso e senza accordo confermato. */
    protected void assertSenzaAccordo(UUID richiestaId, StatoRichiesta statoAtteso) {
        RichiestaDettaglio dettaglio = richieste.consultaRichiesta(banco, richiestaId);
        assertEquals(statoAtteso, dettaglio.richiesta().stato());
        assertNull(dettaglio.accordo(), "non deve risultare alcun accordo confermato");
    }

    // ------------------------------------------------------------------ account di prova

    private IdentitaAutenticata creaOpt(String nome) {
        IdentitaAutenticata opt = creaAccount(nome, RuoloUtente.OPT);
        jdbc.update(
                "insert into utenze_profili_opt (account_id, codice_struttura, comune_codice) values (?, ?, ?)",
                opt.accountId(), codiceUnivoco("OPT-"), comune);
        return opt;
    }

    private IdentitaAutenticata creaOperatore(String nome) {
        IdentitaAutenticata operatore = creaAccount(nome, RuoloUtente.OPERATORE);
        jdbc.update(
                "insert into utenze_profili_operatore (account_id, tipologia, comune_codice) values (?, ?, ?)",
                operatore.accountId(), TipoErogatore.INTERNO_BANCO.name(), comune);
        return operatore;
    }

    private IdentitaAutenticata creaAccount(String nome, RuoloUtente ruolo) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into utenze_account (id, email, password_hash, nome, ruolo, stato) values (?, ?, ?, ?, ?, 'ATTIVO')",
                id, id + "@picus.test", "accesso-non-previsto-nei-test", nome, ruolo.name());
        return new IdentitaAutenticata(id, ruolo);
    }

    private static String codiceUnivoco(String prefisso) {
        return prefisso + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
    }
}
