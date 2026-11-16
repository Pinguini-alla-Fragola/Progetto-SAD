package it.unina.picus.catalogo.api;

import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import java.util.List;

/** Capacità di ricerca dell'offerta, separata dai comandi di amministrazione del catalogo. */
public interface RicercaErogatoriApi {
    /**
     * Cerca tutte le opzioni compatibili per misura e territorio.
     * @param misuraId riferimento alla misura richiesta
     * @param comuneCodice codice del comune della richiesta
     * @return opzioni nello stesso comune, limitrofe o Centro PICUS a distanza
     * @throws java.util.NoSuchElementException se misura o comune non esistono
     * @see CatalogoApi#cercaErogatoriCompatibili(long, String)
     */
    List<ErogatoreCompatibile> cercaErogatoriCompatibili(long misuraId, String comuneCodice);
}
