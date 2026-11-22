package it.unina.picus.utenze.interno.applicazione;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.ElaborazioneRigaOpt.RigaElaborata;
import it.unina.picus.utenze.interno.applicazione.LettoreCsvOpt.Riga;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.DatiOpt;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.Esito;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.RisultatoRiga;

/** Coordina l'importazione confermata dal Banco e raccoglie il report. */
@Service
@Profile("!skeleton")
public class ImportazioneOptService {

    private final ElaborazioneRigaOpt elaborazione;
    private final InvioEmail invioEmail;

    ImportazioneOptService(ElaborazioneRigaOpt elaborazione, InvioEmail invioEmail) {
        this.elaborazione = elaborazione;
        this.invioEmail = invioEmail;
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
                return consegnaInvito(elaborazione.applica(riga.numero(), dati));
            }
        } catch (DatiOptNonValidiException ex) {
            motivi.add(ex.getMessage());
        }
        return new RisultatoRiga(riga.numero(), Esito.SCARTATA, motivi);
    }

    /** Restituisce anche l'esito dell'invio, che avviene dopo il commit della creazione. */
    private RisultatoRiga consegnaInvito(RigaElaborata riga) {
        var risultato = riga.risultato();
        if (riga.invito().isEmpty()) {
            return risultato;
        }
        var invito = riga.invito().orElseThrow();
        try {
            invioEmail.inviaLinkAttivazione(invito.emailDestinatario(), invito.nomeDestinatario(), invito.tokenInChiaro());
        } catch (RuntimeException ex) {
            return new RisultatoRiga(risultato.numeroRiga(), Esito.CREATA,
                    List.of("Invio dell'email di attivazione fallito. Reinviare l'invito dalla gestione utenze."));
        }
        if (invioEmail.isSimulato()) {
            return new RisultatoRiga(risultato.numeroRiga(), Esito.CREATA,
                    List.of("Invito di attivazione generato in modalità simulata."));
        }
        return risultato;
    }
}
