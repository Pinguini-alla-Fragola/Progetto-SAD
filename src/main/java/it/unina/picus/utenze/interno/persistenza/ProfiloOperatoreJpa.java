package it.unina.picus.utenze.interno.persistenza;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "utenze_profili_operatore")
public class ProfiloOperatoreJpa {
    @Id
    private UUID accountId;
    private String tipologia;
    private String comuneCodice;
    private boolean centroPicus;
    private String telefono;
    private String codiceFiscale;

    protected ProfiloOperatoreJpa() {
    }

    public ProfiloOperatoreJpa(UUID accountId, String tipologia, String comuneCodice, boolean centroPicus,
            String telefono, String codiceFiscale) {
        this.accountId = accountId;
        this.tipologia = tipologia;
        this.comuneCodice = comuneCodice;
        this.centroPicus = centroPicus;
        this.telefono = telefono;
        this.codiceFiscale = codiceFiscale;
    }

    public UUID getAccountId() { return accountId; }
    public String getTipologia() { return tipologia; }
    public String getComuneCodice() { return comuneCodice; }
    public boolean isCentroPicus() { return centroPicus; }
    public String getTelefono() { return telefono; }
    public String getCodiceFiscale() { return codiceFiscale; }
}
