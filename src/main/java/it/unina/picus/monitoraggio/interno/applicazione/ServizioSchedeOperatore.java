package it.unina.picus.monitoraggio.interno.applicazione;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.DatiSchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.SchedeOperatoreApi;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeOperatore;
import it.unina.picus.monitoraggio.interno.dominio.DatiSchedaNonValidiException;
import it.unina.picus.monitoraggio.interno.dominio.RegoleAccessoScheda;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoSchedaOperatore;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Coordina il primo invio e la consultazione autorizzata delle schede personali. */
@Service
@Profile("!skeleton")
@Transactional(readOnly = true)
public class ServizioSchedeOperatore implements SchedeOperatoreApi {

    private static final ZoneId FUSO_RENDICONTAZIONE = ZoneId.of("Europe/Rome");

    private final ArchivioSchedeOperatore archivio;
    private final Clock orologio;

    /**
     * Crea il servizio applicativo delle schede personali.
     *
     * @param archivio persistenza delle schede personali
     * @param orologio sorgente dell'istante di invio
     */
    public ServizioSchedeOperatore(ArchivioSchedeOperatore archivio, Clock orologio) {
        this.archivio = archivio;
        this.orologio = orologio;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public UUID inviaSchedaOperatore(IdentitaAutenticata attore, Trimestre trimestre, DatiSchedaOperatore dati) {
        RegoleAccessoScheda.verificaOperatore(attore);
        TracciatoSchedaOperatore.valida(trimestre, dati);
        var istanteInvio = orologio.instant();
        var dataInvio = LocalDate.ofInstant(istanteInvio, FUSO_RENDICONTAZIONE);
        int annoCorrente = dataInvio.getYear();
        int trimestreCorrente = dataInvio.get(IsoFields.QUARTER_OF_YEAR);
        if (trimestre.anno() > annoCorrente
                || (trimestre.anno() == annoCorrente && trimestre.numero() >= trimestreCorrente)) {
            throw new DatiSchedaNonValidiException(Map.of("trimestre", "Scegli un trimestre concluso"));
        }
        var sintesi = new SchedaSintesi(UUID.randomUUID(), attore.accountId(), trimestre, istanteInvio);
        archivio.registra(new SchedaOperatore(sintesi, dati));
        return sintesi.id();
    }

    /** {@inheritDoc} */
    @Override
    public List<SchedaSintesi> elencoSchedeOperatore(IdentitaAutenticata attore) {
        RegoleAccessoScheda.verificaOperatore(attore);
        return archivio.perOperatore(attore.accountId());
    }

    /** {@inheritDoc} */
    @Override
    public SchedaOperatore consultaSchedaOperatore(IdentitaAutenticata attore, UUID schedaId) {
        RegoleAccessoScheda.verificaRuoloConsultazione(attore);
        return archivio.cerca(schedaId, titolare -> RegoleAccessoScheda.verificaConsultazione(attore, titolare))
                .orElseThrow(() -> new NoSuchElementException("Scheda non trovata"));
    }

    /**
     * Elenca per il Banco i riferimenti da cui consultare le schede di un singolo trimestre.
     *
     * @param attore identità verificata del Banco
     * @param trimestre periodo da consultare
     * @return riferimenti alle schede ricevute, eventualmente nessuno
     * @throws SecurityException se l'attore non è il Banco
     * @throws DatiSchedaNonValidiException se il periodo è assente
     */
    public List<SchedaSintesi> elencoSchedeRicevute(IdentitaAutenticata attore, Trimestre trimestre) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        if (trimestre == null) {
            throw new DatiSchedaNonValidiException(Map.of("trimestre", "Il periodo è obbligatorio"));
        }
        return archivio.perTrimestre(trimestre);
    }
}
