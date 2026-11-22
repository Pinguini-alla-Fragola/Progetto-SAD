package it.unina.picus.catalogo.web;

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

import java.io.IOException;
import java.util.Map;

/**
 * Configura un parser JSON dedicato agli ingressi del modulo Catalogo con controllo rigoroso
 * delle chiavi duplicate, senza alterare i parser degli altri moduli.
 */
@Configuration(proxyBeanMethods = false)
class ConfigurazioneJsonCatalogo implements WebMvcConfigurer {

    private final JsonMapper json;

    /**
     * Crea la configurazione derivando dal mapper condiviso un'istanza con rilevamento stretto
     * dei nomi di proprietà duplicati.
     *
     * @param json mapper JSON configurato da Spring Boot
     */
    ConfigurazioneJsonCatalogo(JsonMapper json) {
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder converters) {
        converters.configureMessageConvertersList(lista -> lista.addFirst(new LettoreCorpoCatalogo(json)));
    }

    /** Converter HTTP riservato alla lettura di {@link CorpoJsonCatalogo}. */
    private static final class LettoreCorpoCatalogo extends AbstractHttpMessageConverter<CorpoJsonCatalogo> {

        private final JsonMapper json;

        LettoreCorpoCatalogo(JsonMapper json) {
            super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
            this.json = json;
        }

        @Override
        protected boolean supports(Class<?> tipo) {
            return tipo == CorpoJsonCatalogo.class;
        }

        @Override
        public boolean canWrite(Class<?> tipo, MediaType mediaType) {
            return false;
        }

        @Override
        protected CorpoJsonCatalogo readInternal(Class<? extends CorpoJsonCatalogo> tipo, HttpInputMessage input)
                throws IOException {
            try {
                Map<String, Object> campi = json.readValue(input.getBody(), new TypeReference<>() {});
                return new CorpoJsonCatalogo(campi);
            } catch (JacksonException ex) {
                // Non propagare il messaggio del parser per evitare di esporre frammenti dell'input.
                throw new HttpMessageNotReadableException("Corpo JSON non valido", input);
            }
        }

        @Override
        protected void writeInternal(CorpoJsonCatalogo corpo, HttpOutputMessage output) {
            throw new HttpMessageNotWritableException("Tipo riservato all'ingresso HTTP");
        }
    }
}
