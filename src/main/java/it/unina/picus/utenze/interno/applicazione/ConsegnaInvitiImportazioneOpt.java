package it.unina.picus.utenze.interno.applicazione;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import it.unina.picus.shared.InvioEmail;

/** Consegna gli inviti persistiti dopo il commit e fuori dalla richiesta HTTP. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!skeleton")
class ConsegnaInvitiImportazioneOpt {

    private static final Logger LOG = LoggerFactory.getLogger(ConsegnaInvitiImportazioneOpt.class);
    private static final int INVITI_PER_CICLO = 50;

    private final CodaInvitiImportazioneOpt coda;
    private final InvioEmail invioEmail;

    ConsegnaInvitiImportazioneOpt(CodaInvitiImportazioneOpt coda, InvioEmail invioEmail) {
        this.coda = coda;
        this.invioEmail = invioEmail;
    }

    @Scheduled(fixedDelay = 1000)
    void consegnaInAttesa() {
        for (int i = 0; i < INVITI_PER_CICLO; i++) {
            var prenotazione = coda.prenota();
            if (prenotazione.isEmpty()) {
                return;
            }
            var consegna = prenotazione.orElseThrow();
            var dati = consegna.dati();
            try {
                invioEmail.inviaLinkAttivazione(dati.emailDestinatario(), dati.nomeDestinatario(), dati.tokenInChiaro());
            } catch (RuntimeException ex) {
                if (consegna.tentativo() == CodaInvitiImportazioneOpt.MASSIMO_TENTATIVI) {
                    LOG.warn("Tentativi automatici esauriti per l'invito OPT (accountId={}, causa={})",
                            consegna.accountId(), ex.getClass().getSimpleName());
                } else {
                    LOG.warn("Consegna dell'invito OPT da riprovare (accountId={}, tentativo={}, causa={})",
                            consegna.accountId(), consegna.tentativo(), ex.getClass().getSimpleName());
                }
                continue;
            }
            coda.completa(consegna);
        }
    }
}
