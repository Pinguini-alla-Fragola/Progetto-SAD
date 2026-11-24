package it.unina.picus.catalogo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;

/**
 * Test di integrazione della technical task #168 «Test di aggiornamento e titolarità dell'offerta
 * OPT», sotto-task della user story #47 «Dichiarazione delle misure erogate dalla OPT».
 *
 * <p>I blocchi {@code @Nested} «Criterio 1..5» seguono gli Acceptance Criteria della #47; il blocco
 * «Dopo il ritiro» copre il terzo criterio di completamento della #168 (ricerca, riferimenti storici
 * e richiesta di disponibilità sulla singola pratica). Il criterio della #168 che chiede la verifica
 * da interfaccia resta aperto: le schermate dell'offerta (#167) sono ancora disattivate.</p>
 *
 * <p>Gli endpoint sono quelli reali di {@link OffertaOptController} e del modulo Richieste, con la
 * catena di sicurezza attiva: l'utente è simulato con l'UUID del proprio account, che esiste nel
 * database con lo stesso ruolo, e ogni comando porta il token CSRF, così un {@code 403} dipende solo
 * dalle regole di accesso. Ogni esito HTTP è affiancato dalla lettura delle righe su PostgreSQL.</p>
 *
 * <p>Le misure {@code M01..M03} sono quelle delle migrazioni, con identificativi crescenti. Napoli è
 * il comune di tutti gli account, quindi le OPT con offerta sono candidate nello stesso comune.</p>
 */
@IntegrazionePostgresql
@DisplayName("#168 Aggiornamento e titolarità dell'offerta OPT (US #47) su HTTP e PostgreSQL")
class OffertaOptIntegrazioneTest {

    private static final String OFFERTA = "/api/catalogo/offerte-opt/{optId}";
    private static final String MISURA_OFFERTA = "/api/catalogo/offerte-opt/misure/{misuraId}";
    private static final String DETTAGLIO_RICHIESTA = "/api/richieste/{id}";
    private static final String CANDIDATI = "/api/richieste/{id}/candidati";
    private static final String CONTATTI = "/api/richieste/{id}/contatti";
    private static final String CONTATTO = "/api/contatti-disponibilita/{id}";
    private static final String RISPOSTA = "/api/contatti-disponibilita/{id}/risposta";

    private static final long MISURA_INESISTENTE = 999_999L;
    private static final String MISURA_NON_SELEZIONABILE = "Misura non selezionabile";
    private static final String MISURA_NON_ATTIVA = "Seleziona una misura attiva del catalogo";
    private static final Instant INVIATA_IL = Instant.parse("2026-11-02T09:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private long m01;
    private long m02;
    private long m03;
    private UUID banco;
    private UUID optA;
    private UUID optB;
    private UUID titolare;
    private UUID operatore;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        m01 = dati.misura("M01");
        m02 = dati.misura("M02");
        m03 = dati.misura("M03");
        banco = dati.banco("Banco di prova offerta");
        optA = dati.opt("OPT alfa", "napoli");
        optB = dati.opt("OPT Beta", "napoli");
        titolare = dati.opt("OPT titolare delle richieste", "napoli");
        // Operatore senza misure: non entra mai fra i candidati, serve solo come ruolo estraneo.
        operatore = dati.operatore("Operatore di prova", "INTERNO_BANCO", "napoli", false);
    }

    @Nested
    @DisplayName("Criterio 1: la OPT salva misure attive e il Banco consulta l'offerta aggiornata")
    class DichiarazioneEAggiornamento {

        @Test
        @DisplayName("Le misure salvate appartengono alla OPT autenticata e sono consultabili da Banco e titolare")
        void salvaEConsultaOfferta() throws Exception {
            dichiara(optA, m03, true).andExpect(status().isNoContent()).andExpect(content().string(""));
            dichiara(optA, m01, false).andExpect(status().isNoContent());

            assertThat(offertaVistaDa(banco, "BANCO", optA)).containsExactly(m01 + ":false", m03 + ":true");
            assertThat(offertaVistaDa(optA, "OPT", optA)).containsExactly(m01 + ":false", m03 + ":true");
            assertThat(righe(optA)).containsExactly(m01 + ":false:true", m03 + ":true:true");
            assertThat(righe(optB)).isEmpty();
        }

