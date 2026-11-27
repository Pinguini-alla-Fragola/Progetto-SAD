import test from 'node:test';
import assert from 'node:assert/strict';

// Messaggi mostrati dalla pagina /recupera (US #33): conferma della richiesta, cambio riuscito, link non più valido
// con invito a una nuova richiesta, password da correggere sul campo. Il DOM è finto e scritto a mano; fetch è
// sostituito per rispondere come il backend.

const CONFERMA = "Se l'indirizzo corrisponde a un account attivo, riceverai un link per scegliere una nuova password. Il link vale 15 minuti.";
const LINK_NON_VALIDO = 'Il link non è valido, è scaduto oppure è già stato utilizzato. Richiedine uno nuovo.';

class Elemento {
    constructor(proprieta = {}) {
        this.textContent = '';
        this.hidden = false;
        this.disabled = false;
        this.attributi = {};
        this.ascoltatori = {};
        this.messoAFuoco = 0;
        Object.assign(this, proprieta);
    }

    setAttribute(nome, valore) { this.attributi[nome] = String(valore); }
    removeAttribute(nome) { delete this.attributi[nome]; }
    focus() { this.messoAFuoco++; }
    addEventListener(evento, azione) { this.ascoltatori[evento] = azione; }
}

/** Modulo finto: risponde ai selettori usati da recupero.js e conta i reset. */
function modulo(endpoint, figli, campi = {}) {
    const fieldset = new Elemento();
    const pulsante = new Elemento();
    return new Elemento({
        dataset: { endpoint },
        elements: campi,
        reset() { this.azzerato = (this.azzerato || 0) + 1; },
        querySelector(selettore) {
            if (selettore === 'fieldset') return fieldset;
            if (selettore === 'button[type="submit"]') return pulsante;
            return figli[selettore];
        },
        get fieldset() { return fieldset; },
        get pulsante() { return pulsante; }
    });
}

let caricamenti = 0;

