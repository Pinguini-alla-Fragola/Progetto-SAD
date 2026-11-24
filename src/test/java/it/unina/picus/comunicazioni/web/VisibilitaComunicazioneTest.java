package it.unina.picus.comunicazioni.web;

import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.campi;
import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.inviata;
import static it.unina.picus.shared.ClientHttp.assertDiniegoSenzaCorpo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.comunicazioni.web.AllegatiComunicazioneTest.TipoAmmesso;
import it.unina.picus.comunicazioni.web.ComunicazioniHttp.File;
import it.unina.picus.shared.ComunicazioniBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * CA-05 della #55: una comunicazione inviata a un gruppo non è visibile a un account esterno al gruppo e privo di
 * altri permessi, che riceve {@code FORBIDDEN} e nessun testo o allegato.
 *
 * <p>Tecniche: <b>classi di equivalenza</b> sugli account esclusi (OPT fuori dal gruppo, operatore fuori dal
 * gruppo, account dell'altra categoria) per ciascuno dei due tipi a gruppo; <b>test negativi</b> su consultazione
 * e download ({@code 403} a corpo vuoto, assenza dalle ricevute, contatore invariato) con <b>controlli
 * positivi</b>: il destinatario e il Banco consultano e scaricano. La comunicazione, riletta dal Banco dopo i
 * dinieghi, è identica a prima.</p>
 */
@DisplayName("#55 CA-05 · Diniego agli account esterni al gruppo")
class VisibilitaComunicazioneTest extends ComunicazioniBlackBoxTest {

    private ComunicazioniHttp comunicazioni;

    @BeforeEach
    void preparaClient() {
        comunicazioni = new ComunicazioniHttp(http);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TipoDestinatari.class, names = {"GRUPPO_OPT", "GRUPPO_OPERATORI"})
    @DisplayName("CA-05 · account fuori dal gruppo: 403 senza testo né allegato; destinatario e Banco leggono")
    void esternoAlGruppo(TipoDestinatari tipo) throws Exception {
        Account banco = scenario.banco();
        Account optNelGruppo = scenario.optRichiedente();
        Account optFuori = scenario.optRichiedente();
        Account operatoreNelGruppo = scenario.operatore(TipoErogatore.INTERNO_BANCO);
        Account operatoreFuori = scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        boolean perOpt = tipo == TipoDestinatari.GRUPPO_OPT;
        Account destinatario = perOpt ? optNelGruppo : operatoreNelGruppo;
        Map<String, Account> esclusi = new LinkedHashMap<>();
        esclusi.put("OPT fuori dal gruppo", optFuori);
        esclusi.put("operatore fuori dal gruppo", operatoreFuori);
        esclusi.put("account dell'altra categoria", perOpt ? operatoreNelGruppo : optNelGruppo);

        String marcatore = marcatore();
        File allegato = TipoAmmesso.TXT.file(marcatore);
        UUID scelto = comunicazioni.scegli(banco, tipo.name(), destinatario);
        Map<String, Long> contatoriPrima = new LinkedHashMap<>();
        Map<String, Long> ricevutePrima = new LinkedHashMap<>();
        for (Map.Entry<String, Account> escluso : esclusi.entrySet()) {
            contatoriPrima.put(escluso.getKey(), comunicazioni.daConfermare(escluso.getValue()));
            ricevutePrima.put(escluso.getKey(), comunicazioni.ricevute(escluso.getValue()).totale());
        }

        UUID id = inviata(comunicazioni.inviaMultipart(banco, campi("Riservata " + marcatore,
                "Testo riservato al gruppo " + marcatore, tipo.name(), List.of(scelto)), List.of(allegato))).id();
        Map<String, Object> primaDeiDinieghi = comunicazioni.consultaOk(banco, id);
        @SuppressWarnings("unchecked")
        List<String> allegati = (List<String>) primaDeiDinieghi.get("allegati");
        assertEquals(1, allegati.size());
        UUID allegatoId = UUID.fromString(allegati.get(0));

        for (Map.Entry<String, Account> voce : esclusi.entrySet()) {
            Account escluso = voce.getValue();
            String chi = voce.getKey();
            assertDiniegoSenzaCorpo(comunicazioni.consulta(escluso, id));
            assertDiniegoSenzaCorpo(comunicazioni.scarica(escluso, allegatoId));
            ComunicazioniHttp.Pagina ricevute = comunicazioni.ricevute(escluso);
            assertFalse(ricevute.contiene(id), () -> chi + " non deve trovarla tra le ricevute");
            assertEquals(ricevutePrima.get(chi), ricevute.totale(), () -> chi + ": ricevute invariate");
            assertEquals(contatoriPrima.get(chi), comunicazioni.daConfermare(escluso), () -> chi + ": nessuna notifica");
        }

        // Controlli positivi: il destinatario e il Banco consultano e scaricano.
        AllegatiComunicazioneTest.verificaAllegati(comunicazioni, destinatario, id, List.of(allegato),
                List.of(TipoAmmesso.TXT));
        AllegatiComunicazioneTest.verificaAllegati(comunicazioni, banco, id, List.of(allegato),
                List.of(TipoAmmesso.TXT));
        assertEquals(primaDeiDinieghi, comunicazioni.consultaOk(banco, id),
                "la comunicazione riletta dal Banco è identica a prima dei dinieghi");
    }
}
