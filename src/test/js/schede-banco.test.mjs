import test from 'node:test';
import assert from 'node:assert/strict';

class FakeNode {
    children = [];
    attributes = new Map();
    dataset = {};
    eventi = {};
    hidden = false;
    disabled = false;
    value = '';
    tabIndex = 0;
    classList = {
        set: new Set(),
        add: (...c) => c.forEach(x => this.classList.set.add(x)),
        remove: (...c) => c.forEach(x => this.classList.set.delete(x)),
        toggle: (c, force) => {
            if (force === undefined) {
                if (this.classList.set.has(c)) this.classList.set.delete(c);
                else this.classList.set.add(c);
            } else if (force) this.classList.set.add(c);
            else this.classList.set.delete(c);
        },
        contains: c => this.classList.set.has(c)
    };
    constructor(tag = 'div', className = '') {
        this.tagName = tag.toUpperCase();
        if (className) className.split(/\s+/).forEach(c => this.classList.add(c));
    }
    set className(v) {
        this.classList.set = new Set(String(v || '').split(/\s+/).filter(Boolean));
    }
    get className() { return [...this.classList.set].join(' '); }
    set textContent(v) { this._text = String(v ?? ''); this.children = []; }
    get textContent() {
        return this.children.length ? this.children.map(c => c.textContent).join('') : (this._text ?? '');
    }
    append(...nodes) {
        for (const n of nodes) {
            const node = typeof n === 'string' ? { textContent: n, children: [], contains: () => false } : n;
            if (node !== n) node.textContent = n;
            node.parentElement = this;
            this.children.push(node);
        }
    }
    prepend(...nodes) {
        for (const n of [...nodes].reverse()) {
            n.parentElement = this;
            this.children.unshift(n);
        }
    }
    add(opt) { this.append(opt); }
    replaceChildren(...nodes) { this.children = []; this.append(...nodes); }
    remove() {
        if (this.parentElement) {
            this.parentElement.children = this.parentElement.children.filter(c => c !== this);
        }
    }
    setAttribute(k, v) { this.attributes.set(k, String(v)); }
    getAttribute(k) { return this.attributes.get(k) ?? null; }
    removeAttribute(k) { this.attributes.delete(k); }
    addEventListener(type, fn) { (this.eventi[type] ??= []).push(fn); }
    async dispatch(type, ev = { preventDefault() {} }) {
        for (const fn of this.eventi[type] || []) await fn(ev);
    }
    focus() {}
    contains(n) { return this === n || this.children.some(c => c.contains?.(n)); }
    matchesSelector(sel) {
        if (sel.startsWith('#')) return this.id === sel.slice(1);
        if (sel.startsWith('.')) {
            const cls = sel.slice(1).split('[')[0];
            if (!this.classList.contains(cls)) return false;
            if (sel.includes('[data-ruolo="OPERATORE"]')) return this.dataset.ruolo === 'OPERATORE';
            return true;
        }
        if (sel === '[aria-invalid]') return this.attributes.has('aria-invalid');
        if (sel === 'button[type="submit"]') return this.tagName === 'BUTTON' && this.type === 'submit';
        if (sel === 'input[type=checkbox]') return this.tagName === 'INPUT' && this.type === 'checkbox';
        return this.tagName.toLowerCase() === sel.toLowerCase();
    }
    querySelectorAll(sel) {
        const parts = sel.split(/\s+/);
        if (parts.length > 1) {
            let current = [this];
            for (const p of parts) {
                current = current.flatMap(n => n.querySelectorAll(p));
            }
            return current;
        }
        return this.children.flatMap(c => [
            ...(c.matchesSelector?.(sel) ? [c] : []),
            ...(c.querySelectorAll ? c.querySelectorAll(sel) : [])
        ]);
    }
    querySelector(sel) { return this.querySelectorAll(sel)[0] ?? null; }
    closest() { return null; }
}

