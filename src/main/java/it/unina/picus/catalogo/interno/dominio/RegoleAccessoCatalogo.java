package it.unina.picus.catalogo.interno.dominio;

import it.unina.picus.shared.ControlloAccesso;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

import java.util.function.Predicate;

/**
 * Regole di autorizzazione per la gestione amministrativa del catalogo delle misure (#46).
 *
 * <p>Le politiche sono espresse tramite predicati componibili sull'identità verificata dal
 * server e delegano il diniego a {@link ControlloAccesso}, così da sollevare
 * {@link it.unina.picus.shared.AccessoNegatoException} senza esporre dettagli interni.</p>
 */
public final class RegoleAccessoCatalogo {

    private static final Predicate<IdentitaAutenticata> E_BANCO =
            attore -> attore != null
                    && attore.accountId() != null
                    && attore.ruolo() == RuoloUtente.BANCO;

    private RegoleAccessoCatalogo() {
    }

    /**
     * Verifica che l'attore autenticato possieda il ruolo del Banco di coordinamento.
     *
     * @param attore identità verificata della sessione corrente
     * @throws it.unina.picus.shared.AccessoNegatoException se l'identità è mancante, incompleta
     *                                                      o appartiene a un ruolo diverso da {@link RuoloUtente#BANCO}
     */
    public static void verificaBanco(IdentitaAutenticata attore) {
        ControlloAccesso.richiedi(E_BANCO.test(attore), "Gestione del catalogo riservata al Banco");
    }
}
