package it.unina.picus.richieste.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

/**
 * Test di integrazione della technical task #171 «Test di ruoli, stati e avvisi di annullamento»,
 * sotto-task della user story #54 «Annullamento della richiesta».
 *
 * <p>I blocchi {@code @Nested} «Criterio 1..3» seguono i criteri di completamento della #171, che
 * richiamano gli Acceptance Criteria della #54. Il quarto criterio della #171 (conferma dalla
 * schermata di dettaglio, con motivo facoltativo in Creata e obbligatorio negli altri stati) resta
 * aperto: il collegamento della vista è affidato alla #170 e in {@code richieste/dettaglio.html} il
 * pulsante «Annulla la richiesta» è ancora disattivato.</p>
 *
 * <p>Il comando verificato è quello reale, {@code POST /api/richieste/{id}/annullamento} di
 * {@link AnnullamentoRichiesteController}, con la catena di sicurezza attiva: l'utente è simulato
 * con l'UUID del proprio account, che esiste nel database con lo stesso ruolo, e ogni comando porta
 * il token CSRF, così un {@code 403} dipende solo dalle regole di accesso. La sessione scaduta usa
 * invece il login reale. Ogni esito HTTP è affiancato dalla lettura delle righe su PostgreSQL:
 * richiesta, contatti di disponibilità e avvisi.</p>
 *
 * <p>Le pratiche sono preparate con {@link DatiSintetici} nello stato di partenza richiesto; i casi
 * sul candidato interpellato e sulla ricerca chiusa usano invece gli endpoint reali di contatto,
 * risposta e assegnazione. Tutti gli account sono a Napoli e la misura è {@code M01}: gli erogatori
 * con offerta o abilitazione per {@code M01} sono candidati della pratica.</p>
 */
@IntegrazionePostgresql
@DisplayName("#171 Ruoli, stati e avvisi di annullamento (US #54) su HTTP e PostgreSQL")
class AnnullamentoRichiestaIntegrazioneTest {

    private static final String ANNULLAMENTO = "/api/richieste/{id}/annullamento";
    private static final String DETTAGLIO = "/api/richieste/{id}";
    private static final String CODA = "/api/richieste/coda";
    private static final String PRESA_IN_CARICO = "/api/richieste/{id}/presa-in-carico";
    private static final String CANDIDATI = "/api/richieste/{id}/candidati";
    private static final String CONTATTI = "/api/richieste/{id}/contatti";
    private static final String RISPOSTA = "/api/contatti-disponibilita/{id}/risposta";
    private static final String ASSEGNAZIONE = "/api/contatti-disponibilita/{id}/assegnazione";
    private static final String AVVISI = "/api/avvisi";
    private static final String LOGIN = "/api/auth/login";
    private static final String TOKEN_CSRF = "/api/auth/csrf";

    private static final String MOTIVO = "Motivo sintetico di annullamento";
    private static final String MOTIVO_OBBLIGATORIO = "Motivo obbligatorio";
    private static final String MOTIVO_NON_TESTUALE = "Il motivo deve essere un testo";
    private static final String MOTIVO_TROPPO_LUNGO = "Il motivo può contenere al massimo 500 caratteri";
    private static final String MOTIVO_NON_SEMPLICE = "Usa testo semplice per il motivo";
    private static final String SOLO_MOTIVO = "Indica soltanto il campo motivo";
    private static final Instant INVIATA_IL = Instant.parse("2026-11-02T09:00:00Z");
    private static final Instant DATA_ACCORDO = Instant.parse("2027-01-15T10:00:00Z");

    /** Stato di partenza della pratica e presenza di un erogatore assegnato. */
    enum Partenza {
        CREATA("CREATA", false),
        PRESA_IN_CARICO("PRESA_IN_CARICO", false),
        IN_PROGRAMMAZIONE("IN_PROGRAMMAZIONE", true),
        CONFERMATA("CONFERMATA", true),
        RIFIUTATA_SENZA_EROGATORE("RIFIUTATA", false),
        RIFIUTATA_DOPO_ASSEGNAZIONE("RIFIUTATA", true);

        private final String stato;
        private final boolean assegnata;

        Partenza(String stato, boolean assegnata) {
            this.stato = stato;
            this.assegnata = assegnata;
        }
    }

    /** Erogatori che possono risultare assegnati, uno per tipologia della tabella della #54. */
    enum Erogatore {
        OPT,
        OPT_TRAMITE_PROFESSIONISTA,
        OPERATORE_INTERNO,
        OPERATORE_ESTERNO
    }

    /** Account che possono annullare una pratica assegnata. */
    enum Annullatore {
        BANCO,
        OPT_RICHIEDENTE,
        EROGATORE_ASSEGNATO
    }

    /** Account senza alcuna relazione autorizzante con la pratica. */
    enum Estraneo {
        OPT_ESTRANEA,
        OPT_EROGATRICE_NON_ASSEGNATA,
        OPERATORE_ABILITATO_NON_ASSEGNATO,
        OPERATORE_SENZA_MISURE
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder encoder;

    private DatiSintetici dati;
    private long m01;
    private UUID banco;
    private UUID secondoBanco;
    private UUID titolare;
    private UUID optEstranea;
    private UUID optErogatrice;
    private UUID optTramiteProfessionista;
    private UUID operatoreInterno;
    private UUID operatoreEsterno;
    private UUID operatoreEstraneo;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        m01 = dati.misura("M01");
        banco = dati.banco("Banco di coordinamento");
        // Gli avvisi vanno a tutti gli account attivi del Banco, non solo a chi ha annullato.
        secondoBanco = dati.banco("Secondo account del Banco");
        titolare = dati.opt("OPT richiedente", "napoli");
        optEstranea = dati.opt("OPT estranea", "napoli");
        optErogatrice = dati.opt("OPT erogatrice", "napoli");
        dati.offerta(optErogatrice, m01);
        optTramiteProfessionista = dati.opt("OPT tramite professionista", "napoli");
        jdbc.update("""
                insert into catalogo_offerte_opt (opt_id, misura_id, attiva, tramite_professionista)
                values (?, ?, true, true)
                """, optTramiteProfessionista, m01);
        operatoreInterno = dati.operatore("Operatore interno", "INTERNO_BANCO", "napoli", false, m01);
        operatoreEsterno = dati.operatore("Operatore esterno", "ESTERNO_PAGATO_BANCO", "napoli", false, m01);
        operatoreEstraneo = dati.operatore("Operatore senza misure", "INTERNO_BANCO", "napoli", false);
    }

    // ---------------------------------------------------------------------------------------------
    // Criterio 1
    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Criterio 1: annullano la OPT richiedente, il Banco, la OPT assegnata e l'operatore assegnato, da ogni stato")
    class RuoliEStatiDiPartenza {

