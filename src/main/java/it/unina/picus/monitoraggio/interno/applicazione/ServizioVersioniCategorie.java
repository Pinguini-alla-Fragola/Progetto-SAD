package it.unina.picus.monitoraggio.interno.applicazione;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioVersioniCategorie;
import it.unina.picus.monitoraggio.interno.dominio.AssegnazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.DatiSchedaNonValidiException;
import it.unina.picus.monitoraggio.interno.dominio.RegoleAccessoScheda;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoMonitoraggio;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoSchedaOperatore;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.VoceCategoriaVersione;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Caso d'uso del Banco per preparare bozze di categorie, validarne codici/etichette/definizioni
 * e pubblicare versioni immutabili per singolo trimestre (#41, #181).
 */
@Service
@Profile("!skeleton")
@Transactional(readOnly = true)
public class ServizioVersioniCategorie {

    private final ArchivioVersioniCategorie archivio;
    private final Clock orologio;

    /**
     * Crea il servizio applicativo per la gestione delle versioni di categorie.
     *
     * @param archivio persistenza delle versioni e associazioni trimestrali
     * @param orologio sorgente temporale per creazione e pubblicazione
     */
    public ServizioVersioniCategorie(ArchivioVersioniCategorie archivio, Clock orologio) {
        this.archivio = Objects.requireNonNull(archivio, "archivio");
        this.orologio = Objects.requireNonNull(orologio, "orologio");
    }

    /**
     * Crea una bozza modificabile per il tracciato richiesto.
     *
     * @param attore identità verificata del Banco
     * @param tracciato tracciato di destinazione ({@code TM-OP-01} o {@code TM-OPT-01})
     * @param codiceVersione codice univoco della versione nel tracciato
     * @param descrizione descrizione della versione
     * @param voci elenco ordinato delle categorie
     * @return versione creata in stato {@code BOZZA}
     */
    @Transactional
    public VersioneCategorie creaBozza(
            IdentitaAutenticata attore,
            TracciatoMonitoraggio tracciato,
            String codiceVersione,
            String descrizione,
            List<VoceDraft> voci) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        Map<String, String> errori = new LinkedHashMap<>();
        if (tracciato == null) {
            errori.put("tracciato", "Indica un tracciato valido");
        }
        String codicePulito = codiceVersione == null ? "" : codiceVersione.trim();
        if (!TracciatoSchedaOperatore.codiceCategoriaValido(codicePulito)) {
            errori.put("codiceVersione", "Usa un codice versione valido (1..64 caratteri [A-Za-z0-9_.-])");
        }
        String descrPulita = validaTesto(descrizione, 255, "descrizione", errori,
                "Indica una descrizione valida della versione");
        List<VoceCategoriaVersione> vociValide = validaVoci(voci, errori);
        if (!errori.isEmpty()) {
            throw new DatiSchedaNonValidiException(errori);
        }
        return archivio.creaBozza(tracciato, codicePulito, descrPulita, vociValide, orologio.instant());
    }

    /**
     * Aggiorna una bozza prima della pubblicazione; rifiuta versioni già pubblicate o legacy.
     *
     * @param attore identità verificata del Banco
     * @param versioneId identificativo della versione in bozza
     * @param descrizione nuova descrizione
     * @param voci nuovo elenco completo delle voci
     * @return versione aggiornata
     */
    @Transactional
    public VersioneCategorie aggiornaBozza(
            IdentitaAutenticata attore,
            UUID versioneId,
            String descrizione,
            List<VoceDraft> voci) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        if (versioneId == null) {
            throw new DatiSchedaNonValidiException(Map.of("versioneId", "Indica la versione da aggiornare"));
        }
        Map<String, String> errori = new LinkedHashMap<>();
        String descrPulita = validaTesto(descrizione, 255, "descrizione", errori,
                "Indica una descrizione valida della versione");
        List<VoceCategoriaVersione> vociValide = validaVoci(voci, errori);
        if (!errori.isEmpty()) {
            throw new DatiSchedaNonValidiException(errori);
        }
        return archivio.aggiornaBozza(versioneId, descrPulita, vociValide);
    }

    /**
     * Pubblica in modo immutabile la versione indicata per un singolo trimestre.
     *
     * @param attore identità verificata del Banco
     * @param versioneId identificativo della versione da pubblicare o riusare
     * @param trimestre periodo di destinazione
     * @return configurazione immutabile associata al trimestre
     */
    @Transactional
    public ConfigurazioneTrimestre.Pubblicata pubblicaPerTrimestre(
            IdentitaAutenticata attore,
            UUID versioneId,
            Trimestre trimestre) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        if (versioneId == null || trimestre == null) {
            Map<String, String> errori = new LinkedHashMap<>();
            if (versioneId == null) errori.put("versioneId", "Indica una versione da pubblicare");
            if (trimestre == null) errori.put("trimestre", "Indica anno e trimestre di destinazione");
            throw new DatiSchedaNonValidiException(errori);
        }
        return archivio.pubblicaPerTrimestre(versioneId, trimestre, orologio.instant());
    }

    /** Elenca tutte le versioni censite per il tracciato richiesto. */
    public List<VersioneCategorie> elencaVersioni(IdentitaAutenticata attore, TracciatoMonitoraggio tracciato) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        Objects.requireNonNull(tracciato, "tracciato");
        return archivio.elencaVersioni(tracciato);
    }

    /** Recupera il dettaglio completo di una versione per identificativo. */
    public VersioneCategorie dettaglioVersione(IdentitaAutenticata attore, UUID versioneId) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        Objects.requireNonNull(versioneId, "versioneId");
        return archivio.trovaVersione(versioneId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Versione categorie non trovata"));
    }

    /** Elenca tutte le associazioni trimestre -> versione per il tracciato richiesto. */
    public List<AssegnazioneTrimestre> elencaAssegnazioni(IdentitaAutenticata attore, TracciatoMonitoraggio tracciato) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        Objects.requireNonNull(tracciato, "tracciato");
        return archivio.elencaAssegnazioni(tracciato);
    }

    /** Legge la configurazione risolta (pubblicata, legacy o assente) per il trimestre richiesto. */
    public ConfigurazioneTrimestre configurazioneTrimestre(
            IdentitaAutenticata attore,
            TracciatoMonitoraggio tracciato,
            Trimestre trimestre) {
        RegoleAccessoScheda.verificaRiepilogo(attore);
        Objects.requireNonNull(tracciato, "tracciato");
        Objects.requireNonNull(trimestre, "trimestre");
        return archivio.configurazionePer(tracciato, trimestre);
    }

    private static String validaTesto(
            String valore, int massimo, String campo, Map<String, String> errori, String messaggio) {
        if (valore == null) {
            errori.put(campo, messaggio);
            return "";
        }
        String pulito = valore.trim();
        if (pulito.isEmpty() || pulito.length() > massimo) {
            errori.put(campo, messaggio);
        }
        return pulito;
    }

    private static List<VoceCategoriaVersione> validaVoci(List<VoceDraft> voci, Map<String, String> errori) {
        if (voci == null || voci.isEmpty()) {
            errori.put("voci", "Inserisci almeno una categoria");
            return List.of();
        }
        List<VoceCategoriaVersione> risultato = new ArrayList<>(voci.size());
        Set<String> codiciVisti = new HashSet<>();
        for (int i = 0; i < voci.size(); i++) {
            VoceDraft draft = voci.get(i);
            if (draft == null) {
                errori.put("voci", "Ogni categoria deve essere valorizzata");
                return List.of();
            }
            String codice = draft.codice() == null ? "" : draft.codice().trim();
            if (!TracciatoSchedaOperatore.codiceCategoriaValido(codice)) {
                errori.put("voci", "Ogni categoria richiede un codice valido");
                return List.of();
            }
            if (!codiciVisti.add(codice)) {
                errori.put("voci", "Ogni codice di categoria deve comparire una sola volta nella versione");
                return List.of();
            }
            String descr = draft.descrizione() == null ? "" : draft.descrizione().trim();
            String def = draft.definizione() == null ? "" : draft.definizione().trim();
            if (descr.isEmpty() || descr.length() > 255 || def.isEmpty() || def.length() > 1000) {
                errori.put("voci", "Ogni categoria richiede etichetta e definizione non vuote");
                return List.of();
            }
            int ordine = draft.ordine() != null ? draft.ordine() : i;
            if (ordine < 0) {
                errori.put("voci", "L'ordine delle categorie non può essere negativo");
                return List.of();
            }
            risultato.add(new VoceCategoriaVersione(codice, descr, def, ordine));
        }
        return List.copyOf(risultato);
    }

    /** Dati di una voce ricevuta in bozza dal Banco. */
    public record VoceDraft(String codice, String descrizione, String definizione, Integer ordine) {}
}
