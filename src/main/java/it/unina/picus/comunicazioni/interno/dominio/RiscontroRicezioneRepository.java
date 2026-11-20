package it.unina.picus.comunicazioni.interno.dominio;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Porta di persistenza dei destinatari registrati all'invio e dei loro riscontri di ricezione. */
public interface RiscontroRicezioneRepository {

    /**
     * Legge gli account destinatari registrati al momento dell'invio della comunicazione.
     *
     * @param comunicazioneId identificativo della comunicazione
     * @return {@code Optional.empty()} se la comunicazione manca, altrimenti l'insieme dei
     *         destinatari, che può essere vuoto
     */
    Optional<Set<UUID>> cercaDestinatari(UUID comunicazioneId);

    /**
     * Registra atomicamente l'istante della prima conferma su una riga di destinazione esistente.
     * Una conferma ripetuta o concorrente restituisce l'istante già persistito.
     * L'operazione richiede una transazione di scrittura aperta dal chiamante.
     *
     * @param comunicazioneId identificativo della comunicazione
     * @param destinatarioId account destinatario ricavato dall'identità verificata
     * @param istante istante rilevato dal server per questa conferma
     * @return istante persistito della prima conferma, oppure vuoto se manca la riga di destinazione
     */
    Optional<Instant> registraPrimaConferma(UUID comunicazioneId, UUID destinatarioId, Instant istante);
}
