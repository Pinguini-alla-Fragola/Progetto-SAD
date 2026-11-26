package it.unina.picus.richieste.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import it.unina.picus.catalogo.api.RicercaErogatoriApi;
import it.unina.picus.comunicazioni.api.AvvisiOperativiApi;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.InvioRichiesteApi;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import it.unina.picus.richieste.interno.dominio.ContattiRepository;
import it.unina.picus.richieste.interno.dominio.RichiestaDiMisura;
import it.unina.picus.richieste.interno.dominio.RichiestaRepository;
import it.unina.picus.shared.AccessoNegatoException;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * Regole del Service Layer di coordinamento con porte simulate: autorizzazione del solo Banco,
 * contatto unico con avviso al solo destinatario e assegnazione soltanto dopo una risposta
 * positiva ancora valida. L'atomicità reale è verificata su PostgreSQL nei test di integrazione.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Servizio di coordinamento delle richieste")
@Tag("unit")
class ServizioCoordinamentoRichiesteTest {

    private static final long MISURA = 1L;
    private static final String COMUNE = "napoli";
    private static final String NOME_MISURA = "Accoglienza e ascolto";
    private static final Instant ORA = Instant.parse("2026-11-17T10:00:00Z");

    @Mock
    private RichiestaRepository richieste;
    @Mock
    private ContattiRepository contatti;
    @Mock
    private RicercaErogatoriApi catalogo;
    @Mock
    private InvioRichiesteApi consultazione;
    @Mock
    private AvvisiOperativiApi avvisi;

