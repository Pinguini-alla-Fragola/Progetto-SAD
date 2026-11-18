package it.unina.picus.richieste.interno.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.catalogo.api.CatalogoApi.ErogatoreCompatibile;
import it.unina.picus.richieste.api.RichiesteApi.Candidato;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * Politica d'ordinamento dei candidati (#49, criterio 2 della #102): prossimità, poi operatori
 * del Banco prima delle OPT, poi minor carico; nome e UUID rendono soltanto stabile l'ordine.
 */
@DisplayName("Ordinamento degli erogatori compatibili")
class OrdinamentoErogatoriTest {

    @Test
    @DisplayName("La prossimità prevale su tipo e carico: stesso comune, limitrofo, Centro PICUS a distanza")
    void prossimitaPrevaleSuTipoECarico() {
        var remoto = opzione("Centro PICUS", TipoErogatore.INTERNO_BANCO, AmbitoErogazione.CENTRO_PICUS_A_DISTANZA);
        var limitrofo = opzione("Operatore limitrofo", TipoErogatore.INTERNO_BANCO, AmbitoErogazione.COMUNE_LIMITROFO);
        var locale = opzione("OPT locale", TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);

        var ordinati = OrdinamentoErogatori.ordina(List.of(remoto, limitrofo, locale), Map.of(locale.accountId(), 9));

        assertThat(nomi(ordinati)).containsExactly("OPT locale", "Operatore limitrofo", "Centro PICUS");
    }

    @Test
    @DisplayName("A pari prossimità gli operatori del Banco precedono le OPT anche con più carico; tra loro decide il carico")
    void operatoriDelBancoPrimaDelleOpt() {
        var opt = opzione("OPT", TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);
        var esterno = opzione("Esterno", TipoErogatore.ESTERNO_PAGATO_BANCO, AmbitoErogazione.STESSO_COMUNE);
        var interno = opzione("Interno", TipoErogatore.INTERNO_BANCO, AmbitoErogazione.STESSO_COMUNE);

        // L'esterno ha meno carico dell'interno e la OPT meno di entrambi: una precedenza fissa
        // dell'interno sull'esterno, o del carico sul ruolo, produrrebbe un ordine diverso.
        var ordinati = OrdinamentoErogatori.ordina(List.of(opt, interno, esterno),
                Map.of(esterno.accountId(), 1, interno.accountId(), 3, opt.accountId(), 0));

        assertThat(nomi(ordinati)).containsExactly("Esterno", "Interno", "OPT");
        assertThat(ordinati).extracting(Candidato::caricoAppuntamenti).containsExactly(1, 3, 0);
    }

    @Test
    @DisplayName("A pari prossimità e tipo viene prima il minor carico, riportato nel candidato")
    void minorCaricoPrima() {
        var pesante = opzione("A", TipoErogatore.OPT, AmbitoErogazione.COMUNE_LIMITROFO);
        var libero = opzione("B", TipoErogatore.OPT, AmbitoErogazione.COMUNE_LIMITROFO);
        var medio = opzione("C", TipoErogatore.OPT, AmbitoErogazione.COMUNE_LIMITROFO);

        var ordinati = OrdinamentoErogatori.ordina(List.of(pesante, libero, medio),
                Map.of(pesante.accountId(), 4, libero.accountId(), 0, medio.accountId(), 2));

        assertThat(nomi(ordinati)).containsExactly("B", "C", "A");
        assertThat(ordinati).extracting(Candidato::caricoAppuntamenti).containsExactly(0, 2, 4);
    }

    @Test
    @DisplayName("Un erogatore senza appuntamenti confermati ha carico zero")
    void caricoAssenteValeZero() {
        var occupato = opzione("A", TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);
        var senzaCarico = opzione("Z", TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);

        var ordinati = OrdinamentoErogatori.ordina(List.of(occupato, senzaCarico), Map.of(occupato.accountId(), 1));

        assertThat(ordinati).extracting(c -> c.erogatore().accountId())
                .containsExactly(senzaCarico.accountId(), occupato.accountId());
        assertThat(ordinati.getFirst().caricoAppuntamenti()).isZero();
    }

    @Test
    @DisplayName("A parità completa decidono il nome senza distinzione di maiuscole e poi l'UUID")
    void paritaRisolteInModoStabile() {
        // "Beta" precede "alfa" in un confronto che distingue le maiuscole: l'ordine atteso vale
        // solo se il nome è confrontato senza distinzione. Gli UUID fissi arrivano in ordine inverso.
        var beta = opzione("Beta", TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);
        var alfa = opzione("alfa", TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);
        var omonimoMinore = opzione(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Omonimo",
                TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);
        var omonimoMaggiore = opzione(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Omonimo",
                TipoErogatore.OPT, AmbitoErogazione.STESSO_COMUNE);

        var ordinati = OrdinamentoErogatori.ordina(List.of(omonimoMaggiore, beta, omonimoMinore, alfa), Map.of());

        assertThat(ordinati).extracting(c -> c.erogatore().accountId()).containsExactly(
                alfa.accountId(), beta.accountId(), omonimoMinore.accountId(), omonimoMaggiore.accountId());
    }

    @Test
    @DisplayName("Lo stesso account compare una volta per ciascuna opzione compatibile")
    void stessoAccountInPiuOpzioni() {
        UUID centro = UUID.randomUUID();
        var locale = new ErogatoreCompatibile(centro, "Centro", TipoErogatore.INTERNO_BANCO, "napoli",
                AmbitoErogazione.STESSO_COMUNE);
        var remoto = new ErogatoreCompatibile(centro, "Centro", TipoErogatore.INTERNO_BANCO, "napoli",
                AmbitoErogazione.CENTRO_PICUS_A_DISTANZA);
        var opt = opzione("OPT", TipoErogatore.OPT, AmbitoErogazione.COMUNE_LIMITROFO);

        var ordinati = OrdinamentoErogatori.ordina(List.of(remoto, opt, locale), Map.of(centro, 2));

        assertThat(ordinati).extracting(c -> c.erogatore().ambito()).containsExactly(
                AmbitoErogazione.STESSO_COMUNE, AmbitoErogazione.COMUNE_LIMITROFO,
                AmbitoErogazione.CENTRO_PICUS_A_DISTANZA);
        assertThat(ordinati.getFirst().caricoAppuntamenti()).isEqualTo(2);
        assertThat(ordinati.getLast().caricoAppuntamenti()).isEqualTo(2);
    }

    @Test
    @DisplayName("Senza opzioni compatibili il risultato è vuoto")
    void nessunaOpzione() {
        assertThat(OrdinamentoErogatori.ordina(List.of(), Map.of(UUID.randomUUID(), 3))).isEmpty();
    }

    private static ErogatoreCompatibile opzione(String nome, TipoErogatore tipo, AmbitoErogazione ambito) {
        return opzione(UUID.randomUUID(), nome, tipo, ambito);
    }

    private static ErogatoreCompatibile opzione(UUID account, String nome, TipoErogatore tipo, AmbitoErogazione ambito) {
        return new ErogatoreCompatibile(account, nome, tipo, "napoli", ambito);
    }

    private static List<String> nomi(List<Candidato> candidati) {
        return candidati.stream().map(c -> c.erogatore().nomeVisualizzato()).toList();
    }
}
