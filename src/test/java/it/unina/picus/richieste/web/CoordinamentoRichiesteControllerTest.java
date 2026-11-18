package it.unina.picus.richieste.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

/**
 * Verifica end-to-end via HTTP, su PostgreSQL 17 in Docker, dei criteri della #102 per la #49:
 * coda e presa in carico, ricerca ordinata, interpello con avviso, integrazione con la risposta
 * della #51, assegnazione e casi di conflitto.
 *
 * <p>La catena di sicurezza è attiva. Gli utenti simulati hanno come nome l'UUID di un account
 * sintetico con lo stesso ruolo nel database e ogni POST porta un token CSRF valido: un
 * {@code 403} dipende dunque dalle regole di accesso, non da CSRF o dalla risoluzione
 * dell'identità. Login e UI non sono oggetto di questi test.</p>
 */
@IntegrazionePostgresql
@DisplayName("Coordinamento delle richieste via HTTP su PostgreSQL (#102)")
class CoordinamentoRichiesteControllerTest {

    private static final String BANCO = "BANCO";
    private static final String OPT = "OPT";
    private static final String OPERATORE = "OPERATORE";
    private static final String STESSO_COMUNE = "STESSO_COMUNE";
    private static final String COMUNE_LIMITROFO = "COMUNE_LIMITROFO";
    private static final String A_DISTANZA = "CENTRO_PICUS_A_DISTANZA";
    private static final Instant INVIO = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private long m01;
    private UUID banco;
    private UUID titolare;
    private UUID optLocale;
    private UUID optLimitrofa;
    private UUID operatore;
    private UUID optEstranea;

