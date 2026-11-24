package it.unina.picus.utenze.interno.applicazione;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.DatiOpt;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.Esito;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.RisultatoRiga;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;
import it.unina.picus.utenze.interno.persistenza.ProfiloOptRepository;

/** Applica una riga dell'importazione entro un confine transazionale indipendente. */
@Service
@Profile("!skeleton")
class ElaborazioneRigaOpt {

    private final AccountRepository accounts;
    private final ProfiloOptRepository profili;
    private final RegistrazioneOptService registrazione;
    private final CodaInvitiImportazioneOpt codaInviti;
    private final InvioEmail invioEmail;

    ElaborazioneRigaOpt(AccountRepository accounts, ProfiloOptRepository profili,
                        RegistrazioneOptService registrazione, CodaInvitiImportazioneOpt codaInviti,
                        InvioEmail invioEmail) {
        this.accounts = accounts;
        this.profili = profili;
        this.registrazione = registrazione;
        this.codaInviti = codaInviti;
        this.invioEmail = invioEmail;
    }

    /**
     * Crea una OPT o aggiorna nome e comune di quella già registrata.
     *
     * @param numeroRiga prima linea fisica della riga nel CSV
     * @param dati anagrafica convalidata
     * @return esito della riga, con l'indicazione dell'invito accodato per le nuove OPT
     * @throws DatiOptNonValidiException se l'anagrafica è in conflitto con i dati persistenti
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RisultatoRiga applica(long numeroRiga, DatiOpt dati) {
        try {
            var accountId = profili.cercaAccountPerCodiceStruttura(dati.codiceStruttura());
            if (accountId.isEmpty()) {
                var account = registrazione.creaAccount(dati);
                codaInviti.accoda(account.getId());
                String messaggio = invioEmail.isSimulato()
                        ? "Invito di attivazione accodato in modalità simulata."
                        : "Invito di attivazione accodato. In caso di mancata ricezione, reinviarlo dalla gestione utenze.";
                return new RisultatoRiga(numeroRiga, Esito.CREATA, List.of(messaggio));
            }
            return aggiorna(numeroRiga, dati, accountId.orElseThrow());
        } catch (DataIntegrityViolationException ex) {
            throw RegistrazioneOptService.classificaViolazioneVincolo(ex);
        }
    }

    /** Rilegge lo stato corrente sotto lock prima di modificare l'anagrafica. */
    private RisultatoRiga aggiorna(long numeroRiga, DatiOpt dati, UUID accountId) {
        var account = accounts.findByIdForUpdate(accountId).orElseThrow();
        if (StatoUtenza.REVOCATO.name().equals(account.getStato())) {
            return new RisultatoRiga(numeroRiga, Esito.SCARTATA,
                    List.of("Utenza OPT revocata. Gestire la riga manualmente."));
        }

        boolean emailDiversa = !dati.email().equalsIgnoreCase(account.getEmail());
        if (emailDiversa && accounts.existsByEmailIgnoreCase(dati.email())) {
            return new RisultatoRiga(numeroRiga, Esito.SCARTATA,
                    List.of("Esiste già un account associato a questa email."));
        }
        List<String> motivi = emailDiversa
                ? List.of("Email diversa da quella registrata. Gestire la modifica manualmente.")
                : List.of();

        var profilo = profili.findById(accountId).orElseThrow();
        if (dati.nome().equals(account.getNome()) && dati.comuneCodice().equals(profilo.getComuneCodice())) {
            return new RisultatoRiga(numeroRiga, Esito.INVARIATA, motivi);
        }
        account.rinomina(dati.nome());
        profilo.aggiornaComune(dati.comuneCodice());
        profili.flush();
        return new RisultatoRiga(numeroRiga, Esito.AGGIORNATA, motivi);
    }
}
