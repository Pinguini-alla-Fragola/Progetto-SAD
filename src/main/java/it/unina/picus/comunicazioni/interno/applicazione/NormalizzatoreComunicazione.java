package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.comunicazioni.interno.dominio.BozzaComunicazione;

/** Ripulisce i campi ricevuti, così che le regole lavorino sempre su dati nella stessa forma. */
final class NormalizzatoreComunicazione {

    private NormalizzatoreComunicazione() {
    }

    static BozzaComunicazione normalizza(ComandoInvioComunicazione comando) {
        if (comando == null) {
            return new BozzaComunicazione(null, null, null, Set.of(), true);
        }
        Set<UUID> gruppo = new HashSet<>();
        boolean gruppoLeggibile = leggiGruppo(comando.gruppo(), gruppo);
        return new BozzaComunicazione(testo(comando.titolo()), testo(comando.contenuto()),
                tipo(comando.destinatariTipo()), gruppo, gruppoLeggibile);
    }

    /** Toglie gli spazi ai bordi, lasciando com'è il testo interno; un testo vuoto diventa {@code null}. */
    private static String testo(String valore) {
        if (valore == null) {
            return null;
        }
        String ripulito = valore.strip();
        return ripulito.isEmpty() ? null : ripulito;
    }

    private static TipoDestinatari tipo(String valore) {
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
     * Converte gli identificativi del gruppo.
     *
     * @return falso se almeno un identificativo manca o non è un UUID
     */
    private static boolean leggiGruppo(List<String> valori, Set<UUID> gruppo) {
        if (valori == null) {
            return true;
        }
        for (String valore : valori) {
            try {
                gruppo.add(UUID.fromString(valore.strip()));
            } catch (IllegalArgumentException | NullPointerException e) {
                return false;
            }
        }
        return true;
    }
}
