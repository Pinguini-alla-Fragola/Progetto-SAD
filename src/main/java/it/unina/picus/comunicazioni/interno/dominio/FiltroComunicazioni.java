package it.unina.picus.comunicazioni.interno.dominio;

import java.util.Objects;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;

/**
 * Cosa cercare negli elenchi delle comunicazioni e quale pagina restituire. Ricerca e filtri valgono su tutto lo
 * storico, non solo sulla pagina mostrata; le pagine hanno sempre {@value #DIMENSIONE_PAGINA} comunicazioni, dalla
 * più recente.
 *
 * @param testo parole da cercare in oggetto, testo e nomi degli allegati; vuoto per non filtrare
 * @param destinatari tipo di destinatari; {@code null} per tutti i tipi
 * @param allegati presenza di allegati
 * @param ricezione stato della conferma, solo per le comunicazioni ricevute
 * @param pagina numero della pagina, da zero
 */
public record FiltroComunicazioni(String testo, TipoDestinatari destinatari, FiltroAllegati allegati,
        FiltroRicezione ricezione, int pagina) {

    public static final int DIMENSIONE_PAGINA = 20;
    public static final int LUNGHEZZA_MAX_TESTO = 100;
    /** Oltre questa pagina non si va: con 20 comunicazioni per pagina sono già 200.000 comunicazioni. */
    public static final int PAGINA_MASSIMA = 10_000;

    public FiltroComunicazioni {
        Objects.requireNonNull(testo, "testo");
        Objects.requireNonNull(allegati, "allegati");
        Objects.requireNonNull(ricezione, "ricezione");
        if (pagina < 0 || pagina > PAGINA_MASSIMA) {
            throw new IllegalArgumentException("pagina fuori dai limiti");
        }
    }

    /** @return posizione della prima comunicazione della pagina nell'elenco completo */
    public int primo() {
        return pagina * DIMENSIONE_PAGINA;
    }

    /** Filtro sulla presenza di allegati. */
    public enum FiltroAllegati { QUALSIASI, CON, SENZA }

    /** Filtro sulla conferma di ricezione di chi consulta. */
    public enum FiltroRicezione { QUALSIASI, CONFERMATA, DA_CONFERMARE }
}
