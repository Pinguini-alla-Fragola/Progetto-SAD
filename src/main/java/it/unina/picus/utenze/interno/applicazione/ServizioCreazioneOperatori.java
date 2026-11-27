package it.unina.picus.utenze.interno.applicazione;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;
import it.unina.picus.utenze.interno.dominio.DatiOperatore;
import it.unina.picus.utenze.interno.dominio.Errori;
import it.unina.picus.utenze.interno.dominio.RegistrazioneInConflittoException;
import it.unina.picus.utenze.interno.dominio.RegistroOperatori;
import it.unina.picus.utenze.interno.dominio.RegistroOperatori.OperatoreRegistrato;
import it.unina.picus.utenze.interno.dominio.RegolaUnicita;
import it.unina.picus.utenze.interno.dominio.RegoleAccessoUtenze;
import it.unina.picus.utenze.interno.dominio.ValidatoreDatiOperatore;

/** Coordina i passi della creazione: permesso, pulizia dei dati, controlli, registrazione e link di attivazione. */
@Service
@Profile("!skeleton")
@Transactional
class ServizioCreazioneOperatori implements CreazioneOperatori {

    private final ValidatoreDatiOperatore validatore;
    private final RegistroOperatori registro;
    private final PasswordEncoder encoder;
    private final AttivazioneUtenzaService attivazione;

    ServizioCreazioneOperatori(ValidatoreDatiOperatore validatore, RegistroOperatori registro,
            PasswordEncoder encoder, AttivazioneUtenzaService attivazione) {
        this.validatore = validatore;
        this.registro = registro;
        this.encoder = encoder;
        this.attivazione = attivazione;
    }

    @Override
    public OperatoreCreato crea(IdentitaAutenticata attore, ComandoCreaOperatore comando) {
        RegoleAccessoUtenze.verificaBanco(attore);
        DatiOperatore dati = NormalizzatoreDatiOperatore.normalizza(comando);
        Errori errori = validatore.verifica(dati);
        if (!errori.vuoto()) {
            throw new DatiOperatoreNonValidiException(errori.perCampo());
        }
        try {
            OperatoreRegistrato registrato = registro.registra(dati, passwordInutilizzabile());
            // Il link nasce nella stessa transazione dell'account: o esistono entrambi o nessuno dei due.
            DatiInvioAttivazione invito = attivazione.rigeneraTokenPerAccount(registrato.id(), RuoloUtente.OPERATORE);
            return new OperatoreCreato(registrato.id(), registrato.nomeVisualizzato(), registrato.email(), invito);
        } catch (RegistrazioneInConflittoException e) {
            // Un'altra creazione, nello stesso momento, ha salvato la stessa email o lo stesso codice fiscale.
            throw new DatiOperatoreNonValidiException(Map.of(e.campo(), RegolaUnicita.messaggioDuplicato(e.campo())));
        }
    }

    /** Password casuale che nessuno conosce: l'Operatore ne sceglierà una dal link di attivazione. */
    private String passwordInutilizzabile() {
        return encoder.encode(UUID.randomUUID().toString());
    }
}
