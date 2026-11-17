package it.unina.picus.richieste.interno.dominio;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi.AccordoADistanza;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Verifica i campi della variante di accordo e produce i valori ammessi in persistenza. */
final class ValidazioneAccordo {
    private static final Pattern TELEFONO = Pattern.compile("\\+?[0-9(). /-]+");
    private static final Instant PRIMA_DATA = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant LIMITE_DATA = Instant.parse("+10000-01-01T00:00:00Z");

    private ValidazioneAccordo() {}

    /**
     * Valida i dettagli e aggrega gli errori per consentire la correzione nello stesso invio.
     *
     * @param accordo dettagli della modalità concordata
     * @return accordo con i campi testuali normalizzati e l’istante alla precisione del database
     * @throws DatiRichiestaNonValidiException se la modalità o uno dei campi è invalido
     */
    static DettagliAccordo valida(DettagliAccordo accordo) {
        Map<String, String> errori = new LinkedHashMap<>();
        DettagliAccordo validato = switch (accordo) {
            case null -> throw new DatiRichiestaNonValidiException(Map.of("modalita", "Seleziona la modalità"));
            case AccordoInPresenza presenza -> validaPresenza(presenza, errori);
            case AccordoADistanza distanza -> validaDistanza(distanza, errori);
        };
        if (!errori.isEmpty()) {
            throw new DatiRichiestaNonValidiException(errori);
        }
        return validato;
    }

    private static AccordoInPresenza validaPresenza(AccordoInPresenza accordo, Map<String, String> errori) {
        Instant dataOra = validaData(accordo.dataOra(), errori);
        String luogo = testo(accordo.luogo(), "luogo", "il luogo", 500, errori);
        String nome = testo(accordo.nomeReferente(), "nomeReferente", "il nome del referente", 255, errori);
        String cognome = testo(accordo.cognomeReferente(), "cognomeReferente", "il cognome del referente", 255, errori);
        String telefono = testo(accordo.telefonoReferente(), "telefonoReferente", "il telefono del referente", 50, errori);
        if (!errori.containsKey("telefonoReferente") && (!TELEFONO.matcher(telefono).matches()
                || telefono.chars().noneMatch(Character::isDigit))) {
            errori.put("telefonoReferente", "Indica un numero di telefono valido");
        }
        return new AccordoInPresenza(dataOra, luogo, nome, cognome, telefono);
    }

    private static AccordoADistanza validaDistanza(AccordoADistanza accordo, Map<String, String> errori) {
        Instant dataOra = validaData(accordo.dataOra(), errori);
        URI link = accordo.link();
        if (link == null || link.getHost() == null || link.getUserInfo() != null
                || (!"https".equalsIgnoreCase(link.getScheme()) && !"http".equalsIgnoreCase(link.getScheme()))
                || link.toString().length() > 2048) {
            errori.put("link", "Indica un link HTTP o HTTPS completo, fino a 2048 caratteri");
        }
        return new AccordoADistanza(dataOra, link);
    }

    private static Instant validaData(Instant data, Map<String, String> errori) {
        if (data == null || data.isBefore(PRIMA_DATA) || !data.isBefore(LIMITE_DATA)) {
            errori.put("dataOra", "Indica una data e un orario validi");
            return null;
        }
        return data.truncatedTo(ChronoUnit.MICROS);
    }

    private static String testo(String valore, String campo, String etichetta, int limite, Map<String, String> errori) {
        String pulito = valore == null ? "" : valore.strip();
        if (pulito.isEmpty() || pulito.length() > limite || pulito.codePoints().anyMatch(Character::isISOControl)) {
            errori.put(campo, "Indica " + etichetta + " in testo semplice, fino a " + limite + " caratteri");
        }
        return pulito;
    }
}
