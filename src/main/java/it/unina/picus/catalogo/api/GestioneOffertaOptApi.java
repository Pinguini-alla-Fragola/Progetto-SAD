package it.unina.picus.catalogo.api;

import it.unina.picus.catalogo.api.CatalogoApi.OffertaOpt;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Operazioni sull'offerta delle OPT, utilizzabili indipendentemente dal resto del Catalogo. */
public interface GestioneOffertaOptApi {
    /**
     * Consulta l'offerta corrente di una OPT come titolare o Banco.
     *
     * @param attore identità verificata della OPT titolare o del Banco di coordinamento
     * @param optId account dell'OPT di cui consultare l'offerta
     * @return misure e caratteristiche dell'offerta, eventualmente nessuna
     * @throws SecurityException se l'attore non è autorizzato
     * @throws NoSuchElementException se l'account OPT è sconosciuto
     */
    List<OffertaOpt> elencoOffertaOpt(IdentitaAutenticata attore, UUID optId);

    /**
     * Dichiara o aggiorna l'offerta della OPT autenticata per una misura attiva.
     *
     * @param attore identità verificata dell'account organizzativo titolare
     * @param misuraId identificativo della misura offerta
     * @param tramiteProfessionista indica se la OPT si avvale di un professionista esterno
     * @throws SecurityException se l'attore non è una OPT autorizzata
     * @throws IllegalArgumentException se la misura è sconosciuta o non è attiva
     */
    void registraOffertaOpt(IdentitaAutenticata attore, long misuraId, boolean tramiteProfessionista);

    /**
     * Ritira l'offerta della OPT autenticata per la misura, conservando i riferimenti storici.
     *
     * @param attore identità verificata dell'account organizzativo titolare
     * @param misuraId identificativo della misura da ritirare
     * @throws SecurityException se l'attore non è una OPT autorizzata
     * @throws NoSuchElementException se la OPT non ha un'offerta corrente per la misura
     */
    void ritiraOffertaOpt(IdentitaAutenticata attore, long misuraId);
}
