package it.unina.picus.richieste.web;

import java.io.IOException;
import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Parser dedicato agli ingressi delle richieste; non cambia i parser degli altri moduli. */
@Configuration(proxyBeanMethods = false)
class ConfigurazioneJsonRichieste implements WebMvcConfigurer {
    private final JsonMapper json;

    ConfigurazioneJsonRichieste(JsonMapper json) {
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder converters) {
        converters.configureMessageConvertersList(lista -> lista.addFirst(new Lettore(json)));
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
            try {
                Map<String, Object> campi = json.readValue(input.getBody(), new TypeReference<>() {});
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
