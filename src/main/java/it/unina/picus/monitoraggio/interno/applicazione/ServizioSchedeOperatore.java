package it.unina.picus.monitoraggio.interno.applicazione;

import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeOperatore;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili;
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

/** Coordina l'invio, la consultazione e il riepilogo delle schede personali. */
@Service
@Profile("!skeleton")
@Transactional(readOnly = true)
public class ServizioSchedeOperatore implements MonitoraggioApi {

    private static final ZoneId FUSO_RENDICONTAZIONE = ZoneId.of("Europe/Rome");

    private final ArchivioSchedeOperatore archivio;
    private final ServizioRiepilogoSchede riepilogo;
    private final ConteggiOptApi conteggiOpt;
    private final Clock orologio;

    /**
     * Crea il servizio applicativo delle schede personali.
     *
     * @param archivio persistenza delle schede personali
     * @param archivioRendicontabile sorgente delle schede accettate per il riepilogo
     * @param conteggiOpt servizio dei conteggi trimestrali delle OPT
     * @param orologio sorgente dell'istante di invio
     */
    public ServizioSchedeOperatore(ArchivioSchedeOperatore archivio, ArchivioSchedeRendicontabili archivioRendicontabile,
                                    ConteggiOptApi conteggiOpt, Clock orologio) {
        this.archivio = archivio;
        this.riepilogo = new ServizioRiepilogoSchede(archivioRendicontabile);
        this.conteggiOpt = conteggiOpt;
        this.orologio = orologio;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void registraConteggiOpt(IdentitaAutenticata attore, Trimestre trimestre, List<VoceMisura> conteggi) {
        conteggiOpt.registraConteggiOpt(attore, trimestre, conteggi);
    }

    /** {@inheritDoc} */
    @Override
    public List<ConteggioOpt> consultaConteggiOpt(IdentitaAutenticata attore, UUID optId, Trimestre da, Trimestre a) {
        return conteggiOpt.consultaConteggiOpt(attore, optId, da, a);
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
                .orElseThrow(() -> {
                    RegoleAccessoScheda.verificaConsultazione(attore, null);
                    return new NoSuchElementException("Scheda non trovata");
                });
    }

    /** {@inheritDoc} */
    @Override
    public RiepilogoTrimestre riepilogaSchede(IdentitaAutenticata attore, Trimestre trimestre) {
        return riepilogo.riepilogaSchede(attore, trimestre);
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
