package it.unina.picus.comunicazioni.interno.dominio;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * I tipi di file che si possono allegare: PDF, immagini PNG e JPEG, testo semplice. Sono quelli già indicati nel
 * modulo della pagina del Banco. Il tipo si riconosce dal contenuto del file, non dal nome né da quello che
 * dichiara il browser: un file rinominato non può fingersi un altro tipo.
 */
public enum TipoAllegato {
    PDF("application/pdf", Set.of("pdf")),
    PNG("image/png", Set.of("png")),
    JPEG("image/jpeg", Set.of("jpg", "jpeg")),
    TESTO("text/plain", Set.of("txt"));

    private final String tipoMime;
    private final Set<String> estensioni;

    TipoAllegato(String tipoMime, Set<String> estensioni) {
        this.tipoMime = tipoMime;
        this.estensioni = estensioni;
    }

    public String tipoMime() {
        return tipoMime;
    }

    /**
     * Indica se il nome del file ha un'estensione coerente con questo tipo.
     *
     * @param nomeFile nome del file, con estensione
     * @return vero se l'estensione è una di quelle del tipo
     */
    public boolean ammette(String nomeFile) {
        int punto = nomeFile.lastIndexOf('.');
        return punto >= 0 && estensioni.contains(nomeFile.substring(punto + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * Ritrova il tipo a partire dal tipo MIME salvato.
     *
     * @param tipoMime tipo MIME
     * @return il tipo corrispondente, se è tra quelli ammessi
     */
    public static Optional<TipoAllegato> daTipoMime(String tipoMime) {
        return Arrays.stream(values()).filter(tipo -> tipo.tipoMime.equals(tipoMime)).findFirst();
    }

    /**
     * Riconosce il tipo guardando i primi byte del file, che per PDF, PNG e JPEG sono sempre gli stessi. Un file
     * è considerato testo solo se è UTF-8 valido e non contiene caratteri di controllo oltre a a capo e tabulazioni.
     *
     * @param contenuto contenuto del file
     * @return il tipo riconosciuto, oppure vuoto se il file non è di un tipo ammesso
     */
    public static Optional<TipoAllegato> riconosci(byte[] contenuto) {
        if (iniziaCon(contenuto, Firme.PDF)) {
            return Optional.of(PDF);
        }
        if (iniziaCon(contenuto, Firme.PNG)) {
            return Optional.of(PNG);
        }
        if (iniziaCon(contenuto, Firme.JPEG)) {
            return Optional.of(JPEG);
        }
        return testoSemplice(contenuto) ? Optional.of(TESTO) : Optional.empty();
    }

    private static boolean iniziaCon(byte[] contenuto, byte[] firma) {
        return contenuto.length >= firma.length && Arrays.equals(contenuto, 0, firma.length, firma, 0, firma.length);
    }

    private static boolean testoSemplice(byte[] contenuto) {
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

    /** Primi byte che identificano i formati binari. */
    private static final class Firme {
        static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
        static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

        private Firme() {
        }
    }
}
