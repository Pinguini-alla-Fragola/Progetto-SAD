package it.unina.picus.shared;

public interface InvioEmail {
    void inviaLinkAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro);
}