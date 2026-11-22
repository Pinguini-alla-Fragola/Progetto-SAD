package it.unina.picus.utenze.interno.applicazione;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;
import it.unina.picus.utenze.interno.applicazione.ElaborazioneRigaOpt.RigaElaborata;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.DatiOpt;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.Esito;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt.RisultatoRiga;

class ImportazioneOptServiceTest {

    @Test
    void restituisceIlReportDiPiuRigheMentreSmtpEAncoraBloccato() throws Exception {
        var elaborazione = mock(ElaborazioneRigaOpt.class);
        when(elaborazione.applica(anyLong(), any(DatiOpt.class))).thenAnswer(chiamata -> {
            long numeroRiga = chiamata.getArgument(0);
            DatiOpt dati = chiamata.getArgument(1);
            return new RigaElaborata(new RisultatoRiga(numeroRiga, Esito.CREATA, List.of()),
                    Optional.of(new DatiInvioAttivazione(dati.email(), dati.nome(), "token-di-test")));
        });

        var primoInvioInCorso = new CountDownLatch(1);
        var sbloccaSmtp = new CountDownLatch(1);
        var invitiConsegnati = new CountDownLatch(2);
        var invioEmail = mock(InvioEmail.class);
        doAnswer(chiamata -> {
            primoInvioInCorso.countDown();
            if (sbloccaSmtp.await(10, SECONDS)) {
                invitiConsegnati.countDown();
            }
            return null;
        }).when(invioEmail).inviaLinkAttivazione(anyString(), anyString(), anyString());

        var csv = new ByteArrayInputStream("""
                codice_struttura,nome_opt,comune,codice_fiscale,email
                OPT-1,Prima OPT,F839,12345678901,prima@picus.test
                OPT-2,Seconda OPT,F839,12345678902,seconda@picus.test
                """.getBytes(StandardCharsets.UTF_8));

        var esecutoreInviti = new ConfigurazioneInvitiImportazioneOpt().esecutoreInvitiImportazioneOpt();
        esecutoreInviti.initialize();
        var servizio = new ImportazioneOptService(elaborazione, invioEmail, esecutoreInviti);
        try (var richieste = Executors.newSingleThreadExecutor()) {
            var risposta = richieste.submit(() -> servizio.importa(csv));
            try {
                assertThat(primoInvioInCorso.await(5, SECONDS)).isTrue();
                assertThat(risposta.get(2, SECONDS).righe())
                        .extracting(RisultatoRiga::esito)
                        .containsExactly(Esito.CREATA, Esito.CREATA);
                assertThat(invitiConsegnati.getCount()).isEqualTo(2);
            } finally {
                sbloccaSmtp.countDown();
            }
            assertThat(invitiConsegnati.await(5, SECONDS)).isTrue();
        } finally {
            esecutoreInviti.shutdown();
        }
    }
}
