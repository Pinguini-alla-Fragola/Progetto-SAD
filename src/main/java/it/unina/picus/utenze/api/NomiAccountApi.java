package it.unina.picus.utenze.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fornisce i nomi degli account già individuati dai casi d'uso degli altri moduli. */
public interface NomiAccountApi {

    /**
     * Legge in blocco il nome visualizzato degli account indicati, anche se revocati.
     *
     * @param accountIds identificativi degli account da consultare
     * @return nomi per identificativo, senza voci per gli account sconosciuti
     */
    Map<UUID, String> nomiVisualizzati(Set<UUID> accountIds);
}
