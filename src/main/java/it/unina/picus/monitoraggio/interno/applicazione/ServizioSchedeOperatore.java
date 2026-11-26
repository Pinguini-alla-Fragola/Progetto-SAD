package it.unina.picus.monitoraggio.interno.applicazione;

import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeOperatore;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestreAssenteException;
import it.unina.picus.monitoraggio.interno.dominio.DatiSchedaNonValidiException;
import it.unina.picus.monitoraggio.interno.dominio.RegoleAccessoScheda;
import it.unina.picus.monitoraggio.interno.dominio.StatoVersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoSchedaOperatore;
import it.unina.picus.monitoraggio.interno.dominio.VoceCategoriaVersione;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Set;
import java.util.UUID;

/** Coordina l'invio, la consultazione e il riepilogo delle schede personali. */
@Service
@Profile("!skeleton")
@Transactional(readOnly = true)
public class ServizioSchedeOperatore implements MonitoraggioApi {

    private static final ZoneId FUSO_RENDICONTAZIONE = ZoneId.of("Europe/Rome");

    private final ArchivioSchedeOperatore archivio;
    private final CategorieSchedeRendicontabili categorie;
    private final ServizioRiepilogoSchede riepilogo;
    private final ConteggiOptApi conteggiOpt;
    private final Clock orologio;
    private final Trimestre primoTrimestreObbligatorio;

    /**
     * Crea il servizio applicativo delle schede personali con soglia di adozione obbligatoria configurabile.
     *
     * @param archivio persistenza delle schede personali
     * @param archivioRendicontabile sorgente delle schede accettate per il riepilogo
     * @param categorie fonte delle configurazioni per trimestre di TM-OP-01
     * @param conteggiOpt servizio dei conteggi trimestrali delle OPT
     * @param orologio sorgente dell'istante di invio
     * @param primoAnnoObbligatorio anno da cui è richiesta una configurazione pubblicata se assente
     * @param primoTrimestreObbligatorio trimestre dell'anno indicato da cui è richiesta la pubblicazione
     */
    @Autowired
    public ServizioSchedeOperatore(
            ArchivioSchedeOperatore archivio,
            ArchivioSchedeRendicontabili archivioRendicontabile,
            CategorieSchedeRendicontabili categorie,
            ConteggiOptApi conteggiOpt,
            Clock orologio,
            @Value("${picus.monitoraggio.categorie.primo-trimestre-obbligatorio-anno:2026}") int primoAnnoObbligatorio,
            @Value("${picus.monitoraggio.categorie.primo-trimestre-obbligatorio-trimestre:4}") int primoTrimestreObbligatorio) {
        this.archivio = archivio;
        this.categorie = categorie;
        this.riepilogo = new ServizioRiepilogoSchede(archivioRendicontabile, categorie);
        this.conteggiOpt = conteggiOpt;
        this.orologio = orologio;
        this.primoTrimestreObbligatorio = new Trimestre(primoAnnoObbligatorio, primoTrimestreObbligatorio);
    }

