package it.unina.picus.utenze.interno.persistenza;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.interno.dominio.CampoOperatore;
import it.unina.picus.utenze.interno.dominio.DatiOperatore;
import it.unina.picus.utenze.interno.dominio.DisponibilitaEmail;
import it.unina.picus.utenze.interno.dominio.RegistrazioneInConflittoException;
import it.unina.picus.utenze.interno.dominio.RegistroOperatori;

/** Salva gli Operatori nel database. */
@Repository
@Profile("!skeleton")
@Transactional
class RegistroOperatoriJpa implements RegistroOperatori {

    /** Vincolo di unicità dell'email, con il nome che PostgreSQL gli ha dato nella V1. */
    static final String VINCOLO_EMAIL = "utenze_account_email_key";
    /** Vincolo di unicità del codice fiscale, dichiarato nella V16. */
    static final String VINCOLO_CODICE_FISCALE = "utenze_operatori_codice_fiscale_unico";

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
            Optional<String> campo = campoDuplicato(e);
            if (campo.isPresent()) {
                throw new RegistrazioneInConflittoException(campo.get(), e);
            }
            throw e;
        }
    }

    /**
     * Riconosce dal nome del vincolo quale campo unico è stato violato. Gli altri errori di integrità
     * non vengono attribuiti a un campo e seguono il trattamento ordinario degli errori di persistenza.
     */
    private static Optional<String> campoDuplicato(DataIntegrityViolationException errore) {
        for (Throwable causa = errore; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violazione) {
                if (violazione.getKind() != ConstraintKind.UNIQUE || violazione.getConstraintName() == null) {
                    return Optional.empty();
                }
                String vincolo = violazione.getConstraintName();
                if (vincolo.equalsIgnoreCase(VINCOLO_EMAIL)) {
                    return Optional.of(CampoOperatore.EMAIL);
                }
                if (vincolo.equalsIgnoreCase(VINCOLO_CODICE_FISCALE)) {
                    return Optional.of(CampoOperatore.CODICE_FISCALE);
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
