package it.unina.picus.comunicazioni.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.unina.picus.comunicazioni.interno.dominio.RiscontroRicezioneRepository;
import it.unina.picus.shared.AccessoNegatoException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Test del comando di conferma sul contratto puntuale della porta introdotto con la #194.
 */
@ExtendWith(MockitoExtension.class)
@Tag("issue-145")
@DisplayName("#145 Servizio di conferma di ricezione")
@Tag("unit")
class ServizioConfermaRicezioneTest {

    private static final Instant ORA = Instant.parse("2026-11-22T10:00:00Z");
    private static final Instant PRIMA_CONFERMA = Instant.parse("2026-11-20T08:30:00Z");

    @Mock
    private RiscontroRicezioneRepository riscontri;

    private ServizioConfermaRicezione servizio;
    private final UUID comunicazione = UUID.randomUUID();

    @BeforeEach
    void crea() {
        servizio = new ServizioConfermaRicezione(riscontri, Clock.fixed(ORA, ZoneOffset.UTC));
    }

    @ParameterizedTest(name = "Il destinatario {0} conferma per il proprio account con l'istante del server")
    @EnumSource(value = RuoloUtente.class, names = {"OPT", "OPERATORE"})
    void primaConferma(RuoloUtente ruolo) {
        var destinatario = new IdentitaAutenticata(UUID.randomUUID(), ruolo);
        when(riscontri.registraPrimaConferma(comunicazione, destinatario.accountId(), ORA))
                .thenReturn(Optional.of(ORA));

        assertThat(servizio.confermaRicezione(destinatario, comunicazione)).isEqualTo(ORA);
        verify(riscontri).registraPrimaConferma(comunicazione, destinatario.accountId(), ORA);
    }

    @Test
    @DisplayName("Un ritento restituisce l'istante della prima conferma")
    void ritento() {
        var destinatario = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPT);
        when(riscontri.registraPrimaConferma(comunicazione, destinatario.accountId(), ORA))
                .thenReturn(Optional.of(PRIMA_CONFERMA));

        assertThat(servizio.confermaRicezione(destinatario, comunicazione)).isEqualTo(PRIMA_CONFERMA);
    }

    @ParameterizedTest(name = "{0} senza riga di destinazione: diniego")
    @EnumSource(value = RuoloUtente.class, names = {"OPT", "OPERATORE"})
    void destinazioneAssente(RuoloUtente ruolo) {
        var estraneo = new IdentitaAutenticata(UUID.randomUUID(), ruolo);
        when(riscontri.registraPrimaConferma(comunicazione, estraneo.accountId(), ORA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servizio.confermaRicezione(estraneo, comunicazione))
                .isInstanceOf(AccessoNegatoException.class);
        verify(riscontri).registraPrimaConferma(comunicazione, estraneo.accountId(), ORA);
    }

    @Test
    @DisplayName("Il Banco non accede alla porta dei riscontri")
    void bancoNonConferma() {
        var banco = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);

        assertThatThrownBy(() -> servizio.confermaRicezione(banco, comunicazione))
                .isInstanceOf(AccessoNegatoException.class);
        verifyNoInteractions(riscontri);
    }
}
