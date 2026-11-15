package it.unina.picus.utenze.interno.applicazione;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.interno.persistenza.AccountJpa;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;
import it.unina.picus.utenze.interno.persistenza.ProfiloOperatoreRepository;
import it.unina.picus.utenze.interno.persistenza.ProfiloOptRepository;

/**
 * Verifica delle credenziali e delle sessioni.
 *
 * <p>Esito unico per l'esterno: credenziali errate, email sconosciuta e account non attivo sono
 * indistinguibili (nessuna enumerazione degli account). Il limite sui tentativi è applicato a monte,
 * dal livello web (vedi LimitatoreAccessi). In ogni caso
 * viene eseguito un confronto BCrypt, per non rivelare l'esistenza dell'account dai tempi di risposta.</p>
 */
@Service
@Profile("!skeleton")
@Transactional
class UtenzeService implements UtenzeApi {

    static final int LUNGHEZZA_MAX_EMAIL = 254;
    static final int LUNGHEZZA_MAX_PASSWORD = 128;

    private final AccountRepository accounts;
    private final ProfiloOptRepository profiliOpt;
    private final ProfiloOperatoreRepository profiliOperatore;
    private final PasswordEncoder encoder;
    /** Hash fittizio contro cui confrontare quando l'account non può essere verificato. */
    private final String hashFittizio;

    UtenzeService(AccountRepository accounts, ProfiloOptRepository profiliOpt,
            ProfiloOperatoreRepository profiliOperatore, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.profiliOpt = profiliOpt;
        this.profiliOperatore = profiliOperatore;
        this.encoder = encoder;
        this.hashFittizio = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public Optional<IdentitaAutenticata> verificaCredenziali(String email, String password) {
        String chiave = normalizzaEmail(email);
        if (chiave == null || password == null || password.isEmpty()
                || password.length() > LUNGHEZZA_MAX_PASSWORD) {
            confrontoFittizio();
            return Optional.empty();
        }

        Optional<AccountJpa> trovato = accounts.findByEmailIgnoreCase(chiave);
        if (trovato.isEmpty()) {
            confrontoFittizio();
            return Optional.empty();
        }

        AccountJpa account = trovato.get();
        if (!encoder.matches(password, account.getPasswordHash())
                || !StatoUtenza.ATTIVO.name().equals(account.getStato())) {
            return Optional.empty();
        }

        if (encoder.upgradeEncoding(account.getPasswordHash())) {
            accounts.aggiornaHash(account.getId(), encoder.encode(password));
        }
        return Optional.of(identita(account));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentitaAutenticata> verificaAccountSessione(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return accounts.findById(accountId).filter(UtenzeService::attivo).map(UtenzeService::identita);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSintetico> cercaAccount(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return accounts.findById(accountId).map(UtenzeService::sintetico);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSintetico> elencoAccountAttivi(RuoloUtente ruolo) {
        return accounts.findByRuoloAndStato(ruolo.name(), StatoUtenza.ATTIVO.name()).stream()
                .map(UtenzeService::sintetico).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfiloErogatore> cercaProfiloErogatoreAttivo(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return accounts.findById(accountId).filter(UtenzeService::attivo).flatMap(a -> {
            RuoloUtente ruolo = RuoloUtente.valueOf(a.getRuolo());
            return switch (ruolo) {
                case OPT -> profiliOpt.findById(a.getId()).map(p -> new ProfiloErogatore(
                        a.getId(), a.getNome(), TipoErogatore.OPT, p.getComuneCodice(), false));
                case OPERATORE -> profiliOperatore.findById(a.getId()).map(p -> new ProfiloErogatore(
                        a.getId(), a.getNome(), TipoErogatore.valueOf(p.getTipologia()),
                        p.getComuneCodice(), false));
                case BANCO -> Optional.empty();
            };
        });
    }

    @Override
    public List<ProfiloErogatore> elencoOperatoriAttiviPerMisura(long misuraId) {
        // Lo schema non contiene il legame operatore-misura né l'appartenenza al Centro PICUS.
        throw new UnsupportedOperationException(
                "Serve una migrazione che modelli le misure erogate da ciascun operatore");
    }

    private void confrontoFittizio() {
        encoder.matches(UUID.randomUUID().toString(), hashFittizio);
    }

    static String normalizzaEmail(String email) {
        if (email == null) {
            return null;
        }
        String e = email.strip().toLowerCase(Locale.ROOT);
        return e.isEmpty() || e.length() > LUNGHEZZA_MAX_EMAIL ? null : e;
    }

    private static boolean attivo(AccountJpa a) {
        return StatoUtenza.ATTIVO.name().equals(a.getStato());
    }

    private static IdentitaAutenticata identita(AccountJpa a) {
        return new IdentitaAutenticata(a.getId(), RuoloUtente.valueOf(a.getRuolo()));
    }

    private static AccountSintetico sintetico(AccountJpa a) {
        return new AccountSintetico(a.getId(), a.getNome(),
                RuoloUtente.valueOf(a.getRuolo()), StatoUtenza.valueOf(a.getStato()));
    }
}