        @Test
        @DisplayName("Una nuova dichiarazione della stessa misura aggiorna la riga esistente senza duplicarla")
        void aggiornaSenzaDuplicare() throws Exception {
            dichiara(optA, m01, false).andExpect(status().isNoContent());
            dichiara(optA, m01, true).andExpect(status().isNoContent());
            assertThat(offertaVistaDa(banco, "BANCO", optA)).containsExactly(m01 + ":true");
            assertThat(righe(optA)).containsExactly(m01 + ":true:true");

            dichiara(optA, m01, false).andExpect(status().isNoContent());
            assertThat(offertaVistaDa(banco, "BANCO", optA)).containsExactly(m01 + ":false");
            assertThat(righe(optA)).containsExactly(m01 + ":false:true");
        }

        @Test
        @DisplayName("Una misura ritirata e dichiarata di nuovo torna corrente con il nuovo valore, sulla stessa riga")
        void ridichiaraDopoIlRitiro() throws Exception {
            dichiara(optA, m01, true).andExpect(status().isNoContent());
            ritira(optA, m01).andExpect(status().isNoContent());
            dichiara(optA, m01, false).andExpect(status().isNoContent());

            assertThat(offertaVistaDa(banco, "BANCO", optA)).containsExactly(m01 + ":false");
            assertThat(righe(optA)).containsExactly(m01 + ":false:true");
        }

