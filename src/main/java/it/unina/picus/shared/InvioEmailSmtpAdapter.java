package it.unina.picus.shared;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class InvioEmailSmtpAdapter implements InvioEmail {

    private static final Logger LOG = LoggerFactory.getLogger(InvioEmailSmtpAdapter.class);

    private static final String PERCORSO_ATTIVAZIONE = "/attiva";
    private static final String PERCORSO_RECUPERO = "/recupera";

    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final String mittente;
    private final String nomeMittente;

    public InvioEmailSmtpAdapter(
            JavaMailSender mailSender,
            @Value("${picus.app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${picus.mail.from:noreply@strawberrypenguin.it}") String mittente,
            @Value("${picus.mail.from-name:Piattaforma PICUS}") String nomeMittente) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
        this.mittente = mittente;
        this.nomeMittente = nomeMittente;
    }

    @Override
    public void inviaLinkAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        String linkAttivazione = costruisciLink(tokenInChiaro);
        String oggetto = "PICUS | Attivazione del tuo account";
        String corpoHtml = costruisciCorpoEmailHtml(nomeDestinatario, emailDestinatario, linkAttivazione);
        String corpoTesto = costruisciCorpoEmailTesto(nomeDestinatario, emailDestinatario, linkAttivazione);

        try {
            MimeMessage messaggio = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(messaggio, true, StandardCharsets.UTF_8.name());

            helper.setFrom(mittente, nomeMittente);
            helper.setTo(emailDestinatario);
            helper.setSubject(oggetto);
            helper.setText(corpoTesto, corpoHtml);

            mailSender.send(messaggio);
            LOG.info("Email di attivazione inviata con successo (trasporto=SMTP)");

        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            LOG.error("Invio email di attivazione fallito (trasporto=SMTP, causa={})", e.getClass().getSimpleName());
            throw new IllegalStateException("Impossibile inviare l'email di attivazione", e);
        }
    }

    @Override
    public void inviaLinkResetPassword(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        String link = costruisciLink(PERCORSO_RECUPERO, tokenInChiaro);
        ModelloEmailLink.Contenuto contenuto = ModelloEmailLink.recuperoPassword(nomeDestinatario, link);
        spedisci(emailDestinatario, contenuto, "recupero password");
    }

    /** Spedisce il messaggio; in caso di errore registra solo il tipo di email e la causa, mai indirizzo o link. */
    private void spedisci(String emailDestinatario, ModelloEmailLink.Contenuto contenuto, String tipoEmail) {
        try {
            MimeMessage messaggio = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(messaggio, true, StandardCharsets.UTF_8.name());

            helper.setFrom(mittente, nomeMittente);
            helper.setTo(emailDestinatario);
            helper.setSubject(contenuto.oggetto());
            helper.setText(contenuto.testo(), contenuto.html());

            mailSender.send(messaggio);
            LOG.info("Email di {} inviata con successo (trasporto=SMTP)", tipoEmail);

        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            LOG.error("Invio email di {} fallito (trasporto=SMTP, causa={})", tipoEmail, e.getClass().getSimpleName());
            throw new IllegalStateException("Impossibile inviare l'email di " + tipoEmail, e);
        }
    }

    String costruisciLink(String tokenInChiaro) {
        return costruisciLink(PERCORSO_ATTIVAZIONE, tokenInChiaro);
    }

    private String costruisciLink(String percorso, String tokenInChiaro) {
        String baseNormalizzata = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String tokenEncoded = URLEncoder.encode(tokenInChiaro, StandardCharsets.UTF_8);
        return baseNormalizzata + percorso + "?token=" + tokenEncoded;
    }

    private String costruisciCorpoEmailTesto(String nomeDestinatario, String emailDestinatario, String linkAttivazione) {
        String saluto = (nomeDestinatario != null && !nomeDestinatario.isBlank())
                ? "Gentile " + nomeDestinatario + ","
                : "Gentile Utente,";

        return """
                %s

                L'Amministratore del Banco ha creato il tuo accesso alla piattaforma PICUS.
                Il tuo account (%s) è stato creato ed è attualmente in attesa di attivazione.

                Per completare l'attivazione e impostare la tua password personale, apri il seguente link:
                %s

                Informazioni importanti:
                - Il link è strettamente personale e monouso: una volta impostata la password, non sarà più riutilizzabile.
                - Per motivi di sicurezza, il link ha una validità limitata nel tempo. Se dovesse scadere, apri la pagina del link e richiedine uno nuovo indicando questo indirizzo email.
                - Se non riconosci questa registrazione, ti invitiamo a ignorare questa comunicazione.

                Cordiali saluti,
                Team PICUS
                (Questa è un'email generata automaticamente, si prega di non rispondere a questo indirizzo.)
                """.formatted(saluto, emailDestinatario, linkAttivazione);
    }

    private String costruisciCorpoEmailHtml(String nomeDestinatario, String emailDestinatario, String linkAttivazione) {
        String nomeSicuro = (nomeDestinatario != null && !nomeDestinatario.isBlank())
                ? HtmlUtils.htmlEscape(nomeDestinatario.trim())
                : "Utente";
        String emailSicura = HtmlUtils.htmlEscape(emailDestinatario);
        String linkSicuro = HtmlUtils.htmlEscape(linkAttivazione);

        return """
                <!DOCTYPE html>
                <html lang="it">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Attivazione Account PICUS</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f4f6f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1f2937;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color: #f4f6f9; padding: 32px 16px;">
                        <tr>
                            <td align="center">
                                <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="max-width: 600px; width: 100%%; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); border: 1px solid #e5e7eb;">
                                    <tr>
                                        <td style="background-color: #1e3a8a; padding: 28px 32px; text-align: left;">
                                            <span style="display: inline-block; font-size: 22px; font-weight: 700; letter-spacing: 0.5px; color: #ffffff;">PICUS</span>
                                            <span style="display: block; font-size: 13px; color: #bfdbfe; margin-top: 4px;">Piattaforma Gestione Organizzazioni e Accessi</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 32px;">
                                            <h1 style="margin: 0 0 16px 0; font-size: 20px; font-weight: 600; color: #111827;">
                                                Attivazione del tuo account
                                            </h1>
                                            <p style="margin: 0 0 16px 0; font-size: 15px; line-height: 1.6; color: #374151;">
                                                Gentile <strong>%s</strong>,
                                            </p>
                                            <p style="margin: 0 0 20px 0; font-size: 15px; line-height: 1.6; color: #374151;">
                                                L'Amministratore del Banco ha creato il tuo accesso alla piattaforma <strong>PICUS</strong>. Il tuo account è stato creato ed è attualmente in stato <em>«In attesa di attivazione»</em>.
                                            </p>
                                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; margin-bottom: 24px;">
                                                <tr>
                                                    <td style="padding: 14px 18px; font-size: 14px; color: #475569;">
                                                        <strong style="color: #0f172a;">Email di accesso associata:</strong> %s<br/>
                                                        <strong style="color: #0f172a;">Stato attuale:</strong> In attesa di impostazione password
                                                    </td>
                                                </tr>
                                            </table>
                                            <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 1.6; color: #374151;">
                                                Per attivare l'account e scegliere la tua password personale, clicca sul pulsante qui sotto:
                                            </p>
                                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin: 0 auto 28px auto;">
                                                <tr>
                                                    <td align="center" style="border-radius: 6px; background-color: #2563eb;">
                                                        <a href="%s" target="_blank" style="display: inline-block; padding: 14px 28px; font-size: 15px; font-weight: 600; color: #ffffff; text-decoration: none; border-radius: 6px;">
                                                            Attiva account e imposta password
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>
                                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color: #fffbeb; border-left: 4px solid #f59e0b; border-radius: 4px; margin-bottom: 24px;">
                                                <tr>
                                                    <td style="padding: 12px 16px; font-size: 13px; line-height: 1.5; color: #92400e;">
                                                        <strong>Note importanti sulla sicurezza:</strong>
                                                        <ul style="margin: 6px 0 0 0; padding-left: 18px;">
                                                            <li>Questo link è <strong>monouso</strong>: dopo aver impostato la password non sarà più valido.</li>
                                                            <li>Il link è soggetto a <strong>scadenza temporale</strong>. In caso di scadenza, apri la pagina del link e richiedine uno nuovo indicando questo indirizzo email.</li>
                                                        </ul>
                                                    </td>
                                                </tr>
                                            </table>
                                            <p style="margin: 0; font-size: 12px; line-height: 1.5; color: #6b7280; word-break: break-all;">
                                                Se il pulsante non dovesse funzionare, copia e incolla il seguente indirizzo nella barra del browser:<br/>
                                                <a href="%s" style="color: #2563eb; text-decoration: underline;">%s</a>
                                            </p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="background-color: #f9fafb; padding: 20px 32px; border-top: 1px solid #e5e7eb; text-align: center; font-size: 12px; line-height: 1.5; color: #9ca3af;">
                                            Questa comunicazione è stata inviata automaticamente dal sistema <strong>PICUS</strong>.<br/>
                                            Si prega di non rispondere a questa email. Se hai ricevuto questo messaggio per errore, puoi ignorarlo.
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(nomeSicuro, emailSicura, linkSicuro, linkSicuro, linkSicuro);
    }
}