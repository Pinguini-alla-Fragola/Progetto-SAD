package it.unina.picus.catalogo.api;

import it.unina.picus.catalogo.api.CatalogoApi.Comune;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;

import java.util.List;
import java.util.Optional;

/** Riferimenti di catalogo necessari all'invio e alla consultazione delle richieste. */
public interface ConsultazioneCatalogoApi {
    /**
     * Elenca le misure selezionabili per una nuova richiesta o dichiarazione di offerta.
     * @return misure attive ordinate per codice
     * @see CatalogoApi#elencoMisureAttive()
     */
    List<Misura> elencoMisureAttive();

    /**
     * Risolve il riferimento di richieste, dichiarazioni di offerta e conteggi già registrati,
     * anche quando la misura non è più attiva.
     * @param misuraId identificativo stabile della misura
     * @return misura trovata, oppure vuoto
     * @see CatalogoApi#cercaMisura(long)
     */
    Optional<Misura> cercaMisura(long misuraId);

    /**
     * Elenca i comuni utilizzabili nell'invio.
     * @return comuni censiti ordinati per nome
     * @see CatalogoApi#elencoComuni()
     */
    List<Comune> elencoComuni();

    /**
     * Risolve il codice di un comune.
     * @param codice codice presente in catalogo
     * @return comune trovato, oppure vuoto
     * @see CatalogoApi#cercaComune(String)
     */
    Optional<Comune> cercaComune(String codice);
}
