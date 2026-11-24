package it.unina.picus.comunicazioni.web;

import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.assertCampiDaCompletare;
import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.campi;
import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.inviata;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.comunicazioni.web.ComunicazioniHttp.File;
import it.unina.picus.comunicazioni.web.ComunicazioniHttp.Inviata;
import it.unina.picus.shared.ComunicazioniBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * CA-03 della #55 e file rifiutati: con un allegato ammesso i destinatari autorizzati aprono la comunicazione e
 * scaricano l'allegato; un allegato non ammesso blocca l'invio con l'indicazione del campo {@code files}.
 *
 * <p>Tecniche: <b>classi di equivalenza</b> sui tipi di file, una per tipo ammesso (PDF, PNG, JPEG, TXT) e una
 * per ogni motivo di rifiuto (tipo non ammesso, contenuto incoerente con l'estensione, file troncato, testo non
 * UTF-8, nome non valido); <b>valori limite</b> sul numero (5 e 6 file) e sulla dimensione (vuoto, 5 MB,
 * 5 MB + 1 byte). I file sono generati dal test: immagini con ImageIO, PDF di una pagina con PDFBox, testo
 * UTF-8.</p>
 *
 * <p>Nota: MockMvc non applica il {@code max-file-size} di {@code application.yml}; il limite di 5 MB osservato
 * qui è quello della regola sugli allegati. Il nome non valido si prova con 201 caratteri: un nome con un carattere
 * di controllo non è ottenibile via multipart, perché l'header {@code Content-Disposition} della parte, costruito
 * con {@code ContentDisposition} di Spring, lo elimina e al server arriva un nome valido.</p>
 */
@DisplayName("#55 CA-03 · Allegati ammessi e rifiutati")
class AllegatiComunicazioneTest extends ComunicazioniBlackBoxTest {

    private static final int CINQUE_MB = 5 * 1024 * 1024;

    /** Tipi ammessi con il loro generatore e il Content-Type atteso al download. */
    enum TipoAmmesso {
        PDF("pdf", MediaType.APPLICATION_PDF, AllegatiComunicazioneTest::pdf),
        PNG("png", MediaType.IMAGE_PNG, AllegatiComunicazioneTest::png),
        JPEG("jpg", MediaType.IMAGE_JPEG, AllegatiComunicazioneTest::jpeg),
        TXT("txt", new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8), AllegatiComunicazioneTest::testo);

        final String estensione;
        final MediaType scaricato;
        final Function<String, byte[]> genera;

        TipoAmmesso(String estensione, MediaType scaricato, Function<String, byte[]> genera) {
            this.estensione = estensione;
            this.scaricato = scaricato;
            this.genera = genera;
        }

        String tipoMime() {
            return this == TXT ? MediaType.TEXT_PLAIN_VALUE : scaricato.toString();
        }

