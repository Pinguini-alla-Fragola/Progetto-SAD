import { api } from './area-client.js';
import { segnalaFunzioniInattive } from './funzioni-inattive.js';

segnalaFunzioniInattive();

const form = document.getElementById('credenziali');
form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (form.hasAttribute('data-form-anteprima')) return;
    const button = form.querySelector('button');
    const esito = document.getElementById('esito');
    const dati = Object.fromEntries(new FormData(form));
    button.disabled = true;
    try {
        const operazione = dati.token ? '/conferma' : '/richiedi';
        const risultato = await api(form.dataset.endpoint + operazione, { method: 'POST', body: dati });
        esito.textContent = risultato.messaggio || risultato.message || risultato.errore;
        form.reset();
    } catch {
        esito.textContent = 'Connessione non disponibile. Riprova.';
    } finally {
        button.disabled = false;
    }
});
