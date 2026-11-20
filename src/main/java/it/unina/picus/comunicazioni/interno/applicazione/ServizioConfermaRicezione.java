package it.unina.picus.comunicazioni.interno.applicazione;

import it.unina.picus.comunicazioni.api.ComunicazioniApi;
import it.unina.picus.comunicazioni.interno.dominio.RegoleAccessoComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.RiscontroRicezioneRepository;
import it.unina.picus.shared.AccessoNegatoException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/** Registra il riscontro esplicito di un destinatario sulla comunicazione del Banco. */
@Service
public class ServizioConfermaRicezione {

    private final RiscontroRicezioneRepository riscontri;
    private final Clock clock;

    /**
     * Collega il caso d'uso all'archivio dei destinatari e all'orologio dell'applicazione.
     *
     * @param riscontri archivio dei destinatari effettivi e dei loro riscontri
     * @param clock orologio usato per datare il primo riscontro
     */
    public ServizioConfermaRicezione(RiscontroRicezioneRepository riscontri, Clock clock) {
        this.riscontri = riscontri;
        this.clock = clock;
    }

    /**
     * Conferma la ricezione per l'account verificato della sessione. La verifica dei
     * destinatari e la registrazione avvengono nella stessa transazione. Un ritento
     * restituisce l'istante già registrato per la coppia comunicazione e destinatario.
     *
     * @param attore identità verificata della sessione
     * @param comunicazioneId identificativo della comunicazione ricevuta
     * @return istante persistito del primo riscontro
     * @throws AccessoNegatoException se l'attore non è un destinatario autorizzato
     * @throws NoSuchElementException se la comunicazione non esiste
     * @see ComunicazioniApi#confermaRicezione
     */
    @Transactional
    public Instant confermaRicezione(IdentitaAutenticata attore, UUID comunicazioneId) {
        Objects.requireNonNull(comunicazioneId, "comunicazioneId");
        var destinatari = riscontri.cercaDestinatari(comunicazioneId)
                .orElseThrow(() -> new NoSuchElementException("Comunicazione non trovata"));
        RegoleAccessoComunicazione.verificaConfermaRicezione(attore, destinatari);

        return riscontri.registraPrimaConferma(comunicazioneId, attore.accountId(), clock.instant())
                .orElseThrow(() -> new AccessoNegatoException("Conferma riservata ai destinatari"));
    }
}
