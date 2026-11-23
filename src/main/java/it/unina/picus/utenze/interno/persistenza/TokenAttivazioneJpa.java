package it.unina.picus.utenze.interno.persistenza;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "utenze_token_attivazione")
public class TokenAttivazioneJpa {

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

    @Column(name = "da_importazione_opt", nullable = false)
    private boolean daImportazioneOpt;

    protected TokenAttivazioneJpa() {
    }

    public TokenAttivazioneJpa(UUID id, UUID accountId, String tokenHash, Instant scadenza, Instant creatoIl) {
        this(id, accountId, tokenHash, scadenza, creatoIl, false);
    }

    public TokenAttivazioneJpa(UUID id, UUID accountId, String tokenHash, Instant scadenza, Instant creatoIl,
                               boolean daImportazioneOpt) {
        this.id = Objects.requireNonNull(id, "id obbligatorio");
        this.accountId = Objects.requireNonNull(accountId, "accountId obbligatorio");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash obbligatorio");
        this.scadenza = Objects.requireNonNull(scadenza, "scadenza obbligatoria");
        this.creatoIl = Objects.requireNonNull(creatoIl, "creatoIl obbligatorio");
        this.daImportazioneOpt = daImportazioneOpt;
    }

    public boolean isDaImportazioneOpt() {
        return daImportazioneOpt;
    }

    public boolean isUsato() {
        return usatoIl != null;
    }

    public boolean isScaduto(Instant ora) {
        return !scadenza.isAfter(ora);
    }

    public void segnaComeUsato(Instant ora) {
        if (this.usatoIl != null) {
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