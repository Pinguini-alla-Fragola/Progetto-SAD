package it.unina.picus.comunicazioni.interno.dominio;

/**
 * Una singola regola di validazione su una comunicazione da inviare. Per aggiungere un controllo, per esempio
 * sugli allegati, basta una nuova regola nell'elenco di {@link ValidatoreComunicazione}: le altre non cambiano.
 */
@FunctionalInterface
public interface RegolaComunicazione {

    /**
     * Controlla la bozza e segnala i campi da correggere.
     *
     * @param bozza la comunicazione da controllare
     * @param errori dove aggiungere un errore per ogni campo non valido
     */
    void verifica(BozzaComunicazione bozza, Errori errori);
}
