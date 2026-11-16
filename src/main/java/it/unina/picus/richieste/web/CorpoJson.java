package it.unina.picus.richieste.web;

import java.util.Map;

/** Corpo applicativo letto con controllo delle chiavi duplicate prima del binding. */
record CorpoJson(Map<String, Object> campi) {}
