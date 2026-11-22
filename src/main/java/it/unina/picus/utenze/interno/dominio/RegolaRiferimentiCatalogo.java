package it.unina.picus.utenze.interno.dominio;

import it.unina.picus.utenze.api.RiferimentiCatalogo;

/** Controlla che comune e misure scelti esistano nel Catalogo. */
public final class RegolaRiferimentiCatalogo implements RegolaDatiOperatore {

    /**
     * Limite al numero di misure indicate: ogni misura richiede una verifica sul catalogo, quindi una richiesta
     * con migliaia di misure appesantirebbe il database.
     */
    static final int MASSIMO_MISURE = 100;

    private final RiferimentiCatalogo catalogo;

    public RegolaRiferimentiCatalogo(RiferimentiCatalogo catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public void verifica(DatiOperatore dati, Errori errori) {
        // Un comune già scartato, per esempio per caratteri non ammessi, non va cercato nel catalogo.
        if (!errori.contiene(CampoOperatore.COMUNE)
                && (dati.comuneCodice() == null || !catalogo.comuneEsiste(dati.comuneCodice()))) {
            errori.aggiungi(CampoOperatore.COMUNE, "Seleziona un comune del catalogo");
        }
        if (dati.misure().size() > MASSIMO_MISURE) {
            errori.aggiungi(CampoOperatore.MISURE, "Si possono indicare al massimo " + MASSIMO_MISURE + " misure");
        } else if (dati.misure().isEmpty() || !dati.misure().stream().allMatch(catalogo::misuraAttiva)) {
            errori.aggiungi(CampoOperatore.MISURE, "Seleziona almeno una misura del catalogo");
        }
    }
}
