package it.unina.picus.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${spring.mail.host:}'.trim().isEmpty() or '${spring.profiles.active:}'.contains('dev') or '${spring.profiles.active:}'.contains('test')")
public class InvioEmailLogAdapter implements InvioEmail {

    private static final Logger LOG = LoggerFactory.getLogger(InvioEmailLogAdapter.class);

    private final String baseUrl;

    public InvioEmailLogAdapter(@Value("${picus.app.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean isSimulato() {
        return true;
    }

    @Override
    public void inviaLinkAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        LOG.info("Link di attivazione generato in modalità simulata (trasporto=LOG, baseUrl={})", baseUrl);
    }

    @Override
    public void inviaLinkResetPassword(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
        LOG.info("Link di reset generato in modalità simulata (trasporto=LOG, baseUrl={})", baseUrl);
    }
}