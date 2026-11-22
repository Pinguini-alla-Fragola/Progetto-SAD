package it.unina.picus.comunicazioni.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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
 * Test black box di {@link ServizioConfermaRicezione#confermaRicezione} (#145, codice della #143), con la porta
 * {@link RiscontroRicezioneRepository} simulata e un orologio fisso.
 *
 * Dal Javadoc: il riscontro è registrato per l'account della sessione con l'istante dell'orologio, un ritento
 * restituisce l'istante già registrato, un non destinatario riceve {@link AccessoNegatoException} e una
 * comunicazione inesistente {@link NoSuchElementException}. Nei casi di errore non si registra nulla.
 */
@ExtendWith(MockitoExtension.class)
@Tag("issue-145")
@DisplayName("#145 Servizio di conferma di ricezione")
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

    @ParameterizedTest(name = "Il destinatario {0} conferma: si registra per il suo account con l'istante del server")
    @EnumSource(value = RuoloUtente.class, names = {"OPT", "OPERATORE"})
    void primaConferma(RuoloUtente ruolo) {
        var destinatario = new IdentitaAutenticata(UUID.randomUUID(), ruolo);
        when(riscontri.cercaDestinatari(comunicazione))
                .thenReturn(Optional.of(Set.of(destinatario.accountId(), UUID.randomUUID())));
        when(riscontri.registraPrimaConferma(comunicazione, destinatario.accountId(), ORA))
                .thenReturn(Optional.of(ORA));

        Instant esito = servizio.confermaRicezione(destinatario, comunicazione);

        assertThat(esito).isEqualTo(ORA);
        verify(riscontri).registraPrimaConferma(comunicazione, destinatario.accountId(), ORA);
    }

    @Test
    @DisplayName("Un ritento restituisce l'istante della prima conferma, non quello attuale")
    void ritento() {
        var destinatario = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPT);
        when(riscontri.cercaDestinatari(comunicazione)).thenReturn(Optional.of(Set.of(destinatario.accountId())));
        when(riscontri.registraPrimaConferma(comunicazione, destinatario.accountId(), ORA))
                .thenReturn(Optional.of(PRIMA_CONFERMA));

        assertThat(servizio.confermaRicezione(destinatario, comunicazione)).isEqualTo(PRIMA_CONFERMA);
    }

    @ParameterizedTest(name = "Un account {0} non destinatario riceve AccessoNegatoException e non registra nulla")
    @EnumSource(RuoloUtente.class)
    void nonDestinatario(RuoloUtente ruolo) {
        var estraneo = new IdentitaAutenticata(UUID.randomUUID(), ruolo);
        when(riscontri.cercaDestinatari(comunicazione)).thenReturn(Optional.of(Set.of(UUID.randomUUID())));

        assertThatThrownBy(() -> servizio.confermaRicezione(estraneo, comunicazione))
                .isInstanceOf(AccessoNegatoException.class);
        verify(riscontri, never()).registraPrimaConferma(any(), any(), any());
    }

    @Test
    @DisplayName("Il Banco non conferma al posto di un destinatario")
    void bancoNonConferma() {
        var banco = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);
        when(riscontri.cercaDestinatari(comunicazione)).thenReturn(Optional.of(Set.of(banco.accountId())));

        assertThatThrownBy(() -> servizio.confermaRicezione(banco, comunicazione))
                .isInstanceOf(AccessoNegatoException.class);
        verify(riscontri, never()).registraPrimaConferma(any(), any(), any());
    }

    @Test
    @DisplayName("Una comunicazione inesistente dà NoSuchElementException e non registra nulla")
    void comunicazioneInesistente() {
        var destinatario = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPT);
        when(riscontri.cercaDestinatari(comunicazione)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servizio.confermaRicezione(destinatario, comunicazione))
                .isInstanceOf(NoSuchElementException.class);
        verify(riscontri, never()).registraPrimaConferma(any(), any(), any());
    }

    @Test
    @DisplayName("Una comunicazione senza destinatari non si può confermare")
    void comunicazioneSenzaDestinatari() {
        var attore = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPERATORE);
        when(riscontri.cercaDestinatari(comunicazione)).thenReturn(Optional.of(Set.of()));

        assertThatThrownBy(() -> servizio.confermaRicezione(attore, comunicazione))
                .isInstanceOf(AccessoNegatoException.class);
        verify(riscontri, never()).registraPrimaConferma(any(), any(), any());
    }
}