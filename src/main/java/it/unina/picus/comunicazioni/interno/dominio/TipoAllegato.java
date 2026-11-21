package it.unina.picus.comunicazioni.interno.dominio;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * I tipi di file che si possono allegare: PDF, immagini PNG e JPEG, testo semplice. Sono quelli già indicati nel
 * modulo della pagina del Banco. Il tipo si riconosce dal contenuto del file, non dal nome né da quello che
 * dichiara il browser: un file rinominato non può fingersi un altro tipo. Ogni tipo sa anche controllare che il
 * file sia completo e si possa aprire (vedi {@link StrutturaAllegato}).
 */
public enum TipoAllegato {
    PDF("application/pdf", Set.of("pdf"), Firme.PDF, StrutturaAllegato::pdfApribile),
    PNG("image/png", Set.of("png"), Firme.PNG, contenuto -> StrutturaAllegato.immagineLeggibile(contenuto, "png")),
    JPEG("image/jpeg", Set.of("jpg", "jpeg"), Firme.JPEG,
            contenuto -> StrutturaAllegato.immagineLeggibile(contenuto, "jpeg")),
    /** Il testo non ha una firma: è l'ultima possibilità, se nessun formato binario corrisponde. */
    TESTO("text/plain", Set.of("txt"), Firme.NESSUNA, StrutturaAllegato::testoSemplice);

    private final String tipoMime;
    private final Set<String> estensioni;
    private final byte[] firma;
    private final Predicate<byte[]> struttura;

    TipoAllegato(String tipoMime, Set<String> estensioni, byte[] firma, Predicate<byte[]> struttura) {
        this.tipoMime = tipoMime;
        this.estensioni = estensioni;
        this.firma = firma;
        this.struttura = struttura;
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
     * Indica se il file è completo e si può aprire: non basta che inizi con i byte giusti.
     *
     * @param contenuto contenuto del file, già riconosciuto come di questo tipo
     * @return vero se la struttura del file è valida
     */
    public boolean integro(byte[] contenuto) {
        return struttura.test(contenuto);
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
     * senza una di queste firme è accettato solo se è testo semplice. Che un PDF o un'immagine sia anche integro
     * si controlla a parte con {@link #integro(byte[])}.
     *
     * @param contenuto contenuto del file
     * @return il tipo riconosciuto, oppure vuoto se il file non è di un tipo ammesso
     */
    public static Optional<TipoAllegato> riconosci(byte[] contenuto) {
        return Arrays.stream(values())
                .filter(tipo -> tipo.firma.length > 0 && iniziaCon(contenuto, tipo.firma))
                .findFirst()
                .or(() -> TESTO.integro(contenuto) ? Optional.of(TESTO) : Optional.empty());
    }

    private static boolean iniziaCon(byte[] contenuto, byte[] firma) {
        return contenuto.length >= firma.length && Arrays.equals(contenuto, 0, firma.length, firma, 0, firma.length);
    }

    /** Primi byte che identificano i formati binari. */
    private static final class Firme {
        static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
        static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        static final byte[] NESSUNA = {};

        private Firme() {
        }
    }
}
