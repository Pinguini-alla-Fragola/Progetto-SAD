package it.unina.picus.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InvioEmailLogAdapter implements InvioEmail {

    private static final Logger LOG = LoggerFactory.getLogger(InvioEmailLogAdapter.class);

    private final String baseUrl;

    public InvioEmailLogAdapter(@Value("${picus.app.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void inviaLinkAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        String link = baseUrl + "/attivazione?token=" + tokenInChiaro;
        LOG.info("Invio link di attivazione a {} <{}>: {}", nomeDestinatario, emailDestinatario, link);
    }
}