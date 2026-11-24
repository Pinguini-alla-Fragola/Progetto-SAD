package it.unina.picus.utenze.interno.applicazione;

import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;

/**
 * Ultimo passo di ogni reinvio del link di attivazione, per OPT e Operatori, dal Banco o su richiesta di chi
 * possiede l'indirizzo: registra l'esito dell'invio. Se la posta ha accettato l'email il link nuovo sostituisce i
 * precedenti e l'invito originale esce dalla coda dell'importazione CSV, nella stessa transazione; se l'invio è
 * fallito il link nuovo è scartato e restano validi quelli di prima, invito compreso.
 *
 * <p>Tutti i reinvii passano da qui, così hanno lo stesso esito e lo stesso ordine dei lock: prima la riga della
 * coda, poi l'account, poi i token. È l'ordine con cui li prende anche la consegna in secondo piano della coda:
 * toglierla dopo aver bloccato l'account, o dopo il commit, lascerebbe una finestra in cui la coda prenota
 * l'invito e spedisce un secondo link valido.</p>
 */
@Service
@Profile("!skeleton")
@Transactional
public class ConclusioneReinvio {

    private final CodaInvitiImportazioneOpt codaInviti;
    private final AttivazioneUtenzaService attivazione;

    ConclusioneReinvio(CodaInvitiImportazioneOpt codaInviti, AttivazioneUtenzaService attivazione) {
        this.codaInviti = Objects.requireNonNull(codaInviti);
        this.attivazione = Objects.requireNonNull(attivazione);
    }

    /**
     * Registra l'esito dell'invio del link preparato per questo reinvio.
     *
     * @param accountId account che ha ricevuto il link
     * @param dati link preparato
     * @param riuscito consegna accettata dal server SMTP oppure completata in modalità simulata
     */
    public void concludi(UUID accountId, DatiInvioAttivazione dati, boolean riuscito) {
        if (riuscito) {
            // La prenotazione del job acquisisce i lock nello stesso ordine: coda, account, token.
            codaInviti.rimuovi(accountId);
        }
        attivazione.concludeReinvio(accountId, dati, riuscito);
    }
}
