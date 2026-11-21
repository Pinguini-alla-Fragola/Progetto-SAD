package it.unina.picus.utenze.interno.applicazione;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.OperazioneUtenzaNonConsentitaException;
import it.unina.picus.utenze.api.UtenzaNonTrovataException;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.interno.persistenza.AccountJpa;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;
import it.unina.picus.utenze.interno.persistenza.TokenAttivazioneJpa;
import it.unina.picus.utenze.interno.persistenza.TokenAttivazioneRepository;

/**
 * Servizio comune per la generazione dei token monouso di attivazione utenza e
 * l'impostazione iniziale della password (Task #147).
 */
@Service
@Profile("!skeleton")
@Transactional
public class AttivazioneUtenzaService {

    static final int LUNGHEZZA_MIN_PASSWORD = 12;
    private static final int BYTE_TOKEN = 32; // 256 bit di entropia
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final TokenAttivazioneRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration durataValidita;
    private final Clock clock;

    @Autowired
    public AttivazioneUtenzaService(
            AccountRepository accounts,
            TokenAttivazioneRepository tokens,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            @Value("${picus.utenze.attivazione.validita:PT48H}") Duration durataValidita) {
        this(accounts, tokens, passwordEncoder, eventPublisher, durataValidita, Clock.systemUTC());
    }

