package it.unina.picus.monitoraggio.interno.applicazione;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
import it.unina.picus.monitoraggio.interno.dominio.RegoleAccessoConteggi;
import it.unina.picus.monitoraggio.interno.persistenza.CategoriaMonitoraggioJpa;
import it.unina.picus.monitoraggio.interno.persistenza.CategoriaMonitoraggioRepository;
import it.unina.picus.monitoraggio.interno.persistenza.ConfigurazioneMonitoraggioJpa;
import it.unina.picus.monitoraggio.interno.persistenza.ConfigurazioneMonitoraggioRepository;
import it.unina.picus.monitoraggio.interno.persistenza.ConteggioOptJpa;
import it.unina.picus.monitoraggio.interno.persistenza.ConteggioOptRepository;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.AccountSintetico;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

@Service
@Profile("!skeleton")
public class MonitoraggioOptService implements ConteggiOptApi {

    public static final int ANNO_MINIMO = 2000;
    public static final int ANNO_MASSIMO = 2100;
    private static final BigInteger MAX_CUMULATIVO = BigInteger.valueOf(Long.MAX_VALUE);

    private final ConteggioOptRepository conteggiRepo;
    private final CategoriaMonitoraggioRepository categorieRepo;
    private final ConfigurazioneMonitoraggioRepository configRepo;
    private final ConsultazioneCatalogoApi catalogo;
    private final UtenzeApi utenze;
    private final Clock clock;

