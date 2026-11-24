package it.unina.picus.monitoraggio.interno.applicazione;

import java.math.BigInteger;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/** Caso d'uso interno per consultare i conteggi OPT con precisione intera. */
public interface ConsultazioneConteggiOpt {

    /**
     * Conteggio dichiarato per una voce della misura nel periodo consultato.
     *
     * @param optId account dell'organizzazione
     * @param misuraId identificativo della misura
     * @param voce codice della categoria dichiarata
     * @param valore valore esatto, incluso zero se dichiarato
     */
    record ConteggioEsatto(UUID optId, long misuraId, String voce, BigInteger valore) {}

    /**
     * Somma i valori correnti per OPT, misura e voce nei trimestri inclusi. Le voci mai
     * dichiarate nel periodo sono assenti. Per il singolo trimestre i due estremi coincidono.
     *
     * @param attore identità verificata del Banco o della OPT titolare
     * @param optId account dell'organizzazione oppure {@code null} per tutte le OPT, riservato al Banco
     * @param da primo trimestre incluso
     * @param a ultimo trimestre incluso
     * @return conteggi dichiarati, con totali anche oltre il limite di {@code long}
     * @throws SecurityException se l'attore non può consultare i conteggi richiesti
     * @throws IllegalArgumentException se il periodo manca, non è valido o è invertito
     * @throws NoSuchElementException se l'account OPT richiesto non esiste
     */
    List<ConteggioEsatto> consultaConteggiEsatti(IdentitaAutenticata attore, UUID optId, Trimestre da, Trimestre a);
}
