package it.unina.picus.shared;

import java.util.List;

import org.springframework.web.util.HtmlUtils;

/**
 * Testi delle email che portano un link monouso. Costruisce l'oggetto e le due versioni del corpo, in testo e in
 * HTML, a partire da pochi elementi: i dati scritti da una persona, come il nome, vengono resi sicuri per l'HTML,
 * così un nome con simboli non può alterare la pagina dell'email.
 */
final class ModelloEmailLink {

    /** Oggetto e corpo dell'email nelle due versioni. */
    record Contenuto(String oggetto, String testo, String html) {}

    /** Durata scritta nelle regole degli accessi; è anche il valore di partenza di {@code picus.utenze.recupero.validita}. */
    private static final int VALIDITA_RECUPERO_MINUTI = 15;

    private ModelloEmailLink() {
    }

    /**
     * Email con il link per scegliere una nuova password.
     *
     * @param nome nome mostrato nel saluto, se noto
     * @param link indirizzo completo del link
     */
    static Contenuto recuperoPassword(String nome, String link) {
        String introduzione = "Hai chiesto di recuperare la password del tuo account sulla piattaforma PICUS.";
        List<String> avvisi = List.of(
                "Il link è personale e monouso: dopo aver scelto la nuova password non sarà più valido.",
                "Il link vale " + VALIDITA_RECUPERO_MINUTI + " minuti. Se scade, chiedine uno nuovo dalla pagina di accesso.",
                "Se non hai chiesto tu il recupero, ignora questa email: la tua password non cambia.");
        return costruisci("PICUS | Recupero della password", "Recupero della password", nome, introduzione,
                "Scegli una nuova password", link, avvisi);
    }

    private static Contenuto costruisci(String oggetto, String titolo, String nome, String introduzione,
            String testoPulsante, String link, List<String> avvisi) {
        boolean conNome = nome != null && !nome.isBlank();
        String salutoTesto = conNome ? "Gentile " + nome.trim() + "," : "Gentile Utente,";
        String salutoHtml = conNome ? "Gentile <strong>" + HtmlUtils.htmlEscape(nome.trim()) + "</strong>," : "Gentile Utente,";

        StringBuilder testo = new StringBuilder()
                .append(salutoTesto).append("\n\n")
                .append(introduzione).append("\n\n")
                .append(testoPulsante).append(" aprendo questo link:\n")
                .append(link).append("\n\n")
                .append("Informazioni importanti:\n");
        avvisi.forEach(avviso -> testo.append("- ").append(avviso).append('\n'));
        testo.append("\nCordiali saluti,\nTeam PICUS\n")
                .append("(Questa è un'email generata automaticamente, si prega di non rispondere a questo indirizzo.)\n");

        String linkSicuro = HtmlUtils.htmlEscape(link);
        StringBuilder elenco = new StringBuilder();
        avvisi.forEach(avviso -> elenco.append("<li>").append(HtmlUtils.htmlEscape(avviso)).append("</li>"));
        String html = """
                <!DOCTYPE html>
                <html lang="it">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%1$s</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f4f6f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1f2937;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color: #f4f6f9; padding: 32px 16px;">
                        <tr>
                            <td align="center">
                                <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="max-width: 600px; width: 100%%; background-color: #ffffff; border-radius: 8px; border: 1px solid #e5e7eb;">
                                    <tr>
                                        <td style="background-color: #1e3a8a; padding: 28px 32px;">
                                            <span style="font-size: 22px; font-weight: 700; color: #ffffff;">PICUS</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 32px;">
                                            <h1 style="margin: 0 0 16px 0; font-size: 20px; font-weight: 600; color: #111827;">%1$s</h1>
                                            <p style="margin: 0 0 16px 0; font-size: 15px; line-height: 1.6; color: #374151;">%2$s</p>
                                            <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 1.6; color: #374151;">%3$s</p>
                                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin: 0 auto 28px auto;">
                                                <tr>
                                                    <td align="center" style="border-radius: 6px; background-color: #2563eb;">
                                                        <a href="%4$s" target="_blank" style="display: inline-block; padding: 14px 28px; font-size: 15px; font-weight: 600; color: #ffffff; text-decoration: none; border-radius: 6px;">%5$s</a>
                                                    </td>
                                                </tr>
                                            </table>
                                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color: #fffbeb; border-left: 4px solid #f59e0b; border-radius: 4px; margin-bottom: 24px;">
                                                <tr>
                                                    <td style="padding: 12px 16px; font-size: 13px; line-height: 1.5; color: #92400e;">
                                                        <strong>Note importanti sulla sicurezza:</strong>
                                                        <ul style="margin: 6px 0 0 0; padding-left: 18px;">%6$s</ul>
                                                    </td>
                                                </tr>
                                            </table>
                                            <p style="margin: 0; font-size: 12px; line-height: 1.5; color: #6b7280; word-break: break-all;">
                                                Se il pulsante non funziona, copia e incolla questo indirizzo nel browser:<br/>
                                                <a href="%4$s" style="color: #2563eb; text-decoration: underline;">%4$s</a>
                                            </p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="background-color: #f9fafb; padding: 20px 32px; border-top: 1px solid #e5e7eb; text-align: center; font-size: 12px; line-height: 1.5; color: #9ca3af;">
                                            Questa comunicazione è stata inviata automaticamente dal sistema <strong>PICUS</strong>. Si prega di non rispondere a questa email.
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(titolo), salutoHtml, HtmlUtils.htmlEscape(introduzione), linkSicuro,
                HtmlUtils.htmlEscape(testoPulsante), elenco);
        return new Contenuto(oggetto, testo.toString(), html);
    }
}