        static Stream<Arguments> coordinatoreEPartenza() {
            return Stream.of("BANCO", "OPT_RICHIEDENTE").flatMap(attore -> Stream.of(Partenza.values())
                    .map(partenza -> Arguments.of(attore, partenza)));
        }

        @ParameterizedTest(name = "{0} annulla con motivo da {1}")
        @MethodSource("coordinatoreEPartenza")
        @DisplayName("Banco e OPT richiedente annullano da ogni stato: Annullata con motivo e dati conservati")
        void bancoERichiedenteDaOgniStato(String chi, Partenza partenza) throws Exception {
            UUID id = richiesta(partenza, optErogatrice);
            Map<String, Object> prima = riga(id);
            Attore attore = "BANCO".equals(chi) ? bancoAttore() : opt(titolare);

            assertAnnullata(annulla(attore, id, conMotivo(MOTIVO)), id);

            assertThat(dati.stato(id)).isEqualTo("ANNULLATA");
            assertThat(motivo(id)).isEqualTo(MOTIVO);
            assertThat(senzaStatoEMotivo(riga(id))).isEqualTo(senzaStatoEMotivo(prima));
        }

        @ParameterizedTest(name = "{0} assegnato annulla con motivo da {1}")
        @MethodSource("it.unina.picus.richieste.web.AnnullamentoRichiestaIntegrazioneTest#erogatoreEPartenzaAssegnata")
        @DisplayName("L'erogatore assegnato annulla con motivo: OPT, OPT tramite professionista, operatore interno ed esterno")
        void erogatoreAssegnatoAnnulla(Erogatore tipo, Partenza partenza) throws Exception {
            Attore assegnato = erogatore(tipo);
            UUID id = richiesta(partenza, assegnato.id());
            Map<String, Object> prima = riga(id);

            assertAnnullata(annulla(assegnato, id, conMotivo(MOTIVO)), id);

            assertThat(dati.stato(id)).isEqualTo("ANNULLATA");
            assertThat(motivo(id)).isEqualTo(MOTIVO);
            assertThat(dati.erogatoreAssegnato(id)).isEqualTo(assegnato.id());
            assertThat(senzaStatoEMotivo(riga(id))).isEqualTo(senzaStatoEMotivo(prima));
        }

        static Stream<Arguments> coordinatoreECorpoSenzaMotivo() {
            return Stream.of("BANCO", "OPT_RICHIEDENTE").flatMap(attore -> Stream.of(
                            null, "{}", "{\"motivo\":null}", "{\"motivo\":\"\"}", "{\"motivo\":\"   \"}")
                    .map(corpo -> Arguments.of(attore, corpo)));
        }

        @ParameterizedTest(name = "{0} annulla una pratica Creata con corpo [{1}]")
        @MethodSource("coordinatoreECorpoSenzaMotivo")
        @DisplayName("Da Creata il motivo è facoltativo: senza motivo la pratica è Annullata e il motivo resta vuoto")
        void daCreataSenzaMotivo(String chi, String corpo) throws Exception {
            UUID id = richiesta(Partenza.CREATA, null);
            Map<String, Object> prima = riga(id);
            Attore attore = "BANCO".equals(chi) ? bancoAttore() : opt(titolare);

            assertAnnullata(annulla(attore, id, corpo), id);

            assertThat(dati.stato(id)).isEqualTo("ANNULLATA");
            assertThat(motivo(id)).isNull();
            assertThat(senzaStatoEMotivo(riga(id))).isEqualTo(senzaStatoEMotivo(prima));
        }

        @Test
        @DisplayName("Da Creata un motivo indicato è conservato anche se facoltativo")
        void daCreataConMotivo() throws Exception {
            UUID id = richiesta(Partenza.CREATA, null);

            assertAnnullata(annulla(opt(titolare), id, conMotivo(MOTIVO)), id);

            assertThat(motivo(id)).isEqualTo(MOTIVO);
        }

        @Test
        @DisplayName("Il Banco annulla anche le pratiche delle altre OPT: nessun legame di titolarità richiesto")
        void bancoAnnullaPraticheDiOgniOpt() throws Exception {
            UUID diAltraOpt = dati.richiesta(optEstranea, m01, "napoli", "PRESA_IN_CARICO", INVIATA_IL);

            assertAnnullata(annulla(new Attore(secondoBanco, "BANCO"), diAltraOpt, conMotivo(MOTIVO)), diAltraOpt);

            assertThat(dati.stato(diAltraOpt)).isEqualTo("ANNULLATA");
        }
    }

    static Stream<Arguments> erogatoreEPartenzaAssegnata() {
        return Stream.of(Erogatore.values()).flatMap(tipo -> Stream.of(Partenza.values())
                .filter(partenza -> partenza.assegnata)
                .map(partenza -> Arguments.of(tipo, partenza)));
    }

    // ---------------------------------------------------------------------------------------------
    // Criterio 2
    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Criterio 2: motivo mancante, estraneo, candidato soltanto interpellato, sessione assente o scaduta e ritento")
    class CasiRespinti {

        static Stream<Arguments> partenzaECorpoSenzaMotivo() {
            return Stream.of(Partenza.values()).filter(partenza -> partenza != Partenza.CREATA)
                    .flatMap(partenza -> Stream.of(null, "{}", "{\"motivo\":null}", "{\"motivo\":\"\"}",
                                    "{\"motivo\":\"   \"}", "{\"motivo\":\"\\u00a0\\u202f\\t\\n\"}")
                            .map(corpo -> Arguments.of(partenza, corpo)));
        }

        @ParameterizedTest(name = "da {0} con corpo [{1}]: 400")
        @MethodSource("partenzaECorpoSenzaMotivo")
        @DisplayName("Fuori da Creata il Banco senza motivo riceve 400: stato, dati e avvisi invariati")
        void senzaMotivoFuoriDaCreata(Partenza partenza, String corpo) throws Exception {
            UUID id = richiesta(partenza, optErogatrice);
            Map<String, Object> prima = riga(id);

            assertErroreSulCampo(annulla(bancoAttore(), id, corpo), "motivo", MOTIVO_OBBLIGATORIO);

            assertInvariata(id, prima);
        }

        @Test
        @DisplayName("Senza motivo anche la OPT richiedente e l'erogatore assegnato ricevono 400")
        void senzaMotivoPerOgniAttoreAutorizzato() throws Exception {
            UUID id = richiesta(Partenza.CONFERMATA, operatoreEsterno);
            Map<String, Object> prima = riga(id);

            assertErroreSulCampo(annulla(opt(titolare), id, null), "motivo", MOTIVO_OBBLIGATORIO);
            assertErroreSulCampo(annulla(operatore(operatoreEsterno), id, "{}"), "motivo", MOTIVO_OBBLIGATORIO);

            assertInvariata(id, prima);
        }

