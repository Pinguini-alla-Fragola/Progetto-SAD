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
import org.springframework.transaction.annotation.Propagation;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

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
                || comuneCodice.codePoints().anyMatch(Character::isISOControl)) {
            throw new NoSuchElementException("Comune non trovato");
        }
        String codiceCanonico = catalogo.cercaComune(comuneCodice)
                .orElseThrow(() -> new NoSuchElementException("Comune non trovato"))
                .codice();
        Set<String> limitrofi = Set.copyOf(offerta.comuniLimitrofi(codiceCanonico));
        List<ErogatoreCompatibile> risultato = new ArrayList<>();
        for (var opt : utenze.elencoOptAttive(Set.copyOf(offerta.optAttivePerMisura(misuraId)))) {
            aggiungiLocale(risultato, opt, codiceCanonico, limitrofi);
        }
        for (var operatore : utenze.elencoOperatoriAttiviPerMisura(misuraId)) {
            aggiungiLocale(risultato, operatore, codiceCanonico, limitrofi);
            if (operatore.centroPicus() && misura.dati().erogabileADistanza()) {
                risultato.add(opzione(operatore, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA));
            }
        }
        return List.copyOf(risultato);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean bloccaCompatibilita(long misuraId, String comune, UUID accountId, AmbitoErogazione ambito) {
        var misura = offerta.bloccaMisura(misuraId);
        if (misura.isEmpty() || !misura.get().attiva()) {
            return false;
        }
        var profilo = utenze.bloccaProfiloPerMisura(accountId, misuraId);
        if (profilo.isEmpty()) {
            return false;
        }
        var p = profilo.get();
        if (p.tipo() == UtenzeApi.TipoErogatore.OPT && !offerta.bloccaOfferta(accountId, misuraId)) {
            return false;
        }
        String codiceCanonico = catalogo.cercaComune(comune).map(it.unina.picus.catalogo.api.CatalogoApi.Comune::codice)
                .orElse(comune);
        return switch (ambito) {
            case STESSO_COMUNE -> codiceCanonico.equals(p.comuneCodice());
            case COMUNE_LIMITROFO -> offerta.bloccaAdiacenza(codiceCanonico, p.comuneCodice());
            case CENTRO_PICUS_A_DISTANZA -> p.centroPicus() && misura.get().dati().erogabileADistanza();
        };
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean bloccaAbilitazioneADistanza(long misuraId) {
        return offerta.bloccaMisura(misuraId).filter(misura -> misura.dati().erogabileADistanza()).isPresent();
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
