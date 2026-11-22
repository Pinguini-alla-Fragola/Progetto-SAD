package it.unina.picus.utenze.interno.dominio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rifiuta i caratteri di controllo, come il carattere nullo, in tutti i campi di testo. Non servono in un nome o
 * in un indirizzo e il database non li accetta: senza questa regola il salvataggio fallirebbe con un errore
 * generico invece di indicare il campo da correggere. È la prima regola, così le altre lavorano su testo pulito.
 */
public final class RegolaCaratteri implements RegolaDatiOperatore {

    @Override
    public void verifica(DatiOperatore dati, Errori errori) {
        Map<String, String> campi = new LinkedHashMap<>();
        campi.put(CampoOperatore.NOME, dati.nome());
        campi.put(CampoOperatore.COGNOME, dati.cognome());
        campi.put(CampoOperatore.CODICE_FISCALE, dati.codiceFiscale());
        campi.put(CampoOperatore.EMAIL, dati.email());
        campi.put(CampoOperatore.TELEFONO, dati.telefono());
        campi.put(CampoOperatore.COMUNE, dati.comuneCodice());
        campi.put(CampoOperatore.TIPOLOGIA, dati.tipologia());
        campi.forEach((campo, valore) -> {
            if (contieneCaratteriDiControllo(valore)) {
                errori.aggiungi(campo, "Il campo contiene caratteri non ammessi");
            }
        });
    }

    private static boolean contieneCaratteriDiControllo(String valore) {
        return valore != null && valore.chars().anyMatch(Character::isISOControl);
    }
}
