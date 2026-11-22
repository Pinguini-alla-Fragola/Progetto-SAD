package it.unina.picus.comunicazioni.interno.dominio;

/** Esito della lettura degli account del gruppo ricevuti dal modulo. */
public enum LetturaGruppo {
    /** Tutti gli identificativi sono validi. */
    LEGGIBILE,
    /** Almeno un identificativo manca o non è un UUID. */
    ILLEGGIBILE,
    /** Gli elementi ricevuti superano il limite: non sono stati nemmeno convertiti. */
    TROPPO_GRANDE
}
