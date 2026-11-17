package it.unina.picus.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/* Distinzione fra richieste di API (401 senza redirect) e pagine HTML (302 al login) usata dalle
risposte senza autenticazione valida (#25, #104). Ripreso dal commit {@code f404896}.
*/
@DisplayName("Riconoscimento delle richieste di API")
class RichiestaApiTest {

    @ParameterizedTest(name = "Accept [{0}]: API")
    @ValueSource(strings = {
            "application/json",
            "application/problem+json",
            "application/json, text/plain, */*",
            "text/html;q=0.5, application/json"})
    void acceptCheSceglieJson(String accept) {
        assertTrue(RichiestaApi.e(richiesta("/opt", accept)));
    }

    @ParameterizedTest(name = "Accept [{0}]: pagina HTML")
    @ValueSource(strings = {
            "text/html, application/json;q=0",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "text/html, application/json",
            "*/*",
            "application/jsonx",
            "non-valido//",
            ""})
    void acceptCheNonSceglieJson(String accept) {
        assertFalse(RichiestaApi.e(richiesta("/opt", accept)));
    }

    @Test
    @DisplayName("Senza Accept è una pagina HTML")
    void senzaAccept() {
        assertFalse(RichiestaApi.e(richiesta("/opt", null)));
    }

    @Test
    @DisplayName("Il percorso /api/ e le chiamate XHR sono API qualunque sia Accept")
    void percorsoApiEXhr() {
        assertTrue(RichiestaApi.e(richiesta("/api/richieste", "text/html")));

        MockHttpServletRequest xhr = richiesta("/opt", "text/html");
        xhr.addHeader("X-Requested-With", "XMLHttpRequest");
        assertTrue(RichiestaApi.e(xhr));
    }

    @Test
    @DisplayName("Il contesto dell'applicazione non cambia il percorso considerato")
    void contestoDellApplicazione() {
        MockHttpServletRequest r = richiesta("/picus/api/richieste", null);
        r.setContextPath("/picus");
        assertTrue(RichiestaApi.e(r));

        MockHttpServletRequest pagina = richiesta("/picus/opt", null);
        pagina.setContextPath("/picus");
        assertFalse(RichiestaApi.e(pagina));
    }

    private static MockHttpServletRequest richiesta(String percorso, String accept) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", percorso);
        if (accept != null) {
            r.addHeader("Accept", accept);
        }
        return r;
    }
}