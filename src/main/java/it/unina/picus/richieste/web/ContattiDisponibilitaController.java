package it.unina.picus.richieste.web;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettaglioContatto;
import it.unina.picus.richieste.interno.applicazione.DisponibilitaService;
import it.unina.picus.richieste.interno.dominio.StatoContattoIncompatibileException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Espone via HTTP la consultazione, la risposta e lo storico dei contatti di disponibilità.
 *
 * <p>Agisce da adattatore HTTP. Converte il principal di Spring Security nell'identità usata
 * dai casi d'uso, delega i controlli e la registrazione al servizio e traduce i suoi errori
 * in risposte HTTP.
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
     * @param autenticazione principal verificato da Spring Security
     * @return dettaglio del contatto
     */
    @GetMapping("/contatti-disponibilita/{contattoId}")
    DettaglioContatto consultaContatto(@PathVariable UUID contattoId, Authentication autenticazione) {
        return disponibilita.consultaContatto(identita(autenticazione), contattoId);
    }

    /**
     * Registra la decisione del destinatario sul contatto.
     *
     * @param contattoId identificativo del contatto
     * @param dati conferma o rifiuto fornito dal destinatario
     * @param autenticazione principal verificato da Spring Security
     * @return contatto con l'esito registrato
     */
    @PostMapping("/contatti-disponibilita/{contattoId}/risposta")
    ContattoDisponibilita rispondiDisponibilita(@PathVariable UUID contattoId,
            @Valid @RequestBody DatiRisposta dati, Authentication autenticazione) {
        return disponibilita.rispondiDisponibilita(identita(autenticazione), contattoId, dati.disponibile());
    }

    /**
     * Restituisce al Banco lo storico dei contatti in ordine di invio.
     *
     * @param richiestaId identificativo della richiesta
     * @param autenticazione principal verificato da Spring Security
     * @return contatti e rispettivi esiti
     */
    @GetMapping("/richieste/{richiestaId}/contatti")
    List<ContattoDisponibilita> elencoContatti(@PathVariable UUID richiestaId, Authentication autenticazione) {
        return disponibilita.elencoContatti(identita(autenticazione), richiestaId);
    }

    /**
     * Ricava dal principal l'identificativo dell'account e l'unico ruolo PICUS riconosciuto.
     *
     * @param autenticazione principal fornito da Spring Security
     * @return identità verificata da passare al servizio
     * @throws ResponseStatusException con stato 401 se manca una sessione autenticata
     * @throws SecurityException se identificativo o ruolo PICUS non sono validi
     */
    private static IdentitaAutenticata identita(Authentication autenticazione) {
        if (autenticazione == null || !autenticazione.isAuthenticated()
                || autenticazione instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        UUID accountId;
        try {
            accountId = UUID.fromString(autenticazione.getName());
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Identità PICUS assente", e);
        }
        var ruoli = Arrays.stream(RuoloUtente.values())
                .filter(ruolo -> autenticazione.getAuthorities().stream()
                        .anyMatch(autorita -> ("ROLE_" + ruolo.name()).equals(autorita.getAuthority())))
                .toList();
        if (ruoli.size() != 1) {
            throw new SecurityException("Ruolo PICUS assente o ambiguo");
        }
        return new IdentitaAutenticata(accountId, ruoli.getFirst());
    }

    /** Restituisce 403 per un'identità non autorizzata. */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void accessoNegato() {
    }

    /** Restituisce 404 quando il contatto o la richiesta non esistono. */
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void risorsaNonTrovata() {
    }

    /** Restituisce 409 quando lo stato della richiesta o del contatto impedisce la risposta. */
    @ExceptionHandler(StatoContattoIncompatibileException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    void statoIncompatibile() {
    }

    /**
     * Corpo della richiesta per registrare la risposta.
     *
     * @param disponibile valore obbligatorio, {@code true} per confermare o {@code false} per rifiutare
     */
    record DatiRisposta(@NotNull Boolean disponibile) {
    }
}
