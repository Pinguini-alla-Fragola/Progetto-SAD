package it.unina.picus.comunicazioni.web;

import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.campi;
import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.inviata;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.comunicazioni.web.ComunicazioniHttp.Inviata;
import it.unina.picus.comunicazioni.web.ComunicazioniHttp.Modalita;
import it.unina.picus.shared.ComunicazioniBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * CA-01, CA-02 e CA-06 della #55: il Banco invia una comunicazione a tutte le OPT, a un gruppo di OPT, a tutti gli
 * operatori interni ed esterni pagati dal Banco o a un loro gruppo, e l'avviso di scadenza della scheda
 * trimestrale. La comunicazione è disponibile ai soli account selezionati e notificata in app.
 *
 * <p>Tecniche: <b>classi di equivalenza</b> sul tipo di destinatari e sugli attori (OPT, operatore interno,
 * operatore esterno pagato dal Banco, dentro e fuori dalla selezione); <b>matrice</b> tipo × modalità di invio
 * (JSON e multipart senza file, con lo stesso esito atteso); <b>controlli negativi</b> su chi non è selezionato,
 * accanto ai positivi. Gli account del gruppo si prendono dall'elenco dei selezionabili del Banco.</p>
 *
 * <p>Notifica in app: comunicazione presente nella prima pagina di {@code /ricevute}, con {@code totale} e
 * {@code /da-confermare} aumentati di uno; per chi non è selezionato l'id è assente e i due valori sono invariati.
 * Negli invii «a tutti» il numero di destinatari si verifica solo come limite inferiore, perché il database è
 * condiviso tra i test.</p>
 *
 * <p>Punto aperto (CA-06): la issue parla di operatori «tenuti a compilare» la scheda, ma non dice chi siano;
 * il modello della pagina usa {@code TUTTI_GLI_OPERATORI}. Il test verifica solo il canale, non la platea.</p>
 *
 * <p>Il collegamento tra il pulsante del modello di scadenza e l'invio del Banco (payload della POST normale) è
 * verificato in {@code src/test/js/comunicazioni.test.mjs}; qui si verificano la ricezione via HTTP e che la
 * dashboard renderizzata carichi il modulo e contenga il pulsante.</p>
 */
@DisplayName("#55 CA-01, CA-02, CA-06 · Invio a OPT e operatori e avviso di scadenza")
class InvioComunicazioniTest extends ComunicazioniBlackBoxTest {

    /** Titolo e testo di {@code MODELLO_SCADENZA} in {@code static/js/comunicazioni.js}. */
    static final String TITOLO_SCADENZA = "Scadenza della scheda trimestrale";
    static final String CONTENUTO_SCADENZA =
            "Vi ricordiamo di compilare e inviare la scheda trimestrale entro la scadenza prevista.\n"
                    + "Per qualsiasi dubbio potete rispondere a questa comunicazione contattando il Banco.";

    private static final String DASHBOARD_BANCO = "/banco/dashboard";
    private static final String MODULO_COMUNICAZIONI = "/js/comunicazioni.js";

