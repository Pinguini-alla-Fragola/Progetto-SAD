package it.unina.picus.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(InvioEmail.class)
public class InvioEmailLogAdapter implements InvioEmail {

    private static final Logger LOG = LoggerFactory.getLogger(InvioEmailLogAdapter.class);

    private final String baseUrl;

    public InvioEmailLogAdapter(@Value("${picus.app.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void inviaLinkAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        LOG.info("Link di attivazione generato (trasporto=LOG, baseUrl={})", baseUrl);
    }

    @Override
    public void inviaLinkResetPassword(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        LOG.info("Link di reset generato (trasporto=LOG, baseUrl={})", baseUrl);
    }
}