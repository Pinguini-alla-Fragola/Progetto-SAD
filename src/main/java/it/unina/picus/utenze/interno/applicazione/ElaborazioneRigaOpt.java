package it.unina.picus.utenze.interno.applicazione;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;
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

    ElaborazioneRigaOpt(AccountRepository accounts, ProfiloOptRepository profili,
                        RegistrazioneOptService registrazione) {
        this.accounts = accounts;
        this.profili = profili;
        this.registrazione = registrazione;
    }

    /**
     * Crea una OPT o aggiorna nome e comune di quella già registrata.
     *
     * @param numeroRiga prima linea fisica della riga nel CSV
     * @param dati anagrafica convalidata
     * @return esito della riga ed eventuale invito da consegnare dopo il commit
     * @throws DatiOptNonValidiException se l'anagrafica è in conflitto con i dati persistenti
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RigaElaborata applica(long numeroRiga, DatiOpt dati) {
        try {
            var accountId = profili.cercaAccountPerCodiceStruttura(dati.codiceStruttura());
            if (accountId.isEmpty()) {
                var creazione = registrazione.registra(dati);
                return new RigaElaborata(new RisultatoRiga(numeroRiga, Esito.CREATA, List.of()),
                        Optional.of(creazione.datiInvio()));
            }
            return new RigaElaborata(aggiorna(numeroRiga, dati, accountId.orElseThrow()), Optional.empty());
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

    /**
     * Separa i dati riservati necessari per la consegna dall'esito esposto nel report.
     *
     * @param risultato esito esposto al Banco
     * @param invito dati di consegna per un'utenza appena creata
     */
    record RigaElaborata(RisultatoRiga risultato, Optional<DatiInvioAttivazione> invito) {
    }
}
