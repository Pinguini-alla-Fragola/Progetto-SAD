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

/** Adattatore HTTP per la risposta di disponibilità e lo storico consultabile dal Banco. */
@RestController
@RequestMapping("/api")
class ContattiDisponibilitaController {

    private final DisponibilitaService disponibilita;

    ContattiDisponibilitaController(DisponibilitaService disponibilita) {
        this.disponibilita = disponibilita;
    }

    @GetMapping("/contatti-disponibilita/{contattoId}")
    DettaglioContatto consultaContatto(@PathVariable UUID contattoId, Authentication autenticazione) {
        return disponibilita.consultaContatto(identita(autenticazione), contattoId);
    }

    @PostMapping("/contatti-disponibilita/{contattoId}/risposta")
    ContattoDisponibilita rispondiDisponibilita(@PathVariable UUID contattoId,
            @Valid @RequestBody DatiRisposta dati, Authentication autenticazione) {
        return disponibilita.rispondiDisponibilita(identita(autenticazione), contattoId, dati.disponibile());
    }

    @GetMapping("/richieste/{richiestaId}/contatti")
    List<ContattoDisponibilita> elencoContatti(@PathVariable UUID richiestaId, Authentication autenticazione) {
        return disponibilita.elencoContatti(identita(autenticazione), richiestaId);
    }

    /** Ricava account e ruolo dal principal impostato da Spring Security dopo l'accesso. */
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

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void accessoNegato() {
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void risorsaNonTrovata() {
    }

    @ExceptionHandler(StatoContattoIncompatibileException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    void statoIncompatibile() {
    }

    record DatiRisposta(@NotNull Boolean disponibile) {
    }
}
