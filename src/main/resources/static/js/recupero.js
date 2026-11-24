import { api } from './area-client.js';

const MESSAGGIO_RETE = 'Connessione non disponibile. Riprova.';
const MESSAGGIO_TROPPE_RICHIESTE = 'Hai fatto troppe richieste per questo indirizzo. Riprova tra qualche minuto.';
const LINK_NON_VALIDO = 410;

/** Il server dà già un messaggio leggibile; il limite di richieste ha il suo testo, perché non dura un minuto. */
function messaggioErrore(causa) {
    if (causa?.stato === 429) return MESSAGGIO_TROPPE_RICHIESTE;
    return causa?.message || MESSAGGIO_RETE;
}

/** Richiesta del link: la risposta è la stessa per ogni indirizzo, quindi non dice se l'account esiste. */
const richiesta = document.getElementById('richiesta-recupero');
if (richiesta) {
    richiesta.addEventListener('submit', async (event) => {
        event.preventDefault();
        const button = richiesta.querySelector('button[type="submit"]');
        const esito = document.getElementById('esito');
        button.disabled = true;
        esito.textContent = '';
        try {
            const risultato = await api(richiesta.dataset.endpoint, { method: 'POST', body: Object.fromEntries(new FormData(richiesta)) });
            esito.textContent = risultato.messaggio || 'Richiesta inviata.';
            richiesta.reset();
        } catch (causa) {
            esito.textContent = messaggioErrore(causa);
        } finally {
            button.disabled = false;
        }
    });
}

/**
 * Scelta della nuova password con il link. Una password da correggere resta sul campo e il link si può riusare;
 * un link scaduto o già usato chiude il modulo e propone una nuova richiesta; dopo il successo il modulo si chiude
 * perché il link è stato consumato.
 */
const cambio = document.getElementById('cambio-password');
if (cambio) {
    cambio.addEventListener('submit', async (event) => {
        event.preventDefault();
        const campi = cambio.querySelector('fieldset');
        const button = cambio.querySelector('button[type="submit"]');
        const esito = document.getElementById('esito');
        const errore = document.getElementById('errore-password');
        const password = cambio.elements.nuovaPassword;
        button.disabled = true;
        esito.textContent = '';
        errore.hidden = true;
        password.removeAttribute('aria-invalid');
        try {
            const risultato = await api(cambio.dataset.endpoint, {
                method: 'POST',
                body: { token: cambio.elements.token.value, nuovaPassword: password.value }
            });
            cambio.reset();
            campi.disabled = true;
            esito.textContent = risultato.messaggio || 'Password cambiata.';
            document.getElementById('vai-al-login').hidden = false;
        } catch (causa) {
            if (causa?.stato === LINK_NON_VALIDO) {
                cambio.reset();
                campi.disabled = true;
                esito.textContent = causa.message;
                document.getElementById('nuova-richiesta').hidden = false;
            } else if (causa?.campi?.nuovaPassword) {
                errore.textContent = causa.campi.nuovaPassword;
                errore.hidden = false;
                password.setAttribute('aria-invalid', 'true');
                password.focus();
            } else {
                esito.textContent = messaggioErrore(causa);
            }
        } finally {
            // con il modulo chiuso il pulsante resta spento: non c'è più nulla da inviare
            if (!campi.disabled) button.disabled = false;
        }
    });
}
