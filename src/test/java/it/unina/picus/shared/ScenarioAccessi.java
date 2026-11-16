package it.unina.picus.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import it.unina.picus.catalogo.api.CatalogoApi;
import it.unina.picus.comunicazioni.api.ComunicazioniApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.DatiSchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * Fixture dei test #28. Crea account sintetici e porta le pratiche negli stati richiesti usando
 * soltanto le operazioni pubbliche dei moduli, come farebbero gli utenti. L'unica eccezione è
 * {@link #contattoInAttesa}, dichiarata come preparazione dati.
 *
 * <p>Se un modulo non è ancora presente nel contesto Spring il test viene <em>sospeso</em>
 * (assumption) e non fallito: la suite può stare su {@code main} prima dell'integrazione e
 * si attiva da sola quando l'implementazione arriva.</p>
 */
public final class ScenarioAccessi {

    /** Password sintetica, conforme alla policy proposta (almeno 12 caratteri). */
    public static final String PASSWORD = "Prova-Accessi-28!";
    /** Comune delle richieste di prova. */
    public static final String COMUNE_RICHIESTA = "napoli";
    /** Comune limitrofo usato per le OPT erogatrici. */
    public static final String COMUNE_LIMITROFO = "pozzuoli";
    /** Descrizione sintetica, senza identificativi di assistiti, riservata a titolare e Banco. */
    public static final String DESCRIZIONE = "Descrizione sintetica di prova #28";
    /** Trimestre usato per schede e conteggi. */
    public static final Trimestre TRIMESTRE = new Trimestre(2026, 3);
    /** Accordo in presenza con referente sintetico. */
    public static final DettagliAccordo ACCORDO = new AccordoInPresenza(
            Instant.parse("2026-12-01T09:30:00Z"), "Sede sintetica, Napoli", "Nome", "Referente", "0810000000");

    /**
     * Account di prova con l'identità restituita dal server.
     *
     * @param identita identità verificata da Utenze, mai costruita dal test
     * @param email indirizzo sintetico usato per l'accesso
     * @param nome nome visualizzato sintetico
     */
    public record Account(IdentitaAutenticata identita, String email, String nome) {
        /**
         * Identificativo dell'account.
         *
         * @return identificativo assegnato dal server
         */
        public UUID id() {
            return identita.accountId();
        }

        /**
         * Ruolo persistito dell'account.
         *
         * @return ruolo confermato dal server
         */
        public RuoloUtente ruolo() {
            return identita.ruolo();
        }
    }

    private final ApplicationContext contesto;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private Account banco;
    private Long misuraId;

    /**
     * Crea la fixture di un singolo test.
     *
     * @param contesto contesto Spring da cui leggere i moduli disponibili
     */
    public ScenarioAccessi(ApplicationContext contesto) {
        this.contesto = contesto;
    }

    // ------------------------------------------------------------------ moduli

    /**
     * Restituisce il bean del modulo, oppure sospende il test se non è ancora integrato.
     *
     * @param <T> tipo del contratto richiesto
     * @param tipo contratto del modulo
     * @return implementazione presente nel contesto
     */
    public <T> T modulo(Class<T> tipo) {
        T bean = contesto.getBeanProvider(tipo).getIfAvailable();
        assumeTrue(bean != null,
                () -> tipo.getSimpleName() + " non è ancora disponibile nel contesto: test #28 sospeso");
        return bean;
    }

    /**
     * Contratto del modulo Richieste.
     *
     * @return implementazione di {@link RichiesteApi}, oppure test sospeso
     */
    public RichiesteApi richieste() {
        return modulo(RichiesteApi.class);
    }

    /**
     * Contratto del modulo Catalogo.
     *
     * @return implementazione di {@link CatalogoApi}, oppure test sospeso
     */
    public CatalogoApi catalogo() {
        return modulo(CatalogoApi.class);
    }

    /**
     * Contratto del modulo Monitoraggio.
     *
     * @return implementazione di {@link MonitoraggioApi}, oppure test sospeso
     */
    public MonitoraggioApi monitoraggio() {
        return modulo(MonitoraggioApi.class);
    }

    /**
     * Contratto del modulo Comunicazioni.
     *
     * @return implementazione di {@link ComunicazioniApi}, oppure test sospeso
     */
    public ComunicazioniApi comunicazioni() {
        return modulo(ComunicazioniApi.class);
    }

    /**
     * Contratto del modulo Utenze.
     *
     * @return implementazione di {@link UtenzeApi}, oppure test sospeso
     */
    public UtenzeApi utenze() {
        return modulo(UtenzeApi.class);
    }

    // ------------------------------------------------------------------ account

    /**
     * Banco di coordinamento del test, creato alla prima richiesta.
     *
     * @return account Banco condiviso all'interno del test
     */
    public Account banco() {
        if (banco == null) {
            banco = nuovoAccount(RuoloUtente.BANCO, "Banco di prova");
        }
        return banco;
    }

    /**
     * Account organizzativo di una OPT nel comune indicato, senza offerta.
     *
     * @param comune codice del comune della OPT
     * @return nuova OPT attiva
     */
    public Account opt(String comune) {
        Account opt = nuovoAccount(RuoloUtente.OPT, "OPT di prova");
        jdbc().update("insert into utenze_profili_opt (account_id, codice_struttura, comune_codice) values (?, ?, ?)",
                opt.id(), "T28-" + opt.id().toString().substring(0, 8), comune);
        return opt;
    }

    /**
     * OPT richiedente nel comune della richiesta.
     *
     * @return nuova OPT attiva in {@link #COMUNE_RICHIESTA}
     */
    public Account optRichiedente() {
        return opt(COMUNE_RICHIESTA);
    }

    /**
     * Account personale di un operatore del Banco della tipologia indicata.
     *
     * @param tipologia {@code INTERNO_BANCO} o {@code ESTERNO_PAGATO_BANCO}
     * @return nuovo operatore attivo
     */
    public Account operatore(TipoErogatore tipologia) {
        assumeFalse(tipologia == TipoErogatore.OPT, "una OPT non ha un account personale Operatore");
        Account operatore = nuovoAccount(RuoloUtente.OPERATORE, "Operatore di prova");
        jdbc().update("insert into utenze_profili_operatore (account_id, tipologia, comune_codice) values (?, ?, ?)",
                operatore.id(), tipologia.name(), COMUNE_RICHIESTA);
        return operatore;
    }

    /**
     * Erogatore compatibile con la misura di prova: per {@link TipoErogatore#OPT} una OPT limitrofa
     * con offerta registrata, altrimenti un operatore del Banco della tipologia richiesta.
     *
     * @param tipo tipologia di erogatore
     * @return nuovo erogatore compatibile
     */
    public Account erogatore(TipoErogatore tipo) {
        if (tipo == TipoErogatore.OPT) {
            Account opt = opt(COMUNE_LIMITROFO);
            catalogo().registraOffertaOpt(opt.identita(), misura(), false);
            return opt;
        }
        Account operatore = operatore(tipo);
        abilitaOperatoreAllaMisura(operatore, misura());
        return operatore;
    }

    /**
     * OPT limitrofa che offre la misura di prova tramite un professionista esterno: per la Story resta
     * una OPT erogatrice e può annullare le richieste assegnate alla propria OPT.
     *
     * @return nuova OPT erogatrice tramite professionista
     */
    public Account erogatoreOptTramiteProfessionista() {
        Account opt = opt(COMUNE_LIMITROFO);
        catalogo().registraOffertaOpt(opt.identita(), misura(), true);
        return opt;
    }

    /**
     * Sospende la sessione dell'account come farebbe una revoca (#26/#30).
     *
     * @param account account da revocare
     */
    public void revoca(Account account) {
        jdbc().update("update utenze_account set stato = 'REVOCATO' where id = ?", account.id());
    }

    private Account nuovoAccount(RuoloUtente ruolo, String nome) {
        UUID id = UUID.randomUUID();
        String email = ruolo.name().toLowerCase() + "-" + id + "@picus.test";
        jdbc().update("insert into utenze_account (id, email, password_hash, nome, ruolo, stato) "
                + "values (?, ?, ?, ?, ?, 'ATTIVO')", id, email, encoder.encode(PASSWORD), nome, ruolo.name());

        // L'identità è sempre quella verificata dal server, mai costruita dal test.
        Optional<IdentitaAutenticata> identita = utenze().verificaCredenziali(email, PASSWORD);
        assertNotNull(identita.orElse(null), "le credenziali di un account attivo devono essere accettate");
        assertEquals(ruolo, identita.get().ruolo(), "il ruolo deve essere quello persistito");
        return new Account(identita.get(), email, nome);
    }

    /**
     * Punto di estensione: il profilo misure degli operatori non ha ancora uno schema né un contratto.
     * Quando esisterà, collegare qui l'operatore alla misura. Fino ad allora, se Catalogo non propone
     * l'operatore, i test che lo interpellano vengono sospesi da {@link #interpella}.
     */
    private void abilitaOperatoreAllaMisura(Account operatore, long misura) {
        // intenzionalmente vuoto: dipende dal futuro schema dei profili operatore
    }

    private JdbcTemplate jdbc() {
        return modulo(JdbcTemplate.class);
    }

    // ------------------------------------------------------------------ catalogo

    /**
     * Prima misura attiva del catalogo, usata da tutte le richieste di prova.
     *
     * @return identificativo della misura
     */
    public long misura() {
        if (misuraId == null) {
            List<CatalogoApi.Misura> misure = catalogo().elencoMisureAttive();
            assumeFalse(misure.isEmpty(), "nessuna misura attiva nel catalogo");
            misuraId = misure.getFirst().id();
        }
        return misuraId;
    }

    // ------------------------------------------------------------------ pratiche

    /**
     * Richiesta Creata dalla OPT titolare.
     *
     * @param titolare OPT che invia la richiesta
     * @return identificativo della richiesta
     */
    public UUID richiestaCreata(Account titolare) {
        var dati = new DatiInvio(misura(), TipoDestinatario.PERSONA, COMUNE_RICHIESTA, DESCRIZIONE);
        return richieste().invia(titolare.identita(), dati, UUID.randomUUID()).id();
    }

    /**
     * Richiesta Presa in carico dal Banco.
     *
     * @param titolare OPT che invia la richiesta
     * @return identificativo della richiesta
     */
    public UUID richiestaPresaInCarico(Account titolare) {
        UUID id = richiestaCreata(titolare);
        richieste().prendiInCarico(banco().identita(), id);
        return id;
    }

    /**
     * Interpella l'erogatore per la richiesta Presa in carico e restituisce il contatto in attesa.
     * Sospende il test se l'erogatore non è tra i candidati proposti.
     *
     * @param richiestaId richiesta Presa in carico
     * @param erogatore account da interpellare
     * @return contatto di disponibilità in attesa
     */
    public ContattoDisponibilita interpella(UUID richiestaId, Account erogatore) {
        List<Candidato> candidati = richieste().cercaErogatoriOrdinati(banco().identita(), richiestaId);
        Optional<Candidato> scelto = candidati.stream()
                .filter(c -> c.erogatore().accountId().equals(erogatore.id()))
                .findFirst();
        assumeTrue(scelto.isPresent(),
                "l'erogatore di prova non è tra i candidati: completare il profilo misure dell'operatore");
        return richieste().interpellaErogatore(banco().identita(), richiestaId, erogatore.id(),
                scelto.get().erogatore().ambito());
    }

    /**
     * Richiesta Presa in carico con un contatto in attesa verso l'erogatore.
     *
     * @param titolare OPT che invia la richiesta
     * @param erogatore account da interpellare
     * @return contatto di disponibilità in attesa
     */
    public ContattoDisponibilita richiestaConContatto(Account titolare, Account erogatore) {
        return interpella(richiestaPresaInCarico(titolare), erogatore);
    }

    /**
     * Porta una richiesta Presa in carico a In programmazione con l'erogatore indicato.
     *
     * @param richiestaId richiesta Presa in carico
     * @param erogatore account che accetta e viene assegnato
     */
    public void assegna(UUID richiestaId, Account erogatore) {
        ContattoDisponibilita contatto = interpella(richiestaId, erogatore);
        richieste().rispondiDisponibilita(erogatore.identita(), contatto.id(), true);
        richieste().assegnaErogatore(banco().identita(), contatto.id());
    }

    /**
     * Richiesta In programmazione assegnata all'erogatore.
     *
     * @param titolare OPT che invia la richiesta
     * @param erogatore account assegnato
     * @return identificativo della richiesta
     */
    public UUID richiestaInProgrammazione(Account titolare, Account erogatore) {
        UUID id = richiestaPresaInCarico(titolare);
        assegna(id, erogatore);
        return id;
    }

    /**
     * Richiesta Confermata con l'erogatore assegnato e l'accordo di prova.
     *
     * @param titolare OPT che invia la richiesta
     * @param erogatore account assegnato
     * @return identificativo della richiesta
     */
    public UUID richiestaConfermata(Account titolare, Account erogatore) {
        UUID id = richiestaInProgrammazione(titolare, erogatore);
        richieste().confermaAccordo(banco().identita(), id, ACCORDO);
        return id;
    }

    /**
     * Richiesta Rifiutata dal Banco.
     *
     * @param titolare OPT che invia la richiesta
     * @return identificativo della richiesta
     */
    public UUID richiestaRifiutata(Account titolare) {
        UUID id = richiestaPresaInCarico(titolare);
        richieste().rifiutaRichiesta(banco().identita(), id);
        return id;
    }

    /**
     * Richiesta nello stato indicato, con un erogatore OPT assegnato quando lo stato lo richiede.
     *
     * @param stato stato da raggiungere
     * @param titolare OPT che invia la richiesta
     * @return identificativo della richiesta
     */
    public UUID richiestaInStato(StatoRichiesta stato, Account titolare) {
        return switch (stato) {
            case CREATA -> richiestaCreata(titolare);
            case PRESA_IN_CARICO -> richiestaPresaInCarico(titolare);
            case IN_PROGRAMMAZIONE -> richiestaInProgrammazione(titolare, erogatore(TipoErogatore.OPT));
            case CONFERMATA -> richiestaConfermata(titolare, erogatore(TipoErogatore.OPT));
            case RIFIUTATA -> richiestaRifiutata(titolare);
            case ANNULLATA -> {
                UUID id = richiestaCreata(titolare);
                richieste().annullaRichiesta(titolare.identita(), id, null);
                yield id;
            }
        };
    }

    /**
     * Richiesta con l'erogatore indicato già assegnato: In programmazione, Confermata oppure
     * Rifiutata dal Banco dopo l'assegnazione.
     *
     * @param stato {@code IN_PROGRAMMAZIONE}, {@code CONFERMATA} o {@code RIFIUTATA}
     * @param titolare OPT che invia la richiesta
     * @param assegnato erogatore da assegnare
     * @return identificativo della richiesta
     */
    public UUID richiestaAssegnataInStato(StatoRichiesta stato, Account titolare, Account assegnato) {
        UUID id = richiestaInProgrammazione(titolare, assegnato);
        switch (stato) {
            case IN_PROGRAMMAZIONE -> { }
            case CONFERMATA -> richieste().confermaAccordo(banco().identita(), id, ACCORDO);
            case RIFIUTATA -> richieste().rifiutaRichiesta(banco().identita(), id);
            default -> throw new IllegalArgumentException("Nessun erogatore assegnato nello stato " + stato);
        }
        return id;
    }

    /**
     * Contatto di disponibilità preparato nel database.
     *
     * @param richiestaId richiesta Presa in carico a cui appartiene il contatto
     * @param contattoId contatto in attesa intestato al destinatario
     * @param misuraId misura della richiesta
     * @param nomeMisura nome della misura registrato nel contatto
     */
    public record ContattoPreparato(UUID richiestaId, UUID contattoId, long misuraId, String nomeMisura) {}

    /**
     * <b>Preparazione dati, non verifica.</b> Inserisce una richiesta Presa in carico della OPT titolare e
     * un contatto {@code IN_ATTESA} intestato al destinatario, con la stessa fixture SQL di
     * {@code RispostaDisponibilitaPostgresqlTest} (#110).
     *
     * <p>Serve ai test HTTP sui contatti, già esposti, finché le capacità di invio e coordinamento
     * (#107) non permettono di arrivare allo stesso stato con le operazioni degli utenti. Usa la misura
     * {@code M01} e il comune {@link #COMUNE_RICHIESTA}; la descrizione è {@link #DESCRIZIONE}, così i
     * test possono verificare che non arrivi a chi non deve vederla.</p>
     *
     * @param titolare OPT titolare della richiesta
     * @param destinatario account interpellato
     * @return identificativi e dati minimi del contatto preparato
     */
    public ContattoPreparato contattoInAttesa(Account titolare, Account destinatario) {
        Map<String, Object> misura = jdbc().queryForMap("select id, nome from catalogo_misure where codice = 'M01'");
        long misuraDelContatto = ((Number) misura.get("id")).longValue();
        String nomeMisura = (String) misura.get("nome");
        UUID richiestaId = UUID.randomUUID();
        jdbc().update("""
                insert into richieste (id, titolare_id, misura_id, comune_codice, tipo_destinatario, description,
                                       inviata_il, stato)
                values (?, ?, ?, ?, 'PERSONA', ?, now(), 'PRESA_IN_CARICO')
                """, richiestaId, titolare.id(), misuraDelContatto, COMUNE_RICHIESTA, DESCRIZIONE);
        UUID contattoId = UUID.randomUUID();
        jdbc().update("""
                insert into richieste_contatti_disponibilita (id, richiesta_id, erogatore_id, ambito, nome_misura,
                                                              inviato_il)
                values (?, ?, ?, 'STESSO_COMUNE', ?, now())
                """, contattoId, richiestaId, destinatario.id(), nomeMisura);
        return new ContattoPreparato(richiestaId, contattoId, misuraDelContatto, nomeMisura);
    }

    /**
     * Dettaglio completo della pratica letto dal Banco, usato per confrontare prima e dopo.
     *
     * @param richiestaId pratica da leggere
     * @return dettaglio visibile al Banco
     */
    public RichiestaDettaglio istantanea(UUID richiestaId) {
        return richieste().consultaRichiesta(banco().identita(), richiestaId);
    }

    // ------------------------------------------------------------------ monitoraggio

    /**
     * Dati sintetici di una scheda personale; nessun dato reale.
     *
     * @param nome nome sintetico
     * @param codiceFiscale codice fiscale sintetico
     * @return dati della scheda
     */
    public static DatiSchedaOperatore datiScheda(String nome, String codiceFiscale) {
        return new DatiSchedaOperatore(nome, "Prova", "Napoli", LocalDate.of(1980, 1, 1), codiceFiscale,
                "Educatore", "Collaborazione", "Livello prova", new BigDecimal("10"), new BigDecimal("20.00"),
                Map.of());
    }

    /**
     * Invia la scheda dell'operatore per il trimestre di prova.
     *
     * @param operatore operatore che invia la propria scheda
     * @return identificativo della scheda
     */
    public UUID schedaInviata(Account operatore) {
        return monitoraggio().inviaSchedaOperatore(operatore.identita(), TRIMESTRE,
                datiScheda("Operatore", "PRVOPR80A01F839A"));
    }
}
