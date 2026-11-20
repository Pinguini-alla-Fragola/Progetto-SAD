package it.unina.picus.utenze.interno.dominio;

/**
 * Controlla che email e codice fiscale non siano già registrati. Si ferma se ci sono già errori sui campi:
 * non ha senso cercare un'email che non è nemmeno valida.
 */
public final class RegolaUnicita implements RegolaDatiOperatore {

    private final RegistroOperatori registro;

    public RegolaUnicita(RegistroOperatori registro) {
        this.registro = registro;
    }

    @Override
    public void verifica(DatiOperatore dati, Errori errori) {
        if (!errori.vuoto()) {
            return;
        }
        switch (registro.disponibilitaEmail(dati.email())) {
            case GIA_IN_USO -> errori.aggiungi(CampoOperatore.EMAIL, "Questo indirizzo email è già in uso");
            case ACCOUNT_REVOCATO -> errori.aggiungi(CampoOperatore.EMAIL,
                    "Questo indirizzo appartiene a un account revocato: si può riutilizzare solo riattivando la stessa utenza");
            case LIBERA -> { }
        }
        if (registro.codiceFiscaleInUso(dati.codiceFiscale())) {
            errori.aggiungi(CampoOperatore.CODICE_FISCALE, "Esiste già un operatore con questo codice fiscale");
        }
    }
}
