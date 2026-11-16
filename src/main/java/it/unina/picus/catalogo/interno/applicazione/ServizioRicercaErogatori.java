package it.unina.picus.catalogo.interno.applicazione;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.catalogo.api.RicercaErogatoriApi;
import it.unina.picus.catalogo.interno.persistenza.OffertaTerritorialeJpa;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.ProfiloErogatore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/** Combina offerta OPT e profili personali attraverso i confini pubblici dei moduli. */
@Service
@Transactional(readOnly = true)
public class ServizioRicercaErogatori implements RicercaErogatoriApi {
    private final ConsultazioneCatalogoApi catalogo;
    private final OffertaTerritorialeJpa offerta;
    private final UtenzeApi utenze;

    public ServizioRicercaErogatori(ConsultazioneCatalogoApi catalogo, OffertaTerritorialeJpa offerta, UtenzeApi utenze) {
        this.catalogo = catalogo;
        this.offerta = offerta;
        this.utenze = utenze;
    }

    @Override
    public List<ErogatoreCompatibile> cercaErogatoriCompatibili(long misuraId, String comuneCodice) {
        var misura = catalogo.cercaMisura(misuraId).orElseThrow(() -> new NoSuchElementException("Misura non trovata"));
        if (comuneCodice == null || comuneCodice.length() > 50
                || comuneCodice.codePoints().anyMatch(Character::isISOControl)
                || catalogo.cercaComune(comuneCodice).isEmpty()) {
            throw new NoSuchElementException("Comune non trovato");
        }
        Set<String> limitrofi = Set.copyOf(offerta.comuniLimitrofi(comuneCodice));
        List<ErogatoreCompatibile> risultato = new ArrayList<>();
        for (var optId : offerta.optAttivePerMisura(misuraId)) {
            utenze.cercaProfiloErogatoreAttivo(optId)
                    .filter(p -> p.tipo() == UtenzeApi.TipoErogatore.OPT)
                    .ifPresent(p -> aggiungiLocale(risultato, p, comuneCodice, limitrofi));
        }
        for (var operatore : utenze.elencoOperatoriAttiviPerMisura(misuraId)) {
            aggiungiLocale(risultato, operatore, comuneCodice, limitrofi);
            if (operatore.centroPicus() && misura.dati().erogabileADistanza()) {
                risultato.add(opzione(operatore, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA));
            }
        }
        return List.copyOf(risultato);
    }

    private static void aggiungiLocale(List<ErogatoreCompatibile> risultato, ProfiloErogatore profilo,
            String comune, Set<String> limitrofi) {
        if (comune.equals(profilo.comuneCodice())) {
            risultato.add(opzione(profilo, AmbitoErogazione.STESSO_COMUNE));
        } else if (limitrofi.contains(profilo.comuneCodice())) {
            risultato.add(opzione(profilo, AmbitoErogazione.COMUNE_LIMITROFO));
        }
    }

    private static ErogatoreCompatibile opzione(ProfiloErogatore profilo, AmbitoErogazione ambito) {
        return new ErogatoreCompatibile(profilo.accountId(), profilo.nomeVisualizzato(),
                profilo.tipo(), profilo.comuneCodice(), ambito);
    }
}
