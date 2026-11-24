package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.Map;
import java.util.Optional;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.comunicazioni.interno.dominio.Errori;
import it.unina.picus.comunicazioni.interno.dominio.FiltroComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.FiltroComunicazioni.FiltroAllegati;
import it.unina.picus.comunicazioni.interno.dominio.FiltroComunicazioni.FiltroRicezione;

/**
 * Trasforma la ricerca ricevuta dalla pagina in un filtro controllato. Un valore che manca vuol dire «nessun
 * filtro»; un valore sbagliato non viene ignorato ma segnalato sul suo campo, senza ripeterlo nel messaggio.
 */
final class NormalizzatoreFiltro {

    static final String CAMPO_TESTO = "testo";
    static final String CAMPO_DESTINATARI = "destinatari";
    static final String CAMPO_ALLEGATI = "allegati";
    static final String CAMPO_RICEZIONE = "ricezione";
    static final String CAMPO_PAGINA = "pagina";

    private static final Map<String, FiltroAllegati> ALLEGATI =
            Map.of("con", FiltroAllegati.CON, "senza", FiltroAllegati.SENZA);
    private static final Map<String, FiltroRicezione> RICEZIONE =
            Map.of("confermata", FiltroRicezione.CONFERMATA, "da-confermare", FiltroRicezione.DA_CONFERMARE);

    private NormalizzatoreFiltro() {
    }

    /**
     * Controlla e converte la ricerca.
     *
     * @param richiesta valori ricevuti dalla pagina; {@code null} vale come nessun filtro
     * @return il filtro da usare
     * @throws DatiComunicazioneNonValidiException se un valore non è ammesso
     */
    static FiltroComunicazioni normalizza(RichiestaElenco richiesta) {
        RichiestaElenco valori = richiesta != null ? richiesta : new RichiestaElenco(null, null, null, null, null);
        Errori errori = new Errori();
        String testo = testo(valori.testo(), errori);
        TipoDestinatari destinatari = destinatari(valori.destinatari(), errori);
        FiltroAllegati allegati = scelta(valori.allegati(), ALLEGATI, FiltroAllegati.QUALSIASI, CAMPO_ALLEGATI,
                errori);
        FiltroRicezione ricezione = scelta(valori.ricezione(), RICEZIONE, FiltroRicezione.QUALSIASI,
                CAMPO_RICEZIONE, errori);
        int pagina = pagina(valori.pagina(), errori);
        if (!errori.vuoto()) {
            throw new DatiComunicazioneNonValidiException(errori.perCampo());
        }
        return new FiltroComunicazioni(testo, destinatari, allegati, ricezione, pagina);
    }

    private static String testo(String valore, Errori errori) {
        String testo = Optional.ofNullable(NormalizzatoreComunicazione.testo(valore)).orElse("");
        if (testo.length() > FiltroComunicazioni.LUNGHEZZA_MAX_TESTO) {
            errori.aggiungi(CAMPO_TESTO, "La ricerca può avere al massimo "
                    + FiltroComunicazioni.LUNGHEZZA_MAX_TESTO + " caratteri");
        } else if (testo.chars().anyMatch(Character::isISOControl)) {
            errori.aggiungi(CAMPO_TESTO, "La ricerca contiene caratteri non ammessi");
        }
        return testo;
    }

    private static TipoDestinatari destinatari(String valore, Errori errori) {
        if (NormalizzatoreComunicazione.testo(valore) == null) {
            return null;
        }
        TipoDestinatari tipo = NormalizzatoreComunicazione.tipo(valore);
        if (tipo == null) {
            errori.aggiungi(CAMPO_DESTINATARI, "Tipo di destinatari non valido");
        }
        return tipo;
    }

    private static <T> T scelta(String valore, Map<String, T> ammesse, T qualsiasi, String campo, Errori errori) {
        String nome = NormalizzatoreComunicazione.testo(valore);
        if (nome == null) {
            return qualsiasi;
        }
        T scelta = ammesse.get(nome);
        if (scelta == null) {
            errori.aggiungi(campo, "Filtro non valido");
            return qualsiasi;
        }
        return scelta;
    }

    private static int pagina(String valore, Errori errori) {
        String numero = NormalizzatoreComunicazione.testo(valore);
        if (numero == null) {
            return 0;
        }
        // Al massimo 5 cifre: un numero enorme non arriva nemmeno alla conversione.
        if (numero.length() <= 5 && numero.chars().allMatch(carattere -> carattere >= '0' && carattere <= '9')) {
            int pagina = Integer.parseInt(numero);
            if (pagina <= FiltroComunicazioni.PAGINA_MASSIMA) {
                return pagina;
            }
        }
        errori.aggiungi(CAMPO_PAGINA, "Pagina non valida");
        return 0;
    }
}