        static Stream<Arguments> corpoNonValido() {
            return Stream.of(
                    Arguments.of("{\"motivo\":1}", "motivo", MOTIVO_NON_TESTUALE),
                    Arguments.of("{\"motivo\":true}", "motivo", MOTIVO_NON_TESTUALE),
                    Arguments.of("{\"motivo\":[\"testo\"]}", "motivo", MOTIVO_NON_TESTUALE),
                    Arguments.of("{\"motivo\":{\"testo\":\"x\"}}", "motivo", MOTIVO_NON_TESTUALE),
                    Arguments.of("{\"motivo\":\"testo\",\"stato\":\"CREATA\"}", "comando", SOLO_MOTIVO),
                    Arguments.of("{\"ragione\":\"testo\"}", "comando", SOLO_MOTIVO),
                    Arguments.of(conMotivo("a".repeat(501)), "motivo", MOTIVO_TROPPO_LUNGO),
                    Arguments.of(conMotivo("Motivo con controllo \u0007"), "motivo", MOTIVO_NON_SEMPLICE),
                    Arguments.of(conMotivo("Motivo con surrogato \uD800"), "motivo", MOTIVO_NON_SEMPLICE),
                    Arguments.of(conMotivo("Surrogato basso \uDC00 isolato"), "motivo", MOTIVO_NON_SEMPLICE));
        }

        @ParameterizedTest(name = "corpo [{0}]: 400 sul campo {1}")
        @MethodSource("corpoNonValido")
        @DisplayName("Un motivo non testuale, troppo lungo, con caratteri non ammessi o un campo in più riceve 400")
        void corpoNonValidoRespinto(String corpo, String campo, String messaggio) throws Exception {
            UUID id = richiesta(Partenza.PRESA_IN_CARICO, null);
            Map<String, Object> prima = riga(id);

            assertErroreSulCampo(annulla(bancoAttore(), id, corpo), campo, messaggio);

            assertInvariata(id, prima);
        }

