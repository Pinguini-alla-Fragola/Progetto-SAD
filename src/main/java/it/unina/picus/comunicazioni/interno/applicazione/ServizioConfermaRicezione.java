package it.unina.picus.comunicazioni.interno.applicazione;

import it.unina.picus.comunicazioni.api.ComunicazioniApi;
import it.unina.picus.comunicazioni.interno.dominio.RegoleAccessoComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.RiscontroRicezioneRepository;
import it.unina.picus.shared.AccessoNegatoException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Registra il riscontro esplicito di un destinatario sulla comunicazione del Banco. */
@Service
@Profile("!skeleton")
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
     * @throws AccessoNegatoException se l'attore non può confermare per la comunicazione indicata
     * @throws NullPointerException se manca l'identificativo della comunicazione
     * @see ComunicazioniApi#confermaRicezione
     */
    @Transactional
    public Instant confermaRicezione(IdentitaAutenticata attore, UUID comunicazioneId) {
        RegoleAccessoComunicazione.verificaPossibileDestinatario(attore);
        Objects.requireNonNull(comunicazioneId, "comunicazioneId");

        return riscontri.registraPrimaConferma(comunicazioneId, attore.accountId(), clock.instant())
                .orElseThrow(() -> new AccessoNegatoException("Conferma riservata ai destinatari"));
    }
}
