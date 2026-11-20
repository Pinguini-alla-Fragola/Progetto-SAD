package it.unina.picus.utenze.interno.dominio;

/** Controlla che comune e misure scelti esistano nel Catalogo. */
public final class RegolaRiferimentiCatalogo implements RegolaDatiOperatore {

    private final RiferimentiCatalogo catalogo;

    public RegolaRiferimentiCatalogo(RiferimentiCatalogo catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public void verifica(DatiOperatore dati, Errori errori) {
        if (dati.comuneCodice() == null || !catalogo.comuneEsiste(dati.comuneCodice())) {
            errori.aggiungi(CampoOperatore.COMUNE, "Seleziona un comune del catalogo");
        }
        if (dati.misure().isEmpty() || !dati.misure().stream().allMatch(catalogo::misuraAttiva)) {
            errori.aggiungi(CampoOperatore.MISURE, "Seleziona almeno una misura del catalogo");
        }
    }
}
