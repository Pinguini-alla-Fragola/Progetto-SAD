package it.unina.picus.utenze.interno.applicazione;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
 * <p>L'ordine dei lock è lo stesso dell'attivazione: prima l'account, poi i suoi token.</p>
 */
@Service
@Profile("!skeleton")
@Transactional
public class RecuperoPasswordService {

    /**
     * Dati per consegnare il link. Il token in chiaro non compare nel testo del record, così non finisce nei log.
     *
     * @param emailDestinatario indirizzo dell'account
     * @param nomeDestinatario nome mostrato nel saluto
     * @param tokenInChiaro token da inserire nel link
     */
    public record DatiInvioRecupero(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        @Override
        public String toString() {
            return "DatiInvioRecupero[emailDestinatario=" + emailDestinatario + ", token=***]";
        }
    }

    private final AccountRepository accounts;
    private final TokenRecuperoPasswordRepository tokens;
    private final Duration durataValidita;
    private final Clock clock;

    @Autowired
    public RecuperoPasswordService(AccountRepository accounts, TokenRecuperoPasswordRepository tokens, Clock clock,
            @Value("${picus.utenze.recupero.validita:PT15M}") Duration durataValidita) {
        this.accounts = Objects.requireNonNull(accounts);
        this.tokens = Objects.requireNonNull(tokens);
        this.clock = Objects.requireNonNull(clock);
        this.durataValidita = Objects.requireNonNull(durataValidita);
    }

    /**
     * Emette un nuovo link per l'account attivo che ha questo indirizzo e invalida quelli precedenti. Per ogni
     * altro indirizzo (sconosciuto, in attesa di attivazione, revocato) non cambia nulla e il chiamante non deve
     * far capire la differenza.
     *
     * <p>Lo stato si controlla dopo aver preso il lock sull'account, così una revoca arrivata nel frattempo non
     * lascia un link valido.</p>
     *
     * @param email indirizzo digitato dall'utente
     * @return i dati del nuovo link, oppure vuoto se l'indirizzo non è idoneo
     */
    public Optional<DatiInvioRecupero> emettiLink(String email) {
        String chiave = UtenzeService.normalizzaEmail(email);
        if (chiave == null) {
            return Optional.empty();
        }
        return accounts.findIdByEmailIgnoreCase(chiave)
                .flatMap(accounts::findByIdForUpdate)
                .filter(a -> StatoUtenza.ATTIVO.name().equals(a.getStato()))
                .map(this::emettiNuovoToken);
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

    /** Invalida i token ancora attivi dell'account, bloccato dal chiamante, e ne crea uno nuovo. */
    private DatiInvioRecupero emettiNuovoToken(AccountJpa account) {
        List<TokenRecuperoPasswordJpa> precedenti = tokens.findAttiviByAccountIdForUpdate(account.getId());
        Instant ora = Instant.now(clock);
        for (TokenRecuperoPasswordJpa vecchio : precedenti) {
            vecchio.segnaComeUsato(ora);
        }
        if (!precedenti.isEmpty()) {
            tokens.saveAllAndFlush(precedenti);
        }

        String tokenInChiaro = TokenMonouso.genera();
        tokens.saveAndFlush(new TokenRecuperoPasswordJpa(
                UUID.randomUUID(),
                account.getId(),
                TokenMonouso.impronta(tokenInChiaro),
                ora.plus(durataValidita),
                ora));

        return new DatiInvioRecupero(account.getEmail(), account.getNome(), tokenInChiaro);
    }
}
