package it.unina.picus.utenze.interno.applicazione;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.OperazioneUtenzaNonConsentitaException;
import it.unina.picus.utenze.api.UtenzaNonTrovataException;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.interno.persistenza.AccountJpa;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;
import it.unina.picus.utenze.interno.persistenza.OperatoreMisuraJpa;
import it.unina.picus.utenze.interno.persistenza.ProfiloOperatoreJpa;
import it.unina.picus.utenze.interno.persistenza.ProfiloOperatoreRepository;
import it.unina.picus.utenze.interno.persistenza.ProfiloOptJpa;
import it.unina.picus.utenze.interno.persistenza.ProfiloOptRepository;
import it.unina.picus.utenze.interno.persistenza.TokenAttivazioneJpa;
import it.unina.picus.utenze.interno.persistenza.TokenAttivazioneRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
@Profile("!skeleton")
@Transactional
class UtenzeService implements UtenzeApi {

    static final int LUNGHEZZA_MAX_EMAIL = 254;
    static final int LUNGHEZZA_MAX_PASSWORD_BYTE = 72;

    private final AccountRepository accounts;
    private final ProfiloOptRepository profiliOpt;
    private final ProfiloOperatoreRepository profiliOperatore;
    private final TokenAttivazioneRepository tokensAttivazione;
    private final PasswordEncoder encoder;
    private final EntityManager em;
    private final Clock clock;
    private final String hashFittizio;

    @Autowired
    UtenzeService(AccountRepository accounts, ProfiloOptRepository profiliOpt,
                  ProfiloOperatoreRepository profiliOperatore,
                  TokenAttivazioneRepository tokensAttivazione,
                  PasswordEncoder encoder, EntityManager em) {
        this(accounts, profiliOpt, profiliOperatore, tokensAttivazione, encoder, em, Clock.systemUTC());
    }

