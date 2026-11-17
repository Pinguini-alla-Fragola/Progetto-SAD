import test from 'node:test';
import assert from 'node:assert/strict';

const dati = { misuraId: 1, tipoDestinatario: 'PERSONA', comuneCodice: 'napoli', description: 'Esigenza sintetica' };
const originali = Object.fromEntries(['document', 'sessionStorage', 'fetch'].map(nome => [nome, Object.getOwnPropertyDescriptor(globalThis, nome)]));

async function pagina(t, storage, fetch, utente = 'opt-a') {
    const nodi = new Map();
    const nodo = id => {
        if (!nodi.has(id)) nodi.set(id, {
            hidden: true, disabled: false, textContent: '', dataset: {}, eventi: {},
            addEventListener(nome, azione) { this.eventi[nome] = azione; },
            querySelectorAll() { return []; }, querySelector() { return null; }, focus() {}
        });
        return nodi.get(id);
    };
    const form = nodo('richiestaForm');
    form.dataset.utente = utente;
    form.elements = Object.fromEntries(Object.entries(dati).map(([nome, value]) => [nome, { value, selectedOptions: [{ textContent: String(value) }] }]));
    nodo('btnInvia').disabled = true;
    for (const [nome, value] of Object.entries({ document: { getElementById: nodo }, sessionStorage: storage, fetch })) {
        Object.defineProperty(globalThis, nome, { value, configurable: true, writable: true });
        t.after(() => originali[nome] ? Object.defineProperty(globalThis, nome, originali[nome]) : delete globalThis[nome]);
    }
    await import('../../main/resources/static/js/nuova-richiesta.js?test=' + crypto.randomUUID());
    return { nodo, form, invia: () => form.eventi.submit({ preventDefault() {} }) };
}

function memoria() {
    const valori = new Map();
    return { getItem: k => valori.get(k) ?? null, setItem: (k, v) => valori.set(k, v), removeItem: k => valori.delete(k), valori };
}

test('dopo la perdita della risposta e il reload riprende la stessa chiave senza memorizzare la descrizione', async t => {
    const storage = memoria();
    const chiavi = [];
    let persa = true;
    const fetch = async (url, options) => {
        if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        chiavi.push(options.headers['Idempotency-Key']);
        if (persa) { persa = false; throw new TypeError('Risposta persa'); }
        return Response.json({ id: 'pratica' });
    };
    await (await pagina(t, storage, fetch)).invia();
    const salvato = [...storage.valori.values()].join();
    assert.ok(salvato, 'il riferimento deve precedere il POST');
    assert.ok(!salvato.includes(dati.description), 'la descrizione resta solo nel modulo');
    const ripresa = await pagina(t, storage, fetch);
    assert.equal(ripresa.nodo('invio-incerto').hidden, false);
    await ripresa.invia();
    assert.equal(chiavi.length, 2);
    assert.equal(chiavi[0], chiavi[1]);
    assert.equal(ripresa.nodo('conferma').hidden, false);
    assert.equal(storage.valori.size, 0);
});

test('un invio incerto non accetta dati diversi dopo il reload', async t => {
    const storage = memoria();
    let post = 0;
    const fetch = async (url) => {
        if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        post++; throw new TypeError('Risposta persa');
    };
    await (await pagina(t, storage, fetch)).invia();
    const ripresa = await pagina(t, storage, fetch);
    ripresa.form.elements.description.value = 'Altro bisogno';
    await ripresa.invia();
    assert.equal(post, 1, 'non inviare un nuovo payload con un riferimento incerto');
    assert.equal(ripresa.nodo('campi-invio').disabled, false);
    assert.ok(ripresa.nodo('errore-invio').textContent);
});

test('il recupero è separato per account OPT', async t => {
    const storage = memoria();
    const chiavi = [];
    const fetch = async (url, options) => {
        if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        chiavi.push(options.headers['Idempotency-Key']); throw new TypeError('Risposta persa');
    };
    await (await pagina(t, storage, fetch, 'opt-a')).invia();
    const altra = await pagina(t, storage, fetch, 'opt-b');
    assert.equal(altra.nodo('invio-incerto').hidden, true);
    await altra.invia();
    assert.notEqual(chiavi[0], chiavi[1]);
    await (await pagina(t, storage, fetch, 'opt-a')).invia();
    assert.equal(chiavi[0], chiavi[2]);
});

test('se il riferimento non può essere conservato non esegue il POST', async t => {
    let post = 0;
    const storage = { getItem() { return null; }, setItem() { throw new Error('Storage non disponibile'); } };
    const ui = await pagina(t, storage, async () => { post++; throw new TypeError('Non deve inviare'); });
    await ui.invia();
    assert.equal(post, 0);
    assert.ok(ui.nodo('errore-invio').textContent);
});

test('un rifiuto certo libera il riferimento e consente di correggere i dati', async t => {
    const storage = memoria();
    const chiavi = [];
    const fetch = async (url, options) => {
        if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        chiavi.push(options.headers['Idempotency-Key']);
        return chiavi.length === 1 ? Response.json({}, { status: 400 }) : Response.json({ id: 'pratica' });
    };
    const ui = await pagina(t, storage, fetch);
    await ui.invia();
    assert.equal(storage.valori.size, 0);
    assert.equal(ui.nodo('campi-invio').disabled, false);
    ui.form.elements.description.value = 'Bisogno corretto';
    await ui.invia();
    assert.notEqual(chiavi[0], chiavi[1]);
    assert.equal(ui.nodo('conferma').hidden, false);
});

test('un 403 dopo un invio incerto non perde il riferimento al successivo reload', async t => {
    const storage = memoria();
    const chiavi = [];
    const fetch = async (url, options) => {
        if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        chiavi.push(options.headers['Idempotency-Key']);
        if (chiavi.length === 1) throw new TypeError('Risposta persa');
        return chiavi.length === 2 ? Response.json({}, { status: 403 }) : Response.json({ id: 'pratica' });
    };
    await (await pagina(t, storage, fetch)).invia();
    await (await pagina(t, storage, fetch)).invia();
    assert.equal(storage.valori.size, 1);
    await (await pagina(t, storage, fetch)).invia();
    assert.equal(new Set(chiavi).size, 1);
    assert.equal(storage.valori.size, 0);
});

test('un 400 dopo il reload di un invio incerto consente di correggere il payload rifiutato', async t => {
    const storage = memoria();
    const chiavi = [];
    const fetch = async (url, options) => {
        if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        chiavi.push(options.headers['Idempotency-Key']);
        if (chiavi.length === 1) throw new TypeError('Risposta persa');
        return chiavi.length === 2 ? Response.json({}, { status: 400 }) : Response.json({ id: 'pratica' });
    };
    await (await pagina(t, storage, fetch)).invia();
    const ripresa = await pagina(t, storage, fetch);
    await ripresa.invia();
    assert.equal(storage.valori.size, 0);
    assert.equal(ripresa.nodo('campi-invio').disabled, false);
    ripresa.form.elements.description.value = 'Descrizione corretta';
    await ripresa.invia();
    assert.equal(chiavi[0], chiavi[1]);
    assert.notEqual(chiavi[1], chiavi[2]);
    assert.equal(ripresa.nodo('conferma').hidden, false);
});
