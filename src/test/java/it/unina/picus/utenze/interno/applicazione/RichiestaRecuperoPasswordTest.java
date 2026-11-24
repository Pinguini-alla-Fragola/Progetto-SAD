package it.unina.picus.utenze.interno.applicazione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.RecuperoPasswordService.DatiInvioRecupero;

/*
Richiesta del link di recupero (US #33) senza Spring né database: esecutore sincrono, posta finta scritta a mano e
servizio sostituito con Mockito per osservare quale passo segue l'invio. Il percorso con il database reale è in
RecuperoPasswordPostgresTest.
*/
@DisplayName("Richiesta del link di recupero: invio, conferma o scarto, coda piena")
class RichiestaRecuperoPasswordTest {

    private static final String EMAIL = "utente@picus.test";
    private static final Executor SINCRONO = Runnable::run;

    private RecuperoPasswordService servizio;
    private PostaFinta posta;

    @BeforeEach
    void prepara() {
        servizio = mock(RecuperoPasswordService.class);
        posta = new PostaFinta();
    }

    @Test
    @DisplayName("Account idoneo: invia il link a quell'indirizzo e poi lo conferma")
    void invioRiuscitoConfermaIlLink() {
        DatiInvioRecupero dati = new DatiInvioRecupero(UUID.randomUUID(), EMAIL, "Mario", "token-in-chiaro");
        when(servizio.preparaLink(EMAIL)).thenReturn(Optional.of(dati));

        assertTrue(new RichiestaRecuperoPassword(servizio, posta, SINCRONO).richiedi(EMAIL));

        assertEquals(List.of(EMAIL + "|Mario|token-in-chiaro"), posta.inviate);
        verify(servizio).confermaConsegna(dati.idLink());
        verify(servizio, never()).scartaLink(any());
    }

    @Test
    @DisplayName("Posta in errore: il link nuovo è scartato e la richiesta risulta comunque presa in carico")
    void invioFallitoScartaIlLink() {
        DatiInvioRecupero dati = new DatiInvioRecupero(UUID.randomUUID(), EMAIL, "Mario", "token-in-chiaro");
        when(servizio.preparaLink(EMAIL)).thenReturn(Optional.of(dati));
        posta.guasta = true;

        assertTrue(new RichiestaRecuperoPassword(servizio, posta, SINCRONO).richiedi(EMAIL));

        verify(servizio).scartaLink(dati.idLink());
        verify(servizio, never()).confermaConsegna(any());
    }

    @Test
    @DisplayName("Indirizzo non idoneo: nessuna email, stessa risposta")
    void indirizzoNonIdoneo() {
        when(servizio.preparaLink(EMAIL)).thenReturn(Optional.empty());

        assertTrue(new RichiestaRecuperoPassword(servizio, posta, SINCRONO).richiedi(EMAIL));

        assertTrue(posta.inviate.isEmpty());
        verify(servizio, never()).confermaConsegna(any());
        verify(servizio, never()).scartaLink(any());
    }

    @Test
    @DisplayName("Un errore del servizio resta in secondo piano e non arriva al chiamante")
    void erroreDelServizioNonSiPropaga() {
        when(servizio.preparaLink(EMAIL)).thenThrow(new IllegalStateException("database non raggiungibile"));

        assertTrue(new RichiestaRecuperoPassword(servizio, posta, SINCRONO).richiedi(EMAIL));
        assertTrue(posta.inviate.isEmpty());
    }

    @Test
    @DisplayName("Coda piena: la richiesta non è presa in carico e il servizio non viene chiamato")
    void codaPiena() {
        Executor pieno = lavoro -> {
            throw new RejectedExecutionException("coda piena");
        };

        assertFalse(new RichiestaRecuperoPassword(servizio, posta, pieno).richiedi(EMAIL));
        verify(servizio, never()).preparaLink(any());
    }

    @Test
    @DisplayName("Il testo dei dati di invio non contiene il token in chiaro")
    void tokenAssenteDalTesto() {
        DatiInvioRecupero dati = new DatiInvioRecupero(UUID.randomUUID(), EMAIL, "Mario", "segreto-123");

        assertFalse(dati.toString().contains("segreto-123"));
    }

    /** Fake manuale della posta: registra gli invii e, se guasta, li rifiuta come un SMTP in errore. */
    private static final class PostaFinta implements InvioEmail {
        final List<String> inviate = new ArrayList<>();
        boolean guasta;

        @Override
        public void inviaLinkAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
            throw new AssertionError("il recupero non invia link di attivazione");
        }

        @Override
        public void inviaLinkResetPassword(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
            if (guasta) {
                throw new IllegalStateException("SMTP non disponibile");
            }
            inviate.add(emailDestinatario + "|" + nomeDestinatario + "|" + tokenInChiaro);
        }
    }
}