        @ParameterizedTest(name = "corpo [{0}]")
        @ValueSource(strings = {
                "{\"motivo\":",
                "{\"motivo\":\"uno\",\"motivo\":\"due\"}",
                "[\"motivo\"]",
                "\"motivo\""})
        @DisplayName("Un corpo JSON malformato o con chiavi duplicate riceve 400 senza dati")
        void jsonMalformatoRespinto(String corpo) throws Exception {
            UUID id = richiesta(Partenza.PRESA_IN_CARICO, null);
            Map<String, Object> prima = riga(id);

            annulla(bancoAttore(), id, corpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codice").value("BAD_REQUEST"));

            assertInvariata(id, prima);
        }

        @Test
        @DisplayName("Un corpo non JSON riceve 415 e non annulla")
        void tipoDiContenutoNonAmmesso() throws Exception {
            UUID id = richiesta(Partenza.PRESA_IN_CARICO, null);
            Map<String, Object> prima = riga(id);

            mvc.perform(post(ANNULLAMENTO, id).with(user(banco.toString()).roles("BANCO")).with(csrf())
                            .contentType(MediaType.TEXT_PLAIN).content(MOTIVO))
                    .andExpect(status().isUnsupportedMediaType());

            assertInvariata(id, prima);
        }

        @Test
        @DisplayName("Anche da Creata un motivo oltre 500 caratteri è respinto")
        void daCreataMotivoTroppoLungo() throws Exception {
            UUID id = richiesta(Partenza.CREATA, null);
            Map<String, Object> prima = riga(id);

            assertErroreSulCampo(annulla(opt(titolare), id, conMotivo("b".repeat(501))), "motivo",
                    MOTIVO_TROPPO_LUNGO);

            assertInvariata(id, prima);
        }

        @Test
        @DisplayName("Valori limite accettati: 500 caratteri, accenti, tabulazioni e ritorni a capo conservati come inviati")
        void motivoAlLimiteConservatoComeInviato() throws Exception {
            UUID alLimite = richiesta(Partenza.PRESA_IN_CARICO, null);
            UUID suPiuRighe = richiesta(Partenza.IN_PROGRAMMAZIONE, optErogatrice);
            String cinquecento = "à".repeat(500);
            String conRighe = "  Prima riga\n\tseconda riga con perché  ";

            assertAnnullata(annulla(bancoAttore(), alLimite, conMotivo(cinquecento)), alLimite);
            assertAnnullata(annulla(opt(optErogatrice), suPiuRighe, conMotivo(conRighe)), suPiuRighe);

            assertThat(motivo(alLimite)).isEqualTo(cinquecento);
            assertThat(motivo(suPiuRighe)).isEqualTo(conRighe);
        }

        @ParameterizedTest(name = "{0} su una pratica {1}: 403")
        @MethodSource("it.unina.picus.richieste.web.AnnullamentoRichiestaIntegrazioneTest#estraneoEPartenza")
        @DisplayName("Un account estraneo riceve 403 senza dati: stato, dati e avvisi invariati")
        void estraneoRespinto(Estraneo estraneo, Partenza partenza) throws Exception {
            UUID id = richiesta(partenza, optErogatrice);
            Map<String, Object> prima = riga(id);

            assertNegato(annulla(estraneo(estraneo), id, conMotivo(MOTIVO)));

            assertInvariata(id, prima);
        }

        @ParameterizedTest(name = "corpo [{0}]")
        @ValueSource(strings = {"", "{}", "{\"motivo\":1}", "{\"altro\":\"x\"}", "{\"motivo\":"})
        @DisplayName("Il permesso è verificato prima del corpo: l'estraneo riceve 403 anche con un corpo non valido")
        void permessoPrimaDelCorpo(String corpo) throws Exception {
            UUID id = richiesta(Partenza.CONFERMATA, optErogatrice);
            Map<String, Object> prima = riga(id);

            assertNegato(annulla(opt(optEstranea), id, corpo.isEmpty() ? null : corpo));

            assertInvariata(id, prima);
        }

        @ParameterizedTest(name = "{0} soltanto interpellato: 403")
        @EnumSource(value = Erogatore.class, names = {"OPT", "OPERATORE_INTERNO", "OPERATORE_ESTERNO"})
        @DisplayName("Il candidato soltanto interpellato riceve 403, con il contatto in attesa e dopo l'assegnazione a un altro")
        void candidatoSoltantoInterpellato(Erogatore tipo) throws Exception {
            Attore interpellato = erogatore(tipo);
            Attore assegnato = erogatore(tipo == Erogatore.OPT ? Erogatore.OPERATORE_ESTERNO : Erogatore.OPT);
            UUID id = richiesta(Partenza.PRESA_IN_CARICO, null);

            // Con il contatto ancora in attesa di risposta.
            UUID contatto = interpella(id, interpellato.id());
            Map<String, Object> inAttesa = riga(id);
            assertNegato(annulla(interpellato, id, conMotivo(MOTIVO)));
            assertInvariata(id, inAttesa);

            // Dopo il rifiuto del candidato e l'assegnazione della pratica a un altro erogatore.
            rispondi(interpellato, contatto, false).andExpect(status().isOk());
            UUID altro = interpella(id, assegnato.id());
            rispondi(assegnato, altro, true).andExpect(status().isOk());
            assegna(altro);
            Map<String, Object> assegnata = riga(id);
            assertNegato(annulla(interpellato, id, conMotivo(MOTIVO)));
            assertInvariata(id, assegnata);
            assertThat(dati.esitoContatto(contatto)).isEqualTo("NON_DISPONIBILE");
        }

        @Test
        @DisplayName("Controllo positivo: il candidato interpellato annulla solo quando diventa l'assegnato")
        void interpellatoPoiAssegnato() throws Exception {
            Attore erogatore = operatore(operatoreInterno);
            UUID id = richiesta(Partenza.PRESA_IN_CARICO, null);
            UUID contatto = interpella(id, erogatore.id());
            assertNegato(annulla(erogatore, id, conMotivo(MOTIVO)));

            rispondi(erogatore, contatto, true).andExpect(status().isOk());
            assegna(contatto);

            assertAnnullata(annulla(erogatore, id, conMotivo(MOTIVO)), id);
            assertThat(dati.stato(id)).isEqualTo("ANNULLATA");
            assertThat(dati.erogatoreAssegnato(id)).isEqualTo(operatoreInterno);
        }

        @Test
        @DisplayName("Una pratica inesistente riceve 404 e un identificativo non valido 400")
        void praticaInesistente() throws Exception {
            annulla(bancoAttore(), UUID.randomUUID(), conMotivo(MOTIVO))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codice").value("NOT_FOUND"));
            mvc.perform(post("/api/richieste/non-un-uuid/annullamento").with(user(banco.toString()).roles("BANCO"))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(conMotivo(MOTIVO)))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "corpo [{0}]")
        @ValueSource(strings = {"", "{\"motivo\":\"Motivo sintetico di annullamento\"}"})
        @DisplayName("Senza sessione il comando riceve 401 senza dati né rimando al login; nulla cambia")
        void sessioneAssente(String corpo) throws Exception {
            UUID id = richiesta(Partenza.CONFERMATA, optErogatrice);
            Map<String, Object> prima = riga(id);

            MockHttpServletRequestBuilder richiesta = post(ANNULLAMENTO, id).with(csrf());
            if (!corpo.isEmpty()) {
                richiesta.contentType(MediaType.APPLICATION_JSON).content(corpo);
            }
            assertNonAutenticato(mvc.perform(richiesta));

            assertInvariata(id, prima);
        }

        @Test
        @DisplayName("Con la sessione scaduta il comando riceve 401 e nulla cambia; dopo un nuovo accesso il ritento riesce")
        void sessioneScadutaERitento() throws Exception {
            UUID id = richiesta(Partenza.IN_PROGRAMMAZIONE, optErogatrice);
            Map<String, Object> prima = riga(id);
            SessioneReale sessione = accedi(titolare);
            // Controllo positivo: prima della scadenza la sessione apre la pratica.
            sessione.esegui(get(DETTAGLIO, id)).andExpect(status().isOk());

            // Scadenza: il server invalida la sessione come al timeout e il client la ripresenta.
            sessione.http().invalidate();
            assertNonAutenticato(sessione.annulla(id, conMotivo(MOTIVO)));
            assertInvariata(id, prima);

            SessioneReale nuova = accedi(titolare);
            nuova.annulla(id, conMotivo(MOTIVO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stato").value("ANNULLATA"));
            assertThat(dati.stato(id)).isEqualTo("ANNULLATA");
            assertThat(destinatariAvvisati(id))
                    .containsExactlyInAnyOrder(banco, secondoBanco, titolare, optErogatrice);
        }

        @Test
        @DisplayName("Un utente autenticato senza token CSRF riceve 403 e nulla cambia")
        void senzaTokenCsrf() throws Exception {
            UUID id = richiesta(Partenza.PRESA_IN_CARICO, null);
            Map<String, Object> prima = riga(id);

            mvc.perform(post(ANNULLAMENTO, id).with(user(banco.toString()).roles("BANCO"))
                            .contentType(MediaType.APPLICATION_JSON).content(conMotivo(MOTIVO)))
                    .andExpect(status().isForbidden());

            assertInvariata(id, prima);
        }

        @Test
        @DisplayName("Ritento dopo un rifiuto per motivo mancante: con il motivo l'annullamento riesce una sola volta")
        void ritentoDopoMotivoMancante() throws Exception {
            UUID id = richiesta(Partenza.CONFERMATA, operatoreInterno);
            Map<String, Object> prima = riga(id);
            assertErroreSulCampo(annulla(operatore(operatoreInterno), id, null), "motivo", MOTIVO_OBBLIGATORIO);
            assertInvariata(id, prima);

            assertAnnullata(annulla(operatore(operatoreInterno), id, conMotivo(MOTIVO)), id);

            assertThat(motivo(id)).isEqualTo(MOTIVO);
            assertThat(destinatariAvvisati(id))
                    .containsExactlyInAnyOrder(banco, secondoBanco, titolare, operatoreInterno);
        }
    }

    static Stream<Arguments> estraneoEPartenza() {
        return Stream.of(Estraneo.values()).flatMap(estraneo -> Stream.of(Partenza.values())
                .map(partenza -> Arguments.of(estraneo, partenza)));
    }

    // ---------------------------------------------------------------------------------------------
    // Criterio 3
    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Criterio 3: stato Annullata, motivo, dettagli conservati e avvisi; un secondo comando non duplica nulla")
    class EsitoEAvvisi {

        @ParameterizedTest(name = "{0} annulla una pratica Creata")
        @ValueSource(strings = {"BANCO", "OPT_RICHIEDENTE"})
        @DisplayName("Da Creata sono avvisati tutti gli account del Banco e la OPT richiedente, e nessun altro")
        void avvisiDaCreata(String chi) throws Exception {
            UUID id = richiesta(Partenza.CREATA, null);
            Attore attore = "BANCO".equals(chi) ? bancoAttore() : opt(titolare);

            assertAnnullata(annulla(attore, id, null), id);

            assertThat(destinatariAvvisati(id)).containsExactlyInAnyOrder(banco, secondoBanco, titolare);
            for (UUID destinatario : List.of(banco, secondoBanco)) {
                assertAvvisoVisibile(new Attore(destinatario, "BANCO"), id);
            }
            assertAvvisoVisibile(opt(titolare), id);
            assertNessunAvvisoVisibile(id, opt(optEstranea), opt(optErogatrice), operatore(operatoreInterno),
                    operatore(operatoreEstraneo));
        }

        static Stream<Arguments> annullatoreEErogatore() {
            return Stream.of(Annullatore.values()).flatMap(annullatore -> Stream.of(Erogatore.values())
                    .map(tipo -> Arguments.of(annullatore, tipo)));
        }

        @ParameterizedTest(name = "{0} annulla con {1} assegnato")
        @MethodSource("annullatoreEErogatore")
        @DisplayName("Con un erogatore assegnato sono avvisati Banco, OPT richiedente ed erogatore, chiunque annulli")
        void avvisiConErogatoreAssegnato(Annullatore annullatore, Erogatore tipo) throws Exception {
            Attore assegnato = erogatore(tipo);
            UUID id = richiesta(Partenza.IN_PROGRAMMAZIONE, assegnato.id());
            Attore attore = switch (annullatore) {
                case BANCO -> bancoAttore();
                case OPT_RICHIEDENTE -> opt(titolare);
                case EROGATORE_ASSEGNATO -> assegnato;
            };

            assertAnnullata(annulla(attore, id, conMotivo(MOTIVO)), id);

            assertThat(destinatariAvvisati(id))
                    .containsExactlyInAnyOrder(banco, secondoBanco, titolare, assegnato.id());
            assertAvvisoVisibile(bancoAttore(), id);
            assertAvvisoVisibile(new Attore(secondoBanco, "BANCO"), id);
            assertAvvisoVisibile(opt(titolare), id);
            assertAvvisoVisibile(assegnato, id);
            List<Attore> nonCoinvolti = new ArrayList<>(List.of(opt(optEstranea), operatore(operatoreEstraneo)));
            for (Erogatore altro : Erogatore.values()) {
                if (altro != tipo) {
                    nonCoinvolti.add(erogatore(altro));
                }
            }
            assertNessunAvvisoVisibile(id, nonCoinvolti.toArray(Attore[]::new));
        }

        @ParameterizedTest(name = "da {0}")
        @EnumSource(value = Partenza.class, names = {"CONFERMATA", "RIFIUTATA_DOPO_ASSEGNAZIONE", "RIFIUTATA_SENZA_EROGATORE"})
        @DisplayName("Da Confermata e Rifiutata l'avviso raggiunge l'erogatore solo se la pratica gli era assegnata")
        void avvisiDaConfermataERifiutata(Partenza partenza) throws Exception {
            UUID id = richiesta(partenza, operatoreEsterno);

            assertAnnullata(annulla(bancoAttore(), id, conMotivo(MOTIVO)), id);

            if (partenza.assegnata) {
                assertThat(destinatariAvvisati(id))
                        .containsExactlyInAnyOrder(banco, secondoBanco, titolare, operatoreEsterno);
            } else {
                assertThat(destinatariAvvisati(id)).containsExactlyInAnyOrder(banco, secondoBanco, titolare);
                assertNessunAvvisoVisibile(id, operatore(operatoreEsterno));
            }
        }

        @Test
        @DisplayName("I candidati soltanto interpellati non sono avvisati; contatti e avvisi di disponibilità restano invariati")
        void interpellatiNonAvvisatiEStoricoConservato() throws Exception {
            UUID id = richiesta(Partenza.PRESA_IN_CARICO, null);
            UUID primo = interpella(id, optErogatrice);
            rispondi(opt(optErogatrice), primo, false).andExpect(status().isOk());
            UUID secondo = interpella(id, operatoreInterno);
            Map<String, Object> prima = riga(id);
            List<Map<String, Object>> contattiPrima = contatti(id);
            List<Map<String, Object>> avvisiDisponibilitaPrima = avvisiDisponibilita(id);
            assertThat(avvisiDisponibilitaPrima).hasSize(2);

            assertAnnullata(annulla(opt(titolare), id, conMotivo(MOTIVO)), id);

            assertThat(destinatariAvvisati(id)).containsExactlyInAnyOrder(banco, secondoBanco, titolare);
            assertNessunAvvisoVisibile(id, opt(optErogatrice), operatore(operatoreInterno));
            assertThat(contatti(id)).isEqualTo(contattiPrima);
            assertThat(avvisiDisponibilita(id)).isEqualTo(avvisiDisponibilitaPrima);
            assertThat(dati.esitoContatto(secondo)).isEqualTo("IN_ATTESA");
            assertThat(senzaStatoEMotivo(riga(id))).isEqualTo(senzaStatoEMotivo(prima));
        }

        @Test
        @DisplayName("Dettagli conservati e consultabili: Banco, OPT richiedente ed erogatore leggono la pratica Annullata")
        void dettagliConservatiEConsultabili() throws Exception {
            UUID id = richiesta(Partenza.CONFERMATA, optErogatrice);
            Map<String, Object> prima = riga(id);

            assertAnnullata(annulla(opt(optErogatrice), id, conMotivo(MOTIVO)), id);

            Map<String, Object> dopo = riga(id);
            assertThat(senzaStatoEMotivo(dopo)).isEqualTo(senzaStatoEMotivo(prima));
            assertThat(dopo.get("accordo_modalita")).isEqualTo("IN_PRESENZA");
            assertThat(dopo.get("erogatore_id")).isEqualTo(optErogatrice);
            for (Attore lettore : List.of(bancoAttore(), opt(titolare), opt(optErogatrice))) {
                mvc.perform(get(DETTAGLIO, id).with(user(lettore.id().toString()).roles(lettore.ruolo())))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.richiesta.id").value(id.toString()))
                        .andExpect(jsonPath("$.richiesta.stato").value("ANNULLATA"))
                        .andExpect(jsonPath("$.erogatoreAssegnatoId").value(optErogatrice.toString()))
                        .andExpect(jsonPath("$.accordo").exists());
            }
        }

        static Stream<Arguments> secondoComando() {
            return Stream.of(
                    Arguments.of("BANCO", null),
                    Arguments.of("BANCO", "{\"motivo\":\"Un altro motivo\"}"),
                    Arguments.of("OPT_RICHIEDENTE", "{}"),
                    Arguments.of("OPT_RICHIEDENTE", "{\"motivo\":\"Un altro motivo\"}"),
                    Arguments.of("EROGATORE_ASSEGNATO", "{\"motivo\":\"   \"}"),
                    Arguments.of("EROGATORE_ASSEGNATO", conMotivo(MOTIVO)));
        }

        @ParameterizedTest(name = "secondo comando di {0} con corpo [{1}]")
        @MethodSource("secondoComando")
        @DisplayName("Il secondo comando restituisce Annullata, conserva il primo motivo e non genera avvisi duplicati")
        void secondoComandoIdempotente(String chi, String corpo) throws Exception {
            UUID id = richiesta(Partenza.IN_PROGRAMMAZIONE, operatoreInterno);
            assertAnnullata(annulla(opt(titolare), id, conMotivo(MOTIVO)), id);
            Map<String, Object> dopoIlPrimo = riga(id);
            List<String> avvisiDopoIlPrimo = avvisiAnnullamento(id);
            assertThat(avvisiDopoIlPrimo).hasSize(4);
            Attore attore = switch (chi) {
                case "BANCO" -> bancoAttore();
                case "OPT_RICHIEDENTE" -> opt(titolare);
                default -> operatore(operatoreInterno);
            };

            assertAnnullata(annulla(attore, id, corpo), id);

            assertThat(riga(id)).isEqualTo(dopoIlPrimo);
            assertThat(motivo(id)).isEqualTo(MOTIVO);
            assertThat(avvisiAnnullamento(id)).isEqualTo(avvisiDopoIlPrimo);
            assertAvvisoVisibile(opt(titolare), id);
            assertAvvisoVisibile(operatore(operatoreInterno), id);
        }

        @Test
        @DisplayName("Su una pratica già Annullata l'estraneo riceve ancora 403 e nulla cambia")
        void estraneoSuPraticaAnnullata() throws Exception {
            UUID id = richiesta(Partenza.CONFERMATA, optErogatrice);
            assertAnnullata(annulla(bancoAttore(), id, conMotivo(MOTIVO)), id);
            Map<String, Object> annullata = riga(id);
            List<String> avvisi = avvisiAnnullamento(id);

            assertNegato(annulla(opt(optEstranea), id, conMotivo(MOTIVO)));

            assertThat(riga(id)).isEqualTo(annullata);
            assertThat(avvisiAnnullamento(id)).isEqualTo(avvisi);
        }

        @Test
        @DisplayName("Annullata da Presa in carico: la ricerca resta chiusa anche dopo il secondo comando")
        void ricercaChiusa() throws Exception {
            UUID id = richiesta(Partenza.PRESA_IN_CARICO, null);
            UUID disponibile = interpella(id, optErogatrice);
            rispondi(opt(optErogatrice), disponibile, true).andExpect(status().isOk());

            assertAnnullata(annulla(bancoAttore(), id, conMotivo(MOTIVO)), id);
            assertRicercaChiusa(id, disponibile);

            assertAnnullata(annulla(opt(titolare), id, conMotivo("Secondo comando")), id);
            assertRicercaChiusa(id, disponibile);
            assertThat(dati.contatti(id)).isEqualTo(1);
            assertThat(motivo(id)).isEqualTo(MOTIVO);
        }

        @Test
        @DisplayName("Un contatto ancora in attesa non può più ricevere risposta dopo l'annullamento")
        void contattoInAttesaNonRisponde() throws Exception {
            UUID id = richiesta(Partenza.PRESA_IN_CARICO, null);
            UUID inAttesa = interpella(id, operatoreEsterno);

            assertAnnullata(annulla(opt(titolare), id, conMotivo(MOTIVO)), id);

            rispondi(operatore(operatoreEsterno), inAttesa, true)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codice").value("CONFLICT"));
            assertThat(dati.esitoContatto(inAttesa)).isEqualTo("IN_ATTESA");
            assertThat(dati.stato(id)).isEqualTo("ANNULLATA");
            assertThat(dati.erogatoreAssegnato(id)).isNull();
        }

        @Test
        @DisplayName("Annullata da Creata: la pratica esce dalla coda del Banco e non può essere presa in carico")
        void creataEsceDallaCoda() throws Exception {
            UUID id = richiesta(Partenza.CREATA, null);
            assertThat(codaBanco()).contains(id);

            assertAnnullata(annulla(opt(titolare), id, null), id);
            assertThat(codaBanco()).doesNotContain(id);

            assertAnnullata(annulla(bancoAttore(), id, null), id);
            assertThat(codaBanco()).doesNotContain(id);
            mvc.perform(post(PRESA_IN_CARICO, id).with(user(banco.toString()).roles("BANCO")).with(csrf()))
                    .andExpect(status().isConflict());
            assertThat(dati.stato(id)).isEqualTo("ANNULLATA");
        }

        /**
         * Due comandi entrano insieme e restano fermi sul lock della pratica, tenuto dal test. Il
         * secondo parte solo quando il primo è già in coda: rilasciato il lock, PostgreSQL serve
         * le attese nell'ordine di arrivo, quindi il primo comando registra l'annullamento e il
         * secondo trova la pratica già Annullata.
         */
        @Test
        @DisplayName("Due comandi concorrenti: entrambi ricevono Annullata, vale il motivo del primo e gli avvisi non si duplicano")
        void comandiConcorrenti() throws Exception {
            UUID id = richiesta(Partenza.IN_PROGRAMMAZIONE, optErogatrice);
            String motivoPrimo = "Motivo del primo comando";
            String motivoSecondo = "Motivo del secondo comando";
            ExecutorService esecutore = Executors.newFixedThreadPool(2);
            Future<MvcResult> primo = null;
            Future<MvcResult> secondo = null;
            try {
                try (Connection blocco = dataSource.getConnection()) {
                    blocco.setAutoCommit(false);
                    int processoDelBlocco = bloccaRichiesta(blocco, id);
                    try {
                        primo = esecutore.submit(() -> annulla(bancoAttore(), id, conMotivo(motivoPrimo)).andReturn());
                        attendiComandiFermiSulLock(processoDelBlocco, 1);
                        secondo = esecutore.submit(() -> annulla(opt(titolare), id, conMotivo(motivoSecondo)).andReturn());
                        attendiComandiFermiSulLock(processoDelBlocco, 2);
                        assertThat(dati.stato(id)).isEqualTo("IN_PROGRAMMAZIONE");
                        assertThat(avvisiAnnullamento(id)).isEmpty();
                    } finally {
                        blocco.rollback();
                    }
                }
                for (MvcResult esito : List.of(primo.get(30, TimeUnit.SECONDS), secondo.get(30, TimeUnit.SECONDS))) {
                    assertThat(esito.getResponse().getStatus()).isEqualTo(200);
                    assertThat((String) JsonPath.read(testo(esito), "$.stato")).isEqualTo("ANNULLATA");
                }
            } finally {
                esecutore.shutdownNow();
            }
            assertThat(motivo(id)).isEqualTo(motivoPrimo);
            assertThat(destinatariAvvisati(id))
                    .containsExactlyInAnyOrder(banco, secondoBanco, titolare, optErogatrice);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Supporto
    // ---------------------------------------------------------------------------------------------

    private record Attore(UUID id, String ruolo) {}

    /** Sessione ottenuta con il login reale, con il token CSRF che il server le ha assegnato. */
    private record SessioneReale(MockMvc mvc, MockHttpSession http, String intestazione, String token) {

        ResultActions esegui(MockHttpServletRequestBuilder richiesta) throws Exception {
            // L'identità deve arrivare soltanto dalla sessione, mai da un contesto di sicurezza di test.
            TestSecurityContextHolder.clearContext();
            return mvc.perform(richiesta.session(http).header(intestazione, token));
        }

        ResultActions annulla(UUID richiesta, String corpo) throws Exception {
            return esegui(post(ANNULLAMENTO, richiesta).contentType(MediaType.APPLICATION_JSON)
                    .content(corpo.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private Attore bancoAttore() {
        return new Attore(banco, "BANCO");
    }

    private static Attore opt(UUID id) {
        return new Attore(id, "OPT");
    }

    private static Attore operatore(UUID id) {
        return new Attore(id, "OPERATORE");
    }

    private Attore erogatore(Erogatore tipo) {
        return switch (tipo) {
            case OPT -> opt(optErogatrice);
            case OPT_TRAMITE_PROFESSIONISTA -> opt(optTramiteProfessionista);
            case OPERATORE_INTERNO -> operatore(operatoreInterno);
            case OPERATORE_ESTERNO -> operatore(operatoreEsterno);
        };
    }

    /** Estranei rispetto alle pratiche preparate con {@code optErogatrice} come eventuale assegnata. */
    private Attore estraneo(Estraneo estraneo) {
        return switch (estraneo) {
            case OPT_ESTRANEA -> opt(optEstranea);
            case OPT_EROGATRICE_NON_ASSEGNATA -> opt(optTramiteProfessionista);
            case OPERATORE_ABILITATO_NON_ASSEGNATO -> operatore(operatoreInterno);
            case OPERATORE_SENZA_MISURE -> operatore(operatoreEstraneo);
        };
    }

    /**
     * Pratica della OPT richiedente nello stato di partenza indicato. Le pratiche assegnate nascono
     * Prese in carico e passano allo stato finale con un solo aggiornamento, perché i vincoli del
     * database legano lo stato Confermata alla presenza dell'accordo.
     *
     * @param erogatore account assegnato, usato solo se la partenza prevede un'assegnazione
     */
    private UUID richiesta(Partenza partenza, UUID erogatore) {
        UUID id = dati.richiesta(titolare, m01, "napoli",
                partenza.assegnata ? "PRESA_IN_CARICO" : partenza.stato, INVIATA_IL);
        if (partenza == Partenza.CONFERMATA) {
            jdbc.update("""
                    update richieste
                       set stato = 'CONFERMATA', erogatore_id = ?, erogatore_ambito = 'STESSO_COMUNE',
                           accordo_modalita = 'IN_PRESENZA', accordo_data_ora = ?, accordo_luogo = 'Sede di esempio',
                           accordo_nome_referente = 'Nome', accordo_cognome_referente = 'Cognome',
                           accordo_telefono_referente = '+39 000 0000000'
                     where id = ?
                    """, erogatore, Timestamp.from(DATA_ACCORDO), id);
        } else if (partenza.assegnata) {
            jdbc.update("""
                    update richieste set stato = ?, erogatore_id = ?, erogatore_ambito = 'STESSO_COMUNE'
                     where id = ?
                    """, partenza.stato, erogatore, id);
        }
        return id;
    }

    private ResultActions annulla(Attore attore, UUID richiesta, String corpo) throws Exception {
        MockHttpServletRequestBuilder comando = post(ANNULLAMENTO, richiesta)
                .with(user(attore.id().toString()).roles(attore.ruolo())).with(csrf());
        if (corpo != null) {
            comando.contentType(MediaType.APPLICATION_JSON).content(corpo.getBytes(StandardCharsets.UTF_8));
        }
        return mvc.perform(comando);
    }

    /** Corpo {@code {"motivo": ...}} con il testo codificato come stringa JSON, caratteri speciali compresi. */
    private static String conMotivo(String motivo) {
        StringBuilder json = new StringBuilder("{\"motivo\":\"");
        for (char c : motivo.toCharArray()) {
            if (c == '"' || c == '\\') {
                json.append('\\').append(c);
            } else if (c < 0x20 || Character.isSurrogate(c)) {
                json.append(String.format("\\u%04x", (int) c));
            } else {
                json.append(c);
            }
        }
        return json.append("\"}").toString();
    }

    private UUID interpella(UUID richiesta, UUID erogatore) throws Exception {
        MvcResult esito = mvc.perform(post(CONTATTI, richiesta).with(user(banco.toString()).roles("BANCO"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"erogatoreId\":\"" + erogatore + "\",\"ambito\":\"STESSO_COMUNE\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(testo(esito), "$.id"));
    }

    private ResultActions rispondi(Attore erogatore, UUID contatto, boolean disponibile) throws Exception {
        return mvc.perform(post(RISPOSTA, contatto).with(user(erogatore.id().toString()).roles(erogatore.ruolo()))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"disponibile\":" + disponibile + "}"));
    }

    private void assegna(UUID contatto) throws Exception {
        mvc.perform(post(ASSEGNAZIONE, contatto).with(user(banco.toString()).roles("BANCO")).with(csrf()))
                .andExpect(status().isOk());
    }

    private List<UUID> codaBanco() throws Exception {
        MvcResult esito = mvc.perform(get(CODA).with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk()).andReturn();
        List<String> id = JsonPath.read(testo(esito), "$[*].id");
        return id.stream().map(UUID::fromString).toList();
    }

    /** Login reale con una password generata per il test: serve a provare la scadenza della sessione. */
    private SessioneReale accedi(UUID account) throws Exception {
        String password = "prova-" + UUID.randomUUID();
        jdbc.update("update utenze_account set password_hash = ? where id = ?", encoder.encode(password), account);
        String email = jdbc.queryForObject("select email from utenze_account where id = ?", String.class, account);
        MockHttpSession http = new MockHttpSession();
        SessioneReale anonima = conTokenCsrf(http);
        anonima.esegui(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isNoContent());
        // Dopo il login Spring Security rigenera il token: va richiesto di nuovo.
        return conTokenCsrf(http);
    }

    private SessioneReale conTokenCsrf(MockHttpSession http) throws Exception {
        TestSecurityContextHolder.clearContext();
        String corpo = mvc.perform(get(TOKEN_CSRF).session(http)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new SessioneReale(mvc, http, JsonPath.read(corpo, "$.headerName"), JsonPath.read(corpo, "$.token"));
    }

    // ------------------------------------------------------------------ verifiche ricorrenti

    private void assertAnnullata(ResultActions esito, UUID richiesta) throws Exception {
        esito.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(richiesta.toString()))
                .andExpect(jsonPath("$.misuraId").value(m01))
                .andExpect(jsonPath("$.stato").value("ANNULLATA"));
    }

    private static void assertErroreSulCampo(ResultActions esito, String campo, String messaggio) throws Exception {
        esito.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.campi.*", hasSize(1)))
                .andExpect(jsonPath("$.campi." + campo).value(messaggio));
    }

    /** Diniego delle API: {@code 403} con corpo vuoto, nessun dato della pratica. */
    private static void assertNegato(ResultActions esito) throws Exception {
        esito.andExpect(status().isForbidden()).andExpect(content().string(emptyString()));
    }

    /** Sessione assente o scaduta: {@code 401} con corpo vuoto e senza rimando al login. */
    private static void assertNonAutenticato(ResultActions esito) throws Exception {
        esito.andExpect(status().isUnauthorized())
                .andExpect(content().string(emptyString()))
                .andExpect(header().doesNotExist("Location"));
    }

    /** Pratica identica, riga per riga, a prima del comando respinto e nessun avviso di annullamento. */
    private void assertInvariata(UUID richiesta, Map<String, Object> prima) {
        assertThat(riga(richiesta)).isEqualTo(prima);
        assertThat(avvisiAnnullamento(richiesta)).isEmpty();
    }

    /** Il destinatario vede nella propria casella un solo avviso di annullamento, riferito alla pratica. */
    private void assertAvvisoVisibile(Attore destinatario, UUID richiesta) throws Exception {
        List<Map<String, Object>> avvisi = avvisiDiAnnullamentoVistiDa(destinatario, richiesta);
        assertThat(avvisi).as("avvisi di annullamento visti da %s", destinatario).hasSize(1);
        assertThat(avvisi.get(0).get("contattoId")).isNull();
    }

    private void assertNessunAvvisoVisibile(UUID richiesta, Attore... attori) throws Exception {
        for (Attore attore : attori) {
            assertThat(avvisiDiAnnullamentoVistiDa(attore, richiesta))
                    .as("avvisi di annullamento visti da %s", attore).isEmpty();
        }
    }

    private List<Map<String, Object>> avvisiDiAnnullamentoVistiDa(Attore attore, UUID richiesta) throws Exception {
        MvcResult esito = mvc.perform(get(AVVISI).with(user(attore.id().toString()).roles(attore.ruolo())))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(testo(esito),
                "$[?(@.tipo == 'RICHIESTA_ANNULLATA' && @.richiestaId == '" + richiesta + "')]");
    }

    /** Dopo l'annullamento nessun comando della ricerca procede e la pratica resta senza assegnatario. */
    private void assertRicercaChiusa(UUID richiesta, UUID contattoDisponibile) throws Exception {
        mvc.perform(get(CANDIDATI, richiesta).with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isConflict());
        mvc.perform(post(CONTATTI, richiesta).with(user(banco.toString()).roles("BANCO")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"erogatoreId\":\"" + operatoreInterno + "\",\"ambito\":\"STESSO_COMUNE\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post(ASSEGNAZIONE, contattoDisponibile).with(user(banco.toString()).roles("BANCO")).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post(PRESA_IN_CARICO, richiesta).with(user(banco.toString()).roles("BANCO")).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(dati.stato(richiesta)).isEqualTo("ANNULLATA");
        assertThat(dati.erogatoreAssegnato(richiesta)).isNull();
        assertThat(dati.contattiInAttesa(richiesta)).isZero();
        assertThat(codaBanco()).doesNotContain(richiesta);
    }

    // ------------------------------------------------------------------ letture su PostgreSQL

    private Map<String, Object> riga(UUID richiesta) {
        return new HashMap<>(jdbc.queryForMap("select * from richieste where id = ?", richiesta));
    }

    private static Map<String, Object> senzaStatoEMotivo(Map<String, Object> riga) {
        Map<String, Object> copia = new HashMap<>(riga);
        copia.remove("stato");
        copia.remove("motivo_annullamento");
        return copia;
    }

    private String motivo(UUID richiesta) {
        return jdbc.queryForObject("select motivo_annullamento from richieste where id = ?", String.class, richiesta);
    }

    /** Destinatari degli avvisi di annullamento, uno per riga: un duplicato comparirebbe due volte. */
    private List<UUID> destinatariAvvisati(UUID richiesta) {
        return jdbc.queryForList("""
                select destinatario_id from comunicazioni_avvisi_operativi
                where richiesta_id = ? and tipo = 'RICHIESTA_ANNULLATA'
                """, UUID.class, richiesta);
    }

    /** Avvisi di annullamento come {@code id|destinatario|contatto|registrato_il}, per confronti esatti. */
    private List<String> avvisiAnnullamento(UUID richiesta) {
        return jdbc.query("""
                select id, destinatario_id, contatto_id, registrato_il from comunicazioni_avvisi_operativi
                where richiesta_id = ? and tipo = 'RICHIESTA_ANNULLATA' order by destinatario_id
                """, (rs, n) -> rs.getObject(1) + "|" + rs.getObject(2) + "|" + rs.getObject(3) + "|"
                + rs.getTimestamp(4).toInstant(), richiesta);
    }

    private List<Map<String, Object>> avvisiDisponibilita(UUID richiesta) {
        return jdbc.queryForList("""
                select * from comunicazioni_avvisi_operativi
                where richiesta_id = ? and tipo = 'RICHIESTA_DISPONIBILITA' order by id
                """, richiesta);
    }

    private List<Map<String, Object>> contatti(UUID richiesta) {
        return jdbc.queryForList("select * from richieste_contatti_disponibilita where richiesta_id = ? order by id",
                richiesta);
    }

    private static String testo(MvcResult esito) throws Exception {
        return esito.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ concorrenza

    /**
     * Blocca la riga della richiesta nella transazione della connessione indicata.
     *
     * @return identificativo del processo PostgreSQL che tiene il lock
     */
    private static int bloccaRichiesta(Connection connessione, UUID richiesta) throws SQLException {
        try (PreparedStatement blocca = connessione.prepareStatement("select id from richieste where id = ? for update")) {
            blocca.setObject(1, richiesta);
            try (ResultSet riga = blocca.executeQuery()) {
                assertThat(riga.next()).as("la richiesta da bloccare esiste").isTrue();
            }
        }
        try (Statement interrogazione = connessione.createStatement();
             ResultSet processo = interrogazione.executeQuery("select pg_backend_pid()")) {
            assertThat(processo.next()).isTrue();
            return processo.getInt(1);
        }
    }

    /**
     * Attende che le transazioni in attesa dietro il lock siano almeno quelle indicate. La prima
     * aspetta direttamente il processo del blocco, le successive si accodano dietro la prima: per
     * questo la catena delle attese si segue in modo ricorsivo.
     */
    private void attendiComandiFermiSulLock(int processoDelBlocco, int attese) throws InterruptedException {
        long scadenza = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        int inAttesa;
        do {
            inAttesa = jdbc.queryForObject("""
                    with recursive in_attesa(pid) as (
                        select a.pid from pg_stat_activity a where ? = any(pg_blocking_pids(a.pid))
                        union
                        select a.pid from pg_stat_activity a join in_attesa b on b.pid = any(pg_blocking_pids(a.pid))
                    )
                    select count(*) from in_attesa
                    """, Integer.class, processoDelBlocco);
            if (inAttesa >= attese) {
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < scadenza);
        fail("dopo 30 secondi solo " + inAttesa + " comandi su " + attese + " attendono il lock della richiesta");
    }
}
