package it.unina.picus.catalogo.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.interno.dominio.CodiceMisuraDuplicatoException;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.richieste.interno.applicazione.ServizioInvioRichieste;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Comandi concorrenti sul catalogo su PostgreSQL reale (#165, US #46 e #164): il lock pessimistico
 * della misura e il vincolo di unicità del codice impediscono duplicati e stati incoerenti, e una
 * disattivazione concorrente non lascia una nuova richiesta o una nuova offerta su una misura che
 * non era più selezionabile. Ogni comando gira in un thread e in una transazione propri.
 *
 * <p>Ogni test ricava dall'esito dei comandi quale ha vinto e lo confronta esattamente con il dato
 * salvato. È ripetuto più volte perché l'interleaving dei thread cambia da un'esecuzione all'altra.
 * Le misure create sono eliminate prima e dopo ogni ripetizione: {@link DatiSintetici#svuota()} da
 * sola le lascerebbe nel database condiviso.</p>
 */
@IntegrazionePostgresql
@DisplayName("#165 Catalogo con comandi concorrenti su PostgreSQL")
class ServizioGestioneCatalogoConcorrenzaTest {

    private static final List<String> CODICI_INIZIALI = List.of(
            "M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10", "M11", "M12");

    @Autowired
    private ServizioGestioneCatalogo gestione;

    @Autowired
    private ServizioOffertaOpt offerta;

    @Autowired
    private ServizioInvioRichieste invio;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiSintetici dati;
    private IdentitaAutenticata banco;
    private IdentitaAutenticata opt;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        ripristinaCatalogo();
        banco = new IdentitaAutenticata(dati.banco("Banco di prova concorrenza"), RuoloUtente.BANCO);
        opt = new IdentitaAutenticata(dati.opt("OPT di prova concorrenza", "napoli"), RuoloUtente.OPT);
    }

    @AfterEach
    void ripulisci() {
        ripristinaCatalogo();
    }

    @RepeatedTest(5)
    @DisplayName("Due creazioni simultanee con lo stesso codice: una sola misura, l'altra riceve il conflitto sul codice")
    void creazioniConLoStessoCodice() throws Exception {
        List<Object> esiti = inParallelo(List.of(
                () -> gestione.creaMisura(banco, new DatiMisura("T01", "Nome alfa", "Descrizione", false)),
                () -> gestione.creaMisura(banco, new DatiMisura("T01", "Nome beta", "Descrizione", true))));

        List<Misura> create = esiti.stream().filter(Misura.class::isInstance).map(Misura.class::cast).toList();
        assertThat(create).hasSize(1);
        assertThat(esiti).filteredOn(e -> !(e instanceof Misura))
                .singleElement()
                .isExactlyInstanceOf(CodiceMisuraDuplicatoException.class);

        Misura vincitrice = create.get(0);
        assertThat(jdbc.queryForList("select id from catalogo_misure where codice = 'T01'", Long.class))
                .containsExactly(vincitrice.id());
        assertThat(rigaMisura(vincitrice.id())).isEqualTo("T01|" + vincitrice.dati().nome() + "|Descrizione|"
                + vincitrice.dati().erogabileADistanza() + "|true");
    }

    @RepeatedTest(5)
    @DisplayName("Modifica e disattivazione simultanee: lo stato salvato è quello del comando concluso per ultimo")
    void modificaEDisattivazione() throws Exception {
        long id = gestione.creaMisura(banco, new DatiMisura("T01", "Nome iniziale", "Descrizione", false)).id();

        List<Object> esiti = inParallelo(List.of(
                () -> gestione.aggiornaMisura(banco, id, new DatiMisura("T01", "Nome corretto", "Descrizione", true),
                        true),
                () -> gestione.disattivaMisura(banco, id)));

        assertThat(esiti).allSatisfy(e -> assertThat(e).isInstanceOf(Misura.class));
        Misura modificata = (Misura) esiti.get(0);
        Misura disattivata = (Misura) esiti.get(1);
        assertThat(modificata.attiva()).isTrue();
        assertThat(disattivata.attiva()).isFalse();

        // Se la disattivazione ha già visto il nuovo nome, è stata eseguita dopo la modifica e ha l'ultima parola.
        boolean disattivazioneUltima = "Nome corretto".equals(disattivata.dati().nome());
        if (disattivazioneUltima) {
            assertThat(disattivata.dati().erogabileADistanza()).isTrue();
            assertThat(rigaMisura(id)).isEqualTo("T01|Nome corretto|Descrizione|true|false");
        } else {
            assertThat(disattivata.dati().nome()).isEqualTo("Nome iniziale");
            assertThat(rigaMisura(id)).isEqualTo("T01|Nome corretto|Descrizione|true|true");
        }
    }

    @RepeatedTest(5)
    @DisplayName("Invio e disattivazione simultanei: la richiesta è registrata solo se la misura era ancora attiva")
    void invioEDisattivazione() throws Exception {
        long id = gestione.creaMisura(banco, new DatiMisura("T01", "Nome", "Descrizione", false)).id();
        DatiInvio datiInvio = new DatiInvio(id, TipoDestinatario.PERSONA, "napoli", null);

        List<Object> esiti = inParallelo(List.of(
                () -> invio.invia(opt, datiInvio, null),
                () -> gestione.disattivaMisura(banco, id)));

        assertThat(esiti.get(1)).isInstanceOf(Misura.class);
        assertThat(((Misura) esiti.get(1)).attiva()).isFalse();
        assertThat(rigaMisura(id)).endsWith("|false");

        Object esitoInvio = esiti.get(0);
        if (esitoInvio instanceof RichiestaSintesi registrata) {
            assertThat(registrata.misuraId()).isEqualTo(id);
            assertThat(jdbc.queryForList("select id from richieste where titolare_id = ?", UUID.class, opt.accountId()))
                    .containsExactly(registrata.id());
        } else {
            assertThat(esitoInvio).isExactlyInstanceOf(DatiRichiestaNonValidiException.class);
            assertThat(((DatiRichiestaNonValidiException) esitoInvio).campi()).containsOnlyKeys("misuraId");
            assertThat(richiesteDellaOpt()).isZero();
        }
    }

    @RepeatedTest(5)
    @DisplayName("Offerta e disattivazione simultanee: la dichiarazione è salvata solo se la misura era ancora attiva")
    void offertaEDisattivazione() throws Exception {
        long id = gestione.creaMisura(banco, new DatiMisura("T01", "Nome", "Descrizione", false)).id();

        List<Object> esiti = inParallelo(List.of(
                () -> {
                    offerta.registraOffertaOpt(opt, id, true);
                    return "registrata";
                },
                () -> gestione.disattivaMisura(banco, id)));

        assertThat(esiti.get(1)).isInstanceOf(Misura.class);
        assertThat(rigaMisura(id)).endsWith("|false");

        List<String> righe = jdbc.query("""
                select misura_id, tramite_professionista, attiva from catalogo_offerte_opt where opt_id = ?
                """, (rs, n) -> rs.getLong(1) + ":" + rs.getBoolean(2) + ":" + rs.getBoolean(3), opt.accountId());
        if ("registrata".equals(esiti.get(0))) {
            assertThat(righe).containsExactly(id + ":true:true");
        } else {
            assertThat(esiti.get(0)).isExactlyInstanceOf(MisuraNonOffribileException.class);
            assertThat(righe).isEmpty();
        }
    }

    /**
     * Avvia i comandi insieme, ciascuno nel proprio thread, e restituisce per ognuno il risultato
     * oppure l'eccezione applicativa, nello stesso ordine della lista.
     */
    private static List<Object> inParallelo(List<Callable<Object>> comandi) throws Exception {
        var barriera = new CyclicBarrier(comandi.size());
        ExecutorService pool = Executors.newFixedThreadPool(comandi.size());
        try {
            List<Future<Object>> futuri = new ArrayList<>();
            for (Callable<Object> comando : comandi) {
                futuri.add(pool.submit(() -> {
                    barriera.await(30, TimeUnit.SECONDS);
                    try {
                        return comando.call();
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            List<Object> esiti = new ArrayList<>();
            for (Future<Object> futuro : futuri) {
                esiti.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return esiti;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Riga della misura come {@code codice|nome|descrizione|erogabileADistanza|attiva}. */
    private String rigaMisura(long id) {
        return jdbc.queryForObject("""
                select codice, nome, descrizione, erogabile_a_distanza, attiva from catalogo_misure where id = ?
                """, (rs, n) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|"
                + rs.getBoolean(4) + "|" + rs.getBoolean(5), id);
    }

    private int richiesteDellaOpt() {
        return jdbc.queryForObject("select count(*) from richieste where titolare_id = ?", Integer.class,
                opt.accountId());
    }

    /** Svuota i dati dinamici e riporta il catalogo alle dodici misure delle migrazioni. */
    private void ripristinaCatalogo() {
        dati.svuota();
        jdbc.update("delete from catalogo_misure where codice not in ("
                + String.join(",", CODICI_INIZIALI.stream().map(c -> "'" + c + "'").toList()) + ")");
    }
}
