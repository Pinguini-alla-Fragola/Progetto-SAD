package it.unina.picus.shared;

/**
 * Controllo di accesso comune alle regole dei moduli (#28).
 *
 * <p>Ogni modulo decide da sé chi può accedere alle proprie risorse (ADR-001) e usa questo metodo
 * per segnalare un diniego sempre allo stesso modo. La classe non dipende dai moduli.
 */
public final class ControlloAccesso {

    private ControlloAccesso() {
    }

    /**
     * Unico punto in cui un diniego diventa eccezione.
     *
     * @param consentito esito della decisione di accesso
     * @param messaggio motivo del diniego, senza dati della risorsa
     * @throws AccessoNegatoException se l'accesso non è consentito
     */
    public static void richiedi(boolean consentito, String messaggio) {
        if (!consentito) {
            throw new AccessoNegatoException(messaggio);
        }
    }
}
