package it.unina.picus.utenze.interno.dominio;

/** Se un indirizzo email si può assegnare a un nuovo account. */
public enum DisponibilitaEmail {
    LIBERA,
    GIA_IN_USO,
    /** L'indirizzo si riutilizza solo riattivando la stessa utenza (decisione del PO sulla #65). */
    ACCOUNT_REVOCATO
}
