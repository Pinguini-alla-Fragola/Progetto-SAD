package it.unina.picus.shared;

import java.util.List;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Distingue le richieste di API da quelle di pagine HTML, per rispondere senza autenticazione valida
 * nel modo giusto: le pagine HTML rimandano al login (302), le API ricevono {@code 401} senza redirect.
 *
 * <p>Una richiesta è di API se il percorso è sotto {@code /api/}, se è una chiamata XHR
 * ({@code X-Requested-With: XMLHttpRequest}) o se l'intestazione {@code Accept} preferisce JSON
 * ({@code application/json} o un tipo {@code +json}, con qualità maggiore di zero e maggiore di quella
 * di HTML). Il carattere jolly, {@code Accept} assente o non valido e le richieste del browser sono pagine HTML.</p>
 */
public final class RichiestaApi {

    private RichiestaApi() {
    }

    public static boolean e(HttpServletRequest request) {
        String percorso = request.getRequestURI().substring(request.getContextPath().length());
        return percorso.startsWith("/api/")
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || preferisceJson(request.getHeader("Accept"));
    }

    static boolean preferisceJson(String accept) {
        if (accept == null || accept.isBlank()) {
            return false;
        }
        List<MediaType> tipi;
        try {
            tipi = MediaType.parseMediaTypes(accept);
        } catch (InvalidMediaTypeException ex) {
            return false;
        }
        double json = 0;
        double html = 0;
        for (MediaType tipo : tipi) {
            double qualita = tipo.getQualityValue();
            if (json(tipo)) {
                json = Math.max(json, qualita);
            } else if (html(tipo)) {
                html = Math.max(html, qualita);
            }
        }
        return json > 0 && json > html;
    }

    private static boolean json(MediaType tipo) {
        return "application".equals(tipo.getType())
                && ("json".equals(tipo.getSubtype()) || tipo.getSubtype().endsWith("+json"));
    }

    private static boolean html(MediaType tipo) {
        return ("text".equals(tipo.getType()) && "html".equals(tipo.getSubtype()))
                || ("application".equals(tipo.getType()) && "xhtml+xml".equals(tipo.getSubtype()));
    }
}
