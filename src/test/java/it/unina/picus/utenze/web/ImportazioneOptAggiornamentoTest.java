package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi.AccountAmministrativo;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/** Caricamenti successivi: OPT già registrate, assenti dal file e revocate. */
@DisplayName("#24 CA-04/CA-05/CA-06 · OPT già registrate, assenti e revocate")
class ImportazioneOptAggiornamentoTest extends ImportazioneOptBlackBox {

    // ------------------------------------------------------------------ CA-04

    enum Modifica {
        NESSUNA, NOME, COMUNE, NOME_E_COMUNE;

        Riga applica(Riga riga) {
            Riga conNome = this == NOME || this == NOME_E_COMUNE ? riga.conNome("OPT rinominata") : riga;
            return this == COMUNE || this == NOME_E_COMUNE ? conNome.conComune(ALTRO_COMUNE) : conNome;
        }

        String esito() {
            return this == NESSUNA ? "INVARIATA" : "AGGIORNATA";
        }
    }

    static Stream<Arguments> statoPerModifica() {
        return Stream.of(StatoUtenza.ATTIVO, StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE)
                .flatMap(stato -> Stream.of(Modifica.values()).map(modifica -> Arguments.of(stato, modifica)));
    }

    @ParameterizedTest(name = "{0}, modifica {1}")
    @MethodSource("statoPerModifica")
    @DisplayName("CA-04 · OPT già registrata: cambiano solo nome e comune; password, stato, email e account invariati")
    void aggiornaSoloNomeEComune(StatoUtenza stato, Modifica modifica) throws Exception {
        scenario.banco();
        Account opt = optRegistrata();
        impostaStato(opt, stato);
        Riga riga = modifica.applica(rigaDi(opt));
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(opt.email()));

        Map<Integer, EsitoRiga> report = importa(csv(riga));

        assertEsito(modifica.esito(), report, 2);
        assertSoloNomeEComune(prima, opt, riga);
        assertInviiInvariati(invii);
        if (stato == StatoUtenza.ATTIVO) {
            assertTrue(utenze.verificaCredenziali(opt.email(), ScenarioAccessi.PASSWORD).isPresent(),
                    "la password precedente deve restare valida");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StatoUtenza.class, names = {"ATTIVO", "IN_ATTESA_DI_ATTIVAZIONE"})
    @DisplayName("CA-04 · email diversa: segnalata nel report e non applicata; nome e comune aggiornati")
    void emailDiversa(StatoUtenza stato) throws Exception {
        scenario.banco();
        Account opt = optRegistrata();
        impostaStato(opt, stato);
        Riga riga = Modifica.NOME_E_COMUNE.applica(rigaDi(opt)).conEmail(nuovaRiga().email());
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(opt.email(), riga.email()));

        Map<Integer, EsitoRiga> report = importa(csv(riga));

