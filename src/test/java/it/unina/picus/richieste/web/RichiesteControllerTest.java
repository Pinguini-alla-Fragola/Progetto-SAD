package it.unina.picus.richieste.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

/**
 * Verifica via HTTP, su PostgreSQL 17 in Docker, dei criteri 1–6 della #99 per la storia #48:
 * invio, elenchi e dettaglio, validazione, esclusione degli identificativi degli assistiti
 * (QS-02A), visibilità e dinieghi (QS-01A) e consultazione dell'accordo di una richiesta
 * Confermata predisposta come fixture coerente con la #52.
 *
 * <p>La catena di sicurezza è attiva. Account sintetici distinti per OPT titolare, OPT estranea,
 * Banco e operatore: ogni utente simulato ha come nome l'UUID dell'account con lo stesso ruolo
 * nel database e ogni POST porta un token CSRF valido. Login e UI non sono oggetto di questi test.</p>
 */
@IntegrazionePostgresql
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Invio e consultazione delle richieste via HTTP su PostgreSQL (#99)")
class RichiesteControllerTest {

    private static final String OPT = "OPT";
    private static final String BANCO = "BANCO";
    private static final String OPERATORE = "OPERATORE";
    private static final String DESCRIZIONE = "Accompagnamento ai servizi del territorio.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private long m01;
    private UUID titolare;
    private UUID estranea;
    private UUID banco;
    private UUID operatore;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        m01 = dati.misura("M01");
        titolare = dati.opt("OPT titolare", "napoli");
        estranea = dati.opt("OPT estranea", "portici");
        banco = dati.banco("Banco di coordinamento");
        operatore = dati.operatore("Operatore interno", "INTERNO_BANCO", "napoli", false, m01);
    }

    @Nested
    @DisplayName("Criterio 1: invio valido")
    class InvioValido {

        @Test
        @DisplayName("Con description: persiste i dati e attribuisce id, data, titolare della sessione e Creata")
        void conDescription() throws Exception {
            Instant prima = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusMillis(1);

            String corpo = invia(titolare, corpoInvio(m01, "PERSONA", "napoli", DESCRIZIONE))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.stato").value("CREATA"))
                    .andExpect(jsonPath("$.misuraId").value(m01))
                    .andExpect(jsonPath("$.nomeMisura").value("Accoglienza e ascolto"))
                    .andExpect(jsonPath("$.inviataIl").exists())
                    .andReturn().getResponse().getContentAsString();
            UUID id = UUID.fromString(JsonPath.read(corpo, "$.id"));
            Instant dopo = Instant.now().plusMillis(1);

            Map<String, Object> riga = riga(id);
            assertThat(riga.get("titolare_id")).isEqualTo(titolare);
            assertThat(riga.get("misura_id")).isEqualTo(m01);
            assertThat(riga.get("tipo_destinatario")).isEqualTo("PERSONA");
            assertThat(riga.get("comune_codice")).isEqualTo("napoli");
            assertThat(riga.get("description")).isEqualTo(DESCRIZIONE);
            assertThat(riga.get("stato")).isEqualTo("CREATA");
            assertThat(riga.get("erogatore_id")).isNull();
            assertThat(riga.get("erogatore_ambito")).isNull();
            assertThat(riga.get("accordo_modalita")).isNull();
            assertThat(((Timestamp) riga.get("inviata_il")).toInstant()).isBetween(prima, dopo);
            assertThat(conta("select count(*) from richieste")).isEqualTo(1);
        }

        @Test
        @DisplayName("Senza description, per un nucleo: description assente e stessi dati attribuiti dal server")
        void senzaDescription() throws Exception {
            UUID id = idCreato(invia(titolare, corpoInvio(m01, "NUCLEO", "portici", null)));

            Map<String, Object> riga = riga(id);
            assertThat(riga.get("tipo_destinatario")).isEqualTo("NUCLEO");
            assertThat(riga.get("comune_codice")).isEqualTo("portici");
            assertThat(riga.get("description")).isNull();
            assertThat(riga.get("titolare_id")).isEqualTo(titolare);
            assertThat(riga.get("stato")).isEqualTo("CREATA");
            assertThat(riga.get("erogatore_id")).isNull();
        }

        @Test
        @DisplayName("La description è normalizzata: spazi esterni rimossi, solo spazi salvata come assente")
        void normalizzazione() throws Exception {
            UUID conSpazi = idCreato(invia(titolare, corpoInvio(m01, "PERSONA", " napoli ", "  Testo  ")));
            UUID vuota = idCreato(invia(titolare, corpoInvio(m01, "PERSONA", "napoli", "   ")));

            assertThat(riga(conSpazi).get("description")).isEqualTo("Testo");
            assertThat(riga(conSpazi).get("comune_codice")).isEqualTo("napoli");
            assertThat(riga(vuota).get("description")).isNull();
        }

        @Test
        @DisplayName("La risposta 201 porta il Location del dettaglio della nuova richiesta")
        void location() throws Exception {
            var risultato = invia(titolare, corpoInvio(m01, "PERSONA", "napoli", null))
                    .andExpect(status().isCreated()).andReturn();
            String id = JsonPath.read(risultato.getResponse().getContentAsString(), "$.id");

            assertThat(risultato.getResponse().getHeader("Location")).isEqualTo("/api/richieste/" + id);
        }

        @Test
        @DisplayName("Idempotency-Key: stesso riferimento e dati restituiscono la stessa richiesta, dati diversi 409")
        void ritenti() throws Exception {
            String riferimento = UUID.randomUUID().toString();
            String corpo = corpoInvio(m01, "PERSONA", "napoli", DESCRIZIONE);

            UUID primo = idCreato(invia(titolare, corpo, riferimento));
            UUID ritento = idCreato(invia(titolare, corpo, riferimento));
            invia(titolare, corpoInvio(m01, "NUCLEO", "napoli", DESCRIZIONE), riferimento)
                    .andExpect(status().isConflict());
            invia(titolare, corpo, "non-un-uuid").andExpect(status().isBadRequest());
            UUID distinto = idCreato(invia(titolare, corpo));

            assertThat(ritento).isEqualTo(primo);
            assertThat(distinto).isNotEqualTo(primo);
            assertThat(conta("select count(*) from richieste")).isEqualTo(2);
            assertThat(riga(primo).get("tipo_destinatario")).isEqualTo("PERSONA");
        }

        @Test
        @DisplayName("Lo stesso riferimento usato da un'altra OPT crea una richiesta propria")
        void riferimentoPerTitolare() throws Exception {
            String riferimento = UUID.randomUUID().toString();
            String corpo = corpoInvio(m01, "PERSONA", "napoli", null);

            UUID propria = idCreato(invia(titolare, corpo, riferimento));
            UUID altrui = idCreato(invia(estranea, corpo, riferimento));

            assertThat(altrui).isNotEqualTo(propria);
            assertThat(riga(altrui).get("titolare_id")).isEqualTo(estranea);
        }
    }

    @Nested
    @DisplayName("Criterio 2: elenchi, coda e dettaglio")
    class ElenchiEDettaglio {

        @Test
        @DisplayName("La richiesta inviata compare nell'elenco della titolare e nella coda del Banco, non altrove")
        void elencoECoda() throws Exception {
            UUID id = idCreato(invia(titolare, corpoInvio(m01, "PERSONA", "napoli", DESCRIZIONE)));

            mvc.perform(get("/api/richieste/mie").with(come(titolare, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(id.toString())))
                    .andExpect(jsonPath("$[0].stato").value("CREATA"));
            mvc.perform(get("/api/richieste").with(come(titolare, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(id.toString())));
            mvc.perform(get("/api/richieste/mie").with(come(estranea, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(get("/api/richieste/coda").with(come(banco, BANCO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(id.toString())));
            mvc.perform(get("/api/richieste").with(come(banco, BANCO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(id.toString())));
        }

        @Test
        @DisplayName("Coda del Banco ed elenco OPT in ordine cronologico, a parità di istante per UUID")
        void ordineCronologico() throws Exception {
            Instant base = Instant.parse("2026-11-10T09:00:00Z");
            UUID terza = dati.richiesta(titolare, m01, "napoli", "CREATA", base.plus(2, ChronoUnit.HOURS));
            UUID prima = dati.richiesta(estranea, m01, "portici", "CREATA", base);
            UUID pariA = dati.richiesta(titolare, m01, "napoli", "CREATA", base.plus(1, ChronoUnit.HOURS));
            UUID pariB = dati.richiesta(titolare, m01, "napoli", "CREATA", base.plus(1, ChronoUnit.HOURS));
            dati.richiesta(titolare, m01, "napoli", "RIFIUTATA", base.minus(1, ChronoUnit.HOURS));
            List<String> pari = Stream.of(pariA, pariB).map(UUID::toString).sorted().toList();

            mvc.perform(get("/api/richieste/coda").with(come(banco, BANCO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(prima.toString(), pari.get(0), pari.get(1),
                            terza.toString())));
            mvc.perform(get("/api/richieste/mie").with(come(titolare, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[1].id").value(pari.get(0)))
                    .andExpect(jsonPath("$[3].id").value(terza.toString()));
        }

        @Test
        @DisplayName("Il dettaglio riporta id, data, misura e stato e la sua apertura non modifica la richiesta")
        void dettaglioSenzaModifiche() throws Exception {
            UUID id = idCreato(invia(titolare, corpoInvio(m01, "PERSONA", "napoli", DESCRIZIONE)));
            Map<String, Object> prima = riga(id);

            for (var lettore : List.of(come(titolare, OPT), come(banco, BANCO))) {
                mvc.perform(get("/api/richieste/{id}", id).with(lettore))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.richiesta.id").value(id.toString()))
                        .andExpect(jsonPath("$.richiesta.inviataIl").exists())
                        .andExpect(jsonPath("$.richiesta.misuraId").value(m01))
                        .andExpect(jsonPath("$.richiesta.nomeMisura").value("Accoglienza e ascolto"))
                        .andExpect(jsonPath("$.richiesta.stato").value("CREATA"))
                        .andExpect(jsonPath("$.tipoDestinatario").value("PERSONA"))
                        .andExpect(jsonPath("$.comuneCodice").value("napoli"))
                        .andExpect(jsonPath("$.erogatoreAssegnatoId").doesNotExist())
                        .andExpect(jsonPath("$.accordo").doesNotExist());
            }

            assertThat(riga(id)).isEqualTo(prima);
        }

        @Test
        @DisplayName("Una richiesta inesistente riceve 404")
        void inesistente() throws Exception {
            mvc.perform(get("/api/richieste/{id}", UUID.randomUUID()).with(come(banco, BANCO)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Un identificativo malformato riceve 400")
        void identificativoMalformato() throws Exception {
            mvc.perform(get("/api/richieste/{id}", "non-un-uuid").with(come(banco, BANCO)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Criterio 3: errori di validazione senza richieste create")
    class Validazione {

        static Stream<Arguments> corpiNonValidi() {
            return Stream.of(
                    Arguments.of("misura inesistente",
                            "{\"misuraId\":999999,\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\"}", "misuraId"),
                    Arguments.of("misura zero",
                            "{\"misuraId\":0,\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\"}", "misuraId"),
                    Arguments.of("misura negativa",
                            "{\"misuraId\":-1,\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\"}", "misuraId"),
                    Arguments.of("misura come testo",
                            "{\"misuraId\":\"{m01}\",\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\"}", "misuraId"),
                    Arguments.of("misura decimale",
                            "{\"misuraId\":1.5,\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\"}", "misuraId"),
                    Arguments.of("misura mancante",
                            "{\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\"}", "misuraId"),
                    Arguments.of("comune sconosciuto",
                            "{\"misuraId\":{m01},\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"atlantide\"}", "comuneCodice"),
                    Arguments.of("comune mancante",
                            "{\"misuraId\":{m01},\"tipoDestinatario\":\"PERSONA\"}", "comuneCodice"),
                    Arguments.of("comune vuoto",
                            "{\"misuraId\":{m01},\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"  \"}", "comuneCodice"),
                    Arguments.of("tipo mancante",
                            "{\"misuraId\":{m01},\"comuneCodice\":\"napoli\"}", "tipoDestinatario"),
                    Arguments.of("tipo non previsto",
                            "{\"misuraId\":{m01},\"tipoDestinatario\":\"FAMIGLIA\",\"comuneCodice\":\"napoli\"}",
                            "tipoDestinatario"),
                    Arguments.of("description troppo lunga",
                            "{\"misuraId\":{m01},\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\",\"description\":\""
                                    + "a".repeat(1001) + "\"}", "description"),
                    Arguments.of("description non testuale",
                            "{\"misuraId\":{m01},\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\",\"description\":5}",
                            "description"),
                    Arguments.of("description con carattere di controllo",
                            "{\"misuraId\":{m01},\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\",\"description\":\"a\\u0007\"}",
                            "description"),
                    Arguments.of("corpo vuoto", "{}", "misuraId"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("corpiNonValidi")
        @DisplayName("Ogni campo non valido o mancante riceve 400 con l'indicazione del campo")
        void campoNonValido(String caso, String modello, String campo) throws Exception {
            invia(titolare, modello.replace("{m01}", Long.toString(m01)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.campi." + campo).exists());

            assertThat(conta("select count(*) from richieste")).isZero();
        }

        @Test
        @DisplayName("Una misura disattivata non è disponibile per nuovi invii")
        void misuraDisattivata() throws Exception {
            jdbc.update("update catalogo_misure set attiva = false where id = ?", m01);

            invia(titolare, corpoInvio(m01, "PERSONA", "napoli", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campi.misuraId").exists());

            assertThat(conta("select count(*) from richieste")).isZero();
        }

        @ParameterizedTest(name = "corpo {0}")
        @ValueSource(strings = {"null", "[]", "{", "\"testo\"",
                "{\"misuraId\":1,\"misuraId\":2,\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\"}"})
        @DisplayName("Corpo nullo, non oggetto, malformato o con chiavi duplicate riceve 400")
        void corpoMalformato(String corpo) throws Exception {
            invia(titolare, corpo).andExpect(status().isBadRequest());

            assertThat(conta("select count(*) from richieste")).isZero();
        }

        @Test
        @DisplayName("I messaggi di errore non riportano il contenuto inviato")
        void erroriSenzaValori() throws Exception {
            String corpo = invia(titolare, "{\"misuraId\":{m01},\"tipoDestinatario\":\"VALORE-RISERVATO-TIPO\","
                    .replace("{m01}", Long.toString(m01))
                    + "\"comuneCodice\":\"VALORE-RISERVATO-COMUNE\",\"description\":\"VALORE-RISERVATO-TESTO"
                    + "a".repeat(1000) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            assertThat(corpo).doesNotContain("VALORE-RISERVATO");
        }
    }

    @Nested
    @DisplayName("Criterio 4: campi identificativi aggiuntivi (QS-02A)")
    class CampiIdentificativi {

        static Stream<Arguments> campiIdentificativi() {
            return Stream.of(
                    Arguments.of("codiceFiscale", "\"RSSMRA80A01F839X\""),
                    Arguments.of("nomeAssistito", "\"Mario\""),
                    Arguments.of("cognomeAssistito", "\"Rossi\""),
                    Arguments.of("nome", "\"Maria\""),
                    Arguments.of("cognome", "\"Bianchi\""),
                    Arguments.of("dataNascita", "\"1980-01-01\""),
                    Arguments.of("luogoNascita", "\"Napoli\""),
                    Arguments.of("telefonoAssistito", "\"+39 333 0000000\""),
                    Arguments.of("emailAssistito", "\"assistito@example.invalid\""),
                    Arguments.of("indirizzo", "\"Via Sintetica 1\""),
                    Arguments.of("numeroDocumento", "\"AA0000000\""),
                    Arguments.of("tesseraSanitaria", "\"80380000000000000000\""),
                    Arguments.of("iban", "\"IT00X0000000000000000000000\""),
                    Arguments.of("cittadinanza", "\"Sintetica\""),
                    Arguments.of("codiceAssistito", "\"ASS-0001\""),
                    Arguments.of("componentiNucleo", "[\"Anna\",\"Luca\"]"),
                    Arguments.of("assistito", "{\"nome\":\"Paolo\",\"codiceFiscale\":\"VRDPLA70B02F839Y\"}"),
                    Arguments.of("titolareId", "\"{estranea}\""),
                    Arguments.of("stato", "\"CONFERMATA\""),
                    Arguments.of("id", "\"{id}\""),
                    Arguments.of("erogatoreId", "\"{operatore}\""),
                    Arguments.of("inviataIl", "\"2020-01-01T00:00:00Z\""),
                    Arguments.of("CodiceFiscale", "\"BNCMRA85C41F839Z\""),
                    Arguments.of("descrizione", "\"Testo con dati dell'assistito\""));
        }

        @ParameterizedTest(name = "campo extra {0}")
        @MethodSource("campiIdentificativi")
        @DisplayName("Il server respinge l'intero invio, crea zero richieste e non riporta il valore in risposta, database e log")
        void campoExtraRespinto(String campo, String valore, CapturedOutput log) throws Exception {
            String sentinella = "SINTETICO-" + UUID.randomUUID();
            String valoreConSentinella = valore
                    .replace("{estranea}", estranea.toString())
                    .replace("{operatore}", operatore.toString())
                    .replace("{id}", UUID.randomUUID().toString());
            String corpo = "{\"misuraId\":" + m01 + ",\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\","
                    + "\"description\":\"" + sentinella + "-descrizione\",\"" + campo + "\":"
                    + valoreConSentinella.replaceFirst("\"", "\"" + sentinella + "-") + "}";

            String risposta = invia(titolare, corpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campi.richiesta").exists())
                    .andReturn().getResponse().getContentAsString();

            assertThat(conta("select count(*) from richieste")).isZero();
            assertThat(risposta).doesNotContain(sentinella);
            assertThat(occorrenzeNelDatabase(sentinella)).isZero();
            assertThat(log.getAll()).doesNotContain(sentinella);
        }

        @Test
        @DisplayName("Il numero di casi parametrizzati è almeno 20")
        void almenoVentiCasi() {
            assertThat(campiIdentificativi()).hasSizeGreaterThanOrEqualTo(20);
        }

        @Test
        @DisplayName("L'invio di controllo con i soli campi ammessi riesce")
        void invioDiControllo(CapturedOutput log) throws Exception {
            idCreato(invia(titolare, corpoInvio(m01, "PERSONA", "napoli", DESCRIZIONE)));

            assertThat(conta("select count(*) from richieste")).isEqualTo(1);
            assertThat(log.getAll()).doesNotContain(DESCRIZIONE);
        }

        @Test
        @DisplayName("Un titolare alterato nel payload non cambia l'identità attribuita dal server")
        void titolareAlterato() throws Exception {
            invia(titolare, "{\"misuraId\":" + m01 + ",\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\","
                    + "\"titolareId\":\"" + estranea + "\"}")
                    .andExpect(status().isBadRequest());
            assertThat(conta("select count(*) from richieste")).isZero();

            UUID id = idCreato(invia(titolare, corpoInvio(m01, "PERSONA", "napoli", null)));

            assertThat(riga(id).get("titolare_id")).isEqualTo(titolare);
            mvc.perform(get("/api/richieste/mie").with(come(estranea, OPT)))
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("Criterio 5: visibilità della description e dinieghi")
    class Visibilita {

        @Test
        @DisplayName("La OPT titolare e il Banco consultano la description")
        void titolareEBanco() throws Exception {
            UUID id = idCreato(invia(titolare, corpoInvio(m01, "PERSONA", "napoli", DESCRIZIONE)));

            for (var lettore : List.of(come(titolare, OPT), come(banco, BANCO))) {
                mvc.perform(get("/api/richieste/{id}", id).with(lettore))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.description").value(DESCRIZIONE))
                        .andExpect(jsonPath("$.titolareId").value(titolare.toString()));
            }
        }

        @Test
        @DisplayName("Una OPT estranea senza assegnazioni riceve 403 senza alcun dato della richiesta")
        void optEstranea() throws Exception {
            UUID id = idCreato(invia(titolare, corpoInvio(m01, "PERSONA", "napoli", DESCRIZIONE)));
            Map<String, Object> prima = riga(id);

            mvc.perform(get("/api/richieste/{id}", id).with(come(estranea, OPT)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(""));

            assertThat(riga(id)).isEqualTo(prima);
        }

        @Test
        @DisplayName("Un operatore non assegnato riceve 403; senza sessione si riceve 401; nessun dato restituito")
        void altriDinieghi() throws Exception {
            UUID id = idCreato(invia(titolare, corpoInvio(m01, "PERSONA", "napoli", DESCRIZIONE)));

            mvc.perform(get("/api/richieste/{id}", id).with(come(operatore, OPERATORE)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(""));
            mvc.perform(get("/api/richieste/{id}", id))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("L'erogatore assegnato vede il dettaglio senza description né titolare")
        void erogatoreAssegnato() throws Exception {
            UUID id = dati.richiesta(titolare, m01, "napoli", "IN_PROGRAMMAZIONE", Instant.parse("2026-11-10T09:00:00Z"));
            jdbc.update("update richieste set erogatore_id = ?, erogatore_ambito = 'STESSO_COMUNE' where id = ?",
                    operatore, id);

            mvc.perform(get("/api/richieste/{id}", id).with(come(operatore, OPERATORE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.erogatoreAssegnatoId").value(operatore.toString()))
                    .andExpect(jsonPath("$.description").doesNotExist())
                    .andExpect(jsonPath("$.titolareId").doesNotExist());
            mvc.perform(get("/api/richieste/{id}", id).with(come(estranea, OPT)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Solo una OPT invia: Banco e operatore ricevono 403 anche con corpo malformato; senza sessione 401")
        void invioRiservatoAlleOpt() throws Exception {
            String corpo = corpoInvio(m01, "PERSONA", "napoli", null);

            mvc.perform(richiestaInvio(corpo).with(come(banco, BANCO)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(richiestaInvio(corpo).with(come(operatore, OPERATORE)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(richiestaInvio("{").with(come(banco, BANCO)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(richiestaInvio(corpo).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(richiestaInvio(corpo).with(come(titolare, OPT))).andExpect(status().isForbidden());

            assertThat(conta("select count(*) from richieste")).isZero();
        }

        @Test
        @DisplayName("La coda è riservata al Banco e l'elenco proprio alle OPT")
        void elenchiPerRuolo() throws Exception {
            mvc.perform(get("/api/richieste/coda").with(come(titolare, OPT))).andExpect(status().isForbidden());
            mvc.perform(get("/api/richieste/coda").with(come(operatore, OPERATORE))).andExpect(status().isForbidden());
            mvc.perform(get("/api/richieste/mie").with(come(banco, BANCO))).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Criterio 6: accordo di una richiesta Confermata (fixture #52)")
    class AccordoConfermato {

        @Test
        @DisplayName("La titolare consulta i dettagli dell'accordo in presenza")
        void accordoInPresenza() throws Exception {
            Instant appuntamento = Instant.parse("2026-12-03T09:30:00Z");
            UUID id = dati.appuntamento(titolare, m01, operatore, appuntamento, "CONFERMATA");

            mvc.perform(get("/api/richieste/{id}", id).with(come(titolare, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.richiesta.stato").value("CONFERMATA"))
                    .andExpect(jsonPath("$.erogatoreAssegnatoId").value(operatore.toString()))
                    .andExpect(jsonPath("$.accordo.dataOra").exists())
                    .andExpect(jsonPath("$.accordo.luogo").value("Sede di esempio"))
                    .andExpect(jsonPath("$.accordo.nomeReferente").value("Nome"))
                    .andExpect(jsonPath("$.accordo.cognomeReferente").value("Cognome"))
                    .andExpect(jsonPath("$.accordo.telefonoReferente").value("+39 000 0000000"))
                    .andExpect(jsonPath("$.accordo.link").doesNotExist());
        }

        @Test
        @DisplayName("La titolare consulta data e link di un accordo a distanza")
        void accordoADistanza() throws Exception {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario, inviata_il,
                                           stato, erogatore_id, erogatore_ambito, accordo_modalita, accordo_data_ora,
                                           accordo_link)
                    values (?, ?, ?, 'napoli', 'PERSONA', ?, 'CONFERMATA', ?, 'CENTRO_PICUS_A_DISTANZA', 'A_DISTANZA', ?,
                            'https://meet.example.org/accordo')
                    """, id, titolare, m01, Timestamp.from(Instant.parse("2026-11-01T09:00:00Z")), operatore,
                    Timestamp.from(Instant.parse("2026-12-03T09:30:00Z")));

            mvc.perform(get("/api/richieste/{id}", id).with(come(titolare, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.richiesta.stato").value("CONFERMATA"))
                    .andExpect(jsonPath("$.accordo.dataOra").exists())
                    .andExpect(jsonPath("$.accordo.link").value("https://meet.example.org/accordo"))
                    .andExpect(jsonPath("$.accordo.luogo").doesNotExist());
        }

        @Test
        @DisplayName("L'accordo resta consultabile dopo l'annullamento; una OPT estranea riceve 403")
        void accordoDopoAnnullamentoEDiniego() throws Exception {
            UUID id = dati.appuntamento(titolare, m01, operatore, Instant.parse("2026-12-03T09:30:00Z"), "ANNULLATA");

            mvc.perform(get("/api/richieste/{id}", id).with(come(titolare, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.richiesta.stato").value("ANNULLATA"))
                    .andExpect(jsonPath("$.accordo.luogo").value("Sede di esempio"));
            mvc.perform(get("/api/richieste/{id}", id).with(come(estranea, OPT)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(""));
        }
    }

    private static RequestPostProcessor come(UUID account, String ruolo) {
        return user(account.toString()).roles(ruolo);
    }

    private static MockHttpServletRequestBuilder richiestaInvio(String corpo) {
        return post("/api/richieste").contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private ResultActions invia(UUID opt, String corpo) throws Exception {
        return mvc.perform(richiestaInvio(corpo).with(come(opt, OPT)).with(csrf()));
    }

    private ResultActions invia(UUID opt, String corpo, String riferimento) throws Exception {
        return mvc.perform(richiestaInvio(corpo).header("Idempotency-Key", riferimento).with(come(opt, OPT)).with(csrf()));
    }

    private static UUID idCreato(ResultActions risposta) throws Exception {
        String corpo = risposta.andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(corpo, "$.id");
        return UUID.fromString(id);
    }

    private static String corpoInvio(long misura, String tipo, String comune, String descrizione) {
        return "{\"misuraId\":" + misura + ",\"tipoDestinatario\":\"" + tipo + "\",\"comuneCodice\":\"" + comune + "\""
                + (descrizione == null ? "" : ",\"description\":\"" + descrizione + "\"") + "}";
    }

    private Map<String, Object> riga(UUID id) {
        return jdbc.queryForMap("select * from richieste where id = ?", id);
    }

    private int conta(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    /** Cerca il testo in ogni riga di ogni tabella applicativa, convertita in testo da PostgreSQL. */
    private int occorrenzeNelDatabase(String testo) {
        List<String> tabelle = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = current_schema() and table_type = 'BASE TABLE'
                """, String.class);
        List<Integer> conteggi = new ArrayList<>();
        for (String tabella : tabelle) {
            conteggi.add(jdbc.queryForObject("select count(*) from \"" + tabella.replace("\"", "\"\"")
                    + "\" t where t::text like ?", Integer.class, "%" + testo + "%"));
        }
        return conteggi.stream().mapToInt(Integer::intValue).sum();
    }
}
