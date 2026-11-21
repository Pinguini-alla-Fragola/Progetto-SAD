package it.unina.picus.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Seleziona esattamente un adattatore {@link InvioEmail} in base ai profili effettivi
 * dell'Environment e alla presenza di {@code spring.mail.host}.
 */
@Configuration
class ConfigurazioneInvioEmail {

    @Bean
    @Profile("!dev & !test")
    @ConditionalOnExpression("!'${spring.mail.host:}'.trim().isEmpty()")
    InvioEmailSmtpAdapter invioEmailSmtpAdapter(
            JavaMailSender mailSender,
            @Value("${picus.app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${picus.mail.from:noreply@strawberrypenguin.it}") String mittente,
            @Value("${picus.mail.from-name:Piattaforma PICUS}") String nomeMittente) {
        return new InvioEmailSmtpAdapter(mailSender, baseUrl, mittente, nomeMittente);
    }

    @Bean
    @ConditionalOnMissingBean(InvioEmail.class)
    InvioEmailLogAdapter invioEmailLogAdapter(
            @Value("${picus.app.base-url:http://localhost:8080}") String baseUrl) {
        return new InvioEmailLogAdapter(baseUrl);
    }
}