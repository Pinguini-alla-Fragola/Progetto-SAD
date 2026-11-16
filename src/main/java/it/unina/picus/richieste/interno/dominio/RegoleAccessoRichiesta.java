package it.unina.picus.richieste.interno.dominio;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

import static it.unina.picus.shared.ControlloAccesso.richiedi;

/**
 * Regole di accesso alle richieste (#28), usate dall'implementazione di {@link RichiesteApi}.
 *
 * <p>Il contratto stabilisce che ogni operazione riceve l'identità verificata dal server e
 * controlla il ruolo e il legame dell'attore con la pratica. Queste regole decidono con l'identità
 * della sessione e con i soggetti letti dai dati salvati, mai con parametri del client.
 * Quando l'accesso non è consentito lanciano {@link SecurityException}, come dichiarato nel
 * contratto. Il livello web la traduce in {@code 403} senza restituire dati.
 *
 * <p>Ciò che non è espressamente consentito è negato. Un'identità incompleta non ha mai accesso.
 * Finché l'erogatore non è assegnato, la relazione «erogatore» non concede nulla a nessuno: il
 * Banco e la OPT titolare mantengono il proprio accesso.
 */
public final class RegoleAccessoRichiesta {

    /**
     * Soggetti legati a una richiesta. Vanno letti dai dati salvati, mai dai parametri del client.
     *
     * @param titolareId account della OPT che ha inviato la richiesta
     * @param erogatoreId account dell'erogatore assegnato, {@code null} prima dell'assegnazione
     */
    public record Soggetti(UUID titolareId, UUID erogatoreId) {
        /** Una richiesta ha sempre un titolare. */
        public Soggetti {
            Objects.requireNonNull(titolareId, "titolareId");
        }
    }

    /** Attore e soggetti della richiesta su cui si decide. */
    private record Contesto(IdentitaAutenticata attore, Soggetti soggetti) {
    }

    // -- regole come dati
    // Ruoli ammessi dalle operazioni che dipendono solo dal ruolo di chi le intende eseguire.
    private static final Set<RuoloUtente> SOLO_BANCO = Set.of(RuoloUtente.BANCO);
    private static final Set<RuoloUtente> SOLO_OPT = Set.of(RuoloUtente.OPT);
    private static final Set<RuoloUtente> EROGATORI = Set.of(RuoloUtente.OPT, RuoloUtente.OPERATORE);

    // Relazioni con la richiesta: una per riga della tabella della #28.
    // sono tre domande sull'attore che possono essere riusate in tutto il codice
    // la E iniziale sta per "è": "è il Banco?", "è il titolare?", "è l'erogatore assegnato?"
    // serve per la quinta Acceptance criteria
    private static final Predicate<Contesto> E_BANCO =
            c -> c.attore().ruolo() == RuoloUtente.BANCO;

    // due condizioni in and perché l'accesso ai dati per una richiesta deve essere consentito unicamente
    // all'account che ha come ruolo OPT ma che è anche il titolare della richiesta
    // Serve per soddisfare il primo Acceptance criteria.
    private static final Predicate<Contesto> E_TITOLARE =
            c -> c.attore().ruolo() == RuoloUtente.OPT
                    && c.attore().accountId().equals(c.soggetti().titolareId());

    // equals sull'attore: se l'erogatore non c'è ancora (null) il risultato è false, mai true.
    private static final Predicate<Contesto> E_EROGATORE_ASSEGNATO =
            c -> EROGATORI.contains(c.attore().ruolo())
                    && c.attore().accountId().equals(c.soggetti().erogatoreId());

    // Politiche: chi può fare cosa. Oggi coincidono, ma nascono da requisiti diversi e restano
    // separate, così cambiarne una non modifica l'altra.
    private static final Predicate<Contesto> PUO_CONSULTARE =
            E_BANCO.or(E_TITOLARE).or(E_EROGATORE_ASSEGNATO);

    private static final Predicate<Contesto> PUO_ANNULLARE =
            E_BANCO.or(E_TITOLARE).or(E_EROGATORE_ASSEGNATO);

    private static final Predicate<Contesto> VEDE_DATI_DEL_TITOLARE =
            E_BANCO.or(E_TITOLARE);

    private RegoleAccessoRichiesta() {
    }

    /**
     * Operazioni riservate al Banco di coordinamento: coda e archivio, presa in carico, ricerca e
     * contatto degli erogatori, assegnazione, conferma dell'accordo e rifiuto definitivo.
     *
     * @param attore identità verificata della sessione
     * @throws SecurityException se l'identità è incompleta o l'attore non è il Banco
     * @see RichiesteApi#prendiInCarico
     * @see RichiesteApi#confermaAccordo
     * @see RichiesteApi#rifiutaRichiesta
     */
    public static void verificaBanco(IdentitaAutenticata attore) {
        richiediRuolo(attore, SOLO_BANCO, "Operazione riservata al Banco");
    }

