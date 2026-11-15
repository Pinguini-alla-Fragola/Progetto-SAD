package it.unina.picus.utenze.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.interno.persistenza.AccountJpa;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;
import it.unina.picus.utenze.interno.persistenza.ProfiloOperatoreRepository;
import it.unina.picus.utenze.interno.persistenza.ProfiloOptRepository;

/** Il repository è un mock: nessun database. */
class UtenzeServiceTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private UtenzeService service;

    @BeforeEach
    void setUp() {
        service = new UtenzeService(accounts, mock(ProfiloOptRepository.class),
                mock(ProfiloOperatoreRepository.class), encoder);
    }

    private AccountJpa nuovo(String stato) throws Exception {
        var ctor = AccountJpa.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        AccountJpa a = ctor.newInstance();
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(a, "email", "banco@picus.test");
        ReflectionTestUtils.setField(a, "passwordHash", encoder.encode("segreta"));
        ReflectionTestUtils.setField(a, "nome", "Banco");
        ReflectionTestUtils.setField(a, "ruolo", "BANCO");
        ReflectionTestUtils.setField(a, "stato", stato);
        return a;
    }

    @Test
    void credenzialiCorrette() throws Exception {
        when(accounts.findByEmailIgnoreCase("banco@picus.test")).thenReturn(Optional.of(nuovo("ATTIVO")));

        var esito = service.verificaCredenziali("  Banco@Picus.test ", "segreta");

        assertThat(esito).isPresent();
        assertThat(esito.get().ruolo()).isEqualTo(RuoloUtente.BANCO);
    }

    @Test
    void passwordErrata() throws Exception {
        when(accounts.findByEmailIgnoreCase("banco@picus.test")).thenReturn(Optional.of(nuovo("ATTIVO")));

        assertThat(service.verificaCredenziali("banco@picus.test", "sbagliata")).isEmpty();
    }

    @Test
    void accountRevocatoRifiutatoAnchePasswordCorretta() throws Exception {
        when(accounts.findByEmailIgnoreCase("banco@picus.test")).thenReturn(Optional.of(nuovo("REVOCATO")));

        assertThat(service.verificaCredenziali("banco@picus.test", "segreta")).isEmpty();
    }

    @Test
    void emailSconosciutaEInputNonValidi() {
        when(accounts.findByEmailIgnoreCase("nessuno@picus.test")).thenReturn(Optional.empty());

        assertThat(service.verificaCredenziali("nessuno@picus.test", "x")).isEmpty();
        assertThat(service.verificaCredenziali(null, "x")).isEmpty();
        assertThat(service.verificaCredenziali("banco@picus.test", null)).isEmpty();
        assertThat(service.verificaCredenziali("banco@picus.test", "")).isEmpty();
        assertThat(service.verificaCredenziali("banco@picus.test", "a".repeat(500))).isEmpty();
    }

    @Test
    void sessioneDiAccountRevocatoNonValida() throws Exception {
        AccountJpa a = nuovo("REVOCATO");
        when(accounts.findById(a.getId())).thenReturn(Optional.of(a));

        assertThat(service.verificaAccountSessione(a.getId())).isEmpty();
    }
}
