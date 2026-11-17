package it.unina.picus.richieste.interno.dominio;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi.AccordoADistanza;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Rappresenta lo stato persistito della pratica. Una lettura non esegue transizioni.
 *
 * <p>Secondo il principio GRASP <em>Information Expert</em>, la pratica usa il proprio stato
 * e l'assegnazione registrata per decidere se può accettare un esito definitivo.
 */
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
        return transita(StatoRichiesta.PRESA_IN_CARICO, erogatoreId, erogatoreAmbito, accordo);
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
        return transita(StatoRichiesta.IN_PROGRAMMAZIONE, accountId, ambito, accordo);
    }

    /**
     * Conferma la pratica in programmazione e vi associa l'accordo validato.
     *
     * @param dettagli accordo proposto per l’erogatore assegnato
     * @return pratica Confermata con i dettagli definitivi
     * @throws ConflittoRichiestaException se la pratica non è in programmazione o manca l’erogatore
     * @throws DatiRichiestaNonValidiException se i dati sono incompleti o incompatibili
     */
    public RichiestaDiMisura conferma(DettagliAccordo dettagli) {
        if (stato != StatoRichiesta.IN_PROGRAMMAZIONE || erogatoreId == null) {
            throw new ConflittoRichiestaException("Serve una richiesta In programmazione con erogatore assegnato");
        }
        var accordoValidato = ValidazioneAccordo.valida(dettagli);
        boolean assegnazioneADistanza = erogatoreAmbito == AmbitoErogazione.CENTRO_PICUS_A_DISTANZA;
        if (assegnazioneADistanza != (accordoValidato instanceof AccordoADistanza)) {
            throw new DatiRichiestaNonValidiException(Map.of("modalita",
                    "La modalità dell'accordo deve corrispondere all'ambito dell'assegnazione"));
        }
        return transita(StatoRichiesta.CONFERMATA, erogatoreId, erogatoreAmbito, accordoValidato);
    }

    /**
     * Chiude la pratica con un rifiuto definitivo deciso dal Banco.
     *
     * @return pratica Rifiutata con la storia dell’assegnazione conservata
     * @throws ConflittoRichiestaException se la pratica non è in ricerca o in programmazione
     */
    public RichiestaDiMisura rifiuta() {
        if (stato != StatoRichiesta.PRESA_IN_CARICO && stato != StatoRichiesta.IN_PROGRAMMAZIONE) {
            throw new ConflittoRichiestaException("Serve una richiesta Presa in carico o In programmazione");
        }
        return transita(StatoRichiesta.RIFIUTATA, erogatoreId, erogatoreAmbito, null);
    }

    private RichiestaDiMisura transita(StatoRichiesta nuovoStato, UUID accountId,
            AmbitoErogazione ambito, DettagliAccordo dettagli) {
        return new RichiestaDiMisura(id, titolareId, misuraId, tipoDestinatario, comuneCodice, description,
                inviataIl, nuovoStato, accountId, dettagli, riferimentoInvio, ambito);
    }

    /** Confronta tutti i dati dell'invio, senza confondere richieste distinte con contenuto uguale. */
    public boolean stessoInvio(DatiInvio dati) {
        return misuraId == dati.misuraId() && tipoDestinatario == dati.tipoDestinatario()
                && Objects.equals(comuneCodice, dati.comuneCodice())
                && Objects.equals(description, dati.description());
    }
}
