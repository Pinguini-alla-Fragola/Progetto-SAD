package it.unina.picus.catalogo.interno.dominio;

import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;

/**
 * Entità di dominio immutabile che rappresenta una misura del catalogo e ne governa il ciclo di vita.
 *
 * <p>Secondo i principi GRASP <em>Information Expert</em> e <em>Creator</em> e il pattern GoF
 * <em>Factory Method</em>, racchiude la creazione di nuove misure, l'aggiornamento e la
 * disattivazione garantendo la validazione degli invarianti e la conservazione stabile
 * dell'identificativo usato nello storico delle richieste e delle offerte.</p>
 *
 * @param id identificativo stabile assegnato dalla persistenza, oppure {@code null} prima del primo salvataggio
 * @param codice codice univoco della misura nel catalogo
 * @param nome denominazione della misura mostrata agli utenti
 * @param descrizione descrizione della misura
 * @param erogabileADistanza indica se la misura consente l'erogazione a distanza
 * @param attiva indica se la misura è selezionabile per nuove richieste e nuove offerte
 */
public record MisuraDiCatalogo(
        Long id,
        String codice,
        String nome,
        String descrizione,
        boolean erogabileADistanza,
        boolean attiva) {

    /**
     * Crea una nuova misura attiva a partire dai dati forniti, validandoli e normalizzandoli.
     *
     * @param dati dati descrittivi e di modalità della nuova misura
     * @return nuova misura attiva non ancora persistita ({@code id == null})
     * @throws DatiMisuraNonValidiException se i dati sono incompleti o non conformi alle regole di dominio
     */
    public static MisuraDiCatalogo nuova(DatiMisura dati) {
        DatiMisura validati = RegoleValidazioneMisura.validaENormalizza(dati);
        return new MisuraDiCatalogo(
                null,
                validati.codice(),
                validati.nome(),
                validati.descrizione(),
                validati.erogabileADistanza(),
                true);
    }

    /**
     * Ricostruisce una misura già persistita mantenendone l'identificativo stabile.
     *
     * @param id identificativo positivo della misura salvata
     * @param codice codice della misura
     * @param nome denominazione della misura
     * @param descrizione descrizione della misura
     * @param erogabileADistanza indica se la misura è erogabile a distanza
     * @param attiva indica se la misura è attualmente attiva
     * @return istanza di dominio ricostruita
     * @throws IllegalArgumentException se {@code id} non è strettamente positivo
     */
    public static MisuraDiCatalogo ricostruita(
            long id,
            String codice,
            String nome,
            String descrizione,
            boolean erogabileADistanza,
            boolean attiva) {
        if (id <= 0) {
            throw new IllegalArgumentException("Identificativo misura non valido");
        }
        return new MisuraDiCatalogo(id, codice, nome, descrizione, erogabileADistanza, attiva);
    }

    /**
     * Produce una nuova istanza aggiornata con i dati validati e il nuovo stato di attivazione,
     * conservando invariato l'identificativo storico della misura.
     *
     * @param nuoviDati dati aggiornati da validare e applicare
     * @param nuovoStatoAttivo nuovo flag di attivazione della misura
     * @return misura aggiornata con il medesimo identificativo
     * @throws IllegalStateException se la misura non ha ancora un identificativo persistito
     * @throws DatiMisuraNonValidiException se {@code nuoviDati} non rispetta i vincoli di validazione
     */
    public MisuraDiCatalogo aggiorna(DatiMisura nuoviDati, boolean nuovoStatoAttivo) {
        richiediIdentificativo();
        DatiMisura validati = RegoleValidazioneMisura.validaENormalizza(nuoviDati);
        return new MisuraDiCatalogo(
                id,
                validati.codice(),
                validati.nome(),
                validati.descrizione(),
                validati.erogabileADistanza(),
                nuovoStatoAttivo);
    }

    /**
     * Restituisce una copia disattivata della misura, preservando dati e identificativo storico.
     *
     * @return misura con stato {@code attiva = false} e medesimo identificativo
     * @throws IllegalStateException se la misura non ha ancora un identificativo persistito
     */
    public MisuraDiCatalogo disattiva() {
        richiediIdentificativo();
        return new MisuraDiCatalogo(id, codice, nome, descrizione, erogabileADistanza, false);
    }

    /**
     * Converte l'entità di dominio nel DTO pubblico definito dall'API del modulo Catalogo.
     *
     * @return rappresentazione DTO immutabile della misura
     * @throws IllegalStateException se la misura non è ancora stata persistita
     */
    public Misura aDto() {
        richiediIdentificativo();
        return new Misura(id, new DatiMisura(codice, nome, descrizione, erogabileADistanza), attiva);
    }

    private void richiediIdentificativo() {
        if (id == null) {
            throw new IllegalStateException("La misura non ha ancora un identificativo persistito");
        }
    }
}
