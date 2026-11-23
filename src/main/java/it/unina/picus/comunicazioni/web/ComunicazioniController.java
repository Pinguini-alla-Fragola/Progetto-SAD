package it.unina.picus.comunicazioni.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.unina.picus.comunicazioni.interno.applicazione.AccountSelezionabile;
import it.unina.picus.comunicazioni.interno.applicazione.ComandoInvioComunicazione;
import it.unina.picus.comunicazioni.interno.applicazione.ComunicazioneInviata;
import it.unina.picus.comunicazioni.interno.applicazione.DatiComunicazioneNonValidiException;
import it.unina.picus.comunicazioni.interno.applicazione.InvioComunicazioni;
import it.unina.picus.comunicazioni.interno.applicazione.NotificheComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.AllegatoRicevuto;
import it.unina.picus.comunicazioni.interno.dominio.CampoComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.RegolaAllegati;
import it.unina.picus.comunicazioni.interno.dominio.RegolaDestinatari;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Riceve l'invio delle comunicazioni del Banco, l'elenco degli account da scegliere per un gruppo e la richiesta
 * del contatore da confermare. I nomi dei campi sono quelli del modulo della pagina del Banco. La lettura delle
 * comunicazioni è in {@link ConsultazioneComunicazioniController}.
 */
@RestController
@RequestMapping(value = "/api/comunicazioni", produces = MediaType.APPLICATION_JSON_VALUE)
public class ComunicazioniController {

    /**
     * Campi del modulo di invio così come li manda il browser in JSON, senza allegati. Restano valori JSON
     * generici fino al controllo del tipo: convertiti subito, un booleano diventerebbe il testo "true".
     *
     * @param titolo oggetto facoltativo
     * @param contenuto testo della comunicazione
     * @param destinatariTipo tipo di destinatari scelto
     * @param gruppo identificativi degli account scelti, solo per l'invio a un gruppo
     */
    public record NuovaComunicazioneJson(Object titolo, Object contenuto, Object destinatariTipo,
            List<Object> gruppo) {

        ComandoInvioComunicazione comando() {
            CampiJson campi = new CampiJson();
            ComandoInvioComunicazione comando = new ComandoInvioComunicazione(
                    campi.testo(titolo, CampoComunicazione.TITOLO),
                    campi.testo(contenuto, CampoComunicazione.CONTENUTO),
                    campi.testo(destinatariTipo, CampoComunicazione.DESTINATARI_TIPO),
                    campi.testi(gruppo, CampoComunicazione.GRUPPO, RegolaDestinatari.MASSIMO_GRUPPO),
                    List.of());
            campi.verifica();
            return comando;
        }
    }

    /**
     * Ciò che il browser riceve dopo l'invio.
     *
     * @param id identificativo della comunicazione
     * @param titolo oggetto, se indicato
     * @param inviataIl istante dell'invio
     * @param destinatariTipo tipo di destinatari scelto
     * @param destinatari numero di account che l'hanno ricevuta
     */
    public record ComunicazioneInviataJson(UUID id, String titolo, Instant inviataIl, String destinatariTipo,
            int destinatari) {

        static ComunicazioneInviataJson da(ComunicazioneInviata inviata) {
            return new ComunicazioneInviataJson(inviata.id(), inviata.titolo(), inviata.inviataIl(),
                    inviata.tipo().name(), inviata.destinatari());
        }
    }

    /**
     * Account che il Banco può scegliere per un gruppo.
     *
     * @param id identificativo dell'account
     * @param nome nome mostrato
     */
    public record AccountJson(UUID id, String nome) {

        static AccountJson da(AccountSelezionabile account) {
            return new AccountJson(account.id(), account.nome());
        }
    }

    /**
     * Numero di comunicazioni ricevute che aspettano la conferma.
     *
     * @param daConfermare quante sono
     */
    public record DaConfermareJson(long daConfermare) {}

    /** Nome della parte JSON che porta i campi del modulo nell'invio con allegati. */
    static final String PARTE_DATI = "dati";

    private final InvioComunicazioni invio;
    private final NotificheComunicazioni notifiche;

    public ComunicazioniController(InvioComunicazioni invio, NotificheComunicazioni notifiche) {
        this.invio = invio;
        this.notifiche = notifiche;
    }

    /** Invio in JSON, senza allegati. Risponde {@code 201}. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ComunicazioneInviataJson> invia(IdentitaAutenticata attore,
            @RequestBody NuovaComunicazioneJson corpo) {
        ComunicazioneInviata inviata = invio.invia(attore, corpo.comando());
        return ResponseEntity.status(HttpStatus.CREATED).body(ComunicazioneInviataJson.da(inviata));
    }

    /**
     * Invio dal modulo della pagina, con eventuali allegati. I campi arrivano tutti insieme nella parte JSON
     * {@code dati}, con la stessa forma e gli stessi controlli dell'invio senza allegati: così anche un gruppo di
     * 1000 account occupa una sola parte della richiesta, e le parti restano al massimo una più i file. Il numero
     * di file si controlla prima di leggerne il contenuto. Risponde {@code 201}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComunicazioneInviataJson> inviaConAllegati(IdentitaAutenticata attore,
            @RequestPart(PARTE_DATI) NuovaComunicazioneJson dati,
            @RequestPart(name = CampoComunicazione.ALLEGATI, required = false) List<MultipartFile> files) {
        ComandoInvioComunicazione comando = dati.comando().conAllegati(allegati(files));
        ComunicazioneInviata inviata = invio.invia(attore, comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(ComunicazioneInviataJson.da(inviata));
    }

    @GetMapping("/destinatari")
    public List<AccountJson> selezionabili(IdentitaAutenticata attore, @RequestParam String tipo) {
        return invio.selezionabili(attore, tipo).stream().map(AccountJson::da).toList();
    }

    @GetMapping("/da-confermare")
    public DaConfermareJson daConfermare(IdentitaAutenticata attore) {
        return new DaConfermareJson(notifiche.daConfermare(attore));
    }

    /** Converte i file ricevuti; un campo file lasciato vuoto dal browser non conta come allegato. */
    private static List<AllegatoRicevuto> allegati(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        List<MultipartFile> scelti = files.stream()
                .filter(file -> !(file.isEmpty() && (file.getOriginalFilename() == null
                        || file.getOriginalFilename().isEmpty())))
                .toList();
        if (scelti.size() > RegolaAllegati.MASSIMO_ALLEGATI) {
            throw new DatiComunicazioneNonValidiException(Map.of(CampoComunicazione.ALLEGATI,
                    "Si possono allegare al massimo " + RegolaAllegati.MASSIMO_ALLEGATI + " file"));
        }
        return scelti.stream().map(ComunicazioniController::allegato).toList();
    }

    private static AllegatoRicevuto allegato(MultipartFile file) {
        try {
            return new AllegatoRicevuto(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
