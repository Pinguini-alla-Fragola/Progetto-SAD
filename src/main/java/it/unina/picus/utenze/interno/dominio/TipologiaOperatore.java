package it.unina.picus.utenze.interno.dominio;

import java.util.Arrays;
import java.util.Optional;

/** Le due tipologie di Operatore che il Banco può creare. */
public enum TipologiaOperatore {
    INTERNO_BANCO,
    ESTERNO_PAGATO_BANCO;

    /**
     * @param valore testo ricevuto dal modulo
     * @return la tipologia corrispondente, oppure vuoto se il testo non è nel catalogo
     */
    public static Optional<TipologiaOperatore> da(String valore) {
        return Arrays.stream(values()).filter(tipologia -> tipologia.name().equals(valore)).findFirst();
    }

    /** Il Centro PICUS è composto solo da operatori interni del Banco. */
    public boolean consenteCentroPicus() {
        return this == INTERNO_BANCO;
    }
}