        assertSegnalata(report, 2);
        assertSoloNomeEComune(prima, opt, riga);
        assertInviiInvariati(invii);
    }

    @Test
    @DisplayName("CA-04 · la sola email diversa è segnalata e la OPT resta invariata")
    void solaEmailDiversa() throws Exception {
        scenario.banco();
        Account opt = optRegistrata();
        Riga riga = rigaDi(opt).conEmail(nuovaRiga().email());
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(opt.email(), riga.email()));

        Map<Integer, EsitoRiga> report = importa(csv(riga));

        assertSegnalata(report, 2);
        assertEquals(prima, istantanea());
        assertInviiInvariati(invii);
    }

    enum AltroAccount { OPT, OPERATORE }

    @ParameterizedTest(name = "email di un altro account {0}")
    @EnumSource(AltroAccount.class)
    @DisplayName("CA-04 · OPT registrata con l'email di un altro account: riga segnalata, email e account invariati")
    void emailDiUnAltroAccount(AltroAccount titolare) throws Exception {
        scenario.banco();
        Account opt = optRegistrata();
        Account altro = switch (titolare) {
            case OPT -> optRegistrata();
            case OPERATORE -> scenario.operatore(TipoErogatore.INTERNO_BANCO);
        };
        // nome e comune uguali: se la riga vada scartata o aggiornata non è definito dal criterio
        Riga riga = rigaDi(opt).conEmail(altro.email());
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(opt.email(), altro.email()));

        Map<Integer, EsitoRiga> report = importa(csv(riga));

        assertSegnalata(report, 2);
        assertEquals(prima, istantanea());
        assertInviiInvariati(invii);
    }

    @Test
    @DisplayName("CA-04 · il codice fiscale diverso di una OPT registrata non viene applicato")
    void codiceFiscaleDiverso() throws Exception {
        scenario.banco();
        Account opt = optRegistrata();
        Riga riga = Modifica.NOME_E_COMUNE.applica(rigaDi(opt)).conCodiceFiscale(codiceFiscaleSintetico());
        Istantanea prima = istantanea();

        importa(csv(riga));

        assertSoloNomeEComune(prima, opt, riga);
    }

    @Test
    @DisplayName("CA-04 · stesso file caricato due volte: nessun secondo account né nuovo invito, dati e stato invariati")
    void stessoFileDueVolte() throws Exception {
        scenario.banco();
        Riga prima = nuovaRiga();
        Riga seconda = nuovaRiga();
        String file = csv(prima, seconda);
        importa(file);
        attendiUnInvito(prima, seconda);
        Istantanea dopoIlPrimo = istantanea();
        Map<String, Integer> invii = invii(List.of(prima.email(), seconda.email()));

        Map<Integer, EsitoRiga> report = importa(file);

        assertEquals(2, report.size());
        assertEsito("INVARIATA", report, 2);
        assertEsito("INVARIATA", report, 3);
        assertEquals(dopoIlPrimo, istantanea());
        assertInviiInvariati(invii);
    }

    @Test
    @DisplayName("CA-04 · secondo file misto: aggiorna le registrate, crea la nuova e invita solo quella")
    void secondoFileMisto() throws Exception {
        scenario.banco();
        Riga registrata = nuovaRiga();
        Riga daAggiornare = nuovaRiga();
        importa(csv(registrata, daAggiornare));
        attendiUnInvito(registrata, daAggiornare);
        Riga aggiornata = daAggiornare.conNome("OPT rinominata").conComune(ALTRO_COMUNE);
        Riga nuova = nuovaRiga();
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(registrata.email(), daAggiornare.email()));

        Map<Integer, EsitoRiga> report = importa(csv(registrata, aggiornata, nuova));

        assertEsito("INVARIATA", report, 2);
        assertEsito("AGGIORNATA", report, 3);
        assertEsito("CREATA", report, 4);
        Istantanea dopo = istantanea();
        assertEquals(prima.account() + 1, dopo.account());
        assertEquals(prima.profiliOpt() + 1, dopo.profiliOpt());
        UUID invariata = optConCodice(registrata.codice()).id();
        assertEquals(prima.utenze().get(invariata), dopo.utenze().get(invariata));
        AccountAmministrativo modificata = optConCodice(aggiornata.codice());
        assertEquals(aggiornata.nome(), modificata.nomeVisualizzato());
        assertEquals(aggiornata.comune(), modificata.comuneCodice());
        assertEquals(StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE, modificata.stato());
        assertEquals(daAggiornare.email(), modificata.email());
        attendiUnInvito(nuova);
        assertInviiInvariati(invii);
    }

    /** Nella OPT cambiano solo nome e comune, presi dalla riga; tutto il resto è come prima. */
    private void assertSoloNomeEComune(Istantanea prima, Account opt, Riga riga) {
        Map<UUID, Utenza> attese = new HashMap<>(prima.utenze());
        attese.computeIfPresent(opt.id(), (id, u) -> new Utenza(new AccountAmministrativo(id, riga.nome(),
                u.dati().email(), u.dati().riferimento(), riga.comune(), u.dati().ruolo(), u.dati().stato()),
                u.codiceFiscale(), u.hashPassword()));
        assertEquals(new Istantanea(attese, prima.account(), prima.profiliOpt()), istantanea());
    }

    // ------------------------------------------------------------------ CA-05

    @ParameterizedTest(name = "{0}")
    @EnumSource(StatoUtenza.class)
    @DisplayName("CA-05 · OPT assente dal nuovo file: utenza e dati invariati, nessuna revoca né invio")
    void optAssenteDalFile(StatoUtenza stato) throws Exception {
        scenario.banco();
        Account assente = optRegistrata();
        impostaStato(assente, stato);
        Riga nuova = nuovaRiga();
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(assente.email()));

        assertEsito("CREATA", importa(csv(nuova)), 2);

        assertSoloNuove(prima, nuova);
        attendiUnInvito(nuova);
        assertInviiInvariati(invii);
    }

    @Test
    @DisplayName("CA-05 · OPT creata dal primo file e assente dal secondo: resta com'era")
    void assenteDalSecondoFile() throws Exception {
        scenario.banco();
        Riga assente = nuovaRiga();
        Riga presente = nuovaRiga();
        importa(csv(assente, presente));
        attendiUnInvito(assente, presente);
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(assente.email(), presente.email()));

        assertEsito("INVARIATA", importa(csv(presente)), 2);

        assertEquals(prima, istantanea());
        assertInviiInvariati(invii);
    }

    // ------------------------------------------------------------------ CA-06

    enum RigaRevocata { STESSI_DATI, NOME_E_COMUNE_DIVERSI, EMAIL_DIVERSA, TUTTO_DIVERSO }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RigaRevocata.class)
    @DisplayName("CA-06 · OPT revocata: non riattivata né sostituita, riga segnalata e le altre importate")
    void optRevocata(RigaRevocata caso) throws Exception {
        scenario.banco();
        Account revocata = optRegistrata();
        Riga originale = rigaDi(revocata);
        scenario.revoca(revocata);
        String nuovaEmail = nuovaRiga().email();
        Riga riga = switch (caso) {
            case STESSI_DATI -> originale;
            case NOME_E_COMUNE_DIVERSI -> Modifica.NOME_E_COMUNE.applica(originale);
            case EMAIL_DIVERSA -> originale.conEmail(nuovaEmail);
            case TUTTO_DIVERSO -> Modifica.NOME_E_COMUNE.applica(originale).conEmail(nuovaEmail)
                    .conCodiceFiscale(codiceFiscaleSintetico());
        };
        Riga valida = nuovaRiga();
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(revocata.email(), nuovaEmail));

        Map<Integer, EsitoRiga> report = importa(csv(riga, valida));

        assertScartata(report, 2);
        assertEsito("CREATA", report, 3);
        assertSoloNuove(prima, valida);
        assertEquals(StatoUtenza.REVOCATO, optConCodice(originale.codice()).stato());
        attendiUnInvito(valida);
        assertInviiInvariati(invii);
        assertTrue(utenze.verificaCredenziali(revocata.email(), ScenarioAccessi.PASSWORD).isEmpty(),
                "la OPT revocata non deve poter accedere");
    }

    @Test
    @DisplayName("CA-06 · OPT creata da un'importazione, poi revocata: il caricamento ripetuto non la riattiva")
    void revocataDopoImportazione() throws Exception {
        scenario.banco();
        Riga riga = nuovaRiga();
        importa(csv(riga));
        attendiUnInvito(riga);
        utenze.revocaUtenzaOpt(optConCodice(riga.codice()).id());
        Istantanea prima = istantanea();
        Map<String, Integer> invii = invii(List.of(riga.email()));

        assertScartata(importa(csv(riga)), 2);

        assertEquals(prima, istantanea());
        assertEquals(StatoUtenza.REVOCATO, optConCodice(riga.codice()).stato());
        assertInviiInvariati(invii);
    }
}
