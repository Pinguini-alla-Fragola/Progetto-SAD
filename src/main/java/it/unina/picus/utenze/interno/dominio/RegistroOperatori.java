package it.unina.picus.utenze.interno.dominio;

import java.util.UUID;

/**
 * Dove si registrano gli Operatori. Il dominio la usa senza sapere come vengono salvati i dati
 * (vedi l'adattatore in {@code interno.persistenza}).
 */
public interface RegistroOperatori {

    /** Controlla gli account di qualsiasi stato, anche revocati. */
    DisponibilitaEmail disponibilitaEmail(String email);

    boolean codiceFiscaleInUso(String codiceFiscale);

    /**
     * Salva account, profilo e misure, tutti insieme.
     *
     * @param dati i dati già controllati
     * @param hashPassword password già codificata, da salvare così com'è
     * @throws RegistrazioneInConflittoException se email o codice fiscale risultano già usati
     */
    OperatoreRegistrato registra(DatiOperatore dati, String hashPassword);

    record OperatoreRegistrato(UUID id, String nomeVisualizzato, String email) {}
}
