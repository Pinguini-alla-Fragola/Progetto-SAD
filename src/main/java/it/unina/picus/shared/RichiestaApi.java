package it.unina.picus.shared;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Distingue le richieste di API da quelle di pagine HTML, per rispondere senza autenticazione valida
 * nel modo giusto: le pagine HTML rimandano al login (302), le API ricevono {@code 401} senza redirect.
 *
 * <p>Una richiesta è di API se il percorso è sotto {@code /api/}, se è una chiamata XHR
 * ({@code X-Requested-With: XMLHttpRequest}) o se accetta JSON. Tutto il resto è una pagina HTML.
 * Il {@code 403} per permesso mancante con autenticazione valida vale per entrambe.</p>
 */
public final class RichiestaApi {

    private RichiestaApi() {
    }

    public static boolean e(HttpServletRequest request) {
        String percorso = request.getRequestURI().substring(request.getContextPath().length());
        String accept = request.getHeader("Accept");
        return percorso.startsWith("/api/")
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || (accept != null && accept.contains("application/json"));
    }
}
