package it.unina.picus.catalogo.interno.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza del dominio per il ciclo di vita delle misure di catalogo.
 *
 * <p>Applica i principi GRASP <em>Protected Variations</em> e <em>Dependency Inversion</em>
 * isolando i casi d'uso applicativi dai dettagli JPA e SQL dell'adattatore di persistenza.</p>
 */
public interface MisuraRepository {

    /**
     * Restituisce tutte le misure censite, comprese quelle disattivate, ordinate per codice.
     *
     * @return elenco ordinato delle misure di catalogo
     */
    List<MisuraDiCatalogo> trovaTutte();

    /**
     * Carica la misura acquisendo un lock pessimistico in scrittura fino al termine della transazione.
     *
     * @param misuraId identificativo stabile della misura
     * @return misura trovata sotto lock, oppure vuoto se assente
     */
    Optional<MisuraDiCatalogo> cercaPerAggiornamento(long misuraId);

    /**
     * Verifica in modo case-insensitive se esiste già una misura con il codice indicato.
     *
     * @param codice codice normalizzato da verificare
     * @return {@code true} se il codice è già utilizzato
     */
    boolean esisteCodice(String codice);

    /**
     * Verifica in modo case-insensitive se il codice è usato da una misura diversa da quella indicata.
     *
     * @param codice codice normalizzato da verificare
     * @param misuraIdEsclusa identificativo della misura corrente da escludere dal confronto
     * @return {@code true} se esiste un'altra misura con il medesimo codice
     */
    boolean esisteCodiceSuAltraMisura(String codice, long misuraIdEsclusa);

    /**
     * Persiste una nuova misura e restituisce l'istanza completa dell'identificativo generato,
     * oppure vuoto se il database rileva un conflitto concorrente di unicità sul codice.
     *
     * @param misura nuova misura valida con {@code id == null}
     * @return misura persistita con identificativo assegnato, oppure vuoto in caso di codice duplicato
     */
    Optional<MisuraDiCatalogo> salvaNuova(MisuraDiCatalogo misura);

    /**
     * Persiste l'aggiornamento di una misura precedentemente caricata in scrittura,
     * oppure restituisce vuoto se il database rileva un conflitto concorrente di unicità sul codice.
     *
     * @param misura misura aggiornata con identificativo persistito
     * @return stato salvato della misura, oppure vuoto in caso di codice duplicato
     */
    Optional<MisuraDiCatalogo> aggiorna(MisuraDiCatalogo misura);
}
