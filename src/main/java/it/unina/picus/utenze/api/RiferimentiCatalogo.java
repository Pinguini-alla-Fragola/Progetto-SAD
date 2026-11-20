package it.unina.picus.utenze.api;

/**
 * Porta con cui il modulo Utenze verifica i riferimenti al Catalogo, fornita dal modulo Catalogo.
 * Così Utenze non dipende dal Catalogo: è il Catalogo a implementarla, come per {@code DestinatariAvvisi}.
 */
public interface RiferimentiCatalogo {

    /**
     * Verifica che il comune scelto sia presente nel catalogo.
     *
     * @param codice codice del comune scelto
     * @return vero se il comune esiste nel catalogo
     */
    boolean comuneEsiste(String codice);

    /**
     * Verifica che la misura scelta esista e sia attiva. Una misura disattivata resta solo per lo storico.
     *
     * @param misuraId identificativo della misura scelta
     * @return vero se la misura esiste ed è attiva
     */
    boolean misuraAttiva(long misuraId);
}
