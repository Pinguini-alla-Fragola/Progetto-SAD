package it.unina.picus.comunicazioni.api;

import java.util.UUID;

/** Porta di verifica dei riferimenti, fornita dal modulo proprietario del contatto. */
public interface DestinatariAvvisi {
    /**
     * Verifica che pratica e destinatario coincidano con quelli immutabili del contatto.
     * @param richiestaId pratica indicata nell'avviso
     * @param contattoId contatto che genera l'avviso
     * @param destinatarioId account a cui consegnare
     * @return vero solo per riferimenti coerenti con il contatto esistente
     */
    boolean corrisponde(UUID richiestaId, UUID contattoId, UUID destinatarioId);
}
