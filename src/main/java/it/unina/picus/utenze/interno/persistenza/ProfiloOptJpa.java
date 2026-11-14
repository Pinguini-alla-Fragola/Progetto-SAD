package it.unina.picus.utenze.interno.persistenza;

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

    protected ProfiloOptJpa() {
    }
}
