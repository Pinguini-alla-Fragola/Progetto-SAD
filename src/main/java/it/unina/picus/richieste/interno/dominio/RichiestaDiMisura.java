package it.unina.picus.richieste.interno.dominio;

import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stato persistito della pratica: una lettura non esegue transizioni. */
public record RichiestaDiMisura(
        UUID id, UUID titolareId, long misuraId, TipoDestinatario tipoDestinatario,
        String comuneCodice, String description, Instant inviataIl, StatoRichiesta stato,
        UUID erogatoreId, DettagliAccordo accordo, UUID riferimentoInvio, AmbitoErogazione erogatoreAmbito) {

    /** Crea la pratica nel solo stato ammesso all'invio. */
    public static RichiestaDiMisura inviata(UUID titolareId, DatiInvio dati, Instant istante, UUID riferimento) {
        return new RichiestaDiMisura(UUID.randomUUID(), titolareId, dati.misuraId(), dati.tipoDestinatario(),
                dati.comuneCodice(), dati.description(), istante, StatoRichiesta.CREATA, null, null, riferimento, null);
    }

    /** Esegue la presa in carico esplicita del Banco. */
    public RichiestaDiMisura prendiInCarico() {
        if (stato != StatoRichiesta.CREATA) {
            throw new ConflittoRichiestaException("La richiesta non è Creata");
        }
        return conAssegnazione(StatoRichiesta.PRESA_IN_CARICO, erogatoreId, erogatoreAmbito);
    }

    /** Impone lo stato previsto per contatto e assegnazione. */
    public void verificaPresaInCarico() {
        if (stato != StatoRichiesta.PRESA_IN_CARICO) {
            throw new ConflittoRichiestaException("La richiesta non è Presa in carico");
        }
    }

    /** Registra insieme assegnatario, ambito e passaggio In programmazione. */
    public RichiestaDiMisura assegna(UUID accountId, AmbitoErogazione ambito) {
        verificaPresaInCarico();
        Objects.requireNonNull(accountId, "Assegnatario obbligatorio");
        Objects.requireNonNull(ambito, "Ambito obbligatorio");
        return conAssegnazione(StatoRichiesta.IN_PROGRAMMAZIONE, accountId, ambito);
    }

    private RichiestaDiMisura conAssegnazione(StatoRichiesta nuovoStato, UUID accountId, AmbitoErogazione ambito) {
        return new RichiestaDiMisura(id, titolareId, misuraId, tipoDestinatario, comuneCodice, description,
                inviataIl, nuovoStato, accountId, accordo, riferimentoInvio, ambito);
    }

    /** Confronta tutti i dati dell'invio, senza confondere richieste distinte con contenuto uguale. */
    public boolean stessoInvio(DatiInvio dati) {
        return misuraId == dati.misuraId() && tipoDestinatario == dati.tipoDestinatario()
                && Objects.equals(comuneCodice, dati.comuneCodice())
                && Objects.equals(description, dati.description());
    }
}
