package it.unina.picus.shared;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/*
Email del recupero password (US #33, AC1): testo del messaggio (ModelloEmailLink) e spedizione con l'adattatore SMTP.
Il server di posta è sostituito da un JavaMailSenderImpl che trattiene i messaggi invece di spedirli: il messaggio
costruito è quello vero, con destinatario, oggetto e le due versioni del corpo.
*/
@DisplayName("Email con il link di recupero password")
class EmailRecuperoPasswordTest {

    private static final String LINK = "https://picus.test/recupera?token=abc_DEF-123";

    // testo dell'email

    @Test
    @DisplayName("Testo e HTML contengono il link, la durata di 15 minuti e il saluto con il nome")
    void contenutoDellEmail() {
        ModelloEmailLink.Contenuto email = ModelloEmailLink.recuperoPassword("Mario Rossi", LINK);

        assertEquals("PICUS | Recupero della password", email.oggetto());
        for (String corpo : List.of(email.testo(), email.html())) {
            assertTrue(corpo.contains(LINK), "link assente");
            assertTrue(corpo.contains("15 minuti"), "durata assente");
            assertTrue(corpo.contains("monouso"), "monouso non indicato");
        }
        assertTrue(email.testo().startsWith("Gentile Mario Rossi,"));
        assertTrue(email.html().contains("Gentile <strong>Mario Rossi</strong>,"));
        assertTrue(email.html().contains("href=\"" + LINK + "\""));
    }

    @Test
    @DisplayName("Senza nome il saluto è generico")
    void senzaNome() {
        for (String nome : new String[] {null, "", "   "}) {
            ModelloEmailLink.Contenuto email = ModelloEmailLink.recuperoPassword(nome, LINK);
            assertTrue(email.testo().startsWith("Gentile Utente,"));
            assertTrue(email.html().contains("Gentile Utente,"));
        }
    }

    @Test
    @DisplayName("Nome e link sono resi sicuri per l'HTML")
    void htmlSicuro() {
        String link = "https://picus.test/recupera?token=a&x=\"b\"";
        ModelloEmailLink.Contenuto email = ModelloEmailLink.recuperoPassword("<script>alert(1)</script>", link);

        assertFalse(email.html().contains("<script>"));
        assertTrue(email.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(email.html().contains("token=a&amp;x=&quot;b&quot;"));
    }

    // spedizione con l'adattatore SMTP

    @Test
    @DisplayName("L'adattatore spedisce all'indirizzo dell'account il link a /recupera con il token codificato")
    void spedizioneSmtp() throws Exception {
        PostaTrattenuta posta = new PostaTrattenuta();
        InvioEmailSmtpAdapter adattatore =
                new InvioEmailSmtpAdapter(posta, "https://picus.test/", "noreply@picus.test", "PICUS");

        adattatore.inviaLinkResetPassword("utente@picus.test", "Mario", "a+b/c=");

        assertEquals(1, posta.trattenute.size());
        MimeMessage messaggio = posta.trattenute.get(0);
        assertArrayEquals(new Address[] {new InternetAddress("utente@picus.test")}, messaggio.getAllRecipients());
        assertEquals("noreply@picus.test", ((InternetAddress) messaggio.getFrom()[0]).getAddress());
        assertEquals("PICUS | Recupero della password", messaggio.getSubject());
        String corpo = testoDi(messaggio);
        // una sola barra dopo l'host e token codificato per l'indirizzo
        assertTrue(corpo.contains("https://picus.test/recupera?token=a%2Bb%2Fc%3D"), corpo);
        assertFalse(corpo.contains("token=a+b/c="));
        assertTrue(corpo.contains("15 minuti"));
    }

    @Test
    @DisplayName("Se il server rifiuta, l'adattatore segnala l'errore senza riportare indirizzo o token")
    void spedizioneFallita() {
        PostaTrattenuta posta = new PostaTrattenuta();
        posta.guasta = true;
        InvioEmailSmtpAdapter adattatore =
                new InvioEmailSmtpAdapter(posta, "https://picus.test", "noreply@picus.test", "PICUS");

        IllegalStateException errore = assertThrows(IllegalStateException.class,
                () -> adattatore.inviaLinkResetPassword("utente@picus.test", "Mario", "token-segreto"));

        assertFalse(errore.getMessage().contains("token-segreto"));
        assertFalse(errore.getMessage().contains("utente@picus.test"));
    }

    /** Raccoglie il testo di tutte le parti del messaggio, in chiaro e HTML. */
    private static String testoDi(Part parte) throws Exception {
        // Il messaggio non è stato spedito davvero, quindi le intestazioni non sono aggiornate: si guarda il contenuto.
        Object contenuto = parte.getContent();
        if (contenuto instanceof String testoSemplice) {
            return testoSemplice;
        }
        StringBuilder testo = new StringBuilder();
        if (contenuto instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart figlia = multipart.getBodyPart(i);
                testo.append(testoDi(figlia)).append('\n');
            }
        }
        return testo.toString();
    }

    /** Server di posta finto: costruisce i messaggi veri ma li trattiene, oppure li rifiuta se guasto. */
    private static final class PostaTrattenuta extends JavaMailSenderImpl {
        final List<MimeMessage> trattenute = new ArrayList<>();
        boolean guasta;

        @Override
        protected void doSend(MimeMessage[] messaggi, Object[] originali) {
            if (guasta) {
                throw new MailSendException("connessione rifiutata");
            }
            trattenute.addAll(List.of(messaggi));
        }
    }
}