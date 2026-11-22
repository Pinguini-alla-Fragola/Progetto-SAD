package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.List;

/**
 * Richiesta del Banco di inviare una comunicazione, con i campi così come arrivano dal modulo.
 *
 * @param titolo oggetto facoltativo
 * @param contenuto testo della comunicazione
 * @param destinatariTipo nome del tipo di destinatari scelto
 * @param gruppo identificativi degli account scelti, solo per l'invio a un gruppo
 */
public record ComandoInvioComunicazione(String titolo, String contenuto, String destinatariTipo, List<String> gruppo) {}
