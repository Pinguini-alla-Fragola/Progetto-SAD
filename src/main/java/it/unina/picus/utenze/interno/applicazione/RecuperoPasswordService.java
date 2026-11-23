package it.unina.picus.utenze.interno.applicazione;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.interno.persistenza.AccountJpa;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;
import it.unina.picus.utenze.interno.persistenza.TokenRecuperoPasswordJpa;
import it.unina.picus.utenze.interno.persistenza.TokenRecuperoPasswordRepository;

/**
 * Link monouso per il recupero autonomo della password (US #33). Vale solo per gli account attivi: chi è in
 * attesa di attivazione usa il link di attivazione, chi è revocato non riacquista l'accesso. Il link dura
 * 15 minuti e un nuovo link rende inutilizzabile il precedente.
 *
 * <p>Il link precedente non si invalida alla creazione del nuovo, ma solo quando la posta ha accettato
 * l'email: se l'invio fallisce, l'utente conserva l'ultimo link che ha ricevuto davvero. Per questo il percorso
 * ha tre passi, ciascuno in una propria transazione e senza tenere lock durante l'invio:
 * {@link #preparaLink}, l'invio, poi {@link #confermaConsegna} oppure {@link #scartaLink}.</p>
 *
 * <p>Con il link l'utente sceglie la nuova password ({@link #cambiaPassword}), secondo la stessa regola
 * dell'attivazione ({@link PoliticaPassword}).</p>
 *
 * <p>L'ordine dei lock è lo stesso dell'attivazione: prima l'account, poi i suoi token.</p>
 */
@Service
@Profile("!skeleton")
@Transactional
public class RecuperoPasswordService {

    /**
     * Dati per consegnare il link. Il token in chiaro non compare nel testo del record, così non finisce nei log.
     *
     * @param idLink identificativo del link salvato, per confermarlo o scartarlo dopo l'invio
     * @param emailDestinatario indirizzo dell'account
     * @param nomeDestinatario nome mostrato nel saluto
     * @param tokenInChiaro token da inserire nel link
     */
    public record DatiInvioRecupero(UUID idLink, String emailDestinatario, String nomeDestinatario,
            String tokenInChiaro) {
        @Override
        public String toString() {
            return "DatiInvioRecupero[idLink=" + idLink + ", emailDestinatario=" + emailDestinatario + ", token=***]";
        }
    }

    private final AccountRepository accounts;
    private final TokenRecuperoPasswordRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final Duration durataValidita;
    private final Clock clock;

    @Autowired
    public RecuperoPasswordService(AccountRepository accounts, TokenRecuperoPasswordRepository tokens,
            PasswordEncoder passwordEncoder, Clock clock,
            @Value("${picus.utenze.recupero.validita:PT15M}") Duration durataValidita) {
        this.accounts = Objects.requireNonNull(accounts);
        this.tokens = Objects.requireNonNull(tokens);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.clock = Objects.requireNonNull(clock);
        this.durataValidita = Objects.requireNonNull(durataValidita);
    }

    /**
     * Crea un nuovo link per l'account attivo che ha questo indirizzo, senza toccare quelli già emessi. Per ogni
     * altro indirizzo (sconosciuto, in attesa di attivazione, revocato) non cambia nulla e il chiamante non deve
     * far capire la differenza.
     *
     * <p>Lo stato si controlla dopo aver preso il lock sull'account, così una revoca arrivata nel frattempo non
     * lascia un link valido.</p>
     *
     * @param email indirizzo digitato dall'utente
     * @return i dati del nuovo link, oppure vuoto se l'indirizzo non è idoneo
     */
    public Optional<DatiInvioRecupero> preparaLink(String email) {
        String chiave = UtenzeService.normalizzaEmail(email);
        if (chiave == null) {
            return Optional.empty();
        }
        return accounts.findIdByEmailIgnoreCase(chiave)
                .flatMap(accounts::findByIdForUpdate)
                .filter(a -> StatoUtenza.ATTIVO.name().equals(a.getStato()))
                .map(this::creaLink);
    }

    /**
     * La posta ha accettato l'email: da ora il link nuovo sostituisce quelli emessi prima. Si invalidano solo i
     * link più vecchi di questo, così due richieste ravvicinate non si annullano a vicenda e resta valido l'ultimo.
     * Se l'account nel frattempo non è più attivo non si fa nulla: la revoca ha già invalidato tutti i link.
     *
     * @param idLink link appena consegnato
     */
    public void confermaConsegna(UUID idLink) {
        tokens.findById(idLink).ifPresent(link -> {
            Optional<AccountJpa> account = accounts.findByIdForUpdate(link.getAccountId());
            if (account.filter(a -> StatoUtenza.ATTIVO.name().equals(a.getStato())).isPresent()) {
                tokens.invalidaPrecedenti(link.getAccountId(), link.getCreatoIl(), Instant.now(clock));
            }
        });
    }

    /**
     * La posta non ha accettato l'email: il link non è mai arrivato a nessuno, quindi si scarta e restano validi
     * quelli di prima, che l'utente può ancora usare.
     *
     * @param idLink link che non è stato consegnato
     */
    public void scartaLink(UUID idLink) {
        // un solo aggiornamento condizionato: non serve il lock e non legge uno stato superato da altre richieste
        tokens.chiudiSeAttivo(idLink, Instant.now(clock));
    }

