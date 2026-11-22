package it.unina.picus.utenze.interno.persistenza;

import java.util.Objects;
import java.util.UUID;

import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.StatoUtenza;
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

    protected AccountJpa() {
    }

    public AccountJpa(String email, String passwordHash, String nome, String ruolo, String stato) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nome = nome;
        this.ruolo = ruolo;
        this.stato = stato;
    }

    public static AccountJpa inAttesaDiAttivazione(String email, String nome, RuoloUtente ruolo) {
        AccountJpa account = new AccountJpa();
        account.email = Objects.requireNonNull(email, "email obbligatoria");
        account.nome = Objects.requireNonNull(nome, "nome obbligatorio");
        account.ruolo = Objects.requireNonNull(ruolo, "ruolo obbligatorio").name();
        account.stato = StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name();
        account.passwordHash = null;
        return account;
    }
    public void revoca() {
        if (StatoUtenza.REVOCATO.name().equals(this.stato)) {
            throw new IllegalStateException("L'account è già stato revocato");
        }
        if (!StatoUtenza.ATTIVO.name().equals(this.stato)
                && !StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(this.stato)) {
            throw new IllegalStateException("Stato utenza non revocabile: " + this.stato);
        }
        this.stato = StatoUtenza.REVOCATO.name();
    }
    public void attivaConPassword(String nuovoPasswordHash) {
        if (!StatoUtenza.IN_ATTESA_DI_ATTIVAZIONE.name().equals(this.stato)) {
            throw new IllegalStateException("Solo un account in attesa di attivazione può essere attivato");
        }
        this.passwordHash = Objects.requireNonNull(nuovoPasswordHash, "passwordHash obbligatorio");
        this.stato = StatoUtenza.ATTIVO.name();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNome() { return nome; }
    public String getRuolo() { return ruolo; }
    public String getStato() { return stato; }
}
