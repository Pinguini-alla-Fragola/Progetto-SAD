import { api } from './area-client.js';
import { segnalaFunzioniInattive } from './funzioni-inattive.js';

segnalaFunzioniInattive();

const form = document.getElementById('credenziali');
if (form) {
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (form.hasAttribute('data-form-anteprima')) return;
        const button = form.querySelector('button[type="submit"]');
        const esito = document.getElementById('esito');
        const dati = Object.fromEntries(new FormData(form));
        if (!dati.token) return;
        button.disabled = true;
        esito.textContent = '';
        try {
            const risultato = await api(form.dataset.endpoint + '/conferma', { method: 'POST', body: dati });
            esito.textContent = risultato.messaggio || risultato.message || 'Password salvata con successo.';
            form.reset();
        } catch (causa) {
            esito.textContent = causa?.message || 'Connessione non disponibile. Riprova.';
        } finally {
            button.disabled = false;
        }
    });
}