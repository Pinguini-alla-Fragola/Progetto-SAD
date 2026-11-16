package it.unina.picus.richieste.interno.dominio;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Politica d'ordinamento isolata dai comandi e dalla persistenza. */
public final class OrdinamentoErogatori {
    private OrdinamentoErogatori() {}

    public static List<Candidato> ordina(List<ErogatoreCompatibile> opzioni, Map<UUID, Integer> carichi) {
        return opzioni.stream().map(o -> new Candidato(o, carichi.getOrDefault(o.accountId(), 0)))
                .sorted(Comparator.comparingInt((Candidato c) -> priorita(c.erogatore().ambito()))
                        .thenComparingInt(c -> c.erogatore().tipo() == TipoErogatore.OPT ? 1 : 0)
                        .thenComparingInt(Candidato::caricoAppuntamenti)
                        .thenComparing(c -> c.erogatore().nomeVisualizzato(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(c -> c.erogatore().accountId()))
                .toList();
    }

    private static int priorita(AmbitoErogazione ambito) {
        return switch (ambito) {
            case STESSO_COMUNE -> 0;
            case COMUNE_LIMITROFO -> 1;
            case CENTRO_PICUS_A_DISTANZA -> 2;
        };
    }
}
