package it.unina.picus.utenze.interno.stub;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import it.unina.picus.utenze.api.OperazioneUtenzaNonConsentitaException;
import it.unina.picus.utenze.api.UtenzaNonTrovataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import it.unina.picus.utenze.api.UtenzeApi;

/**
 * Stub in memoria di {@link UtenzeApi} per il walking skeleton: l'applicazione parte e si usa da
 * capo a coda (login, area per ruolo, logout) senza database. Attivo solo con il profilo
 * {@code skeleton}; sostituisce {@code UtenzeService}. Non usare in produzione.
 */
@Component
@Profile("skeleton")
public class StubUtenzeApi implements UtenzeApi {

    private static final Logger LOG = LoggerFactory.getLogger(StubUtenzeApi.class);

    /** Come in UtenzeService: BCrypt considera solo i primi 72 byte UTF-8. */
    static final int LUNGHEZZA_MAX_PASSWORD_BYTE = 72;

    public static final String BANCO = "banco@picus.test";
    public static final String OPT = "opt@picus.test";
    public static final String OPERATORE = "operatore@picus.test";
    public static final String REVOCATO = "revocato@picus.test";
    /** Account attivo di prova, da revocare nei test di sessione. */
    public static final String REVOCABILE = "revocabile@picus.test";

    private record Account(UUID id, String nome, RuoloUtente ruolo, StatoUtenza stato) {}

    private final Map<String, Account> perEmail = new ConcurrentHashMap<>();
    private final PasswordEncoder encoder;
    private final String hash;

    StubUtenzeApi(PasswordEncoder encoder, @Value("${picus.skeleton.password:picus-demo}") String password) {
        this.encoder = encoder;
        this.hash = encoder.encode(password); // un solo hash per tutti gli account di prova
        aggiungi(BANCO, "Banco Demo", RuoloUtente.BANCO, StatoUtenza.ATTIVO);
        aggiungi(OPT, "OPT Demo", RuoloUtente.OPT, StatoUtenza.ATTIVO);
        aggiungi(OPERATORE, "Operatore Demo", RuoloUtente.OPERATORE, StatoUtenza.ATTIVO);
        aggiungi(REVOCATO, "Operatore Revocato", RuoloUtente.OPERATORE, StatoUtenza.REVOCATO);
        aggiungi(REVOCABILE, "Operatore Temporaneo", RuoloUtente.OPERATORE, StatoUtenza.ATTIVO);
        LOG.warn("Profilo 'skeleton': utenze in memoria (banco/opt/operatore@picus.test). Solo sviluppo e test.");
    }

    @Override
    public void revocaUtenzaOpt(UUID optAccountId) {
        revocaAccountConRuolo(optAccountId, RuoloUtente.OPT,
                "La revoca manuale è consentita solo per account con ruolo OPT.");
    }

    @Override
    public void revocaUtenzaOperatore(UUID operatoreAccountId) {
        revocaAccountConRuolo(operatoreAccountId, RuoloUtente.OPERATORE,
                "La revoca manuale è consentita solo per account con ruolo OPERATORE.");
    }

    private void revocaAccountConRuolo(UUID accountId, RuoloUtente ruoloAtteso, String messaggioErroreRuolo) {
        if (accountId == null) {
            throw new UtenzaNonTrovataException("Identificativo account obbligatorio.");
        }
        Account trovato = trova(accountId)
                .orElseThrow(() -> new UtenzaNonTrovataException("Account non trovato."));
        if (trovato.ruolo() != ruoloAtteso) {
            throw new OperazioneUtenzaNonConsentitaException(messaggioErroreRuolo);
        }
        if (trovato.stato() == StatoUtenza.REVOCATO) {
            throw new OperazioneUtenzaNonConsentitaException("L'account è già stato revocato.");
        }
        perEmail.entrySet().stream()
                .filter(e -> e.getValue().id().equals(accountId))
                .findFirst()
                .ifPresent(e -> perEmail.put(e.getKey(),
                        new Account(trovato.id(), trovato.nome(), trovato.ruolo(), StatoUtenza.REVOCATO)));
    }
    private void aggiungi(String email, String nome, RuoloUtente ruolo, StatoUtenza stato) {
        perEmail.put(email, new Account(UUID.nameUUIDFromBytes(email.getBytes()), nome, ruolo, stato));
    }

