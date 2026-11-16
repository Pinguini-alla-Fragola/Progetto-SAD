package it.unina.picus.richieste.api;

import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.util.List;
import java.util.UUID;

/**
 * Capacità di invio e consultazione della richiesta, indipendente dai comandi di coordinamento.
 * Le operazioni rispettano i contratti omonimi di {@link RichiesteApi}.
 */
public interface InvioRichiesteApi {
    /**
     * Registra l'invio valido in Creata o riconosce un ritento della stessa OPT.
     * @param attore identità verificata della OPT richiedente
     * @param dati dati ammessi del modulo
     * @param riferimentoInvio UUID dell'invio logico, oppure null
     * @return sintesi della richiesta nuova o già registrata
     * @throws SecurityException se l'attore non è una OPT
     * @throws IllegalArgumentException se i dati non sono validi
     * @throws IllegalStateException se il riferimento è associato a dati diversi
     * @see RichiesteApi#invia(IdentitaAutenticata, DatiInvio, UUID)
     */
    RichiestaSintesi invia(IdentitaAutenticata attore, DatiInvio dati, UUID riferimentoInvio);

    /**
     * Elenca le sole richieste della OPT titolare.
     * @param attore identità verificata della OPT
     * @return sintesi ordinate per invio, eventualmente nessuna
     * @throws SecurityException se l'attore non è una OPT
     * @see RichiesteApi#elencoRichiesteOpt(IdentitaAutenticata)
     */
    List<RichiestaSintesi> elencoRichiesteOpt(IdentitaAutenticata attore);

    /**
     * Elenca le richieste aperte in ordine cronologico di arrivo.
     * @param attore identità verificata del Banco
     * @return richieste Creata, Presa in carico e In programmazione
     * @throws SecurityException se l'attore non è il Banco
     * @see RichiesteApi#codaBanco(IdentitaAutenticata)
     */
    List<RichiestaSintesi> codaBanco(IdentitaAutenticata attore);

    /**
     * Consulta la pratica senza transizioni, filtrando i dati per il richiedente.
     * @param attore identità verificata di titolare, Banco o assegnatario
     * @param richiestaId identificativo della pratica
     * @return dettaglio con i soli dati autorizzati
     * @throws SecurityException se l'attore non è autorizzato sulla pratica
     * @throws java.util.NoSuchElementException se la pratica non esiste
     * @see RichiesteApi#consultaRichiesta(IdentitaAutenticata, UUID)
     */
    RichiestaDettaglio consultaRichiesta(IdentitaAutenticata attore, UUID richiestaId);
}
