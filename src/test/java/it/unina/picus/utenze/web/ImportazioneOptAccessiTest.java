package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import it.unina.picus.shared.ClientHttp;
import it.unina.picus.shared.ClientHttp.Csrf;
import it.unina.picus.shared.ClientHttp.SessioneAnonima;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * Ruoli non abilitati e richieste senza sessione valida. Le richieste portano sempre un token CSRF, così
 * l'esito dipende dall'identità.
 */
@DisplayName("#24 CA-07 · Accesso all'importazione")
class ImportazioneOptAccessiTest extends ImportazioneOptBlackBox {

    /** Classi di file: con un attore abilitato produrrebbero esiti diversi. */
    enum File { NUOVA_OPT, AGGIORNAMENTO, NON_VALIDO }

    static Stream<Arguments> ruoloPerFile() {
        return Stream.of(RuoloUtente.values())
                .flatMap(ruolo -> Stream.of(File.values()).map(file -> Arguments.of(ruolo, file)));
    }

    @ParameterizedTest(name = "{0}, file {1}")
    @MethodSource("ruoloPerFile")
    @DisplayName("CA-07 · OPT e Operatore ricevono 403 senza scritture per ogni file; il Banco è ammesso")
    void ruolo(RuoloUtente ruolo, File file) throws Exception {
        Account attore = switch (ruolo) {
            case OPT -> optRegistrata();
            case OPERATORE -> scenario.operatore(TipoErogatore.INTERNO_BANCO);
            case BANCO -> scenario.banco();
        };
        Account registrata = optRegistrata();
        Riga nuova = nuovaRiga();
        byte[] contenuto = contenuto(file, registrata, nuova);
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(registrata.email(), nuova.email()));

        MockHttpServletResponse risposta = carica(attore, contenuto);

        if (ruolo == RuoloUtente.BANCO) {
            // controllo positivo: lo stesso file, caricato dal Banco, viene elaborato
            assertEquals(file == File.NON_VALIDO ? 400 : 200, risposta.getStatus());
            if (file == File.NUOVA_OPT) {
                attendiUnInvito(nuova);
            }
            return;
        }
        ClientHttp.assertDiniegoSenzaCorpo(risposta);
        assertEquals(prima, istantanea(), "nessun dato creato o modificato");
        assertInviiInvariati(invii);
    }

    enum Sessione { ANONIMA, SCADUTA, ACCOUNT_REVOCATO }

    static Stream<Arguments> sessionePerFile() {
        return Stream.of(Sessione.values())
                .flatMap(sessione -> Stream.of(File.values()).map(file -> Arguments.of(sessione, file)));
    }

    @ParameterizedTest(name = "{0}, file {1}")
    @MethodSource("sessionePerFile")
    @DisplayName("CA-07 · senza sessione valida: 401 senza redirect né dati, nessuna scrittura")
    void senzaSessioneValida(Sessione tipo, File file) throws Exception {
        Account banco = scenario.banco();
        Account registrata = optRegistrata();
        Riga nuova = nuovaRiga();
        byte[] contenuto = contenuto(file, registrata, nuova);
        MockHttpSession sessione;
        Csrf csrf;
        if (tipo == Sessione.ANONIMA) {
            SessioneAnonima anonima = http.sessioneAnonima();
            sessione = anonima.sessione();
            csrf = anonima.csrf();
        } else {
            sessione = http.accedi(banco);
            csrf = http.csrf(sessione);
            // la sessione del Banco era valida: il file è rifiutato nel merito, non per l'accesso
            assertEquals(400, esegui(caricamento(sessione, csrf, new byte[0])).getStatus());
            if (tipo == Sessione.SCADUTA) {
                sessione.invalidate();
            } else {
                scenario.revoca(banco);
            }
        }
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(registrata.email(), nuova.email()));

        MockHttpServletResponse risposta = esegui(caricamento(sessione, csrf, contenuto));

        ClientHttp.assertNonAutenticatoSenzaDati(risposta);
        assertEquals(prima, istantanea(), "nessun dato creato o modificato");
        assertInviiInvariati(invii);
    }

    private byte[] contenuto(File file, Account registrata, Riga nuova) {
        String testo = switch (file) {
            case NUOVA_OPT -> csv(nuova);
            case AGGIORNAMENTO -> csv(rigaDi(registrata).conNome("Nome da non applicare").conComune(ALTRO_COMUNE));
            case NON_VALIDO -> "colonna\nvalore\n";
        };
        return testo.getBytes(StandardCharsets.UTF_8);
    }
}
