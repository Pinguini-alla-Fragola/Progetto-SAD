package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.comunicazioni.interno.dominio.AllegatoRicevuto;
import it.unina.picus.comunicazioni.interno.dominio.BozzaComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.LetturaGruppo;
import it.unina.picus.comunicazioni.interno.dominio.RegolaDestinatari;

/** Ripulisce i campi ricevuti, così che le regole lavorino sempre su dati nella stessa forma. */
final class NormalizzatoreComunicazione {

    private NormalizzatoreComunicazione() {
    }

    static BozzaComunicazione normalizza(ComandoInvioComunicazione comando) {
        if (comando == null) {
            return new BozzaComunicazione(null, null, null, Set.of(), LetturaGruppo.LEGGIBILE, List.of());
        }
        Set<UUID> gruppo = new HashSet<>();
        LetturaGruppo lettura = leggiGruppo(comando.gruppo(), gruppo);
        return new BozzaComunicazione(testo(comando.titolo()), testo(comando.contenuto()),
                tipo(comando.destinatariTipo()), gruppo, lettura, allegati(comando.allegati()));
    }

    /** Toglie gli spazi ai bordi, lasciando com'è il testo interno; un testo vuoto diventa {@code null}. */
    private static String testo(String valore) {
        if (valore == null) {
            return null;
        }
        String ripulito = valore.strip();
        return ripulito.isEmpty() ? null : ripulito;
    }

    /**
     * Converte il nome del tipo di destinatari.
     *
     * @return il tipo, oppure {@code null} se manca o non esiste
     */
    static TipoDestinatari tipo(String valore) {
        String nome = testo(valore);
        if (nome == null) {
            return null;
        }
        try {
            return TipoDestinatari.valueOf(nome);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Converte gli identificativi del gruppo. Gli elementi ricevuti si contano prima di convertirli e di togliere
     * le ripetizioni: oltre il limite non se ne converte nessuno.
     */
    private static LetturaGruppo leggiGruppo(List<String> valori, Set<UUID> gruppo) {
        if (valori == null) {
            return LetturaGruppo.LEGGIBILE;
        }
        if (valori.size() > RegolaDestinatari.MASSIMO_GRUPPO) {
            return LetturaGruppo.TROPPO_GRANDE;
        }
        for (String valore : valori) {
            try {
                gruppo.add(UUID.fromString(valore.strip()));
            } catch (IllegalArgumentException | NullPointerException e) {
                return LetturaGruppo.ILLEGGIBILE;
            }
        }
        return LetturaGruppo.LEGGIBILE;
    }

    /** Tiene del nome del file solo l'ultima parte: un nome come {@code ../../segreto.txt} diventa {@code segreto.txt}. */
    private static List<AllegatoRicevuto> allegati(List<AllegatoRicevuto> allegati) {
        if (allegati == null) {
            return List.of();
        }
        return allegati.stream().filter(Objects::nonNull)
                .map(allegato -> new AllegatoRicevuto(nomeFile(allegato.nomeFile()), allegato.contenuto()))
                .toList();
    }

    private static String nomeFile(String nome) {
        if (nome == null) {
            return null;
        }
        int separatore = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\'));
        return testo(nome.substring(separatore + 1));
    }
}