    /** Simula la revoca di un account mentre ha una sessione aperta. */
    public void revoca(String email) {
        perEmail.computeIfPresent(email, (e, a) -> new Account(a.id(), a.nome(), a.ruolo(), StatoUtenza.REVOCATO));
    }

    @Override
    public Optional<IdentitaAutenticata> verificaCredenziali(String email, String password) {
        Account account = email == null ? null : perEmail.get(email.strip().toLowerCase(Locale.ROOT));
        // confronto sempre eseguito: i tempi non rivelano se l'account esiste
        boolean passwordOk = password != null && !password.isEmpty() && password.getBytes(StandardCharsets.UTF_8).length <= LUNGHEZZA_MAX_PASSWORD_BYTE
                && encoder.matches(password, hash);
        if (account == null || !passwordOk || account.stato() != StatoUtenza.ATTIVO) {
            return Optional.empty();
        }
        return Optional.of(new IdentitaAutenticata(account.id(), account.ruolo()));
    }

    @Override
    public Optional<IdentitaAutenticata> verificaAccountSessione(UUID accountId) {
        return trova(accountId).filter(a -> a.stato() == StatoUtenza.ATTIVO)
                .map(a -> new IdentitaAutenticata(a.id(), a.ruolo()));
    }

    @Override
    public Optional<AccountSintetico> cercaAccount(UUID accountId) {
        return trova(accountId).map(StubUtenzeApi::sintetico);
    }

    @Override
    public List<AccountAmministrativo> elencoAccountPerRuolo(RuoloUtente ruolo) {
        return perEmail.entrySet().stream()
                .filter(e -> e.getValue().ruolo() == ruolo)
                .map(e -> {
                    Account a = e.getValue();
                    return new AccountAmministrativo(
                            a.id(), a.nome(), e.getKey(), a.id().toString().substring(0, 8).toUpperCase(),
                            null, a.ruolo(), a.stato());
                })
                .toList();
    }
    @Override
    public List<AccountSintetico> elencoAccountAttivi(RuoloUtente ruolo) {
        return perEmail.values().stream()
                .filter(a -> a.ruolo() == ruolo && a.stato() == StatoUtenza.ATTIVO)
                .map(StubUtenzeApi::sintetico).toList();
    }

    @Override
    public Optional<ProfiloErogatore> cercaProfiloErogatoreAttivo(UUID accountId) {
        return trova(accountId).filter(a -> a.stato() == StatoUtenza.ATTIVO).flatMap(StubUtenzeApi::profilo);
    }

    @Override
    public List<ProfiloErogatore> elencoOperatoriAttiviPerMisura(long misuraId) {
        return perEmail.values().stream()
                .filter(a -> a.ruolo() == RuoloUtente.OPERATORE && a.stato() == StatoUtenza.ATTIVO)
                .flatMap(a -> profilo(a).stream()).toList();
    }

    @Override
    public List<ProfiloErogatore> elencoOptAttive(Set<UUID> ids) {
        return perEmail.values().stream()
                .filter(a -> ids.contains(a.id()) && a.ruolo() == RuoloUtente.OPT && a.stato() == StatoUtenza.ATTIVO)
                .flatMap(a -> profilo(a).stream()).toList();
    }

    @Override
    public Optional<ProfiloErogatore> bloccaProfiloPerMisura(UUID accountId, long misuraId) {
        // Lo skeleton offre solo consultazione in memoria; non esegue assegnazioni persistite.
        return cercaProfiloErogatoreAttivo(accountId);
    }

    private Optional<Account> trova(UUID id) {
        return id == null ? Optional.empty()
                : perEmail.values().stream().filter(a -> a.id().equals(id)).findFirst();
    }

    private static AccountSintetico sintetico(Account a) {
        return new AccountSintetico(a.id(), a.nome(), a.ruolo(), a.stato());
    }

    private static Optional<ProfiloErogatore> profilo(Account a) {
        return switch (a.ruolo()) {
            case OPT -> Optional.of(new ProfiloErogatore(a.id(), a.nome(), TipoErogatore.OPT, "napoli", false));
            case OPERATORE -> Optional.of(
                    new ProfiloErogatore(a.id(), a.nome(), TipoErogatore.INTERNO_BANCO, "napoli", false));
            case BANCO -> Optional.empty();
        };
    }
}