    /**
     * Operazioni della OPT richiedente: invio ed elenco delle proprie richieste.
     *
     * @param attore identità verificata della sessione
     * @throws SecurityException se l'identità è incompleta o l'attore non è una OPT
     * @see RichiesteApi#invia
     * @see RichiesteApi#elencoRichiesteOpt
     */
    public static void verificaOpt(IdentitaAutenticata attore) {
        richiediRuolo(attore, SOLO_OPT, "Operazione riservata alle OPT");
    }

    /**
     * Elenco delle pratiche assegnate, riservato a chi può erogare: OPT e operatori.
     *
     * @param attore identità verificata della sessione
     * @throws SecurityException se l'identità è incompleta o l'attore non ha un ruolo da erogatore
     * @see RichiesteApi#elencoAssegnazioni
     */
    public static void verificaErogatore(IdentitaAutenticata attore) {
        richiediRuolo(attore, EROGATORI, "Operazione riservata agli erogatori");
    }

    /**
     * Consultazione del dettaglio: il Banco, la OPT titolare o l'erogatore assegnato.
     *
     * @param attore identità verificata della sessione
     * @param soggetti titolare ed erogatore letti dalla richiesta salvata
     * @throws SecurityException se l'identità è incompleta o l'attore non ha relazione con la richiesta
     * @throws NullPointerException se i soggetti mancano per un attore con identità completa
     * @see RichiesteApi#consultaRichiesta
     */
    public static void verificaConsultazione(IdentitaAutenticata attore, Soggetti soggetti) {
        richiedi(consente(PUO_CONSULTARE, attore, soggetti), "Accesso negato alla richiesta");
    }

    // Attenzione: essere un erogatore candidato non significa avere la possibilità di accedere alle funzionalità dell'erogatore
    // Solo dopo l'assegnazione alla richiesta dà la possibilità di annullarla (con motivazione obbligatoria).
    /**
     * Annullamento: il Banco, la OPT titolare o l'erogatore assegnato. Il solo contatto come
     * candidato non basta, perché l'erogatore viene registrato solo con l'assegnazione. Le regole
     * su motivo e stato sono in {@link RegoleAnnullamento}.
     *
     * @param attore identità verificata della sessione
     * @param soggetti titolare ed erogatore letti dalla richiesta salvata
     * @throws SecurityException se l'identità è incompleta o l'attore non ha relazione con la richiesta
     * @throws NullPointerException se i soggetti mancano per un attore con identità completa
     * @see RichiesteApi#annullaRichiesta
     */
    public static void verificaAnnullamento(IdentitaAutenticata attore, Soggetti soggetti) {
        richiedi(consente(PUO_ANNULLARE, attore, soggetti), "Annullamento non consentito");
    }

    /**
     * Dice se l'attore può vedere titolare e descrizione nel {@link RichiesteApi.RichiestaDettaglio}.
     * Li vedono solo il Banco e la OPT titolare: all'erogatore assegnato quei campi arrivano
     * {@code null}. Va usato dopo {@link #verificaConsultazione}.
     *
     * @param attore identità verificata della sessione
     * @param soggetti titolare ed erogatore letti dalla richiesta salvata
     * @return {@code true} per il Banco e per la OPT titolare
     * @throws NullPointerException se i soggetti mancano per un attore con identità completa
     */
    public static boolean vedeDatiDelTitolare(IdentitaAutenticata attore, Soggetti soggetti) {
        return consente(VEDE_DATI_DEL_TITOLARE, attore, soggetti);
    }

    // richiediRuolo esiste per scrivere una sola volta il controllo "il ruolo è tra quelli ammessi?"
    private static void richiediRuolo(IdentitaAutenticata attore, Set<RuoloUtente> ammessi, String messaggio) {
        richiedi(identitaCompleta(attore) && ammessi.contains(attore.ruolo()), messaggio);
    }

    /** Un'identità incompleta, senza account o senza ruolo, non ha mai accesso. */
    private static boolean identitaCompleta(IdentitaAutenticata attore) {
        return attore != null && attore.accountId() != null && attore.ruolo() != null;
    }

    /**
     * Valuta una politica solo su un'identità completa: un attore incompleto non ha mai accesso.
     * I soggetti sono una precondizione per un attore completo.
     */
    private static boolean consente(Predicate<Contesto> politica, IdentitaAutenticata attore, Soggetti soggetti) {
        if (!identitaCompleta(attore)) {
            return false;
        }
        Objects.requireNonNull(soggetti, "soggetti");
        return politica.test(new Contesto(attore, soggetti));
    }
}
