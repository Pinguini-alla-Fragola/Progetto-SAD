package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Porta di persistenza e consultazione delle schede personali già inviate. */
public interface ArchivioSchedeOperatore {

    /**
     * Registra la scheda e i suoi conteggi nella stessa transazione.
     *
     * @param scheda invio già validato
     * @throws SchedaGiaInviataException se il titolare ha già inviato nello stesso periodo
     */
    void registra(SchedaOperatore scheda);

    /**
     * Cerca la scheda e verifica l'accesso prima di costruire il dettaglio.
     *
     * @param id identificativo della scheda
     * @param verificaTitolare controllo sull'account titolare letto dall'archivio
     * @return dettaglio autorizzato, oppure vuoto se la scheda è assente
     */
    Optional<SchedaOperatore> cerca(UUID id, Consumer<UUID> verificaTitolare);

    /**
     * Proietta i riferimenti alle schede dell'operatore.
     *
     * @param operatoreId account titolare
     * @return riferimenti in ordine di invio
     */
    List<SchedaSintesi> perOperatore(UUID operatoreId);

    /**
     * Proietta i riferimenti alle schede inviate nel periodo.
     *
     * @param trimestre anno e trimestre da consultare
     * @return riferimenti in ordine di invio
     */
    List<SchedaSintesi> perTrimestre(Trimestre trimestre);
}