    /**
     * Sceglie la nuova password con il link di recupero. Cambio della password e consumo del link avvengono nella
     * stessa transazione, sotto il lock dell'account: o succedono entrambi o nessuno dei due. Il link non vale più, e
     * con lui nessun altro link ancora aperto dello stesso account.
     *
     * <p>Si legge prima solo l'account del link e lo stato del link si legge dopo il lock: se il link fosse già
     * caricato, la lettura con lock restituirebbe la copia in memoria con lo stato di prima, e un link appena
     * consumato o sostituito potrebbe essere usato una seconda volta.</p>
     *
     * <p>La password si controlla dopo il link: con un link valido e una password da correggere la transazione
     * non cambia nulla, quindi l'utente può riprovare con lo stesso link.</p>
     *
     * @param tokenInChiaro token letto dall'indirizzo del link
     * @param nuovaPassword password scelta dall'utente
     * @throws LinkRecuperoNonValidoException se il link non esiste, è scaduto o già usato, o l'account non è attivo
     * @throws PasswordNonValidaException se la password non rispetta la regola concordata
     */
    public void cambiaPassword(String tokenInChiaro, String nuovaPassword) {
        if (tokenInChiaro == null || tokenInChiaro.isBlank()) {
            throw new LinkRecuperoNonValidoException();
        }
        String impronta = TokenMonouso.impronta(tokenInChiaro.strip());

        // 1. Senza lock si individua solo l'account da serializzare
        UUID accountId = tokens.findAccountIdByTokenHash(impronta).orElseThrow(LinkRecuperoNonValidoException::new);

        // 2. Ordine unico di lock: prima l'account, poi il suo link
        AccountJpa account = accounts.findByIdForUpdate(accountId).orElseThrow(LinkRecuperoNonValidoException::new);
        TokenRecuperoPasswordJpa link = tokens.findByTokenHashForUpdate(impronta)
                .orElseThrow(LinkRecuperoNonValidoException::new);

        // 3. Con entrambi i lock si rileggono stato, uso e scadenza
        Instant ora = Instant.now(clock);
        if (!StatoUtenza.ATTIVO.name().equals(account.getStato()) || link.isUsato() || link.isScaduto(ora)) {
            throw new LinkRecuperoNonValidoException();
        }
        PoliticaPassword.verifica(nuovaPassword);

        // 4. Link consumato e nuova password nella stessa transazione; poi si chiudono gli altri link aperti
        link.segnaComeUsato(ora);
        tokens.saveAndFlush(link);
        accounts.aggiornaHash(accountId, passwordEncoder.encode(nuovaPassword));
        tokens.invalidaTokenAttiviPerAccount(accountId, ora);
    }

    /**
     * Dice se il link è ancora utilizzabile: esiste, non è scaduto né usato e l'account è attivo. Non consuma il
     * link: serve alla pagina che deve scegliere tra il modulo di cambio password e l'invito a ripetere la richiesta.
     *
     * @param tokenInChiaro token letto dall'indirizzo del link
     * @return vero se il link è valido in questo momento
     */
    @Transactional(readOnly = true)
    public boolean isLinkValido(String tokenInChiaro) {
        if (tokenInChiaro == null || tokenInChiaro.isBlank()) {
            return false;
        }
        Instant ora = Instant.now(clock);
        return tokens.findByTokenHash(TokenMonouso.impronta(tokenInChiaro.strip()))
                .filter(t -> !t.isUsato() && !t.isScaduto(ora))
                .flatMap(t -> accounts.findById(t.getAccountId()))
                .filter(a -> StatoUtenza.ATTIVO.name().equals(a.getStato()))
                .isPresent();
    }

    /**
     * Istante di emissione del prossimo link dell'account. Ogni link ha un istante strettamente maggiore di quelli
     * già emessi: l'orologio può restituire lo stesso valore due volte e il database conserva solo i microsecondi,
     * quindi il timestamp da solo non direbbe quale link è il precedente. Il valore si assegna sotto il lock
     * dell'account, e la sostituzione ({@link #confermaConsegna}) confronta proprio questo ordine.
     */
    private Instant prossimaEmissione(UUID accountId) {
        Instant adesso = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return tokens.ultimaEmissione(accountId)
                .map(ultima -> ultima.isBefore(adesso) ? adesso : ultima.plus(1, ChronoUnit.MICROS))
                .orElse(adesso);
    }

    /** Crea il token per l'account, bloccato dal chiamante. */
    private DatiInvioRecupero creaLink(AccountJpa account) {
        Instant ora = prossimaEmissione(account.getId());
        String tokenInChiaro = TokenMonouso.genera();
        TokenRecuperoPasswordJpa link = tokens.saveAndFlush(new TokenRecuperoPasswordJpa(
                UUID.randomUUID(),
                account.getId(),
                TokenMonouso.impronta(tokenInChiaro),
                ora.plus(durataValidita),
                ora));
        return new DatiInvioRecupero(link.getId(), account.getEmail(), account.getNome(), tokenInChiaro);
    }
}
