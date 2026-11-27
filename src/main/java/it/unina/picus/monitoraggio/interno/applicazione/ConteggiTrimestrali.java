package it.unina.picus.monitoraggio.interno.applicazione;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
import it.unina.picus.monitoraggio.interno.applicazione.ConsultazioneConteggiOpt.ConteggioEsatto;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

final class ConteggiTrimestrali {

    private static final int ANNO_MINIMO = 2000;
    private static final int ANNO_MASSIMO = 2100;
    private static final BigInteger MASSIMO_CONTEGGIO_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    static final Comparator<Trimestre> ORDINE_PERIODI =
            Comparator.comparingInt(Trimestre::anno).thenComparingInt(Trimestre::numero);

    private ConteggiTrimestrali() {}

    static void validaPeriodo(Trimestre periodo, String campoAnno, String campoTrimestre) {
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

    static void validaIntervallo(Trimestre da, Trimestre a) {
        if (da == null || a == null) {
            throw new IllegalArgumentException("Periodo iniziale e finale obbligatori.");
        }
        validaPeriodo(da, "anno", "trimestre");
        validaPeriodo(a, "annoFine", "trimestreFine");
        if (ORDINE_PERIODI.compare(da, a) > 0) {
            throw new IllegalArgumentException("Il trimestre iniziale non può essere successivo a quello finale.");
        }
    }

    static void verificaOpt(UtenzeApi utenze, UUID optId) {
        if (optId != null) {
            var account = utenze.cercaAccount(optId)
                    .orElseThrow(() -> new NoSuchElementException("Organizzazione partner non trovata."));
            if (account.ruolo() != RuoloUtente.OPT) {
                throw new NoSuchElementException("L'account indicato non è una OPT.");
            }
        }
    }

    /**
     * Somma le dichiarazioni correnti per OPT, misura e voce. Una chiave nasce solo da una
     * dichiarazione effettiva, perciò uno zero dichiarato compare e una voce mancante resta assente.
     *
     * @param righe valori correnti dei trimestri selezionati
     * @return totali esatti nell'ordine in cui le chiavi compaiono nelle righe
     */
    static List<ConteggioEsatto> cumula(Iterable<ConteggioOpt> righe) {
        record Chiave(UUID optId, long misuraId, String voce) {}
        Map<Chiave, BigInteger> cumulativi = new LinkedHashMap<>();

        for (ConteggioOpt riga : righe) {
            VoceMisura voce = riga.conteggio();
            var chiave = new Chiave(riga.optId(), voce.misuraId(), voce.voce());
            cumulativi.merge(chiave, BigInteger.valueOf(voce.valore()), BigInteger::add);
        }

        return cumulativi.entrySet().stream()
                .map(entry -> {
                    var chiave = entry.getKey();
                    return new ConteggioEsatto(chiave.optId(), chiave.misuraId(), chiave.voce(), entry.getValue());
                })
                .toList();
    }

    /**
     * Converte i totali nel tipo con valore {@code long} del contratto applicativo esistente.
     *
     * @param totali conteggi esatti del periodo
     * @return conteggi rappresentabili nel contratto applicativo
     * @throws IllegalStateException se un totale supera il limite del tipo restituito
     */
    static List<ConteggioOpt> convertiInConteggi(List<ConteggioEsatto> totali) {
        return totali.stream().map(totale -> {
            if (totale.valore().compareTo(MASSIMO_CONTEGGIO_LONG) > 0) {
                throw new IllegalStateException(
                        "Il totale cumulativo per la voce " + totale.voce() + " supera il limite massimo rappresentabile.");
            }
            return new ConteggioOpt(totale.optId(),
                    new VoceMisura(totale.misuraId(), totale.voce(), totale.valore().longValueExact()));
        }).toList();
    }
}