    private final IdentitaAutenticata banco = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);
    private final UUID richiestaId = UUID.randomUUID();
    private final UUID locale = UUID.randomUUID();
    private final UUID centro = UUID.randomUUID();
    private ServizioCoordinamentoRichieste servizio;

    @BeforeEach
    void crea() {
        servizio = new ServizioCoordinamentoRichieste(richieste, contatti, catalogo, consultazione, avvisi,
                Clock.fixed(ORA, ZoneOffset.UTC));
    }

    static Stream<IdentitaAutenticata> identitaNonBanco() {
        return Stream.of(
                null,
                new IdentitaAutenticata(null, RuoloUtente.BANCO),
                new IdentitaAutenticata(UUID.randomUUID(), null),
                new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPT),
                new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.OPERATORE));
    }

    @ParameterizedTest
    @MethodSource("identitaNonBanco")
    @DisplayName("OPT, operatori e identità incomplete non eseguono alcuna azione di coordinamento")
    void azioniRiservateAlBanco(IdentitaAutenticata attore) {
        UUID contatto = UUID.randomUUID();

        assertThatThrownBy(() -> servizio.prendiInCarico(attore, richiestaId)).isInstanceOf(AccessoNegatoException.class);
        assertThatThrownBy(() -> servizio.cercaErogatoriOrdinati(attore, richiestaId))
                .isInstanceOf(AccessoNegatoException.class);
        assertThatThrownBy(() -> servizio.interpellaErogatore(attore, richiestaId, locale, AmbitoErogazione.STESSO_COMUNE))
                .isInstanceOf(AccessoNegatoException.class);
        assertThatThrownBy(() -> servizio.assegnaErogatore(attore, contatto)).isInstanceOf(AccessoNegatoException.class);
        assertThatThrownBy(() -> servizio.consultaContattoBanco(attore, richiestaId, contatto))
                .isInstanceOf(AccessoNegatoException.class);

        verifyNoInteractions(richieste, contatti, catalogo, consultazione, avvisi);
    }

    @Nested
    @DisplayName("Presa in carico e ricerca")
    class PresaInCaricoERicerca {

        @Test
        @DisplayName("La presa in carico salva Presa in carico sotto lock")
        void presaInCarico() {
            when(richieste.cercaPerAggiornamento(richiestaId)).thenReturn(Optional.of(richiesta(StatoRichiesta.CREATA)));
            when(consultazione.consultaRichiesta(banco, richiestaId)).thenReturn(dettaglio(StatoRichiesta.PRESA_IN_CARICO));

            var sintesi = servizio.prendiInCarico(banco, richiestaId);

            var salvata = ArgumentCaptor.forClass(RichiestaDiMisura.class);
            verify(richieste).aggiorna(salvata.capture());
            assertThat(salvata.getValue().stato()).isEqualTo(StatoRichiesta.PRESA_IN_CARICO);
            assertThat(sintesi.stato()).isEqualTo(StatoRichiesta.PRESA_IN_CARICO);
        }

        @Test
        @DisplayName("Una presa in carico ripetuta è in conflitto e non salva nulla")
        void presaInCaricoRipetuta() {
            when(richieste.cercaPerAggiornamento(richiestaId))
                    .thenReturn(Optional.of(richiesta(StatoRichiesta.PRESA_IN_CARICO)));

            assertThatThrownBy(() -> servizio.prendiInCarico(banco, richiestaId))
                    .isInstanceOf(ConflittoRichiestaException.class);
            verify(richieste, never()).aggiorna(any());
        }

        @Test
        @DisplayName("Una richiesta inesistente non può essere presa in carico")
        void presaInCaricoInesistente() {
            when(richieste.cercaPerAggiornamento(richiestaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servizio.prendiInCarico(banco, richiestaId))
                    .isInstanceOf(RisorsaRichiesteNonTrovataException.class);
        }

        @Test
        @DisplayName("La ricerca ordina con il carico calcolato all'istante corrente e non cambia lo stato")
        void ricercaSenzaTransizioni() {
            var opt = opzione(UUID.randomUUID(), TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);
            var operatore = opzione(locale, TipoErogatore.INTERNO_BANCO, AmbitoErogazione.STESSO_COMUNE);
            when(richieste.cercaPerId(richiestaId)).thenReturn(Optional.of(richiesta(StatoRichiesta.PRESA_IN_CARICO)));
            when(catalogo.cercaErogatoriCompatibili(MISURA, COMUNE)).thenReturn(List.of(opt, operatore));
            when(richieste.carichiConfermati(ORA)).thenReturn(Map.of(locale, 4));

            var candidati = servizio.cercaErogatoriOrdinati(banco, richiestaId);

            assertThat(candidati).extracting(c -> c.erogatore().accountId()).containsExactly(locale, opt.accountId());
            assertThat(candidati.getFirst().caricoAppuntamenti()).isEqualTo(4);
            verify(richieste, never()).cercaPerAggiornamento(any());
            verify(richieste, never()).aggiorna(any());
            verifyNoInteractions(contatti, avvisi);
        }

        @Test
        @DisplayName("La ricerca è in conflitto su una richiesta non ancora presa in carico")
        void ricercaSuRichiestaCreata() {
            when(richieste.cercaPerId(richiestaId)).thenReturn(Optional.of(richiesta(StatoRichiesta.CREATA)));

            assertThatThrownBy(() -> servizio.cercaErogatoriOrdinati(banco, richiestaId))
                    .isInstanceOf(ConflittoRichiestaException.class);
            verifyNoInteractions(catalogo);
        }
    }

    @Nested
    @DisplayName("Interpello di un erogatore")
    class Interpello {

        @Test
        @DisplayName("Registra un contatto e un avviso per il solo destinatario scelto")
        void contattoEAvvisoUnici() {
            var altro = opzione(UUID.randomUUID(), TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);
            preparaInterpello(List.of(opzione(locale, TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE), altro));
            var creato = contatto(locale, AmbitoErogazione.STESSO_COMUNE, EsitoContatto.IN_ATTESA);
            when(contatti.registra(richiestaId, locale, AmbitoErogazione.STESSO_COMUNE, NOME_MISURA, ORA))
                    .thenReturn(creato);

            var risultato = servizio.interpellaErogatore(banco, richiestaId, locale, AmbitoErogazione.STESSO_COMUNE);

            assertThat(risultato).isEqualTo(creato);
            verify(avvisi).avvisaDisponibilita(richiestaId, creato.id(), locale);
            verifyNoMoreInteractions(avvisi);
            verify(richieste, never()).aggiorna(any());
        }

        @Test
        @DisplayName("Con un contatto già in attesa il nuovo interpello è in conflitto senza scritture")
        void contattoGiaInAttesa() {
            when(richieste.cercaPerAggiornamento(richiestaId))
                    .thenReturn(Optional.of(richiesta(StatoRichiesta.PRESA_IN_CARICO)));
            when(contatti.inAttesa(richiestaId)).thenReturn(true);

            assertThatThrownBy(() -> servizio.interpellaErogatore(banco, richiestaId, locale,
                    AmbitoErogazione.STESSO_COMUNE)).isInstanceOf(ConflittoRichiestaException.class);
            verify(contatti, never()).registra(any(), any(), any(), any(), any());
            verifyNoInteractions(avvisi);
        }

        @Test
        @DisplayName("Un account o un ambito non restituiti dalla ricerca sono dati non validi")
        void opzioneNonCompatibile() {
            when(richieste.cercaPerAggiornamento(richiestaId))
                    .thenReturn(Optional.of(richiesta(StatoRichiesta.PRESA_IN_CARICO)));
            when(contatti.inAttesa(richiestaId)).thenReturn(false);
            when(catalogo.cercaErogatoriCompatibili(MISURA, COMUNE))
                    .thenReturn(List.of(opzione(locale, TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE)));

            assertThatThrownBy(() -> servizio.interpellaErogatore(banco, richiestaId, locale,
                    AmbitoErogazione.COMUNE_LIMITROFO)).isInstanceOf(DatiRichiestaNonValidiException.class);
            assertThatThrownBy(() -> servizio.interpellaErogatore(banco, richiestaId, UUID.randomUUID(),
                    AmbitoErogazione.STESSO_COMUNE)).isInstanceOf(DatiRichiestaNonValidiException.class);
            verify(contatti, never()).registra(any(), any(), any(), any(), any());
            verifyNoInteractions(avvisi);
        }

        @Test
        @DisplayName("Erogatore e ambito sono obbligatori e vengono controllati prima di leggere la pratica")
        void datiObbligatori() {
            assertThatThrownBy(() -> servizio.interpellaErogatore(banco, richiestaId, null,
                    AmbitoErogazione.STESSO_COMUNE)).isInstanceOf(DatiRichiestaNonValidiException.class);
            assertThatThrownBy(() -> servizio.interpellaErogatore(banco, richiestaId, locale, null))
                    .isInstanceOf(DatiRichiestaNonValidiException.class);
            verifyNoInteractions(richieste, contatti, avvisi);
        }

        @ParameterizedTest(name = "in {0}")
        @EnumSource(value = StatoRichiesta.class, names = "PRESA_IN_CARICO", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Fuori da Presa in carico l'interpello è in conflitto")
        void statoIncompatibile(StatoRichiesta stato) {
            when(richieste.cercaPerAggiornamento(richiestaId)).thenReturn(Optional.of(richiesta(stato)));

            assertThatThrownBy(() -> servizio.interpellaErogatore(banco, richiestaId, locale,
                    AmbitoErogazione.STESSO_COMUNE)).isInstanceOf(ConflittoRichiestaException.class);
            verifyNoInteractions(contatti, avvisi);
        }

        @Test
        @DisplayName("Una richiesta inesistente non può essere interpellata")
        void richiestaInesistente() {
            when(richieste.cercaPerAggiornamento(richiestaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servizio.interpellaErogatore(banco, richiestaId, locale,
                    AmbitoErogazione.STESSO_COMUNE)).isInstanceOf(RisorsaRichiesteNonTrovataException.class);
        }

        @Test
        @DisplayName("Il Centro PICUS a distanza attende il diniego delle opzioni locali")
        void remotoDopoLocali() {
            when(richieste.cercaPerAggiornamento(richiestaId))
                    .thenReturn(Optional.of(richiesta(StatoRichiesta.PRESA_IN_CARICO)));
            when(contatti.inAttesa(richiestaId)).thenReturn(false);
            when(catalogo.cercaErogatoriCompatibili(MISURA, COMUNE)).thenReturn(List.of(
                    opzione(locale, TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE),
                    opzione(centro, TipoErogatore.INTERNO_BANCO, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA)));
            when(contatti.storico(richiestaId)).thenReturn(List.of());

            assertThatThrownBy(() -> servizio.interpellaErogatore(banco, richiestaId, centro,
                    AmbitoErogazione.CENTRO_PICUS_A_DISTANZA)).isInstanceOf(ConflittoRichiestaException.class);
            verifyNoInteractions(avvisi);
        }

        @Test
        @DisplayName("Dopo il diniego dell'ultima opzione locale il Centro PICUS può essere interpellato")
        void remotoConLocaliDeclinati() {
            preparaInterpello(List.of(
                    opzione(locale, TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE),
                    opzione(centro, TipoErogatore.INTERNO_BANCO, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA)));
            when(contatti.storico(richiestaId)).thenReturn(List.of(
                    contatto(locale, AmbitoErogazione.STESSO_COMUNE, EsitoContatto.NON_DISPONIBILE)));
            var creato = contatto(centro, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA, EsitoContatto.IN_ATTESA);
            when(contatti.registra(richiestaId, centro, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA, NOME_MISURA, ORA))
                    .thenReturn(creato);

            servizio.interpellaErogatore(banco, richiestaId, centro, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA);

            verify(avvisi).avvisaDisponibilita(richiestaId, creato.id(), centro);
        }

        @Test
        @DisplayName("Senza opzioni locali il Centro PICUS è subito utilizzabile")
        void remotoSenzaLocali() {
            preparaInterpello(List.of(
                    opzione(centro, TipoErogatore.INTERNO_BANCO, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA)));
            var creato = contatto(centro, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA, EsitoContatto.IN_ATTESA);
            when(contatti.registra(richiestaId, centro, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA, NOME_MISURA, ORA))
                    .thenReturn(creato);

            servizio.interpellaErogatore(banco, richiestaId, centro, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA);

            verify(avvisi).avvisaDisponibilita(richiestaId, creato.id(), centro);
        }

        private void preparaInterpello(List<ErogatoreCompatibile> opzioni) {
            when(richieste.cercaPerAggiornamento(richiestaId))
                    .thenReturn(Optional.of(richiesta(StatoRichiesta.PRESA_IN_CARICO)));
            when(contatti.inAttesa(richiestaId)).thenReturn(false);
            when(catalogo.cercaErogatoriCompatibili(MISURA, COMUNE)).thenReturn(opzioni);
            when(consultazione.consultaRichiesta(banco, richiestaId)).thenReturn(dettaglio(StatoRichiesta.PRESA_IN_CARICO));
        }
    }

    @Nested
    @DisplayName("Assegnazione")
    class Assegnazione {

        @ParameterizedTest(name = "contatto {0}")
        @EnumSource(value = EsitoContatto.class, names = "DISPONIBILE", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Senza una risposta positiva non si assegna")
        void senzaDisponibilita(EsitoContatto esito) {
            var contatto = contatto(locale, AmbitoErogazione.STESSO_COMUNE, esito);
            preparaContatto(contatto);

            assertThatThrownBy(() -> servizio.assegnaErogatore(banco, contatto.id()))
                    .isInstanceOf(ConflittoRichiestaException.class);
            verify(richieste, never()).aggiorna(any());
        }

        @Test
        @DisplayName("Una risposta positiva superata da un contatto successivo della stessa opzione non vale")
        void rispostaSuperata() {
            var positivo = contatto(locale, AmbitoErogazione.STESSO_COMUNE, EsitoContatto.DISPONIBILE);
            var successivo = contatto(locale, AmbitoErogazione.STESSO_COMUNE, EsitoContatto.NON_DISPONIBILE);
            preparaContatto(positivo);
            when(contatti.storico(richiestaId)).thenReturn(List.of(positivo, successivo));

            assertThatThrownBy(() -> servizio.assegnaErogatore(banco, positivo.id()))
                    .isInstanceOf(ConflittoRichiestaException.class);
            verify(richieste, never()).aggiorna(any());
        }

        @Test
        @DisplayName("Con un altro contatto in attesa non si assegna")
        void altroContattoInAttesa() {
            var positivo = contatto(locale, AmbitoErogazione.STESSO_COMUNE, EsitoContatto.DISPONIBILE);
            preparaContatto(positivo);
            when(contatti.storico(richiestaId)).thenReturn(List.of(positivo,
                    contatto(UUID.randomUUID(), AmbitoErogazione.STESSO_COMUNE, EsitoContatto.IN_ATTESA)));
            when(contatti.inAttesa(richiestaId)).thenReturn(true);

            assertThatThrownBy(() -> servizio.assegnaErogatore(banco, positivo.id()))
                    .isInstanceOf(ConflittoRichiestaException.class);
            verifyNoInteractions(catalogo);
            verify(richieste, never()).aggiorna(any());
        }

        @Test
        @DisplayName("Un erogatore non più abilitato non viene assegnato")
        void erogatoreNonPiuAbilitato() {
            var positivo = contatto(locale, AmbitoErogazione.STESSO_COMUNE, EsitoContatto.DISPONIBILE);
            preparaContatto(positivo);
            when(contatti.storico(richiestaId)).thenReturn(List.of(positivo));
            when(contatti.inAttesa(richiestaId)).thenReturn(false);
            when(catalogo.bloccaCompatibilita(MISURA, COMUNE, locale, AmbitoErogazione.STESSO_COMUNE)).thenReturn(false);

            assertThatThrownBy(() -> servizio.assegnaErogatore(banco, positivo.id()))
                    .isInstanceOf(ConflittoRichiestaException.class);
            verify(richieste, never()).aggiorna(any());
        }

        @Test
        @DisplayName("Assegnatario, ambito e In programmazione sono salvati con un unico aggiornamento")
        void assegnazioneRegistrataInsieme() {
            var positivo = contatto(locale, AmbitoErogazione.COMUNE_LIMITROFO, EsitoContatto.DISPONIBILE);
            preparaContatto(positivo);
            when(contatti.storico(richiestaId)).thenReturn(List.of(positivo));
            when(contatti.inAttesa(richiestaId)).thenReturn(false);
            when(catalogo.bloccaCompatibilita(MISURA, COMUNE, locale, AmbitoErogazione.COMUNE_LIMITROFO)).thenReturn(true);
            when(consultazione.consultaRichiesta(banco, richiestaId)).thenReturn(dettaglio(StatoRichiesta.IN_PROGRAMMAZIONE));

            var sintesi = servizio.assegnaErogatore(banco, positivo.id());

            var salvata = ArgumentCaptor.forClass(RichiestaDiMisura.class);
            verify(richieste).aggiorna(salvata.capture());
            assertThat(salvata.getValue().stato()).isEqualTo(StatoRichiesta.IN_PROGRAMMAZIONE);
            assertThat(salvata.getValue().erogatoreId()).isEqualTo(locale);
            assertThat(salvata.getValue().erogatoreAmbito()).isEqualTo(AmbitoErogazione.COMUNE_LIMITROFO);
            assertThat(sintesi.stato()).isEqualTo(StatoRichiesta.IN_PROGRAMMAZIONE);
        }

        @Test
        @DisplayName("Un contatto inesistente non può essere usato per assegnare")
        void contattoInesistente() {
            UUID contatto = UUID.randomUUID();
            when(contatti.cercaRiferimento(contatto)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servizio.assegnaErogatore(banco, contatto))
                    .isInstanceOf(RisorsaRichiesteNonTrovataException.class);
            verify(richieste, never()).cercaPerAggiornamento(any());
            verify(catalogo, never()).bloccaCompatibilita(anyLong(), any(), any(), any());
        }

        private void preparaContatto(ContattoDisponibilita contatto) {
            when(contatti.cercaRiferimento(contatto.id()))
                    .thenReturn(Optional.of(new ContattiRepository.Riferimento(richiestaId, contatto.erogatoreId())));
            when(richieste.cercaPerAggiornamento(richiestaId))
                    .thenReturn(Optional.of(richiesta(StatoRichiesta.PRESA_IN_CARICO)));
            when(contatti.cerca(contatto.id())).thenReturn(Optional.of(contatto));
        }
    }

    private RichiestaDiMisura richiesta(StatoRichiesta stato) {
        return new RichiestaDiMisura(richiestaId, UUID.randomUUID(), MISURA, TipoDestinatario.PERSONA, COMUNE,
                null, Instant.parse("2026-11-01T09:00:00Z"), stato, null, null, null, null);
    }

    private RichiestaDettaglio dettaglio(StatoRichiesta stato) {
        return new RichiestaDettaglio(new RichiestaSintesi(richiestaId, Instant.parse("2026-11-01T09:00:00Z"), MISURA,
                NOME_MISURA, stato), UUID.randomUUID(), TipoDestinatario.PERSONA, COMUNE, null, null, null);
    }

    private ContattoDisponibilita contatto(UUID erogatore, AmbitoErogazione ambito, EsitoContatto esito) {
        return new ContattoDisponibilita(UUID.randomUUID(), richiestaId, erogatore, ambito, esito);
    }

    private static ErogatoreCompatibile opzione(UUID account, TipoErogatore tipo, AmbitoErogazione ambito) {
        return new ErogatoreCompatibile(account, "Erogatore " + account, tipo, COMUNE, ambito);
    }
}
