package it.unina.picus.comunicazioni.interno.dominio;

import java.util.List;

/**
 * Una pagina di un elenco di comunicazioni.
 *
 * @param elementi comunicazioni della pagina, dalla più recente
 * @param pagina numero della pagina, da zero
 * @param dimensione numero massimo di comunicazioni per pagina
 * @param totale comunicazioni che rispettano il filtro in tutto lo storico
 */
public record PaginaComunicazioni(List<ComunicazioneSalvata> elementi, int pagina, int dimensione, long totale) {

    public PaginaComunicazioni {
        elementi = List.copyOf(elementi);
    }
}
