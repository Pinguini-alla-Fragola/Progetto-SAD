package it.unina.picus.monitoraggio.interno.applicazione;

import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.RiepilogoTrimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili.VersioneInviata;
import it.unina.picus.monitoraggio.interno.dominio.RegoleAccessoScheda;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BinaryOperator;

/** Caso d'uso di riepilogo trimestrale delle schede personali per il Banco (#181). */
public final class ServizioRiepilogoSchede {

    private static final BinaryOperator<VersioneInviata> VERSIONE_PIU_RECENTE =
            BinaryOperator.maxBy(Comparator.comparingLong(VersioneInviata::numeroVersione));
    private static final Comparator<SchedaSintesi> PER_INVIO =
            Comparator.comparing(SchedaSintesi::inviataIl).thenComparing(SchedaSintesi::id);

    private final ArchivioSchedeRendicontabili archivio;

    /**
     * Crea il caso d'uso a partire dall'archivio di lettura delle schede.
     *
     * @param archivio sorgente delle versioni inviate e accettate
     */
    public ServizioRiepilogoSchede(ArchivioSchedeRendicontabili archivio) {
        this.archivio = Objects.requireNonNull(archivio, "archivio");
    }

    /**
     * Usa l'ultima versione accettata per operatore e somma i conteggi dichiarati per codice
     * di categoria ammesso dal tracciato dell'invio. Le categorie sovrapponibili restano
     * distinte e una correzione sostituisce l'intero invio precedente. Uno zero dichiarato
     * compare fra i totali, mentre una categoria mai dichiarata è assente.
     *
     * @param attore identità verificata del Banco
     * @param trimestre singolo periodo da riepilogare
     * @return sintesi degli invii in ordine cronologico e totali per categoria
     * @throws SecurityException se l'attore non è autorizzato
     * @throws NullPointerException se manca il trimestre
     * @throws ArithmeticException se un totale supera la capacità del tipo {@code long}
     * @see MonitoraggioApi#riepilogaSchede
     */
    public RiepilogoTrimestre riepilogaSchede(IdentitaAutenticata attore, Trimestre trimestre) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        Objects.requireNonNull(trimestre, "trimestre");

        Map<UUID, VersioneInviata> ultimeVersioni = new HashMap<>();
        for (VersioneInviata versione : archivio.elencaVersioniInviate(trimestre)) {
            SchedaSintesi sintesi = versione.sintesi();
            if (trimestre.equals(sintesi.trimestre())) {
                ultimeVersioni.merge(sintesi.operatoreId(), versione, VERSIONE_PIU_RECENTE);
            }
        }

        Map<String, Long> totali = new HashMap<>();
        for (VersioneInviata versione : ultimeVersioni.values()) {
            versione.conteggiDichiarati().forEach((categoria, valore) -> {
                if (versione.categorieTracciato().contains(categoria)) {
                    totali.merge(categoria, valore, Math::addExact);
                }
            });
        }

        List<SchedaSintesi> schede = ultimeVersioni.values().stream()
                .map(VersioneInviata::sintesi)
                .sorted(PER_INVIO)
                .toList();
        return new RiepilogoTrimestre(schede, totali);
    }
}
