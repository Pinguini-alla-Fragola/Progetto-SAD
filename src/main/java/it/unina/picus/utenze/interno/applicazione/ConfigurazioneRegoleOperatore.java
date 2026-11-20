package it.unina.picus.utenze.interno.applicazione;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import it.unina.picus.utenze.interno.dominio.RegistroOperatori;
import it.unina.picus.utenze.interno.dominio.RegolaAnagrafica;
import it.unina.picus.utenze.interno.dominio.RegolaContatti;
import it.unina.picus.utenze.interno.dominio.RegolaInquadramento;
import it.unina.picus.utenze.interno.dominio.RegolaRiferimentiCatalogo;
import it.unina.picus.utenze.interno.dominio.RegolaUnicita;
import it.unina.picus.utenze.interno.dominio.RiferimentiCatalogo;
import it.unina.picus.utenze.interno.dominio.ValidatoreDatiOperatore;

/** L'elenco delle regole applicate alla creazione di un Operatore. Per aggiungerne una, si aggiunge qui. */
@Configuration
@Profile("!skeleton")
class ConfigurazioneRegoleOperatore {

    @Bean
    ValidatoreDatiOperatore validatoreDatiOperatore(RiferimentiCatalogo catalogo, RegistroOperatori registro) {
        return new ValidatoreDatiOperatore(List.of(
                new RegolaAnagrafica(),
                new RegolaContatti(),
                new RegolaInquadramento(),
                new RegolaRiferimentiCatalogo(catalogo),
                new RegolaUnicita(registro)));
    }
}
