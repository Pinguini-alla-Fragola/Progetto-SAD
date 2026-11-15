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

    protected ProfiloOperatoreJpa() {
    }
}
