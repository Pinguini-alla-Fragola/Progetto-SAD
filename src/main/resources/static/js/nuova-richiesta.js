import { api } from './area-client.js';

const form = document.getElementById('richiestaForm');
const campi = document.getElementById('campi-invio');
const button = document.getElementById('btnInvia');
const errore = document.getElementById('errore-invio');
const incerto = document.getElementById('invio-incerto');
const chiaveMemoria = 'picus:invio-pendente:' + form.dataset.utente;
let invio;
let occupato = false;

function leggiRiferimento() {
    if (!form.dataset.utente) throw new Error('Sessione non disponibile. Accedi di nuovo.');
    const riferimento = JSON.parse(sessionStorage.getItem(chiaveMemoria));
    if (riferimento && (!/^[0-9a-f-]{36}$/i.test(riferimento.chiave) || !/^[0-9a-f]{64}$/.test(riferimento.impronta))) {
        throw new Error('Riferimento di invio non leggibile. Verifica le tue richieste prima di inviarne un’altra.');
    }
    return riferimento;
}

async function preparaInvio() {
    const dati = {
        misuraId: Number(form.elements.misuraId.value),
        tipoDestinatario: form.elements.tipoDestinatario.value,
        comuneCodice: form.elements.comuneCodice.value,
        description: form.elements.description.value.trim() || null
    };
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(dati)));
    const impronta = [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
    const precedente = leggiRiferimento();
    if (precedente && precedente.impronta !== impronta) {
        throw new Error('C’è un invio da verificare. Reinserisci gli stessi dati, compresa la descrizione, oppure consulta «Le mie richieste» nell’area OPT.');
    }
    const chiave = precedente?.chiave || crypto.randomUUID();
    // Conserva solo riferimento e impronta: la descrizione non viene salvata nel browser.
    sessionStorage.setItem(chiaveMemoria, JSON.stringify({ chiave, impronta }));
    return { chiave, dati, incerto: !!precedente };
}

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (occupato) return;
    occupato = true;
    button.disabled = true;
    try {
        invio ??= await preparaInvio();
    } catch (causa) {
        errore.textContent = causa.message + ' Se la memoria del browser è disabilitata, abilitala per proteggere l’invio dai duplicati.';
        occupato = false;
        button.disabled = false;
        return;
    }
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
        sessionStorage.removeItem(chiaveMemoria);
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
        // Il backend restituisce un precedente invio prima dei controlli di catalogo:
        // un 400 con gli stessi dati certifica quindi che non è stato registrato.
        if (causa.stato === 400 || (!invio.incerto && [403, 429].includes(causa.stato))) {
            sessionStorage.removeItem(chiaveMemoria);
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

try {
    if (leggiRiferimento()) {
        incerto.textContent = 'C’è un invio da verificare. Reinserisci gli stessi dati, compresa la descrizione, e riprova: il riferimento originale evita una seconda pratica. Puoi anche consultare «Le mie richieste» nell’area OPT.';
        incerto.hidden = false;
        button.textContent = 'Verifica lo stesso invio';
    }
    campi.disabled = false;
    button.disabled = false;
    document.getElementById('avvio-invio').hidden = true;
} catch (causa) {
    errore.textContent = 'Impossibile recuperare il riferimento di invio. Verifica le tue richieste nell’area OPT e la memoria del browser prima di riprovare.';
}
