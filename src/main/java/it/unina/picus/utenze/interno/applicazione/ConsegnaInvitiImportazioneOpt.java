package it.unina.picus.utenze.interno.applicazione;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import it.unina.picus.utenze.interno.applicazione.ConsegnaLinkAttivazione.Esito;

/** Consegna gli inviti persistiti dopo il commit e fuori dalla richiesta HTTP. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!skeleton")
class ConsegnaInvitiImportazioneOpt {

    private static final Logger LOG = LoggerFactory.getLogger(ConsegnaInvitiImportazioneOpt.class);
    private static final int INVITI_PER_CICLO = 50;

    private final CodaInvitiImportazioneOpt coda;
    private final ConsegnaLinkAttivazione consegnaLink;

    ConsegnaInvitiImportazioneOpt(CodaInvitiImportazioneOpt coda, ConsegnaLinkAttivazione consegnaLink) {
        this.coda = coda;
        this.consegnaLink = consegnaLink;
    }

    @Scheduled(fixedDelay = 1000)
    void consegnaInAttesa() {
        for (int i = 0; i < INVITI_PER_CICLO; i++) {
            var prenotazione = coda.prenota();
            if (prenotazione.isEmpty()) {
                return;
            }
            var invito = prenotazione.orElseThrow();
            if (consegnaLink.consegna(invito.dati()) == Esito.NON_RIUSCITO) {
                if (invito.tentativo() == CodaInvitiImportazioneOpt.MASSIMO_TENTATIVI) {
                    LOG.warn("Tentativi automatici esauriti per l'invito OPT (accountId={})", invito.accountId());
                } else {
                    LOG.warn("Consegna dell'invito OPT da riprovare (accountId={}, tentativo={})",
                            invito.accountId(), invito.tentativo());
                }
                continue;
            }
            coda.completa(invito);
        }
    }
}
