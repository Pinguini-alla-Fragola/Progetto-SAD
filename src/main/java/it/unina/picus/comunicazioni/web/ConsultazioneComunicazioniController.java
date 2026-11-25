package it.unina.picus.comunicazioni.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.Riscontro;
import it.unina.picus.comunicazioni.interno.applicazione.ConsultazioneComunicazioni;
import it.unina.picus.comunicazioni.interno.applicazione.RichiestaElenco;
import it.unina.picus.comunicazioni.interno.dominio.ComunicazioneSalvata;
import it.unina.picus.comunicazioni.interno.dominio.FileSalvato;
import it.unina.picus.comunicazioni.interno.dominio.InfoAllegato;
import it.unina.picus.comunicazioni.interno.dominio.PaginaComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.TipoAllegato;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Restituisce le comunicazioni: le inviate al Banco, le ricevute a OPT e Operatori, la singola comunicazione e i
 * suoi allegati a chi ha il permesso di vederli.
 */
@RestController
@RequestMapping(value = "/api/comunicazioni", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConsultazioneComunicazioniController {

    /**
     * Una comunicazione come la mostra la pagina. La forma è quella già attesa da {@code communication-view.js}:
     * gli identificativi degli allegati in {@code allegati} e i loro dati in {@code dettagliAllegati}.
     *
     * @param id identificativo della comunicazione
     * @param titolo oggetto, se indicato
     * @param contenuto testo della comunicazione
     * @param inviataIl istante dell'invio
     * @param destinatariTipo tipo di destinatari scelto
     * @param destinatari numero di account che l'hanno ricevuta
     * @param allegati identificativi degli allegati
     * @param dettagliAllegati nome, tipo e dimensione di ogni allegato
     * @param confermataIl quando chi la consulta ne ha confermato la ricezione, se l'ha fatto
     */
    public record ComunicazioneJson(UUID id, String titolo, String contenuto, Instant inviataIl,
            String destinatariTipo, int destinatari, List<UUID> allegati, List<AllegatoJson> dettagliAllegati,
            Instant confermataIl) {

        static ComunicazioneJson da(ComunicazioneSalvata comunicazione) {
            return new ComunicazioneJson(comunicazione.id(), comunicazione.titolo(), comunicazione.contenuto(),
                    comunicazione.inviataIl(), comunicazione.tipo().name(), comunicazione.destinatari(),
                    comunicazione.allegati().stream().map(InfoAllegato::id).toList(),
                    comunicazione.allegati().stream().map(AllegatoJson::da).toList(),
                    comunicazione.confermataIl());
        }
    }

    /**
     * Dati di un allegato, senza il contenuto.
     *
     * @param id identificativo dell'allegato
     * @param nomeFile nome del file
     * @param tipoMime tipo del file
     * @param dimensione dimensione in byte
     */
    public record AllegatoJson(UUID id, String nomeFile, String tipoMime, long dimensione) {

        static AllegatoJson da(InfoAllegato allegato) {
            return new AllegatoJson(allegato.id(), allegato.nomeFile(), allegato.tipo().tipoMime(),
                    allegato.dimensioneByte());
        }
    }

    /**
     * Una pagina di un elenco.
     *
     * @param elementi comunicazioni della pagina
     * @param pagina numero della pagina, da zero
     * @param dimensione numero massimo di comunicazioni per pagina
     * @param totale comunicazioni trovate in tutto lo storico
     */
    public record PaginaJson(List<ComunicazioneJson> elementi, int pagina, int dimensione, long totale) {

        static PaginaJson da(PaginaComunicazioni pagina) {
            return new PaginaJson(pagina.elementi().stream().map(ComunicazioneJson::da).toList(), pagina.pagina(),
                    pagina.dimensione(), pagina.totale());
        }
    }

    private final ConsultazioneComunicazioni consultazione;

    public ConsultazioneComunicazioniController(ConsultazioneComunicazioni consultazione) {
        this.consultazione = consultazione;
    }

    /**
     * Pagina delle comunicazioni inviate. Ricerca e filtri valgono su tutto lo storico: ogni parametro è
     * facoltativo e un valore non ammesso riceve {@code 400} sul suo campo.
     */
    @GetMapping("/inviate")
    public PaginaJson inviate(IdentitaAutenticata attore, @RequestParam(required = false) String testo,
            @RequestParam(required = false) String destinatari, @RequestParam(required = false) String allegati,
            @RequestParam(required = false) String pagina) {
        return PaginaJson.da(consultazione.inviate(attore,
                new RichiestaElenco(testo, destinatari, allegati, null, pagina)));
    }

    /** Pagina delle comunicazioni ricevute, con gli stessi parametri delle inviate più lo stato di ricezione. */
    @GetMapping("/ricevute")
    public PaginaJson ricevute(IdentitaAutenticata attore, @RequestParam(required = false) String testo,
            @RequestParam(required = false) String destinatari, @RequestParam(required = false) String allegati,
            @RequestParam(required = false) String ricezione, @RequestParam(required = false) String pagina) {
        return PaginaJson.da(consultazione.ricevute(attore,
                new RichiestaElenco(testo, destinatari, allegati, ricezione, pagina)));
    }

    @GetMapping("/{comunicazioneId}")
    public ComunicazioneJson consulta(IdentitaAutenticata attore, @PathVariable UUID comunicazioneId) {
        return ComunicazioneJson.da(consultazione.consulta(attore, comunicazioneId));
    }

    /**
     * Restituisce al Banco lo stato di ricezione per ciascun account destinatario dell'invio.
     *
     * @param attore identità verificata della sessione
     * @param comunicazioneId identificativo della comunicazione inviata
     * @return elenco con l'istante della prima conferma oppure il riscontro in attesa per ogni destinatario
     */
    @GetMapping("/{comunicazioneId}/riscontri")
    public List<Riscontro> riscontri(IdentitaAutenticata attore, @PathVariable UUID comunicazioneId) {
        return consultazione.elencoRiscontri(attore, comunicazioneId);
    }

    /**
     * Scarica un allegato. Il file arriva sempre come download, mai aperto dentro la pagina, e con il tipo
     * riconosciuto al momento dell'invio.
     */
    @GetMapping(value = "/allegati/{allegatoId}", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> scarica(IdentitaAutenticata attore, @PathVariable UUID allegatoId) {
        FileSalvato file = consultazione.scarica(attore, allegatoId);
        byte[] contenuto = file.contenuto();
        MediaType tipo = file.tipo() == TipoAllegato.TESTO
                ? new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8)
                : MediaType.parseMediaType(file.tipo().tipoMime());
        return ResponseEntity.ok()
                .contentType(tipo)
                .contentLength(contenuto.length)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.nomeFile(), StandardCharsets.UTF_8).build()
                                .toString())
                .body(contenuto);
    }
}
