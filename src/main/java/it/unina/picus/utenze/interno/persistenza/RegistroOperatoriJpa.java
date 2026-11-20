package it.unina.picus.utenze.interno.persistenza;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.interno.dominio.DatiOperatore;
import it.unina.picus.utenze.interno.dominio.DisponibilitaEmail;
import it.unina.picus.utenze.interno.dominio.RegistrazioneInConflittoException;
import it.unina.picus.utenze.interno.dominio.RegistroOperatori;

/** Salva gli Operatori nel database. */
@Repository
@Profile("!skeleton")
@Transactional
class RegistroOperatoriJpa implements RegistroOperatori {

    private final AccountRepository accounts;
    private final ProfiloOperatoreRepository profili;
    private final OperatoreMisuraRepository misure;

    RegistroOperatoriJpa(AccountRepository accounts, ProfiloOperatoreRepository profili,
            OperatoreMisuraRepository misure) {
        this.accounts = accounts;
        this.profili = profili;
        this.misure = misure;
    }

    @Override
    @Transactional(readOnly = true)
    public DisponibilitaEmail disponibilitaEmail(String email) {
        return accounts.findByEmailIgnoreCase(email)
                .map(account -> StatoUtenza.REVOCATO.name().equals(account.getStato())
                        ? DisponibilitaEmail.ACCOUNT_REVOCATO : DisponibilitaEmail.GIA_IN_USO)
                .orElse(DisponibilitaEmail.LIBERA);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean codiceFiscaleInUso(String codiceFiscale) {
        return profili.existsByCodiceFiscale(codiceFiscale);
    }

    @Override
    public OperatoreRegistrato registra(DatiOperatore dati, String hashPassword) {
        try {
            AccountJpa account = accounts.saveAndFlush(new AccountJpa(dati.email(), hashPassword,
                    dati.nomeVisualizzato(), RuoloUtente.OPERATORE.name(),
                    StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name()));
            profili.saveAndFlush(new ProfiloOperatoreJpa(account.getId(), dati.tipologia(), dati.comuneCodice(),
                    dati.centroPicus(), dati.telefono(), dati.codiceFiscale()));
            misure.saveAllAndFlush(dati.misure().stream()
                    .map(misuraId -> new OperatoreMisuraJpa(account.getId(), misuraId)).toList());
            return new OperatoreRegistrato(account.getId(), dati.nomeVisualizzato(), dati.email());
        } catch (DataIntegrityViolationException e) {
            throw new RegistrazioneInConflittoException(e);
        }
    }
}
