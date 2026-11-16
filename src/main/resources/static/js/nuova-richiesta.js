import { api } from './area-client.js';

const form = document.getElementById('richiestaForm');
// Mantiene il riferimento dell'invio quando si ritenta dopo un'interruzione.
let riferimentoInvio;

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (form.hasAttribute('data-form-anteprima')) return;

    riferimentoInvio ??= crypto.randomUUID();
    const button = document.getElementById('btnInvia');
    const errore = document.getElementById('errore-invio');
    errore.textContent = '';
    button.disabled = true;
    button.textContent = 'Registrazione in corso…';

    const payload = {
        misuraId: Number(form.elements.misuraId.value),
        tipoDestinatario: form.elements.tipoDestinatario.value,
        comuneCodice: form.elements.comuneCodice.value.trim(),
        description: form.elements.description.value.trim() || null,
        riferimentoInvio
    };
    try {
        const richiesta = await api('/api/richieste', { method: 'POST', body: payload });
        form.hidden = true;
        document.getElementById('confId').textContent = richiesta.id;
        document.getElementById('linkDettaglio').href = '/richieste/' + encodeURIComponent(richiesta.id);
        document.getElementById('conferma').hidden = false;
    } catch (causa) {
        errore.textContent = causa.stato ? causa.message : 'Connessione interrotta. Riprova l’invio senza duplicare la pratica.';
        button.disabled = false;
        button.textContent = 'Riprova l’invio';
    }
});

window.addEventListener('pageshow', () => {
    if (!form.hasAttribute('data-form-anteprima')) {
        const button = document.getElementById('btnInvia');
        button.disabled = false;
        button.textContent = 'Invia la richiesta al Banco';
    }
});
