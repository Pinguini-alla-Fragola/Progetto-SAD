package it.unina.picus.monitoraggio.interno.dominio;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

import static it.unina.picus.shared.ControlloAccesso.identitaCompleta;
import static it.unina.picus.shared.ControlloAccesso.richiedi;
import static it.unina.picus.shared.ControlloAccesso.richiediRuolo;

/**
 * Regole di accesso alle schede personali degli operatori (#28, criteri 4 e 5).
 *
 * <p>La scheda contiene dati personali, contrattuali ed economici: la invia il solo operatore
 * titolare e la consultano il titolare e il Banco. Le OPT e gli altri operatori non hanno accesso.
 * L'identità arriva dalla sessione e il titolare della scheda dai dati salvati, mai da parametri
 * del client. Un accesso non consentito lancia {@link SecurityException}, che il livello web
 * traduce in {@code 403} senza restituire dati.
 *
 * <p>Ciò che non è espressamente consentito è negato.
 *
 * @see MonitoraggioApi
 */
public final class RegoleAccessoScheda {

    private static final Set<RuoloUtente> SOLO_BANCO = Set.of(RuoloUtente.BANCO);
    private static final Set<RuoloUtente> SOLO_OPERATORE = Set.of(RuoloUtente.OPERATORE);

    // Relazioni con la scheda. Il secondo argomento è l'account dell'operatore titolare.
    private static final BiPredicate<IdentitaAutenticata, UUID> E_BANCO =
            (attore, operatoreId) -> attore.ruolo() == RuoloUtente.BANCO;

    private static final BiPredicate<IdentitaAutenticata, UUID> E_TITOLARE =
            (attore, operatoreId) -> attore.ruolo() == RuoloUtente.OPERATORE
                    && attore.accountId().equals(operatoreId);

    private static final BiPredicate<IdentitaAutenticata, UUID> PUO_CONSULTARE =
            E_BANCO.or(E_TITOLARE);

    private RegoleAccessoScheda() {
    }

    /**
     * Invio ed elenco delle proprie schede: riservati agli operatori interni e agli esterni pagati
     * dal Banco. La scheda inviata appartiene all'operatore autenticato.
     *
     * @param attore identità verificata della sessione
     * @throws SecurityException se l'attore non è un operatore
     * @see MonitoraggioApi#inviaSchedaOperatore
     * @see MonitoraggioApi#elencoSchedeOperatore
     */
    public static void verificaOperatore(IdentitaAutenticata attore) {
        richiediRuolo(attore, SOLO_OPERATORE, "Operazione riservata agli operatori");
    }

    /**
     * Consultazione di una scheda: il Banco oppure l'operatore titolare, e nessun altro.
     *
     * @param attore identità verificata della sessione
     * @param operatoreId account dell'operatore titolare, letto dalla scheda salvata
     * @throws SecurityException se l'attore è una OPT, un altro operatore o un account incompleto
     * @see MonitoraggioApi#consultaSchedaOperatore
     */
    public static void verificaConsultazione(IdentitaAutenticata attore, UUID operatoreId) {
        richiedi(consente(PUO_CONSULTARE, attore, operatoreId), "Accesso negato alla scheda");
    }

    /**
     * Riepilogo delle schede ricevute nel trimestre, per la rendicontazione: riservato al Banco.
     *
     * @param attore identità verificata della sessione
     * @throws SecurityException se l'attore non è il Banco
     * @see MonitoraggioApi#riepilogaSchede
     */
    public static void verificaRiepilogo(IdentitaAutenticata attore) {
        richiediRuolo(attore, SOLO_BANCO, "Operazione riservata al Banco");
    }

    /** Valuta una politica solo su un'identità completa: un attore incompleto non ha mai accesso. */
    private static boolean consente(BiPredicate<IdentitaAutenticata, UUID> politica,
                                    IdentitaAutenticata attore, UUID operatoreId) {
        Objects.requireNonNull(operatoreId, "operatoreId");
        if (!identitaCompleta(attore)) {
            return false;
        }
        return politica.test(attore, operatoreId);
    }

}