package it.unina.picus.comunicazioni.interno.dominio;

import java.util.Optional;

/**
 * Controlla gli allegati: al massimo {@value #MASSIMO_ALLEGATI} file, ciascuno non vuoto e non più grande di
 * 5 MB, con un nome valido e di un tipo ammesso, riconosciuto dal contenuto e coerente con l'estensione, e integro:
 * un file troncato o rovinato non si può aprire e viene rifiutato.
 */
public final class RegolaAllegati implements RegolaComunicazione {

    public static final int MASSIMO_ALLEGATI = 5;
    public static final int DIMENSIONE_MASSIMA_BYTE = 5 * 1024 * 1024;
    static final int LUNGHEZZA_MAX_NOME = 200;

    @Override
    public void verifica(BozzaComunicazione bozza, Errori errori) {
        if (bozza.allegati().size() > MASSIMO_ALLEGATI) {
            errori.aggiungi(CampoComunicazione.ALLEGATI,
                    "Si possono allegare al massimo " + MASSIMO_ALLEGATI + " file");
            return;
        }
        bozza.allegati().stream().map(RegolaAllegati::problema).flatMap(Optional::stream).findFirst()
                .ifPresent(problema -> errori.aggiungi(CampoComunicazione.ALLEGATI, problema));
    }

    private static Optional<String> problema(AllegatoRicevuto allegato) {
        if (!nomeValido(allegato.nomeFile())) {
            return Optional.of("Il nome di un allegato non è valido");
        }
        if (allegato.dimensione() == 0) {
            return Optional.of("Un allegato è vuoto");
        }
        if (allegato.dimensione() > DIMENSIONE_MASSIMA_BYTE) {
            return Optional.of("Ogni allegato può pesare al massimo 5 MB");
        }
        Optional<TipoAllegato> tipo = TipoAllegato.riconosci(allegato.contenuto())
                .filter(riconosciuto -> riconosciuto.ammette(allegato.nomeFile()));
        if (tipo.isEmpty()) {
            return Optional.of("Si possono allegare solo PDF, immagini PNG o JPEG e file di testo");
        }
        // Il controllo più costoso per ultimo, solo su un file già riconosciuto e della dimensione giusta.
        return tipo.get().integro(allegato.contenuto()) ? Optional.empty()
                : Optional.of("Un allegato è danneggiato o incompleto e non si può aprire");
    }

    private static boolean nomeValido(String nome) {
        return nome != null && !nome.isBlank() && nome.length() <= LUNGHEZZA_MAX_NOME
                && !nome.equals(".") && !nome.equals("..")
                && nome.chars().noneMatch(carattere -> Character.isISOControl(carattere)
                        || carattere == '/' || carattere == '\\');
    }
}
