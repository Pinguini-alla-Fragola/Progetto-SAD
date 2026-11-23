package it.unina.picus.comunicazioni.interno.persistenza;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import it.unina.picus.comunicazioni.interno.dominio.FiltroComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.FiltroComunicazioni.FiltroAllegati;
import jakarta.persistence.TypedQuery;

/**
 * Traduce un filtro nella parte {@code from ... where ...} delle query sugli elenchi. La stessa parte serve per la
 * pagina e per il conteggio, così i due risultati non possono mai divergere. I valori cercati arrivano alla query
 * solo come parametri, mai dentro il testo della query; nel testo cercato {@code %} e {@code _} valgono come
 * caratteri normali.
 */
final class CondizioniElenco {

    private static final String INVIATE = "from ComunicazioneJpa c where 1 = 1";
    private static final String RICEVUTE = """
            from ComunicazioneJpa c, DestinatarioComunicazioneJpa d
            where d.id.comunicazioneId = c.id and d.id.destinatarioId = :account""";
    private static final String CON_ALLEGATI =
            "exists (select 1 from AllegatoComunicazioneJpa a where a.comunicazioneId = c.id)";
    private static final String TESTO = """
             and (lower(coalesce(c.titolo, '')) like :testo escape '!'
              or lower(c.testo) like :testo escape '!'
              or exists (select 1 from AllegatoComunicazioneJpa n
                         where n.comunicazioneId = c.id and lower(n.nomeFile) like :testo escape '!'))""";

    private final StringBuilder daDove;
    private final Map<String, Object> parametri = new LinkedHashMap<>();

    private CondizioniElenco(String base) {
        this.daDove = new StringBuilder(base);
    }

    /** Comunicazioni inviate: il filtro sulla ricezione non vale. */
    static CondizioniElenco perInviate(FiltroComunicazioni filtro) {
        return new CondizioniElenco(INVIATE).comuni(filtro);
    }

    /** Comunicazioni ricevute da un account, sempre filtrate per quell'account. */
    static CondizioniElenco perRicevute(UUID destinatarioId, FiltroComunicazioni filtro) {
        CondizioniElenco condizioni = new CondizioniElenco(RICEVUTE).comuni(filtro);
        condizioni.parametri.put("account", destinatarioId);
        switch (filtro.ricezione()) {
            case CONFERMATA -> condizioni.daDove.append(" and d.confermataIl is not null");
            case DA_CONFERMARE -> condizioni.daDove.append(" and d.confermataIl is null");
            case QUALSIASI -> { }
        }
        return condizioni;
    }

    private CondizioniElenco comuni(FiltroComunicazioni filtro) {
        if (!filtro.testo().isEmpty()) {
            daDove.append(TESTO);
            parametri.put("testo", "%" + letterale(filtro.testo().toLowerCase(Locale.ROOT)) + "%");
        }
        if (filtro.destinatari() != null) {
            daDove.append(" and c.tipoDestinatari = :tipo");
            parametri.put("tipo", filtro.destinatari());
        }
        if (filtro.allegati() == FiltroAllegati.CON) {
            daDove.append(" and ").append(CON_ALLEGATI);
        } else if (filtro.allegati() == FiltroAllegati.SENZA) {
            daDove.append(" and not ").append(CON_ALLEGATI);
        }
        return this;
    }

    /** Parte {@code from ... where ...} della query. */
    String daDove() {
        return daDove.toString();
    }

    /** Assegna alla query i valori cercati. */
    <T> TypedQuery<T> parametri(TypedQuery<T> query) {
        parametri.forEach(query::setParameter);
        return query;
    }

    /** I caratteri speciali di {@code like} diventano caratteri normali, preceduti dal carattere di escape. */
    private static String letterale(String testo) {
        return testo.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
