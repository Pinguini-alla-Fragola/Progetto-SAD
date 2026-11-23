package it.unina.picus.comunicazioni.interno.dominio;

import java.util.List;

/** Applica in ordine tutte le regole e restituisce gli errori trovati. */
public final class ValidatoreComunicazione {

    private final List<RegolaComunicazione> regole;

    public ValidatoreComunicazione(List<RegolaComunicazione> regole) {
        this.regole = List.copyOf(regole);
    }

    public Errori verifica(BozzaComunicazione bozza) {
        Errori errori = new Errori();
        for (RegolaComunicazione regola : regole) {
            regola.verifica(bozza, errori);
        }
        return errori;
    }
}