    /**
     * Dati di base per una richiesta M01 a Napoli: OPT locale e OPT di Portici con offerta,
     * operatore interno di Napoli con la misura nel profilo, OPT titolare ed estranea senza offerta.
     */
    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        m01 = dati.misura("M01");
        banco = dati.banco("Banco di coordinamento");
        titolare = dati.opt("OPT titolare", "napoli");
        optLocale = dati.opt("OPT locale", "napoli");
        dati.offerta(optLocale, m01);
        optLimitrofa = dati.opt("OPT limitrofa", "portici");
        dati.offerta(optLimitrofa, m01);
        operatore = dati.operatore("Operatore interno", "INTERNO_BANCO", "napoli", false, m01);
        optEstranea = dati.opt("OPT estranea", "napoli");
    }

    @Nested
    @DisplayName("Criterio 1: coda, dettaglio e presa in carico esplicita")
    class CodaEPresaInCarico {

        @Test
        @DisplayName("La coda del Banco elenca le sole richieste aperte in ordine cronologico di invio")
        void codaCronologica() throws Exception {
            UUID terza = dati.richiesta(titolare, m01, "napoli", "IN_PROGRAMMAZIONE", INVIO.plus(3, ChronoUnit.HOURS));
            UUID prima = dati.richiesta(titolare, m01, "napoli", "CREATA", INVIO.plus(1, ChronoUnit.HOURS));
            dati.richiesta(titolare, m01, "napoli", "RIFIUTATA", INVIO);
            UUID seconda = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIO.plus(2, ChronoUnit.HOURS));
            dati.richiesta(titolare, m01, "napoli", "ANNULLATA", INVIO.plus(4, ChronoUnit.HOURS));

            mvc.perform(get("/api/richieste/coda").with(come(banco, BANCO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(prima.toString(), seconda.toString(), terza.toString())))
                    .andExpect(jsonPath("$[*].stato", contains("CREATA", "PRESA_IN_CARICO", "IN_PROGRAMMAZIONE")));
        }

        @Test
        @DisplayName("Aprire il dettaglio non cambia lo stato della richiesta")
        void dettaglioSenzaTransizione() throws Exception {
            UUID richiesta = creata();

            for (int i = 0; i < 2; i++) {
                mvc.perform(get("/api/richieste/{id}", richiesta).with(come(banco, BANCO)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.richiesta.stato").value("CREATA"));
            }
            mvc.perform(get("/api/richieste/coda").with(come(banco, BANCO))).andExpect(status().isOk());

            assertThat(dati.stato(richiesta)).isEqualTo("CREATA");
        }

        @Test
        @DisplayName("Il Banco prende in carico esplicitamente una richiesta Creata")
        void presaInCaricoEsplicita() throws Exception {
            UUID richiesta = creata();

            presaInCarico(banco, BANCO, richiesta)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(richiesta.toString()))
                    .andExpect(jsonPath("$.stato").value("PRESA_IN_CARICO"));

            assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
            assertThat(dati.contatti(richiesta)).isZero();
        }

        @Test
        @DisplayName("Il corpo vuoto {} è ammesso per la presa in carico")
        void presaInCaricoConCorpoVuoto() throws Exception {
            UUID richiesta = creata();

            mvc.perform(post("/api/richieste/{id}/presa-in-carico", richiesta).with(come(banco, BANCO)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stato").value("PRESA_IN_CARICO"));
        }

        @ParameterizedTest(name = "corpo {0}")
        @ValueSource(strings = {"{\"stato\":\"PRESA_IN_CARICO\"}", "null", "[]", "{"})
        @DisplayName("Un corpo con campi, null o malformato riceve 400 e lascia la richiesta Creata")
        void presaInCaricoConCorpoNonValido(String corpo) throws Exception {
            UUID richiesta = creata();

            mvc.perform(post("/api/richieste/{id}/presa-in-carico", richiesta).with(come(banco, BANCO)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest());

            assertThat(dati.stato(richiesta)).isEqualTo("CREATA");
        }

        @ParameterizedTest(name = "da {0}")
        @ValueSource(strings = {"PRESA_IN_CARICO", "IN_PROGRAMMAZIONE", "RIFIUTATA", "ANNULLATA"})
        @DisplayName("La presa in carico di una richiesta non Creata riceve 409 e non cambia stato")
        void presaInCaricoStatoIncompatibile(String stato) throws Exception {
            UUID richiesta = dati.richiesta(titolare, m01, "napoli", stato, INVIO);

            presaInCarico(banco, BANCO, richiesta).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codice").value("CONFLICT"));

            assertThat(dati.stato(richiesta)).isEqualTo(stato);
        }

        @Test
        @DisplayName("Una seconda presa in carico della stessa richiesta riceve 409")
        void presaInCaricoRipetuta() throws Exception {
            UUID richiesta = creata();
            presaInCarico(banco, BANCO, richiesta).andExpect(status().isOk());

            presaInCarico(banco, BANCO, richiesta).andExpect(status().isConflict());

            assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
        }

        @Test
        @DisplayName("Una richiesta inesistente riceve 404")
        void presaInCaricoInesistente() throws Exception {
            presaInCarico(banco, BANCO, UUID.randomUUID()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Senza sessione coda e presa in carico ricevono 401")
        void senzaSessione() throws Exception {
            UUID richiesta = creata();

            mvc.perform(get("/api/richieste/coda")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/richieste/{id}/presa-in-carico", richiesta).with(csrf()))
                    .andExpect(status().isUnauthorized());

            assertThat(dati.stato(richiesta)).isEqualTo("CREATA");
        }

        @ParameterizedTest(name = "ruolo {0}")
        @ValueSource(strings = {OPT, OPERATORE})
        @DisplayName("OPT e operatori ricevono 403 su ogni azione di coordinamento e nulla cambia")
        void azioniDiCoordinamentoVietate(String ruolo) throws Exception {
            UUID attore = OPT.equals(ruolo) ? titolare : operatore;
            UUID creata = creata();
            UUID presa = presaInCaricoDiProva();
            UUID contatto = contattoCreato(presa, optLocale, STESSO_COMUNE);
            risposta(optLocale, OPT, contatto, true).andExpect(status().isOk());

            mvc.perform(get("/api/richieste/coda").with(come(attore, ruolo))).andExpect(status().isForbidden());
            presaInCarico(attore, ruolo, creata).andExpect(status().isForbidden());
            mvc.perform(get("/api/richieste/{id}/candidati", presa).with(come(attore, ruolo)))
                    .andExpect(status().isForbidden());
            interpella(attore, ruolo, presa, optLimitrofa, COMUNE_LIMITROFO).andExpect(status().isForbidden());
            mvc.perform(get("/api/richieste/{id}/contatti", presa).with(come(attore, ruolo)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/richieste/{id}/contatti/{c}", presa, contatto).with(come(attore, ruolo)))
                    .andExpect(status().isForbidden());
            assegna(attore, ruolo, contatto).andExpect(status().isForbidden());

            assertThat(dati.stato(creata)).isEqualTo("CREATA");
            assertThat(dati.stato(presa)).isEqualTo("PRESA_IN_CARICO");
            assertThat(dati.erogatoreAssegnato(presa)).isNull();
            assertThat(dati.contatti(presa)).isEqualTo(1);
            assertThat(dati.avvisi(presa)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Criterio 2: candidati ordinati per prossimità, tipo e carico")
    class Candidati {

        @Test
        @DisplayName("Ordine completo con dati sintetici e carico dei soli appuntamenti Confermata futuri")
        void ordineCompleto() throws Exception {
            dati.abilitaADistanza(m01);
            UUID esterno = dati.operatore("Operatore esterno", "ESTERNO_PAGATO_BANCO", "napoli", false, m01);
            UUID centro = dati.operatore("Centro PICUS Pozzuoli", "INTERNO_BANCO", "pozzuoli", true, m01);
            Instant futuro = Instant.now().plus(7, ChronoUnit.DAYS);
            Instant passato = Instant.now().minus(7, ChronoUnit.DAYS);
            // Stesso comune: esterno 1, interno 2, OPT 0 appuntamenti validi. L'ordine atteso
            // (esterno, interno, OPT) distingue la precedenza per ruolo dal minor carico.
            dati.appuntamento(titolare, m01, esterno, futuro, "CONFERMATA");
            dati.appuntamento(titolare, m01, operatore, futuro, "CONFERMATA");
            dati.appuntamento(titolare, m01, operatore, futuro.plus(1, ChronoUnit.DAYS), "CONFERMATA");
            dati.appuntamento(titolare, m01, operatore, passato, "CONFERMATA");
            dati.appuntamento(titolare, m01, operatore, futuro, "ANNULLATA");
            dati.appuntamento(titolare, m01, optLocale, passato, "CONFERMATA");
            UUID richiesta = presaInCaricoDiProva();

            String corpo = mvc.perform(get("/api/richieste/{id}/candidati", richiesta).with(come(banco, BANCO)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            List<String> account = JsonPath.read(corpo, "$[*].erogatore.accountId");
            List<String> ambiti = JsonPath.read(corpo, "$[*].erogatore.ambito");
            List<Integer> carichi = JsonPath.read(corpo, "$[*].caricoAppuntamenti");
            assertThat(account).containsExactly(esterno.toString(), operatore.toString(), optLocale.toString(),
                    centro.toString(), optLimitrofa.toString(), centro.toString());
            assertThat(ambiti).containsExactly(STESSO_COMUNE, STESSO_COMUNE, STESSO_COMUNE,
                    COMUNE_LIMITROFO, COMUNE_LIMITROFO, A_DISTANZA);
            assertThat(carichi).containsExactly(1, 2, 0, 0, 0, 0);
            assertThat(account).doesNotContain(titolare.toString(), optEstranea.toString());
        }

        @Test
        @DisplayName("Con misura non abilitata a distanza il Centro PICUS non è proposto come remoto")
        void remotoEscluso() throws Exception {
            dati.operatore("Centro PICUS lontano", "INTERNO_BANCO", DatiSintetici.COMUNE_LONTANO, true, m01);
            UUID richiesta = presaInCaricoDiProva();

            mvc.perform(get("/api/richieste/{id}/candidati", richiesta).with(come(banco, BANCO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[*].erogatore.ambito",
                            contains(STESSO_COMUNE, STESSO_COMUNE, COMUNE_LIMITROFO)));
        }

        @Test
        @DisplayName("Senza candidati la ricerca restituisce un elenco vuoto")
        void nessunCandidato() throws Exception {
            UUID richiesta = dati.richiesta(titolare, dati.misura("M12"), "napoli", "PRESA_IN_CARICO", INVIO);

            mvc.perform(get("/api/richieste/{id}/candidati", richiesta).with(come(banco, BANCO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("La ricerca non cambia lo stato e non crea contatti")
        void ricercaSenzaEffetti() throws Exception {
            UUID richiesta = presaInCaricoDiProva();

            mvc.perform(get("/api/richieste/{id}/candidati", richiesta).with(come(banco, BANCO)))
                    .andExpect(status().isOk());

            assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
            assertThat(dati.contatti(richiesta)).isZero();
            assertThat(dati.avvisi(richiesta)).isZero();
        }

        @Test
        @DisplayName("La ricerca su una richiesta Creata riceve 409, su una inesistente 404")
        void ricercaStatoIncompatibile() throws Exception {
            mvc.perform(get("/api/richieste/{id}/candidati", creata()).with(come(banco, BANCO)))
                    .andExpect(status().isConflict());
            mvc.perform(get("/api/richieste/{id}/candidati", UUID.randomUUID()).with(come(banco, BANCO)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Criterio 3: interpello con un contatto e un avviso per il solo destinatario")
    class Interpello {

        @Test
        @DisplayName("L'interpello crea un contatto in attesa e un avviso per il destinatario scelto")
        void contattoEAvviso() throws Exception {
            UUID richiesta = presaInCaricoDiProva();

            String corpo = interpella(banco, BANCO, richiesta, optLocale, STESSO_COMUNE)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.richiestaId").value(richiesta.toString()))
                    .andExpect(jsonPath("$.erogatoreId").value(optLocale.toString()))
                    .andExpect(jsonPath("$.ambito").value(STESSO_COMUNE))
                    .andExpect(jsonPath("$.esito").value("IN_ATTESA"))
                    .andReturn().getResponse().getContentAsString();
            String contatto = JsonPath.read(corpo, "$.id");

            assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
            assertThat(dati.contatti(richiesta)).isEqualTo(1);
            assertThat(dati.avvisi(richiesta)).isEqualTo(1);
            assertThat(dati.avvisiCoerentiConIlContatto(richiesta)).isEqualTo(1);

            mvc.perform(get("/api/avvisi").with(come(optLocale, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].tipo").value("RICHIESTA_DISPONIBILITA"))
                    .andExpect(jsonPath("$[0].richiestaId").value(richiesta.toString()))
                    .andExpect(jsonPath("$[0].contattoId").value(contatto));
            for (UUID altro : List.of(optLimitrofa, titolare, optEstranea)) {
                mvc.perform(get("/api/avvisi").with(come(altro, OPT)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
            }
            mvc.perform(get("/api/avvisi").with(come(operatore, OPERATORE)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Il Location della creazione porta al contatto consultabile dal Banco")
        void locationDelContatto() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            String corpo = interpella(banco, BANCO, richiesta, optLocale, STESSO_COMUNE)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String contatto = JsonPath.read(corpo, "$.id");

            interpella(banco, BANCO, richiesta, optLimitrofa, COMUNE_LIMITROFO).andExpect(status().isConflict());
            mvc.perform(get("/api/richieste/{id}/contatti/{c}", richiesta, contatto).with(come(banco, BANCO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.esito").value("IN_ATTESA"));
            mvc.perform(get("/api/richieste/{id}/contatti/{c}", UUID.randomUUID(), contatto).with(come(banco, BANCO)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Il Location punta a /api/richieste/{id}/contatti/{contattoId}")
        void intestazioneLocation() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            var risultato = interpella(banco, BANCO, richiesta, optLocale, STESSO_COMUNE)
                    .andExpect(status().isCreated()).andReturn();
            String contatto = JsonPath.read(risultato.getResponse().getContentAsString(), "$.id");

            assertThat(risultato.getResponse().getHeader("Location"))
                    .isEqualTo("/api/richieste/" + richiesta + "/contatti/" + contatto);
        }

        @Test
        @DisplayName("Solo l'account interpellato apre il contatto e vede i dati minimi, senza descrizione")
        void soloIlDestinatarioApre() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID contatto = contattoCreato(richiesta, optLocale, STESSO_COMUNE);

            mvc.perform(get("/api/contatti-disponibilita/{id}", contatto).with(come(optLocale, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contatto.id").value(contatto.toString()))
                    .andExpect(jsonPath("$.nomeMisura").value("Accoglienza e ascolto"))
                    .andExpect(jsonPath("$.comuneCodice").value("napoli"))
                    .andExpect(jsonPath("$.tipoDestinatario").value("PERSONA"))
                    .andExpect(jsonPath("$.description").doesNotExist())
                    .andExpect(jsonPath("$.titolareId").doesNotExist());
            mvc.perform(get("/api/contatti-disponibilita/{id}", contatto).with(come(optLimitrofa, OPT)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/contatti-disponibilita/{id}", contatto).with(come(titolare, OPT)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/contatti-disponibilita/{id}", contatto).with(come(operatore, OPERATORE)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/contatti-disponibilita/{id}", contatto).with(come(banco, BANCO)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Solo l'account interpellato può rispondere; i tentativi altrui lasciano il contatto in attesa")
        void soloIlDestinatarioRisponde() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID contatto = contattoCreato(richiesta, optLocale, STESSO_COMUNE);

            risposta(optLimitrofa, OPT, contatto, true).andExpect(status().isForbidden());
            risposta(operatore, OPERATORE, contatto, true).andExpect(status().isForbidden());
            risposta(banco, BANCO, contatto, true).andExpect(status().isForbidden());
            assertThat(dati.esitoContatto(contatto)).isEqualTo("IN_ATTESA");

            risposta(optLocale, OPT, contatto, true).andExpect(status().isOk())
                    .andExpect(jsonPath("$.esito").value("DISPONIBILE"));
            assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
        }

        @Test
        @DisplayName("Un operatore interpellato riceve l'avviso e apre il proprio contatto")
        void interpelloOperatore() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID contatto = contattoCreato(richiesta, operatore, STESSO_COMUNE);

            mvc.perform(get("/api/avvisi").with(come(operatore, OPERATORE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].contattoId", contains(contatto.toString())));
            mvc.perform(get("/api/contatti-disponibilita/{id}", contatto).with(come(operatore, OPERATORE)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Con un contatto in attesa un nuovo interpello riceve 409 senza contatti o avvisi in più")
        void unSoloContattoInAttesa() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            contattoCreato(richiesta, optLocale, STESSO_COMUNE);

            interpella(banco, BANCO, richiesta, operatore, STESSO_COMUNE).andExpect(status().isConflict());
            interpella(banco, BANCO, richiesta, optLocale, STESSO_COMUNE).andExpect(status().isConflict());

            assertThat(dati.contatti(richiesta)).isEqualTo(1);
            assertThat(dati.avvisi(richiesta)).isEqualTo(1);
        }

        @Test
        @DisplayName("Account non compatibile o ambito diverso dalla ricerca: 400 senza contatti")
        void opzioneNonCompatibile() throws Exception {
            UUID richiesta = presaInCaricoDiProva();

            interpella(banco, BANCO, richiesta, optEstranea, STESSO_COMUNE).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campi.contatto").exists());
            interpella(banco, BANCO, richiesta, optLocale, COMUNE_LIMITROFO).andExpect(status().isBadRequest());
            interpella(banco, BANCO, richiesta, optLimitrofa, STESSO_COMUNE).andExpect(status().isBadRequest());
            interpella(banco, BANCO, richiesta, banco, STESSO_COMUNE).andExpect(status().isBadRequest());
            interpella(banco, BANCO, richiesta, optLocale, A_DISTANZA).andExpect(status().isBadRequest());

            assertThat(dati.contatti(richiesta)).isZero();
            assertThat(dati.avvisi(richiesta)).isZero();
        }

        @ParameterizedTest(name = "corpo {0}")
        @ValueSource(strings = {
                "{\"erogatoreId\":\"{id}\",\"ambito\":\"STESSO_COMUNE\",\"nota\":\"extra\"}",
                "{\"erogatoreId\":\"{id}\"}",
                "{\"ambito\":\"STESSO_COMUNE\"}",
                "{\"erogatoreId\":\"non-un-uuid\",\"ambito\":\"STESSO_COMUNE\"}",
                "{\"erogatoreId\":\"{id}\",\"ambito\":\"ALTROVE\"}",
                "{\"erogatoreId\":\"{id}\",\"ambito\":null}",
                "{\"erogatoreId\":\"{id}\",\"erogatoreId\":\"{id}\",\"ambito\":\"STESSO_COMUNE\"}",
                "null",
                "{"})
        @DisplayName("Corpi con campi extra, mancanti, duplicati o malformati ricevono 400 senza contatti")
        void corpoNonValido(String modello) throws Exception {
            UUID richiesta = presaInCaricoDiProva();

            mvc.perform(post("/api/richieste/{id}/contatti", richiesta).with(come(banco, BANCO)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(modello.replace("{id}", optLocale.toString())))
                    .andExpect(status().isBadRequest());

            assertThat(dati.contatti(richiesta)).isZero();
            assertThat(dati.avvisi(richiesta)).isZero();
        }

        @Test
        @DisplayName("Interpello su richiesta Creata 409, su richiesta inesistente 404")
        void statoIncompatibile() throws Exception {
            UUID richiesta = creata();

            interpella(banco, BANCO, richiesta, optLocale, STESSO_COMUNE).andExpect(status().isConflict());
            interpella(banco, BANCO, UUID.randomUUID(), optLocale, STESSO_COMUNE).andExpect(status().isNotFound());

            assertThat(dati.contatti(richiesta)).isZero();
        }

        @Test
        @DisplayName("Remoto ammesso solo dopo il diniego di tutte le opzioni locali, poi assegnabile")
        void remotoDopoIDinieghiLocali() throws Exception {
            dati.abilitaADistanza(m01);
            UUID centro = dati.operatore("Centro PICUS lontano", "INTERNO_BANCO", DatiSintetici.COMUNE_LONTANO, true, m01);
            UUID richiesta = presaInCaricoDiProva();

            interpella(banco, BANCO, richiesta, centro, A_DISTANZA).andExpect(status().isConflict());
            declina(richiesta, optLocale, OPT, STESSO_COMUNE);
            declina(richiesta, operatore, OPERATORE, STESSO_COMUNE);
            interpella(banco, BANCO, richiesta, centro, A_DISTANZA).andExpect(status().isConflict());
            declina(richiesta, optLimitrofa, OPT, COMUNE_LIMITROFO);

            UUID remoto = contattoCreato(richiesta, centro, A_DISTANZA);
            risposta(centro, OPERATORE, remoto, true).andExpect(status().isOk());
            assegna(banco, BANCO, remoto).andExpect(status().isOk())
                    .andExpect(jsonPath("$.stato").value("IN_PROGRAMMAZIONE"));

            assertThat(dati.erogatoreAssegnato(richiesta)).isEqualTo(centro);
            assertThat(dati.ambitoAssegnato(richiesta)).isEqualTo(A_DISTANZA);
            assertThat(dati.contatti(richiesta)).isEqualTo(4);
        }

        @Test
        @DisplayName("Senza opzioni locali il Centro PICUS a distanza è subito interpellabile")
        void remotoSenzaLocali() throws Exception {
            long m04 = dati.misura("M04");
            dati.abilitaADistanza(m04);
            UUID centro = dati.operatore("Centro PICUS lontano", "INTERNO_BANCO", DatiSintetici.COMUNE_LONTANO, true, m04);
            UUID richiesta = dati.richiesta(titolare, m04, "napoli", "PRESA_IN_CARICO", INVIO);

            interpella(banco, BANCO, richiesta, centro, A_DISTANZA).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Con misura non abilitata a distanza l'opzione remota non è compatibile")
        void remotoNonAbilitato() throws Exception {
            long m04 = dati.misura("M04");
            UUID centro = dati.operatore("Centro PICUS lontano", "INTERNO_BANCO", DatiSintetici.COMUNE_LONTANO, true, m04);
            UUID richiesta = dati.richiesta(titolare, m04, "napoli", "PRESA_IN_CARICO", INVIO);

            interpella(banco, BANCO, richiesta, centro, A_DISTANZA).andExpect(status().isBadRequest());
            assertThat(dati.contatti(richiesta)).isZero();
        }
    }

    @Nested
    @DisplayName("Criterio 4: interpelli concorrenti via HTTP")
    class InterpelliConcorrenti {

        @Test
        @DisplayName("Interpelli simultanei: un solo 201, gli altri 409, un solo contatto e un solo avviso")
        void unSoloContattoSottoConcorrenza() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            int partecipanti = 6;
            var barriera = new CyclicBarrier(partecipanti);
            ExecutorService pool = Executors.newFixedThreadPool(partecipanti);
            List<Integer> codici = new ArrayList<>();
            try {
                List<Future<Integer>> futuri = new ArrayList<>();
                for (int i = 0; i < partecipanti; i++) {
                    UUID erogatore = i % 2 == 0 ? optLocale : operatore;
                    Callable<Integer> comando = () -> {
                        barriera.await(30, TimeUnit.SECONDS);
                        return interpella(banco, BANCO, richiesta, erogatore, STESSO_COMUNE)
                                .andReturn().getResponse().getStatus();
                    };
                    futuri.add(pool.submit(comando));
                }
                for (Future<Integer> futuro : futuri) {
                    codici.add(futuro.get(60, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(codici).containsOnly(201, 409);
            assertThat(codici).filteredOn(codice -> codice == 201).hasSize(1);
            assertThat(dati.contattiInAttesa(richiesta)).isEqualTo(1);
            assertThat(dati.contatti(richiesta)).isEqualTo(1);
            assertThat(dati.avvisi(richiesta)).isEqualTo(1);
            assertThat(dati.avvisiCoerentiConIlContatto(richiesta)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Criteri 5 e 6: integrazione con la risposta (#51) e assegnazione")
    class RispostaEAssegnazione {

        @Test
        @DisplayName("Un rifiuto è conservato, la richiesta resta Presa in carico e si contatta il candidato successivo")
        void rifiutoEProssimoCandidato() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID primo = contattoCreato(richiesta, operatore, STESSO_COMUNE);

            risposta(operatore, OPERATORE, primo, false).andExpect(status().isOk())
                    .andExpect(jsonPath("$.esito").value("NON_DISPONIBILE"));
            assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
            assegna(banco, BANCO, primo).andExpect(status().isConflict());

            UUID secondo = contattoCreato(richiesta, optLocale, STESSO_COMUNE);
            risposta(optLocale, OPT, secondo, true).andExpect(status().isOk());
            assertThat(dati.stato(richiesta)).as("la risposta positiva da sola non assegna")
                    .isEqualTo("PRESA_IN_CARICO");

            assegna(banco, BANCO, secondo).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(richiesta.toString()))
                    .andExpect(jsonPath("$.stato").value("IN_PROGRAMMAZIONE"));

            assertThat(dati.stato(richiesta)).isEqualTo("IN_PROGRAMMAZIONE");
            assertThat(dati.erogatoreAssegnato(richiesta)).isEqualTo(optLocale);
            assertThat(dati.ambitoAssegnato(richiesta)).isEqualTo(STESSO_COMUNE);
            mvc.perform(get("/api/richieste/{id}/contatti", richiesta).with(come(banco, BANCO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(primo.toString(), secondo.toString())))
                    .andExpect(jsonPath("$[*].esito", contains("NON_DISPONIBILE", "DISPONIBILE")));
        }

        @Test
        @DisplayName("Dopo l'assegnazione l'erogatore assegnato consulta la pratica senza descrizione, gli altri no")
        void visibilitaDopoAssegnazione() throws Exception {
            UUID richiesta = assegnataA(optLocale);

            mvc.perform(get("/api/richieste/{id}", richiesta).with(come(optLocale, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.erogatoreAssegnatoId").value(optLocale.toString()))
                    .andExpect(jsonPath("$.description").doesNotExist());
            mvc.perform(get("/api/richieste/{id}", richiesta).with(come(titolare, OPT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.richiesta.stato").value("IN_PROGRAMMAZIONE"));
            mvc.perform(get("/api/richieste/{id}", richiesta).with(come(optLimitrofa, OPT)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/richieste/{id}", richiesta).with(come(operatore, OPERATORE)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Un contatto ancora in attesa non consente l'assegnazione")
        void assegnazioneSenzaRisposta() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID contatto = contattoCreato(richiesta, optLocale, STESSO_COMUNE);

            assegna(banco, BANCO, contatto).andExpect(status().isConflict());

            assertNessunaAssegnazione(richiesta);
        }

        @Test
        @DisplayName("Una risposta positiva superata da un nuovo contatto della stessa opzione non consente l'assegnazione")
        void rispostaSuperata() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID positivo = contattoCreato(richiesta, optLocale, STESSO_COMUNE);
            risposta(optLocale, OPT, positivo, true).andExpect(status().isOk());
            UUID successivo = contattoCreato(richiesta, optLocale, STESSO_COMUNE);

            assegna(banco, BANCO, positivo).andExpect(status().isConflict());
            risposta(optLocale, OPT, successivo, false).andExpect(status().isOk());
            assegna(banco, BANCO, positivo).andExpect(status().isConflict());

            assertNessunaAssegnazione(richiesta);
        }

        @Test
        @DisplayName("Con un altro contatto in attesa la risposta positiva precedente non si assegna")
        void altroContattoInAttesa() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID positivo = contattoCreato(richiesta, optLocale, STESSO_COMUNE);
            risposta(optLocale, OPT, positivo, true).andExpect(status().isOk());
            contattoCreato(richiesta, operatore, STESSO_COMUNE);

            assegna(banco, BANCO, positivo).andExpect(status().isConflict());

            assertNessunaAssegnazione(richiesta);
        }

        @Test
        @DisplayName("Un account revocato dopo la risposta positiva non viene assegnato")
        void accountRevocato() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID positivo = contattoCreato(richiesta, optLocale, STESSO_COMUNE);
            risposta(optLocale, OPT, positivo, true).andExpect(status().isOk());
            dati.impostaStatoAccount(optLocale, "REVOCATO");

            assegna(banco, BANCO, positivo).andExpect(status().isConflict());

            assertNessunaAssegnazione(richiesta);
        }

        @Test
        @DisplayName("Un'offerta ritirata dopo la risposta positiva impedisce l'assegnazione")
        void offertaRitirata() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID positivo = contattoCreato(richiesta, optLocale, STESSO_COMUNE);
            risposta(optLocale, OPT, positivo, true).andExpect(status().isOk());
            dati.ritiraOfferta(optLocale, m01);

            assegna(banco, BANCO, positivo).andExpect(status().isConflict());

            assertNessunaAssegnazione(richiesta);
        }

        @Test
        @DisplayName("Una seconda assegnazione riceve 409 e non cambia l'assegnatario")
        void assegnazioneRipetuta() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID primo = contattoCreato(richiesta, optLocale, STESSO_COMUNE);
            risposta(optLocale, OPT, primo, true).andExpect(status().isOk());
            UUID secondo = contattoCreato(richiesta, operatore, STESSO_COMUNE);
            risposta(operatore, OPERATORE, secondo, true).andExpect(status().isOk());

            assegna(banco, BANCO, primo).andExpect(status().isOk());
            assegna(banco, BANCO, secondo).andExpect(status().isConflict());
            assegna(banco, BANCO, primo).andExpect(status().isConflict());

            assertThat(dati.stato(richiesta)).isEqualTo("IN_PROGRAMMAZIONE");
            assertThat(dati.erogatoreAssegnato(richiesta)).isEqualTo(optLocale);
        }

        @Test
        @DisplayName("Dopo l'assegnazione non si cercano né si interpellano altri erogatori")
        void nessunContattoDopoAssegnazione() throws Exception {
            UUID richiesta = assegnataA(optLocale);

            interpella(banco, BANCO, richiesta, operatore, STESSO_COMUNE).andExpect(status().isConflict());
            mvc.perform(get("/api/richieste/{id}/candidati", richiesta).with(come(banco, BANCO)))
                    .andExpect(status().isConflict());

            assertThat(dati.contatti(richiesta)).isEqualTo(1);
            assertThat(dati.avvisi(richiesta)).isEqualTo(1);
        }

        @Test
        @DisplayName("Assegnazione con corpo non vuoto 400, contatto inesistente 404, senza effetti")
        void assegnazioneNonValida() throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID positivo = contattoCreato(richiesta, optLocale, STESSO_COMUNE);
            risposta(optLocale, OPT, positivo, true).andExpect(status().isOk());

            mvc.perform(post("/api/contatti-disponibilita/{id}/assegnazione", positivo).with(come(banco, BANCO))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"erogatoreId\":\"" + operatore + "\"}"))
                    .andExpect(status().isBadRequest());
            assegna(banco, BANCO, UUID.randomUUID()).andExpect(status().isNotFound());

            assertNessunaAssegnazione(richiesta);
        }

        private UUID assegnataA(UUID erogatore) throws Exception {
            UUID richiesta = presaInCaricoDiProva();
            UUID contatto = contattoCreato(richiesta, erogatore, STESSO_COMUNE);
            risposta(erogatore, OPT, contatto, true).andExpect(status().isOk());
            assegna(banco, BANCO, contatto).andExpect(status().isOk());
            return richiesta;
        }

        private void assertNessunaAssegnazione(UUID richiesta) {
            assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
            assertThat(dati.erogatoreAssegnato(richiesta)).isNull();
            assertThat(dati.ambitoAssegnato(richiesta)).isNull();
        }
    }

    private UUID creata() {
        return dati.richiesta(titolare, m01, "napoli", "CREATA", INVIO);
    }

    private UUID presaInCaricoDiProva() {
        return dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIO);
    }

    private static RequestPostProcessor come(UUID account, String ruolo) {
        return user(account.toString()).roles(ruolo);
    }

    private ResultActions presaInCarico(UUID attore, String ruolo, UUID richiesta) throws Exception {
        return mvc.perform(post("/api/richieste/{id}/presa-in-carico", richiesta).with(come(attore, ruolo)).with(csrf()));
    }

    private ResultActions interpella(UUID attore, String ruolo, UUID richiesta, UUID erogatore, String ambito)
            throws Exception {
        return mvc.perform(post("/api/richieste/{id}/contatti", richiesta).with(come(attore, ruolo)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"erogatoreId\":\"" + erogatore + "\",\"ambito\":\"" + ambito + "\"}"));
    }

    private UUID contattoCreato(UUID richiesta, UUID erogatore, String ambito) throws Exception {
        String corpo = interpella(banco, BANCO, richiesta, erogatore, ambito)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(corpo, "$.id");
        return UUID.fromString(id);
    }

    private ResultActions risposta(UUID attore, String ruolo, UUID contatto, boolean disponibile) throws Exception {
        return mvc.perform(post("/api/contatti-disponibilita/{id}/risposta", contatto).with(come(attore, ruolo))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"disponibile\": " + disponibile + "}"));
    }

    private ResultActions assegna(UUID attore, String ruolo, UUID contatto) throws Exception {
        return mvc.perform(post("/api/contatti-disponibilita/{id}/assegnazione", contatto).with(come(attore, ruolo))
                .with(csrf()));
    }

    private void declina(UUID richiesta, UUID erogatore, String ruolo, String ambito) throws Exception {
        UUID contatto = contattoCreato(richiesta, erogatore, ambito);
        risposta(erogatore, ruolo, contatto, false).andExpect(status().isOk());
    }
}
