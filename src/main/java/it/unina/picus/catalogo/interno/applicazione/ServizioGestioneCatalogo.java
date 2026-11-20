package it.unina.picus.catalogo.interno.applicazione;

import it.unina.picus.catalogo.api.CatalogoApi;
import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.interno.dominio.CodiceMisuraDuplicatoException;
import it.unina.picus.catalogo.interno.dominio.MisuraDiCatalogo;
import it.unina.picus.catalogo.interno.dominio.MisuraNonTrovataException;
import it.unina.picus.catalogo.interno.dominio.MisuraRepository;
import it.unina.picus.catalogo.interno.dominio.RegoleAccessoCatalogo;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servizio applicativo per la gestione amministrativa delle misure persistenti del catalogo (#163).
 *
 * <p>Applica il pattern <em>Service Layer</em> (GRASP <em>Controller</em> / GoF <em>Facade</em>):
 * delimita le transazioni, verifica il ruolo del Banco tramite {@link RegoleAccessoCatalogo}
 * e orchestra il dominio {@link MisuraDiCatalogo} attraverso {@link MisuraRepository}.</p>
 */
@Service
@Transactional(readOnly = true)
public class ServizioGestioneCatalogo {

    private final MisuraRepository misure;

    /**
     * Costruisce il servizio con la porta repository delle misure.
     *
     * @param misure repository delle misure del catalogo
     */
    public ServizioGestioneCatalogo(MisuraRepository misure) {
        this.misure = misure;
    }

    /**
     * Elenca per il Banco tutte le misure del catalogo, comprese quelle disattivate.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @return misure presenti in catalogo con il rispettivo stato di attivazione
     * @throws it.unina.picus.shared.AccessoNegatoException se l'attore non è il Banco
     * @see CatalogoApi#elencoMisure(IdentitaAutenticata)
     */
    public List<Misura> elencoMisure(IdentitaAutenticata attore) {
        RegoleAccessoCatalogo.verificaBanco(attore);
        return misure.trovaTutte().stream().map(MisuraDiCatalogo::aDto).toList();
    }

    /**
     * Crea una nuova misura attiva dopo aver verificato permessi, validità e unicità del codice.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param dati dati descrittivi e di modalità della nuova misura
     * @return misura registrata completa dell'identificativo stabile
     * @throws it.unina.picus.shared.AccessoNegatoException se l'attore non è il Banco
     * @throws it.unina.picus.catalogo.interno.dominio.DatiMisuraNonValidiException se i dati sono incompleti o invalidi
     * @throws CodiceMisuraDuplicatoException se il codice è già utilizzato nel catalogo
     * @see CatalogoApi#creaMisura(IdentitaAutenticata, DatiMisura)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Misura creaMisura(IdentitaAutenticata attore, DatiMisura dati) {
        RegoleAccessoCatalogo.verificaBanco(attore);
        MisuraDiCatalogo nuova = MisuraDiCatalogo.nuova(dati);
        if (misure.esisteCodice(nuova.codice())) {
            throw new CodiceMisuraDuplicatoException();
        }
        return misure.salvaNuova(nuova).aDto();
    }

    /**
     * Aggiorna i dati o lo stato di attivazione di una misura mantenendone l'identificativo storico.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param misuraId identificativo della misura esistente
     * @param dati nuovi dati della misura
     * @param attiva nuovo stato di attivazione
     * @return misura aggiornata con il medesimo identificativo
     * @throws it.unina.picus.shared.AccessoNegatoException se l'attore non è il Banco
     * @throws MisuraNonTrovataException se la misura è inesistente
     * @throws it.unina.picus.catalogo.interno.dominio.DatiMisuraNonValidiException se i dati sono invalidi
     * @throws CodiceMisuraDuplicatoException se il codice entra in conflitto con un'altra misura
     * @see CatalogoApi#aggiornaMisura(IdentitaAutenticata, long, DatiMisura, boolean)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Misura aggiornaMisura(IdentitaAutenticata attore, long misuraId, DatiMisura dati, boolean attiva) {
        MisuraDiCatalogo esistente = misuraInScrittura(attore, misuraId);
        MisuraDiCatalogo aggiornata = esistente.aggiorna(dati, attiva);
        if (misure.esisteCodiceSuAltraMisura(aggiornata.codice(), misuraId)) {
            throw new CodiceMisuraDuplicatoException();
        }
        return misure.aggiorna(aggiornata).aDto();
    }

    /**
     * Disattiva una misura mantenendo dati e identificativo nello storico.
     *
     * <p>L'operazione è idempotente: se la misura risulta già disattivata restituisce lo stato
     * corrente senza applicare nuove scritture.</p>
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param misuraId identificativo della misura da disattivare
     * @return misura nello stato disattivato
     * @throws it.unina.picus.shared.AccessoNegatoException se l'attore non è il Banco
     * @throws MisuraNonTrovataException se la misura è inesistente
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Misura disattivaMisura(IdentitaAutenticata attore, long misuraId) {
        MisuraDiCatalogo esistente = misuraInScrittura(attore, misuraId);
        if (!esistente.attiva()) {
            return esistente.aDto();
        }
        return misure.aggiorna(esistente.disattiva()).aDto();
    }

    private MisuraDiCatalogo misuraInScrittura(IdentitaAutenticata attore, long misuraId) {
        RegoleAccessoCatalogo.verificaBanco(attore);
        return misure.cercaPerAggiornamento(misuraId).orElseThrow(MisuraNonTrovataException::new);
    }
}
