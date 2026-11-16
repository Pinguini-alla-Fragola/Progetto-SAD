package it.unina.picus.comunicazioni.interno.dominio;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import it.unina.picus.comunicazioni.api.ComunicazioniApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

import static it.unina.picus.shared.ControlloAccesso.identitaCompleta;
import static it.unina.picus.shared.ControlloAccesso.richiedi;
import static it.unina.picus.shared.ControlloAccesso.richiediRuolo;

/**
 * Regole di accesso alle comunicazioni del Banco (#28, criterio 7).
 *
 * <p>Una comunicazione la invia e ne vede i riscontri il solo Banco. La consultano, allegati
 * compresi, il Banco e i destinatari, cioè gli account OPT od operatori che il modulo ha
 * registrato al momento dell'invio. Ogni destinatario conferma la ricezione soltanto per il
 * proprio account: il riscontro va registrato per l'account della sessione, mai per uno indicato
 * dal client. L'identità arriva dalla sessione e i destinatari dai dati salvati. Un accesso non
 * consentito lancia {@link SecurityException}, che il livello web traduce in {@code 403} senza
 * restituire dati.
 *
 * <p>Gli elenchi delle comunicazioni ricevute e degli avvisi operativi non richiedono una regola:
 * restituiscono soltanto ciò che appartiene all'account della sessione.
 *
 * @see ComunicazioniApi
 */
public final class RegoleAccessoComunicazione {

    private static final Set<RuoloUtente> SOLO_BANCO = Set.of(RuoloUtente.BANCO);

    // Ruoli che possono essere destinatari: le OPT e gli operatori, non il Banco.
    private static final Set<RuoloUtente> RUOLI_DESTINATARI = Set.of(RuoloUtente.OPT, RuoloUtente.OPERATORE);

    // Relazioni con la comunicazione. Il secondo argomento sono gli account destinatari registrati all'invio.
    private static final BiPredicate<IdentitaAutenticata, Set<UUID>> E_BANCO =
            (attore, destinatari) -> attore.ruolo() == RuoloUtente.BANCO;

    private static final BiPredicate<IdentitaAutenticata, Set<UUID>> E_DESTINATARIO =
            (attore, destinatari) -> RUOLI_DESTINATARI.contains(attore.ruolo())
                    && destinatari.contains(attore.accountId());

    // Oggi la conferma è dei soli destinatari
    private static final BiPredicate<IdentitaAutenticata, Set<UUID>> PUO_CONSULTARE =
            E_BANCO.or(E_DESTINATARIO);

    private static final BiPredicate<IdentitaAutenticata, Set<UUID>> PUO_CONFERMARE =
            E_DESTINATARIO;

    private RegoleAccessoComunicazione() {
    }

    /**
     * Invio delle comunicazioni, elenco delle inviate e dei riscontri: riservati al Banco.
     *
     * @param attore identità verificata della sessione
     * @throws SecurityException se l'attore non è il Banco
     * @see ComunicazioniApi#inviaComunicazione
     * @see ComunicazioniApi#elencoInviate
     * @see ComunicazioniApi#elencoRiscontri
     */
    public static void verificaBanco(IdentitaAutenticata attore) {
        richiediRuolo(attore, SOLO_BANCO, "Operazione riservata al Banco");
    }

    /**
     * Consultazione di una comunicazione e download dei suoi allegati: il Banco oppure un
     * destinatario. Per un allegato si usano i destinatari della comunicazione a cui appartiene.
     *
     * @param attore identità verificata della sessione
     * @param destinatari account destinatari registrati all'invio, letti dai dati salvati
     * @throws SecurityException se l'attore non è il Banco né un destinatario
     * @see ComunicazioniApi#consultaComunicazione
     * @see ComunicazioniApi#scaricaAllegato
     */
    public static void verificaConsultazione(IdentitaAutenticata attore, Set<UUID> destinatari) {
        richiedi(consente(PUO_CONSULTARE, attore, destinatari), "Accesso negato alla comunicazione");
    }

    /**
     * Conferma di ricezione: riservata ai destinatari. Il Banco non può confermare al posto loro.
     * Chi chiama registra il riscontro per {@code attore.accountId()}.
     *
     * @param attore identità verificata della sessione
     * @param destinatari account destinatari registrati all'invio, letti dai dati salvati
     * @throws SecurityException se l'attore non è un destinatario
     * @see ComunicazioniApi#confermaRicezione
     */
    public static void verificaConfermaRicezione(IdentitaAutenticata attore, Set<UUID> destinatari) {
        richiedi(consente(PUO_CONFERMARE, attore, destinatari), "Conferma riservata ai destinatari");
    }

    /** Valuta una politica solo su un'identità completa: un attore incompleto non ha mai accesso. */
    private static boolean consente(BiPredicate<IdentitaAutenticata, Set<UUID>> politica,
                                    IdentitaAutenticata attore, Set<UUID> destinatari) {
        Objects.requireNonNull(destinatari, "destinatari");
        if (!identitaCompleta(attore)) {
            return false;
        }
        return politica.test(attore, destinatari);
    }

}