package it.unina.picus.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class RichiestaApiTest {

    private static MockHttpServletRequest richiesta(String percorso, String accept) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", percorso);
        if (accept != null) {
            r.addHeader("Accept", accept);
        }
        return r;
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "application/json",
        "application/problem+json",
        "application/json, text/plain, */*",
        "text/html;q=0.5, application/json"})
    void unAcceptCheSceglieJsonERichiestaDiApi(String accept) {
        assertThat(RichiestaApi.e(richiesta("/opt", accept))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "text/html, application/json;q=0",
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "text/html, application/json",
        "*/*",
        "application/jsonx",
        "non-valido//",
        ""})
    void unAcceptCheNonSceglieJsonEUnaPaginaHtml(String accept) {
        assertThat(RichiestaApi.e(richiesta("/opt", accept))).isFalse();
    }

    @Test
    void senzaAcceptEUnaPaginaHtml() {
        assertThat(RichiestaApi.e(richiesta("/opt", null))).isFalse();
    }

    @Test
    void ilPercorsoApiEUnaChiamataXhrSonoApiQualunqueSiaAccept() {
        assertThat(RichiestaApi.e(richiesta("/api/richieste", "text/html"))).isTrue();

        MockHttpServletRequest xhr = richiesta("/opt", "text/html");
        xhr.addHeader("X-Requested-With", "XMLHttpRequest");
        assertThat(RichiestaApi.e(xhr)).isTrue();
    }

    @Test
    void ilContestoDellApplicazioneNonCambiaIlPercorso() {
        MockHttpServletRequest r = richiesta("/picus/api/richieste", null);
        r.setContextPath("/picus");

        assertThat(RichiestaApi.e(r)).isTrue();
    }
}
