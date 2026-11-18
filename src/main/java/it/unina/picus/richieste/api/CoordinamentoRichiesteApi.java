package it.unina.picus.richieste.api;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import java.util.List;
import java.util.UUID;

/** Capacità del Banco per coordinare le richieste e registrarne l'esito. */
public interface CoordinamentoRichiesteApi {
    /**
     * Consulta il contatto della pratica dal perimetro autorizzato del Banco.
     * @param attore identità corrente del Banco
     * @param richiestaId pratica a cui deve appartenere il contatto
     * @param contattoId contatto da leggere
     * @return contatto e relativo esito
     */
    ContattoDisponibilita consultaContattoBanco(IdentitaAutenticata attore, UUID richiestaId, UUID contattoId);
    /**
     * Prende esplicitamente in carico una richiesta Creata.
     * @param attore identità verificata del Banco
     * @param richiestaId pratica in coda
     * @return sintesi in Presa in carico
     * @see RichiesteApi#prendiInCarico(IdentitaAutenticata, UUID)
     */
    RichiestaSintesi prendiInCarico(IdentitaAutenticata attore, UUID richiestaId);

    /**
     * Ordina tutte le opzioni compatibili senza cambiare lo stato della pratica.
     * @param attore identità verificata del Banco
     * @param richiestaId pratica Presa in carico
     * @return candidati ordinati per territorio, tipo e carico
     * @see RichiesteApi#cercaErogatoriOrdinati(IdentitaAutenticata, UUID)
     */
    List<Candidato> cercaErogatoriOrdinati(IdentitaAutenticata attore, UUID richiestaId);

    /**
     * Registra un solo contatto in attesa e il suo avviso al destinatario.
     * @param attore identità verificata del Banco
     * @param richiestaId pratica Presa in carico
     * @param erogatoreId account scelto
     * @param ambito opzione compatibile scelta
     * @return contatto in attesa
     * @see RichiesteApi#interpellaErogatore(IdentitaAutenticata, UUID, UUID, AmbitoErogazione)
     */
    ContattoDisponibilita interpellaErogatore(IdentitaAutenticata attore, UUID richiestaId,
            UUID erogatoreId, AmbitoErogazione ambito);

    /**
     * Assegna l'erogatore ancora abilitato che ha risposto positivamente.
     * @param attore identità verificata del Banco
     * @param contattoId contatto che attesta disponibilità
     * @return sintesi in In programmazione
     * @see RichiesteApi#assegnaErogatore(IdentitaAutenticata, UUID)
     */
    RichiestaSintesi assegnaErogatore(IdentitaAutenticata attore, UUID contattoId);

    /**
     * Conferma l'accordo per la richiesta e l'erogatore assegnato.
     * @param attore identità verificata del Banco
     * @param richiestaId identificativo della richiesta
     * @param accordo dettagli concordati
     * @return dettaglio della richiesta confermata
     * @see RichiesteApi#confermaAccordo(IdentitaAutenticata, UUID, DettagliAccordo)
     */
    RichiestaDettaglio confermaAccordo(IdentitaAutenticata attore, UUID richiestaId, DettagliAccordo accordo);

    /**
     * Registra il rifiuto definitivo della richiesta.
     * @param attore identità verificata del Banco
     * @param richiestaId identificativo della richiesta
     * @return sintesi della richiesta rifiutata
     * @see RichiesteApi#rifiutaRichiesta(IdentitaAutenticata, UUID)
     */
    RichiestaSintesi rifiutaRichiesta(IdentitaAutenticata attore, UUID richiestaId);
}
