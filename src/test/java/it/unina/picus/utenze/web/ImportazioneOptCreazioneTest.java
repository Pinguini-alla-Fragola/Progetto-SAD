package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.ClientHttp.SessioneAnonima;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi.AccountAmministrativo;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/** Nuove OPT, file non valido e righe scartate. */
@DisplayName("#24 CA-01/CA-02/CA-03 · Creazione, file non valido e righe scartate")
class ImportazioneOptCreazioneTest extends ImportazioneOptBlackBox {

    // ------------------------------------------------------------------ CA-01

    @Test
    @DisplayName("CA-01 · ogni nuova OPT valida ottiene un account OPT in attesa e un solo link alla propria email")
    void creaLeNuoveOpt() throws Exception {
        scenario.banco();
        Riga prima = nuovaRiga();
        Riga seconda = nuovaRiga().conComune(ALTRO_COMUNE);
        Istantanea stato = istantanea();

        Map<Integer, EsitoRiga> report = importa(csv(prima, seconda));

        assertEquals(2, report.size(), "una voce per riga");
        assertEsito("CREATA", report, 2);
        assertEsito("CREATA", report, 3);
        assertSoloNuove(stato, prima, seconda);
        for (Riga riga : List.of(prima, seconda)) {
            AccountAmministrativo opt = optConCodice(riga.codice());
            assertEquals(RuoloUtente.OPT, opt.ruolo());
            assertEquals(StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE, opt.stato());
            assertEquals(riga.nome(), opt.nomeVisualizzato());
            assertEquals(riga.comune(), opt.comuneCodice());
            assertEquals(riga.email(), opt.email());
            assertEquals(riga.codiceFiscale(), istantanea().utenze().get(opt.id()).codiceFiscale());
        }
        attendiUnInvito(prima, seconda);
        assertNotEquals(tokenInviati(prima.email()), tokenInviati(seconda.email()), "link distinti");
        assertInviiInvariati(invii(List.of(prima.email(), seconda.email())));
    }

    @Test
    @DisplayName("CA-01 · il link inviato attiva l'account una sola volta")
    void linkMonouso() throws Exception {
        Riga riga = nuovaRiga();
        assertEsito("CREATA", importa(csv(riga)), 2);
        attendiUnInvito(riga);
        String token = tokenInviati(riga.email()).getFirst();

        assertEquals(200, usaLink(token, ScenarioAccessi.PASSWORD).getStatus(), "primo uso del link");
        assertEquals(StatoUtenza.ATTIVO, optConCodice(riga.codice()).stato());
        Istantanea attivata = istantanea();

        assertEquals(400, usaLink(token, "Altra-Prova-24-Csv!").getStatus(), "secondo uso del link");
        assertEquals(attivata, istantanea(), "il link già usato non deve cambiare nulla");
    }

