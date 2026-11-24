package it.unina.picus.richieste.interno.dominio;

import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.Map;

/** Porta di persistenza del modulo Richieste. */
public interface RichiestaRepository {
    /**
     * Registra atomicamente un nuovo invio con una misura attiva oppure restituisce l'invio
     * già presente con la stessa chiave, anche se la sua misura è stata disattivata.
     * Richiede una transazione di scrittura a isolamento READ COMMITTED.
     *
     * @param richiesta dati del nuovo invio
     * @return richiesta appena registrata o invio già presente
     * @throws MisuraNonSelezionabileException se un nuovo invio usa una misura non selezionabile
     */
    RichiestaDiMisura registra(RichiestaDiMisura richiesta);

    /** Segnala che la misura non è selezionabile al momento della registrazione. */
    final class MisuraNonSelezionabileException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** Cerca una pratica senza modificarla. */
    Optional<RichiestaDiMisura> cercaPerId(UUID id);

    /**
     * Verifica l'esistenza di una pratica senza caricarne i dettagli.
     *
     * @param id identificativo della pratica
     * @return se la pratica esiste
     */
    boolean esiste(UUID id);

    /** Rilegge e blocca la pratica fino alla fine della transazione chiamante. */
    Optional<RichiestaDiMisura> cercaPerAggiornamento(UUID id);

    /** Salva la transizione della pratica già bloccata nella stessa transazione. */
    void aggiorna(RichiestaDiMisura richiesta);

    /** Conta gli appuntamenti confermati futuri per erogatore con una query aggregata. */
    Map<UUID, Integer> carichiConfermati(Instant da);

    /** Cerca un invio nel solo perimetro della OPT titolare. */
    Optional<RichiestaDiMisura> cercaPerInvio(UUID titolareId, UUID riferimentoInvio);

    /** Elenca le pratiche della OPT in ordine cronologico stabile. */
    List<RichiestaDiMisura> cercaPerTitolare(UUID titolareId);

    /** Elenca solo le pratiche aperte in ordine cronologico stabile. */
    List<RichiestaDiMisura> codaBanco();
}
