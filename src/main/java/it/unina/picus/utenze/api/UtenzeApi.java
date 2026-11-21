package it.unina.picus.utenze.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UtenzeApi {

    Optional<IdentitaAutenticata> verificaCredenziali(String email, String password);

    Optional<IdentitaAutenticata> verificaAccountSessione(UUID accountId);

    Optional<AccountSintetico> cercaAccount(UUID accountId);

    List<AccountSintetico> elencoAccountAttivi(RuoloUtente ruolo);

    Optional<ProfiloErogatore> cercaProfiloErogatoreAttivo(UUID accountId);

    List<ProfiloErogatore> elencoOptAttive(Set<UUID> accountIds);

    /**
     * Elenca tutti gli account di un ruolo in qualsiasi stato (attivo, in attesa, revocato),
     * includendo email e riferimento univoco per la gestione amministrativa del Banco.
     */
    List<AccountAmministrativo> elencoAccountPerRuolo(RuoloUtente ruolo);

    void revocaUtenzaOpt(UUID optAccountId);

    void revocaUtenzaOperatore(UUID operatoreAccountId);

    Optional<ProfiloErogatore> bloccaProfiloPerMisura(UUID accountId, long misuraId);

    List<ProfiloErogatore> elencoOperatoriAttiviPerMisura(long misuraId);

    record IdentitaAutenticata(UUID accountId, RuoloUtente ruolo) {}

    record AccountSintetico(UUID id, String nomeVisualizzato, RuoloUtente ruolo, StatoUtenza stato) {}

    record AccountAmministrativo(
            UUID id,
            String nomeVisualizzato,
            String email,
            String riferimento,
            String comuneCodice,
            RuoloUtente ruolo,
            StatoUtenza stato
    ) {}

    record ProfiloErogatore(
            UUID accountId, String nomeVisualizzato, TipoErogatore tipo, String comuneCodice, boolean centroPicus) {}

    enum RuoloUtente {
        BANCO,
        OPT,
        OPERATORE
    }

    enum StatoUtenza {
        IN_ATTESA_DI_ATTIVAZIONE,
        ATTIVO,
        REVOCATO
    }

    enum TipoErogatore {
        OPT,
        INTERNO_BANCO,
        ESTERNO_PAGATO_BANCO
    }
}