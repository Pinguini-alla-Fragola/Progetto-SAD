package it.unina.picus.richieste.interno.dominio;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta per i contatti del coordinamento; non espone entità o query JPA. */
public interface ContattiRepository {
    /** Legge il riferimento immutabile senza caricare l'esito prima del lock della pratica. */
    Optional<UUID> richiestaDelContatto(UUID contattoId);
    /** Legge il contatto corrente. */
    Optional<ContattoDisponibilita> cerca(UUID contattoId);
    /** Ricostruisce i tentativi nel loro ordine persistente. */
    List<ContattoDisponibilita> storico(UUID richiestaId);
    /** Verifica i contatti ancora in attesa, sotto il lock della pratica. */
    boolean inAttesa(UUID richiestaId);
    /** Registra un tentativo e rende visibile la riga nella transazione chiamante. */
    ContattoDisponibilita registra(UUID richiestaId, UUID erogatoreId, AmbitoErogazione ambito,
            String nomeMisura, Instant istante);
}
