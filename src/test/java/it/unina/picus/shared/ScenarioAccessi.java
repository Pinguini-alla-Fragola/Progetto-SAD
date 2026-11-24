package it.unina.picus.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.context.WebApplicationContext;

import it.unina.picus.catalogo.api.CatalogoApi;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.catalogo.api.RicercaErogatoriApi;
import it.unina.picus.comunicazioni.api.AvvisiOperativiApi;
import it.unina.picus.comunicazioni.api.ComunicazioniApi;
import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.DatiSchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.richieste.api.CoordinamentoRichiesteApi;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * Fixture dei test #28. Crea account sintetici e porta le pratiche negli stati richiesti con le capacità
 * pubbliche già consegnate ({@link Capacita}) e con gli endpoint HTTP reali dei contatti
 * ({@link ContattiDisponibilitaHttp}), come farebbero gli utenti.
 *
 * <p>Il database si usa solo per preparare ciò che nessuna capacità consegnata permette ancora di creare,
 * sempre con dati sintetici: account e profili, una misura dedicata al singolo test, l'offerta delle OPT
 * erogatrici, l'abilitazione degli operatori alla misura e la revoca di un account. La misura dedicata isola
 * i candidati del test: compaiono soltanto gli erogatori che il test ha abilitato.</p>
 *
 * <p>Se una capacità non è ancora consegnata il caso viene <em>sospeso</em>; se una capacità consegnata
 * manca, o se un erogatore abilitato dalla fixture non compare tra i candidati, il caso <em>fallisce</em>.</p>
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
    private final Map<UUID, Account> account = new HashMap<>();
    private Account banco;
    private Long misuraId;
    private ContattiDisponibilitaHttp contatti;

    /**
     * Crea la fixture di un singolo test.
     *
     * @param contesto contesto Spring da cui leggere le capacità disponibili
     */
    public ScenarioAccessi(ApplicationContext contesto) {
        this.contesto = contesto;
    }

    // ------------------------------------------------------------------ capacità

    /**
     * Restituisce l'implementazione della capacità. Se manca: il test fallisce quando la capacità è già
     * consegnata (regressione), altrimenti viene sospeso.
     *
     * @param <T> tipo del contratto
     * @param capacita capacità richiesta dal caso
     * @param tipo contratto atteso, uguale a {@link Capacita#contratto()}
     * @return implementazione presente nel contesto
     */
    public <T> T capacita(Capacita capacita, Class<T> tipo) {
        assertEquals(capacita.contratto(), tipo, "contratto non corrispondente alla capacità " + capacita);
        T bean = contesto.getBeanProvider(tipo).getIfAvailable();
        if (bean == null && capacita.consegnata()) {
            fail("Capacità " + capacita + " consegnata ma assente dal contesto (" + tipo.getSimpleName()
                    + "): regressione");
        }
        assumeTrue(bean != null, () -> "capacità " + capacita + " non ancora consegnata: caso sospeso");
        return bean;
    }

    /**
     * Invio, elenco proprio, coda del Banco e consultazione della richiesta.
     *
     * @return capacità consegnata
     */
    public InvioRichiesteApi invio() {
        return capacita(Capacita.INVIO_RICHIESTE, InvioRichiesteApi.class);
    }

    /**
     * Presa in carico, ricerca, contatto, assegnazione, conferma e rifiuto definitivo.
     *
     * @return capacità consegnata
     */
    public CoordinamentoRichiesteApi coordinamento() {
        return capacita(Capacita.COORDINAMENTO_RICHIESTE, CoordinamentoRichiesteApi.class);
    }

    /**
     * Misure e comuni del catalogo.
     *
     * @return capacità consegnata
     */
    public ConsultazioneCatalogoApi consultazioneCatalogo() {
        return capacita(Capacita.CONSULTAZIONE_CATALOGO, ConsultazioneCatalogoApi.class);
    }

    /**
     * Erogatori compatibili con misura e comune.
     *
     * @return capacità consegnata
     */
    public RicercaErogatoriApi ricercaErogatori() {
        return capacita(Capacita.RICERCA_EROGATORI, RicercaErogatoriApi.class);
    }

    /**
     * Avvisi operativi dell'account.
     *
     * @return capacità consegnata
     */
    public AvvisiOperativiApi avvisi() {
        return capacita(Capacita.AVVISI_OPERATIVI, AvvisiOperativiApi.class);
    }

    /**
     * Contratto completo di Richieste, per le sole operazioni non ancora consegnate come capacità
     * (annullamento, archivio del Banco, elenco delle assegnazioni).
     *
     * @return implementazione, oppure caso sospeso
     */
    public RichiesteApi richieste() {
        return capacita(Capacita.RICHIESTE_COMPLETE, RichiesteApi.class);
    }

    /**
     * Contratto completo del Catalogo (offerta delle OPT, catalogo completo del Banco).
     *
     * @return implementazione, oppure caso sospeso
     */
    public CatalogoApi catalogo() {
        return capacita(Capacita.CATALOGO_COMPLETO, CatalogoApi.class);
    }

    /**
     * Contratto del modulo Monitoraggio.
     *
     * @return implementazione, oppure caso sospeso
     */
    public MonitoraggioApi monitoraggio() {
        return capacita(Capacita.MONITORAGGIO, MonitoraggioApi.class);
    }

    /**
     * Registrazione e consultazione dei conteggi trimestrali delle OPT.
     *
     * @return implementazione consegnata
     */
    public ConteggiOptApi conteggiOpt() {
        return capacita(Capacita.CONTEGGI_OPT, ConteggiOptApi.class);
    }

    /**
     * Contratto del modulo Comunicazioni.
     *
     * @return implementazione, oppure caso sospeso
     */
    public ComunicazioniApi comunicazioni() {
        return capacita(Capacita.COMUNICAZIONI, ComunicazioniApi.class);
    }

    /**
     * Contratto del modulo Utenze.
     *
     * @return capacità consegnata
     */
    public UtenzeApi utenze() {
        return capacita(Capacita.UTENZE, UtenzeApi.class);
    }

    /**
     * Consultazione, risposta e storico dei contatti attraverso gli endpoint HTTP reali.
     *
     * @return adattatore HTTP dei contatti
     */
    public ContattiDisponibilitaHttp contatti() {
        if (contatti == null) {
            contatti = new ContattiDisponibilitaHttp(new ClientHttp((WebApplicationContext) contesto));
        }
        return contatti;
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
     * Account personale di un operatore del Banco della tipologia indicata, non abilitato alla misura.
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
     * Erogatore compatibile con la misura del test: per {@link TipoErogatore#OPT} una OPT limitrofa con
     * offerta, altrimenti un operatore del Banco della tipologia richiesta abilitato alla misura.
     *
     * @param tipo tipologia di erogatore
     * @return nuovo erogatore compatibile
     */
    public Account erogatore(TipoErogatore tipo) {
        if (tipo == TipoErogatore.OPT) {
            Account opt = opt(COMUNE_LIMITROFO);
            registraOfferta(opt, false);
            return opt;
        }
        Account operatore = operatore(tipo);
        abilitaOperatoreAllaMisura(operatore);
        return operatore;
    }

    /**
     * OPT limitrofa che offre la misura del test tramite un professionista esterno: per la Story resta
     * una OPT erogatrice e può annullare le richieste assegnate alla propria OPT.
     *
     * @return nuova OPT erogatrice tramite professionista
     */
    public Account erogatoreOptTramiteProfessionista() {
        Account opt = opt(COMUNE_LIMITROFO);
        registraOfferta(opt, true);
        return opt;
    }

    /**
     * Revoca l'account come farebbe la revoca del Banco (#26/#30).
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
        Account creato = new Account(identita.get(), email, nome);
        account.put(creato.id(), creato);
        return creato;
    }

    /** Preparazione dati: offerta della OPT per la misura del test (registrazione dell'offerta non consegnata). */
    private void registraOfferta(Account opt, boolean tramiteProfessionista) {
        jdbc().update("insert into catalogo_offerte_opt (opt_id, misura_id, tramite_professionista) values (?, ?, ?)",
                opt.id(), misura(), tramiteProfessionista);
    }

    /** Preparazione dati: abilitazione dell'operatore alla misura del test ({@code utenze_operatori_misure}, V9). */
    private void abilitaOperatoreAllaMisura(Account operatore) {
        jdbc().update("insert into utenze_operatori_misure (account_id, misura_id) values (?, ?)",
                operatore.id(), misura());
    }

    /** Rende compatibile con la misura del test un account già creato, OPT oppure operatore. */
    private void abilita(Account erogatore) {
        if (erogatore.ruolo() == RuoloUtente.OPT) {
            registraOfferta(erogatore, false);
        } else {
            abilitaOperatoreAllaMisura(erogatore);
        }
    }

    private JdbcTemplate jdbc() {
        return contesto.getBean(JdbcTemplate.class);
    }

    // ------------------------------------------------------------------ catalogo

    /**
     * Misura sintetica dedicata al test, attiva e non erogabile a distanza. Viene creata nel database perché la
     * gestione del catalogo non è ancora consegnata; la consultazione del catalogo deve vederla.
     *
     * @return identificativo della misura
     */
    public long misura() {
        if (misuraId == null) {
            String codice = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
            Long id = jdbc().queryForObject("""
                    insert into catalogo_misure (codice, nome, descrizione, attiva, erogabile_a_distanza)
                    values (?, ?, 'Misura sintetica dei test black box della #28', true, false)
                    returning id
                    """, Long.class, codice, "Misura di prova " + codice);
            assertNotNull(id, "misura di prova non creata");
            assertTrue(consultazioneCatalogo().cercaMisura(id).filter(CatalogoApi.Misura::attiva).isPresent(),
                    "il catalogo deve vedere la misura di prova attiva");
            misuraId = id;
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
        RichiestaSintesi sintesi = invio().invia(titolare.identita(), dati, UUID.randomUUID());
        assertEquals(StatoRichiesta.CREATA, sintesi.stato(), "una richiesta appena inviata è Creata");
        return sintesi.id();
    }

    /**
     * Richiesta Presa in carico dal Banco.
     *
     * @param titolare OPT che invia la richiesta
     * @return identificativo della richiesta
     */
    public UUID richiestaPresaInCarico(Account titolare) {
        UUID id = richiestaCreata(titolare);
        coordinamento().prendiInCarico(banco().identita(), id);
        return id;
    }

    /**
     * Interpella l'erogatore per la richiesta Presa in carico e restituisce il contatto in attesa.
     * Fallisce se un erogatore abilitato dalla fixture non compare tra i candidati: è una regressione.
     *
     * @param richiestaId richiesta Presa in carico
     * @param erogatore account da interpellare, abilitato alla misura del test
     * @return contatto di disponibilità in attesa
     */
    public ContattoDisponibilita interpella(UUID richiestaId, Account erogatore) {
        List<Candidato> candidati = coordinamento().cercaErogatoriOrdinati(banco().identita(), richiestaId);
        Optional<Candidato> scelto = candidati.stream()
                .filter(c -> c.erogatore().accountId().equals(erogatore.id()))
                .findFirst();
        if (scelto.isEmpty()) {
            fail("L'erogatore " + erogatore.ruolo() + " " + erogatore.id() + ", abilitato alla misura " + misura()
                    + ", non compare tra i candidati della richiesta " + richiestaId + ". Candidati: "
                    + candidati.stream().map(c -> c.erogatore().accountId() + "/" + c.erogatore().ambito()).toList());
        }
        return coordinamento().interpellaErogatore(banco().identita(), richiestaId, erogatore.id(),
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
     * Porta una richiesta Presa in carico a In programmazione con l'erogatore indicato: contatto, risposta
     * positiva dell'erogatore sull'endpoint reale e assegnazione del Banco.
     *
     * @param richiestaId richiesta Presa in carico
     * @param erogatore account che accetta e viene assegnato
     */
    public void assegna(UUID richiestaId, Account erogatore) {
        ContattoDisponibilita contatto = interpella(richiestaId, erogatore);
        contatti().rispondiDisponibilita(erogatore, contatto.id(), true);
        coordinamento().assegnaErogatore(banco().identita(), contatto.id());
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
        coordinamento().confermaAccordo(banco().identita(), id, ACCORDO);
        return id;
    }

    /**
     * Richiesta Rifiutata dal Banco. Prima del rifiuto ogni candidato compatibile viene interpellato e declina,
     * come richiede il coordinamento.
     *
     * @param titolare OPT che invia la richiesta
     * @return identificativo della richiesta
     */
    public UUID richiestaRifiutata(Account titolare) {
        UUID id = richiestaPresaInCarico(titolare);
        for (Candidato candidato : coordinamento().cercaErogatoriOrdinati(banco().identita(), id)) {
            Account erogatore = account.get(candidato.erogatore().accountId());
            assertNotNull(erogatore, "candidato non creato da questo test: la misura di prova non è isolata");
            ContattoDisponibilita contatto = coordinamento().interpellaErogatore(banco().identita(), id,
                    erogatore.id(), candidato.erogatore().ambito());
            contatti().rispondiDisponibilita(erogatore, contatto.id(), false);
        }
        coordinamento().rifiutaRichiesta(banco().identita(), id);
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
            case CONFERMATA -> coordinamento().confermaAccordo(banco().identita(), id, ACCORDO);
            case RIFIUTATA -> coordinamento().rifiutaRichiesta(banco().identita(), id);
            default -> throw new IllegalArgumentException("Nessun erogatore assegnato nello stato " + stato);
        }
        return id;
    }

    /**
     * Contatto in attesa intestato a un destinatario.
     *
     * @param richiestaId richiesta Presa in carico a cui appartiene il contatto
     * @param contattoId contatto in attesa intestato al destinatario
     * @param misuraId misura della richiesta
     * @param nomeMisura nome della misura registrato nel contatto
     */
    public record ContattoPreparato(UUID richiestaId, UUID contattoId, long misuraId, String nomeMisura) {}

    /**
     * Contatto {@code IN_ATTESA} intestato al destinatario, ottenuto con le operazioni degli utenti: invio della
     * OPT titolare, presa in carico e contatto del Banco. Il destinatario viene prima abilitato alla misura del
     * test, così compare tra i candidati.
     *
     * @param titolare OPT titolare della richiesta
     * @param destinatario account da interpellare, OPT oppure operatore
     * @return identificativi e dati minimi del contatto
     */
    public ContattoPreparato contattoInAttesa(Account titolare, Account destinatario) {
        abilita(destinatario);
        UUID richiestaId = richiestaPresaInCarico(titolare);
        ContattoDisponibilita contatto = interpella(richiestaId, destinatario);
        String nomeMisura = istantanea(richiestaId).richiesta().nomeMisura();
        return new ContattoPreparato(richiestaId, contatto.id(), misura(), nomeMisura);
    }

    /**
     * Esito corrente di un contatto letto dal Banco con la capacità di coordinamento.
     *
     * @param richiestaId richiesta del contatto
     * @param contattoId contatto da leggere
     * @return esito registrato
     */
    public EsitoContatto esitoPerIlBanco(UUID richiestaId, UUID contattoId) {
        return coordinamento().consultaContattoBanco(banco().identita(), richiestaId, contattoId).esito();
    }

    /**
     * Dettaglio completo della pratica letto dal Banco, usato per confrontare prima e dopo.
     *
     * @param richiestaId pratica da leggere
     * @return dettaglio visibile al Banco
     */
    public RichiestaDettaglio istantanea(UUID richiestaId) {
        return invio().consultaRichiesta(banco().identita(), richiestaId);
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
