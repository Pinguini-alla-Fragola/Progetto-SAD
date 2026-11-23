package it.unina.picus.utenze.interno.dominio;

import it.unina.picus.shared.ControlloAccesso;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/** Chi può gestire gli account degli Operatori. */
public final class RegoleAccessoUtenze {

    private RegoleAccessoUtenze() {
    }

    public static void verificaBanco(IdentitaAutenticata attore) {
        boolean banco = attore != null && attore.accountId() != null && attore.ruolo() == RuoloUtente.BANCO;
        ControlloAccesso.richiedi(banco, "Gestione degli Operatori riservata al Banco");
    }
}
