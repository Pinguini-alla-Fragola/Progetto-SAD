package it.unina.picus.monitoraggio.interno.applicazione;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
import it.unina.picus.monitoraggio.interno.dominio.RegoleAccessoConteggi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

@Service
@Profile("skeleton")
public class SkeletonMonitoraggioService implements ConteggiOptApi {

    private final Map<String, Long> archivio = new ConcurrentHashMap<>();

    @Override
    public void registraConteggiOpt(IdentitaAutenticata attore, Trimestre trimestre, List<VoceMisura> conteggi) {
        RegoleAccessoConteggi.verificaRegistrazione(attore);
        if (trimestre == null || conteggi == null || conteggi.isEmpty()) {
            throw new DatiConteggiNonValidiException("Dati mancanti.", Map.of("voci", "Inserisci almeno una voce."));
        }
        for (VoceMisura v : conteggi) {
            if (v.valore() < 0 || v.voce() == null || v.voce().isBlank()) {
                throw new DatiConteggiNonValidiException("Dati non validi.", Map.of("voci", "Controlla voce e valore."));
            }
            String key = attore.accountId() + "|" + trimestre.anno() + "|" + trimestre.numero() + "|" + v.misuraId() + "|" + v.voce().trim();
            Long prev = archivio.get(key);
            if (prev != null && prev != v.valore()) {
                throw new IllegalStateException("Correzioni disattivate.");
            }
            archivio.put(key, v.valore());
        }
    }

    @Override
    public List<ConteggioOpt> consultaConteggiOpt(IdentitaAutenticata attore, UUID optId, Trimestre da, Trimestre a) {
        RegoleAccessoConteggi.verificaConsultazione(attore, optId);
        return List.of();
    }
}