package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.unina.picus.comunicazioni.interno.dominio.RegolaAllegati;
import it.unina.picus.comunicazioni.interno.dominio.RegolaCaratteri;
import it.unina.picus.comunicazioni.interno.dominio.RegolaContenuto;
import it.unina.picus.comunicazioni.interno.dominio.RegolaDestinatari;
import it.unina.picus.comunicazioni.interno.dominio.ValidatoreComunicazione;

/** L'elenco delle regole applicate all'invio di una comunicazione. Per aggiungerne una, si aggiunge qui. */
@Configuration(proxyBeanMethods = false)
class ConfigurazioneRegoleComunicazione {

    @Bean
    ValidatoreComunicazione validatoreComunicazione() {
        return new ValidatoreComunicazione(
                List.of(new RegolaCaratteri(), new RegolaContenuto(), new RegolaDestinatari(), new RegolaAllegati()));
    }
}
