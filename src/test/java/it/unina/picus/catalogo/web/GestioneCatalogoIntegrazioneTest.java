package it.unina.picus.catalogo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test di integrazione della technical task #165 «Test di gestione del catalogo e regressioni»,
 * sotto-task della user story #46 «Gestione del catalogo delle misure».
 *
 * <p>I blocchi {@code @Nested} «Criterio 1..5» seguono gli Acceptance Criteria della #46. Il blocco
 * «Regressioni» copre la parte della #165 sulla ricerca esistente: elenchi e dettaglio delle richieste
 * già registrate, consultazione della misura per identificativo e ritento idempotente di un invio
 * concluso prima della disattivazione (#164). La gestione da interfaccia del Banco (#186) non è ancora
 * implementata: qui si provano gli endpoint di {@link CatalogoController}, la vista di nuova richiesta
 * della OPT e gli endpoint di invio e di offerta che usano il catalogo attivo.</p>
 *
 * <p>Gli endpoint sono quelli reali, con la catena di sicurezza attiva: l'utente è simulato con l'UUID
 * del proprio account, che esiste nel database con lo stesso ruolo, e ogni comando porta il token CSRF,
 * così un {@code 403} dipende solo dalle regole di accesso. Ogni esito HTTP è affiancato dalla lettura
 * delle righe su PostgreSQL.</p>
 *
 * <p>{@link DatiSintetici#svuota()} riattiva le misure ma non cancella quelle create dai test: per non
 * lasciare misure estranee alle altre classi, prima e dopo ogni test sono eliminate tutte le misure
 * diverse dalle dodici delle migrazioni. Le misure iniziali non vengono mai modificate.</p>
 */
@IntegrazionePostgresql
@DisplayName("#165 Gestione del catalogo e regressioni (US #46) su HTTP e PostgreSQL")
class GestioneCatalogoIntegrazioneTest {

    private static final String MISURE = "/api/catalogo/misure";
    private static final String TUTTE = "/api/catalogo/misure/tutte";
    private static final String MISURA = "/api/catalogo/misure/{id}";
    private static final String DISATTIVAZIONE = "/api/catalogo/misure/{id}/disattivazione";
    private static final String NUOVA_RICHIESTA = "/richieste/nuova";
    private static final String RICHIESTE = "/api/richieste";
    private static final String RICHIESTE_OPT = "/api/richieste/mie";
    private static final String RICHIESTA = "/api/richieste/{id}";
    private static final String OFFERTA_MISURA = "/api/catalogo/offerte-opt/misure/{misuraId}";

    private static final List<String> CODICI_INIZIALI = List.of(
            "M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10", "M11", "M12");
    private static final long MISURA_INESISTENTE = 999_999L;
    private static final String MISURA_NON_DISPONIBILE = "Seleziona una misura disponibile";
    private static final String MISURA_NON_SELEZIONABILE = "Misura non selezionabile";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private UUID banco;
    private UUID opt;
    private UUID operatore;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        ripristinaCatalogo();
        banco = dati.banco("Banco di prova catalogo");
        opt = dati.opt("OPT di prova catalogo", "napoli");
        operatore = dati.operatore("Operatore di prova catalogo", "INTERNO_BANCO", "napoli", false);
    }

    @AfterEach
    void ripulisci() {
        ripristinaCatalogo();
    }

    @Nested
    @DisplayName("Criterio 1: una nuova misura valida diventa selezionabile per richieste e offerte")
    class Creazione {

        @Test
        @DisplayName("Il Banco crea la misura: 201, Location e dati normalizzati uguali alla riga salvata")
        void creaMisuraValida() throws Exception {
            MvcResult esito = crea(banco, "BANCO", corpo("  T01  ", "  Misura di prova  ",
                    "  Descrizione di prova  ", true))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dati.codice").value("T01"))
                    .andExpect(jsonPath("$.dati.nome").value("Misura di prova"))
                    .andExpect(jsonPath("$.dati.descrizione").value("Descrizione di prova"))
                    .andExpect(jsonPath("$.dati.erogabileADistanza").value(true))
                    .andExpect(jsonPath("$.attiva").value(true))
                    .andReturn();
            long id = idMisura(esito);

            assertThat(esito.getResponse().getHeader("Location")).isEqualTo("/api/catalogo/misure/" + id);
            assertThat(rigaMisura(id)).isEqualTo("T01|Misura di prova|Descrizione di prova|true|true");
            assertThat(idPerCodice("T01")).isEqualTo(id);
            assertThat(codiciSalvati()).containsExactlyElementsOf(conIniziali("T01"));
        }

        @Test
        @DisplayName("Senza erogabileADistanza la nuova misura non è erogabile a distanza")
        void erogabilitaPredefinita() throws Exception {
            long id = idMisura(crea(banco, "BANCO",
                    "{\"codice\":\"T01\",\"nome\":\"Misura di prova\",\"descrizione\":\"Descrizione\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dati.erogabileADistanza").value(false))
                    .andReturn());

            assertThat(rigaMisura(id)).isEqualTo("T01|Misura di prova|Descrizione|false|true");
        }

        @Test
        @DisplayName("La misura compare nel catalogo attivo, ordinata per codice, per OPT, Operatore e Banco")
        void compareNelCatalogoAttivo() throws Exception {
            // Inserite in ordine inverso: l'ordine della risposta deve dipendere dal codice, non dall'id.
            long t02 = creaMisura("T02", "Seconda misura di prova", "Descrizione due", false);
            long t01 = creaMisura("T01", "Prima misura di prova", "Descrizione uno", false);
            assertThat(t02).isLessThan(t01);

            for (var attore : List.of(Map.entry(opt, "OPT"), Map.entry(operatore, "OPERATORE"),
                    Map.entry(banco, "BANCO"))) {
                assertThat(codiciAttivi(attore.getKey(), attore.getValue()))
                        .containsExactlyElementsOf(conIniziali("T01", "T02"));
            }
            assertThat(codiciTutte()).containsExactlyElementsOf(conIniziali("T01", "T02"));
        }

        @Test
        @DisplayName("La misura compare fra quelle della vista di nuova richiesta della OPT")
        void compareNellaNuovaRichiesta() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);

            List<Misura> selezionabili = misureNuovaRichiesta();

            assertThat(selezionabili).extracting(Misura::id).contains(id);
            assertThat(selezionabili).filteredOn(m -> m.id() == id)
                    .singleElement()
                    .satisfies(m -> {
                        assertThat(m.dati().codice()).isEqualTo("T01");
                        assertThat(m.dati().nome()).isEqualTo("Misura di prova");
                    });
            assertThat(selezionabili).extracting(m -> m.dati().codice())
                    .containsExactlyElementsOf(conIniziali("T01"));
        }

        @Test
        @DisplayName("La OPT invia una nuova richiesta con la misura appena creata")
        void inviaRichiestaConNuovaMisura() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);

            UUID richiesta = idRichiesta(inviaRichiesta(id)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.misuraId").value(id))
                    .andExpect(jsonPath("$.nomeMisura").value("Misura di prova"))
                    .andReturn());

            assertThat(misuraDellaRichiesta(richiesta)).isEqualTo(id);
        }

        @Test
        @DisplayName("La OPT dichiara nella propria offerta la misura appena creata")
        void dichiaraOffertaConNuovaMisura() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);

            dichiaraOfferta(id).andExpect(status().isNoContent());

            assertThat(righeOfferta()).containsExactly(id + ":true:true");
        }
    }

    @Nested
    @DisplayName("Criterio 2: la modifica è visibile subito, senza rilascio né riavvio")
    class Modifica {

        @Test
        @DisplayName("Il Banco modifica i dati: risposta, riga e consultazione mostrano i nuovi valori con lo stesso id")
        void modificaDati() throws Exception {
            long id = creaMisura("T01", "Nome iniziale", "Descrizione iniziale", false);

            aggiorna(banco, "BANCO", id, corpo("  T09  ", "  Nome corretto  ", "Riga uno\nRiga due", true, true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.dati.codice").value("T09"))
                    .andExpect(jsonPath("$.dati.nome").value("Nome corretto"))
                    .andExpect(jsonPath("$.dati.descrizione").value("Riga uno\nRiga due"))
                    .andExpect(jsonPath("$.dati.erogabileADistanza").value(true))
                    .andExpect(jsonPath("$.attiva").value(true));

            assertThat(rigaMisura(id)).isEqualTo("T09|Nome corretto|Riga uno\nRiga due|true|true");
            assertThat(codiciSalvati()).containsExactlyElementsOf(conIniziali("T09"));
            mvc.perform(get(MISURA, id).with(come(opt, "OPT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dati.codice").value("T09"))
                    .andExpect(jsonPath("$.dati.nome").value("Nome corretto"));
        }

        @Test
        @DisplayName("Il catalogo attivo e la vista di nuova richiesta mostrano subito il nuovo nome")
        void nuovoNomeNelleSelezioni() throws Exception {
            long id = creaMisura("T01", "Nome iniziale", "Descrizione", false);
            assertThat(nomeNelCatalogoAttivo(id)).isEqualTo("Nome iniziale");

            aggiorna(banco, "BANCO", id, corpo("T01", "Nome corretto", "Descrizione", false, true))
                    .andExpect(status().isOk());

            assertThat(nomeNelCatalogoAttivo(id)).isEqualTo("Nome corretto");
            assertThat(misureNuovaRichiesta()).filteredOn(m -> m.id() == id)
                    .singleElement()
                    .satisfies(m -> assertThat(m.dati().nome()).isEqualTo("Nome corretto"));
        }

        @Test
        @DisplayName("Una richiesta già registrata resta sulla stessa misura e ne mostra il nome aggiornato")
        void richiestaPrecedenteVedeIlNuovoNome() throws Exception {
            long id = creaMisura("T01", "Nome iniziale", "Descrizione", false);
            UUID richiesta = idRichiesta(inviaRichiesta(id).andExpect(status().isCreated()).andReturn());

            aggiorna(banco, "BANCO", id, corpo("T01", "Nome corretto", "Descrizione", false, true))
                    .andExpect(status().isOk());

            assertThat(misuraDellaRichiesta(richiesta)).isEqualTo(id);
            mvc.perform(get(RICHIESTA, richiesta).with(come(opt, "OPT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.richiesta.misuraId").value(id))
                    .andExpect(jsonPath("$.richiesta.nomeMisura").value("Nome corretto"));
            assertThat(nomeMisuraNellElenco(RICHIESTE_OPT, opt, "OPT", richiesta)).isEqualTo("Nome corretto");
            assertThat(nomeMisuraNellElenco(RICHIESTE, banco, "BANCO", richiesta)).isEqualTo("Nome corretto");
        }

        @Test
        @DisplayName("Il codice si può cambiare solo di maiuscole sulla stessa misura")
        void cambioMaiuscoleDelProprioCodice() throws Exception {
            long id = creaMisura("Tx1", "Nome", "Descrizione", false);

            aggiorna(banco, "BANCO", id, corpo("TX1", "Nome", "Descrizione", false, true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dati.codice").value("TX1"));

            assertThat(rigaMisura(id)).isEqualTo("TX1|Nome|Descrizione|false|true");
        }

        @ParameterizedTest(name = "codice [{0}]")
        @ValueSource(strings = {"M01", "m01", " m01 "})
        @DisplayName("Il codice di un'altra misura, anche con maiuscole diverse, è rifiutato con 409 e non cambia nulla")
        void codiceDiAltraMisura(String codice) throws Exception {
            long id = creaMisura("T01", "Nome", "Descrizione", false);
            List<String> prima = catalogo();

            aggiorna(banco, "BANCO", id, corpo(codice, "Nome cambiato", "Descrizione", true, true))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codice").value("CONFLICT"))
                    .andExpect(jsonPath("$.campi.codice").exists());

            assertThat(catalogo()).isEqualTo(prima);
        }

        @Test
        @DisplayName("La modifica di una misura inesistente risponde 404 e non crea righe")
        void misuraInesistente() throws Exception {
            List<String> prima = catalogo();

            aggiorna(banco, "BANCO", MISURA_INESISTENTE, corpo("T01", "Nome", "Descrizione", false, true))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codice").value("NOT_FOUND"));

            assertThat(catalogo()).isEqualTo(prima);
        }
    }

    @Nested
    @DisplayName("Criterio 3: la misura disattivata esce dalle nuove selezioni e resta collegata alle richieste registrate")
    class Disattivazione {

        @Test
        @DisplayName("Il Banco disattiva la misura: cambia solo lo stato e la misura resta consultabile")
        void disattivaMisura() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", true);

            disattiva(banco, "BANCO", id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.attiva").value(false))
                    .andExpect(jsonPath("$.dati.nome").value("Misura di prova"));

            assertThat(rigaMisura(id)).isEqualTo("T01|Misura di prova|Descrizione|true|false");
            mvc.perform(get(MISURA, id).with(come(opt, "OPT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attiva").value(false))
                    .andExpect(jsonPath("$.dati.nome").value("Misura di prova"));
            assertThat(codiciTutte()).containsExactlyElementsOf(conIniziali("T01"));
        }

        @Test
        @DisplayName("La misura disattivata non compare nel catalogo attivo né nella vista di nuova richiesta")
        void esceDalleSelezioni() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);

            disattiva(banco, "BANCO", id).andExpect(status().isOk());

            assertThat(codiciAttivi(opt, "OPT")).containsExactlyElementsOf(CODICI_INIZIALI);
            assertThat(misureNuovaRichiesta()).extracting(Misura::id).doesNotContain(id);
            assertThat(misureNuovaRichiesta()).extracting(m -> m.dati().codice())
                    .containsExactlyElementsOf(CODICI_INIZIALI);
        }

        @Test
        @DisplayName("Una nuova richiesta con la misura disattivata è rifiutata e non viene registrata")
        void rifiutaNuovaRichiesta() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);
            disattiva(banco, "BANCO", id).andExpect(status().isOk());

            inviaRichiesta(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campi.misuraId").value(MISURA_NON_DISPONIBILE));

            assertThat(richiesteDellaOpt()).isZero();
        }

        @Test
        @DisplayName("Una nuova dichiarazione di offerta con la misura disattivata è rifiutata e non viene salvata")
        void rifiutaNuovaOfferta() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);
            disattiva(banco, "BANCO", id).andExpect(status().isOk());

            dichiaraOfferta(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messaggio").value(MISURA_NON_SELEZIONABILE))
                    .andExpect(jsonPath("$.campi.misuraId").exists());

            assertThat(righeOfferta()).isEmpty();
        }

        @Test
        @DisplayName("La richiesta già registrata resta collegata alla misura, con stato e dati invariati")
        void richiestaPrecedenteRestaCollegata() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);
            UUID richiesta = idRichiesta(inviaRichiesta(id).andExpect(status().isCreated()).andReturn());
            String rigaPrima = rigaRichiesta(richiesta);

            disattiva(banco, "BANCO", id).andExpect(status().isOk());

            assertThat(rigaRichiesta(richiesta)).isEqualTo(rigaPrima);
            assertThat(misuraDellaRichiesta(richiesta)).isEqualTo(id);
            for (var attore : List.of(Map.entry(opt, "OPT"), Map.entry(banco, "BANCO"))) {
                mvc.perform(get(RICHIESTA, richiesta).with(come(attore.getKey(), attore.getValue())))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.richiesta.id").value(richiesta.toString()))
                        .andExpect(jsonPath("$.richiesta.misuraId").value(id))
                        .andExpect(jsonPath("$.richiesta.nomeMisura").value("Misura di prova"))
                        .andExpect(jsonPath("$.richiesta.stato").value("CREATA"));
            }
        }

        @Test
        @DisplayName("Una dichiarazione di offerta già salvata non viene cancellata dalla disattivazione")
        void offertaPrecedenteResta() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);
            dichiaraOfferta(id).andExpect(status().isNoContent());

            disattiva(banco, "BANCO", id).andExpect(status().isOk());

            assertThat(righeOfferta()).containsExactly(id + ":true:true");
        }

        @Test
        @DisplayName("Una seconda disattivazione restituisce lo stato corrente senza altri effetti")
        void disattivazioneRipetuta() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);
            disattiva(banco, "BANCO", id).andExpect(status().isOk());
            List<String> prima = catalogo();

            disattiva(banco, "BANCO", id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attiva").value(false));

            assertThat(catalogo()).isEqualTo(prima);
        }

        @Test
        @DisplayName("Un corpo vuoto è ammesso, un corpo con campi è rifiutato e la misura resta attiva")
        void corpoDellaDisattivazione() throws Exception {
            long vuoto = creaMisura("T01", "Misura uno", "Descrizione", false);
            long conCampi = creaMisura("T02", "Misura due", "Descrizione", false);

            disattivaConCorpo(vuoto, "{}").andExpect(status().isOk());
            disattivaConCorpo(conCampi, "{\"attiva\":false}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campi.comando").exists());

            assertThat(rigaMisura(vuoto)).endsWith("|false");
            assertThat(rigaMisura(conCampi)).endsWith("|true");
        }

        @Test
        @DisplayName("La disattivazione di una misura inesistente risponde 404")
        void misuraInesistente() throws Exception {
            List<String> prima = catalogo();

            disattiva(banco, "BANCO", MISURA_INESISTENTE)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codice").value("NOT_FOUND"));

            assertThat(catalogo()).isEqualTo(prima);
        }

        @Test
        @DisplayName("Con attiva a true la modifica riattiva la misura, che torna selezionabile")
        void riattivazione() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);
            disattiva(banco, "BANCO", id).andExpect(status().isOk());

            aggiorna(banco, "BANCO", id, corpo("T01", "Misura di prova", "Descrizione", false, true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attiva").value(true));

            assertThat(rigaMisura(id)).endsWith("|true");
            assertThat(codiciAttivi(opt, "OPT")).containsExactlyElementsOf(conIniziali("T01"));
            inviaRichiesta(id).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Con attiva a false la modifica disattiva la misura insieme ai nuovi dati")
        void disattivazioneConModifica() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);

            aggiorna(banco, "BANCO", id, corpo("T01", "Nome finale", "Descrizione", false, false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attiva").value(false));

            assertThat(rigaMisura(id)).isEqualTo("T01|Nome finale|Descrizione|false|false");
            assertThat(codiciAttivi(opt, "OPT")).containsExactlyElementsOf(CODICI_INIZIALI);
        }
    }

    @Nested
    @DisplayName("Criterio 4: con un campo obbligatorio mancante il sistema indica il problema e non salva")
    class DatiIncompleti {

        static Stream<Arguments> creazioniNonValide() {
            String lungo11 = "A".repeat(11);
            String lungo256 = "a".repeat(256);
            return Stream.of(
                    Arguments.of("codice mancante", "{\"nome\":\"Nome\",\"descrizione\":\"Descrizione\"}", "codice"),
                    Arguments.of("nome mancante", "{\"codice\":\"T01\",\"descrizione\":\"Descrizione\"}", "nome"),
                    Arguments.of("descrizione mancante", "{\"codice\":\"T01\",\"nome\":\"Nome\"}", "descrizione"),
                    Arguments.of("codice vuoto",
                            "{\"codice\":\"   \",\"nome\":\"Nome\",\"descrizione\":\"Descrizione\"}", "codice"),
                    Arguments.of("nome vuoto",
                            "{\"codice\":\"T01\",\"nome\":\"\",\"descrizione\":\"Descrizione\"}", "nome"),
                    Arguments.of("descrizione di soli spazi",
                            "{\"codice\":\"T01\",\"nome\":\"Nome\",\"descrizione\":\" \\n \"}", "descrizione"),
                    Arguments.of("codice null",
                            "{\"codice\":null,\"nome\":\"Nome\",\"descrizione\":\"Descrizione\"}", "codice"),
                    Arguments.of("codice di 11 caratteri",
                            "{\"codice\":\"" + lungo11 + "\",\"nome\":\"Nome\",\"descrizione\":\"Descrizione\"}",
                            "codice"),
                    Arguments.of("nome di 256 caratteri",
                            "{\"codice\":\"T01\",\"nome\":\"" + lungo256 + "\",\"descrizione\":\"Descrizione\"}",
                            "nome"),
                    Arguments.of("descrizione di 256 caratteri",
                            "{\"codice\":\"T01\",\"nome\":\"Nome\",\"descrizione\":\"" + lungo256 + "\"}",
                            "descrizione"),
                    Arguments.of("carattere di controllo nel codice",
                            "{\"codice\":\"T\\u000701\",\"nome\":\"Nome\",\"descrizione\":\"Descrizione\"}", "codice"),
                    Arguments.of("a capo nel nome",
                            "{\"codice\":\"T01\",\"nome\":\"Nome\\nsu due righe\",\"descrizione\":\"Descrizione\"}",
                            "nome"),
                    Arguments.of("codice numerico",
                            "{\"codice\":101,\"nome\":\"Nome\",\"descrizione\":\"Descrizione\"}", "codice"),
                    Arguments.of("erogabileADistanza testuale",
                            "{\"codice\":\"T01\",\"nome\":\"Nome\",\"descrizione\":\"Descrizione\","
                                    + "\"erogabileADistanza\":\"true\"}", "erogabileADistanza"),
                    Arguments.of("erogabileADistanza null",
                            "{\"codice\":\"T01\",\"nome\":\"Nome\",\"descrizione\":\"Descrizione\","
                                    + "\"erogabileADistanza\":null}", "erogabileADistanza"),
                    Arguments.of("campo non previsto",
                            "{\"codice\":\"T01\",\"nome\":\"Nome\",\"descrizione\":\"Descrizione\",\"attiva\":false}",
                            "misura"),
                    Arguments.of("corpo null", "null", "misura"),
                    Arguments.of("corpo vuoto", "{}", "codice"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("creazioniNonValide")
        @DisplayName("La creazione con dati mancanti o non validi risponde 400 sul campo e non salva nulla")
        void creazioneNonValida(String caso, String corpo, String campo) throws Exception {
            List<String> prima = catalogo();

            crea(banco, "BANCO", corpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.campi." + campo).exists());

            assertThat(catalogo()).isEqualTo(prima);
        }

        @Test
        @DisplayName("Tutti i campi obbligatori mancanti sono segnalati insieme")
        void erroriSegnalatiInsieme() throws Exception {
            crea(banco, "BANCO", "{\"codice\":\"\",\"nome\":\" \",\"descrizione\":\"\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campi.codice").exists())
                    .andExpect(jsonPath("$.campi.nome").exists())
                    .andExpect(jsonPath("$.campi.descrizione").exists());

            assertThat(codiciSalvati()).containsExactlyElementsOf(CODICI_INIZIALI);
        }

        @ParameterizedTest(name = "corpo [{0}]")
        @ValueSource(strings = {
                "",
                "[]",
                "\"T01\"",
                "{\"codice\":\"T01\",\"nome\":\"Nome\",\"descrizione\":\"Descrizione\"",
                "{\"codice\":\"T01\",\"codice\":\"T02\",\"nome\":\"Nome\",\"descrizione\":\"Descrizione\"}"})
        @DisplayName("Un corpo JSON malformato o con chiavi duplicate risponde 400 e non salva nulla")
        void corpoMalformato(String corpo) throws Exception {
            List<String> prima = catalogo();

            crea(banco, "BANCO", corpo).andExpect(status().isBadRequest());

            assertThat(catalogo()).isEqualTo(prima);
        }

        @Test
        @DisplayName("I valori al limite (10, 255 e 255 caratteri, a capo nella descrizione) sono accettati")
        void valoriAlLimite() throws Exception {
            String codice = "C".repeat(10);
            String nome = "n".repeat(255);
            String descrizione = "Prima riga\n" + "d".repeat(244);

            long id = idMisura(crea(banco, "BANCO", corpo(codice, nome, descrizione, false))
                    .andExpect(status().isCreated())
                    .andReturn());

            assertThat(rigaMisura(id)).isEqualTo(codice + "|" + nome + "|" + descrizione + "|false|true");
        }

        @ParameterizedTest(name = "codice [{0}]")
        @ValueSource(strings = {"M01", "m01", "  M01  "})
        @DisplayName("Un codice già usato, anche con maiuscole o spazi diversi, risponde 409 e non salva nulla")
        void codiceDuplicato(String codice) throws Exception {
            List<String> prima = catalogo();

            crea(banco, "BANCO", corpo(codice, "Nome", "Descrizione", false))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codice").value("CONFLICT"))
                    .andExpect(jsonPath("$.campi.codice").exists());

            assertThat(catalogo()).isEqualTo(prima);
        }

        static Stream<Arguments> modificheNonValide() {
            return Stream.of(
                    Arguments.of("attiva mancante",
                            "{\"codice\":\"T01\",\"nome\":\"Nuovo\",\"descrizione\":\"Nuova\","
                                    + "\"erogabileADistanza\":true}", "attiva"),
                    Arguments.of("erogabileADistanza mancante",
                            "{\"codice\":\"T01\",\"nome\":\"Nuovo\",\"descrizione\":\"Nuova\",\"attiva\":false}",
                            "erogabileADistanza"),
                    Arguments.of("nome vuoto",
                            "{\"codice\":\"T01\",\"nome\":\"\",\"descrizione\":\"Nuova\","
                                    + "\"erogabileADistanza\":true,\"attiva\":false}", "nome"),
                    Arguments.of("descrizione mancante",
                            "{\"codice\":\"T01\",\"nome\":\"Nuovo\",\"erogabileADistanza\":true,\"attiva\":false}",
                            "descrizione"),
                    Arguments.of("attiva testuale",
                            "{\"codice\":\"T01\",\"nome\":\"Nuovo\",\"descrizione\":\"Nuova\","
                                    + "\"erogabileADistanza\":true,\"attiva\":\"false\"}", "attiva"),
                    Arguments.of("campo non previsto",
                            "{\"id\":1,\"codice\":\"T01\",\"nome\":\"Nuovo\",\"descrizione\":\"Nuova\","
                                    + "\"erogabileADistanza\":true,\"attiva\":false}", "misura"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("modificheNonValide")
        @DisplayName("La modifica con dati mancanti o non validi risponde 400 e lascia la misura invariata")
        void modificaNonValida(String caso, String corpo, String campo) throws Exception {
            long id = creaMisura("T01", "Nome", "Descrizione", false);
            List<String> prima = catalogo();

            aggiorna(banco, "BANCO", id, corpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.campi." + campo).exists());

            assertThat(catalogo()).isEqualTo(prima);
        }
    }

    @Nested
    @DisplayName("Criterio 5: OPT e Operatore ricevono FORBIDDEN e il catalogo non cambia")
    class AccessiNonAutorizzati {

        @ParameterizedTest(name = "ruolo {0}")
        @ValueSource(strings = {"OPT", "OPERATORE"})
        @DisplayName("Creazione, modifica, disattivazione ed elenco completo rispondono 403 senza modifiche")
        void ruoliNonBanco(String ruolo) throws Exception {
            long id = creaMisura("T01", "Nome", "Descrizione", false);
            UUID attore = "OPT".equals(ruolo) ? opt : operatore;
            List<String> prima = catalogo();

            crea(attore, ruolo, corpo("T02", "Nome", "Descrizione", false)).andExpect(status().isForbidden());
            aggiorna(attore, ruolo, id, corpo("T01", "Nome cambiato", "Descrizione", true, false))
                    .andExpect(status().isForbidden());
            disattiva(attore, ruolo, id).andExpect(status().isForbidden());
            mvc.perform(get(TUTTE).with(come(attore, ruolo))).andExpect(status().isForbidden());

            assertThat(catalogo()).isEqualTo(prima);
        }

        @ParameterizedTest(name = "ruolo {0}")
        @ValueSource(strings = {"OPT", "OPERATORE"})
        @DisplayName("Il ruolo è verificato prima del corpo: anche un corpo non valido risponde 403")
        void ruoloPrimaDelCorpo(String ruolo) throws Exception {
            long id = creaMisura("T01", "Nome", "Descrizione", false);
            UUID attore = "OPT".equals(ruolo) ? opt : operatore;
            List<String> prima = catalogo();

            crea(attore, ruolo, "{\"altro\":1}").andExpect(status().isForbidden());
            aggiorna(attore, ruolo, id, "{").andExpect(status().isForbidden());
            disattivaCome(attore, ruolo, id, "{\"altro\":1}").andExpect(status().isForbidden());

            assertThat(catalogo()).isEqualTo(prima);
        }

        @ParameterizedTest(name = "ruolo {0}")
        @ValueSource(strings = {"OPT", "OPERATORE"})
        @DisplayName("OPT e Operatore possono consultare il catalogo attivo e la singola misura")
        void consultazioneAmmessa(String ruolo) throws Exception {
            long id = creaMisura("T01", "Nome", "Descrizione", false);
            UUID attore = "OPT".equals(ruolo) ? opt : operatore;

            assertThat(codiciAttivi(attore, ruolo)).containsExactlyElementsOf(conIniziali("T01"));
            mvc.perform(get(MISURA, id).with(come(attore, ruolo)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id));
        }

        @Test
        @DisplayName("Senza sessione ogni operazione sul catalogo risponde 401 e non cambia nulla")
        void senzaSessione() throws Exception {
            long id = creaMisura("T01", "Nome", "Descrizione", false);
            List<String> prima = catalogo();

            mvc.perform(post(MISURE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("T02", "Nome", "Descrizione", false)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(put(MISURA, id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("T01", "Altro", "Descrizione", false, false)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(DISATTIVAZIONE, id).with(csrf())).andExpect(status().isUnauthorized());
            for (String percorso : List.of(MISURE, TUTTE, "/api/catalogo/misure/" + id)) {
                mvc.perform(get(percorso)).andExpect(status().isUnauthorized());
            }

            assertThat(catalogo()).isEqualTo(prima);
        }

        @Test
        @DisplayName("Il Banco senza token CSRF riceve 403 e il catalogo non cambia")
        void bancoSenzaCsrf() throws Exception {
            long id = creaMisura("T01", "Nome", "Descrizione", false);
            List<String> prima = catalogo();

            mvc.perform(post(MISURE).with(come(banco, "BANCO")).contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("T02", "Nome", "Descrizione", false)))
                    .andExpect(status().isForbidden());
            mvc.perform(put(MISURA, id).with(come(banco, "BANCO")).contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("T01", "Altro", "Descrizione", false, false)))
                    .andExpect(status().isForbidden());
            mvc.perform(post(DISATTIVAZIONE, id).with(come(banco, "BANCO"))).andExpect(status().isForbidden());

            assertThat(catalogo()).isEqualTo(prima);
        }
    }

    @Nested
    @DisplayName("Regressioni (#165): ricerca esistente e richieste registrate prima della disattivazione")
    class Regressioni {

        @Test
        @DisplayName("Gli elenchi della OPT e del Banco mostrano ancora la richiesta con la misura disattivata")
        void elenchiRichieste() throws Exception {
            long disattivata = creaMisura("T01", "Misura ritirata", "Descrizione", false);
            long attiva = creaMisura("T02", "Misura in uso", "Descrizione", false);
            UUID vecchia = idRichiesta(inviaRichiesta(disattivata).andExpect(status().isCreated()).andReturn());
            UUID nuova = idRichiesta(inviaRichiesta(attiva).andExpect(status().isCreated()).andReturn());

            disattiva(banco, "BANCO", disattivata).andExpect(status().isOk());

            assertThat(nomeMisuraNellElenco(RICHIESTE_OPT, opt, "OPT", vecchia)).isEqualTo("Misura ritirata");
            assertThat(nomeMisuraNellElenco(RICHIESTE_OPT, opt, "OPT", nuova)).isEqualTo("Misura in uso");
            assertThat(nomeMisuraNellElenco(RICHIESTE, opt, "OPT", vecchia)).isEqualTo("Misura ritirata");
            assertThat(nomeMisuraNellElenco(RICHIESTE, banco, "BANCO", vecchia)).isEqualTo("Misura ritirata");
            assertThat(nomeMisuraNellElenco(RICHIESTE, banco, "BANCO", nuova)).isEqualTo("Misura in uso");
        }

        @Test
        @DisplayName("Il ritento con la stessa Idempotency-Key restituisce l'invio registrato prima della disattivazione")
        void ritentoDopoLaDisattivazione() throws Exception {
            long id = creaMisura("T01", "Misura di prova", "Descrizione", false);
            UUID chiave = UUID.fromString("00000000-0000-4000-8000-000000000165");
            UUID registrata = idRichiesta(inviaRichiesta(id, chiave).andExpect(status().isCreated()).andReturn());

            disattiva(banco, "BANCO", id).andExpect(status().isOk());

            UUID ritentata = idRichiesta(inviaRichiesta(id, chiave)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.misuraId").value(id))
                    .andExpect(jsonPath("$.nomeMisura").value("Misura di prova"))
                    .andReturn());
            assertThat(ritentata).isEqualTo(registrata);
            assertThat(richiesteDellaOpt()).isEqualTo(1);

            inviaRichiesta(id, UUID.fromString("00000000-0000-4000-8000-000000000166"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campi.misuraId").value(MISURA_NON_DISPONIBILE));
            assertThat(richiesteDellaOpt()).isEqualTo(1);
        }

        @Test
        @DisplayName("Le misure iniziali restano selezionabili e invariate dopo le operazioni su una nuova misura")
        void misureInizialiInvariate() throws Exception {
            List<String> iniziali = catalogo();
            long id = creaMisura("T01", "Nome", "Descrizione", false);
            aggiorna(banco, "BANCO", id, corpo("T01", "Altro nome", "Altra descrizione", true, true))
                    .andExpect(status().isOk());
            disattiva(banco, "BANCO", id).andExpect(status().isOk());

            assertThat(catalogo()).filteredOn(riga -> !riga.contains("|T01|"))
                    .containsExactlyElementsOf(iniziali);
            assertThat(codiciAttivi(opt, "OPT")).containsExactlyElementsOf(CODICI_INIZIALI);
            inviaRichiesta(dati.misura("M01")).andExpect(status().isCreated());
        }
    }

    // ---- comandi HTTP -------------------------------------------------------------------------

    private ResultActions crea(UUID attore, String ruolo, String corpo) throws Exception {
        return mvc.perform(conCorpo(post(MISURE), corpo).with(come(attore, ruolo)).with(csrf()));
    }

    private ResultActions aggiorna(UUID attore, String ruolo, long id, String corpo) throws Exception {
        return mvc.perform(conCorpo(put(MISURA, id), corpo).with(come(attore, ruolo)).with(csrf()));
    }

    private ResultActions disattiva(UUID attore, String ruolo, long id) throws Exception {
        return mvc.perform(post(DISATTIVAZIONE, id).with(come(attore, ruolo)).with(csrf()));
    }

    private ResultActions disattivaCome(UUID attore, String ruolo, long id, String corpo) throws Exception {
        return mvc.perform(conCorpo(post(DISATTIVAZIONE, id), corpo).with(come(attore, ruolo)).with(csrf()));
    }

    private ResultActions disattivaConCorpo(long id, String corpo) throws Exception {
        return disattivaCome(banco, "BANCO", id, corpo);
    }

    private ResultActions inviaRichiesta(long misura) throws Exception {
        return mvc.perform(richiestaInvio(misura).with(come(opt, "OPT")).with(csrf()));
    }

    private ResultActions inviaRichiesta(long misura, UUID chiave) throws Exception {
        return mvc.perform(richiestaInvio(misura).header("Idempotency-Key", chiave.toString())
                .with(come(opt, "OPT")).with(csrf()));
    }

    private ResultActions dichiaraOfferta(long misura) throws Exception {
        return mvc.perform(conCorpo(put(OFFERTA_MISURA, misura), "{\"tramiteProfessionista\":true}")
                .with(come(opt, "OPT")).with(csrf()));
    }

    /** Crea la misura come Banco e ne restituisce l'identificativo. */
    private long creaMisura(String codice, String nome, String descrizione, boolean erogabileADistanza)
            throws Exception {
        return idMisura(crea(banco, "BANCO", corpo(codice, nome, descrizione, erogabileADistanza))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private static MockHttpServletRequestBuilder richiestaInvio(long misura) {
        return conCorpo(post(RICHIESTE),
                "{\"misuraId\":" + misura + ",\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\"}");
    }

    private static MockHttpServletRequestBuilder conCorpo(MockHttpServletRequestBuilder richiesta, String corpo) {
        return richiesta.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor come(UUID attore, String ruolo) {
        return user(attore.toString()).roles(ruolo);
    }

    // ---- corpi JSON ---------------------------------------------------------------------------

    /** Corpo di creazione prodotto con {@link JsonMapper}, come quello di un client reale. */
    private static String corpo(String codice, String nome, String descrizione, boolean erogabileADistanza) {
        Map<String, Object> campi = new LinkedHashMap<>();
        campi.put("codice", codice);
        campi.put("nome", nome);
        campi.put("descrizione", descrizione);
        campi.put("erogabileADistanza", erogabileADistanza);
        return JSON.writeValueAsString(campi);
    }

    /** Corpo di modifica: richiede anche lo stato di attivazione. */
    private static String corpo(String codice, String nome, String descrizione, boolean erogabileADistanza,
            boolean attiva) {
        Map<String, Object> campi = new LinkedHashMap<>();
        campi.put("codice", codice);
        campi.put("nome", nome);
        campi.put("descrizione", descrizione);
        campi.put("erogabileADistanza", erogabileADistanza);
        campi.put("attiva", attiva);
        return JSON.writeValueAsString(campi);
    }

    // ---- letture HTTP -------------------------------------------------------------------------

    private List<String> codiciAttivi(UUID attore, String ruolo) throws Exception {
        return JsonPath.read(testo(mvc.perform(get(MISURE).with(come(attore, ruolo)))
                .andExpect(status().isOk()).andReturn()), "$[*].dati.codice");
    }

    private List<String> codiciTutte() throws Exception {
        return JsonPath.read(testo(mvc.perform(get(TUTTE).with(come(banco, "BANCO")))
                .andExpect(status().isOk()).andReturn()), "$[*].dati.codice");
    }

    private String nomeNelCatalogoAttivo(long id) throws Exception {
        List<String> nomi = JsonPath.read(testo(mvc.perform(get(MISURE).with(come(opt, "OPT")))
                .andExpect(status().isOk()).andReturn()), "$[?(@.id == " + id + ")].dati.nome");
        assertThat(nomi).hasSize(1);
        return nomi.get(0);
    }

    /** Misure passate alla vista di nuova richiesta della OPT, nell'ordine della pagina. */
    @SuppressWarnings("unchecked")
    private List<Misura> misureNuovaRichiesta() throws Exception {
        MvcResult esito = mvc.perform(get(NUOVA_RICHIESTA).with(come(opt, "OPT")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(esito.getModelAndView()).isNotNull();
        return new ArrayList<>((List<Misura>) esito.getModelAndView().getModel().get("misure"));
    }

    /** Nome della misura mostrato per la richiesta nell'elenco indicato; la richiesta deve comparire una volta. */
    private String nomeMisuraNellElenco(String percorso, UUID attore, String ruolo, UUID richiesta)
            throws Exception {
        List<String> nomi = JsonPath.read(testo(mvc.perform(get(percorso).with(come(attore, ruolo)))
                .andExpect(status().isOk()).andReturn()), "$[?(@.id == '" + richiesta + "')].nomeMisura");
        assertThat(nomi).as("richiesta %s in %s", richiesta, percorso).hasSize(1);
        return nomi.get(0);
    }

    private static long idMisura(MvcResult esito) throws Exception {
        return ((Number) JsonPath.read(testo(esito), "$.id")).longValue();
    }

    private static UUID idRichiesta(MvcResult esito) throws Exception {
        return UUID.fromString(JsonPath.read(testo(esito), "$.id"));
    }

    private static String testo(MvcResult esito) throws Exception {
        return esito.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ---- letture del database -----------------------------------------------------------------

    /** Riga della misura come {@code codice|nome|descrizione|erogabileADistanza|attiva}. */
    private String rigaMisura(long id) {
        return jdbc.queryForObject("""
                select codice, nome, descrizione, erogabile_a_distanza, attiva from catalogo_misure where id = ?
                """, (rs, n) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|"
                + rs.getBoolean(4) + "|" + rs.getBoolean(5), id);
    }

    /** Intero catalogo salvato, una riga per misura ordinata per id: serve a provare che nulla è cambiato. */
    private List<String> catalogo() {
        return jdbc.query("""
                select id, codice, nome, descrizione, erogabile_a_distanza, attiva from catalogo_misure order by id
                """, (rs, n) -> rs.getLong(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|"
                + rs.getString(4) + "|" + rs.getBoolean(5) + "|" + rs.getBoolean(6));
    }

    private List<String> codiciSalvati() {
        return jdbc.queryForList("select codice from catalogo_misure order by codice", String.class);
    }

    private long idPerCodice(String codice) {
        return jdbc.queryForObject("select id from catalogo_misure where codice = ?", Long.class, codice);
    }

    private long misuraDellaRichiesta(UUID richiesta) {
        return jdbc.queryForObject("select misura_id from richieste where id = ?", Long.class, richiesta);
    }

    /** Dati della richiesta che la disattivazione della misura non deve toccare. */
    private String rigaRichiesta(UUID richiesta) {
        return jdbc.queryForObject("""
                select titolare_id, misura_id, comune_codice, tipo_destinatario, inviata_il, stato
                from richieste where id = ?
                """, (rs, n) -> rs.getString(1) + "|" + rs.getLong(2) + "|" + rs.getString(3) + "|"
                + rs.getString(4) + "|" + rs.getTimestamp(5).toInstant() + "|" + rs.getString(6), richiesta);
    }

    private int richiesteDellaOpt() {
        return jdbc.queryForObject("select count(*) from richieste where titolare_id = ?", Integer.class, opt);
    }

    /** Righe dell'offerta della OPT come {@code misura:tramiteProfessionista:attiva}, ordinate per misura. */
    private List<String> righeOfferta() {
        return jdbc.query("""
                select misura_id, tramite_professionista, attiva from catalogo_offerte_opt
                where opt_id = ? order by misura_id
                """, (rs, n) -> rs.getLong(1) + ":" + rs.getBoolean(2) + ":" + rs.getBoolean(3), opt);
    }

    // ---- preparazione -------------------------------------------------------------------------

    private static List<String> conIniziali(String... aggiunti) {
        List<String> codici = new ArrayList<>(CODICI_INIZIALI);
        codici.addAll(List.of(aggiunti));
        return codici;
    }

    /**
     * Svuota i dati dinamici e riporta il catalogo alle dodici misure delle migrazioni.
     * Le richieste e le offerte che usano le misure di prova sono già cancellate da {@code svuota()}.
     */
    private void ripristinaCatalogo() {
        dati.svuota();
        jdbc.update("delete from catalogo_misure where codice not in ("
                + String.join(",", CODICI_INIZIALI.stream().map(c -> "'" + c + "'").toList()) + ")");
    }
}
