package it.unina.picus.monitoraggio.api;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.DatiSchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Contratto di invio e consultazione delle schede personali degli operatori del Banco. */
public interface SchedeOperatoreApi {

    /**
     * Registra il primo invio di un trimestre concluso a nome dell'operatore autenticato.
     * L'invio è consentito dal primo giorno del trimestre successivo nel fuso {@code Europe/Rome}
     * e valida i conteggi contro la configurazione pubblicata per il trimestre (preservando i
     * periodi già rendicontati con {@code v0.1} prima della migrazione e rifiutando i trimestri
     * privi di pubblicazione una volta avviata la pubblicazione delle versioni del tracciato,
     * senza creare nuove associazioni {@code v0.1}).
     *
     * @param attore identità verificata dell'operatore interno o esterno pagato dal Banco
     * @param trimestre periodo dichiarato
     * @param dati dati dichiarati senza firma
     * @return identificativo della scheda inviata
     * @throws SecurityException se l'attore non è un operatore autorizzato
     * @throws IllegalArgumentException se il trimestre è assente o non concluso oppure se i dati o le categorie non sono validi
     * @throws IllegalStateException se esiste già una scheda per l'operatore e il trimestre oppure manca la pubblicazione delle categorie per il trimestre
     */
    UUID inviaSchedaOperatore(IdentitaAutenticata attore, Trimestre trimestre, DatiSchedaOperatore dati);

    /**
     * Elenca le schede inviate dall'operatore autenticato.
     *
     * @param attore identità verificata dell'operatore titolare
     * @return riferimenti alle schede proprie, eventualmente nessuna
     * @throws SecurityException se l'attore non è un operatore autorizzato
     */
    List<SchedaSintesi> elencoSchedeOperatore(IdentitaAutenticata attore);

    /**
     * Consulta la scheda salvata come titolare o come Banco autorizzato.
     *
     * @param attore identità verificata del titolare o del Banco
     * @param schedaId identificativo della scheda
     * @return dati della scheda inviata
     * @throws SecurityException se l'attore non può consultare la scheda o se è un operatore e la scheda è assente
     * @throws NoSuchElementException se la scheda non esiste e l'attore è il Banco
     */
    SchedaOperatore consultaSchedaOperatore(IdentitaAutenticata attore, UUID schedaId);
}
