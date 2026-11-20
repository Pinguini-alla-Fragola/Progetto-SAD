package it.unina.picus.utenze.interno.dominio;

/** Controlla nome, cognome e codice fiscale. */
public final class RegolaAnagrafica implements RegolaDatiOperatore {

    static final int LUNGHEZZA_MAX_NOME = 100;

    @Override
    public void verifica(DatiOperatore dati, Errori errori) {
        if (!nomeValido(dati.nome())) {
            errori.aggiungi(CampoOperatore.NOME, "Inserisci il nome");
        }
        if (!nomeValido(dati.cognome())) {
            errori.aggiungi(CampoOperatore.COGNOME, "Inserisci il cognome");
        }
        if (!CodiceFiscale.valido(dati.codiceFiscale())) {
            errori.aggiungi(CampoOperatore.CODICE_FISCALE, "Inserisci un codice fiscale valido");
        }
    }

    private static boolean nomeValido(String valore) {
        return valore != null && valore.length() <= LUNGHEZZA_MAX_NOME;
    }
}