    public MonitoraggioOptService(
            ConteggioOptRepository conteggiRepo,
            CategoriaMonitoraggioRepository categorieRepo,
            ConfigurazioneMonitoraggioRepository configRepo,
            ConsultazioneCatalogoApi catalogo,
            UtenzeApi utenze,
            Optional<Clock> clock) {
        this.conteggiRepo = conteggiRepo;
        this.categorieRepo = categorieRepo;
        this.configRepo = configRepo;
        this.catalogo = catalogo;
        this.utenze = utenze;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaVoce> elencoCategorieAttive() {
        return categorieRepo.findByAttivaTrueOrderByCodiceAsc().stream()
                .map(c -> new CategoriaVoce(c.getCodice(), c.getDescrizione()))
                .toList();
    }

    @Override
    @Transactional
    public void registraConteggiOpt(IdentitaAutenticata attore, Trimestre trimestre, List<VoceMisura> conteggi) {
        RegoleAccessoConteggi.verificaRegistrazione(attore);

        if (trimestre == null) {
            throw new DatiConteggiNonValidiException(
                    "Trimestre obbligatorio.",
                    Map.of("trimestre", "Seleziona anno e trimestre."));
        }
        validaPeriodo(trimestre, "anno", "trimestre");

        if (conteggi == null || conteggi.isEmpty()) {
            throw new DatiConteggiNonValidiException(
                    "Inserisci almeno una voce di conteggio.",
                    Map.of("voci", "Inserisci almeno una voce valida."));
        }

        Map<String, String> errori = new LinkedHashMap<>();
        Set<String> chiaviNelBatch = new HashSet<>();
        Set<String> categorieAttive = categorieRepo.findByAttivaTrueOrderByCodiceAsc().stream()
                .map(CategoriaMonitoraggioJpa::getCodice)
                .collect(Collectors.toSet());
        Map<Long, Boolean> misureCache = new HashMap<>();

        for (int i = 0; i < conteggi.size(); i++) {
            VoceMisura item = conteggi.get(i);
            if (item == null) {
                errori.put("voci[" + i + "]", "Voce non valida.");
                continue;
            }
            if (item.misuraId() <= 0 || !misuraEsiste(item.misuraId(), misureCache)) {
                errori.put("misuraId", "La misura selezionata non è valida.");
            }
            String codiceVoce = item.voce() == null ? "" : item.voce().trim();
            if (codiceVoce.isEmpty() || !categorieAttive.contains(codiceVoce)) {
                errori.put("voci[" + i + "].voce", "Voce del tracciato non valida o non attiva.");
            }
            if (item.valore() < 0) {
                errori.put("voci[" + i + "].valore", "Il conteggio non può essere negativo (usa 0 per dichiarare zero).");
            }
            String chiaveDuplicato = item.misuraId() + "::" + codiceVoce;
            if (!chiaviNelBatch.add(chiaveDuplicato)) {
                errori.put("voci[" + i + "].voce", "Voce duplicata per la stessa misura nel medesimo invio.");
            }
        }

        if (!errori.isEmpty()) {
            throw new DatiConteggiNonValidiException("Controlla i campi dei conteggi trimestrali.", errori);
        }

        Instant adesso = Instant.now(clock);
        UUID optId = attore.accountId();

        // Invii equivalenti acquisiscono i lock delle voci nello stesso ordine.
        var vociOrdinate = conteggi.stream()
                .sorted(Comparator.comparingLong(VoceMisura::misuraId).thenComparing(voce -> voce.voce().trim()))
                .toList();
        for (VoceMisura item : vociOrdinate) {
            String voce = item.voce().trim();
            int inseriti = conteggiRepo.inserisciSeAssente(
                    UUID.randomUUID(), optId, trimestre.anno(), trimestre.numero(),
                    item.misuraId(), voce, item.valore(), adesso);

            if (inseriti == 0) {
                ConteggioOptJpa esistente = conteggiRepo
                        .findByOptIdAndAnnoAndTrimestreAndMisuraIdAndVoce(
                                optId, trimestre.anno(), trimestre.numero(), item.misuraId(), voce)
                        .orElseThrow(() -> new IllegalStateException("Conflitto concorrente durante la lettura del conteggio."));

                if (esistente.getValore() == item.valore()) {
                    continue;
                }
                verificaCorrezioneConsentita(adesso);
                esistente.aggiornaValore(item.valore(), adesso);
                conteggiRepo.save(esistente);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConteggioOpt> consultaConteggiOpt(IdentitaAutenticata attore, UUID optId, Trimestre da, Trimestre a) {
        RegoleAccessoConteggi.verificaConsultazione(attore, optId);

        if (da == null || a == null) {
            throw new IllegalArgumentException("Periodo iniziale e finale obbligatori.");
        }
        validaPeriodo(da, "anno", "trimestre");
        validaPeriodo(a, "annoFine", "trimestreFine");

        if (da.anno() > a.anno() || (da.anno() == a.anno() && da.numero() > a.numero())) {
            throw new IllegalArgumentException("Il trimestre iniziale non può essere successivo a quello finale.");
        }

        if (optId != null) {
            AccountSintetico account = utenze.cercaAccount(optId)
                    .orElseThrow(() -> new NoSuchElementException("Organizzazione partner non trovata."));
            if (account.ruolo() != RuoloUtente.OPT) {
                throw new NoSuchElementException("L'account indicato non è una OPT.");
            }
        }

        List<ConteggioOptJpa> righe = conteggiRepo.cercaNelPeriodo(
                optId, da.anno(), da.numero(), a.anno(), a.numero());

        record ChiaveAggregazione(UUID optId, long misuraId, String voce) {}
        Map<ChiaveAggregazione, BigInteger> cumulativi = new LinkedHashMap<>();

        for (ConteggioOptJpa riga : righe) {
            ChiaveAggregazione chiave = new ChiaveAggregazione(riga.getOptId(), riga.getMisuraId(), riga.getVoce());
            BigInteger totaleAggiornato = cumulativi
                    .getOrDefault(chiave, BigInteger.ZERO)
                    .add(BigInteger.valueOf(riga.getValore()));
            if (totaleAggiornato.compareTo(MAX_CUMULATIVO) > 0) {
                throw new IllegalStateException(
                        "Il totale cumulativo per la voce " + riga.getVoce() + " supera il limite massimo rappresentabile.");
            }
            cumulativi.put(chiave, totaleAggiornato);
        }

        return cumulativi.entrySet().stream()
                .map(entry -> new ConteggioOpt(
                        entry.getKey().optId(),
                        new VoceMisura(
                                entry.getKey().misuraId(),
                                entry.getKey().voce(),
                                entry.getValue().longValueExact())))
                .toList();
    }

    private void validaPeriodo(Trimestre periodo, String campoAnno, String campoTrimestre) {
        Map<String, String> errori = new LinkedHashMap<>();
        if (periodo.anno() < ANNO_MINIMO || periodo.anno() > ANNO_MASSIMO) {
            errori.put(campoAnno, "L'anno deve essere compreso tra " + ANNO_MINIMO + " e " + ANNO_MASSIMO + ".");
        }
        if (periodo.numero() < 1 || periodo.numero() > 4) {
            errori.put(campoTrimestre, "Il trimestre deve essere compreso tra 1 e 4.");
        }
        if (!errori.isEmpty()) {
            throw new DatiConteggiNonValidiException("Periodo di riferimento non valido.", errori);
        }
    }

    private boolean misuraEsiste(long misuraId, Map<Long, Boolean> cache) {
        return cache.computeIfAbsent(misuraId, id -> catalogo.cercaMisura(id).isPresent());
    }

    private void verificaCorrezioneConsentita(Instant adesso) {
        ConfigurazioneMonitoraggioJpa config = configRepo.findById((short) 1).orElse(null);
        if (config == null || !config.isCorrezioniAbilitate()) {
            throw new IllegalStateException(
                    "I conteggi per questa voce risultano già registrati e le correzioni sono disattivate dal Banco.");
        }
        if (config.getScadenzaCorrezioni() != null && adesso.isAfter(config.getScadenzaCorrezioni())) {
            throw new IllegalStateException(
                    "Il termine fissato dal Banco per le correzioni dei conteggi è scaduto.");
        }
    }
}