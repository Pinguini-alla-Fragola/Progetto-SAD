package it.unina.picus.utenze.interno.persistenza;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Link monouso di recupero password: si conserva solo l'impronta del token, valida fino alla scadenza. */
@Entity
@Table(name = "utenze_token_recupero_password")
public class TokenRecuperoPasswordJpa {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "scadenza", nullable = false)
    private Instant scadenza;

    @Column(name = "creato_il", nullable = false)
    private Instant creatoIl;

    @Column(name = "usato_il")
    private Instant usatoIl;

    protected TokenRecuperoPasswordJpa() {
    }

    public TokenRecuperoPasswordJpa(UUID id, UUID accountId, String tokenHash, Instant scadenza, Instant creatoIl) {
        this.id = Objects.requireNonNull(id, "id obbligatorio");
        this.accountId = Objects.requireNonNull(accountId, "accountId obbligatorio");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash obbligatorio");
        this.scadenza = Objects.requireNonNull(scadenza, "scadenza obbligatoria");
        this.creatoIl = Objects.requireNonNull(creatoIl, "creatoIl obbligatorio");
    }

    public boolean isUsato() {
        return usatoIl != null;
    }

    public boolean isScaduto(Instant ora) {
        return !scadenza.isAfter(ora);
    }

    public void segnaComeUsato(Instant ora) {
        if (usatoIl != null) {
            throw new IllegalStateException("Token già utilizzato");
        }
        this.usatoIl = Objects.requireNonNull(ora, "ora obbligatoria");
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getScadenza() {
        return scadenza;
    }

    public Instant getCreatoIl() {
        return creatoIl;
    }

    public Instant getUsatoIl() {
        return usatoIl;
    }
}
