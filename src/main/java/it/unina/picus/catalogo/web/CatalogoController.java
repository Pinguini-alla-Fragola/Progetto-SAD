package it.unina.picus.catalogo.web;

import it.unina.picus.catalogo.api.CatalogoApi.Comune;
import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.catalogo.interno.applicazione.ServizioGestioneCatalogo;
import it.unina.picus.catalogo.interno.dominio.MisuraNonTrovataException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Espone gli endpoint REST del modulo Catalogo per consultazione e gestione delle misure persistenti.
 *
 * <p>Delega la consultazione a {@link ConsultazioneCatalogoApi} e i comandi riservati al Banco a
 * {@link ServizioGestioneCatalogo}, senza introdurre logica di presentazione.</p>
 */
@RestController
@RequestMapping(value = "/api/catalogo", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogoController {

    private final ConsultazioneCatalogoApi catalogo;
    private final ServizioGestioneCatalogo gestione;

    /**
     * Costruisce il controller collegando le capacità di consultazione e gestione catalogo.
     *
     * @param catalogo servizio di lettura di misure attive, storiche e comuni
     * @param gestione servizio applicativo per creazione, modifica e disattivazione delle misure
     */
    public CatalogoController(ConsultazioneCatalogoApi catalogo, ServizioGestioneCatalogo gestione) {
        this.catalogo = catalogo;
        this.gestione = gestione;
    }

    /**
     * Elenca le misure attive selezionabili nelle nuove richieste e offerte.
     *
     * @param attore identità autenticata corrente
     * @return misure attive ordinate per codice
     */
    @GetMapping("/misure")
    public List<Misura> elencoMisureAttive(IdentitaAutenticata attore) {
        return catalogo.elencoMisureAttive();
    }

    /**
     * Elenca per il Banco tutte le misure del catalogo, comprese quelle disattivate.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @return elenco completo delle misure presenti in catalogo
     */
    @GetMapping("/misure/tutte")
    public List<Misura> elencoMisure(IdentitaAutenticata attore) {
        return gestione.elencoMisure(attore);
    }

    /**
     * Restituisce i dati di una misura anche quando è stata disattivata, per consultazione storica.
     *
     * @param attore identità autenticata corrente
     * @param id identificativo stabile della misura
     * @return dettaglio della misura trovata
     * @throws MisuraNonTrovataException se l'identificativo non corrisponde ad alcuna misura
     */
    @GetMapping("/misure/{id}")
    public Misura consultaMisura(IdentitaAutenticata attore, @PathVariable long id) {
        return catalogo.cercaMisura(id).orElseThrow(MisuraNonTrovataException::new);
    }

    /**
     * Crea una nuova misura attiva nel catalogo persistente su comando del Banco.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param corpo proprietà JSON della nuova misura
     * @return risposta {@code 201 Created} con intestazione {@code Location} e misura creata
     */
    @PostMapping(value = "/misure", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Misura> creaMisura(IdentitaAutenticata attore, @RequestBody CorpoJsonCatalogo corpo) {
        DatiMisura dati = MisuraJson.leggiCreazione(corpo.campi());
        Misura creata = gestione.creaMisura(attore, dati);
        return ResponseEntity.created(URI.create("/api/catalogo/misure/" + creata.id())).body(creata);
    }

    /**
     * Aggiorna i dati o lo stato di attivazione di una misura esistente conservandone l'identificativo.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param id identificativo stabile della misura da aggiornare
     * @param corpo proprietà JSON aggiornate della misura
     * @return misura aggiornata
     */
    @PutMapping(value = "/misure/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Misura aggiornaMisura(
            IdentitaAutenticata attore, @PathVariable long id, @RequestBody CorpoJsonCatalogo corpo) {
        var aggiornamento = MisuraJson.leggiAggiornamento(corpo.campi());
        return gestione.aggiornaMisura(attore, id, aggiornamento.dati(), aggiornamento.attiva());
    }

    /**
     * Disattiva una misura esistente impedendone l'uso in nuove selezioni senza alterare lo storico.
     *
     * @param attore identità verificata del Banco di coordinamento
     * @param id identificativo stabile della misura da disattivare
     * @param corpo corpo opzionale che, se presente, deve essere un oggetto JSON vuoto
     * @return misura aggiornata con {@code attiva = false}
     */
    @PostMapping("/misure/{id}/disattivazione")
    public Misura disattivaMisura(
            IdentitaAutenticata attore,
            @PathVariable long id,
            @RequestBody(required = false) CorpoJsonCatalogo corpo) {
        MisuraJson.verificaCorpoVuoto(corpo);
        return gestione.disattivaMisura(attore, id);
    }

    /**
     * Elenca i comuni censiti nel catalogo.
     *
     * @param attore identità autenticata corrente
     * @return comuni ordinati per nome
     */
    @GetMapping("/comuni")
    public List<Comune> elencoComuni(IdentitaAutenticata attore) {
        return catalogo.elencoComuni();
    }
}
