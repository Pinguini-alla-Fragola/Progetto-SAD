package it.unina.picus.richieste.interno.persistenza;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "richieste")
public class RichiestaJpa {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID titolareId;
    private Long misuraId;
    private String comuneCodice;
    private String tipoDestinatario;
    private String description;
    private Instant inviataIl;
    private String stato = "CREATA";

    @Column(name = "erogatore_id")
    private UUID erogatoreId;

    protected RichiestaJpa() {
    }
}
