package it.unina.picus.utenze.interno.applicazione;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import it.unina.picus.utenze.interno.applicazione.LettoreCsvOpt.Riga;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.DatiOpt;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.Esito;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.RisultatoRiga;

/** Coordina l'importazione confermata dal Banco e raccoglie il report. */
@Service
@Profile("!skeleton")
public class ImportazioneOptService {

    private final ElaborazioneRigaOpt elaborazione;

    ImportazioneOptService(ElaborazioneRigaOpt elaborazione) {
        this.elaborazione = elaborazione;
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
                return elaborazione.applica(riga.numero(), dati);
            }
        } catch (DatiOptNonValidiException ex) {
            motivi.add(ex.getMessage());
        }
        return new RisultatoRiga(riga.numero(), Esito.SCARTATA, motivi);
    }
}
