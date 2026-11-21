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
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.comunicazioni.interno.applicazione.ConsultazioneComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.ComunicazioneSalvata;
import it.unina.picus.comunicazioni.interno.dominio.FileSalvato;
import it.unina.picus.comunicazioni.interno.dominio.InfoAllegato;
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

    private final ConsultazioneComunicazioni consultazione;

    public ConsultazioneComunicazioniController(ConsultazioneComunicazioni consultazione) {
        this.consultazione = consultazione;
    }

    @GetMapping("/inviate")
    public List<ComunicazioneJson> inviate(IdentitaAutenticata attore) {
        return consultazione.inviate(attore).stream().map(ComunicazioneJson::da).toList();
    }

    @GetMapping("/ricevute")
    public List<ComunicazioneJson> ricevute(IdentitaAutenticata attore) {
        return consultazione.ricevute(attore).stream().map(ComunicazioneJson::da).toList();
    }

    @GetMapping("/{comunicazioneId}")
    public ComunicazioneJson consulta(IdentitaAutenticata attore, @PathVariable UUID comunicazioneId) {
        return ComunicazioneJson.da(consultazione.consulta(attore, comunicazioneId));
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
