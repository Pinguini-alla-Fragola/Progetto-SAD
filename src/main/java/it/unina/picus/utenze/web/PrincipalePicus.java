package it.unina.picus.utenze.web;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/** Identità conservata nella sessione HTTP: solo ID e ruolo, nessun dato personale né credenziale. */
record PrincipalePicus(UUID accountId, RuoloUtente ruolo) implements Principal, Serializable {

    @Override
    public String getName() {
        return accountId.toString();
    }
}
