package it.unina.picus.shared;

import java.util.Set;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Controlli di accesso comuni alle regole dei moduli (#28).
 *
 * <p>Ogni modulo decide da sé chi può accedere alle proprie risorse (ADR-001) e usa questi metodi
 * per le parti che altrimenti sarebbero identiche ovunque: come un diniego diventa eccezione, la
 * verifica che l'identità sia completa e il controllo del solo ruolo. Un accesso non consentito
 * lancia {@link SecurityException}, come dichiarano i contratti dei moduli. La classe dipende solo
 * dall'identità di {@code utenze.api}.
 */
public final class ControlloAccesso {

    private ControlloAccesso() {
    }

    /**
     * Unico punto in cui un diniego diventa eccezione.
     *
     * @param consentito esito della decisione di accesso
     * @param messaggio motivo del diniego, senza dati della risorsa
     * @throws SecurityException se l'accesso non è consentito
     */
    public static void richiedi(boolean consentito, String messaggio) {
        if (!consentito) {
            throw new SecurityException(messaggio);
        }
    }

    /**
     * Dice se l'identità contiene sia l'account sia il ruolo. Un'identità incompleta non ha mai accesso.
     *
     * @param attore identità da controllare, anche {@code null}
     * @return {@code true} solo se l'identità, l'account e il ruolo sono presenti
     */
    public static boolean identitaCompleta(IdentitaAutenticata attore) {
        return attore != null && attore.accountId() != null && attore.ruolo() != null;
    }

    /**
     * Controllo delle operazioni che dipendono solo dal ruolo: l'identità deve essere completa e
     * il ruolo tra quelli ammessi.
     *
     * @param attore identità verificata della sessione
     * @param ammessi ruoli che possono eseguire l'operazione
     * @param messaggio motivo del diniego, senza dati della risorsa
     * @throws SecurityException se l'identità è incompleta o il ruolo non è ammesso
     */
    public static void richiediRuolo(IdentitaAutenticata attore, Set<RuoloUtente> ammessi, String messaggio) {
        richiedi(identitaCompleta(attore) && ammessi.contains(attore.ruolo()), messaggio);
    }
}