    private MockHttpServletResponse usaLink(String token, String password) throws Exception {
        SessioneAnonima anonima = http.sessioneAnonima();
        return http.esegui(post("/api/auth/attiva/conferma").session(anonima.sessione())
                .header(anonima.csrf().header(), anonima.csrf().token())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"nuovaPassword\":\"" + password + "\"}"));
    }

    // ------------------------------------------------------------------ CA-02

    static Stream<Arguments> fileNonValidi() {
        Riga riga = nuovaRiga();
        List<String> colonne = List.of(INTESTAZIONE.split(","));
        List<String> valori = List.of(riga.csv().split(","));
        Stream<Arguments> senzaColonna = colonne.stream().map(colonna -> {
            int indice = colonne.indexOf(colonna);
            return arguments("senza colonna " + colonna, riga,
                    utf8(senza(colonne, indice) + "\n" + senza(valori, indice) + "\n"));
        });
        ByteArrayOutputStream nonUtf8 = new ByteArrayOutputStream();
        nonUtf8.writeBytes(utf8(csv(riga)));
        nonUtf8.writeBytes(new byte[] {(byte) 0xC3, (byte) 0x28, (byte) 0xFF, '\n'});
        Stream<Arguments> illeggibili = Stream.of(
                arguments("file vuoto", riga, new byte[0]),
                arguments("senza intestazione", riga, utf8(riga.csv() + "\n")),
                arguments("contenuto binario", riga,
                        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, (byte) 0xFF}),
                arguments("byte non UTF-8 dopo una riga valida", riga, nonUtf8.toByteArray()),
                arguments("virgolette mai chiuse dopo una riga valida", riga,
                        utf8(csv(riga) + "\"T24-APERTA,nome,napoli\n")));
        return Stream.concat(senzaColonna, illeggibili);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fileNonValidi")
    @DisplayName("CA-02 · file illeggibile o senza colonna: problema indicato e nessuna riga importata")
    void fileNonValido(String classe, Riga riga, byte[] contenuto) throws Exception {
        Account banco = scenario.banco();
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(riga.email()));

        MockHttpServletResponse risposta = carica(banco, contenuto);

        assertEquals(400, risposta.getStatus(), classe);
        String dettaglio = JsonPath.read(risposta.getContentAsString(StandardCharsets.UTF_8), "$.detail");
        assertNotNull(dettaglio, "il problema deve essere indicato");
        assertFalse(dettaglio.isBlank(), "il problema deve essere indicato");
        assertEquals(prima, istantanea(), "nessuna riga importata");
        assertInviiInvariati(invii);
    }

    private static String senza(List<String> campi, int indice) {
        List<String> rimasti = new ArrayList<>(campi);
        rimasti.remove(indice);
        return String.join(",", rimasti);
    }

    private static byte[] utf8(String testo) {
        return testo.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ CA-03: campi non validi

    static Stream<Arguments> campiNonValidi() {
        return Stream.of(
                campo("codice_struttura vuoto", r -> r.conCodice("")),
                campo("codice_struttura di soli spazi", r -> r.conCodice("   ")),
                campo("nome_opt vuoto", r -> r.conNome("")),
                campo("nome_opt di soli spazi", r -> r.conNome("   ")),
                campo("comune vuoto", r -> r.conComune("")),
                campo("codice_fiscale vuoto", r -> r.conCodiceFiscale("")),
                campo("email vuota", r -> r.conEmail("")),
                campo("email senza chiocciola", r -> r.conEmail("opt-sintetica.picus.test")),
                campo("email senza parte locale", r -> r.conEmail("@picus.test")),
                campo("email senza dominio", r -> r.conEmail("opt-sintetica@")),
                campo("email con spazio interno", r -> r.conEmail("opt sintetica@picus.test")),
                campo("email con due chiocciole", r -> r.conEmail("opt@sintetica@picus.test")),
                campo("codice fiscale di 10 cifre", r -> r.conCodiceFiscale("9123456789")),
                campo("codice fiscale di 12 cifre", r -> r.conCodiceFiscale("912345678901")),
                campo("codice fiscale di 11 caratteri non numerici", r -> r.conCodiceFiscale("9123456789A")),
                campo("codice fiscale di 16 cifre", r -> r.conCodiceFiscale("9123456789012345")),
                campo("codice fiscale di 15 caratteri", r -> r.conCodiceFiscale("XXXYYY00A00X000")));
    }

    private static Arguments campo(String classe, UnaryOperator<Riga> difetto) {
        return arguments(classe, difetto);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("campiNonValidi")
    @DisplayName("CA-03 · la riga con un campo non valido è scartata con motivo, le righe valide vicine sono importate")
    void campoNonValido(String classe, UnaryOperator<Riga> difetto) throws Exception {
        scenario.banco();
        Riga precedente = nuovaRiga();
        Riga difettosa = difetto.apply(nuovaRiga());
        Riga successiva = nuovaRiga();
        Istantanea prima = istantanea();

        Map<Integer, EsitoRiga> report = importa(csv(precedente, difettosa, successiva));

        assertEsito("CREATA", report, 2);
        assertScartata(report, 3);
        assertEsito("CREATA", report, 4);
        assertSoloNuove(prima, precedente, successiva);
        attendiUnInvito(precedente, successiva);
        assertTrue(tokenInviati(difettosa.email()).isEmpty(), "nessun invito per la riga scartata");
    }

    @Test
    @DisplayName("CA-03 · più righe non valide: numero di riga e motivo per ciascuna, importate tutte le altre")
    void piuRigheNonValide() throws Exception {
        scenario.banco();
        Riga valida = nuovaRiga();
        Riga altraValida = nuovaRiga();
        Istantanea prima = istantanea();

        Map<Integer, EsitoRiga> report = importa(csv(nuovaRiga().conNome(""), valida,
                nuovaRiga().conEmail("opt-sintetica.picus.test"), altraValida, nuovaRiga().conCodiceFiscale("123")));

        for (int scartata : List.of(2, 4, 6)) {
            assertScartata(report, scartata);
        }
        assertEsito("CREATA", report, 3);
        assertEsito("CREATA", report, 5);
        assertSoloNuove(prima, valida, altraValida);
        attendiUnInvito(valida, altraValida);
    }

    // ------------------------------------------------------------------ CA-03: email già usata

    enum Titolare { BANCO, OPERATORE, OPT_ATTIVA, OPT_IN_ATTESA, OPT_REVOCATA }

    @ParameterizedTest(name = "email di {0}")
    @EnumSource(Titolare.class)
    @DisplayName("CA-03 · nuova OPT con email già usata da un altro account: scartata, le altre importate")
    void emailGiaUsata(Titolare titolare) throws Exception {
        scenario.banco();
        Account altro = switch (titolare) {
            case BANCO -> scenario.banco();
            case OPERATORE -> scenario.operatore(TipoErogatore.INTERNO_BANCO);
            default -> optRegistrata();
        };
        if (titolare == Titolare.OPT_IN_ATTESA) {
            impostaStato(altro, StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE);
        } else if (titolare == Titolare.OPT_REVOCATA) {
            scenario.revoca(altro);
        }
        Riga conflitto = nuovaRiga().conEmail(altro.email());
        Riga valida = nuovaRiga();
        Istantanea prima = istantanea();

        Map<Integer, EsitoRiga> report = importa(csv(conflitto, valida));

        assertScartata(report, 2);
        assertEsito("CREATA", report, 3);
        assertSoloNuove(prima, valida);
        attendiUnInvito(valida);
        assertTrue(tokenInviati(altro.email()).isEmpty(), "nessun invito all'email in conflitto");
    }

    // ------------------------------------------------------------------ CA-03: duplicati nel file

    static Stream<Arguments> duplicati() {
        Riga codice = nuovaRiga();
        Riga email = nuovaRiga();
        Riga identica = nuovaRiga();
        Riga terna = nuovaRiga();
        Riga conInvalida = nuovaRiga();
        Riga catena = nuovaRiga();
        Riga ponte = nuovaRiga().conCodice(catena.codice());
        return Stream.of(
                arguments("stesso codice", List.of(codice, nuovaRiga().conCodice(codice.codice()))),
                arguments("stessa email", List.of(email, nuovaRiga().conEmail(email.email()))),
                arguments("righe identiche", List.of(identica, identica)),
                arguments("stesso codice tre volte", List.of(terna, nuovaRiga().conCodice(terna.codice()),
                        nuovaRiga().conCodice(terna.codice()))),
                arguments("una delle due ha anche un campo non valido",
                        List.of(conInvalida, nuovaRiga().conCodice(conInvalida.codice()).conNome(""))),
                arguments("codice con la prima ed email con la terza",
                        List.of(catena, ponte, nuovaRiga().conEmail(ponte.email()))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicati")
    @DisplayName("CA-03 · duplicati nel file: scartate TUTTE le righe coinvolte, importate le altre")
    void duplicatiNelFile(String classe, List<Riga> coinvolte) throws Exception {
        scenario.banco();
        Riga valida = nuovaRiga();
        Istantanea prima = istantanea();
        // la riga valida sta in mezzo: i duplicati non sono per forza consecutivi
        List<Riga> righe = new ArrayList<>(coinvolte);
        righe.add(1, valida);

        Map<Integer, EsitoRiga> report = importa(csv(righe.toArray(Riga[]::new)));

        for (int i = 0; i < righe.size(); i++) {
            if (righe.get(i) == valida) {
                assertEsito("CREATA", report, i + 2);
            } else {
                assertScartata(report, i + 2);
            }
        }
        assertSoloNuove(prima, valida);
        attendiUnInvito(valida);
        for (Riga scartata : coinvolte) {
            assertNull(optConCodice(scartata.codice()), "nessuna riga coinvolta va importata");
            assertTrue(tokenInviati(scartata.email()).isEmpty(), "nessun invito ai duplicati");
        }
    }

    @ParameterizedTest(name = "prima la riga {0}")
    @ValueSource(strings = {"della OPT registrata", "duplicata"})
    @DisplayName("CA-03 · codice di una OPT registrata ripetuto nel file: righe scartate e OPT invariata")
    void duplicatoDiOptRegistrata(String ordine) throws Exception {
        scenario.banco();
        Account opt = optRegistrata();
        Riga aggiornata = rigaDi(opt).conNome("Nome da non applicare");
        Riga doppia = nuovaRiga().conCodice(aggiornata.codice());
        Riga[] righe = ordine.equals("duplicata") ? new Riga[] {doppia, aggiornata} : new Riga[] {aggiornata, doppia};
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(opt.email(), doppia.email()));

        Map<Integer, EsitoRiga> report = importa(csv(righe));

        assertScartata(report, 2);
        assertScartata(report, 3);
        assertEquals(prima, istantanea());
        assertInviiInvariati(invii);
    }
}
