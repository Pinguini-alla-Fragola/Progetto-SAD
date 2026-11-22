package it.unina.picus.utenze.interno.dominio;

import java.util.Optional;

/** Controlla la tipologia dell'Operatore e se può appartenere al Centro PICUS. */
public final class RegolaInquadramento implements RegolaDatiOperatore {

    @Override
    public void verifica(DatiOperatore dati, Errori errori) {
        Optional<TipologiaOperatore> tipologia = TipologiaOperatore.da(dati.tipologia());
        if (tipologia.isEmpty()) {
            errori.aggiungi(CampoOperatore.TIPOLOGIA, "Seleziona una tipologia consentita");
        }
        if (dati.centroPicus() && !tipologia.map(TipologiaOperatore::consenteCentroPicus).orElse(false)) {
            errori.aggiungi(CampoOperatore.CENTRO_PICUS, "Solo gli operatori interni possono essere del Centro PICUS");
        }
    }
}
