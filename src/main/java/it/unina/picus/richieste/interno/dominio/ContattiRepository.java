package it.unina.picus.richieste.interno.dominio;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettaglioContatto;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta di persistenza dei contatti di disponibilità. */
public interface ContattiRepository {
    /**
     * Legge pratica e destinatario prima del lock. L'esito viene caricato dopo il lock.
     *
     * @param contattoId identificativo del contatto
     * @return riferimento al contatto, se presente
     */
    Optional<Riferimento> cercaRiferimento(UUID contattoId);

    /**
     * Carica la proiezione con i dati necessari a valutare la disponibilità.
     *
     * @param contattoId identificativo del contatto
     * @return dettaglio minimo del contatto, se presente
     */
    Optional<DettaglioContatto> cercaDettaglio(UUID contattoId);

    /**
     * Registra la risposta mentre il chiamante mantiene il lock sulla pratica.
     *
     * @param contattoId identificativo del contatto in attesa
     * @param disponibile esito comunicato dal destinatario
     * @return contatto con l'esito registrato
     * @throws RisorsaRichiesteNonTrovataException se il contatto non esiste
     * @throws ConflittoRichiestaException se il contatto ha già un esito definitivo
     */
    ContattoDisponibilita rispondi(UUID contattoId, boolean disponibile);

    /** Legge il contatto corrente. */
    Optional<ContattoDisponibilita> cerca(UUID contattoId);
    /** Ricostruisce i tentativi nel loro ordine persistente. */
    List<ContattoDisponibilita> storico(UUID richiestaId);
    /** Verifica i contatti ancora in attesa, sotto il lock della pratica. */
    boolean inAttesa(UUID richiestaId);
    /** Registra un tentativo e rende visibile la riga nella transazione chiamante. */
    ContattoDisponibilita registra(UUID richiestaId, UUID erogatoreId, AmbitoErogazione ambito,
            String nomeMisura, Instant istante);

    /**
     * Identificativi immutabili utili per autorizzare il destinatario e bloccare la pratica.
     *
     * @param richiestaId pratica associata al contatto
     * @param erogatoreId account destinatario
     */
    record Riferimento(UUID richiestaId, UUID erogatoreId) {}
}