/** Installa il DOM finto, sostituisce fetch e carica una copia nuova del modulo della pagina. */
async function caricaPagina(t, elementi, rispondi) {
    const documento = { getElementById: id => elementi[id] ?? null };
    const precedente = Object.getOwnPropertyDescriptor(globalThis, 'document');
    Object.defineProperty(globalThis, 'document', { value: documento, configurable: true });
    t.after(() => precedente ? Object.defineProperty(globalThis, 'document', precedente) : delete globalThis.document);

    const chiamate = [];
    t.mock.method(globalThis, 'fetch', async (url, opzioni) => {
        if (url === '/api/auth/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'prova' });
        chiamate.push({ url, ...opzioni, corpo: JSON.parse(opzioni.body) });
        return rispondi(url);
    });
    await import(`../../main/resources/static/js/recupero.js?pagina=${++caricamenti}`);
    return chiamate;
}

async function invia(form) {
    let impedito = false;
    await form.ascoltatori.submit({ preventDefault() { impedito = true; } });
    assert.ok(impedito, 'il modulo non deve ricaricare la pagina');
}

/** FormData finto: Node non accetta un modulo come argomento. */
function formDataFinto(t, valori) {
    const precedente = globalThis.FormData;
    globalThis.FormData = class { [Symbol.iterator]() { return Object.entries(valori)[Symbol.iterator](); } };
    t.after(() => { globalThis.FormData = precedente; });
}

function paginaRichiesta() {
    const esito = new Elemento();
    const form = modulo('/api/auth/recupera', {});
    return { form, esito, elementi: { 'richiesta-recupero': form, esito } };
}

function paginaCambio() {
    const esito = new Elemento();
    const errore = new Elemento({ hidden: true });
    const vaiAlLogin = new Elemento({ hidden: true });
    const nuovaRichiesta = new Elemento({ hidden: true });
    const password = new Elemento({ value: 'Password-nuova-2026' });
    const token = new Elemento({ value: 'token-del-link' });
    const form = modulo('/api/auth/recupera/conferma', {}, { nuovaPassword: password, token });
    return {
        form, esito, errore, vaiAlLogin, nuovaRichiesta, password,
        elementi: {
            'cambio-password': form, esito, 'errore-password': errore,
            'vai-al-login': vaiAlLogin, 'nuova-richiesta': nuovaRichiesta
        }
    };
}

test('richiesta del link: invia l\'email con il token CSRF e mostra la conferma del server', async t => {
    formDataFinto(t, { email: 'utente@picus.test' });
    const pagina = paginaRichiesta();
    const chiamate = await caricaPagina(t, pagina.elementi, () => Response.json({ messaggio: CONFERMA }));

    await invia(pagina.form);

    assert.equal(chiamate.length, 1);
    assert.equal(chiamate[0].url, '/api/auth/recupera');
    assert.equal(chiamate[0].method, 'POST');
    assert.equal(chiamate[0].headers['X-CSRF-TOKEN'], 'prova');
    assert.deepEqual(chiamate[0].corpo, { email: 'utente@picus.test' });
    assert.equal(pagina.esito.textContent, CONFERMA);
    assert.equal(pagina.form.azzerato, 1);
    assert.equal(pagina.form.pulsante.disabled, false);
});

test('richiesta del link oltre il limite: messaggio dedicato e nessun reset del modulo', async t => {
    formDataFinto(t, { email: 'utente@picus.test' });
    const pagina = paginaRichiesta();
    await caricaPagina(t, pagina.elementi, () => Response.json({ messaggio: 'dal server' }, { status: 429 }));

    await invia(pagina.form);

    assert.equal(pagina.esito.textContent, 'Hai fatto troppe richieste per questo indirizzo. Riprova tra qualche minuto.');
    assert.equal(pagina.form.azzerato, undefined);
    assert.equal(pagina.form.pulsante.disabled, false);
});

test('cambio riuscito: conferma del server, modulo chiuso e collegamento all\'accesso', async t => {
    const pagina = paginaCambio();
    const chiamate = await caricaPagina(t, pagina.elementi,
        () => Response.json({ messaggio: 'Password cambiata. Ora puoi accedere con la nuova password.' }));

    await invia(pagina.form);

    assert.equal(chiamate[0].url, '/api/auth/recupera/conferma');
    assert.deepEqual(chiamate[0].corpo, { token: 'token-del-link', nuovaPassword: 'Password-nuova-2026' });
    assert.equal(pagina.esito.textContent, 'Password cambiata. Ora puoi accedere con la nuova password.');
    assert.equal(pagina.form.fieldset.disabled, true);
    assert.equal(pagina.form.pulsante.disabled, true);
    assert.equal(pagina.vaiAlLogin.hidden, false);
    assert.equal(pagina.nuovaRichiesta.hidden, true);
    assert.equal(pagina.form.azzerato, 1);
});

test('link scaduto o già usato (410): messaggio del server, modulo chiuso e invito a una nuova richiesta', async t => {
    const pagina = paginaCambio();
    await caricaPagina(t, pagina.elementi, () => Response.json({ messaggio: LINK_NON_VALIDO }, { status: 410 }));

    await invia(pagina.form);

    assert.equal(pagina.esito.textContent, LINK_NON_VALIDO);
    assert.equal(pagina.nuovaRichiesta.hidden, false);
    assert.equal(pagina.vaiAlLogin.hidden, true);
    assert.equal(pagina.form.fieldset.disabled, true);
    assert.equal(pagina.form.azzerato, 1, 'la password digitata va svuotata');
});

test('password da correggere (400): errore sul campo, modulo ancora attivo per riprovare con lo stesso link', async t => {
    const pagina = paginaCambio();
    const cosaCorreggere = 'La password deve contenere almeno 12 caratteri.';
    await caricaPagina(t, pagina.elementi, () => Response.json(
        { messaggio: cosaCorreggere, campi: { nuovaPassword: cosaCorreggere } }, { status: 400 }));

    await invia(pagina.form);

    assert.equal(pagina.errore.textContent, cosaCorreggere);
    assert.equal(pagina.errore.hidden, false);
    assert.equal(pagina.password.attributi['aria-invalid'], 'true');
    assert.equal(pagina.password.messoAFuoco, 1);
    assert.equal(pagina.form.fieldset.disabled, false);
    assert.equal(pagina.form.pulsante.disabled, false);
    assert.equal(pagina.form.azzerato, undefined);
    assert.equal(pagina.nuovaRichiesta.hidden, true);
    assert.equal(pagina.esito.textContent, '');
});