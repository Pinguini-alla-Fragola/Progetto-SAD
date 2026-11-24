package it.unina.picus.monitoraggio.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.AccountSintetico;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;

class SkeletonMonitoraggioServiceTest {

    private final UtenzeApi utenze = mock(UtenzeApi.class);
    private final SkeletonMonitoraggioService servizio = new SkeletonMonitoraggioService(utenze);
    private final IdentitaAutenticata opt = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPT);
    private final IdentitaAutenticata altraOpt = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPT);
    private final IdentitaAutenticata banco = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);
    private final Trimestre fineAnno = new Trimestre(2025, 4);
    private final Trimestre inizioAnno = new Trimestre(2026, 1);

    @Test
    void leggeIConteggiRegistratiPerPeriodoETitolare() {
        when(utenze.cercaAccount(opt.accountId())).thenReturn(Optional.of(
                new AccountSintetico(opt.accountId(), "OPT di prova", RuoloUtente.OPT, StatoUtenza.ATTIVO)));

        var minori = new VoceMisura(1L, "MINORI", 7L);
        var anziani = new VoceMisura(1L, "ANZIANI", 0L);

        servizio.registraConteggiOpt(opt, fineAnno, List.of(minori, anziani));
        servizio.registraConteggiOpt(opt, fineAnno, List.of(minori, anziani));
        servizio.registraConteggiOpt(opt, inizioAnno, List.of(new VoceMisura(1L, "MINORI", 5L)));
        servizio.registraConteggiOpt(opt, new Trimestre(2026, 2), List.of(new VoceMisura(1L, "MINORI", 20L)));
        servizio.registraConteggiOpt(altraOpt, inizioAnno, List.of(minori));

        assertThat(servizio.consultaConteggiOpt(opt, opt.accountId(), fineAnno, fineAnno))
                .containsExactly(new ConteggioOpt(opt.accountId(), anziani), new ConteggioOpt(opt.accountId(), minori));
        assertThat(servizio.consultaConteggiOpt(banco, opt.accountId(), fineAnno, inizioAnno))
                .containsExactly(new ConteggioOpt(opt.accountId(), anziani),
                        new ConteggioOpt(opt.accountId(), new VoceMisura(1L, "MINORI", 12L)));
        assertThat(servizio.consultaConteggiOpt(banco, null, inizioAnno, inizioAnno))
                .containsExactlyInAnyOrder(new ConteggioOpt(opt.accountId(), new VoceMisura(1L, "MINORI", 5L)),
                        new ConteggioOpt(altraOpt.accountId(), minori));
        assertThatThrownBy(() -> servizio.consultaConteggiOpt(altraOpt, opt.accountId(), fineAnno, inizioAnno))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servizio.consultaConteggiOpt(altraOpt, null, fineAnno, inizioAnno))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servizio.consultaConteggiOpt(banco, null, inizioAnno, fineAnno))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conservaLaDichiarazionePrecedenteSeUnaVoceDelBatchVaRifiutata() {
        var esistente = new VoceMisura(1L, "MINORI", 4L);
        var nuova = new VoceMisura(2L, "ANZIANI", 3L);
        servizio.registraConteggiOpt(opt, fineAnno, List.of(esistente));

        assertThatThrownBy(() -> servizio.registraConteggiOpt(opt, fineAnno,
                List.of(nuova, new VoceMisura(1L, "MINORI", 5L))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> servizio.registraConteggiOpt(opt, fineAnno,
                List.of(nuova, new VoceMisura(2L, "ANZIANI", -1L))))
                .isInstanceOf(DatiConteggiNonValidiException.class);

        assertThat(servizio.consultaConteggiOpt(banco, null, fineAnno, fineAnno))
                .containsExactly(new ConteggioOpt(opt.accountId(), esistente));
    }
}
