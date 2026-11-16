package it.unina.picus.richieste.interno.dominio;

import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stato persistito della pratica: una lettura non esegue transizioni. */
public record RichiestaDiMisura(
        UUID id, UUID titolareId, long misuraId, TipoDestinatario tipoDestinatario,
        String comuneCodice, String description, Instant inviataIl, StatoRichiesta stato,
        UUID erogatoreId, DettagliAccordo accordo, UUID riferimentoInvio) {

    /** Crea la pratica nel solo stato ammesso all'invio. */
    public static RichiestaDiMisura inviata(UUID titolareId, DatiInvio dati, Instant istante, UUID riferimento) {
        return new RichiestaDiMisura(UUID.randomUUID(), titolareId, dati.misuraId(), dati.tipoDestinatario(),
                dati.comuneCodice(), dati.description(), istante, StatoRichiesta.CREATA, null, null, riferimento);
    }

    /** Confronta tutti i dati dell'invio, senza confondere richieste distinte con contenuto uguale. */
    public boolean stessoInvio(DatiInvio dati) {
        return misuraId == dati.misuraId() && tipoDestinatario == dati.tipoDestinatario()
                && Objects.equals(comuneCodice, dati.comuneCodice())
                && Objects.equals(description, dati.description());
    }
}
