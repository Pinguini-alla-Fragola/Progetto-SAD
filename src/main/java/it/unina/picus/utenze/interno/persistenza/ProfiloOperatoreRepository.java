package it.unina.picus.utenze.interno.persistenza;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfiloOperatoreRepository extends JpaRepository<ProfiloOperatoreJpa, UUID> {
    boolean existsByCodiceFiscale(String codiceFiscale);

    @Query("""
            select p.accountId as accountId, a.nome as nome, p.tipologia as tipologia,
                   p.comuneCodice as comuneCodice, p.centroPicus as centroPicus
            from ProfiloOperatoreJpa p, OperatoreMisuraJpa m, AccountJpa a
            where p.accountId = m.id.accountId and p.accountId = a.id and m.id.misuraId = :misura
              and a.stato = 'ATTIVO' and a.ruolo = 'OPERATORE'
            """)
    List<ProfiloCompatibile> perMisura(@Param("misura") long misuraId);

    interface ProfiloCompatibile {
        UUID getAccountId();
        String getNome();
        String getTipologia();
        String getComuneCodice();
        boolean isCentroPicus();
    }
}
