package it.unina.picus.utenze.interno.applicazione;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;
import it.unina.picus.utenze.interno.applicazione.ElaborazioneRigaOpt.RigaElaborata;
import it.unina.picus.utenze.interno.applicazione.LettoreCsvOpt.Riga;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.DatiOpt;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.Esito;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.RisultatoRiga;

/** Coordina l'importazione confermata dal Banco e raccoglie il report. */
@Service
@Profile("!skeleton")
public class ImportazioneOptService {

    private static final Logger LOG = LoggerFactory.getLogger(ImportazioneOptService.class);

    private final ElaborazioneRigaOpt elaborazione;
    private final InvioEmail invioEmail;
    private final TaskExecutor esecutoreInviti;

    ImportazioneOptService(ElaborazioneRigaOpt elaborazione, InvioEmail invioEmail,
                           @Qualifier("esecutoreInvitiImportazioneOpt") TaskExecutor esecutoreInviti) {
        this.elaborazione = elaborazione;
        this.invioEmail = invioEmail;
        this.esecutoreInviti = esecutoreInviti;
    }

    /**
     * Verifica l'intero file prima di elaborare separatamente le righe valide.
     *
     * @param file contenuto CSV da leggere e chiudere
     * @return report degli esiti
     * @throws CsvOptNonValidoException se il file non può essere elaborato
     */
    public ReportImportazioneOpt importa(InputStream file) {
        var righe = LettoreCsvOpt.leggi(file);
        return new ReportImportazioneOpt(righe.stream().map(this::elabora).toList());
    }

    private RisultatoRiga elabora(Riga riga) {
        var motivi = new ArrayList<>(riga.errori());
        try {
            DatiOpt dati = RegistrazioneOptService.valida(riga.dati());
            if (motivi.isEmpty()) {
                return accodaInvito(elaborazione.applica(riga.numero(), dati));
            }
        } catch (DatiOptNonValidiException ex) {
            motivi.add(ex.getMessage());
        }
        return new RisultatoRiga(riga.numero(), Esito.SCARTATA, motivi);
    }

    /** Accoda la consegna dopo il commit, senza attendere SMTP. */
    private RisultatoRiga accodaInvito(RigaElaborata riga) {
        var risultato = riga.risultato();
        if (riga.invito().isEmpty()) {
            return risultato;
        }
        var invito = riga.invito().orElseThrow();
        try {
            esecutoreInviti.execute(() -> consegnaInvito(invito));
        } catch (TaskRejectedException ex) {
            return new RisultatoRiga(risultato.numeroRiga(), Esito.CREATA,
                    List.of("Invito di attivazione non accodato. Reinviarlo dalla gestione utenze."));
        }
        if (invioEmail.isSimulato()) {
            return new RisultatoRiga(risultato.numeroRiga(), Esito.CREATA,
                    List.of("Invito di attivazione generato in modalità simulata."));
        }
        return new RisultatoRiga(risultato.numeroRiga(), Esito.CREATA,
                List.of("Invito di attivazione accodato. In caso di mancata ricezione, reinviarlo dalla gestione utenze."));
    }

    private void consegnaInvito(DatiInvioAttivazione invito) {
        try {
            invioEmail.inviaLinkAttivazione(invito.emailDestinatario(), invito.nomeDestinatario(), invito.tokenInChiaro());
        } catch (RuntimeException ex) {
            LOG.warn("Consegna dell'invito OPT importato fallita (causa={})", ex.getClass().getSimpleName());
        }
    }
}