async function setupDom(t, fetchHandler, extraOps = []) {
    const byId = new Map();
    const getOrCreate = (id, tag = 'div') => {
        if (!byId.has(id)) {
            const node = new FakeNode(tag);
            node.id = id;
            byId.set(id, node);
        }
        return byId.get(id);
    };

    const root = new FakeNode('body');
    const accountBox = getOrCreate('account');
    root.append(accountBox);

    const ops = [
        { id: 'op-1', nome: 'Mario Rossi', riferimento: 'INTERNO_BANCO', stato: 'ATTIVO', email: 'segreto1@picus.test' },
        { id: 'op-2', nome: 'Mario Rossi', riferimento: 'ESTERNO_PAGATO_BANCO', stato: 'REVOCATO', email: 'segreto2@picus.test' },
        ...extraOps
    ];
    for (const op of ops) {
        const row = new FakeNode('article', 'account-row');
        row.dataset = { id: op.id, nome: op.nome, riferimento: op.riferimento, ruolo: 'OPERATORE', stato: op.stato, email: op.email };
        accountBox.append(row);
    }

    const form = getOrCreate('riepilogo-schede', 'form');
    const annoIn = new FakeNode('input'); annoIn.name = 'anno'; annoIn.value = '2025';
    const trimIn = new FakeNode('select'); trimIn.name = 'trimestre'; trimIn.value = '1';
    const submitBtn = new FakeNode('button'); submitBtn.type = 'submit';
    form.append(annoIn, trimIn, submitBtn);
    form.elements = { anno: annoIn, trimestre: trimIn };

    const lista = getOrCreate('riepilogo-schede-lista');
    const esito = getOrCreate('riepilogo-schede-esito');
    const riprova = getOrCreate('riepilogo-schede-riprova', 'button');
    riprova.hidden = true;
    root.append(form, esito, riprova, lista);

    const doc = {
        activeElement: null,
        createElement: tag => new FakeNode(tag),
        createTextNode: text => { const n = new FakeNode('#text'); n._text = String(text); return n; },
        getElementById: id => byId.get(id) ?? null,
        querySelectorAll: sel => root.querySelectorAll(sel)
    };
    const prevDoc = Object.getOwnPropertyDescriptor(globalThis, 'document');
    const prevOpt = Object.getOwnPropertyDescriptor(globalThis, 'Option');
    Object.defineProperty(globalThis, 'document', { value: doc, configurable: true });
    Object.defineProperty(globalThis, 'Option', {
        value: class extends FakeNode { constructor(t, v) { super('option'); this._text = t; this.value = v; } },
        configurable: true
    });
    t.after(() => {
        if (prevDoc) Object.defineProperty(globalThis, 'document', prevDoc); else delete globalThis.document;
        if (prevOpt) Object.defineProperty(globalThis, 'Option', prevOpt); else delete globalThis.Option;
    });
    t.mock.method(globalThis, 'fetch', fetchHandler);

    const modUrl = new URL('../../main/resources/static/js/schede-banco.js', import.meta.url);
    modUrl.searchParams.set('test', String(Math.random()));
    await import(modUrl);

    return { form, annoIn, trimIn, submitBtn, lista, esito, riprova };
}

