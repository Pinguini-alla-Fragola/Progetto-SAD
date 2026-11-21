package it.unina.picus.comunicazioni.interno.dominio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Controlla che un allegato sia completo e si possa aprire, non solo che inizi con i byte del suo formato. Un file
 * troncato o rovinato viene rifiutato all'invio invece di arrivare ai destinatari.
 *
 * <p>I controlli restano leggeri anche con un file costruito apposta: le immagini vengono lette fino in fondo ma
 * ridotte, così la memoria usata non dipende dalle dimensioni dichiarate, e oltre {@value #MASSIMO_PIXEL} pixel
 * vengono rifiutate senza leggerle. I PDF li apre Apache PDFBox con un limite di memoria. Si legge tutto in
 * memoria, mai su file temporanei.
 */
final class StrutturaAllegato {

    /** Oltre ai 48 megapixel delle fotocamere più diffuse: un'immagine più grande non serve in una comunicazione. */
    static final long MASSIMO_PIXEL = 50_000_000L;
    /** Pixel tenuti in memoria durante la lettura: l'immagine viene ridotta fino a starci. */
    private static final long PIXEL_LETTI = 1_000_000L;
    /** Un PDF finisce con «startxref», la posizione dell'indice e «%%EOF», più al massimo qualche byte vuoto. */
    private static final int CODA_PDF = 1024;
    private static final Pattern FINE_PDF = Pattern.compile("startxref\\s+(\\d{1,10})\\s+%%EOF\\s*$");
    /** All'inizio dell'indice c'è la parola «xref», oppure un oggetto («12 0 obj») se l'indice è compresso. */
    private static final Pattern INIZIO_INDICE = Pattern.compile("\\A\\s*(xref|\\d+\\s+\\d+\\s+obj)");
    private static final int INIZIO_INDICE_BYTE = 64;
    /** Memoria massima che PDFBox può usare per un allegato; i file restano sempre in memoria, mai su disco. */
    private static final long MEMORIA_PDF_BYTE = 32L * 1024 * 1024;

    private StrutturaAllegato() {
    }

    /**
     * Un PDF è valido se supera tre controlli, dal più economico al più costoso:
     * <ol>
     * <li>finisce con {@code startxref}, la posizione dell'indice e {@code %%EOF}: un file troncato no;</li>
     * <li>quella posizione è dentro il file e lì inizia davvero l'indice, come la cerca un lettore PDF;</li>
     * <li>PDFBox lo apre e trova almeno una pagina, con le sue dimensioni.</li>
     * </ol>
     * Del documento si leggono solo la struttura e l'elenco delle pagine, mai il contenuto: niente testo, immagini,
     * caratteri o codice.
     *
     * @param contenuto file che inizia con la firma PDF
     * @return vero se il PDF si apre e ha almeno una pagina
     */
    static boolean pdfApribile(byte[] contenuto) {
        return indiceTrovato(contenuto) && pagineLeggibili(contenuto);
    }

    private static boolean indiceTrovato(byte[] contenuto) {
        int inizio = Math.max(0, contenuto.length - CODA_PDF);
        String coda = new String(contenuto, inizio, contenuto.length - inizio, StandardCharsets.ISO_8859_1);
        Matcher fine = FINE_PDF.matcher(coda);
        if (!fine.find()) {
            return false;
        }
        long posizione = Long.parseLong(fine.group(1));
        if (posizione >= contenuto.length) {
            return false;
        }
        int lunghezza = (int) Math.min(INIZIO_INDICE_BYTE, contenuto.length - posizione);
        String indice = new String(contenuto, (int) posizione, lunghezza, StandardCharsets.ISO_8859_1);
        return INIZIO_INDICE.matcher(indice).lookingAt();
    }

    private static boolean pagineLeggibili(byte[] contenuto) {
        try (PDDocument documento = Loader.loadPDF(contenuto, "", null, null,
                MemoryUsageSetting.setupMainMemoryOnly(MEMORIA_PDF_BYTE).streamCache)) {
            if (documento.getNumberOfPages() == 0) {
                return false;
            }
            for (PDPage pagina : documento.getPages()) {
                PDRectangle dimensioni = pagina.getMediaBox();
                if (dimensioni.getWidth() <= 0 || dimensioni.getHeight() <= 0) {
                    return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            // PDF illeggibile, protetto da password o fuori dal limite di memoria: per l'invio conta che non si apre.
            return false;
        }
    }

    /**
     * Un'immagine è valida se il lettore del suo formato la decodifica tutta senza errori né avvisi: un file
     * troncato produce un errore oppure l'avviso di dati mancanti, e in entrambi i casi viene rifiutato.
     *
     * @param contenuto file che inizia con la firma del formato
     * @param formato nome del formato per {@link ImageIO}, per esempio {@code png}
     * @return vero se l'immagine si legge tutta
     */
    static boolean immagineLeggibile(byte[] contenuto, String formato) {
        Iterator<ImageReader> lettori = ImageIO.getImageReadersByFormatName(formato);
        if (!lettori.hasNext()) {
            return false;
        }
        ImageReader lettore = lettori.next();
        try (ImageInputStream ingresso = new MemoryCacheImageInputStream(new ByteArrayInputStream(contenuto))) {
            lettore.setInput(ingresso, true, true);
            // Il lettore JPEG segnala i dati mancanti solo con un avviso, non con un errore: va controllato a parte.
            AtomicBoolean avvisi = new AtomicBoolean();
            lettore.addIIOReadWarningListener((sorgente, avviso) -> avvisi.set(true));
            long pixel = (long) lettore.getWidth(0) * lettore.getHeight(0);
            if (pixel <= 0 || pixel > MASSIMO_PIXEL) {
                return false;
            }
            ImageReadParam parametri = lettore.getDefaultReadParam();
            int passo = (int) Math.ceil(Math.sqrt((double) pixel / PIXEL_LETTI));
            parametri.setSourceSubsampling(passo, passo, 0, 0);
            lettore.read(0, parametri);
            return !avvisi.get();
        } catch (IOException | RuntimeException e) {
            // Il file non rispetta il formato: per l'invio conta solo che non si apre.
            return false;
        } finally {
            lettore.dispose();
        }
    }

    /**
     * Testo semplice: UTF-8 valido, senza caratteri di controllo oltre a a capo e tabulazioni.
     *
     * @param contenuto contenuto del file
     * @return vero se il file è testo semplice
     */
    static boolean testoSemplice(byte[] contenuto) {
        try {
            String testo = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenuto)).toString();
            return testo.chars().noneMatch(carattere -> Character.isISOControl(carattere)
                    && carattere != '\n' && carattere != '\r' && carattere != '\t');
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
