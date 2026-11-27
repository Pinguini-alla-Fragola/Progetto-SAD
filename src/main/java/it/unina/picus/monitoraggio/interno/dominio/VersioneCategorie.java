package it.unina.picus.monitoraggio.interno.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Versione delle categorie di un tracciato di monitoraggio (#41, #181).
 * Le versioni in {@link StatoVersioneCategorie#BOZZA} sono modificabili dal Banco;
 * le versioni in {@link StatoVersioneCategorie#PUBBLICATA} e {@link StatoVersioneCategorie#LEGACY} sono immutabili.
 *
 * @param id identificativo stabile della versione
 * @param tracciato tracciato a cui appartiene la versione
 * @param codiceVersione codice identificativo univoco per tracciato (es. {@code 2026-t2})
 * @param stato stato corrente del ciclo di vita
 * @param descrizione descrizione della versione
 * @param creataIl istante di creazione
 * @param pubblicataIl istante di pubblicazione, o {@code null} finché resta bozza
 * @param voci elenco ordinato delle voci configurate nella versione
 */
public record VersioneCategorie(
        UUID id,
        TracciatoMonitoraggio tracciato,
        String codiceVersione,
        StatoVersioneCategorie stato,
        String descrizione,
        Instant creataIl,
        Instant pubblicataIl,
        List<VoceCategoriaVersione> voci) {

    public VersioneCategorie {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tracciato, "tracciato");
        if (!TracciatoSchedaOperatore.codiceCategoriaValido(codiceVersione)) {
            throw new IllegalArgumentException("Codice versione non valido");
        }
        Objects.requireNonNull(stato, "stato");
        Objects.requireNonNull(descrizione, "descrizione");
        descrizione = descrizione.trim();
        if (descrizione.isEmpty() || descrizione.length() > 255) {
            throw new IllegalArgumentException("Descrizione della versione non valida");
        }
        Objects.requireNonNull(creataIl, "creataIl");
        if (stato == StatoVersioneCategorie.BOZZA && pubblicataIl != null) {
            throw new IllegalArgumentException("Una bozza non può avere istante di pubblicazione");
        }
        if (stato != StatoVersioneCategorie.BOZZA && pubblicataIl == null) {
            throw new IllegalArgumentException("Una versione pubblicata o legacy richiede l'istante di pubblicazione");
        }
        voci = List.copyOf(Objects.requireNonNull(voci, "voci"));
    }

    /** {@return insieme immutabile dei codici ammessi dalla versione} */
    public Set<String> codiciAmmessi() {
        return voci.stream().map(VoceCategoriaVersione::codice).collect(Collectors.toUnmodifiableSet());
    }
}
