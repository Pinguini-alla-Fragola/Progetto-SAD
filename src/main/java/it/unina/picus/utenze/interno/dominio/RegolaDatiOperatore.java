package it.unina.picus.utenze.interno.dominio;

/**
 * Una singola regola di validazione sui dati di un Operatore. Per aggiungere un controllo basta una nuova
 * regola, da inserire nell'elenco di {@link ValidatoreDatiOperatore}: le altre non cambiano.
 */
@FunctionalInterface
public interface RegolaDatiOperatore {

    /**
     * @param dati i dati da controllare
     * @param errori dove aggiungere un errore per ogni campo non valido
     */
    void verifica(DatiOperatore dati, Errori errori);
}
