package it.unina.picus.monitoraggio.interno.applicazione;

import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.RiepilogoTrimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili.VersioneInviata;
import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.RegoleAccessoScheda;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoSchedaOperatore;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

/**
 * Caso d'uso di riepilogo trimestrale delle schede personali per il Banco (#181, #57, #41).
 * Distingue la configurazione pubblicata del singolo trimestre, l'associazione storica a
 * {@code v0.1} dei periodi già rendicontati e il trimestre privo di pubblicazione.
 */
public final class ServizioRiepilogoSchede {

    private static final BinaryOperator<VersioneInviata> VERSIONE_PIU_RECENTE =
            BinaryOperator.maxBy(Comparator.comparingLong(VersioneInviata::numeroVersione));
    private static final Comparator<SchedaSintesi> PER_INVIO =
            Comparator.comparing(SchedaSintesi::inviataIl).thenComparing(SchedaSintesi::id);

    private final ArchivioSchedeRendicontabili archivio;
    private final CategorieSchedeRendicontabili categorie;

    /**
     * Crea il caso d'uso a partire dall'archivio delle schede e dalla fonte delle configurazioni per trimestre.
     *
     * @param archivio sorgente delle versioni inviate e accettate
     * @param categorie fonte autorizzata delle versioni di categorie per trimestre (TM-OP-01)
     */
    public ServizioRiepilogoSchede(
            ArchivioSchedeRendicontabili archivio,
            CategorieSchedeRendicontabili categorie) {
        this.archivio = Objects.requireNonNull(archivio, "archivio");
        this.categorie = Objects.requireNonNull(categorie, "categorie");
    }

    /**
     * Usa l'ultima versione accettata per operatore e somma i conteggi dichiarati secondo la
     * configurazione risolta per il trimestre richiesto:
     * <ul>
     *   <li>{@link ConfigurazioneTrimestre.Pubblicata}: ammette esclusivamente i codici della
     *   versione immutabile pubblicata per quel trimestre; modifiche o disattivazioni pubblicate
     *   per trimestri successivi non alterano questo riepilogo;</li>
     *   <li>{@link ConfigurazioneTrimestre.LegacyProvvisoria}: periodo già rendicontato prima
     *   della migrazione e associato esplicitamente al tracciato provvisorio {@code v0.1}, che
     *   ammette i codici sintatticamente validi;</li>
     *   <li>{@link ConfigurazioneTrimestre.NonConfigurata}: se il Banco ha già pubblicato almeno
     *   una versione per il tracciato non aggrega voci non pubblicate; finché nessuna versione è
     *   stata ancora pubblicata resta attivo in via transitoria il filtro sintattico {@code v0.1}
     *   senza registrare nuove associazioni legacy.</li>
     * </ul>
     *
     * @param attore identità verificata del Banco
     * @param trimestre singolo periodo da riepilogare
     * @return sintesi ordinate per istante di invio della versione usata e totali per categoria
     * @throws SecurityException se l'attore non è autorizzato
     * @throws NullPointerException se manca il trimestre
     * @throws IllegalStateException se l'archivio restituisce un progressivo duplicato per
     *                               lo stesso operatore nel trimestre richiesto
     * @see MonitoraggioApi#riepilogaSchede
     */
    public RiepilogoTrimestre riepilogaSchede(IdentitaAutenticata attore, Trimestre trimestre) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        Objects.requireNonNull(trimestre, "trimestre");

        ConfigurazioneTrimestre configurazione = categorie.configurazionePer(trimestre);
        Predicate<String> ammessa = switch (configurazione) {
            case ConfigurazioneTrimestre.Pubblicata pubblicata -> {
                Set<String> ammesse = pubblicata.versione().codiciAmmessi();
                yield codice -> ammesse.contains(codice) && TracciatoSchedaOperatore.codiceCategoriaValido(codice);
            }
            case ConfigurazioneTrimestre.LegacyProvvisoria ignored -> TracciatoSchedaOperatore::codiceCategoriaValido;
            case ConfigurazioneTrimestre.NonConfigurata nonConfigurata ->
                    nonConfigurata.transitorioSenzaPubblicazioni()
                            ? TracciatoSchedaOperatore::codiceCategoriaValido
                            : codice -> false;
        };

        Map<UUID, VersioneInviata> ultimeVersioni = new HashMap<>();
        Map<UUID, Set<Long>> progressiviPerOperatore = new HashMap<>();
        for (VersioneInviata versione : archivio.elencaVersioniInviate(trimestre)) {
            SchedaSintesi sintesi = versione.sintesi();
            if (!trimestre.equals(sintesi.trimestre())) {
                continue;
            }
            UUID operatoreId = sintesi.operatoreId();
            Set<Long> progressivi = progressiviPerOperatore.computeIfAbsent(operatoreId, id -> new HashSet<>());
            if (!progressivi.add(versione.numeroVersione())) {
                throw new IllegalStateException("Progressivo duplicato per lo stesso operatore nel trimestre richiesto");
            }
            ultimeVersioni.merge(operatoreId, versione, VERSIONE_PIU_RECENTE);
        }

        Map<String, BigInteger> totali = new HashMap<>();
        for (VersioneInviata versione : ultimeVersioni.values()) {
            versione.conteggiDichiarati().forEach((categoria, valore) -> {
                if (ammessa.test(categoria)) {
                    totali.merge(categoria, BigInteger.valueOf(valore), BigInteger::add);
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
