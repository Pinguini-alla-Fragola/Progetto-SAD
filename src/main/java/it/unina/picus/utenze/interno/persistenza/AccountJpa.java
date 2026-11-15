package it.unina.picus.utenze.interno.persistenza;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "utenze_account")
public class AccountJpa {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String email;
    private String passwordHash;
    private String nome;
    private String ruolo;
    private String stato;
    private int tentativiFalliti;
    private Instant bloccatoFino;

    protected AccountJpa() {
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNome() { return nome; }
    public String getRuolo() { return ruolo; }
    public String getStato() { return stato; }
    public int getTentativiFalliti() { return tentativiFalliti; }

    /** Vero se l'accesso con credenziali è sospeso all'istante indicato. */
    public boolean isBloccatoAl(Instant ora) {
        return bloccatoFino != null && bloccatoFino.isAfter(ora);
    }
}
