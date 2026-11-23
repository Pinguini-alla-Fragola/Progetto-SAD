package it.unina.picus.utenze.interno.applicazione;

import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;
import it.unina.picus.utenze.interno.persistenza.TokenAttivazioneRepository;

/** Conserva gli inviti CSV in attesa e prepara il link prima della consegna. */
@Service
@Profile("!skeleton")
class CodaInvitiImportazioneOpt {

    private static final Duration DURATA_PRENOTAZIONE = Duration.ofMinutes(5);
    static final int MASSIMO_TENTATIVI = 5;

    private final JdbcClient jdbc;
    private final AccountRepository accounts;
    private final TokenAttivazioneRepository tokens;
    private final AttivazioneUtenzaService attivazione;

    CodaInvitiImportazioneOpt(JdbcClient jdbc, AccountRepository accounts,
                            TokenAttivazioneRepository tokens, AttivazioneUtenzaService attivazione) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.tokens = tokens;
        this.attivazione = attivazione;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accoda(UUID accountId) {
        jdbc.sql("insert into utenze_inviti_importazione_opt (account_id) values (?)")
                .param(accountId).update();
    }

    /** Dopo il commit il link è valido e, se la consegna si interrompe, la riga torna disponibile. */
    @Transactional
    public Optional<Consegna> prenota() {
        var prossimo = jdbc.sql("""
                select account_id, tentativi from utenze_inviti_importazione_opt
                 where disponibile_dal <= current_timestamp and tentativi < ?
                 order by disponibile_dal, account_id
                 fetch first 1 row only for update skip locked
                """).param(MASSIMO_TENTATIVI)
                .query((rs, numero) -> new Invito(rs.getObject("account_id", UUID.class),
                        rs.getInt("tentativi"))).optional();
        if (prossimo.isEmpty()) {
            return Optional.empty();
        }

        var invito = prossimo.orElseThrow();
        var account = accounts.findByIdForUpdate(invito.accountId()).orElseThrow();
        // Un token di un reinvio manuale ha precedenza su quello della coda.
        if (!StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(account.getStato())
                || tokens.findAttiviByAccountIdForUpdate(invito.accountId()).stream()
                        .anyMatch(token -> !token.isDaImportazioneOpt())) {
            jdbc.sql("delete from utenze_inviti_importazione_opt where account_id = ?")
                    .param(invito.accountId()).update();
            return Optional.empty();
        }

        var dati = attivazione.preparaTokenImportazioneOpt(account);
        int tentativo = invito.tentativi() + 1;
        OffsetDateTime disponibileDal = tentativo == MASSIMO_TENTATIVI ? null
                : OffsetDateTime.now(ZoneOffset.UTC).plus(DURATA_PRENOTAZIONE);
        jdbc.sql("""
                update utenze_inviti_importazione_opt
                   set tentativi = :tentativo, disponibile_dal = :disponibileDal where account_id = :accountId
                """).param("tentativo", tentativo)
                .param("disponibileDal", disponibileDal, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("accountId", invito.accountId()).update();
        return Optional.of(new Consegna(invito.accountId(), tentativo, dati));
    }

    public void completa(Consegna consegna) {
        jdbc.sql("delete from utenze_inviti_importazione_opt where account_id = ? and tentativi = ?")
                .params(consegna.accountId(), consegna.tentativo()).update();
    }

    private record Invito(UUID accountId, int tentativi) {
    }

    record Consegna(UUID accountId, int tentativo, DatiInvioAttivazione dati) {
    }
}
