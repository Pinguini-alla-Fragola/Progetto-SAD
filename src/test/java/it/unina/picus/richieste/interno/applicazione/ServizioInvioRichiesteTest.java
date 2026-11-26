package it.unina.picus.richieste.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.unina.picus.catalogo.api.CatalogoApi.Comune;
import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.catalogo.api.SelezioneMisureApi;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import it.unina.picus.richieste.interno.dominio.RichiestaDiMisura;
import it.unina.picus.richieste.interno.dominio.RichiestaRepository;
import it.unina.picus.shared.AccessoNegatoException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Casi d'uso di invio e consultazione (#48) con porte simulate: titolare ricavata dalla
 * sessione, stato Creata, validazione senza scritture e visibilità per ruolo e relazione.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Servizio di invio e consultazione delle richieste")
@Tag("unit")
class ServizioInvioRichiesteTest {

    private static final Instant ORA = Instant.parse("2026-11-17T10:00:00Z");
    private static final long MISURA = 1L;
    private static final String NOME_MISURA = "Accoglienza e ascolto";

    @Mock
    private RichiestaRepository repository;
    @Mock
    private ConsultazioneCatalogoApi catalogo;
    @Mock
    private SelezioneMisureApi selezioneMisure;

    private final IdentitaAutenticata opt = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPT);
    private final IdentitaAutenticata banco = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);
    private ServizioInvioRichieste servizio;

    @BeforeEach
    void crea() {
        servizio = new ServizioInvioRichieste(repository, catalogo, selezioneMisure, Clock.fixed(ORA, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("Invio")
    class Invio {

        @Test
        @DisplayName("Registra la richiesta Creata con titolare della sessione, istante del server e dati normalizzati")
        void invioValido() {
            catalogoValido();
            when(repository.registra(any())).thenAnswer(invocazione -> invocazione.getArgument(0));

            var sintesi = servizio.invia(opt, new DatiInvio(MISURA, TipoDestinatario.PERSONA, "  napoli ",
                    "  Bisogno generico  "), null);

            var registrata = ArgumentCaptor.forClass(RichiestaDiMisura.class);
            verify(repository).registra(registrata.capture());
            var richiesta = registrata.getValue();
            assertThat(richiesta.titolareId()).isEqualTo(opt.accountId());
            assertThat(richiesta.stato()).isEqualTo(StatoRichiesta.CREATA);
            assertThat(richiesta.inviataIl()).isEqualTo(ORA);
            assertThat(richiesta.comuneCodice()).isEqualTo("napoli");
            assertThat(richiesta.description()).isEqualTo("Bisogno generico");
            assertThat(richiesta.erogatoreId()).isNull();
            assertThat(sintesi.id()).isEqualTo(richiesta.id());
            assertThat(sintesi.stato()).isEqualTo(StatoRichiesta.CREATA);
            assertThat(sintesi.nomeMisura()).isEqualTo(NOME_MISURA);
        }

        @Test
        @DisplayName("Una description di soli spazi viene salvata come assente")
        void descriptionVuota() {
            catalogoValido();
            when(repository.registra(any())).thenAnswer(invocazione -> invocazione.getArgument(0));

            servizio.invia(opt, new DatiInvio(MISURA, TipoDestinatario.NUCLEO, "napoli", "   "), null);

            var registrata = ArgumentCaptor.forClass(RichiestaDiMisura.class);
            verify(repository).registra(registrata.capture());
            assertThat(registrata.getValue().description()).isNull();
        }

        static Stream<IdentitaAutenticata> nonOpt() {
            return Stream.of(null,
                    new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO),
                    new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPERATORE),
                    new IdentitaAutenticata(null, RuoloUtente.OPT),
                    new IdentitaAutenticata(UUID.randomUUID(), null));
        }

        @ParameterizedTest
        @MethodSource("nonOpt")
        @DisplayName("Solo una OPT con identità completa può inviare")
        void soloOpt(IdentitaAutenticata attore) {
            assertThatThrownBy(() -> servizio.invia(attore,
                    new DatiInvio(MISURA, TipoDestinatario.PERSONA, "napoli", null), null))
                    .isInstanceOf(AccessoNegatoException.class);
            verifyNoInteractions(repository, catalogo);
        }

        static Stream<Arguments> datiNonValidi() {
            return Stream.of(
                    Arguments.of(null, "misuraId"),
                    Arguments.of(new DatiInvio(0L, TipoDestinatario.PERSONA, "napoli", null), "misuraId"),
                    Arguments.of(new DatiInvio(-3L, TipoDestinatario.PERSONA, "napoli", null), "misuraId"),
                    Arguments.of(new DatiInvio(MISURA, null, "napoli", null), "tipoDestinatario"),
                    Arguments.of(new DatiInvio(MISURA, TipoDestinatario.PERSONA, null, null), "comuneCodice"),
                    Arguments.of(new DatiInvio(MISURA, TipoDestinatario.PERSONA, "   ", null), "comuneCodice"),
                    Arguments.of(new DatiInvio(MISURA, TipoDestinatario.PERSONA, "x".repeat(51), null), "comuneCodice"),
                    Arguments.of(new DatiInvio(MISURA, TipoDestinatario.PERSONA, "napo\u0000li", null), "comuneCodice"),
                    Arguments.of(new DatiInvio(MISURA, TipoDestinatario.PERSONA, "napoli", "a".repeat(1001)),
                            "description"),
                    Arguments.of(new DatiInvio(MISURA, TipoDestinatario.PERSONA, "napoli", "testo\u0007"),
                            "description"));
        }

        @ParameterizedTest(name = "errore su {1}")
        @MethodSource("datiNonValidi")
        @DisplayName("Campi mancanti o malformati sono respinti prima di consultare catalogo e persistenza")
        void campiNonValidi(DatiInvio dati, String campo) {
            assertThatThrownBy(() -> servizio.invia(opt, dati, null))
                    .isInstanceOfSatisfying(DatiRichiestaNonValidiException.class,
                            ex -> assertThat(ex.campi()).containsKey(campo));
            verifyNoInteractions(repository, catalogo);
        }

        @Test
        @DisplayName("Una description di 1000 caratteri con a capo e tabulazioni è ammessa")
        void descriptionAlLimite() {
            catalogoValido();
            when(repository.registra(any())).thenAnswer(invocazione -> invocazione.getArgument(0));
            String testo = "riga\n\tseguito\r\n" + "a".repeat(985);

            servizio.invia(opt, new DatiInvio(MISURA, TipoDestinatario.PERSONA, "napoli", testo), null);

            verify(repository).registra(any());
        }

        @Test
        @DisplayName("Misura disattivata o inesistente e comune sconosciuto sono errori di validazione")
        void riferimentiDiCatalogoNonValidi() {
            when(repository.cercaPerInvio(opt.accountId(), null)).thenReturn(Optional.empty());
            when(catalogo.cercaMisura(MISURA)).thenReturn(Optional.of(misura(false)));
            when(catalogo.cercaMisura(99L)).thenReturn(Optional.empty());
            when(catalogo.cercaComune("ignoto")).thenReturn(Optional.empty());
            when(catalogo.cercaComune("napoli")).thenReturn(Optional.of(new Comune("napoli", "Napoli")));

            assertThatThrownBy(() -> servizio.invia(opt, new DatiInvio(MISURA, TipoDestinatario.PERSONA, "napoli", null),
                    null)).isInstanceOfSatisfying(DatiRichiestaNonValidiException.class,
                            ex -> assertThat(ex.campi()).containsOnlyKeys("misuraId"));
            assertThatThrownBy(() -> servizio.invia(opt, new DatiInvio(99L, TipoDestinatario.PERSONA, "ignoto", null),
                    null)).isInstanceOfSatisfying(DatiRichiestaNonValidiException.class,
                            ex -> assertThat(ex.campi()).containsOnlyKeys("misuraId", "comuneCodice"));
            verify(repository, never()).registra(any());
        }

        @Test
        @DisplayName("Un ritento con lo stesso riferimento e gli stessi dati restituisce la richiesta già registrata")
        void ritentoRiconosciuto() {
            UUID riferimento = UUID.randomUUID();
            var dati = new DatiInvio(MISURA, TipoDestinatario.PERSONA, "napoli", null);
            var esistente = RichiestaDiMisura.inviata(opt.accountId(), dati, ORA.minusSeconds(60), riferimento);
            when(repository.cercaPerInvio(opt.accountId(), riferimento)).thenReturn(Optional.of(esistente));
            when(catalogo.cercaMisura(MISURA)).thenReturn(Optional.of(misura(true)));

            var sintesi = servizio.invia(opt, dati, riferimento);

            assertThat(sintesi.id()).isEqualTo(esistente.id());
            verify(repository, never()).registra(any());
        }

        @Test
        @DisplayName("Lo stesso riferimento con dati diversi è in conflitto")
        void riferimentoConDatiDiversi() {
            UUID riferimento = UUID.randomUUID();
            var esistente = RichiestaDiMisura.inviata(opt.accountId(),
                    new DatiInvio(MISURA, TipoDestinatario.PERSONA, "napoli", null), ORA, riferimento);
            when(repository.cercaPerInvio(opt.accountId(), riferimento)).thenReturn(Optional.of(esistente));

            assertThatThrownBy(() -> servizio.invia(opt,
                    new DatiInvio(MISURA, TipoDestinatario.NUCLEO, "napoli", null), riferimento))
                    .isInstanceOf(ConflittoRichiestaException.class);
            verify(repository, never()).registra(any());
        }

        @Test
        @DisplayName("Una misura disattivata al momento del lock rifiuta il nuovo invio e restituisce il ritento già salvato")
        void misuraDisattivataAlLock() {
            UUID riferimento = UUID.randomUUID();
            var dati = new DatiInvio(MISURA, TipoDestinatario.PERSONA, "napoli", null);
            var esistente = RichiestaDiMisura.inviata(opt.accountId(), dati, ORA.minusSeconds(10), riferimento);
            when(repository.cercaPerInvio(opt.accountId(), null)).thenReturn(Optional.empty());
            when(repository.cercaPerInvio(opt.accountId(), riferimento)).thenReturn(Optional.empty(), Optional.of(esistente));
            when(catalogo.cercaMisura(MISURA)).thenReturn(Optional.of(misura(true)));
            when(catalogo.cercaComune("napoli")).thenReturn(Optional.of(new Comune("napoli", "Napoli")));
            when(selezioneMisure.bloccaMisuraAttiva(MISURA)).thenReturn(false);

            assertThatThrownBy(() -> servizio.invia(opt, dati, null))
                    .isInstanceOfSatisfying(DatiRichiestaNonValidiException.class,
                            ex -> assertThat(ex.campi()).containsOnlyKeys("misuraId"));
            assertThat(servizio.invia(opt, dati, riferimento).id()).isEqualTo(esistente.id());
            verify(repository, never()).registra(any());
        }

        private void catalogoValido() {
            when(repository.cercaPerInvio(opt.accountId(), null)).thenReturn(Optional.empty());
            when(catalogo.cercaMisura(MISURA)).thenReturn(Optional.of(misura(true)));
            when(catalogo.cercaComune("napoli")).thenReturn(Optional.of(new Comune("napoli", "Napoli")));
            when(selezioneMisure.bloccaMisuraAttiva(MISURA)).thenReturn(true);
        }
    }

    @Nested
    @DisplayName("Elenchi")
    class Elenchi {

        @Test
        @DisplayName("L'elenco OPT contiene solo le richieste del titolare della sessione")
        void elencoOpt() {
            var propria = richiesta(opt.accountId(), null);
            when(repository.cercaPerTitolare(opt.accountId())).thenReturn(List.of(propria));
            when(catalogo.cercaMisura(MISURA)).thenReturn(Optional.of(misura(true)));

            assertThat(servizio.elencoRichiesteOpt(opt)).extracting(s -> s.id()).containsExactly(propria.id());
        }

        @Test
        @DisplayName("La coda è riservata al Banco e l'elenco OPT alle OPT")
        void elenchiPerRuolo() {
            assertThatThrownBy(() -> servizio.codaBanco(opt)).isInstanceOf(AccessoNegatoException.class);
            assertThatThrownBy(() -> servizio.elencoRichiesteOpt(banco)).isInstanceOf(AccessoNegatoException.class);
            assertThatThrownBy(() -> servizio.codaBanco(null)).isInstanceOf(AccessoNegatoException.class);
            verifyNoInteractions(repository);
        }
    }

    @Nested
    @DisplayName("Consultazione del dettaglio")
    class Consultazione {

        @Test
        @DisplayName("Titolare e Banco vedono description e titolare; la lettura non scrive")
        void titolareEBanco() {
            var richiesta = richiesta(opt.accountId(), null);
            preparaLettura(richiesta);

            for (var attore : List.of(opt, banco)) {
                var dettaglio = servizio.consultaRichiesta(attore, richiesta.id());
                assertThat(dettaglio.description()).isEqualTo("Descrizione riservata");
                assertThat(dettaglio.titolareId()).isEqualTo(opt.accountId());
                assertThat(dettaglio.richiesta().stato()).isEqualTo(StatoRichiesta.CREATA);
            }
            verify(repository, never()).registra(any());
            verify(repository, never()).aggiorna(any());
            verify(repository, never()).cercaPerAggiornamento(any());
        }

        @Test
        @DisplayName("L'erogatore assegnato vede il dettaglio senza description né titolare")
        void erogatoreAssegnato() {
            var erogatore = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPERATORE);
            var richiesta = richiesta(opt.accountId(), erogatore.accountId());
            preparaLettura(richiesta);

            var dettaglio = servizio.consultaRichiesta(erogatore, richiesta.id());

            assertThat(dettaglio.description()).isNull();
            assertThat(dettaglio.titolareId()).isNull();
            assertThat(dettaglio.erogatoreAssegnatoId()).isEqualTo(erogatore.accountId());
        }

        @Test
        @DisplayName("Una OPT estranea senza assegnazione e un operatore non assegnato ricevono un diniego")
        void estranei() {
            var richiesta = richiesta(opt.accountId(), null);
            when(repository.cercaPerId(richiesta.id())).thenReturn(Optional.of(richiesta));

            assertThatThrownBy(() -> servizio.consultaRichiesta(
                    new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPT), richiesta.id()))
                    .isInstanceOf(AccessoNegatoException.class);
            assertThatThrownBy(() -> servizio.consultaRichiesta(
                    new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPERATORE), richiesta.id()))
                    .isInstanceOf(AccessoNegatoException.class);
            verifyNoInteractions(catalogo);
        }

        @Test
        @DisplayName("Una richiesta inesistente è segnalata come non trovata")
        void inesistente() {
            UUID id = UUID.randomUUID();
            when(repository.cercaPerId(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servizio.consultaRichiesta(banco, id))
                    .isInstanceOf(RisorsaRichiesteNonTrovataException.class);
        }

        @Test
        @DisplayName("Su una richiesta Confermata il dettaglio riporta l'accordo")
        void accordoConfermato() {
            var accordo = new AccordoInPresenza(ORA.plusSeconds(86_400), "Sede di esempio", "Nome", "Cognome",
                    "+39 000 0000000");
            var confermata = new RichiestaDiMisura(UUID.randomUUID(), opt.accountId(), MISURA, TipoDestinatario.PERSONA,
                    "napoli", null, ORA, StatoRichiesta.CONFERMATA, UUID.randomUUID(), accordo, null, null);
            preparaLettura(confermata);

            var dettaglio = servizio.consultaRichiesta(opt, confermata.id());

            assertThat(dettaglio.accordo()).isEqualTo(accordo);
            assertThat(dettaglio.richiesta().stato()).isEqualTo(StatoRichiesta.CONFERMATA);
        }

        private void preparaLettura(RichiestaDiMisura richiesta) {
            when(repository.cercaPerId(richiesta.id())).thenReturn(Optional.of(richiesta));
            when(catalogo.cercaMisura(MISURA)).thenReturn(Optional.of(misura(true)));
        }
    }

    private static Misura misura(boolean attiva) {
        return new Misura(MISURA, new DatiMisura("M01", NOME_MISURA, "Descrizione", false), attiva);
    }

    private static RichiestaDiMisura richiesta(UUID titolare, UUID erogatore) {
        return new RichiestaDiMisura(UUID.randomUUID(), titolare, MISURA, TipoDestinatario.PERSONA, "napoli",
                "Descrizione riservata", ORA, StatoRichiesta.CREATA, erogatore, null, null, null);
    }
}
