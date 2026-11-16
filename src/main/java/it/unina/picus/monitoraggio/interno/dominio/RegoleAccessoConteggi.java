package it.unina.picus.monitoraggio.interno.dominio;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

import static it.unina.picus.shared.ControlloAccesso.richiedi;

/**
 * Regole di accesso ai conteggi trimestrali delle OPT (#28, criterio 5).
 *
 * <p>La OPT registra e consulta i propri conteggi. Il Banco consulta quelli di qualsiasi OPT e
 * quelli di tutte insieme. Gli operatori non hanno accesso. L'identità arriva dalla sessione,
 * mai da parametri del client. Un accesso non consentito lancia {@link SecurityException}, che il
 * livello web traduce in {@code 403} senza restituire dati. Un'identità incompleta non ha mai accesso.
 *
 * @see MonitoraggioApi
 */
public final class RegoleAccessoConteggi {

    private static final Set<RuoloUtente> SOLO_OPT = Set.of(RuoloUtente.OPT);

    // Relazioni con i conteggi richiesti. Il secondo argomento è la OPT richiesta, null per tutte.
    private static final BiPredicate<IdentitaAutenticata, UUID> E_BANCO =
            (attore, optId) -> attore.ruolo() == RuoloUtente.BANCO;

    // equals sull'attore: con optId null (tutte le OPT) il risultato è false, mai true.
    private static final BiPredicate<IdentitaAutenticata, UUID> E_OPT_TITOLARE =
            (attore, optId) -> attore.ruolo() == RuoloUtente.OPT
                    && attore.accountId().equals(optId);

    private static final BiPredicate<IdentitaAutenticata, UUID> PUO_CONSULTARE =
            E_BANCO.or(E_OPT_TITOLARE);

    private RegoleAccessoConteggi() {
    }

    /**
     * Registrazione dei conteggi: riservata alla OPT, che dichiara i propri.
     *
     * @param attore identità verificata della sessione
     * @throws SecurityException se l'identità è incompleta o l'attore non è una OPT
     * @see MonitoraggioApi#registraConteggiOpt
     */
    public static void verificaRegistrazione(IdentitaAutenticata attore) {
        richiediRuolo(attore, SOLO_OPT, "Operazione riservata alle OPT");
    }

    /**
     * Consultazione dei conteggi: il Banco per una qualsiasi OPT o per tutte, una OPT solo per sé.
     * Una OPT che chiede «tutte le OPT» è negata, perché quella vista è riservata al Banco.
     *
     * @param attore identità verificata della sessione
     * @param optId account della OPT richiesta, oppure {@code null} per tutte le OPT
     * @throws SecurityException se l'identità è incompleta o l'attore non è autorizzato alla vista richiesta
     * @see MonitoraggioApi#consultaConteggiOpt
     */
    public static void verificaConsultazione(IdentitaAutenticata attore, UUID optId) {
        richiedi(identitaCompleta(attore) && PUO_CONSULTARE.test(attore, optId), "Accesso negato ai conteggi");
    }

    private static void richiediRuolo(IdentitaAutenticata attore, Set<RuoloUtente> ammessi, String messaggio) {
        richiedi(identitaCompleta(attore) && ammessi.contains(attore.ruolo()), messaggio);
    }

    /** Un'identità incompleta, senza account o senza ruolo, non ha mai accesso. */
    private static boolean identitaCompleta(IdentitaAutenticata attore) {
        return attore != null && attore.accountId() != null && attore.ruolo() != null;
    }
}
