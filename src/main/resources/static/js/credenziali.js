import { api } from './area-client.js';
import { segnalaFunzioniInattive } from './funzioni-inattive.js';

segnalaFunzioniInattive();

const MESSAGGIO_RETE = 'Connessione non disponibile. Riprova.';
const MESSAGGIO_TROPPE_RICHIESTE = 'Hai chiesto troppi link per questo indirizzo. Riprova tra qualche minuto.';

/** Il server dà già un messaggio leggibile; il limite di richieste ha il suo testo, perché non dura un minuto. */
function messaggioErrore(causa) {
    if (causa?.stato === 429) return MESSAGGIO_TROPPE_RICHIESTE;
    return causa?.message || MESSAGGIO_RETE;
}

/** Password iniziale o richiesta di un nuovo link: il form dichiara l'indirizzo a cui inviare i suoi campi. */
const form = document.getElementById('credenziali');
if (form) {
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const endpoint = form.dataset.endpoint;
        if (form.hasAttribute('data-form-anteprima') || !endpoint) return;
        const campi = form.querySelector('fieldset');
        const button = form.querySelector('button[type="submit"]');
        const esito = document.getElementById('esito');
        button.disabled = true;
        esito.textContent = '';
        try {
            const risultato = await api(endpoint, { method: 'POST', body: Object.fromEntries(new FormData(form)) });
            esito.textContent = risultato.messaggio || risultato.message || 'Operazione completata.';
            form.reset();
            const accesso = document.getElementById('vai-al-login');
            if (accesso) {
                // password salvata: il link non vale più, il modulo non serve altro
                campi.disabled = true;
                accesso.hidden = false;
            }
        } catch (causa) {
            esito.textContent = messaggioErrore(causa);
        } finally {
            // dopo l'attivazione il modulo resta chiuso: il pulsante torna attivo solo se si può riprovare
            if (!campi.disabled) button.disabled = false;
        }
    });
}
