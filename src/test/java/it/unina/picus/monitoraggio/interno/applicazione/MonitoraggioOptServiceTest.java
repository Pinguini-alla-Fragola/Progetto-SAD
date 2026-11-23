package it.unina.picus.monitoraggio.interno.applicazione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
import it.unina.picus.monitoraggio.interno.persistenza.CategoriaMonitoraggioJpa;
import it.unina.picus.monitoraggio.interno.persistenza.CategoriaMonitoraggioRepository;
import it.unina.picus.monitoraggio.interno.persistenza.ConfigurazioneMonitoraggioRepository;
import it.unina.picus.monitoraggio.interno.persistenza.ConteggioOptJpa;
import it.unina.picus.monitoraggio.interno.persistenza.ConteggioOptRepository;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.AccountSintetico;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task #174 · Salvataggio e isolamento dei conteggi OPT")
class MonitoraggioOptServiceTest {

    @Mock private ConteggioOptRepository conteggiRepo;
    @Mock private CategoriaMonitoraggioRepository categorieRepo;
    @Mock private ConfigurazioneMonitoraggioRepository configRepo;
    @Mock private ConsultazioneCatalogoApi catalogo;
    @Mock private UtenzeApi utenze;

    private MonitoraggioOptService service;
    private final Instant now = Instant.parse("2026-05-10T10:00:00Z");

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        service = new MonitoraggioOptService(
                conteggiRepo, categorieRepo, configRepo, catalogo, utenze, Optional.of(fixedClock));
    }

    @Test
    @DisplayName("Salva i conteggi validi associandoli alla OPT titolare, periodo e misura")
    void salvataggioConteggiValidi() {
        UUID optId = UUID.randomUUID();
        IdentitaAutenticata opt = mockIdentita(optId, RuoloUtente.OPT);
        Trimestre q1 = new Trimestre(2026, 1);

        attivaCategorie("PERSONE_RAGGIUNTE", "MINORI");
        when(catalogo.cercaMisura(10L)).thenReturn(Optional.of(mock()));
        when(conteggiRepo.findByOptIdAndAnnoAndTrimestreAndMisuraIdAndVoce(any(), any(int.class), any(int.class), any(long.class), any()))
                .thenReturn(Optional.empty());

        service.registraConteggiOpt(opt, q1, List.of(
                new VoceMisura(10L, "PERSONE_RAGGIUNTE", 42L),
                new VoceMisura(10L, "MINORI", 15L)));

        ArgumentCaptor<ConteggioOptJpa> captor = ArgumentCaptor.forClass(ConteggioOptJpa.class);
        verify(conteggiRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(optId, captor.getAllValues().get(0).getOptId());
        assertEquals(42L, captor.getAllValues().get(0).getValore());
        assertEquals(15L, captor.getAllValues().get(1).getValore());
    }

    @Test
    @DisplayName("La ritrasmissione identica dello stesso invio è idempotente")
    void ritrasmissioneIdenticaIdempotente() {
        UUID optId = UUID.randomUUID();
        IdentitaAutenticata opt = mockIdentita(optId, RuoloUtente.OPT);
        Trimestre q1 = new Trimestre(2026, 1);

        attivaCategorie("MINORI");
        when(catalogo.cercaMisura(10L)).thenReturn(Optional.of(mock()));

        ConteggioOptJpa esistente = new ConteggioOptJpa(
                UUID.randomUUID(), optId, 2026, 1, 10L, "MINORI", 15L, now.minusSeconds(60));
        when(conteggiRepo.findByOptIdAndAnnoAndTrimestreAndMisuraIdAndVoce(optId, 2026, 1, 10L, "MINORI"))
                .thenReturn(Optional.of(esistente));

        service.registraConteggiOpt(opt, q1, List.of(new VoceMisura(10L, "MINORI", 15L)));

        verify(conteggiRepo, never()).save(any());
    }

    @Test
    @DisplayName("Dati non validi o negativi vengono rifiutati con dettaglio sui campi senza salvare")
    void validazioneDatiNonValidi() {
        IdentitaAutenticata opt = mockIdentita(UUID.randomUUID(), RuoloUtente.OPT);
        Trimestre q1 = new Trimestre(2026, 1);

        when(categorieRepo.findByAttivaTrueOrderByCodiceAsc()).thenReturn(List.of());
        when(catalogo.cercaMisura(10L)).thenReturn(Optional.of(mock()));

        DatiConteggiNonValidiException ex = assertThrows(
                DatiConteggiNonValidiException.class,
                () -> service.registraConteggiOpt(opt, q1, List.of(new VoceMisura(10L, "VOCE_INESISTENTE", -5L))));

        assertTrue(ex.campi().containsKey("voci[0].voce"));
        assertTrue(ex.campi().containsKey("voci[0].valore"));
        verify(conteggiRepo, never()).save(any());
    }

    @Test
    @DisplayName("La consultazione su più trimestri aggrega il cumulativo per OPT, misura e voce")
    void consultazioneCumulativa() {
        UUID optId = UUID.randomUUID();
        IdentitaAutenticata opt = mockIdentita(optId, RuoloUtente.OPT);

        AccountSintetico accountOpt = mock(AccountSintetico.class);
        when(accountOpt.ruolo()).thenReturn(RuoloUtente.OPT);
        when(utenze.cercaAccount(optId)).thenReturn(Optional.of(accountOpt));

        ConteggioOptJpa r1 = new ConteggioOptJpa(UUID.randomUUID(), optId, 2026, 1, 10L, "PERSONE_RAGGIUNTE", 20L, now);
        ConteggioOptJpa r2 = new ConteggioOptJpa(UUID.randomUUID(), optId, 2026, 2, 10L, "PERSONE_RAGGIUNTE", 30L, now);
        when(conteggiRepo.cercaNelPeriodo(optId, 20261, 20262)).thenReturn(List.of(r1, r2));

        List<ConteggioOpt> risultato = service.consultaConteggiOpt(
                opt, optId, new Trimestre(2026, 1), new Trimestre(2026, 2));

        assertEquals(List.of(new ConteggioOpt(optId, new VoceMisura(10L, "PERSONE_RAGGIUNTE", 50L))), risultato);
    }

    @Test
    @DisplayName("Isolamento: una OPT estranea o un attore non OPT riceve SecurityException")
    void isolamentoTraAttori() {
        UUID optTitolare = UUID.randomUUID();
        IdentitaAutenticata altraOpt = mockIdentita(UUID.randomUUID(), RuoloUtente.OPT);
        IdentitaAutenticata banco = mockIdentita(UUID.randomUUID(), RuoloUtente.BANCO);
        Trimestre q1 = new Trimestre(2026, 1);

        assertThrows(SecurityException.class, () -> service.consultaConteggiOpt(altraOpt, optTitolare, q1, q1));
        assertThrows(SecurityException.class, () -> service.registraConteggiOpt(
                banco, q1, List.of(new VoceMisura(10L, "PERSONE_RAGGIUNTE", 10L))));
        verifyNoInteractions(conteggiRepo);
    }

    private void attivaCategorie(String... codici) {
        List<CategoriaMonitoraggioJpa> lista = java.util.Arrays.stream(codici)
                .map(c -> {
                    CategoriaMonitoraggioJpa cat = mock(CategoriaMonitoraggioJpa.class);
                    when(cat.getCodice()).thenReturn(c);
                    return cat;
                })
                .toList();
        when(categorieRepo.findByAttivaTrueOrderByCodiceAsc()).thenReturn(lista);
    }

    private IdentitaAutenticata mockIdentita(UUID id, RuoloUtente ruolo) {
        IdentitaAutenticata identita = mock(IdentitaAutenticata.class);
        lenient().when(identita.accountId()).thenReturn(id);
        lenient().when(identita.ruolo()).thenReturn(ruolo);
        return identita;
    }
}