package it.unina.picus.comunicazioni.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.Riscontro;
import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni.RicezioneDestinatario;
import it.unina.picus.utenze.api.NomiAccountApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Test black box di {@link ConsultazioneComunicazioni#elencoRiscontri} (#145, codice della #144 nella PR #193),
 * con l'archivio e i nomi degli account simulati.
 *
 * Dal Javadoc: solo il Banco consulta i riscontri, una comunicazione inesistente dà
 * {@link RisorsaComunicazioniNonTrovataException}, si restituisce una voce per ogni destinatario registrato
 * all'invio (anche in attesa) con l'eventuale istante di conferma, in ordine di nome.
 */
@ExtendWith(MockitoExtension.class)
@Tag("issue-145")
@DisplayName("#145 Servizio di consultazione dei riscontri del Banco")
class ServizioConsultazioneRiscontriTest {

    private static final Instant PRIMA_CONFERMA = Instant.parse("2026-11-20T08:30:00Z");

    @Mock
    private ArchivioComunicazioni archivio;

    @Mock
    private NomiAccountApi nomiAccount;

    private ConsultazioneComunicazioni consultazione;
    private final UUID comunicazione = UUID.randomUUID();
    private final IdentitaAutenticata banco = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);

    @BeforeEach
    void crea() {
        consultazione = new ServizioConsultazioneComunicazioni(archivio, nomiAccount);
    }

    // ------------------------------------------------------------------ accesso

    @ParameterizedTest(name = "Un account {0} non consulta i riscontri e l'archivio non viene letto")
    @EnumSource(value = RuoloUtente.class, names = {"OPT", "OPERATORE"})
    void soloIlBanco(RuoloUtente ruolo) {
        var attore = new IdentitaAutenticata(UUID.randomUUID(), ruolo);

        assertThatThrownBy(() -> consultazione.elencoRiscontri(attore, comunicazione))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(archivio, nomiAccount);
    }

    static Stream<Arguments> identitaIncomplete() {
        return Stream.of(
                Arguments.of("identità assente", null),
                Arguments.of("account assente", new IdentitaAutenticata(null, RuoloUtente.BANCO)),
                Arguments.of("ruolo assente", new IdentitaAutenticata(UUID.randomUUID(), null)));
    }

    @ParameterizedTest(name = "Identità incompleta ({0}): SecurityException")
    @MethodSource("identitaIncomplete")
    void identitaIncompleta(String caso, IdentitaAutenticata attore) {
        assertThatThrownBy(() -> consultazione.elencoRiscontri(attore, comunicazione))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(archivio, nomiAccount);
    }

    @Test
    @DisplayName("Un non Banco riceve il diniego anche per una comunicazione inesistente, senza scoprire se esiste")
    void diniegoPrimaDellEsistenza() {
        var opt = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPT);

        assertThatThrownBy(() -> consultazione.elencoRiscontri(opt, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(archivio);
    }

    @Test
    @DisplayName("Comunicazione inesistente per il Banco: RisorsaComunicazioniNonTrovataException")
    void comunicazioneInesistente() {
        when(archivio.ricezioni(comunicazione)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultazione.elencoRiscontri(banco, comunicazione))
                .isInstanceOf(RisorsaComunicazioniNonTrovataException.class);
    }

    // ------------------------------------------------------------------ contenuto

    @Test
    @DisplayName("AC2 - una voce per destinatario: conferma con istante e attesa senza istante")
    void confermatiEInAttesa() {
        UUID confermato = UUID.randomUUID();
        UUID inAttesa = UUID.randomUUID();
        when(archivio.ricezioni(comunicazione)).thenReturn(Optional.of(List.of(
                new RicezioneDestinatario(confermato, PRIMA_CONFERMA),
                new RicezioneDestinatario(inAttesa, null))));
        when(nomiAccount.nomiVisualizzati(Set.of(confermato, inAttesa)))
                .thenReturn(Map.of(confermato, "OPT Alfa", inAttesa, "OPT Beta"));

        List<Riscontro> riscontri = consultazione.elencoRiscontri(banco, comunicazione);

        assertThat(riscontri).containsExactly(
                new Riscontro(confermato, "OPT Alfa", PRIMA_CONFERMA),
                new Riscontro(inAttesa, "OPT Beta", null));
    }

    @Test
    @DisplayName("Comunicazione senza destinatari: elenco vuoto, non un errore")
    void senzaDestinatari() {
        when(archivio.ricezioni(comunicazione)).thenReturn(Optional.of(List.of()));
        // il servizio può anche non chiedere nomi per un insieme vuoto
        lenient().when(nomiAccount.nomiVisualizzati(Set.of())).thenReturn(Map.of());

        assertThat(consultazione.elencoRiscontri(banco, comunicazione)).isEmpty();
    }

    @Test
    @DisplayName("I nomi si chiedono in blocco, una sola volta, per tutti i destinatari")
    void nomiInBlocco() {
        List<UUID> destinatari = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(archivio.ricezioni(comunicazione)).thenReturn(Optional.of(destinatari.stream()
                .map(id -> new RicezioneDestinatario(id, null)).toList()));
        when(nomiAccount.nomiVisualizzati(anySet())).thenReturn(Map.of());

        consultazione.elencoRiscontri(banco, comunicazione);

        verify(nomiAccount).nomiVisualizzati(Set.copyOf(destinatari));
    }

    @Test
    @DisplayName("Ordine per nome senza distinguere maiuscole e minuscole")
    void ordinePerNome() {
        UUID b = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(archivio.ricezioni(comunicazione)).thenReturn(Optional.of(List.of(
                new RicezioneDestinatario(b, null),
                new RicezioneDestinatario(c, PRIMA_CONFERMA),
                new RicezioneDestinatario(a, null))));
        when(nomiAccount.nomiVisualizzati(Set.of(a, b, c)))
                .thenReturn(Map.of(b, "OPT beta", a, "opt Alfa", c, "OPT Gamma"));

        assertThat(consultazione.elencoRiscontri(banco, comunicazione))
                .extracting(Riscontro::nomeVisualizzato)
                .containsExactly("opt Alfa", "OPT beta", "OPT Gamma");
    }

    @Test
    @DisplayName("Omonimi: restano due voci distinte, ciascuna con il proprio account e il proprio stato")
    void omonimi() {
        UUID primo = UUID.randomUUID();
        UUID secondo = UUID.randomUUID();
        when(archivio.ricezioni(comunicazione)).thenReturn(Optional.of(List.of(
                new RicezioneDestinatario(primo, PRIMA_CONFERMA),
                new RicezioneDestinatario(secondo, null))));
        when(nomiAccount.nomiVisualizzati(Set.of(primo, secondo)))
                .thenReturn(Map.of(primo, "Operatore Rossi", secondo, "Operatore Rossi"));

        assertThat(consultazione.elencoRiscontri(banco, comunicazione)).containsExactlyInAnyOrder(
                new Riscontro(primo, "Operatore Rossi", PRIMA_CONFERMA),
                new Riscontro(secondo, "Operatore Rossi", null));
    }

    @Test
    @DisplayName("Un destinatario senza nome noto resta nell'elenco con il suo stato e un nome non vuoto")
    void nomeNonDisponibile() {
        UUID noto = UUID.randomUUID();
        UUID sconosciuto = UUID.randomUUID();
        when(archivio.ricezioni(comunicazione)).thenReturn(Optional.of(List.of(
                new RicezioneDestinatario(noto, null),
                new RicezioneDestinatario(sconosciuto, PRIMA_CONFERMA))));
        when(nomiAccount.nomiVisualizzati(Set.of(noto, sconosciuto))).thenReturn(Map.of(noto, "OPT Nota"));

        List<Riscontro> riscontri = consultazione.elencoRiscontri(banco, comunicazione);

        assertThat(riscontri).hasSize(2);
        assertThat(riscontri).filteredOn(r -> r.destinatarioId().equals(sconosciuto)).singleElement()
                .satisfies(r -> {
                    assertThat(r.nomeVisualizzato()).isNotBlank();
                    assertThat(r.confermataIl()).isEqualTo(PRIMA_CONFERMA);
                });
    }
}