    // Lettura del markup renderizzato, senza i commenti HTML: tag di apertura, attributi e form delle comunicazioni.
    private static final Pattern COMMENTO = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern ATTRIBUTO = Pattern.compile("([\\w:-]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'))?");
    private static final Pattern FORM = Pattern.compile("<form\\b([^>]*)>(.*?)</form>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private ComunicazioniHttp comunicazioni;

    @BeforeEach
    void preparaClient() {
        comunicazioni = new ComunicazioniHttp(http);
    }

    static Stream<Arguments> versoLeOpt() {
        return combinazioni(TipoDestinatari.TUTTE_LE_OPT, TipoDestinatari.GRUPPO_OPT);
    }

    static Stream<Arguments> versoGliOperatori() {
        return combinazioni(TipoDestinatari.TUTTI_GLI_OPERATORI, TipoDestinatari.GRUPPO_OPERATORI);
    }

    private static Stream<Arguments> combinazioni(TipoDestinatari... tipi) {
        return Stream.of(tipi).flatMap(tipo -> Stream.of(Modalita.values()).map(modo -> Arguments.of(tipo, modo)));
    }

    @ParameterizedTest(name = "{0} via {1}")
    @MethodSource("versoLeOpt")
    @DisplayName("CA-01 · comunicazione a tutte le OPT o a un gruppo: la ricevono solo le OPT selezionate")
    void invioAlleOpt(TipoDestinatari tipo, Modalita modalita) throws Exception {
        verificaInvio(tipo, modalita);
    }

    @ParameterizedTest(name = "{0} via {1}")
    @MethodSource("versoGliOperatori")
    @DisplayName("CA-02 · comunicazione a tutti gli operatori interni ed esterni o a un gruppo")
    void invioAgliOperatori(TipoDestinatari tipo, Modalita modalita) throws Exception {
        verificaInvio(tipo, modalita);
    }

    /**
     * Prepara due OPT e tre operatori (due interni, uno esterno pagato dal Banco), invia e verifica chi riceve.
     */
    private void verificaInvio(TipoDestinatari tipo, Modalita modalita) throws Exception {
        Account banco = scenario.banco();
        Account optA = scenario.optRichiedente();
        Account optB = scenario.opt(ScenarioAccessi.COMUNE_LIMITROFO);
        Account interno = scenario.operatore(TipoErogatore.INTERNO_BANCO);
        Account esterno = scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        Account terzo = scenario.operatore(TipoErogatore.INTERNO_BANCO);

        List<UUID> gruppo = List.of();
        List<Account> ricevono;
        List<Account> esclusi;
        switch (tipo) {
            case TUTTE_LE_OPT -> {
                ricevono = List.of(optA, optB);
                esclusi = List.of(interno, esterno);
            }
            case GRUPPO_OPT -> {
                List<UUID> selezionabili = comunicazioni.destinatari(banco, tipo.name());
                assertTrue(selezionabili.containsAll(List.of(optA.id(), optB.id())), "le OPT nuove sono selezionabili");
                assertFalse(selezionabili.contains(interno.id()) || selezionabili.contains(esterno.id()),
                        "gli operatori non sono selezionabili per un gruppo di OPT");
                gruppo = List.of(comunicazioni.scegli(banco, tipo.name(), optA));
                ricevono = List.of(optA);
                esclusi = List.of(optB, interno);
            }
            case TUTTI_GLI_OPERATORI -> {
                ricevono = List.of(interno, esterno);
                esclusi = List.of(optA);
            }
            case GRUPPO_OPERATORI -> {
                List<UUID> selezionabili = comunicazioni.destinatari(banco, tipo.name());
                assertTrue(selezionabili.containsAll(List.of(interno.id(), esterno.id(), terzo.id())),
                        "operatori interni ed esterni pagati dal Banco sono selezionabili");
                assertFalse(selezionabili.contains(optA.id()) || selezionabili.contains(optB.id()),
                        "le OPT non sono selezionabili per un gruppo di operatori");
                gruppo = List.of(comunicazioni.scegli(banco, tipo.name(), interno),
                        comunicazioni.scegli(banco, tipo.name(), esterno));
                ricevono = List.of(interno, esterno);
                esclusi = List.of(terzo, optA);
            }
            default -> throw new IllegalArgumentException(tipo.name());
        }

        String marcatore = marcatore();
        String titolo = "Comunicazione di prova " + marcatore;
        String contenuto = "Testo sintetico per " + tipo + " " + marcatore;
        Map<Account, Prima> prima = prima(ricevono, esclusi);

        Inviata inviata = inviata(comunicazioni.invia(banco, modalita,
                campi(titolo, contenuto, tipo.name(), tipo.name().startsWith("GRUPPO") ? gruppo : null)));

        assertEquals(tipo.name(), inviata.destinatariTipo());
        assertEquals(titolo, inviata.titolo());
        if (tipo.name().startsWith("GRUPPO")) {
            assertEquals(gruppo.size(), inviata.destinatari(), "un destinatario per ogni account del gruppo");
        } else {
            assertTrue(inviata.destinatari() >= ricevono.size(), "almeno gli account attivi creati dal test");
        }

        for (Account destinatario : ricevono) {
            verificaRicevuta(destinatario, inviata.id(), prima.get(destinatario));
            Map<String, Object> letta = comunicazioni.consultaOk(destinatario, inviata.id());
            assertEquals(contenuto, letta.get("contenuto"));
            assertEquals(titolo, letta.get("titolo"));
            assertEquals(tipo.name(), letta.get("destinatariTipo"));
        }
        for (Account escluso : esclusi) {
            verificaNonRicevuta(escluso, inviata.id(), prima.get(escluso));
        }
        assertTrue(comunicazioni.inviate(banco).contiene(inviata.id()), "il Banco la trova tra le inviate");
    }

    @ParameterizedTest(name = "via {0}")
    @EnumSource(Modalita.class)
    @DisplayName("CA-06 · l'avviso di scadenza della scheda trimestrale arriva agli operatori come le altre")
    void avvisoDiScadenza(Modalita modalita) throws Exception {
        Account banco = scenario.banco();
        Account interno = scenario.operatore(TipoErogatore.INTERNO_BANCO);
        Account esterno = scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        Account opt = scenario.optRichiedente();
        List<Account> operatori = List.of(interno, esterno);
        Map<Account, Prima> prima = prima(operatori, List.of(opt));

        Inviata inviata = inviata(comunicazioni.invia(banco, modalita,
                campi(TITOLO_SCADENZA, CONTENUTO_SCADENZA, TipoDestinatari.TUTTI_GLI_OPERATORI.name(), null)));

        assertEquals(TipoDestinatari.TUTTI_GLI_OPERATORI.name(), inviata.destinatariTipo());
        assertTrue(inviata.destinatari() >= operatori.size(), "almeno gli operatori creati dal test");
        for (Account operatore : operatori) {
            verificaRicevuta(operatore, inviata.id(), prima.get(operatore));
            Map<String, Object> letta = comunicazioni.consultaOk(operatore, inviata.id());
            assertEquals(TITOLO_SCADENZA, letta.get("titolo"));
            assertEquals(CONTENUTO_SCADENZA, letta.get("contenuto"));
            assertEquals(TipoDestinatari.TUTTI_GLI_OPERATORI.name(), letta.get("destinatariTipo"));
        }
        verificaNonRicevuta(opt, inviata.id(), prima.get(opt));
        assertTrue(comunicazioni.inviate(banco).contiene(inviata.id()), "il Banco vede l'avviso tra le inviate");
    }

    @Test
    @DisplayName("CA-06 · la dashboard del Banco renderizzata carica comunicazioni.js e contiene il pulsante del modello")
    void dashboardConModuloEPulsante() throws Exception {
        Account banco = scenario.banco();

        MockHttpServletResponse pagina = comunicazioni.paginaHtml(banco, DASHBOARD_BANCO);

        assertEquals(200, pagina.getStatus());
        assertTrue(MediaType.TEXT_HTML.isCompatibleWith(MediaType.parseMediaType(pagina.getContentType())),
                "la dashboard è una pagina HTML");
        String markup = COMMENTO.matcher(pagina.getContentAsString(StandardCharsets.UTF_8)).replaceAll("");

        List<Map<String, String>> script = tag(markup, "script").stream()
                .filter(attributi -> MODULO_COMUNICAZIONI.equals(attributi.get("src"))).toList();
        assertEquals(1, script.size(), "un solo script della dashboard carica " + MODULO_COMUNICAZIONI);
        assertEquals("module", script.get(0).get("type"), "lo script è un modulo JavaScript");

        List<MatchResult> form = FORM.matcher(markup).results()
                .filter(trovato -> "comunicazione".equals(attributi(trovato.group(1)).get("id"))).toList();
        assertEquals(1, form.size(), "un solo form id=comunicazione");
        assertEquals(1, tag(form.get(0).group(2), "button").stream()
                .filter(attributi -> attributi.containsKey("data-modello-scadenza")).count(),
                "un pulsante del modello nel form delle comunicazioni");

        MockHttpServletResponse modulo = comunicazioni.risorsaStatica(banco, MODULO_COMUNICAZIONI);
        assertEquals(200, modulo.getStatus(), "il modulo caricato dalla pagina è disponibile");
        MediaType tipo = MediaType.parseMediaType(modulo.getContentType());
        assertTrue(List.of("text/javascript", "application/javascript").contains(tipo.getType() + "/" + tipo.getSubtype()),
                () -> "content type JavaScript, ottenuto " + tipo);
    }

    /** Attributi di ogni tag di apertura con quel nome. */
    private static List<Map<String, String>> tag(String markup, String nome) {
        return Pattern.compile("<" + nome + "\\b([^>]*)>", Pattern.CASE_INSENSITIVE).matcher(markup).results()
                .map(trovato -> attributi(trovato.group(1))).toList();
    }

    /** Attributi in qualsiasi ordine; quelli senza valore hanno valore vuoto. */
    private static Map<String, String> attributi(String testo) {
        Map<String, String> attributi = new HashMap<>();
        ATTRIBUTO.matcher(testo).results().forEach(trovato -> attributi.put(trovato.group(1),
                trovato.group(2) != null ? trovato.group(2) : trovato.group(3) != null ? trovato.group(3) : ""));
        return attributi;
    }

    /**
     * Stato di un account prima dell'invio.
     *
     * @param ricevute totale di {@code /ricevute}
     * @param daConfermare contatore di {@code /da-confermare}
     */
    private record Prima(long ricevute, long daConfermare) {}

    private Map<Account, Prima> prima(List<Account> ricevono, List<Account> esclusi) throws Exception {
        Map<Account, Prima> prima = new HashMap<>();
        for (Account account : Stream.concat(ricevono.stream(), esclusi.stream()).toList()) {
            prima.put(account, new Prima(comunicazioni.ricevute(account).totale(),
                    comunicazioni.daConfermare(account)));
        }
        return prima;
    }

    /** Ricevuta: l'id è nella prima pagina delle ricevute, il totale e il contatore aumentano di uno. */
    private void verificaRicevuta(Account destinatario, UUID id, Prima prima) throws Exception {
        ComunicazioniHttp.Pagina ricevute = comunicazioni.ricevute(destinatario);
        assertTrue(ricevute.contiene(id), () -> destinatario.ruolo() + " selezionato deve trovarla tra le ricevute");
        assertEquals(prima.ricevute() + 1, ricevute.totale(), "una comunicazione in più tra le ricevute");
        assertEquals(prima.daConfermare() + 1, comunicazioni.daConfermare(destinatario),
                "notifica in app per il destinatario");
    }

    /** Non ricevuta: l'id non è tra le ricevute, totale e contatore invariati. */
    private void verificaNonRicevuta(Account escluso, UUID id, Prima prima) throws Exception {
        ComunicazioniHttp.Pagina ricevute = comunicazioni.ricevute(escluso);
        assertFalse(ricevute.contiene(id), () -> escluso.ruolo() + " non selezionato non deve riceverla");
        assertEquals(prima.ricevute(), ricevute.totale(), "ricevute invariate per chi non è selezionato");
        assertEquals(prima.daConfermare(), comunicazioni.daConfermare(escluso),
                "nessuna notifica a chi non è selezionato");
    }
}