    UtenzeService(AccountRepository accounts, ProfiloOptRepository profiliOpt,
                  ProfiloOperatoreRepository profiliOperatore,
                  TokenAttivazioneRepository tokensAttivazione,
                  PasswordEncoder encoder, EntityManager em, Clock clock) {
        this.accounts = accounts;
        this.profiliOpt = profiliOpt;
        this.profiliOperatore = profiliOperatore;
        this.tokensAttivazione = tokensAttivazione;
        this.encoder = encoder;
        this.em = em;
        this.clock = Objects.requireNonNull(clock);
        this.hashFittizio = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public void revocaUtenzaOpt(UUID optAccountId) {
        revocaAccount(optAccountId, RuoloUtente.OPT,
                "La revoca manuale è consentita solo per account con ruolo OPT.");
    }

    @Override
    public void revocaUtenzaOperatore(UUID operatoreAccountId) {
        revocaAccount(operatoreAccountId, RuoloUtente.OPERATORE,
                "La revoca manuale è consentita solo per account con ruolo OPERATORE.");
    }

    private void revocaAccount(UUID accountId, RuoloUtente ruoloAtteso, String messaggioErroreRuolo) {
        if (accountId == null) {
            throw new UtenzaNonTrovataException("Identificativo account obbligatorio.");
        }

        // 1. Punto unico di serializzazione: prima AccountJpa, poi TokenAttivazioneJpa
        AccountJpa account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new UtenzaNonTrovataException("Account non trovato."));

        // 2. Verifica ruolo e stato sotto lock esclusivo dell'account
        if (!ruoloAtteso.name().equals(account.getRuolo())) {
            throw new OperazioneUtenzaNonConsentitaException(messaggioErroreRuolo);
        }

        if (StatoUtenza.REVOCATO.name().equals(account.getStato())) {
            throw new OperazioneUtenzaNonConsentitaException("L'account è già stato revocato.");
        }

        // 3. Blocca e invalida i token ancora attivi (ordine: account -> token)
        List<TokenAttivazioneJpa> tokenAttivi = tokensAttivazione.findAttiviByAccountIdForUpdate(accountId);
        Instant ora = Instant.now(clock);
        account.revoca();
        accounts.saveAndFlush(account);

        for (TokenAttivazioneJpa token : tokenAttivi) {
            if (!token.isUsato()) {
                token.segnaComeUsato(ora);
            }
        }
        if (!tokenAttivi.isEmpty()) {
            tokensAttivazione.saveAllAndFlush(tokenAttivi);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountAmministrativo> elencoAccountPerRuolo(RuoloUtente ruolo) {
        List<AccountJpa> lista = accounts.findByRuolo(ruolo.name());
        if (lista.isEmpty()) {
            return List.of();
        }
        if (ruolo == RuoloUtente.OPT) {
            Map<UUID, ProfiloOptJpa> profili = profiliOpt.findAllById(
                    lista.stream().map(AccountJpa::getId).toList()
            ).stream().collect(Collectors.toMap(ProfiloOptJpa::getAccountId, Function.identity()));
            return lista.stream().map(a -> {
                ProfiloOptJpa p = profili.get(a.getId());
                String codiceStruttura = p != null ? p.getCodiceStruttura() : null;
                String comune = p != null ? p.getComuneCodice() : null;
                return new AccountAmministrativo(
                        a.getId(), a.getNome(), a.getEmail(), codiceStruttura, comune,
                        RuoloUtente.OPT, StatoUtenza.valueOf(a.getStato()));
            }).toList();
        }
        if (ruolo == RuoloUtente.OPERATORE) {
            Map<UUID, ProfiloOperatoreJpa> profili = profiliOperatore.findAllById(
                    lista.stream().map(AccountJpa::getId).toList()
            ).stream().collect(Collectors.toMap(ProfiloOperatoreJpa::getAccountId, Function.identity()));
            return lista.stream().map(a -> {
                ProfiloOperatoreJpa p = profili.get(a.getId());
                String comune = p != null ? p.getComuneCodice() : null;
                String ref = p != null ? p.getTipologia() : null;
                return new AccountAmministrativo(
                        a.getId(), a.getNome(), a.getEmail(), ref, comune,
                        RuoloUtente.OPERATORE, StatoUtenza.valueOf(a.getStato()));
            }).toList();
        }
        return lista.stream().map(a -> new AccountAmministrativo(
                a.getId(), a.getNome(), a.getEmail(), null, null,
                RuoloUtente.valueOf(a.getRuolo()), StatoUtenza.valueOf(a.getStato()))).toList();
    }

    @Override
    public Optional<IdentitaAutenticata> verificaCredenziali(String email, String password) {
        String chiave = normalizzaEmail(email);
        if (chiave == null || password == null || password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > LUNGHEZZA_MAX_PASSWORD_BYTE) {
            confrontoFittizio();
            return Optional.empty();
        }

        Optional<AccountJpa> trovato = accounts.findByEmailIgnoreCase(chiave);
        if (trovato.isEmpty()) {
            confrontoFittizio();
            return Optional.empty();
        }

        AccountJpa account = trovato.get();
        String passwordHash = account.getPasswordHash();
        if (passwordHash == null) {
            confrontoFittizio();
            return Optional.empty();
        }

        if (!encoder.matches(password, passwordHash)
                || !StatoUtenza.ATTIVO.name().equals(account.getStato())) {
            return Optional.empty();
        }

        if (encoder.upgradeEncoding(passwordHash)) {
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
                        p.getComuneCodice(), p.isCentroPicus()));
                case BANCO -> Optional.empty();
            };
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfiloErogatore> elencoOperatoriAttiviPerMisura(long misuraId) {
        return profiliOperatore.perMisura(misuraId).stream()
                .map(p -> new ProfiloErogatore(p.getAccountId(), p.getNome(),
                        TipoErogatore.valueOf(p.getTipologia()), p.getComuneCodice(), p.isCentroPicus()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfiloErogatore> elencoOptAttive(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return profiliOpt.attive(ids).stream().map(p -> new ProfiloErogatore(
                p.getAccountId(), p.getNome(), TipoErogatore.OPT, p.getComuneCodice(), false)).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ProfiloErogatore> bloccaProfiloPerMisura(UUID accountId, long misuraId) {
        if (accountId == null) {
            return Optional.empty();
        }
        var account = blocca(AccountJpa.class, accountId);
        if (account == null || !attivo(account)) {
            return Optional.empty();
        }
        return switch (RuoloUtente.valueOf(account.getRuolo())) {
            case OPT -> Optional.ofNullable(blocca(ProfiloOptJpa.class, accountId))
                    .map(p -> new ProfiloErogatore(accountId, account.getNome(), TipoErogatore.OPT,
                            p.getComuneCodice(), false));
            case OPERATORE -> {
                var profilo = blocca(ProfiloOperatoreJpa.class, accountId);
                var competenza = blocca(OperatoreMisuraJpa.class, new OperatoreMisuraJpa.Chiave(accountId, misuraId));
                yield profilo == null || competenza == null ? Optional.empty()
                        : Optional.of(new ProfiloErogatore(accountId, account.getNome(),
                        TipoErogatore.valueOf(profilo.getTipologia()), profilo.getComuneCodice(), profilo.isCentroPicus()));
            }
            case BANCO -> Optional.empty();
        };
    }

    private <T> T blocca(Class<T> tipo, Object id) {
        var entita = em.find(tipo, id, LockModeType.PESSIMISTIC_READ);
        if (entita != null) {
            em.refresh(entita, LockModeType.PESSIMISTIC_READ);
        }
        return entita;
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