package it.unina.picus.monitoraggio.interno.dominio;

import java.util.Objects;

/**
 * Singola voce di una versione di categorie del monitoraggio (#41, #181).
 *
 * @param codice codice sintatticamente valido della voce
 * @param descrizione etichetta leggibile esposta nei moduli e nei riepiloghi
 * @param definizione definizione operativa validata dal Banco prima della pubblicazione
 * @param ordine posizione ordinata nel modulo (non negativa)
 */
public record VoceCategoriaVersione(
        String codice,
        String descrizione,
        String definizione,
        int ordine) {

    public VoceCategoriaVersione {
        if (!TracciatoSchedaOperatore.codiceCategoriaValido(codice)) {
            throw new IllegalArgumentException("Codice categoria non valido");
        }
        Objects.requireNonNull(descrizione, "descrizione");
        descrizione = descrizione.trim();
        if (descrizione.isEmpty() || descrizione.length() > 255) {
            throw new IllegalArgumentException("Descrizione della categoria non valida");
        }
        Objects.requireNonNull(definizione, "definizione");
        definizione = definizione.trim();
        if (definizione.isEmpty() || definizione.length() > 1000) {
            throw new IllegalArgumentException("Definizione della categoria non valida");
        }
        if (ordine < 0) {
            throw new IllegalArgumentException("Ordine della categoria non negativo richiesto");
        }
    }
}
