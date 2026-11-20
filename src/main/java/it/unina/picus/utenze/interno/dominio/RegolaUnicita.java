package it.unina.picus.utenze.interno.dominio;

/**
 * Controlla che email e codice fiscale non siano già registrati. Si ferma se ci sono già errori sui campi:
 * non ha senso cercare un'email che non è nemmeno valida.
 */
public final class RegolaUnicita implements RegolaDatiOperatore {

    static final String EMAIL_IN_USO = "Questo indirizzo email è già in uso";
    static final String EMAIL_DI_ACCOUNT_REVOCATO =
            "Questo indirizzo appartiene a un account revocato: si può riutilizzare solo riattivando la stessa utenza";
    static final String CODICE_FISCALE_IN_USO = "Esiste già un operatore con questo codice fiscale";

    private final RegistroOperatori registro;

    public RegolaUnicita(RegistroOperatori registro) {
        this.registro = registro;
    }

    /**
     * Restituisce il messaggio per un valore già registrato, anche quando il duplicato emerge solo al salvataggio.
     *
     * @param campo {@link CampoOperatore#EMAIL} oppure {@link CampoOperatore#CODICE_FISCALE}
     * @return il messaggio da mostrare sul campo
     */
    public static String messaggioDuplicato(String campo) {
        return CampoOperatore.CODICE_FISCALE.equals(campo) ? CODICE_FISCALE_IN_USO : EMAIL_IN_USO;
    }

    @Override
    public void verifica(DatiOperatore dati, Errori errori) {
        if (!errori.vuoto()) {
            return;
        }
        switch (registro.disponibilitaEmail(dati.email())) {
            case GIA_IN_USO -> errori.aggiungi(CampoOperatore.EMAIL, EMAIL_IN_USO);
            case ACCOUNT_REVOCATO -> errori.aggiungi(CampoOperatore.EMAIL, EMAIL_DI_ACCOUNT_REVOCATO);
            case LIBERA -> { }
        }
        if (registro.codiceFiscaleInUso(dati.codiceFiscale())) {
            errori.aggiungi(CampoOperatore.CODICE_FISCALE, CODICE_FISCALE_IN_USO);
        }
    }
}
