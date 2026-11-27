package it.unina.picus.comunicazioni.interno.dominio;

/**
 * Rifiuta i caratteri di controllo, come il carattere nullo: il database non li accetta e senza questa regola
 * l'invio fallirebbe con un errore generico invece di indicare il campo da correggere. Nel testo restano ammessi
 * gli a capo e le tabulazioni, che servono a impaginare il messaggio; nell'oggetto non è ammesso nessuno.
 * È la prima regola, così le altre lavorano su testo pulito.
 */
public final class RegolaCaratteri implements RegolaComunicazione {

    @Override
    public void verifica(BozzaComunicazione bozza, Errori errori) {
        if (contiene(bozza.titolo(), false)) {
            errori.aggiungi(CampoComunicazione.TITOLO, "L'oggetto contiene caratteri non ammessi");
        }
        if (contiene(bozza.contenuto(), true)) {
            errori.aggiungi(CampoComunicazione.CONTENUTO, "Il testo contiene caratteri non ammessi");
        }
    }

    private static boolean contiene(String valore, boolean impaginazioneAmmessa) {
        return valore != null && valore.chars().anyMatch(carattere -> Character.isISOControl(carattere)
                && !(impaginazioneAmmessa && (carattere == '\n' || carattere == '\r' || carattere == '\t')));
    }
}
