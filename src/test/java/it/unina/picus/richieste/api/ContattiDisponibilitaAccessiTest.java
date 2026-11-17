package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.AccessiBlackBoxTest;
import it.unina.picus.shared.ClientHttp;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.shared.ScenarioAccessi.ContattoPreparato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettaglioContatto;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * #28 — CA-02: l'account interpellato (OPT o operatore del Banco) vede i soli dati minimi e risponde
 * solo al proprio contatto; le altre pratiche della richiedente restano riservate.
 *
 * <p>Tecniche: classi di equivalenza sull'attore (interpellato, altro candidato della stessa richiesta,
 * estraneo, OPT richiedente, Banco) per ciascuna tipologia di erogatore, con un controllo positivo.</p>
 *
 * <p>Da confermare con il team: QS-01A dice che la OPT interpellata «apre R1», mentre il contratto
 * {@code consultaRichiesta} ammette solo Banco, titolare e assegnato e l'interpellato usa
 * {@code consultaContatto}. I test seguono il contratto.</p>
 */
@DisplayName("#28 CA-02 · Erogatore interpellato e contatto di disponibilità")
class ContattiDisponibilitaAccessiTest extends AccessiBlackBoxTest {

    /** Consultazione HTTP di un contatto ({@code ContattiDisponibilitaController}). */
    private static final String CONSULTA_CONTATTO = "/api/contatti-disponibilita/{id}";

    @Autowired
    private WebApplicationContext web;

    @ParameterizedTest(name = "CA-02 · {0} interpellato consulta i dati minimi del contatto")
    @EnumSource(TipoErogatore.class)
    void interpellatoConsultaDatiMinimi(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        DettaglioContatto dettaglio = scenario.contatti().consultaContatto(interpellato, contatto.id());

        assertEquals(contatto.id(), dettaglio.contatto().id());
        assertEquals(interpellato.id(), dettaglio.contatto().erogatoreId());
        assertEquals(EsitoContatto.IN_ATTESA, dettaglio.contatto().esito());
        assertEquals(scenario.misura(), dettaglio.misuraId());
        assertEquals(TipoDestinatario.PERSONA, dettaglio.tipoDestinatario());
        assertEquals(ScenarioAccessi.COMUNE_RICHIESTA, dettaglio.comuneCodice());
    }

    @Test
    @DisplayName("CA-02 · la proiezione del contatto non espone titolare, descrizione né accordo")
    void proiezioneContattoMinima() {
        Set<String> campi = Arrays.stream(DettaglioContatto.class.getRecordComponents())
                .map(c -> c.getName()).collect(Collectors.toSet());

        assertEquals(Set.of("contatto", "misuraId", "nomeMisura", "tipoDestinatario", "comuneCodice"), campi,
                "i dati minimi per valutare la disponibilità sono cambiati: rivedere QS-01A e #28");
    }

    @Test
    @DisplayName("CA-02 · via HTTP il destinatario riceve solo i dati minimi del contatto; un estraneo riceve 403")
    void datiMinimiNellaRispostaHttp() throws Exception {
        ClientHttp http = new ClientHttp(web);
        Account titolare = scenario.optRichiedente();
        Account destinatario = scenario.optRichiedente();
        Account estraneo = scenario.optRichiedente();
        ContattoPreparato contatto = scenario.contattoInAttesa(titolare, destinatario);

        MockHttpServletRequestBuilder lettura = get(CONSULTA_CONTATTO, contatto.contattoId())
                .accept(MediaType.APPLICATION_JSON);
        http.richiediEndpointConsegnato(lettura);

        MockHttpServletResponse risposta = http.esegui(lettura.session(http.accedi(destinatario)));

        assertEquals(200, risposta.getStatus());
        String json = risposta.getContentAsString();
        Map<String, Object> corpo = JsonPath.read(json, "$");
        Map<String, Object> datiContatto = JsonPath.read(json, "$.contatto");
        assertEquals(Set.of("contatto", "misuraId", "nomeMisura", "tipoDestinatario", "comuneCodice"), corpo.keySet(),
                "solo i campi consentiti");
        assertEquals(Set.of("id", "richiestaId", "erogatoreId", "ambito", "esito"), datiContatto.keySet(),
                "solo i campi consentiti del contatto");
        assertEquals(contatto.contattoId().toString(), datiContatto.get("id"));
        assertEquals(destinatario.id().toString(), datiContatto.get("erogatoreId"));
        assertEquals("IN_ATTESA", datiContatto.get("esito"));
        assertEquals(contatto.misuraId(), ((Number) corpo.get("misuraId")).longValue());
        assertEquals(contatto.nomeMisura(), corpo.get("nomeMisura"));
        assertEquals(ScenarioAccessi.COMUNE_RICHIESTA, corpo.get("comuneCodice"));
        assertFalse(json.contains(titolare.id().toString()), "nessun titolare");
        assertFalse(json.contains(ScenarioAccessi.DESCRIZIONE), "nessuna descrizione");

        MockHttpServletResponse negata = http.esegui(get(CONSULTA_CONTATTO, contatto.contattoId())
                .session(http.accedi(estraneo)).accept(MediaType.APPLICATION_JSON));

        assertEquals(403, negata.getStatus());
        assertFalse(negata.getContentAsString().contains(contatto.contattoId().toString()), "nessun dato del contatto");
    }

