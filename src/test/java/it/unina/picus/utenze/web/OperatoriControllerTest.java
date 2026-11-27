package it.unina.picus.utenze.web;

import static it.unina.picus.utenze.web.OperatoriDiProva.CF_A;
import static it.unina.picus.utenze.web.OperatoriDiProva.CF_B;
import static it.unina.picus.utenze.web.OperatoriDiProva.CF_C;
import static it.unina.picus.utenze.web.OperatoriDiProva.CODICE_FISCALE_IN_USO;
import static it.unina.picus.utenze.web.OperatoriDiProva.EMAIL_DI_ACCOUNT_REVOCATO;
import static it.unina.picus.utenze.web.OperatoriDiProva.EMAIL_IN_USO;
import static it.unina.picus.utenze.web.OperatoriDiProva.ESTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.INTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.NOME_VISUALIZZATO;
import static it.unina.picus.utenze.web.OperatoriDiProva.TELEFONO;
import static it.unina.picus.utenze.web.OperatoriDiProva.VALIDITA_LINK;
import static it.unina.picus.utenze.web.OperatoriDiProva.impronta;
import static it.unina.picus.utenze.web.OperatoriDiProva.linkConsegnati;
import static it.unina.picus.utenze.web.OperatoriDiProva.modulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.InvioEmail;

/**
 * Verifica via HTTP, su PostgreSQL 17 in Docker, dei criteri 1, 2, 3, 4 e 9 della storia #27 (task #154):
 * creazione dell'account personale di un Operatore da parte del Banco, rifiuto di tipologie fuori catalogo,
 * di campi mancanti o non validi e di email già in uso, e dinieghi per gli altri ruoli.
 *
 * <p>La catena di sicurezza è attiva: ogni utente simulato ha come nome l'UUID di un account con lo stesso
 * ruolo nel database e ogni POST porta un token CSRF valido, così un {@code 403} dipende solo dalle regole
 * di accesso. L'invio dell'email passa da uno spy di {@link InvioEmail}, che lascia girare l'adattatore
 * reale e permette di leggere il link consegnato. Oltre alla risposta si controlla sempre cosa è stato
 * salvato: account, profilo, misure e link.</p>
 *
 * <p>I criteri 5–8 sono in {@link AttivazioneOperatoreTest} e {@link ReinvioAttivazioneOperatoreTest}; la
 * concorrenza sui duplicati in {@code CreazioneOperatoriConcorrenzaTest}.</p>
 */
