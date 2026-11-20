package it.unina.picus.utenze.api;

/**
 * Porta con cui il modulo Utenze verifica i riferimenti al Catalogo, fornita dal modulo Catalogo.
 * Così Utenze non dipende dal Catalogo: è il Catalogo a implementarla, come per {@code DestinatariAvvisi}.
 */
public interface RiferimentiCatalogo {

    /**
     * @param codice codice del comune scelto
     * @return vero se il comune esiste nel catalogo
     */
    boolean comuneEsiste(String codice);

    /**
     * @param misuraId identificativo della misura scelta
     * @return vero se la misura esiste ed è attiva; una misura disattivata resta solo per lo storico
     */
    boolean misuraAttiva(long misuraId);
}
