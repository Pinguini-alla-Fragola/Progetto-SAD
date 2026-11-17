import { api } from './area-client.js';

const form = document.getElementById('richiestaForm');
const campi = document.getElementById('campi-invio');
const button = document.getElementById('btnInvia');
const errore = document.getElementById('errore-invio');
const incerto = document.getElementById('invio-incerto');
let invio;
let occupato = false;

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (occupato) return;
    occupato = true;
    invio ??= {
        chiave: crypto.randomUUID(),
        incerto: false,
        dati: {
            misuraId: Number(form.elements.misuraId.value),
            tipoDestinatario: form.elements.tipoDestinatario.value,
            comuneCodice: form.elements.comuneCodice.value,
            description: form.elements.description.value.trim() || null
        }
    };
    errore.textContent = '';
    for (const nodo of form.querySelectorAll('[aria-invalid]')) nodo.removeAttribute('aria-invalid');
    for (const nodo of form.querySelectorAll('.picus-errore')) nodo.textContent = '';
    campi.disabled = true;
    button.disabled = true;
    button.textContent = 'Registrazione in corso…';
    try {
        const richiesta = await api('/api/richieste', {
            method: 'POST', body: invio.dati, headers: { 'Idempotency-Key': invio.chiave }
        });
        form.hidden = true;
        document.getElementById('invio-intestazione').hidden = true;
        document.getElementById('conferma-misura').textContent = form.elements.misuraId.selectedOptions[0].textContent;
        document.getElementById('conferma-comune').textContent = form.elements.comuneCodice.selectedOptions[0].textContent;
        document.getElementById('linkDettaglio').href = '/richieste/' + encodeURIComponent(richiesta.id);
        const conferma = document.getElementById('conferma');
        conferma.hidden = false;
        conferma.focus();
    } catch (causa) {
        errore.textContent = causa.stato ? causa.message : 'Connessione interrotta. Riprova per verificare l’invio senza creare duplicati.';
        if (!invio.incerto && [400, 403, 429].includes(causa.stato)) {
            invio = undefined;
            campi.disabled = false;
            incerto.hidden = true;
        } else {
            invio.incerto = true;
            incerto.hidden = false;
        }
        for (const [nome, testo] of Object.entries(causa.campi || {})) {
            const messaggio = document.getElementById('errore-' + nome);
            if (messaggio) messaggio.textContent = testo;
            form.querySelector('[name="' + CSS.escape(nome) + '"]')?.setAttribute('aria-invalid', 'true');
        }
        form.querySelector('[aria-invalid]')?.focus();
        button.textContent = invio ? 'Riprova lo stesso invio' : 'Invia richiesta';
    } finally {
        occupato = false;
        button.disabled = false;
    }
});
