package it.unina.picus.richieste.web;

import java.util.List;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettaglioContatto;
import it.unina.picus.richieste.interno.applicazione.DisponibilitaService;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Espone via HTTP la consultazione, la risposta e lo storico dei contatti di disponibilità.
 *
 * <p>Agisce da adattatore HTTP. Riceve dal resolver Utenze l'identità corrente usata
 * dai casi d'uso e delega i controlli e la registrazione al servizio.
 * Il gestore comune del modulo traduce gli errori applicativi in risposte HTTP.
 *
 * @see DisponibilitaService
 */
@RestController
@RequestMapping("/api")
class ContattiDisponibilitaController {

    private final DisponibilitaService disponibilita;

    /**
     * Collega l'adattatore HTTP al servizio applicativo.
     *
     * @param disponibilita servizio dei casi d'uso di disponibilità
     */
    ContattiDisponibilitaController(DisponibilitaService disponibilita) {
        this.disponibilita = disponibilita;
    }

    /**
     * Restituisce il dettaglio minimo all'erogatore destinatario.
     *
     * @param contattoId identificativo del contatto
     * @param attore identita corrente verificata dal resolver Utenze
     * @return dettaglio del contatto
     */
    @GetMapping("/contatti-disponibilita/{contattoId}")
    DettaglioContatto consultaContatto(@PathVariable UUID contattoId, IdentitaAutenticata attore) {
        return disponibilita.consultaContatto(attore, contattoId);
    }

    /**
     * Registra la decisione del destinatario sul contatto.
     *
     * @param contattoId identificativo del contatto
     * @param dati conferma o rifiuto fornito dal destinatario
     * @param attore identita corrente verificata dal resolver Utenze
     * @return contatto con l'esito registrato
     */
    @PostMapping("/contatti-disponibilita/{contattoId}/risposta")
    ContattoDisponibilita rispondiDisponibilita(@PathVariable UUID contattoId,
            @Valid @RequestBody DatiRisposta dati, IdentitaAutenticata attore) {
        return disponibilita.rispondiDisponibilita(attore, contattoId, dati.disponibile());
    }

    /**
     * Restituisce al Banco lo storico dei contatti in ordine di invio.
     *
     * @param richiestaId identificativo della richiesta
     * @param attore identita corrente verificata dal resolver Utenze
     * @return contatti e rispettivi esiti
     */
    @GetMapping("/richieste/{richiestaId}/contatti")
    List<ContattoDisponibilita> elencoContatti(@PathVariable UUID richiestaId, IdentitaAutenticata attore) {
        return disponibilita.elencoContatti(attore, richiestaId);
    }

    /**
     * Corpo della richiesta per registrare la risposta.
     *
     * @param disponibile valore obbligatorio, {@code true} per confermare o {@code false} per rifiutare
     */
    record DatiRisposta(@NotNull Boolean disponibile) {
    }
}
