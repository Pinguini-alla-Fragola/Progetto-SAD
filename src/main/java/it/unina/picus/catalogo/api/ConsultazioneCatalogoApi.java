package it.unina.picus.catalogo.api;

import it.unina.picus.catalogo.api.CatalogoApi.Comune;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;

import java.util.List;
import java.util.Optional;

/** Riferimenti di catalogo necessari all'invio e alla consultazione delle richieste. */
public interface ConsultazioneCatalogoApi {
    /** @see CatalogoApi#elencoMisureAttive() */
    List<Misura> elencoMisureAttive();

    /** @see CatalogoApi#cercaMisura(long) */
    Optional<Misura> cercaMisura(long misuraId);

    /** @see CatalogoApi#elencoComuni() */
    List<Comune> elencoComuni();

    /** @see CatalogoApi#cercaComune(String) */
    Optional<Comune> cercaComune(String codice);
}
