package it.unina.picus.richieste.interno.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.Map;

/** Porta di persistenza del modulo Richieste. */
public interface RichiestaRepository {
    /** Registra atomicamente l'invio oppure restituisce quello già presente con la stessa chiave. */
    RichiestaDiMisura registra(RichiestaDiMisura richiesta);

    /** Cerca una pratica senza modificarla. */
    Optional<RichiestaDiMisura> cercaPerId(UUID id);

    /**
     * Verifica l'esistenza di una pratica senza caricarne i dettagli.
     *
     * @param id identificativo della pratica
     * @return se la pratica esiste
     */
    boolean esiste(UUID id);

    /**
     * Cerca e blocca in scrittura le richieste In programmazione o Confermate assegnate
     * all'erogatore indicato, ordinate per id per prevenire deadlock.
     */
    List<RichiestaDiMisura> cercaPerAggiornamentoDaErogatore(UUID erogatoreId);
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
