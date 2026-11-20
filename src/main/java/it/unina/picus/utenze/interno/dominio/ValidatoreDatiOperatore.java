package it.unina.picus.utenze.interno.dominio;

import java.util.List;

/** Applica in ordine tutte le regole e restituisce gli errori trovati. */
public final class ValidatoreDatiOperatore {

    private final List<RegolaDatiOperatore> regole;

    public ValidatoreDatiOperatore(List<RegolaDatiOperatore> regole) {
        this.regole = List.copyOf(regole);
    }

    public Errori verifica(DatiOperatore dati) {
        Errori errori = new Errori();
        for (RegolaDatiOperatore regola : regole) {
            regola.verifica(dati, errori);
        }
        return errori;
    }
}
