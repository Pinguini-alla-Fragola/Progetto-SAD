package it.unina.picus.utenze.interno.applicazione;

import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;

import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.ComandoRegistrazioneOpt;

/** Legge il tracciato CSV delle OPT e rileva errori strutturali e duplicati interni al file. */
final class LettoreCsvOpt {

    private static final String CODICE = "codice_struttura";
    private static final String NOME = "nome_opt";
    private static final String COMUNE = "comune";
    private static final String CODICE_FISCALE = "codice_fiscale";
    private static final String EMAIL = "email";
    private static final List<String> COLONNE_OBBLIGATORIE = List.of(CODICE, NOME, COMUNE, CODICE_FISCALE, EMAIL);
    private static final CSVFormat FORMATO = CSVFormat.RFC4180.builder()
            .setHeader()
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .get();

    private LettoreCsvOpt() {
    }

    /**
     * Legge e chiude il file prima che il chiamante possa applicare gli aggiornamenti.
     * Accetta il BOM UTF-8 facoltativo e numera le righe fisiche a partire dall'intestazione.
     *
     * @param file contenuto UTF-8 del CSV con campi separati da virgola
     * @return righe nell'ordine originale, comprese quelle da scartare
     * @throws CsvOptNonValidoException se il file o le intestazioni non sono leggibili
     */
    static List<Riga> leggi(InputStream file) {
        try (var lettore = new PushbackReader(new InputStreamReader(file, StandardCharsets.UTF_8.newDecoder()))) {
            int primo = lettore.read();
            if (primo != -1 && primo != '\uFEFF') {
                lettore.unread(primo);
            }

            try (var parser = FORMATO.parse(new LettoreVirgolette(lettore))) {
                var intestazioni = parser.getHeaderMap();
                var mancanti = COLONNE_OBBLIGATORIE.stream()
                        .filter(colonna -> !intestazioni.containsKey(colonna))
                        .toList();
                if (!mancanti.isEmpty()) {
                    throw new CsvOptNonValidoException("Colonne obbligatorie mancanti: " + String.join(", ", mancanti));
                }

                List<Riga> righe = new ArrayList<>();
                long numero = parser.getCurrentLineNumber() + 1;
                for (CSVRecord record : parser) {
                    righe.add(leggiRiga(numero, record));
                    numero = parser.getCurrentLineNumber() + 1;
                }
                return segnalaDuplicati(righe);
            }
        } catch (IllegalArgumentException ex) {
            throw new CsvOptNonValidoException("L'intestazione contiene colonne senza nome o duplicate.");
        } catch (IOException | UncheckedIOException ex) {
            throw new CsvOptNonValidoException(ex);
        }
    }

    /** Crea una riga conservando codice ed email anche se mancano altre colonne. */
    private static Riga leggiRiga(long numero, CSVRecord record) {
        var dati = new ComandoRegistrazioneOpt(valore(record, CODICE), valore(record, NOME),
                valore(record, COMUNE), valore(record, CODICE_FISCALE), valore(record, EMAIL));
        List<String> errori = new ArrayList<>();
        if (!record.isConsistent()) {
            errori.add("Il numero di campi non corrisponde all'intestazione.");
        }
        if (record.stream().anyMatch(campo -> campo.indexOf(0) >= 0)) {
            errori.add("La riga contiene caratteri non validi.");
        }
        return new Riga(numero, dati, errori);
    }

    private static String valore(CSVRecord record, String colonna) {
        return record.isSet(colonna) ? record.get(colonna) : "";
    }

    /** Conta anche le occorrenze nelle righe già invalide, così ogni riga coinvolta viene scartata. */
    private static List<Riga> segnalaDuplicati(List<Riga> righe) {
        Map<String, Integer> codici = new HashMap<>();
        Map<String, Integer> email = new HashMap<>();
        for (Riga riga : righe) {
            codici.merge(chiaveCodice(riga), 1, Integer::sum);
            email.merge(chiaveEmail(riga), 1, Integer::sum);
        }

        return righe.stream().map(riga -> {
            List<String> errori = new ArrayList<>(riga.errori());
            if (ripetuto(chiaveCodice(riga), codici)) {
                errori.add("Codice struttura ripetuto nel file.");
            }
            if (ripetuto(chiaveEmail(riga), email)) {
                errori.add("Email ripetuta nel file.");
            }
            return new Riga(riga.numero(), riga.dati(), errori);
        }).toList();
    }

    private static boolean ripetuto(String chiave, Map<String, Integer> conteggi) {
        return !chiave.isEmpty() && conteggi.getOrDefault(chiave, 0) > 1;
    }

    private static String chiaveCodice(Riga riga) {
        return riga.dati().codiceStruttura().strip().toUpperCase(Locale.ROOT);
    }

    private static String chiaveEmail(Riga riga) {
        return riga.dati().email().strip().toLowerCase(Locale.ROOT);
    }

    /** Rifiuta le virgolette fuori posizione che Commons CSV tratterebbe come testo letterale. */
    private static final class LettoreVirgolette extends FilterReader {

        private enum Stato {
            INIZIO_CAMPO, NON_QUOTATO, QUOTATO, DOPO_VIRGOLETTE
        }

        private Stato stato = Stato.INIZIO_CAMPO;

        LettoreVirgolette(Reader lettore) {
            super(lettore);
        }

        @Override
        public int read() throws IOException {
            int carattere = super.read();
            if (carattere != -1) {
                verifica(carattere);
            }
            return carattere;
        }

        @Override
        public int read(char[] buffer, int offset, int lunghezza) throws IOException {
            int letti = super.read(buffer, offset, lunghezza);
            for (int i = 0; i < letti; i++) {
                verifica(buffer[offset + i]);
            }
            return letti;
        }

        private void verifica(int carattere) throws IOException {
            boolean separatore = carattere == ',' || carattere == '\r' || carattere == '\n';
            boolean virgolette = carattere == '"';
            stato = switch (stato) {
                case INIZIO_CAMPO -> {
                    if (virgolette) {
                        yield Stato.QUOTATO;
                    }
                    yield separatore ? Stato.INIZIO_CAMPO : Stato.NON_QUOTATO;
                }
                case NON_QUOTATO -> {
                    if (virgolette) {
                        throw new IOException("Virgolette in un campo non quotato");
                    }
                    yield separatore ? Stato.INIZIO_CAMPO : Stato.NON_QUOTATO;
                }
                case QUOTATO -> virgolette ? Stato.DOPO_VIRGOLETTE : Stato.QUOTATO;
                case DOPO_VIRGOLETTE -> {
                    if (virgolette) {
                        yield Stato.QUOTATO;
                    }
                    if (!separatore) {
                        throw new IOException("Carattere dopo la chiusura del campo quotato");
                    }
                    yield Stato.INIZIO_CAMPO;
                }
            };
        }
    }

    /** Riga del tracciato con numero fisico iniziale, valori ed eventuali errori del file. */
    record Riga(long numero, ComandoRegistrazioneOpt dati, List<String> errori) {
        Riga {
            errori = List.copyOf(errori);
        }
    }
}
