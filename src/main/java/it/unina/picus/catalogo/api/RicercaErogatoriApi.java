package it.unina.picus.catalogo.api;

import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import java.util.List;
import java.util.UUID;
import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;

/** Capacità di ricerca dell'offerta, separata dai comandi di amministrazione del catalogo. */
public interface RicercaErogatoriApi {
    /**
     * Cerca tutte le opzioni compatibili per misura e territorio.
     * @param misuraId riferimento alla misura richiesta
     * @param comuneCodice codice del comune della richiesta
     * @return opzioni nello stesso comune, limitrofe o Centro PICUS a distanza
     * @throws java.util.NoSuchElementException se misura o comune non esistono
     * @see CatalogoApi#cercaErogatoriCompatibili(long, String)
     */
    List<ErogatoreCompatibile> cercaErogatoriCompatibili(long misuraId, String comuneCodice);

    /**
     * Verifica l'opzione corrente mantenendo le abilitazioni bloccate fino al commit chiamante.
     * Richiede una transazione di scrittura. Ordine: misura, account/profilo/competenza, offerta,
     * adiacenza; ciascun modulo blocca esclusivamente i dati di propria competenza.
     * @param misuraId misura della pratica
     * @param comuneCodice comune della pratica
     * @param accountId erogatore scelto
     * @param ambito opzione del contatto positivo
     * @return vero se ancora compatibile e abilitato
     */
    boolean bloccaCompatibilita(long misuraId, String comuneCodice, UUID accountId, AmbitoErogazione ambito);

    /**
     * Verifica se la misura ammette l'erogazione a distanza e la mantiene bloccata fino al commit.
     * La verifica richiede una transazione di scrittura già aperta.
     *
     * @param misuraId identificativo della misura già assegnata
     * @return vero se la misura esiste e ammette l'erogazione a distanza
     */
    boolean bloccaAbilitazioneADistanza(long misuraId);
}