    /**
     * Costruttore di convenienza per test unitari con soglia predefinita al {@code 2026-T4}.
     *
     * @param archivio persistenza delle schede personali
     * @param archivioRendicontabile sorgente delle schede accettate per il riepilogo
     * @param categorie fonte delle configurazioni per trimestre di TM-OP-01
     * @param conteggiOpt servizio dei conteggi trimestrali delle OPT
     * @param orologio sorgente dell'istante di invio
     */
    public ServizioSchedeOperatore(
            ArchivioSchedeOperatore archivio,
            ArchivioSchedeRendicontabili archivioRendicontabile,
            CategorieSchedeRendicontabili categorie,
            ConteggiOptApi conteggiOpt,
            Clock orologio) {
        this(archivio, archivioRendicontabile, categorie, conteggiOpt, orologio, 2026, 4);
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

    /**
     * Risolve la configurazione del modulo per il trimestre richiesto, esponendo le categorie
     * pubblicate oppure la modalità provvisoria storica {@code v0.1}. Un trimestre successivo alla
     * soglia privo di configurazione riceve {@link ConfigurazioneTrimestreAssenteException}.
     *
     * @param attore identità verificata dell'operatore
     * @param trimestre periodo richiesto
     * @return metadati della configurazione di categorie applicabile al modulo
     */
    public ConfigurazioneModulo moduloPer(IdentitaAutenticata attore, Trimestre trimestre) {
        RegoleAccessoScheda.verificaOperatore(attore);
        if (trimestre == null) {
            throw new DatiSchedaNonValidiException(Map.of("trimestre", "Il periodo è obbligatorio"));
        }
        ConfigurazioneTrimestre conf = categorie.configurazionePer(trimestre);
        return switch (conf) {
            case ConfigurazioneTrimestre.Pubblicata p -> new ConfigurazioneModulo(
                    p.versione().codiceVersione(),
                    StatoVersioneCategorie.PUBBLICATA,
                    p.versione().voci());
            case ConfigurazioneTrimestre.LegacyProvvisoria l -> new ConfigurazioneModulo(
                    l.versione().codiceVersione(),
                    StatoVersioneCategorie.LEGACY,
                    l.versione().voci());
            case ConfigurazioneTrimestre.NonConfigurata n -> {
                if (precedeSogliaObbligatoria(trimestre)) {
                    yield new ConfigurazioneModulo("v0.1", StatoVersioneCategorie.LEGACY, List.of());
                }
                throw new ConfigurazioneTrimestreAssenteException(
                        "Configurazione delle categorie non ancora pubblicata dal Banco per il trimestre selezionato");
            }
        };
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

        ConfigurazioneTrimestre conf = categorie.configurazionePer(trimestre);
        if (conf instanceof ConfigurazioneTrimestre.NonConfigurata) {
            if (!precedeSogliaObbligatoria(trimestre)) {
                throw new ConfigurazioneTrimestreAssenteException(
                        "Configurazione delle categorie non ancora pubblicata dal Banco per il trimestre selezionato");
            }
            conf = categorie.associaLegacySeAssente(trimestre, istanteInvio);
        }

        UUID versioneCategorieId;
        switch (conf) {
            case ConfigurazioneTrimestre.Pubblicata p -> {
                Set<String> ammesse = p.versione().codiciAmmessi();
                if (!dati.conteggi().keySet().stream().allMatch(ammesse::contains)) {
                    throw new DatiSchedaNonValidiException(Map.of("conteggi",
                            "Contiene categorie non previste dalla configurazione pubblicata per il trimestre"));
                }
                versioneCategorieId = p.versione().id();
            }
            case ConfigurazioneTrimestre.LegacyProvvisoria l -> versioneCategorieId = l.versione().id();
            case ConfigurazioneTrimestre.NonConfigurata ignored ->
                    throw new ConfigurazioneTrimestreAssenteException(
                            "Configurazione delle categorie non ancora pubblicata dal Banco per il trimestre selezionato");
        }

        var sintesi = new SchedaSintesi(UUID.randomUUID(), attore.accountId(), trimestre, istanteInvio);
        archivio.registra(new SchedaOperatore(sintesi, dati), versioneCategorieId);
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

    private boolean precedeSogliaObbligatoria(Trimestre trimestre) {
        return trimestre.anno() < primoTrimestreObbligatorio.anno()
                || (trimestre.anno() == primoTrimestreObbligatorio.anno()
                && trimestre.numero() < primoTrimestreObbligatorio.numero());
    }

    /** Metadati della configurazione delle categorie esposti al modulo di compilazione. */
    public record ConfigurazioneModulo(
            String codiceVersione,
            StatoVersioneCategorie stato,
            List<VoceCategoriaVersione> categorie) {}
}
