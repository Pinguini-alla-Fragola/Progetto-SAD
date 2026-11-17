package it.unina.picus.richieste.api;

import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.util.UUID;

/** Capacità del Banco per registrare l'esito definitivo della richiesta. */
public interface EsitoRichiesteApi {

    /**
     * Conferma l'accordo con l'erogatore assegnato e salva insieme lo stato e i dettagli.
     *
     * @param attore identità verificata del Banco
     * @param richiestaId identificativo della richiesta In programmazione
     * @param accordo dettagli concordati per la modalità scelta
     * @return dettaglio della richiesta confermata
     * @throws SecurityException se l'attore non è il Banco
     * @throws RisorsaRichiesteNonTrovataException se la richiesta non esiste
     * @throws DatiRichiestaNonValidiException se i dati sono incompleti o la modalità è incompatibile
     * @throws ConflittoRichiestaException se la richiesta non è In programmazione con un erogatore assegnato
     * @see RichiesteApi#confermaAccordo(IdentitaAutenticata, UUID, DettagliAccordo)
     */
    RichiestaDettaglio confermaAccordo(IdentitaAutenticata attore, UUID richiestaId, DettagliAccordo accordo);

    /**
     * Registra la decisione del Banco quando la ricerca si conclude senza soluzioni praticabili.
     *
     * @param attore identità verificata del Banco
     * @param richiestaId identificativo della richiesta Presa in carico o In programmazione
     * @return sintesi della richiesta Rifiutata
     * @throws SecurityException se l'attore non è il Banco
     * @throws RisorsaRichiesteNonTrovataException se la richiesta non esiste
     * @throws ConflittoRichiestaException se lo stato è incompatibile o un contatto è ancora in attesa
     * @see RichiesteApi#rifiutaRichiesta(IdentitaAutenticata, UUID)
     */
    RichiestaSintesi rifiutaRichiesta(IdentitaAutenticata attore, UUID richiestaId);
}
