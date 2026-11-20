package it.unina.picus.utenze.interno.dominio;

/**
 * Ciò che il modulo Utenze deve sapere dal Catalogo. È una porta: il dominio la usa senza sapere
 * chi la realizza (vedi l'adattatore in {@code interno.integrazioni}).
 */
public interface RiferimentiCatalogo {

    boolean comuneEsiste(String codice);

    boolean misuraAttiva(long misuraId);
}
