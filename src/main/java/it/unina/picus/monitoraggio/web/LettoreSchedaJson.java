package it.unina.picus.monitoraggio.web;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.DatiSchedaOperatore;
import it.unina.picus.monitoraggio.interno.dominio.DatiSchedaNonValidiException;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoSchedaOperatore;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/** Binding dedicato alla scheda. Le regole di deserializzazione degli altri moduli restano indipendenti. */
@Component
class LettoreSchedaJson {

    private static final int MASSIMO_BYTE = 65_536;
    private final ObjectReader lettore;

    /** Prepara il lettore con controllo di tipi, chiavi duplicate e campi sconosciuti. */
    LettoreSchedaJson(JsonMapper json) {
        lettore = json.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build().readerFor(Invio.class);
    }

    /** Legge un oggetto JSON di dimensione limitata e restituisce i soli dati dichiarabili. */
    DatiSchedaOperatore leggi(InputStream corpo) throws IOException {
        byte[] contenuto = corpo.readNBytes(MASSIMO_BYTE + 1);
        if (contenuto.length > MASSIMO_BYTE) {
            throw errore("dati", "La scheda supera la dimensione consentita");
        }
        Invio invio;
        try {
            invio = lettore.readValue(contenuto);
        } catch (JacksonException ex) {
            String campo = ex.getPath().isEmpty() ? null : ex.getPath().getFirst().getPropertyName();
            boolean noto = TracciatoSchedaOperatore.campi().stream().anyMatch(c -> c.nome().equals(campo));
            throw errore(noto ? campo : "dati", "Formato non valido");
        }
        if (invio == null) {
            throw errore("dati", "I dati della scheda sono obbligatori");
        }
        return invio.aDati();
    }

    private static DatiSchedaNonValidiException errore(String campo, String messaggio) {
        return new DatiSchedaNonValidiException(Map.of(campo, messaggio));
    }

    /** Dati ricevuti via HTTP, prima della copia immutabile dei conteggi nel contratto Java. */
    private record Invio(String nome, String cognome, String luogoNascita, LocalDate dataNascita,
                         String codiceFiscale, String qualifica, String tipologiaContratto, String areaLivello,
                         BigDecimal ore, BigDecimal costoOrarioLordo, Map<String, Long> conteggi) {

        DatiSchedaOperatore aDati() {
            if (conteggi == null || conteggi.values().stream().anyMatch(Objects::isNull)) {
                throw errore("conteggi", "Inserisci un oggetto con conteggi interi per categoria");
            }
            return new DatiSchedaOperatore(nome, cognome, luogoNascita, dataNascita, codiceFiscale,
                    qualifica, tipologiaContratto, areaLivello, ore, costoOrarioLordo, conteggi);
        }
    }
}
