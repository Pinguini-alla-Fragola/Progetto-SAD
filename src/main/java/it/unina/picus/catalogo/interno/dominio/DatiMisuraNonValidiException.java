package it.unina.picus.catalogo.interno.dominio;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

/**
 * Eccezione di dominio per dati di misura incompleti, non conformi o campi non ammessi.
 *
 * <p>Estende {@link IllegalArgumentException} per rispettare il contratto di
 * {@link it.unina.picus.catalogo.api.CatalogoApi}. I messaggi associati ai campi sono
 * controllati e non riportano mai i valori grezzi ricevuti dal client.</p>
 */
public class DatiMisuraNonValidiException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Messaggi controllati indicizzati per nome del campo.
     *
     * @serial Nomi dei campi ammessi associati ai rispettivi messaggi di correzione.
     */
    private final Map<String, String> campi;

    /**
     * Costruisce l'eccezione con i messaggi di errore sui singoli campi.
     *
     * @param campi mappa non nulla dei campi errati e relativi messaggi controllati
     */
    public DatiMisuraNonValidiException(Map<String, String> campi) {
        this("Controlla i campi della misura", campi);
    }

    /**
     * Costruisce l'eccezione con un messaggio generale e la mappa dei campi errati.
     *
     * @param messaggio messaggio controllato privo di valori inseriti dall'utente
     * @param campi mappa non nulla dei campi errati e relativi messaggi controllati
     */
    protected DatiMisuraNonValidiException(String messaggio, Map<String, String> campi) {
        super(Objects.requireNonNull(messaggio, "messaggio"));
        this.campi = Map.copyOf(Objects.requireNonNull(campi, "campi"));
    }

    /**
     * Restituisce una vista immutabile degli errori per campo.
     *
     * @return mappa immutabile con i nomi dei campi e i messaggi di correzione
     */
    public Map<String, String> campi() {
        return campi;
    }
}
