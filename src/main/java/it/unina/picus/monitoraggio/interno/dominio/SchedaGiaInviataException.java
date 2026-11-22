package it.unina.picus.monitoraggio.interno.dominio;

/** Esito del primo invio se l'account ha già registrato una scheda nello stesso periodo. */
public final class SchedaGiaInviataException extends IllegalStateException {

    /** Crea il conflitto senza esporre i dati della scheda registrata. */
    public SchedaGiaInviataException() {
        super("Esiste già una scheda inviata per questo trimestre");
    }
}
