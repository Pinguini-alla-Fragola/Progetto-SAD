package it.unina.picus.comunicazioni.interno.dominio;

/**
 * Controlla la scelta dei destinatari: il tipo è obbligatorio, un invio a un gruppo richiede almeno un account
 * e un invio a tutti non accetta un gruppo, per non lasciare dubbi su chi riceverà la comunicazione.
 */
public final class RegolaDestinatari implements RegolaComunicazione {

    /** Limite al numero di account scelti a mano: impedisce richieste enormi che appesantirebbero il server. */
    static final int MASSIMO_GRUPPO = 1_000;

    @Override
    public void verifica(BozzaComunicazione bozza, Errori errori) {
        if (bozza.tipo() == null) {
            errori.aggiungi(CampoComunicazione.DESTINATARI_TIPO, "Scegli a chi inviare la comunicazione");
            return;
        }
        if (!bozza.gruppoLeggibile()) {
            errori.aggiungi(CampoComunicazione.GRUPPO, "Il gruppo contiene un account non riconosciuto");
            return;
        }
        boolean perGruppo = PlateaDestinatari.di(bozza.tipo()).gruppo();
        if (perGruppo && bozza.gruppo().isEmpty()) {
            errori.aggiungi(CampoComunicazione.GRUPPO, "Scegli almeno un account del gruppo");
        }
        if (bozza.gruppo().size() > MASSIMO_GRUPPO) {
            errori.aggiungi(CampoComunicazione.GRUPPO, "Il gruppo può avere al massimo " + MASSIMO_GRUPPO + " account");
        }
        if (!perGruppo && !bozza.gruppo().isEmpty()) {
            errori.aggiungi(CampoComunicazione.GRUPPO, "Per un invio a tutti non serve scegliere un gruppo");
        }
    }
}
