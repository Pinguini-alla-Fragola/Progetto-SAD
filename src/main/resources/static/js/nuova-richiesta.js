import { api } from './area-client.js';

const LIMITE_SUGGERIMENTI = 12;
const TESTO_AIUTO_COMUNE = 'Digita il nome del comune per filtrare l’elenco e distinguerlo per provincia o città metropolitana.';

export function normalizzaTestoComune(testo) {
    return String(testo || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .replace(/\s+/g, ' ')
        .trim();
}

export function collegaRicercaComune({ input, select, suggerimenti, stato, form } = {}) {
    if (!input || !select) return null;
    const opzioniIniziali = Array.from(select.options || [])
        .filter(op => op.value)
        .map(op => {
            const nome = (op.dataset?.nome || op.textContent || '').trim();
            const territorio = (op.dataset?.territorio || '').trim();
            return {
                codice: op.value,
                nome,
                territorio,
                etichetta: (op.textContent || nome).trim(),
                nomeNorm: normalizzaTestoComune(nome)
            };
        });
    if (!opzioniIniziali.length) return null;

    const mappa = new Map(opzioniIniziali.map(item => [item.codice, item]));
    let visibili = [];
    let indiceAttivo = -1;

    const impostaStato = testo => {
        if (stato) stato.textContent = testo;
    };

    const popolaSelect = (elementi, valoreSelezionato) => {
        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = 'Seleziona un comune';
        const frag = [placeholder];
        for (const item of elementi) {
            const op = document.createElement('option');
            op.value = item.codice;
            op.textContent = item.etichetta;
            if (op.dataset) {
                op.dataset.nome = item.nome;
                op.dataset.territorio = item.territorio;
            }
            frag.push(op);
        }
        select.replaceChildren(...frag);
        select.value = valoreSelezionato && elementi.some(e => e.codice === valoreSelezionato) ? valoreSelezionato : '';
    };

    const chiudiSuggerimenti = () => {
        if (!suggerimenti) return;
        suggerimenti.hidden = true;
        suggerimenti.replaceChildren();
        visibili = [];
        indiceAttivo = -1;
    };

    const aggiornaEvidenza = nuovoIndice => {
        if (!suggerimenti || !visibili.length) return;
        indiceAttivo = (nuovoIndice + visibili.length) % visibili.length;
        const bottoni = Array.from(suggerimenti.querySelectorAll('[role="option"]'));
        bottoni.forEach((btn, idx) => {
            const attivo = idx === indiceAttivo;
            btn.setAttribute('aria-selected', attivo ? 'true' : 'false');
            btn.classList.toggle('is-active', attivo);
        });
    };

    const selezionaComune = codice => {
        const scelto = mappa.get(codice);
        if (!scelto) return;
        popolaSelect(opzioniIniziali, scelto.codice);
        select.value = scelto.codice;
        input.value = scelto.nome;
        chiudiSuggerimenti();
        impostaStato(scelto.territorio ? `Selezionato: ${scelto.nome} — ${scelto.territorio}.` : `Selezionato: ${scelto.nome}.`);
        select.dispatchEvent(new Event('change', { bubbles: true }));
    };

    const filtra = () => {
        const query = normalizzaTestoComune(input.value);
        if (!query) {
            popolaSelect(opzioniIniziali, select.value);
            chiudiSuggerimenti();
            impostaStato(TESTO_AIUTO_COMUNE);
            return;
        }
        const prefissi = [];
        const interni = [];
        for (const item of opzioniIniziali) {
            if (item.nomeNorm.startsWith(query)) prefissi.push(item);
            else if (item.nomeNorm.includes(query)) interni.push(item);
        }
        const trovati = [...prefissi, ...interni];
        const corrente = mappa.get(select.value);
        const mantieniCorrente = corrente && corrente.nomeNorm === query ? corrente.codice : '';
        popolaSelect(trovati, mantieniCorrente);

        if (!trovati.length) {
            chiudiSuggerimenti();
            impostaStato(`Nessun comune campano trovato per «${input.value.trim()}».`);
            return;
        }

        const esatti = trovati.length === 1 ? '1 comune trovato' : `${trovati.length} comuni trovati`;
        impostaStato(`${esatti} per «${input.value.trim()}».`);

        if (!suggerimenti) return;
        visibili = trovati.slice(0, LIMITE_SUGGERIMENTI);
        indiceAttivo = 0;
        const nodi = visibili.map((item, idx) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'municipality-option' + (idx === 0 ? ' is-active' : '');
            btn.setAttribute('role', 'option');
            btn.setAttribute('aria-selected', idx === 0 ? 'true' : 'false');
            btn.dataset.codice = item.codice;
            const nomeEl = document.createElement('strong');
            nomeEl.textContent = item.nome;
            btn.append(nomeEl);
            if (item.territorio) {
                const terrEl = document.createElement('span');
                terrEl.className = 'picus-meta';
                terrEl.textContent = item.territorio;
                btn.append(terrEl);
            }
            btn.addEventListener('click', () => selezionaComune(item.codice));
            return btn;
        });
        suggerimenti.replaceChildren(...nodi);
        suggerimenti.hidden = false;
    };

    input.addEventListener('input', filtra);
    input.addEventListener('keydown', event => {
        if (!suggerimenti || suggerimenti.hidden || !visibili.length) return;
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            aggiornaEvidenza(indiceAttivo + 1);
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            aggiornaEvidenza(indiceAttivo - 1);
        } else if (event.key === 'Enter') {
            const scelto = visibili[indiceAttivo >= 0 ? indiceAttivo : 0];
            if (scelto) {
                event.preventDefault();
                selezionaComune(scelto.codice);
            }
        } else if (event.key === 'Escape') {
            event.preventDefault();
            chiudiSuggerimenti();
        }
    });

    select.addEventListener('change', () => {
        const scelto = mappa.get(select.value);
        if (!scelto) return;
        if (normalizzaTestoComune(input.value) !== scelto.nomeNorm) {
            input.value = scelto.nome;
        }
        chiudiSuggerimenti();
        impostaStato(scelto.territorio ? `Selezionato: ${scelto.nome} — ${scelto.territorio}.` : `Selezionato: ${scelto.nome}.`);
    });

    form?.addEventListener('reset', () => {
        input.value = '';
        popolaSelect(opzioniIniziali, '');
        chiudiSuggerimenti();
        impostaStato(TESTO_AIUTO_COMUNE);
    });

    return { filtra, selezionaComune, chiudiSuggerimenti };
}