    @ParameterizedTest(name = "CA-02 · {0} interpellato risponde al proprio contatto")
    @EnumSource(TipoErogatore.class)
    void interpellatoRispondeAlProprioContatto(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        ContattoDisponibilita risposta =
                scenario.contatti().rispondiDisponibilita(interpellato, contatto.id(), true);

        assertEquals(EsitoContatto.DISPONIBILE, risposta.esito());
        assertEquals(StatoRichiesta.PRESA_IN_CARICO, scenario.istantanea(contatto.richiestaId()).richiesta().stato());
    }

    @ParameterizedTest(name = "CA-02 · {0} interpellato non apre il dettaglio completo della richiesta")
    @EnumSource(TipoErogatore.class)
    void interpellatoNonApreIlDettaglioCompleto(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        assertNegatoSenzaModifiche(contatto.richiestaId(),
                () -> scenario.invio().consultaRichiesta(interpellato.identita(), contatto.richiestaId()));
    }

    @ParameterizedTest(name = "CA-02 · le altre pratiche della richiedente restano riservate a {0}")
    @EnumSource(TipoErogatore.class)
    void altrePraticheDellaRichiedenteRiservate(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        scenario.richiestaConContatto(titolare, interpellato);
        UUID altra = scenario.richiestaCreata(titolare);

        assertNegatoSenzaModifiche(altra,
                () -> scenario.invio().consultaRichiesta(interpellato.identita(), altra));
    }

    @ParameterizedTest(name = "CA-02 · {0} interpellato non annulla un'altra pratica della richiedente")
    @EnumSource(TipoErogatore.class)
    void altrePraticheDellaRichiedenteNonAnnullabili(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        scenario.richiestaConContatto(titolare, interpellato);
        UUID altra = scenario.richiestaCreata(titolare);

        assertNegatoSenzaModifiche(altra,
                () -> scenario.richieste().annullaRichiesta(interpellato.identita(), altra, "Motivo di prova"));
    }

    @ParameterizedTest(name = "CA-02 · un account non interpellato ({0}) non consulta né risponde")
    @EnumSource(TipoErogatore.class)
    void accountNonInterpellatoNonConsultaNeRisponde(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(TipoErogatore.OPT);
        Account estraneo = tipo == TipoErogatore.OPT ? scenario.opt(ScenarioAccessi.COMUNE_LIMITROFO)
                : scenario.operatore(tipo);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        assertNegato(() -> scenario.contatti().consultaContatto(estraneo, contatto.id()));
        assertNegatoSenzaModifiche(contatto.richiestaId(),
                () -> scenario.contatti().rispondiDisponibilita(estraneo, contatto.id(), true));
        assertEquals(EsitoContatto.IN_ATTESA, esitoPerIlBanco(contatto));
    }

    @ParameterizedTest(name = "CA-02 · {0} interpellato non consulta né risponde al contatto di un altro candidato")
    @EnumSource(TipoErogatore.class)
    void interpellatoNonUsaIlContattoDiUnAltroCandidato(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account candidatoA = scenario.erogatore(tipo);
        Account candidatoB = scenario.erogatore(tipo);
        UUID id = scenario.richiestaPresaInCarico(titolare);

        // #49: un contatto per volta. A risponde prima che il Banco interpelli B.
        ContattoDisponibilita contattoA = scenario.interpella(id, candidatoA);
        scenario.contatti().rispondiDisponibilita(candidatoA, contattoA.id(), false);
        assertEquals(EsitoContatto.NON_DISPONIBILE, esitoPerIlBanco(contattoA), "risposta legittima di A");
        ContattoDisponibilita contattoB = scenario.interpella(id, candidatoB);

        // stessa richiesta, ma il contatto ricevuto da A non autorizza il contatto di B
        assertNegato(() -> scenario.contatti().consultaContatto(candidatoA, contattoB.id()));
        assertNegatoSenzaModifiche(id,
                () -> scenario.contatti().rispondiDisponibilita(candidatoA, contattoB.id(), true));
        assertEquals(EsitoContatto.IN_ATTESA, esitoPerIlBanco(contattoB), "il diniego non modifica il contatto di B");

        // controllo positivo: B risponde al proprio contatto
        scenario.contatti().rispondiDisponibilita(candidatoB, contattoB.id(), true);
        assertEquals(EsitoContatto.DISPONIBILE, esitoPerIlBanco(contattoB), "risposta legittima di B");
    }

    @Test
    @DisplayName("CA-02 · la OPT richiedente e il Banco non rispondono al posto dell'interpellato")
    void soloIlDestinatarioRisponde() {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(TipoErogatore.OPT);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        assertNegatoSenzaModifiche(contatto.richiestaId(),
                () -> scenario.contatti().rispondiDisponibilita(titolare, contatto.id(), true));
        assertNegatoSenzaModifiche(contatto.richiestaId(),
                () -> scenario.contatti().rispondiDisponibilita(scenario.banco(), contatto.id(), true));
        assertEquals(EsitoContatto.IN_ATTESA, esitoPerIlBanco(contatto));
    }

    private EsitoContatto esitoPerIlBanco(ContattoDisponibilita contatto) {
        return scenario.esitoPerIlBanco(contatto.richiestaId(), contatto.id());
    }
}