test('mostra omonimi senza email, Long.MAX_VALUE nel dettaglio da JSON grezzo, zeri e aggiorna rubrica senza reload', async t => {
    const chiamate = [];
    let nuovoOperatoreAttivo = false;
    const ui = await setupDom(t, async url => {
        chiamate.push(url);
        if (url === '/api/utenze/rubrica?ruolo=OPERATORE') {
            return Response.json([
                { id: 'op-1', nome: 'Mario Rossi', riferimento: 'INTERNO_BANCO',
                    stato: nuovoOperatoreAttivo ? 'REVOCATO' : 'ATTIVO' },
                { id: 'op-2', nome: 'Mario Rossi', riferimento: 'ESTERNO_PAGATO_BANCO', stato: 'ATTIVO' },
                ...(nuovoOperatoreAttivo ? [
                    { id: 'op-nuovo', nome: 'Chiara Neri', riferimento: 'INTERNO_BANCO', stato: 'ATTIVO' }
                ] : [])
            ]);
        }
        if (url.startsWith('/api/monitoraggio/schede-operatore/ricevute/riepilogo')) {
            return Response.json({
                schede: [
                    { id: 'sch-1', operatoreId: 'op-1', trimestre: { anno: 2025, numero: 1 }, inviataIl: '2025-04-02T09:00:00Z' },
                    { id: 'sch-2', operatoreId: 'op-2', trimestre: { anno: 2025, numero: 1 }, inviataIl: '2025-04-03T10:00:00Z' },
                    { id: 'sch-4', operatoreId: 'op-sconosciuto', trimestre: { anno: 2025, numero: 1 }, inviataIl: '2025-04-05T10:00:00Z' },
                    ...(nuovoOperatoreAttivo ? [
                        { id: 'sch-3', operatoreId: 'op-nuovo', trimestre: { anno: 2025, numero: 1 }, inviataIl: '2025-04-04T10:00:00Z' }
                    ] : [])
                ],
                conteggi: { donne: '9007199254740993', minori: '0' }
            });
        }
        if (url === '/api/monitoraggio/schede-operatore/sch-1') {
            return new Response(JSON.stringify({
                sintesi: { id: 'sch-1', operatoreId: 'op-1', trimestre: { anno: 2025, numero: 1 }, inviataIl: '2025-04-02T09:00:00Z' },
                dati: {
                    nome: 'Mario', cognome: 'Rossi', luogoNascita: 'Napoli', dataNascita: '1985-01-01',
                    codiceFiscale: 'RSSMRA85A01F839K', qualifica: 'Educatore', tipologiaContratto: 'Dipendente',
                    areaLivello: 'Profilo "D1" 9223372036854775807', conteggi: { __V1__: 0 }
                }
            }).replace('"conteggi":{"__V1__":0}',
                '"ore":9999999999999.999999,"costoOrarioLordo":20.00,"conteggi":{"donne":9223372036854775807,"minori":0}'), {
                status: 200,
                headers: { 'Content-Type': 'application/json' }
            });
        }
        throw new Error('URL imprevisto: ' + url);
    });

    await ui.form.dispatch('submit');
    assert.doesNotMatch(ui.lista.textContent, /Chiara Neri|Account revocato/);
    nuovoOperatoreAttivo = true;
    await ui.form.dispatch('submit');
    assert.equal(chiamate.filter(url => url === '/api/utenze/rubrica?ruolo=OPERATORE').length, 2);
    assert.equal(ui.lista.hidden, false);
    const txt = ui.lista.textContent;
    assert.match(txt, /Mario Rossi/);
    assert.match(txt, /Chiara Neri/);
    assert.match(txt, /Operatore \(op-sconosciuto\)/);
    assert.match(txt, /ID account op-sconosciuto/);
    assert.match(txt, /ID account op-1/);
    assert.match(txt, /ID account op-2/);
    assert.match(txt, /Account revocato/);
    assert.doesNotMatch(txt, /segreto1@picus\.test/);
    assert.match(txt, /9007199254740993/);
    assert.match(txt, /minori0/);

    const toggles = ui.lista.querySelectorAll('button').filter(b => b.textContent === 'Apri scheda');
    assert.equal(toggles.length, 4);
    await toggles[0].dispatch('click');
    assert.equal(toggles[0].textContent, 'Chiudi scheda');
    const detailTxt = ui.lista.textContent;
    assert.match(detailTxt, /RSSMRA85A01F839K/);
    assert.match(detailTxt, /donne9223372036854775807/);
    assert.match(detailTxt, /9999999999999\.999999/);
    assert.match(detailTxt, /20\.00 €/);
    assert.match(detailTxt, /Profilo "D1" 9223372036854775807/);
    assert.doesNotMatch(detailTxt, /9223372036854776000/);
    assert.doesNotMatch(detailTxt, /Firma/);
});