        @ParameterizedTest(name = "corpo [{0}]")
        @ValueSource(strings = {
                "",
                "{}",
                "{\"tramiteProfessionista\":\"true\"}",
                "{\"tramiteProfessionista\":null}",
                "{\"tramiteProfessionista\":1}",
                "{\"tramiteProfessionista\":true,\"altro\":1}",
                "{\"tramiteProfessionista\":true,\"tramiteProfessionista\":false}",
                "{\"tramiteProfessionista\":true} {}",
                "{\"tramiteProfessionista\":",
                "[true]",
                "true"})
        @DisplayName("Un corpo diverso dal solo booleano tramiteProfessionista è rifiutato e non salva nulla")
        void rifiutaCorpoNonValido(String corpo) throws Exception {
            dichiaraConCorpo(optA, m01, corpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.campi.tramiteProfessionista").exists())
                    .andExpect(jsonPath("$.campi.misuraId").doesNotExist());

            assertThat(righe(optA)).isEmpty();
        }

        @Test
        @DisplayName("Un optId nel corpo non sceglie la OPT titolare: la richiesta è rifiutata")
        void ignoraTitolareDalCorpo() throws Exception {
            dichiaraConCorpo(optA, m01, "{\"tramiteProfessionista\":true,\"optId\":\"" + optB + "\"}")
                    .andExpect(status().isBadRequest());

            assertThat(righe(optA)).isEmpty();
            assertThat(righe(optB)).isEmpty();
        }

        @Test
        @DisplayName("Il Banco riceve 404 per un account inesistente o che non è una OPT")
        void consultaOptInesistente() throws Exception {
            for (UUID nonOpt : List.of(UUID.randomUUID(), banco, operatore)) {
                mvc.perform(get(OFFERTA, nonOpt).with(user(banco.toString()).roles("BANCO")))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.codice").value("NOT_FOUND"));
            }
        }

        @Test
        @DisplayName("Una OPT senza dichiarazioni ha un'offerta vuota, non un errore")
        void offertaVuota() throws Exception {
            assertThat(offertaVistaDa(banco, "BANCO", optB)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Criterio 2: una misura fuori dal catalogo attivo è rifiutata con l'errore indicato")
    class MisuraNonAttiva {

        @Test
        @DisplayName("Una misura disattivata non entra nell'offerta")
        void rifiutaMisuraDisattivata() throws Exception {
            disattiva(m02);

            dichiara(optA, m02, false)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.messaggio").value(MISURA_NON_SELEZIONABILE))
                    .andExpect(jsonPath("$.campi.misuraId").value(MISURA_NON_ATTIVA));

            assertThat(righe(optA)).isEmpty();
            assertThat(offertaVistaDa(optA, "OPT", optA)).isEmpty();
        }

        @Test
        @DisplayName("Una misura inesistente non entra nell'offerta")
        void rifiutaMisuraInesistente() throws Exception {
            dichiara(optA, MISURA_INESISTENTE, false)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messaggio").value(MISURA_NON_SELEZIONABILE))
                    .andExpect(jsonPath("$.campi.misuraId").value(MISURA_NON_ATTIVA));

            assertThat(righe(optA)).isEmpty();
        }

        @Test
        @DisplayName("Dopo la disattivazione la dichiarazione esistente non si può più modificare e resta invariata")
        void nonAggiornaMisuraDisattivata() throws Exception {
            dichiara(optA, m01, true).andExpect(status().isNoContent());
            disattiva(m01);

            dichiara(optA, m01, false).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campi.misuraId").value(MISURA_NON_ATTIVA));

            assertThat(righe(optA)).containsExactly(m01 + ":true:true");
        }
    }

    @Nested
    @DisplayName("Criterio 3: la misura ritirata esce dall'offerta corrente senza cancellare i dati")
    class Ritiro {

        @Test
        @DisplayName("Il ritiro toglie solo quella misura dall'offerta e conserva la riga con i suoi dati")
        void ritiraConservandoLaRiga() throws Exception {
            dichiara(optA, m01, true).andExpect(status().isNoContent());
            dichiara(optA, m02, false).andExpect(status().isNoContent());

            ritira(optA, m01).andExpect(status().isNoContent()).andExpect(content().string(""));

            assertThat(offertaVistaDa(optA, "OPT", optA)).containsExactly(m02 + ":false");
            assertThat(offertaVistaDa(banco, "BANCO", optA)).containsExactly(m02 + ":false");
            assertThat(righe(optA)).containsExactly(m01 + ":true:false", m02 + ":false:true");
        }

        @Test
        @DisplayName("Ritirare una misura già ritirata o mai dichiarata dà 404 e non cambia nulla")
        void ritiroSenzaOffertaCorrente() throws Exception {
            dichiara(optA, m01, true).andExpect(status().isNoContent());
            ritira(optA, m01).andExpect(status().isNoContent());

            ritira(optA, m01).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.messaggio").value("Offerta corrente non trovata"));
            ritira(optA, m03).andExpect(status().isNotFound());

            assertThat(righe(optA)).containsExactly(m01 + ":true:false");
        }

        @Test
        @DisplayName("La disattivazione della misura non cancella la dichiarazione, che si può ancora ritirare")
        void ritiroDiMisuraDisattivata() throws Exception {
            dichiara(optA, m01, true).andExpect(status().isNoContent());
            disattiva(m01);

            assertThat(righe(optA)).containsExactly(m01 + ":true:true");
            assertThat(offertaVistaDa(optA, "OPT", optA)).containsExactly(m01 + ":true");

            ritira(optA, m01).andExpect(status().isNoContent());

            assertThat(offertaVistaDa(optA, "OPT", optA)).isEmpty();
            assertThat(righe(optA)).containsExactly(m01 + ":true:false");
        }
    }

    @Nested
    @DisplayName("Criterio 4: il Banco vede l'offerta fra i compatibili senza disponibilità preventive")
    class OffertaNellaRicerca {

        @Test
        @DisplayName("La OPT con offerta è candidata con i dati usati dal Banco e senza giorni o fasce orarie")
        void candidatoSenzaDisponibilitaPreventive() throws Exception {
            dichiara(optA, m01, true).andExpect(status().isNoContent());
            UUID richiesta = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIATA_IL);

            MvcResult esito = mvc.perform(get(CANDIDATI, richiesta).with(user(banco.toString()).roles("BANCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].erogatore.accountId").value(optA.toString()))
                    .andExpect(jsonPath("$[0].erogatore.nomeVisualizzato").value("OPT alfa"))
                    .andExpect(jsonPath("$[0].erogatore.tipo").value("OPT"))
                    .andExpect(jsonPath("$[0].erogatore.comuneCodice").value("napoli"))
                    .andExpect(jsonPath("$[0].erogatore.ambito").value("STESSO_COMUNE"))
                    .andExpect(jsonPath("$[0].caricoAppuntamenti").value(0))
                    .andReturn();

            assertThat(candidati(esito)).containsExactly(optA);
            assertThat(chiaviDiDisponibilitaPreventiva(JsonPath.read(testo(esito), "$"))).isEmpty();

            MvcResult offerta = mvc.perform(get(OFFERTA, optA).with(user(banco.toString()).roles("BANCO")))
                    .andExpect(status().isOk()).andReturn();
            assertThat(offertaVistaDa(banco, "BANCO", optA)).containsExactly(m01 + ":true");
            assertThat(chiaviDiDisponibilitaPreventiva(JsonPath.read(testo(offerta), "$"))).isEmpty();
        }

        @Test
        @DisplayName("Dichiarare l'offerta non crea contatti: la disponibilità si chiede sulla singola richiesta")
        void dichiarazioneSenzaContatti() throws Exception {
            UUID richiesta = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIATA_IL);

            dichiara(optA, m01, false).andExpect(status().isNoContent());

            assertThat(dati.contatti(richiesta)).isZero();
            assertThat(dati.avvisi(richiesta)).isZero();
            assertThat(dati.stato(richiesta)).isEqualTo("PRESA_IN_CARICO");
        }
    }

    @Nested
    @DisplayName("Criterio 5: un account estraneo non modifica l'offerta e i dati restano invariati")
    class AccountEstraneo {

        @BeforeEach
        void offertaDiA() throws Exception {
            dichiara(optA, m01, true).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Un'altra OPT non consulta l'offerta di A")
        void altraOptNonConsulta() throws Exception {
            mvc.perform(get(OFFERTA, optA).with(user(optB.toString()).roles("OPT")))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("Un'altra OPT che ritira la stessa misura non tocca la dichiarazione di A")
        void altraOptNonRitira() throws Exception {
            ritira(optB, m01).andExpect(status().isNotFound());

            assertThat(righe(optA)).containsExactly(m01 + ":true:true");
            assertThat(righe(optB)).isEmpty();
        }

        @Test
        @DisplayName("Un'altra OPT che dichiara la stessa misura scrive solo la propria riga")
        void altraOptScriveSoloLaPropria() throws Exception {
            dichiara(optB, m01, false).andExpect(status().isNoContent());

            assertThat(righe(optA)).containsExactly(m01 + ":true:true");
            assertThat(righe(optB)).containsExactly(m01 + ":false:true");
            assertThat(offertaVistaDa(banco, "BANCO", optA)).containsExactly(m01 + ":true");
        }

        @Test
        @DisplayName("Banco e Operatore non dichiarano né ritirano; l'Operatore non consulta")
        void ruoliNonAutorizzati() throws Exception {
            for (var attore : List.of(new Attore(banco, "BANCO"), new Attore(operatore, "OPERATORE"))) {
                mvc.perform(put(MISURA_OFFERTA, m01).with(user(attore.id().toString()).roles(attore.ruolo()))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tramiteProfessionista\":false}"))
                        .andExpect(status().isForbidden());
                mvc.perform(delete(MISURA_OFFERTA, m01).with(user(attore.id().toString()).roles(attore.ruolo()))
                        .with(csrf()))
                        .andExpect(status().isForbidden());
            }
            mvc.perform(get(OFFERTA, optA).with(user(operatore.toString()).roles("OPERATORE")))
                    .andExpect(status().isForbidden());

            assertThat(righe(optA)).containsExactly(m01 + ":true:true");
            assertThat(jdbc.queryForObject("select count(*) from catalogo_offerte_opt", Integer.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("Senza sessione le rotte rispondono 401; senza token CSRF i comandi rispondono 403")
        void senzaSessioneOToken() throws Exception {
            mvc.perform(get(OFFERTA, optA)).andExpect(status().isUnauthorized());
            mvc.perform(put(MISURA_OFFERTA, m01).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tramiteProfessionista\":false}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(MISURA_OFFERTA, m01).with(csrf())).andExpect(status().isUnauthorized());

            mvc.perform(put(MISURA_OFFERTA, m01).with(user(optA.toString()).roles("OPT"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"tramiteProfessionista\":false}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(MISURA_OFFERTA, m01).with(user(optA.toString()).roles("OPT")))
                    .andExpect(status().isForbidden());

            assertThat(righe(optA)).containsExactly(m01 + ":true:true");
        }
    }

    @Nested
    @DisplayName("Criterio 3 della #168: dopo il ritiro la ricerca si aggiorna e lo storico resta consultabile")
    class DopoIlRitiro {

        @Test
        @DisplayName("La OPT esce dalla ricerca solo per la misura ritirata")
        void esceDallaRicercaSoloPerQuellaMisura() throws Exception {
            dichiara(optA, m01, false).andExpect(status().isNoContent());
            dichiara(optA, m02, false).andExpect(status().isNoContent());
            dichiara(optB, m01, false).andExpect(status().isNoContent());
            UUID suM01 = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIATA_IL);
            UUID suM02 = dati.richiesta(titolare, m02, "napoli", "PRESA_IN_CARICO", INVIATA_IL.plusSeconds(60));
            assertThat(candidatiPer(suM01)).containsExactlyInAnyOrder(optA, optB);

            ritira(optA, m01).andExpect(status().isNoContent());

            assertThat(candidatiPer(suM01)).containsExactly(optB);
            assertThat(candidatiPer(suM02)).containsExactly(optA);
        }

        @Test
        @DisplayName("Una pratica già confermata con la OPT resta consultabile e invariata")
        void riferimentiStoriciConsultabili() throws Exception {
            dichiara(optA, m01, false).andExpect(status().isNoContent());
            UUID confermata = dati.appuntamento(titolare, m01, optA,
                    Instant.now().plus(Duration.ofDays(7)), "CONFERMATA");

            ritira(optA, m01).andExpect(status().isNoContent());

            for (var attore : List.of(new Attore(banco, "BANCO"), new Attore(optA, "OPT"))) {
                mvc.perform(get(DETTAGLIO_RICHIESTA, confermata)
                        .with(user(attore.id().toString()).roles(attore.ruolo())))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.richiesta.stato").value("CONFERMATA"))
                        .andExpect(jsonPath("$.richiesta.misuraId").value(m01))
                        .andExpect(jsonPath("$.erogatoreAssegnatoId").value(optA.toString()));
            }
            assertThat(dati.stato(confermata)).isEqualTo("CONFERMATA");
            assertThat(dati.erogatoreAssegnato(confermata)).isEqualTo(optA);
            assertThat(righe(optA)).containsExactly(m01 + ":false:false");
        }

        @Test
        @DisplayName("Il contatto inviato prima del ritiro si consulta e riceve risposta; la ricerca prosegue")
        void richiestaDiDisponibilitaDopoIlRitiro() throws Exception {
            dichiara(optA, m01, false).andExpect(status().isNoContent());
            dichiara(optB, m01, false).andExpect(status().isNoContent());
            UUID richiesta = dati.richiesta(titolare, m01, "napoli", "PRESA_IN_CARICO", INVIATA_IL);
            UUID contatto = interpella(richiesta, optA).andExpect(status().isCreated()).idCreato();

            ritira(optA, m01).andExpect(status().isNoContent());

            mvc.perform(get(CONTATTO, contatto).with(user(optA.toString()).roles("OPT")))
                    .andExpect(status().isOk());
            mvc.perform(post(RISPOSTA, contatto).with(user(optA.toString()).roles("OPT")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"disponibile\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.esito").value("DISPONIBILE"));
            assertThat(dati.esitoContatto(contatto)).isEqualTo("DISPONIBILE");

            mvc.perform(get(CONTATTI, richiesta).with(user(banco.toString()).roles("BANCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(contatto.toString()))
                    .andExpect(jsonPath("$[0].erogatoreId").value(optA.toString()))
                    .andExpect(jsonPath("$[0].esito").value("DISPONIBILE"));

            // L'esito dell'assegnazione a una OPT che ha ritirato la misura dipende dalla Q-20:
            // sarà fissato in un test separato dopo la decisione, non qui.

            // La ricerca prosegue con il prossimo candidato, che offre ancora la misura.
            assertThat(candidatiPer(richiesta)).containsExactly(optB);
            interpella(richiesta, optB).andExpect(status().isCreated());
            assertThat(dati.contatti(richiesta)).isEqualTo(2);
            assertThat(dati.contattiInAttesa(richiesta)).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Supporto
    // ---------------------------------------------------------------------------------------------

    private record Attore(UUID id, String ruolo) {}

    /** Esito di un {@code POST} che crea un contatto, con lettura dell'identificativo creato. */
    private record Creazione(ResultActions esito) {
        Creazione andExpect(org.springframework.test.web.servlet.ResultMatcher atteso) throws Exception {
            esito.andExpect(atteso);
            return this;
        }

        UUID idCreato() throws Exception {
            return UUID.fromString(JsonPath.read(testo(esito.andReturn()), "$.id"));
        }
    }

    private ResultActions dichiara(UUID opt, long misura, boolean tramiteProfessionista) throws Exception {
        return dichiaraConCorpo(opt, misura, "{\"tramiteProfessionista\":" + tramiteProfessionista + "}");
    }

    private ResultActions dichiaraConCorpo(UUID opt, long misura, String corpo) throws Exception {
        return mvc.perform(put(MISURA_OFFERTA, misura).with(user(opt.toString()).roles("OPT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions ritira(UUID opt, long misura) throws Exception {
        return mvc.perform(delete(MISURA_OFFERTA, misura).with(user(opt.toString()).roles("OPT")).with(csrf()));
    }

    private Creazione interpella(UUID richiesta, UUID erogatore) throws Exception {
        return new Creazione(mvc.perform(post(CONTATTI, richiesta).with(user(banco.toString()).roles("BANCO"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"erogatoreId\":\"" + erogatore + "\",\"ambito\":\"STESSO_COMUNE\"}")));
    }

    private void disattiva(long misura) {
        jdbc.update("update catalogo_misure set attiva = false where id = ?", misura);
    }

    /** Offerta restituita dall'API come sequenza {@code misuraId:tramiteProfessionista}, nell'ordine ricevuto. */
    private List<String> offertaVistaDa(UUID attore, String ruolo, UUID opt) throws Exception {
        MvcResult esito = mvc.perform(get(OFFERTA, opt).with(user(attore.toString()).roles(ruolo)))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> voci = JsonPath.read(testo(esito), "$");
        return voci.stream()
                .map(v -> ((Number) v.get("misuraId")).longValue() + ":" + v.get("tramiteProfessionista"))
                .toList();
    }

    /** Righe persistite della OPT come {@code misura:tramiteProfessionista:attiva}, ordinate per misura. */
    private List<String> righe(UUID opt) {
        return jdbc.query("""
                select misura_id, tramite_professionista, attiva from catalogo_offerte_opt
                where opt_id = ? order by misura_id
                """, (rs, n) -> rs.getLong(1) + ":" + rs.getBoolean(2) + ":" + rs.getBoolean(3), opt);
    }

    private List<UUID> candidatiPer(UUID richiesta) throws Exception {
        return candidati(mvc.perform(get(CANDIDATI, richiesta).with(user(banco.toString()).roles("BANCO")))
                .andExpect(status().isOk()).andReturn());
    }

    private static List<UUID> candidati(MvcResult esito) throws Exception {
        List<String> id = JsonPath.read(testo(esito), "$[*].erogatore.accountId");
        return id.stream().map(UUID::fromString).toList();
    }

    /**
     * Chiavi del JSON, a ogni livello, che descriverebbero una disponibilità preventiva (giorni,
     * fasce orarie, calendari). Le altre chiavi restano libere, così il test non fissa la forma dei DTO.
     */
    private static List<String> chiaviDiDisponibilitaPreventiva(Object json) {
        var trovate = new ArrayList<String>();
        raccogliChiavi(json, trovate);
        return trovate.stream()
                .filter(chiave -> chiave.matches("(?i).*(disponib|giorn|fasc|orari|calendar|slot|agenda).*"))
                .toList();
    }

    private static void raccogliChiavi(Object nodo, List<String> chiavi) {
        if (nodo instanceof Map<?, ?> mappa) {
            mappa.forEach((chiave, valore) -> {
                chiavi.add(String.valueOf(chiave));
                raccogliChiavi(valore, chiavi);
            });
        } else if (nodo instanceof List<?> elenco) {
            elenco.forEach(elemento -> raccogliChiavi(elemento, chiavi));
        }
    }

    private static String testo(MvcResult esito) throws Exception {
        return esito.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
