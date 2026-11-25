package it.unina.picus.utenze.interno.applicazione;

import java.util.UUID;

import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;

/**
 * Esito della creazione: l'account nasce sempre in attesa di attivazione.
 *
 * @param id identificativo dell'account
 * @param nomeVisualizzato nome e cognome mostrati nelle liste
 * @param email indirizzo a cui va consegnato il link
 * @param invito link di attivazione già salvato, da consegnare dopo il commit; non va mai mostrato al Banco
 */
public record OperatoreCreato(UUID id, String nomeVisualizzato, String email, DatiInvioAttivazione invito) {}