test('distingue assenza schede da schede presenti senza voci rendicontabili', async t => {
    let modo = 'vuoto';
    const ui = await setupDom(t, async url => {
        if (url === '/api/utenze/rubrica?ruolo=OPERATORE') return Response.json({}, { status: 503 });
        if (modo === 'vuoto') return Response.json({ schede: [], conteggi: {} });
        return Response.json({
            schede: [{ id: 'sch-1', operatoreId: 'op-1', trimestre: { anno: 2025, numero: 2 }, inviataIl: '2025-07-01T08:00:00Z' }],
            conteggi: {}
        });
    });

    await ui.form.dispatch('submit');
    assert.match(ui.lista.textContent, /Nessuna scheda ricevuta per il trimestre selezionato/);
    assert.doesNotMatch(ui.lista.textContent, /nessuna voce rendicontabile/);

    modo = 'senza-voci';
    await ui.form.dispatch('submit');
    assert.match(ui.lista.textContent, /Mario Rossi/);
    assert.match(ui.lista.textContent, /nessuna voce rendicontabile nei totali/);
});

test('scarta risposte tardive di riepilogo e dettaglio al cambio periodo e gestisce 401/503', async t => {
    const p1 = Promise.withResolvers();
    const d1 = Promise.withResolvers();
    let modo = 'p1';
    const ui = await setupDom(t, async url => {
        if (url === '/api/utenze/rubrica?ruolo=OPERATORE') return Response.json({}, { status: 503 });
        if (url.includes('trimestre=1')) return p1.promise;
        if (url.includes('trimestre=2')) return Response.json({
            schede: [{ id: 'sch-2', operatoreId: 'op-2', trimestre: { anno: 2025, numero: 2 }, inviataIl: '2025-07-02T09:00:00Z' }],
            conteggi: { donne: '2' }
        });
        if (url.includes('/sch-2')) return d1.promise;
        if (modo === 'err503') return Response.json({}, { status: 503 });
        return new Response(null, { status: 401 });
    });

    const f1 = ui.form.dispatch('submit');
    assert.equal(ui.submitBtn.disabled, true);
    ui.trimIn.value = '2';
    await ui.form.dispatch('input');
    assert.equal(ui.lista.hidden, true);
    assert.equal(ui.submitBtn.disabled, false);
    await ui.form.dispatch('submit');
    p1.resolve(Response.json({ schede: [{ id: 'sch-1', operatoreId: 'op-1', trimestre: { anno: 2025, numero: 1 }, inviataIl: '2025-04-02T09:00:00Z' }], conteggi: { donne: '999' } }));
    await f1;
    assert.doesNotMatch(ui.lista.textContent, /999/);
    assert.match(ui.lista.textContent, /Account revocato/);

    const toggle = ui.lista.querySelectorAll('button').find(b => b.textContent === 'Apri scheda');
    const articolo = ui.lista.querySelectorAll('.report-record')[0];
    const dettaglioObsoleto = toggle.dispatch('click');
    ui.trimIn.value = '3';
    await ui.form.dispatch('input');
    d1.resolve(Response.json({
        sintesi: { id: 'sch-2', operatoreId: 'op-2', trimestre: { anno: 2025, numero: 2 }, inviataIl: '2025-07-02T09:00:00Z' },
        dati: { nome: 'VECCHIO', cognome: 'TARDIVO', conteggi: {} }
    }));
    await dettaglioObsoleto;
    assert.equal(ui.lista.hidden, true);
    assert.doesNotMatch(ui.lista.textContent, /TARDIVO/);
    assert.doesNotMatch(articolo.textContent, /TARDIVO/);

    modo = 'err503';
    await ui.form.dispatch('submit');
    assert.equal(ui.riprova.hidden, false);

    modo = 'err401';
    await ui.riprova.dispatch('click');
    assert.match(ui.esito.textContent, /sessione/i);
    assert.match(ui.esito.textContent, /Accedi di nuovo/);
});
