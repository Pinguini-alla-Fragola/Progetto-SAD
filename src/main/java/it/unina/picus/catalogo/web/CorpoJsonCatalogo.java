package it.unina.picus.catalogo.web;

import java.util.Map;

/**
 * Corpo applicativo delle richieste JSON di Catalogo letto con rilevamento delle chiavi duplicate
 * prima del binding nei DTO.
 *
 * @param campi mappa delle proprietà JSON ricevute dal client, oppure {@code null} per corpo {@code null}
 */
record CorpoJsonCatalogo(Map<String, Object> campi) {
}
