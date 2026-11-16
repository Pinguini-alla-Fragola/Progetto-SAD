package it.unina.picus.richieste.interno.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta di persistenza del modulo Richieste. */
public interface RichiestaRepository {
    /** Registra atomicamente l'invio oppure restituisce quello già presente con la stessa chiave. */
    RichiestaDiMisura registra(RichiestaDiMisura richiesta);

    /** Cerca una pratica senza modificarla. */
    Optional<RichiestaDiMisura> cercaPerId(UUID id);

    /** Cerca un invio nel solo perimetro della OPT titolare. */
    Optional<RichiestaDiMisura> cercaPerInvio(UUID titolareId, UUID riferimentoInvio);

    /** Elenca le pratiche della OPT in ordine cronologico stabile. */
    List<RichiestaDiMisura> cercaPerTitolare(UUID titolareId);

    /** Elenca solo le pratiche aperte in ordine cronologico stabile. */
    List<RichiestaDiMisura> codaBanco();
}