    AttivazioneUtenzaService(
            AccountRepository accounts,
            TokenAttivazioneRepository tokens,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Duration durataValidita,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts);
        this.tokens = Objects.requireNonNull(tokens);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.durataValidita = Objects.requireNonNull(durataValidita);
        this.clock = Objects.requireNonNull(clock);
    }

    public record DatiInvioAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
    }

    public record LinkAttivazioneDaInviareEvent(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
    }

    /**
     * Genera un nuovo token monouso per un account in attesa di attivazione,
     * invalida eventuali token precedenti ancora attivi sotto lock e restituisce
     * i dati per la consegna post-commit.
     */
    public DatiInvioAttivazione preparaTokenAttivazione(AccountJpa account) {
        Objects.requireNonNull(account, "account obbligatorio");
        if (!StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(account.getStato())) {
            throw new IllegalStateException("L'account non è in stato IN_ATTESA_DI_ATTIVAZIONE");
        }

        List<TokenAttivazioneJpa> precedenti = tokens.findAttiviByAccountIdForUpdate(account.getId());
        Instant ora = Instant.now(clock);
        for (TokenAttivazioneJpa vecchio : precedenti) {
            vecchio.segnaComeUsato(ora);
        }
        if (!precedenti.isEmpty()) {
            tokens.saveAllAndFlush(precedenti);
        }

        String tokenInChiaro = generaTokenCasuale();
        String tokenHash = calcolaSha256(tokenInChiaro);

        TokenAttivazioneJpa nuovoToken = new TokenAttivazioneJpa(
                UUID.randomUUID(),
                account.getId(),
                tokenHash,
                ora.plus(durataValidita),
                ora
        );
        tokens.saveAndFlush(nuovoToken);

        return new DatiInvioAttivazione(account.getEmail(), account.getNome(), tokenInChiaro);
    }

    /**
     * Rigenera un token monouso per un account del ruolo atteso ancora in attesa di attivazione.
     *
     * <p>Mantiene lo stesso ordine dei lock di {@link #attivaAccount} e {@code UtenzeService.revocaAccount}
     * (prima i token pendenti, poi l'account) e rilegge i token attivi dopo l'acquisizione del lock
     * sull'account per serializzare eventuali reinvii concorrenti ed evitare doppi token attivi.</p>
     */
    public DatiInvioAttivazione rigeneraTokenPerAccount(UUID accountId, RuoloUtente ruoloAtteso) {
        if (accountId == null) {
            throw new UtenzaNonTrovataException("Identificativo account obbligatorio.");
        }
        Objects.requireNonNull(ruoloAtteso, "ruoloAtteso obbligatorio");

        // 1. Ordine dei lock coerente con attivaAccount e revocaAccount: prima token, poi account
        tokens.findAttiviByAccountIdForUpdate(accountId);
        AccountJpa account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new UtenzaNonTrovataException("Account non trovato."));

        // 2. Verifica ruolo e stato sotto lock esclusivo dell'account
        if (!ruoloAtteso.name().equals(account.getRuolo())) {
            throw new OperazioneUtenzaNonConsentitaException(
                    "Il reinvio dell'invito è consentito solo per account con ruolo " + ruoloAtteso.name() + ".");
        }
        if (!StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(account.getStato())) {
            throw new OperazioneUtenzaNonConsentitaException("L'account non è in attesa di attivazione.");
        }

        // 3. Rilegge sotto lock sull'account tutti i token attivi (inclusi quelli appena committati da reinvii concorrenti)
        List<TokenAttivazioneJpa> precedenti = tokens.findAttiviByAccountIdForUpdate(accountId);
        Instant ora = Instant.now(clock);
        for (TokenAttivazioneJpa vecchio : precedenti) {
            if (!vecchio.isUsato()) {
                vecchio.segnaComeUsato(ora);
            }
        }
        if (!precedenti.isEmpty()) {
            tokens.saveAllAndFlush(precedenti);
        }

        String tokenInChiaro = generaTokenCasuale();
        String tokenHash = calcolaSha256(tokenInChiaro);
        TokenAttivazioneJpa nuovoToken = new TokenAttivazioneJpa(
                UUID.randomUUID(),
                account.getId(),
                tokenHash,
                ora.plus(durataValidita),
                ora
        );
        tokens.saveAndFlush(nuovoToken);

        return new DatiInvioAttivazione(account.getEmail(), account.getNome(), tokenInChiaro);
    }

    /**
     * Mantiene la compatibilità per i chiamanti interni che usano l'evento AFTER_COMMIT.
     */
    public void generaEInviaAttivazione(AccountJpa account) {
        DatiInvioAttivazione dati = preparaTokenAttivazione(account);
        eventPublisher.publishEvent(
                new LinkAttivazioneDaInviareEvent(dati.emailDestinatario(), dati.nomeDestinatario(), dati.tokenInChiaro()));
    }

    /**
     * Verifica che un token esista, non sia scaduto e non sia già stato usato.
     */
    @Transactional(readOnly = true)
    public boolean isTokenValido(String tokenInChiaro) {
        if (tokenInChiaro == null || tokenInChiaro.isBlank()) {
            return false;
        }
        Instant ora = Instant.now(clock);
        return tokens.findByTokenHash(calcolaSha256(tokenInChiaro.strip()))
                .filter(t -> !t.isUsato() && !t.isScaduto(ora))
                .flatMap(t -> accounts.findById(t.getAccountId()))
                .filter(a -> StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(a.getStato()))
                .isPresent();
    }

    /**
     * Consuma il token monouso in modo atomico (ordine lock: prima token, poi account),
     * rilegge stato e scadenza sotto lock, imposta l'hash BCrypt della nuova password
     * e attiva l'account.
     */
    public void attivaAccount(String tokenInChiaro, String nuovaPassword) {
        validaPassword(nuovaPassword);

        if (tokenInChiaro == null || tokenInChiaro.isBlank()) {
            throw new TokenAttivazioneNonValidoException("Link di attivazione non valido o mancante.");
        }

        String hash = calcolaSha256(tokenInChiaro.strip());

        // 1. Ordine dei lock: prima TokenAttivazioneJpa, poi AccountJpa
        TokenAttivazioneJpa token = tokens.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new TokenAttivazioneNonValidoException("Link di attivazione non valido."));

        AccountJpa account = accounts.findByIdForUpdate(token.getAccountId())
                .orElseThrow(() -> new TokenAttivazioneNonValidoException("Account associato non trovato."));

        // 2. Rilegge l'istante corrente dopo aver acquisito entrambi i lock
        Instant ora = Instant.now(clock);

        // 3. Verifica validità, scadenza e stato account sotto lock
        if (token.isUsato() || token.isScaduto(ora)) {
            throw new TokenAttivazioneNonValidoException("Il link di attivazione è scaduto o è già stato utilizzato.");
        }

        if (!StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(account.getStato())) {
            throw new TokenAttivazioneNonValidoException("L'account non è più attivabile.");
        }

        token.segnaComeUsato(ora);
        account.attivaConPassword(passwordEncoder.encode(nuovaPassword));
        tokens.saveAndFlush(token);
        accounts.saveAndFlush(account);
    }

    private static void validaPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La password è obbligatoria.");
        }
        if (password.length() < LUNGHEZZA_MIN_PASSWORD) {
            throw new IllegalArgumentException("La password deve contenere almeno " + LUNGHEZZA_MIN_PASSWORD + " caratteri.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > UtenzeService.LUNGHEZZA_MAX_PASSWORD_BYTE) {
            throw new IllegalArgumentException("La password supera la lunghezza massima consentita di 72 byte.");
        }
    }

    private static String generaTokenCasuale() {
        byte[] bytes = new byte[BYTE_TOKEN];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String calcolaSha256(String valore) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(valore.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 non disponibile", e);
        }
    }
}