@IntegrazionePostgresql
@DisplayName("Creazione dell'account Operatore via HTTP su PostgreSQL (#154, storia #27)")
class OperatoriControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private InvioEmail posta;

    private DatiSintetici dati;
    private OperatoriDiProva operatori;
    private UUID banco;
    private long m01;
    private long m02;
    private long m03;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        operatori = new OperatoriDiProva(mvc, jdbc);
        m01 = dati.misura("M01");
        m02 = dati.misura("M02");
        m03 = dati.misura("M03");
        banco = dati.banco("Banco di coordinamento");
    }

    @Nested
    @DisplayName("Criterio 1: il Banco crea un Operatore di ciascuna tipologia, in attesa di attivazione, e invia un link monouso")
    class CreazioneValida {

        @ParameterizedTest(name = "tipologia {0}")
        @ValueSource(strings = { INTERNO, ESTERNO })
        @DisplayName("Salva account, profilo, misure e un solo link valido 72 ore; consegna il link all'email indicata")
        void creaPerTipologia(String tipologia) throws Exception {
            doReturn(false).when(posta).isSimulato();
            Map<String, Object> modulo = modulo("  Nuovo.Operatore@Picus.TEST ", "prvopr80a01f839c", tipologia, m03, m01);
            modulo.put("nome", "  Nome ");
            Instant prima = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusMillis(1);

            String corpo = operatori.crea(banco, modulo)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nomeVisualizzato").value(NOME_VISUALIZZATO))
                    .andExpect(jsonPath("$.email").value("nuovo.operatore@picus.test"))
                    .andExpect(jsonPath("$.stato").value("IN_ATTESA_DI_ATTIVAZIONE"))
                    .andExpect(jsonPath("$.invitoInviato").value(true))
                    .andExpect(jsonPath("$.erroreInvio").value(false))
                    .andExpect(jsonPath("$.messaggio").value("Operatore creato. Email di attivazione inviata."))
                    .andReturn().getResponse().getContentAsString();
            Instant dopo = Instant.now().plusMillis(1);
            UUID id = UUID.fromString(JsonPath.read(corpo, "$.id"));

            // Dati mostrati al Banco = dati salvati.
            assertThat(operatori.accountPerEmail("nuovo.operatore@picus.test")).isEqualTo(id);
            Map<String, Object> account = operatori.account(id);
            assertThat(account.get("ruolo")).isEqualTo("OPERATORE");
            assertThat(account.get("stato")).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
            assertThat(account.get("nome")).isEqualTo(NOME_VISUALIZZATO);
            Map<String, Object> profilo = operatori.profilo(id);
            assertThat(profilo.get("tipologia")).isEqualTo(tipologia);
            assertThat(profilo.get("comune_codice")).isEqualTo("napoli");
            assertThat(profilo.get("telefono")).isEqualTo(TELEFONO);
            assertThat(profilo.get("codice_fiscale")).isEqualTo(CF_A);
            assertThat(profilo.get("centro_picus")).isEqualTo(false);
            assertThat(operatori.misure(id)).containsExactly(Math.min(m01, m03), Math.max(m01, m03));
            assertThat(operatori.conta("select count(*) from utenze_account where ruolo = 'OPERATORE'")).isEqualTo(1);

            // Un solo link, consegnato una volta all'indirizzo dell'account con il nome dell'Operatore.
            verify(posta, times(1)).inviaLinkAttivazione(anyString(), anyString(), anyString());
            List<String> link = linkConsegnati(posta, "nuovo.operatore@picus.test", 1);
            assertThat(link).hasSize(1);
            verify(posta).inviaLinkAttivazione("nuovo.operatore@picus.test", NOME_VISUALIZZATO, link.get(0));
            assertThat(operatori.impronteAttive(id)).containsExactly(impronta(link.get(0)));
            Map<String, Object> token = operatori.token(link.get(0));
            assertThat(token.get("account_id")).isEqualTo(id);
            assertThat(token.get("usato_il")).isNull();
            Instant creato = ((Timestamp) token.get("creato_il")).toInstant();
            Instant scadenza = ((Timestamp) token.get("scadenza")).toInstant();
            assertThat(creato).isBetween(prima, dopo);
            assertThat(Duration.between(creato, scadenza)).isEqualTo(VALIDITA_LINK);

            // Il link non esce mai verso il Banco, né in chiaro né come impronta.
            assertThat(corpo).doesNotContain(link.get(0)).doesNotContain(impronta(link.get(0)));
        }

        @Test
        @DisplayName("Un Operatore per tipologia: due account distinti, ciascuno con il proprio link")
        void dueTipologieDueAccount() throws Exception {
            var interno = operatori.creaValido(banco, posta, "interno@picus.test", CF_A, INTERNO, m01);
            var esterno = operatori.creaValido(banco, posta, "esterno@picus.test", CF_B, ESTERNO, m02);

            assertThat(interno.id()).isNotEqualTo(esterno.id());
            assertThat(interno.link()).isNotEqualTo(esterno.link());
            assertThat(operatori.profilo(interno.id()).get("tipologia")).isEqualTo(INTERNO);
            assertThat(operatori.profilo(esterno.id()).get("tipologia")).isEqualTo(ESTERNO);
            assertThat(operatori.misure(interno.id())).containsExactly(m01);
            assertThat(operatori.misure(esterno.id())).containsExactly(m02);
            assertThat(operatori.token(interno.link()).get("account_id")).isEqualTo(interno.id());
            assertThat(operatori.token(esterno.link()).get("account_id")).isEqualTo(esterno.id());
            assertThat(linkConsegnati(posta, "interno@picus.test", 1)).hasSize(1);
            assertThat(linkConsegnati(posta, "esterno@picus.test", 1)).hasSize(1);
        }

        @Test
        @DisplayName("Un interno del Banco può far parte del Centro PICUS e il profilo lo registra")
        void internoDelCentroPicus() throws Exception {
            Map<String, Object> modulo = modulo("centro@picus.test", CF_A, INTERNO, m01);
            modulo.put("centroPicus", true);

            UUID id = idCreato(operatori.crea(banco, modulo).andExpect(status().isCreated()));

            assertThat(operatori.profilo(id).get("centro_picus")).isEqualTo(true);
        }

        @Test
        @DisplayName("Senza server di posta (modalità simulata): account creato e link generato, la risposta dice che non è partito")
        void modalitaSimulata() throws Exception {
            UUID id = idCreato(operatori.crea(banco, modulo("simulato@picus.test", CF_A, INTERNO, m01))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.invitoInviato").value(false))
                    .andExpect(jsonPath("$.erroreInvio").value(false)));

            assertThat(operatori.stato(id)).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
            String link = linkConsegnati(posta, "simulato@picus.test", 1).get(0);
            assertThat(operatori.impronteAttive(id)).containsExactly(impronta(link));
        }

        @Test
        @DisplayName("Se la posta rifiuta l'email l'account resta in attesa con il suo link e il Banco può reinviarlo")
        void invioFallito() throws Exception {
            doThrow(new IllegalStateException("SMTP non raggiungibile"))
                    .when(posta).inviaLinkAttivazione(anyString(), anyString(), anyString());

            UUID id = idCreato(operatori.crea(banco, modulo("smtp@picus.test", CF_A, ESTERNO, m01))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.stato").value("IN_ATTESA_DI_ATTIVAZIONE"))
                    .andExpect(jsonPath("$.invitoInviato").value(false))
                    .andExpect(jsonPath("$.erroreInvio").value(true)));

            assertThat(operatori.stato(id)).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
            assertThat(operatori.impronteAttive(id)).hasSize(1);

            doReturn(false).when(posta).isSimulato();
            doCallRealMethod()
                    .when(posta).inviaLinkAttivazione(anyString(), anyString(), anyString());
            operatori.reinvioDelBanco(banco, "BANCO", id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(id.toString()))
                    .andExpect(jsonPath("$.invitoInviato").value(true));
            String nuovo = OperatoriDiProva.ultimoLink(posta, "smtp@picus.test", 2);
            assertThat(operatori.impronteAttive(id)).containsExactly(impronta(nuovo));
        }
    }

    @Nested
    @DisplayName("Criterio 2: una tipologia fuori dal catalogo è rifiutata dal server e non parte nessun link")
    class TipologiaFuoriCatalogo {

        @ParameterizedTest(name = "tipologia \"{0}\"")
        @ValueSource(strings = { "ALTRO", "OPT", "BANCO", "OPERATORE", "interno_banco", "Esterno_Pagato_Banco",
                "INTERNO BANCO", "ESTERNO_PAGATO" })
        @DisplayName("Testo non previsto: 400 sul solo campo tipologia, nessun account")
        void testoNonPrevisto(String tipologia) throws Exception {
            Map<String, String> campi = campiErrati(operatori.crea(banco,
                    modulo("fuori@picus.test", CF_A, tipologia, m01)));

            assertThat(campi).containsExactly(Map.entry("tipo", "Seleziona una tipologia consentita"));
            nessunEffetto();
        }

        @Test
        @DisplayName("Tipologia assente o vuota: 400 sul campo tipologia, nessun account")
        void assenteOVuota() throws Exception {
            Map<String, Object> senza = modulo("fuori@picus.test", CF_A, INTERNO, m01);
            senza.remove("tipologia");
            Map<String, Object> vuota = modulo("fuori@picus.test", CF_A, "   ", m01);

            assertThat(campiErrati(operatori.crea(banco, senza)))
                    .containsExactly(Map.entry("tipo", "Seleziona una tipologia consentita"));
            assertThat(campiErrati(operatori.crea(banco, vuota)))
                    .containsExactly(Map.entry("tipo", "Seleziona una tipologia consentita"));
            nessunEffetto();
        }

        @Test
        @DisplayName("Tipologia non testuale (numero, booleano, elenco, oggetto): 400 sul campo tipologia")
        void nonTestuale() throws Exception {
            for (Object valore : List.of(1, true, List.of(INTERNO), Map.of("valore", INTERNO))) {
                Map<String, Object> modulo = modulo("fuori@picus.test", CF_A, INTERNO, m01);
                modulo.put("tipologia", valore);

                assertThat(campiErrati(operatori.crea(banco, modulo)))
                        .as("tipologia %s", valore)
                        .containsExactly(Map.entry("tipo", "Il campo deve contenere un testo"));
            }
            nessunEffetto();
        }

        @Test
        @DisplayName("Esterno pagato dal Banco indicato come Centro PICUS: inquadramento non ammesso, nessun account")
        void esternoNelCentroPicus() throws Exception {
            Map<String, Object> modulo = modulo("fuori@picus.test", CF_A, ESTERNO, m01);
            modulo.put("centroPicus", true);

            assertThat(campiErrati(operatori.crea(banco, modulo))).containsExactly(
                    Map.entry("centroPicus", "Solo gli operatori interni possono essere del Centro PICUS"));
            nessunEffetto();
        }
    }

    @Nested
    @DisplayName("Criterio 3: campi mancanti, nessuna misura o email e codice fiscale non validi non creano l'account")
    class CampiNonValidi {

        static Stream<Arguments> campiObbligatori() {
            return Stream.of(
                    Arguments.of("nome", "nome", "Inserisci il nome"),
                    Arguments.of("cognome", "cognome", "Inserisci il cognome"),
                    Arguments.of("codiceFiscale", "codiceFiscale", "Inserisci un codice fiscale valido"),
                    Arguments.of("email", "email", "Inserisci un'email valida"),
                    Arguments.of("telefono", "telefono", "Inserisci un numero di telefono valido"),
                    Arguments.of("comuneCodice", "comune", "Seleziona un comune del catalogo"));
        }

        @ParameterizedTest(name = "{0} assente")
        @MethodSource("campiObbligatori")
        @DisplayName("Campo obbligatorio assente: 400 sul solo campo mancante")
        void campoAssente(String campoModulo, String campoErrore, String messaggio) throws Exception {
            Map<String, Object> modulo = modulo("campi@picus.test", CF_A, INTERNO, m01);
            modulo.remove(campoModulo);

            assertThat(campiErrati(operatori.crea(banco, modulo))).containsExactly(Map.entry(campoErrore, messaggio));
            nessunEffetto();
        }

        @ParameterizedTest(name = "{0} di soli spazi")
        @MethodSource("campiObbligatori")
        @DisplayName("Campo obbligatorio di soli spazi: 400 sul solo campo vuoto")
        void campoVuoto(String campoModulo, String campoErrore, String messaggio) throws Exception {
            Map<String, Object> modulo = modulo("campi@picus.test", CF_A, INTERNO, m01);
            modulo.put(campoModulo, "   ");

            assertThat(campiErrati(operatori.crea(banco, modulo))).containsExactly(Map.entry(campoErrore, messaggio));
            nessunEffetto();
        }

        @Test
        @DisplayName("Nessuna misura (elenco vuoto o assente): 400 sul campo misure")
        void nessunaMisura() throws Exception {
            Map<String, Object> vuoto = modulo("campi@picus.test", CF_A, INTERNO);
            Map<String, Object> assente = modulo("campi@picus.test", CF_A, INTERNO);
            assente.remove("misure");

            for (Map<String, Object> modulo : List.of(vuoto, assente)) {
                assertThat(campiErrati(operatori.crea(banco, modulo)))
                        .containsExactly(Map.entry("misure", "Seleziona almeno una misura del catalogo"));
            }
            nessunEffetto();
        }

        @Test
        @DisplayName("Misura inesistente o disattivata, anche insieme a una valida: 400 sul campo misure")
        void misuraNonDelCatalogo() throws Exception {
            jdbc.update("update catalogo_misure set attiva = false where id = ?", m02);

            for (long misura : new long[] { 999_999L, m02 }) {
                assertThat(campiErrati(operatori.crea(banco, modulo("campi@picus.test", CF_A, INTERNO, m01, misura))))
                        .as("misura %s", misura)
                        .containsExactly(Map.entry("misure", "Seleziona almeno una misura del catalogo"));
            }
            nessunEffetto();
        }

        @Test
        @DisplayName("Misure non numeriche intere (testo, decimale, valore nullo): 400 sul campo misure")
        void misuraNonIntera() throws Exception {
            for (Object misura : Arrays.asList(String.valueOf(m01), 1.5, null)) {
                Map<String, Object> modulo = modulo("campi@picus.test", CF_A, INTERNO);
                modulo.put("misure", Arrays.asList(misura));

                assertThat(campiErrati(operatori.crea(banco, modulo)))
                        .as("misura %s", misura)
                        .containsExactly(
                                Map.entry("misure", "Seleziona le misure tramite il loro identificativo numerico"));
            }
            nessunEffetto();
        }

        @ParameterizedTest(name = "email \"{0}\"")
        @ValueSource(strings = { "senza-chiocciola.picus.test", "due@@picus.test", "spazio interno@picus.test",
                "senza-dominio@", "@picus.test", "senza-punto@picus" })
        @DisplayName("Email non valida: 400 sul solo campo email")
        void emailNonValida(String email) throws Exception {
            assertThat(campiErrati(operatori.crea(banco, modulo(email, CF_A, INTERNO, m01))))
                    .containsExactly(Map.entry("email", "Inserisci un'email valida"));
            nessunEffetto();
        }

        @ParameterizedTest(name = "codice fiscale \"{0}\"")
        @ValueSource(strings = { "PRVOPR80A01F839D", "PRVOPR80A01F839", "PRVOPR80A01F839CX", "12345678901",
                "PRVOPR80A01F8391", "PRV0PR80A01F839C" })
        @DisplayName("Codice fiscale con forma o carattere di controllo errati: 400 sul solo campo codice fiscale")
        void codiceFiscaleNonValido(String codiceFiscale) throws Exception {
            assertThat(campiErrati(operatori.crea(banco, modulo("campi@picus.test", codiceFiscale, INTERNO, m01))))
                    .containsExactly(Map.entry("codiceFiscale", "Inserisci un codice fiscale valido"));
            nessunEffetto();
        }

        @Test
        @DisplayName("Comune fuori catalogo e telefono non valido: 400 su ciascun campo")
        void comuneETelefono() throws Exception {
            Map<String, Object> modulo = modulo("campi@picus.test", CF_A, INTERNO, m01);
            modulo.put("comuneCodice", "comune-inesistente");
            modulo.put("telefono", "numero-senza-cifre");

            assertThat(campiErrati(operatori.crea(banco, modulo))).containsOnly(
                    Map.entry("comune", "Seleziona un comune del catalogo"),
                    Map.entry("telefono", "Inserisci un numero di telefono valido"));
            nessunEffetto();
        }

        @Test
        @DisplayName("Modulo vuoto: indica insieme tutti i campi da correggere")
        void moduloVuoto() throws Exception {
            assertThat(campiErrati(operatori.crea(banco, "{}"))).containsOnlyKeys(
                    "nome", "cognome", "codiceFiscale", "email", "telefono", "tipo", "comune", "misure");
            nessunEffetto();
        }

        @Test
        @DisplayName("Testo atteso ma valore di altro tipo: 400 sul campo, il booleano non diventa il testo \"true\"")
        void tipoJsonErrato() throws Exception {
            Map<String, Object> modulo = modulo("campi@picus.test", CF_A, INTERNO, m01);
            modulo.put("nome", true);
            modulo.put("cognome", 42);

            assertThat(campiErrati(operatori.crea(banco, modulo))).containsOnly(
                    Map.entry("nome", "Il campo deve contenere un testo"),
                    Map.entry("cognome", "Il campo deve contenere un testo"));
            nessunEffetto();
        }

        @Test
        @DisplayName("JSON non leggibile: 400 con messaggio di formato, nessun account")
        void jsonMalformato() throws Exception {
            operatori.crea(banco, "{\"nome\":")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messaggio").value("Formato dei dati non valido"));
            nessunEffetto();
        }

        @Test
        @DisplayName("La risposta d'errore indica i campi senza ripetere nessuno dei valori inseriti")
        void nonRipeteIValori() throws Exception {
            Map<String, Object> modulo = modulo("sentinella-email-a1", "SENTINELLACFB2", INTERNO, m01);
            modulo.put("telefono", "sentinella-telefono-c3");
            modulo.put("comuneCodice", "sentinella-comune-d4");
            modulo.put("tipologia", "SENTINELLA_TIPO_E5");

            String corpo = operatori.crea(banco, modulo)
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            assertThat(corpo).doesNotContain("sentinella-email-a1", "SENTINELLACFB2", "sentinella-telefono-c3",
                    "sentinella-comune-d4", "SENTINELLA_TIPO_E5");
            nessunEffetto();
        }
    }

    @Nested
    @DisplayName("Criterio 4: un'email già associata a un altro account, anche revocato, è rifiutata come già in uso")
    class EmailDuplicata {

        @ParameterizedTest(name = "account revocato {0}")
        @ValueSource(strings = { "OPERATORE", "OPT" })
        @DisplayName("Email di un account revocato: rifiutata, indica la riattivazione della stessa utenza")
        void emailDiAccountRevocato(String ruolo) throws Exception {
            UUID revocato = operatori.inserisciAccount("revocato@picus.test", ruolo, "REVOCATO");
            int account = operatori.conta("select count(*) from utenze_account");

            assertThat(campiErrati(operatori.crea(banco, modulo(" Revocato@Picus.Test ", CF_A, ESTERNO, m01))))
                    .containsExactly(Map.entry("email", EMAIL_DI_ACCOUNT_REVOCATO));

            // Nessun secondo account: lo storico resta sull'utenza revocata, che non viene toccata.
            assertThat(operatori.conta("select count(*) from utenze_account")).isEqualTo(account);
            assertThat(operatori.accountPerEmail("revocato@picus.test")).isEqualTo(revocato);
            assertThat(operatori.stato(revocato)).isEqualTo("REVOCATO");
            assertThat(operatori.conta("select count(*) from utenze_profili_operatore")).isZero();
            assertThat(operatori.conta("select count(*) from utenze_token_attivazione")).isZero();
            verify(posta, never()).inviaLinkAttivazione(anyString(), anyString(), anyString());
        }

        @ParameterizedTest(name = "account {0} {1}")
        @CsvSource({
                "OPERATORE, ATTIVO",
                "OPERATORE, IN_ATTESA_DI_ATTIVAZIONE",
                "OPT, ATTIVO",
                "BANCO, ATTIVO" })
        @DisplayName("Email di un account esistente, scritta con maiuscole e spazi: 400 sul campo email, nessun account")
        void emailDiAccountEsistente(String ruolo, String stato) throws Exception {
            UUID esistente = operatori.inserisciAccount("esistente@picus.test", ruolo, stato);
            int account = operatori.conta("select count(*) from utenze_account");

            assertThat(campiErrati(operatori.crea(banco, modulo("  ESISTENTE@Picus.Test ", CF_A, INTERNO, m01))))
                    .containsExactly(Map.entry("email", EMAIL_IN_USO));

            assertThat(operatori.conta("select count(*) from utenze_account")).isEqualTo(account);
            assertThat(operatori.accountPerEmail("esistente@picus.test")).isEqualTo(esistente);
            assertThat(operatori.stato(esistente)).isEqualTo(stato);
            assertThat(operatori.conta("select count(*) from utenze_profili_operatore")).isZero();
            assertThat(operatori.conta("select count(*) from utenze_token_attivazione")).isZero();
            verify(posta, never()).inviaLinkAttivazione(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Seconda creazione con la stessa email: rifiutata, il primo Operatore e il suo link restano invariati")
        void secondaCreazioneStessaEmail() throws Exception {
            var primo = operatori.creaValido(banco, posta, "doppio@picus.test", CF_A, INTERNO, m01);

            assertThat(campiErrati(operatori.crea(banco, modulo("doppio@picus.test", CF_B, ESTERNO, m02))))
                    .containsExactly(Map.entry("email", EMAIL_IN_USO));

            assertThat(operatori.conta("select count(*) from utenze_account where ruolo = 'OPERATORE'")).isEqualTo(1);
            assertThat(operatori.profilo(primo.id()).get("tipologia")).isEqualTo(INTERNO);
            assertThat(operatori.profilo(primo.id()).get("codice_fiscale")).isEqualTo(CF_A);
            assertThat(operatori.misure(primo.id())).containsExactly(m01);
            assertThat(operatori.impronteAttive(primo.id())).containsExactly(impronta(primo.link()));
            assertThat(linkConsegnati(posta, "doppio@picus.test", 1)).hasSize(1);
            verify(posta, times(1)).inviaLinkAttivazione(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Codice fiscale già registrato, scritto in minuscolo, con email diversa: 400 sul campo codice fiscale")
        void codiceFiscaleDuplicato() throws Exception {
            operatori.creaValido(banco, posta, "primo@picus.test", CF_C, INTERNO, m01);

            assertThat(campiErrati(operatori.crea(banco, modulo("secondo@picus.test", CF_C.toLowerCase(), ESTERNO, m01))))
                    .containsExactly(Map.entry("codiceFiscale", CODICE_FISCALE_IN_USO));

            assertThat(operatori.conta("select count(*) from utenze_account where ruolo = 'OPERATORE'")).isEqualTo(1);
            assertThat(operatori.conta("select count(*) from utenze_account where email = 'secondo@picus.test'")).isZero();
            verify(posta, never()).inviaLinkAttivazione(eq("secondo@picus.test"), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Criterio 9: OPT, Operatore o richiesta non autenticata non possono creare un Operatore")
    class Dinieghi {

        @ParameterizedTest(name = "ruolo {0}")
        @ValueSource(strings = { "OPT", "OPERATORE" })
        @DisplayName("Ruolo diverso dal Banco, con sessione e CSRF validi: 403 e nessun account")
        void ruoloNonAbilitato(String ruolo) throws Exception {
            UUID attore = "OPT".equals(ruolo)
                    ? dati.opt("OPT richiedente", "napoli")
                    : dati.operatore("Operatore richiedente", INTERNO, "napoli", false, m01);
            int account = operatori.conta("select count(*) from utenze_account");

            operatori.creaCome(attore, ruolo, modulo("negato@picus.test", CF_A, INTERNO, m01))
                    .andExpect(status().isForbidden());

            assertNessunaCreazione(account);
        }

        @Test
        @DisplayName("Senza sessione: 401 e nessun account")
        void senzaSessione() throws Exception {
            int account = operatori.conta("select count(*) from utenze_account");

            mvc.perform(post("/api/utenze/operatori").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(OperatoriDiProva.json(modulo("negato@picus.test", CF_A, INTERNO, m01))))
                    .andExpect(status().isUnauthorized());

            assertNessunaCreazione(account);
        }

        @Test
        @DisplayName("Banco senza token CSRF: 403 e nessun account")
        void bancoSenzaCsrf() throws Exception {
            int account = operatori.conta("select count(*) from utenze_account");

            mvc.perform(post("/api/utenze/operatori").with(user(banco.toString()).roles("BANCO"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(OperatoriDiProva.json(modulo("negato@picus.test", CF_A, INTERNO, m01))))
                    .andExpect(status().isForbidden());

            assertNessunaCreazione(account);
        }

        private void assertNessunaCreazione(int accountPrima) {
            assertThat(operatori.conta("select count(*) from utenze_account")).isEqualTo(accountPrima);
            assertThat(operatori.conta("select count(*) from utenze_account where email = 'negato@picus.test'")).isZero();
            assertThat(operatori.conta("select count(*) from utenze_token_attivazione")).isZero();
            verify(posta, never()).inviaLinkAttivazione(anyString(), anyString(), anyString());
        }
    }

    private static UUID idCreato(ResultActions risposta) throws Exception {
        return UUID.fromString(JsonPath.read(risposta.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    /** Verifica il {@code 400} del modulo e restituisce i campi da correggere con i messaggi. */
    private static Map<String, String> campiErrati(ResultActions risposta) throws Exception {
        String corpo = risposta
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.messaggio").value("Controlla i campi del modulo"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.campi");
    }

    private void nessunEffetto() {
        operatori.nessunOperatoreSalvato();
        verify(posta, never()).inviaLinkAttivazione(anyString(), anyString(), anyString());
    }
}
