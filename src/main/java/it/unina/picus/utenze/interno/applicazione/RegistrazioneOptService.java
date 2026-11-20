package it.unina.picus.utenze.interno.applicazione;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.interno.persistenza.AccountJpa;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;
import it.unina.picus.utenze.interno.persistenza.ProfiloOptJpa;
import it.unina.picus.utenze.interno.persistenza.ProfiloOptRepository;

/**
 * Registrazione manuale di una singola OPT da parte dell'Amministratore del Banco (Task #146).
 */
@Service
@Profile("!skeleton")
@Transactional
public class RegistrazioneOptService {

    static final int LUNGHEZZA_MAX_CODICE_STRUTTURA = 30;
    static final int LUNGHEZZA_MAX_NOME = 200;
    static final int LUNGHEZZA_MAX_COMUNE_CODICE = 50;

    // Accetta sia Codice Fiscale ordinario (16 caratteri alfanumerici) sia CF numerico di enti/organizzazioni (11 cifre)
    private static final Pattern PATTERN_CODICE_FISCALE =
            Pattern.compile("^(?:[A-Z]{6}[0-9]{2}[A-Z][0-9]{2}[A-Z][0-9]{3}[A-Z]|[0-9]{11})$");

    private static final Pattern PATTERN_EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AccountRepository accounts;
    private final ProfiloOptRepository profiliOpt;
    private final AttivazioneUtenzaService attivazioneService;

    public RegistrazioneOptService(
            AccountRepository accounts,
            ProfiloOptRepository profiliOpt,
            AttivazioneUtenzaService attivazioneService) {
        this.accounts = Objects.requireNonNull(accounts);
        this.profiliOpt = Objects.requireNonNull(profiliOpt);
        this.attivazioneService = Objects.requireNonNull(attivazioneService);
    }

    public record ComandoRegistrazioneOpt(
            String codiceStruttura,
            String nome,
            String comuneCodice,
            String codiceFiscale,
            String email
    ) {}

    /**
     * Registra una nuova OPT in stato IN_ATTESA_DI_ATTIVAZIONE e invia il link di attivazione.
     *
     * @return l'UUID dell'account creato
     */
    public UUID registraOpt(ComandoRegistrazioneOpt comando) {
        Objects.requireNonNull(comando, "comando obbligatorio");

        String codiceStruttura = validaTestoObbligatorio(
                comando.codiceStruttura(), "codiceStruttura",
                "Il codice struttura è obbligatorio.",
                LUNGHEZZA_MAX_CODICE_STRUTTURA,
                "Il codice struttura non può superare " + LUNGHEZZA_MAX_CODICE_STRUTTURA + " caratteri.",
                Locale.ROOT, true);

        String nome = validaTestoObbligatorio(
                comando.nome(), "nome",
                "Il nome della OPT è obbligatorio.",
                LUNGHEZZA_MAX_NOME,
                "Il nome della OPT non può superare " + LUNGHEZZA_MAX_NOME + " caratteri.",
                null, false);

        String comuneCodice = validaTestoObbligatorio(
                comando.comuneCodice(), "comuneCodice",
                "Il comune è obbligatorio.",
                LUNGHEZZA_MAX_COMUNE_CODICE,
                "Il codice del comune non può superare " + LUNGHEZZA_MAX_COMUNE_CODICE + " caratteri.",
                Locale.ROOT, false);

        String codiceFiscale = validaCodiceFiscale(comando.codiceFiscale());
        String email = validaEmail(comando.email());

        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new DatiOptNonValidiException("email", "Esiste già un account associato a questa email.");
        }
        if (profiliOpt.existsByCodiceStrutturaIgnoreCase(codiceStruttura)) {
            throw new DatiOptNonValidiException("codiceStruttura", "Esiste già una OPT registrata con questo codice struttura.");
        }

        try {
            AccountJpa account = AccountJpa.inAttesaDiAttivazione(email, nome, RuoloUtente.OPT);
            account = accounts.saveAndFlush(account);

            ProfiloOptJpa profilo = new ProfiloOptJpa(
                    account.getId(),
                    codiceStruttura,
                    comuneCodice,
                    codiceFiscale
            );
            profiliOpt.saveAndFlush(profilo);

            attivazioneService.generaEInviaAttivazione(account);
            return account.getId();

        } catch (DataIntegrityViolationException ex) {
            throw classificaViolazioneVincolo(ex);
        }
    }

    private static DatiOptNonValidiException classificaViolazioneVincolo(DataIntegrityViolationException ex) {
        String nomeVincolo = estraiNomeVincolo(ex);
        if (nomeVincolo == null || nomeVincolo.isBlank()) {
            throw ex;
        }
        String vincoloLower = nomeVincolo.toLowerCase(Locale.ROOT);
        if (vincoloLower.contains("email")) {
            return new DatiOptNonValidiException("email", "Esiste già un account associato a questa email.");
        }
        if (vincoloLower.contains("codice_struttura")) {
            return new DatiOptNonValidiException("codiceStruttura", "Esiste già una OPT registrata con questo codice struttura.");
        }
        if (vincoloLower.contains("comune")) {
            return new DatiOptNonValidiException("comuneCodice", "Il comune selezionato non è valido.");
        }
        throw ex;
    }

    private static String estraiNomeVincolo(DataIntegrityViolationException ex) {
        Throwable corrente = ex;
        while (corrente != null) {
            if (corrente instanceof ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            corrente = corrente.getCause();
        }
        return null;
    }

    private static String validaTestoObbligatorio(
            String valore,
            String campo,
            String msgObbligatorio,
            int maxLunghezza,
            String msgLunghezza,
            Locale localeNormalizzazione,
            boolean maiuscolo) {
        if (valore == null || valore.isBlank()) {
            throw new DatiOptNonValidiException(campo, msgObbligatorio);
        }
        String normalizzato = valore.strip();
        if (localeNormalizzazione != null) {
            normalizzato = maiuscolo
                    ? normalizzato.toUpperCase(localeNormalizzazione)
                    : normalizzato.toLowerCase(localeNormalizzazione);
        }
        if (normalizzato.length() > maxLunghezza) {
            throw new DatiOptNonValidiException(campo, msgLunghezza);
        }
        return normalizzato;
    }

    private static String validaEmail(String emailRaw) {
        String normalizzata = UtenzeService.normalizzaEmail(emailRaw);
        if (normalizzata == null || !PATTERN_EMAIL.matcher(normalizzata).matches()) {
            throw new DatiOptNonValidiException("email", "Inserisci un indirizzo email valido.");
        }
        if (normalizzata.length() > UtenzeService.LUNGHEZZA_MAX_EMAIL) {
            throw new DatiOptNonValidiException("email",
                    "L'indirizzo email non può superare " + UtenzeService.LUNGHEZZA_MAX_EMAIL + " caratteri.");
        }
        return normalizzata;
    }

    private static String validaCodiceFiscale(String cfRaw) {
        if (cfRaw == null || cfRaw.isBlank()) {
            throw new DatiOptNonValidiException("codiceFiscale", "Il codice fiscale è obbligatorio.");
        }
        String cf = cfRaw.strip().toUpperCase(Locale.ROOT);
        if ("00000000000".equals(cf) || !PATTERN_CODICE_FISCALE.matcher(cf).matches()) {
            throw new DatiOptNonValidiException("codiceFiscale",
                    "Il codice fiscale non ha un formato valido (16 caratteri alfanumerici o 11 cifre).");
        }
        return cf;
    }
}