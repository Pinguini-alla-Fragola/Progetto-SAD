package it.unina.picus.catalogo.interno.applicazione;

import it.unina.picus.catalogo.api.CatalogoApi.OffertaOpt;
import it.unina.picus.catalogo.api.GestioneOffertaOptApi;
import it.unina.picus.catalogo.interno.persistenza.OffertaTerritorialeJpa;
import it.unina.picus.shared.AccessoNegatoException;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Coordina le operazioni sull'offerta delle OPT e delimita le relative transazioni. */
@Service
@Transactional(readOnly = true)
public class ServizioOffertaOpt implements GestioneOffertaOptApi {
    private final OffertaTerritorialeJpa offerta;
    private final UtenzeApi utenze;

    /**
     * Collega l'offerta ai dati di catalogo e alle identità organizzative.
     *
     * @param offerta adattatore di persistenza delle dichiarazioni
     * @param utenze contratto di consultazione e verifica delle utenze
     */
    public ServizioOffertaOpt(OffertaTerritorialeJpa offerta, UtenzeApi utenze) {
        this.offerta = offerta;
        this.utenze = utenze;
    }

    /** {@inheritDoc} */
    @Override
    public List<OffertaOpt> elencoOffertaOpt(IdentitaAutenticata attore, UUID optId) {
        if (attore == null || attore.accountId() == null
                || (attore.ruolo() != RuoloUtente.BANCO
                        && !(attore.ruolo() == RuoloUtente.OPT && attore.accountId().equals(optId)))) {
            throw new AccessoNegatoException("Consultazione dell'offerta non consentita");
        }
        utenze.cercaAccount(optId).filter(account -> account.ruolo() == RuoloUtente.OPT)
                .orElseThrow(() -> new NoSuchElementException("OPT non trovata"));
        return offerta.elencoOffertaOpt(optId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Mantiene il lock della misura durante la scrittura seguendo l'ordine dei lock
     * della verifica di compatibilità usata dal Banco.
     */
    @Override
    @Transactional
    public void registraOffertaOpt(IdentitaAutenticata attore, long misuraId, boolean tramiteProfessionista) {
        UUID optId = verificaOpt(attore);
        if (!offerta.bloccaMisuraAttiva(misuraId)) {
            throw new MisuraNonOffribileException();
        }
        bloccaProfiloOpt(optId, misuraId);
        offerta.registraOffertaOpt(optId, misuraId, tramiteProfessionista);
    }

    /**
     * {@inheritDoc}
     *
     * <p>La dichiarazione può essere ritirata anche se la misura è stata disattivata.
     */
    @Override
    @Transactional
    public void ritiraOffertaOpt(IdentitaAutenticata attore, long misuraId) {
        UUID optId = verificaOpt(attore);
        bloccaProfiloOpt(optId, misuraId);
        if (!offerta.ritiraOffertaOpt(optId, misuraId)) {
            throw new NoSuchElementException("Offerta corrente non trovata");
        }
    }

    private static UUID verificaOpt(IdentitaAutenticata attore) {
        if (attore == null || attore.ruolo() != RuoloUtente.OPT || attore.accountId() == null) {
            throw new AccessoNegatoException("Comando riservato alle OPT");
        }
        return attore.accountId();
    }

    private void bloccaProfiloOpt(UUID optId, long misuraId) {
        utenze.bloccaProfiloPerMisura(optId, misuraId).filter(profilo -> profilo.tipo() == TipoErogatore.OPT)
                .orElseThrow(() -> new AccessoNegatoException("Profilo OPT non abilitato"));
    }
}
