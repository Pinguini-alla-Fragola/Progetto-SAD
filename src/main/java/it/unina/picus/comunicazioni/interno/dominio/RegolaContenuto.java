package it.unina.picus.comunicazioni.interno.dominio;

/** Controlla oggetto e testo: l'oggetto è facoltativo, il testo è obbligatorio. */
public final class RegolaContenuto implements RegolaComunicazione {

    static final int LUNGHEZZA_MAX_TITOLO = 150;
    static final int LUNGHEZZA_MAX_CONTENUTO = 10_000;

    @Override
    public void verifica(BozzaComunicazione bozza, Errori errori) {
        if (bozza.titolo() != null && bozza.titolo().length() > LUNGHEZZA_MAX_TITOLO) {
            errori.aggiungi(CampoComunicazione.TITOLO,
                    "L'oggetto può avere al massimo " + LUNGHEZZA_MAX_TITOLO + " caratteri");
        }
        if (bozza.contenuto() == null) {
            errori.aggiungi(CampoComunicazione.CONTENUTO, "Scrivi il testo della comunicazione");
        } else if (bozza.contenuto().length() > LUNGHEZZA_MAX_CONTENUTO) {
            errori.aggiungi(CampoComunicazione.CONTENUTO,
                    "Il testo può avere al massimo " + LUNGHEZZA_MAX_CONTENUTO + " caratteri");
        }
    }
}
