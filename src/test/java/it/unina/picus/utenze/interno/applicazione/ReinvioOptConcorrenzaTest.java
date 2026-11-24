package it.unina.picus.utenze.interno.applicazione;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.ComandoRegistrazioneOpt;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.EsitoRegistrazioneOpt;
import it.unina.picus.utenze.interno.persistenza.AccountJpa;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;

@SpringBootTest(properties = "spring.datasource.url=${PICUS_TEST_IMPORTAZIONE_DB:jdbc:h2:mem:reinvio-opt-concorrenza}")
class ReinvioOptConcorrenzaTest {

    @Autowired
    private RegistrazioneOptService registrazione;
    @Autowired
    private AttivazioneUtenzaService attivazione;
    @Autowired
    private TransactionTemplate transazioni;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager entityManager;
    @MockitoSpyBean
    private AccountRepository accountRepository;

    @Test
    void rifiutaIlLinkSostituitoMentreAttendeIlLockDellAccount() {
        var opt = registraOpt();
        UUID accountId = opt.accountId();
        String linkPrecedente = opt.datiInvio().tokenInChiaro();
        var nuovo = registrazione.preparaReinvio(accountId);
        var primaDelLock = new CompletableFuture<Void>();

        var consumo = transazioni.execute(stato -> {
            accountRepository.findByIdForUpdate(accountId).orElseThrow();
            doAnswer(chiamata -> {
                primaDelLock.complete(null);
                return Optional.ofNullable(entityManager.find(AccountJpa.class, accountId, LockModeType.PESSIMISTIC_WRITE));
            }).when(accountRepository).findByIdForUpdate(accountId);

            var tentativo = CompletableFuture.runAsync(
                    () -> attivazione.attivaAccount(linkPrecedente, "Password-di-prova-1"));
            primaDelLock.orTimeout(10, SECONDS).join();
            registrazione.concludeReinvio(accountId, nuovo, true);
            return tentativo;
        });

        assertThatThrownBy(() -> consumo.get(10, SECONDS))
                .hasCauseInstanceOf(TokenAttivazioneNonValidoException.class);
        assertThat(jdbc.queryForObject("select stato from utenze_account where id = ?", String.class, accountId))
                .isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
        assertThat(attivazione.isTokenValido(nuovo.tokenInChiaro())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mantieneIlLinkEmessoPerUltimoConTimestampUguali(boolean confermeInverse) {
        var opt = registraOpt();
        UUID accountId = opt.accountId();
        var primo = registrazione.preparaReinvio(accountId);
        var secondo = registrazione.preparaReinvio(accountId);
        jdbc.update("""
                update utenze_token_attivazione
                   set creato_il = timestamp with time zone '2026-01-01 00:00:00+00'
                 where account_id = ?
                """, accountId);

        registrazione.concludeReinvio(accountId, confermeInverse ? secondo : primo, true);
        registrazione.concludeReinvio(accountId, confermeInverse ? primo : secondo, true);

        assertThat(attivazione.isTokenValido(opt.datiInvio().tokenInChiaro())).isFalse();
        assertThat(attivazione.isTokenValido(primo.tokenInChiaro())).isFalse();
        assertThat(attivazione.isTokenValido(secondo.tokenInChiaro())).isTrue();
    }

    private EsitoRegistrazioneOpt registraOpt() {
        String riferimento = UUID.randomUUID().toString().substring(0, 8);
        return registrazione.registraOpt(new ComandoRegistrazioneOpt(
                "OPT-" + riferimento, "OPT sintetica", "napoli", "12345678910", riferimento + "@picus.test"));
    }
}
