package it.unina.picus.utenze.web;

import java.io.IOException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

import it.unina.picus.utenze.interno.applicazione.CsvOptNonValidoException;
import it.unina.picus.utenze.interno.applicazione.ImportazioneOptService;
import it.unina.picus.utenze.interno.applicazione.ReportImportazioneOpt;

/** Endpoint Banco per confermare l'importazione di un file CSV di OPT. */
@RestController
@Profile("!skeleton")
class ImportazioneOptController {

    private static final long DIMENSIONE_MASSIMA_CSV = 1024 * 1024;

    private final ImportazioneOptService importazione;

    ImportazioneOptController(ImportazioneOptService importazione) {
        this.importazione = importazione;
    }

    /**
     * Elabora il CSV inviato dopo la conferma del Banco.
     *
     * @param file parte multipart contenente il CSV
     * @return report degli esiti per riga
     */
    @PostMapping(path = "/api/opt/importazioni", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ReportImportazioneOpt importa(@RequestPart("file") MultipartFile file) {
        if (file.getSize() > DIMENSIONE_MASSIMA_CSV) {
            throw new MaxUploadSizeExceededException(DIMENSIONE_MASSIMA_CSV);
        }
        try {
            return importazione.importa(file.getInputStream());
        } catch (IOException ex) {
            throw new CsvOptNonValidoException(ex);
        }
    }

    /** Restituisce il motivo del rifiuto dell'intero file. */
    @ExceptionHandler(CsvOptNonValidoException.class)
    ProblemDetail fileNonValido(CsvOptNonValidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail fileTroppoGrande() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Il file CSV può pesare al massimo 1 MB.");
    }

    @ExceptionHandler(MultipartException.class)
    ProblemDetail multipartNonValido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Impossibile leggere il file CSV caricato.");
    }
}
