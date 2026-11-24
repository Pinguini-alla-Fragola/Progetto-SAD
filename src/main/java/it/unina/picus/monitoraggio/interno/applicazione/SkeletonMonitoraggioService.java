package it.unina.picus.monitoraggio.interno.applicazione;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
import it.unina.picus.monitoraggio.interno.dominio.RegoleAccessoConteggi;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

@Service
@Profile("skeleton")
public class SkeletonMonitoraggioService implements ConteggiOptApi {

    private record Chiave(UUID optId, Trimestre periodo, long misuraId, String voce) {}

    private static final List<CategoriaVoce> CATEGORIE = List.of(
            new CategoriaVoce("ANZIANI", "Anziani raggiunti"),
            new CategoriaVoce("MINORI", "Minori raggiunti"),
            new CategoriaVoce("NUCLEI_FAMILIARI", "Nuclei familiari raggiunti"),
            new CategoriaVoce("PERSONE_RAGGIUNTE", "Persone raggiunte nel trimestre"));

    private final Map<Chiave, Long> archivio = new HashMap<>();
    private final UtenzeApi utenze;

    public SkeletonMonitoraggioService(UtenzeApi utenze) {
        this.utenze = utenze;
    }

    @Override
    public List<CategoriaVoce> elencoCategorieAttive() {
        return CATEGORIE;
    }

    @Override
    public synchronized void registraConteggiOpt(IdentitaAutenticata attore, Trimestre trimestre, List<VoceMisura> conteggi) {
        RegoleAccessoConteggi.verificaRegistrazione(attore);
        if (trimestre == null) {
            throw new DatiConteggiNonValidiException("Trimestre obbligatorio.",
                    Map.of("trimestre", "Seleziona anno e trimestre."));
        }
        ConteggiTrimestrali.validaPeriodo(trimestre, "anno", "trimestre");
        if (conteggi == null || conteggi.isEmpty()) {
            throw new DatiConteggiNonValidiException("Dati mancanti.", Map.of("voci", "Inserisci almeno una voce."));
        }

        Map<Chiave, Long> dichiarazione = new HashMap<>();
        for (VoceMisura v : conteggi) {
            if (v == null || v.misuraId() <= 0 || v.valore() < 0 || v.voce() == null
                    || CATEGORIE.stream().noneMatch(categoria -> categoria.codice().equals(v.voce().trim()))) {
                throw new DatiConteggiNonValidiException("Dati non validi.", Map.of("voci", "Controlla misura, voce e valore."));
            }
            var chiave = new Chiave(attore.accountId(), trimestre, v.misuraId(), v.voce().trim());
            if (dichiarazione.putIfAbsent(chiave, v.valore()) != null) {
                throw new DatiConteggiNonValidiException("Voce duplicata.",
                        Map.of("voci", "Dichiara una sola volta ogni voce della misura."));
            }
        }
        dichiarazione.forEach((chiave, valore) -> {
            Long precedente = archivio.get(chiave);
            if (precedente != null && !precedente.equals(valore)) {
                throw new IllegalStateException("Correzioni disattivate.");
            }
        });
        archivio.putAll(dichiarazione);
    }

    @Override
    public synchronized List<ConteggioOpt> consultaConteggiOpt(
            IdentitaAutenticata attore, UUID optId, Trimestre da, Trimestre a) {
        RegoleAccessoConteggi.verificaConsultazione(attore, optId);
        ConteggiTrimestrali.validaIntervallo(da, a);
        ConteggiTrimestrali.verificaOpt(utenze, optId);

        var ordine = ConteggiTrimestrali.ORDINE_PERIODI;
        var righe = archivio.entrySet().stream()
                .filter(entry -> {
                    var chiave = entry.getKey();
                    return (optId == null || optId.equals(chiave.optId()))
                            && ordine.compare(da, chiave.periodo()) <= 0 && ordine.compare(chiave.periodo(), a) <= 0;
                })
                .map(entry -> {
                    var chiave = entry.getKey();
                    return new ConteggioOpt(chiave.optId(),
                            new VoceMisura(chiave.misuraId(), chiave.voce(), entry.getValue()));
                })
                .sorted(Comparator.comparing((ConteggioOpt riga) -> riga.optId().toString())
                        .thenComparingLong(riga -> riga.conteggio().misuraId())
                        .thenComparing(riga -> riga.conteggio().voce()))
                .toList();
        return ConteggiTrimestrali.cumula(righe);
    }
}
