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
        return nuovo(stato, "segreta");
    }

    private AccountJpa nuovo(String stato, String password) throws Exception {
        var ctor = AccountJpa.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        AccountJpa a = ctor.newInstance();
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(a, "email", "banco@picus.test");
        ReflectionTestUtils.setField(a, "passwordHash", encoder.encode(password));
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
    void passwordDi72ByteAccettata() throws Exception {
        String password = "a".repeat(UtenzeService.LUNGHEZZA_MAX_PASSWORD_BYTE);
        when(accounts.findByEmailIgnoreCase("banco@picus.test")).thenReturn(Optional.of(nuovo("ATTIVO", password)));

        assertThat(service.verificaCredenziali("banco@picus.test", password)).isPresent();
    }

    @Test
    void passwordDi72ByteConSuffissoExtraRifiutata() throws Exception {
        String valida = "a".repeat(UtenzeService.LUNGHEZZA_MAX_PASSWORD_BYTE);
        when(accounts.findByEmailIgnoreCase("banco@picus.test")).thenReturn(Optional.of(nuovo("ATTIVO", valida)));

        // BCrypt ignorerebbe il byte oltre il 72°: il limite deve respingerla prima del confronto
        assertThat(service.verificaCredenziali("banco@picus.test", valida + "b")).isEmpty();
    }

    @Test
    void ilLimiteSiMisuraInByteUtf8NonInCaratteri() throws Exception {
        String multibyte = "è".repeat(36); // 36 caratteri, 72 byte
        when(accounts.findByEmailIgnoreCase("banco@picus.test")).thenReturn(Optional.of(nuovo("ATTIVO", multibyte)));

        assertThat(service.verificaCredenziali("banco@picus.test", multibyte)).isPresent();
        assertThat(service.verificaCredenziali("banco@picus.test", multibyte + "x")).isEmpty();
        // 37 caratteri (74 byte): sotto il vecchio limite di 128 caratteri, ma oltre i 72 byte
        assertThat(service.verificaCredenziali("banco@picus.test", "è".repeat(37))).isEmpty();
    }

    @Test
    void sessioneDiAccountRevocatoNonValida() throws Exception {
        AccountJpa a = nuovo("REVOCATO");
        when(accounts.findById(a.getId())).thenReturn(Optional.of(a));

        assertThat(service.verificaAccountSessione(a.getId())).isEmpty();
    }
}
