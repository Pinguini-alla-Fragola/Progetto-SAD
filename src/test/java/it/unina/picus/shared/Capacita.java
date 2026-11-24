package it.unina.picus.shared;

import it.unina.picus.catalogo.api.CatalogoApi;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.catalogo.api.RicercaErogatoriApi;
import it.unina.picus.comunicazioni.api.AvvisiOperativiApi;
import it.unina.picus.comunicazioni.api.ComunicazioniApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.richieste.api.CoordinamentoRichiesteApi;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.utenze.api.UtenzeApi;

/**
 * Capacità pubbliche usate dalla suite #28, con la disponibilità attesa nell'incremento corrente.
 *
 * <p>Una capacità <b>consegnata</b> deve essere presente nel contesto Spring: se manca il test fallisce,
 * perché è una regressione. Una capacità <b>non ancora consegnata</b> sospende i soli casi che la usano.
 * Quando un caso d'uso viene integrato su {@code main} basta impostare qui {@code consegnata = true}:
 * da quel momento i suoi casi vengono eseguiti e un'eventuale assenza viene segnalata.</p>
 *
 * <p>Stato allineato a {@code main} dopo #106, #107, #117 e #124.</p>
 */
public enum Capacita {

    /** Identità verificate e dati minimi degli account (#25). */
    UTENZE(UtenzeApi.class, true),
    /** Importazione delle OPT da CSV (#24), consegnata con la #202: solo rotta HTTP, dati riletti da Utenze. */
    IMPORTAZIONE_OPT(UtenzeApi.class, true),
    /** Invio, elenco proprio, coda del Banco e consultazione della richiesta (#48). */
    INVIO_RICHIESTE(InvioRichiesteApi.class, true),
    /** Presa in carico, ricerca, contatto, assegnazione, conferma e rifiuto definitivo (#49, #52). */
    COORDINAMENTO_RICHIESTE(CoordinamentoRichiesteApi.class, true),
    /** Misure e comuni del catalogo. */
    CONSULTAZIONE_CATALOGO(ConsultazioneCatalogoApi.class, true),
    /** Erogatori compatibili con misura e comune. */
    RICERCA_EROGATORI(RicercaErogatoriApi.class, true),
    /** Avvisi operativi dell'account autenticato. */
    AVVISI_OPERATIVI(AvvisiOperativiApi.class, true),
    /** Operazioni presenti solo nel contratto completo: annullamento, archivio, elenco delle assegnazioni. */
    RICHIESTE_COMPLETE(RichiesteApi.class, false),
    /** Catalogo completo: offerta delle OPT ed elenco delle misure per il Banco. */
    CATALOGO_COMPLETO(CatalogoApi.class, false),
    /** Schede personali e conteggi OPT. */
    MONITORAGGIO(MonitoraggioApi.class, true),
    /** Comunicazioni del Banco e riscontri. */
    COMUNICAZIONI(ComunicazioniApi.class, false);

    private final Class<?> contratto;
    private final boolean consegnata;

    Capacita(Class<?> contratto, boolean consegnata) {
        this.contratto = contratto;
        this.consegnata = consegnata;
    }

    /**
     * Contratto pubblico che espone la capacità.
     *
     * @return interfaccia {@code *Api} del modulo
     */
    public Class<?> contratto() {
        return contratto;
    }

    /**
     * Indica se la capacità è già integrata nell'incremento corrente.
     *
     * @return {@code true} se la sua assenza è una regressione
     */
    public boolean consegnata() {
        return consegnata;
    }
}
