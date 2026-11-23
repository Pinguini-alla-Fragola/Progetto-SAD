import { api } from './area-client.js';
import { segnalaFunzioniInattive } from './funzioni-inattive.js';

segnalaFunzioniInattive();

const MESSAGGIO_RETE = 'Connessione non disponibile. Riprova.';
const MESSAGGIO_TROPPE_RICHIESTE = 'Hai fatto troppe richieste per questo indirizzo. Riprova tra qualche minuto.';

/** Il server dà già un messaggio leggibile; il limite di richieste ha il suo testo, perché non dura un minuto. */
function messaggioErrore(causa) {
    if (causa?.stato === 429) return MESSAGGIO_TROPPE_RICHIESTE;
    return causa?.message || MESSAGGIO_RETE;
}

/** Richiesta del link: la risposta è la stessa per ogni indirizzo, quindi non dice se l'account esiste. */
const form = document.getElementById('richiesta-recupero');
if (form) {
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const button = form.querySelector('button[type="submit"]');
        const esito = document.getElementById('esito');
        button.disabled = true;
        esito.textContent = '';
        try {
            const risultato = await api(form.dataset.endpoint, { method: 'POST', body: Object.fromEntries(new FormData(form)) });
            esito.textContent = risultato.messaggio || 'Richiesta inviata.';
            form.reset();
        } catch (causa) {
            esito.textContent = messaggioErrore(causa);
        } finally {
            button.disabled = false;
        }
    });
}
