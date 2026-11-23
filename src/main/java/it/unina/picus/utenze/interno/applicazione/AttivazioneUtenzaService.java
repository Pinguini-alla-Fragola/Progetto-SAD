package it.unina.picus.utenze.interno.applicazione;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * l'impostazione iniziale della password (Task #147). Vale per OPT e Operatori: il link dura 72 ore
 * (regole degli accessi) e un nuovo link rende inutilizzabile il precedente.
 */
@Service
@Profile("!skeleton")
@Transactional
public class AttivazioneUtenzaService {

    static final int LUNGHEZZA_MIN_PASSWORD = 12;

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
            @Value("${picus.utenze.attivazione.validita:PT72H}") Duration durataValidita) {
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

    /** Dati per consegnare il link. Il token in chiaro non compare mai nel testo del record, così non finisce nei log. */
    public record DatiInvioAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        @Override
        public String toString() {
            return "DatiInvioAttivazione[emailDestinatario=" + emailDestinatario + ", token=***]";
        }
    }

    /**
     * Link appena creato, non ancora consegnato.
     *
     * @param idToken identificativo del token salvato, per confermarlo o scartarlo dopo l'invio
     * @param dati dati per la consegna
     */
    public record LinkPreparato(UUID idToken, DatiInvioAttivazione dati) {}

    /**
     * Pubblicato, nella stessa transazione, quando la posta ha accettato un nuovo link e questo ha sostituito i
     * precedenti. Chi tiene inviti in attesa per quell'account, per esempio la coda dell'importazione CSV, li
     * toglie solo adesso e non prima: se il nuovo invio fosse fallito, l'invito originale servirebbe ancora.
     *
     * @param accountId account che ha ricevuto il nuovo link
     */
    public record LinkAttivazioneConsegnato(UUID accountId) {}

    public record LinkAttivazioneDaInviareEvent(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        @Override
        public String toString() {
            return "LinkAttivazioneDaInviareEvent[emailDestinatario=" + emailDestinatario + ", token=***]";
        }
    }

    /**
     * Genera un nuovo token monouso per un account in attesa di attivazione,
     * invalida eventuali token precedenti ancora attivi sotto lock e restituisce
     * i dati per la consegna post-commit. L'account deve essere appena inserito
     * nella transazione corrente oppure già bloccato dal chiamante.
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

        return creaToken(account, ora, false).dati();
    }

    /**
     * Emette un token monouso per un account del ruolo atteso ancora in attesa di attivazione: vale sia per il
     * primo link, alla creazione, sia per i reinvii, che sostituiscono il link precedente.
     *
     * <p>Mantiene lo stesso ordine dei lock di {@link #attivaAccount} e {@code UtenzeService.revocaAccount}
     * (prima l'account, poi i token pendenti) e rilegge i token attivi dopo l'acquisizione del lock
     * sull'account per serializzare eventuali reinvii concorrenti ed evitare doppi token attivi.</p>
     */
    public DatiInvioAttivazione rigeneraTokenPerAccount(UUID accountId, RuoloUtente ruoloAtteso) {
        AccountJpa account = bloccaAccountPerReinvio(accountId, ruoloAtteso);
        return preparaTokenAttivazione(account);
    }

    private AccountJpa bloccaAccountPerReinvio(UUID accountId, RuoloUtente ruoloAtteso) {
        if (accountId == null) {
            throw new UtenzaNonTrovataException("Identificativo account obbligatorio.");
        }
        Objects.requireNonNull(ruoloAtteso, "ruoloAtteso obbligatorio");

        // 1. Punto unico di serializzazione: prima AccountJpa, poi TokenAttivazioneJpa
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

        return account;
    }

    /**
     * Primo passo del reinvio richiesto da chi possiede l'indirizzo, senza sessione: crea un nuovo link solo se
     * l'email appartiene a un account in attesa di attivazione, di qualsiasi ruolo, e non tocca quelli già
     * emessi. Per ogni altro indirizzo (sconosciuto, già attivo, revocato) non succede nulla e il chiamante non
     * deve far capire la differenza.
     *
     * <p>Il link precedente si invalida solo quando la posta ha accettato l'email ({@link #confermaConsegna}):
     * se l'invio fallisce ({@link #scartaToken}) l'utente conserva l'ultimo link che ha ricevuto davvero. I tre
     * passi sono transazioni separate, così durante l'invio non c'è nessun lock.</p>
     *
     * <p>Lo stato si controlla dopo aver preso il lock sull'account, così una attivazione o una revoca arrivata
     * nel frattempo non lascia un link valido.</p>
     *
     * @param email indirizzo digitato dall'utente
     * @return il link creato, oppure vuoto se l'indirizzo non è idoneo
     */
    public Optional<LinkPreparato> preparaTokenPerEmail(String email) {
        String chiave = UtenzeService.normalizzaEmail(email);
        if (chiave == null) {
            return Optional.empty();
        }
        return accounts.findIdByEmailIgnoreCase(chiave)
                .flatMap(accounts::findByIdForUpdate)
                .filter(a -> StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(a.getStato()))
                .map(a -> creaToken(a, Instant.now(clock), false));
    }

    /**
     * La posta ha accettato l'email: da ora il link nuovo sostituisce quelli emessi prima. Si invalidano i link con
     * numero di emissione minore e quelli dell'importazione CSV, così due richieste ravvicinate non si annullano
     * a vicenda e resta valido l'ultimo. Se l'account nel frattempo non è più in attesa non si fa nulla: attivazione e revoca hanno già
     * chiuso tutti i link.
     *
     * @param idToken link appena consegnato
     */
    public void confermaConsegna(UUID idToken) {
        tokens.findById(idToken).ifPresent(token -> {
            Optional<AccountJpa> account = accounts.findByIdForUpdate(token.getAccountId());
            if (account.filter(a -> StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(a.getStato())).isPresent()) {
                tokens.invalidaPrecedenti(token.getAccountId(), token.getId(), token.getOrdineEmissione(),
                        Instant.now(clock));
                eventPublisher.publishEvent(new LinkAttivazioneConsegnato(token.getAccountId()));
            }
        });
    }

    /**
     * La posta non ha accettato l'email: il link non è mai arrivato a nessuno, quindi si scarta e restano validi
     * quelli di prima.
     *
     * @param idToken link che non è stato consegnato
     */
    public void scartaToken(UUID idToken) {
        // un solo aggiornamento condizionato: non serve il lock e non legge uno stato superato da altre richieste
        tokens.chiudiSeAttivo(idToken, Instant.now(clock));
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
        return tokens.findByTokenHash(TokenMonouso.impronta(tokenInChiaro.strip()))
                .filter(t -> !t.isUsato() && !t.isScaduto(ora))
                .flatMap(t -> accounts.findById(t.getAccountId()))
                .filter(a -> StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(a.getStato()))
                .isPresent();
    }

    /**
     * Consuma il token monouso in modo atomico (ordine lock: prima account, poi token),
     * rilegge stato e scadenza sotto lock, imposta l'hash BCrypt della nuova password
     * e attiva l'account.
     */
    public void attivaAccount(String tokenInChiaro, String nuovaPassword) {
        validaPassword(nuovaPassword);

        if (tokenInChiaro == null || tokenInChiaro.isBlank()) {
            throw new TokenAttivazioneNonValidoException("Link di attivazione non valido o mancante.");
        }

        String hash = TokenMonouso.impronta(tokenInChiaro.strip());

        // 1. Risoluzione senza lock per individuare l'account da serializzare. Si legge solo l'identificativo: se il
        //    token fosse già caricato, la lettura con lock restituirebbe la copia in memoria con lo stato di prima e
        //    un link appena sostituito da un reinvio attiverebbe comunque l'account.
        UUID accountId = tokens.findAccountIdByTokenHash(hash)
                .orElseThrow(() -> new TokenAttivazioneNonValidoException("Link di attivazione non valido."));

        // 2. Ordine unico di lock: prima AccountJpa, poi TokenAttivazioneJpa
        AccountJpa account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new TokenAttivazioneNonValidoException("Account associato non trovato."));

        TokenAttivazioneJpa token = tokens.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new TokenAttivazioneNonValidoException("Link di attivazione non valido."));

        // 3. Rilegge l'istante corrente dopo aver acquisito entrambi i lock
        Instant ora = Instant.now(clock);

        // 4. Verifica stato account, validità, scadenza e uso sotto lock
        if (!StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(account.getStato())) {
            throw new TokenAttivazioneNonValidoException("L'account non è più attivabile.");
        }

        if (token.isUsato() || token.isScaduto(ora)) {
            throw new TokenAttivazioneNonValidoException("Il link di attivazione è scaduto o è già stato utilizzato.");
        }

        token.segnaComeUsato(ora);
        account.attivaConPassword(passwordEncoder.encode(nuovaPassword));
        tokens.saveAndFlush(token);
        accounts.saveAndFlush(account);
    }

    /**
     * Crea il link di un tentativo di consegna con l'account già bloccato dalla coda.
     * I tentativi precedenti possono aver già raggiunto la casella del destinatario.
     */
    DatiInvioAttivazione preparaTokenImportazioneOpt(AccountJpa account) {
        return creaToken(account, Instant.now(clock), true).dati();
    }

    DatiInvioAttivazione preparaReinvioOpt(UUID accountId) {
        AccountJpa account = bloccaAccountPerReinvio(accountId, RuoloUtente.OPT);
        return creaToken(account, Instant.now(clock), false).dati();
    }

    void concludeReinvioOpt(UUID accountId, DatiInvioAttivazione dati, boolean riuscito) {
        var account = accounts.findByIdForUpdate(accountId);
        if (account.isEmpty() || !StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(account.get().getStato())) {
            return;
        }

        String hash = TokenMonouso.impronta(dati.tokenInChiaro());
        var attivi = tokens.findAttiviByAccountIdForUpdate(accountId);
        var tokenInviato = attivi.stream().filter(token -> token.getTokenHash().equals(hash)).findFirst();
        if (tokenInviato.isEmpty()) {
            return;
        }

        var inviato = tokenInviato.get();
        Instant ora = Instant.now(clock);
        if (!riuscito) {
            inviato.segnaComeUsato(ora);
            return;
        }

        // Anche i link CSV preparati durante l'invio manuale sono sostituiti.
        attivi.stream()
                .filter(token -> token.isDaImportazioneOpt() || token.getOrdineEmissione() < inviato.getOrdineEmissione())
                .forEach(token -> token.segnaComeUsato(ora));
    }

    /**
     * Salva un nuovo token senza toccare quelli già emessi. Ogni token ha un numero di emissione strettamente
     * maggiore dei precedenti dell'account, ed è questo ordine, e non il timestamp, che dice quale link sostituisce
     * quale.
     */
    private LinkPreparato creaToken(AccountJpa account, Instant ora, boolean daImportazioneOpt) {
        // L'inserimento dell'account o il suo lock serializza le emissioni anche su più istanze.
        long ordineEmissione = tokens.ultimoOrdineEmissione(account.getId()) + 1;
        String tokenInChiaro = TokenMonouso.genera();
        TokenAttivazioneJpa token = tokens.saveAndFlush(new TokenAttivazioneJpa(
                UUID.randomUUID(), account.getId(), TokenMonouso.impronta(tokenInChiaro),
                ora.plus(durataValidita), ora, daImportazioneOpt, ordineEmissione));
        return new LinkPreparato(token.getId(),
                new DatiInvioAttivazione(account.getEmail(), account.getNome(), tokenInChiaro));
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
}