        File file(String marcatore) {
            return new File("allegato-" + marcatore + "." + estensione, tipoMime(), genera.apply(marcatore));
        }
    }

    private ComunicazioniHttp comunicazioni;
    private Account banco;
    private Account destinatario;

    @BeforeEach
    void preparaClient() {
        comunicazioni = new ComunicazioniHttp(http);
        banco = scenario.banco();
        destinatario = scenario.optRichiedente();
    }

    // ------------------------------------------------------------------ ammessi

    @ParameterizedTest(name = "{0}")
    @EnumSource(TipoAmmesso.class)
    @DisplayName("CA-03 · allegato ammesso: il destinatario apre la comunicazione e scarica il file identico")
    void allegatoAmmesso(TipoAmmesso tipo) throws Exception {
        String marcatore = marcatore();
        File file = tipo.file(marcatore);

        UUID id = inviaAlGruppo(marcatore, List.of(file)).id();

        verificaAllegati(destinatario, id, List.of(file), List.of(tipo));
    }

    @Test
    @DisplayName("CA-03 · valore limite: 5 file ammessi sono accettati e scaricabili")
    void cinqueFile() throws Exception {
        String marcatore = marcatore();
        List<TipoAmmesso> tipi = List.of(TipoAmmesso.PDF, TipoAmmesso.PNG, TipoAmmesso.JPEG, TipoAmmesso.TXT,
                TipoAmmesso.TXT);
        List<File> file = new ArrayList<>();
        for (int i = 0; i < tipi.size(); i++) {
            TipoAmmesso tipo = tipi.get(i);
            file.add(new File("file" + i + "-" + marcatore + "." + tipo.estensione, tipo.tipoMime(),
                    tipo.genera.apply(marcatore + i)));
        }

        UUID id = inviaAlGruppo(marcatore, file).id();

        verificaAllegati(destinatario, id, file, tipi);
    }

    @Test
    @DisplayName("CA-03 · valore limite: un TXT di esattamente 5 MB è accettato e scaricabile")
    void testoDiCinqueMb() throws Exception {
        String marcatore = marcatore();
        File file = new File("grande-" + marcatore + ".txt", MediaType.TEXT_PLAIN_VALUE, testoDiByte(CINQUE_MB));

        UUID id = inviaAlGruppo(marcatore, List.of(file)).id();

        verificaAllegati(destinatario, id, List.of(file), List.of(TipoAmmesso.TXT));
    }

    // ------------------------------------------------------------------ rifiutati

    static Stream<Arguments> rifiutati() {
        return Stream.of(
                rifiutato("6 file ammessi (uno oltre il limite)", m -> {
                    List<File> sei = new ArrayList<>();
                    for (int i = 0; i < 6; i++) {
                        sei.add(new File("file" + i + "-" + m + ".txt", MediaType.TEXT_PLAIN_VALUE, testo(m + i)));
                    }
                    return sei;
                }),
                rifiutato("file vuoto", m -> List.of(new File("vuoto-" + m + ".txt", MediaType.TEXT_PLAIN_VALUE,
                        new byte[0]))),
                rifiutato("TXT di 5 MB + 1 byte", m -> List.of(new File("grande-" + m + ".txt",
                        MediaType.TEXT_PLAIN_VALUE, testoDiByte(CINQUE_MB + 1)))),
                rifiutato("GIF (tipo non ammesso)", m -> List.of(new File("immagine-" + m + ".gif",
                        MediaType.IMAGE_GIF_VALUE, immagine("gif", BufferedImage.TYPE_INT_RGB)))),
                rifiutato("ZIP (tipo non ammesso)", m -> List.of(new File("archivio-" + m + ".zip",
                        "application/zip", zip(m)))),
                rifiutato("contenuto PNG con nome .pdf", m -> List.of(new File("finto-" + m + ".pdf",
                        MediaType.APPLICATION_PDF_VALUE, png(m)))),
                rifiutato("PDF troncato", m -> List.of(new File("troncato-" + m + ".pdf",
                        MediaType.APPLICATION_PDF_VALUE, meta(pdf(m))))),
                rifiutato("PNG troncato", m -> List.of(new File("troncato-" + m + ".png",
                        MediaType.IMAGE_PNG_VALUE, meta(png(m))))),
                rifiutato("TXT non UTF-8", m -> List.of(new File("latin-" + m + ".txt", MediaType.TEXT_PLAIN_VALUE,
                        new byte[] {'c', 'a', 'f', (byte) 0xE8, '\n'}))),
                rifiutato("nome di 201 caratteri", m -> List.of(new File(
                        m + "x".repeat(201 - m.length() - ".txt".length()) + ".txt", MediaType.TEXT_PLAIN_VALUE,
                        testo(m)))));
    }

    private static Arguments rifiutato(String motivo, Function<String, List<File>> file) {
        return Arguments.of(motivo, file);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rifiutati")
    @DisplayName("CA-03 · file rifiutato: 400 sul campo files e nessun invio")
    void fileRifiutato(String motivo, Function<String, List<File>> generatore) throws Exception {
        String marcatore = marcatore();
        List<File> file = generatore.apply(marcatore);
        Account operatore = scenario.operatore(TipoErogatore.INTERNO_BANCO);
        List<Account> possibili = List.of(destinatario, operatore);
        Map<Account, long[]> prima = new HashMap<>();
        for (Account account : possibili) {
            prima.put(account, new long[] {comunicazioni.ricevute(account).totale(),
                    comunicazioni.daConfermare(account)});
        }
        UUID scelto = comunicazioni.scegli(banco, TipoDestinatari.GRUPPO_OPT.name(), destinatario);

        MockHttpServletResponse risposta = comunicazioni.inviaMultipart(banco, campi("Oggetto " + marcatore,
                "Testo con allegato " + marcatore, TipoDestinatari.GRUPPO_OPT.name(), List.of(scelto)), file);

        assertCampiDaCompletare(risposta, Set.of("files"), marcatore);
        assertFalse(comunicazioni.inviate(banco).contieneMarcatore(marcatore), "nessun invio: " + motivo);
        for (Account account : possibili) {
            assertEquals(prima.get(account)[0], comunicazioni.ricevute(account).totale(),
                    () -> account.ruolo() + ": ricevute invariate");
            assertEquals(prima.get(account)[1], comunicazioni.daConfermare(account),
                    () -> account.ruolo() + ": nessuna notifica");
        }
    }

    // ------------------------------------------------------------------ verifiche

    private Inviata inviaAlGruppo(String marcatore, List<File> file) throws Exception {
        UUID scelto = comunicazioni.scegli(banco, TipoDestinatari.GRUPPO_OPT.name(), destinatario);
        Inviata inviata = inviata(comunicazioni.inviaMultipart(banco, campi("Oggetto " + marcatore,
                "Testo con allegati " + marcatore, TipoDestinatari.GRUPPO_OPT.name(), List.of(scelto)), file));
        assertEquals(1, inviata.destinatari());
        return inviata;
    }

    /**
     * Il destinatario apre la comunicazione, vede nome, tipo e dimensione di ogni allegato e scarica byte identici
     * con tipo, download come allegato e divieto di cache.
     */
    static void verificaAllegati(ComunicazioniHttp comunicazioni, Account attore, UUID id, List<File> file,
            List<TipoAmmesso> tipi) throws Exception {
        Map<String, Object> letta = comunicazioni.consultaOk(attore, id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dettagli = (List<Map<String, Object>>) letta.get("dettagliAllegati");
        @SuppressWarnings("unchecked")
        List<String> allegati = (List<String>) letta.get("allegati");
        assertEquals(file.size(), dettagli.size(), "un dettaglio per ogni file inviato");
        assertEquals(dettagli.stream().map(dettaglio -> dettaglio.get("id")).toList(), allegati);
        for (int i = 0; i < file.size(); i++) {
            File inviato = file.get(i);
            TipoAmmesso tipo = tipi.get(i);
            Map<String, Object> dettaglio = dettagli.stream()
                    .filter(candidato -> inviato.nome().equals(candidato.get("nomeFile"))).findFirst()
                    .orElseThrow(() -> new AssertionError("allegato assente: " + inviato.nome()));
            assertEquals(Set.of("id", "nomeFile", "tipoMime", "dimensione"), dettaglio.keySet());
            assertEquals(tipo.tipoMime(), dettaglio.get("tipoMime"));
            assertEquals((long) inviato.contenuto().length, ((Number) dettaglio.get("dimensione")).longValue());

            MockHttpServletResponse scaricato = comunicazioni.scarica(attore,
                    UUID.fromString((String) dettaglio.get("id")));
            assertEquals(200, scaricato.getStatus());
            assertArrayEquals(inviato.contenuto(), scaricato.getContentAsByteArray(), "byte identici all'invio");
            assertEquals(tipo.scaricato, MediaType.parseMediaType(scaricato.getContentType()), "tipo riconosciuto");
            String disposizione = scaricato.getHeader(HttpHeaders.CONTENT_DISPOSITION);
            assertNotNull(disposizione, "Content-Disposition presente");
            ContentDisposition download = ContentDisposition.parse(disposizione);
            assertTrue(download.isAttachment(), "sempre come download");
            assertEquals(inviato.nome(), download.getFilename());
            String cache = scaricato.getHeader(HttpHeaders.CACHE_CONTROL);
            assertTrue(cache != null && cache.contains("no-store"), "Cache-Control no-store");
        }
    }

    private void verificaAllegati(Account attore, UUID id, List<File> file, List<TipoAmmesso> tipi)
            throws Exception {
        verificaAllegati(comunicazioni, attore, id, file, tipi);
    }

    // ------------------------------------------------------------------ file sintetici

    static byte[] testo(String marcatore) {
        return ("Allegato sintetico " + marcatore + "\nseconda riga\tcon tabulazione, àèìòù\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    static byte[] testoDiByte(int dimensione) {
        byte[] contenuto = new byte[dimensione];
        Arrays.fill(contenuto, (byte) 'a');
        for (int i = 79; i < dimensione; i += 80) {
            contenuto[i] = '\n';
        }
        return contenuto;
    }

    static byte[] pdf(String marcatore) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream uscita = new ByteArrayOutputStream()) {
            documento.addPage(new PDPage());
            documento.getDocumentInformation().setTitle("Allegato sintetico " + marcatore);
            documento.save(uscita);
            return uscita.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] png(String marcatore) {
        return immagine("png", BufferedImage.TYPE_INT_ARGB);
    }

    static byte[] jpeg(String marcatore) {
        return immagine("jpeg", BufferedImage.TYPE_INT_RGB);
    }

    static byte[] immagine(String formato, int tipo) {
        BufferedImage immagine = new BufferedImage(64, 48, tipo);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 48; y++) {
                immagine.setRGB(x, y, new Color(x * 4, y * 5, (x * y) % 256).getRGB());
            }
        }
        try (ByteArrayOutputStream uscita = new ByteArrayOutputStream()) {
            if (!ImageIO.write(immagine, formato, uscita)) {
                throw new IllegalStateException("nessun writer ImageIO per " + formato);
            }
            return uscita.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] zip(String marcatore) {
        try (ByteArrayOutputStream uscita = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(uscita)) {
            zip.putNextEntry(new ZipEntry("dentro.txt"));
            zip.write(testo(marcatore));
            zip.closeEntry();
            zip.finish();
            return uscita.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Prima metà del file: inizia con la firma giusta ma non si può aprire. */
    static byte[] meta(byte[] contenuto) {
        return Arrays.copyOf(contenuto, contenuto.length / 2);
    }
}
