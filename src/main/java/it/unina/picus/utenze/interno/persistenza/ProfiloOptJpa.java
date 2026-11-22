package it.unina.picus.utenze.interno.persistenza;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "utenze_profili_opt")
public class ProfiloOptJpa {
    @Id
    private UUID accountId;
    private String codiceStruttura;
    private String comuneCodice;
    private String codiceFiscale;

    protected ProfiloOptJpa() {
    }

    public ProfiloOptJpa(UUID accountId, String codiceStruttura, String comuneCodice, String codiceFiscale) {
        this.accountId = Objects.requireNonNull(accountId, "accountId obbligatorio");
        this.codiceStruttura = Objects.requireNonNull(codiceStruttura, "codiceStruttura obbligatorio");
        this.comuneCodice = Objects.requireNonNull(comuneCodice, "comuneCodice obbligatorio");
        this.codiceFiscale = Objects.requireNonNull(codiceFiscale, "codiceFiscale obbligatorio");
    }

    public UUID getAccountId() { return accountId; }
    public String getCodiceStruttura() { return codiceStruttura; }
    public String getComuneCodice() { return comuneCodice; }
    public String getCodiceFiscale() { return codiceFiscale; }
}