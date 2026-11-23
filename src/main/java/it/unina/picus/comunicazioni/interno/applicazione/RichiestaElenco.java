package it.unina.picus.comunicazioni.interno.applicazione;

/**
 * Ricerca e pagina chieste dalla pagina web per un elenco di comunicazioni, con i valori così come arrivano.
 * Ogni campo può mancare.
 *
 * @param testo parole da cercare
 * @param destinatari nome del tipo di destinatari
 * @param allegati {@code con} o {@code senza}
 * @param ricezione {@code confermata} o {@code da-confermare}, solo per le comunicazioni ricevute
 * @param pagina numero della pagina, da zero
 */
public record RichiestaElenco(String testo, String destinatari, String allegati, String ricezione, String pagina) {}
