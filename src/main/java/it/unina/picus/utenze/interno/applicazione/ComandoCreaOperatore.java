package it.unina.picus.utenze.interno.applicazione;

import java.util.List;

/** Richiesta del Banco di creare un Operatore, con i campi così come arrivano dal modulo. */
public record ComandoCreaOperatore(String nome, String cognome, String codiceFiscale, String email,
        String telefono, String comuneCodice, String tipologia, List<Long> misure, boolean centroPicus) {}