const form = typeof document !== 'undefined' ? document.getElementById('richiestaForm') : null;
if (form) {
const campi = document.getElementById('campi-invio');
const button = document.getElementById('btnInvia');
const errore = document.getElementById('errore-invio');
const incerto = document.getElementById('invio-incerto');
const nuovo = document.getElementById('nuovo-invio');
const precedenti = document.getElementById('invii-precedenti');
const ricercaComune = collegaRicercaComune({
    input: document.getElementById('ricerca-comune'),
    select: document.getElementById('comune'),
    suggerimenti: document.getElementById('risultati-comune'),
    stato: document.getElementById('aiuto-comune'),
    form
});
const chiaveMemoria = 'picus:invio-pendente:' + form.dataset.utente;
// Il token renderizzato appartiene alla sessione che ha aperto il modulo.
const csrf = {
    headerName: form.dataset.csrfHeader,
    token: form.elements.namedItem(form.dataset.csrfParametro)?.value
};
let invio;
let occupato = false;
let sessioneDaRicaricare = false;

function leggiRiferimenti() {
    const salvati = JSON.parse(sessionStorage.getItem(chiaveMemoria));
    // Le pagine aperte prima dell’aggiornamento salvavano un singolo riferimento.
    const riferimenti = Array.isArray(salvati) ? salvati : salvati ? [{ ...salvati, attivo: true }] : [];
    if (riferimenti.some(rif => !rif || !/^[0-9a-f-]{36}$/i.test(rif.chiave) || !/^[0-9a-f]{64}$/.test(rif.impronta) || typeof rif.attivo !== 'boolean')) {
        throw new Error('Riferimento di invio non leggibile. Verifica le tue richieste prima di inviarne un’altra.');
    }
    return riferimenti;
}

function salvaRiferimenti(riferimenti) {
    if (riferimenti.length) sessionStorage.setItem(chiaveMemoria, JSON.stringify(riferimenti));
    else sessionStorage.removeItem(chiaveMemoria);
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
    const riferimenti = leggiRiferimenti();
    const attivo = riferimenti.find(rif => rif.attivo);
    if (attivo && attivo.impronta !== impronta) {
        throw new Error('C’è un invio da verificare. Reinserisci gli stessi dati o scegli «Ho verificato, inizia una nuova richiesta».');
    }
    const precedente = attivo ?? riferimenti.find(rif => rif.impronta === impronta);
    const chiave = precedente?.chiave || crypto.randomUUID();
    // Conserva solo riferimento e impronta: la descrizione non viene salvata nel browser.
    const altri = riferimenti.filter(rif => rif.chiave !== chiave);
    salvaRiferimenti([...altri, { chiave, impronta, attivo: true }]);
    return { chiave, dati, incerto: !!precedente, altri };
}

nuovo.addEventListener('click', () => {
    if (occupato || sessioneDaRicaricare) return;
    try {
        salvaRiferimenti(leggiRiferimenti().map(rif => ({ ...rif, attivo: false })));
    } catch {
        errore.textContent = 'Impossibile conservare il riferimento. Ricarica la pagina prima di iniziare una nuova richiesta.';
        return;
    }
    invio = undefined;
    form.reset();
    errore.textContent = '';
    incerto.hidden = true;
    precedenti.hidden = false;
    campi.disabled = false;
    button.textContent = 'Invia richiesta';
    form.elements.misuraId.focus();
});

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (occupato || sessioneDaRicaricare) return;
    occupato = true;
    button.disabled = true;
    nuovo.disabled = true;
    try {
        invio ??= await preparaInvio();
    } catch (causa) {
        errore.textContent = causa.message + ' Se la memoria del browser è disabilitata, abilitala per proteggere l’invio dai duplicati.';
        occupato = false;
        button.disabled = false;
        nuovo.disabled = false;
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
            method: 'POST', body: invio.dati, headers: { 'Idempotency-Key': invio.chiave }, csrf
        });
        salvaRiferimenti(invio.altri);
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
        sessioneDaRicaricare = [401, 403].includes(causa.stato);
        if (sessioneDaRicaricare) {
            errore.textContent = 'La sessione potrebbe essere cambiata. Ricarica la pagina o accedi di nuovo prima di inviare.';
        }
        if (causa.stato === 400 || (!invio.incerto && [401, 403, 429].includes(causa.stato))) {
            salvaRiferimenti(invio.altri);
            precedenti.hidden = invio.altri.length === 0;
            invio = undefined;
            campi.disabled = sessioneDaRicaricare;
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
        button.disabled = sessioneDaRicaricare;
        nuovo.disabled = sessioneDaRicaricare;
    }
});

try {
    if (!form.dataset.utente || !csrf.headerName || !csrf.token) {
        throw new Error('Sessione non disponibile. Ricarica la pagina o accedi di nuovo.');
    }
    const riferimenti = leggiRiferimenti();
    if (riferimenti.some(rif => rif.attivo)) {
        incerto.hidden = false;
        button.textContent = 'Verifica lo stesso invio';
    }
    precedenti.hidden = !riferimenti.some(rif => !rif.attivo);
    campi.disabled = false;
    button.disabled = false;
    document.getElementById('avvio-invio').hidden = true;
} catch (causa) {
    errore.textContent = causa.message;
}
}
