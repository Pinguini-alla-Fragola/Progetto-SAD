package it.unina.picus.richieste.web;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Parser dedicato agli ingressi delle richieste; non cambia i parser degli altri moduli. */
@Configuration(proxyBeanMethods = false)
class ConfigurazioneJsonRichieste implements WebMvcConfigurer {
    private final Lettore lettore;

    ConfigurazioneJsonRichieste(JsonMapper json) {
        this.lettore = new Lettore(json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder converters) {
        converters.configureMessageConvertersList(lista -> lista.addFirst(lettore));
    }

    /** Legge il corpo facoltativo dei comandi che controllano l'accesso prima del binding. */
    CorpoJson leggiFacoltativo(HttpServletRequest richiesta) {
        var input = new ServletServerHttpRequest(richiesta);
        try {
            var corpo = new BufferedInputStream(input.getBody());
            corpo.mark(1);
            if (corpo.read() == -1) {
                return null;
            }
            corpo.reset();
            MediaType tipo = input.getHeaders().getContentType();
            if (tipo == null || !lettore.canRead(CorpoJson.class, tipo)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
            return lettore.leggi(corpo, input);
        } catch (IOException ex) {
            throw new HttpMessageNotReadableException("Corpo JSON non valido", input);
        }
    }

    private static final class Lettore extends AbstractHttpMessageConverter<CorpoJson> {
        private final JsonMapper json;

        Lettore(JsonMapper json) {
            super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
            this.json = json;
        }

        @Override
        protected boolean supports(Class<?> tipo) {
            return tipo == CorpoJson.class;
        }

        @Override
        public boolean canWrite(Class<?> tipo, MediaType mediaType) {
            return false;
        }

        @Override
        protected CorpoJson readInternal(Class<? extends CorpoJson> tipo, HttpInputMessage input) throws IOException {
            return leggi(input.getBody(), input);
        }

        private CorpoJson leggi(InputStream corpo, HttpInputMessage input) {
            try {
                Map<String, Object> campi = json.readValue(corpo, new TypeReference<>() {});
                return new CorpoJson(campi);
            } catch (JacksonException ex) {
                // Non conservare messaggi/cause del parser: possono contenere valori del corpo.
                throw new HttpMessageNotReadableException("Corpo JSON non valido", input);
            }
        }

        @Override
        protected void writeInternal(CorpoJson corpo, HttpOutputMessage output) {
            throw new HttpMessageNotWritableException("Tipo riservato all'ingresso HTTP");
        }
    }
}
