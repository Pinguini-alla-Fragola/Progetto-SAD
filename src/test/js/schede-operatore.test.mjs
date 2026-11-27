import test from 'node:test';
import assert from 'node:assert/strict';
import {
    initSchedeOperatore,
    normalizzaDecimaleScheda,
    serializzaInvioScheda
} from '../../main/resources/static/js/schede-operatore.js';

class FakeNode {
    children = [];
    attributes = new Map();
    dataset = {};
    eventi = {};
    hidden = false;
    disabled = false;
    required = false;
    value = '';
    textContent = '';
    classList = {
        set: new Set(),
        add: (...c) => c.forEach(x => this.classList.set.add(x)),
        remove: (...c) => c.forEach(x => this.classList.set.delete(x)),
        toggle: (c, f) => {
            if (f === undefined) {
                if (this.classList.set.has(c)) this.classList.set.delete(c);
                else this.classList.set.add(c);
            } else if (f) this.classList.set.add(c);
            else this.classList.set.delete(c);
        },
        contains: c => this.classList.set.has(c)
    };
    constructor(tag = 'div', id = null) {
        this.tagName = tag.toUpperCase();
        this.id = id;
    }
    append(...nodes) {
        for (const n of nodes) {
            if (typeof n === 'string') {
                this.textContent += n;
            } else {
                n.parentElement = this;
                this.children.push(n);
            }
        }
    }
    replaceChildren(...nodes) { this.children = []; this.textContent = ''; this.append(...nodes); }
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
    closest() { return null; }
    querySelectorAll(sel) {
        if (sel === 'input[data-categoria]') {
            return this.children.flatMap(c => [
                ...(c.tagName === 'INPUT' && c.dataset.categoria ? [c] : []),
                ...c.querySelectorAll(sel)
            ]);
        }
        if (sel === '.picus-errore:not(.picus-esito)') return [];
        if (sel === '[aria-invalid]') return [];
        if (sel === 'button[type="submit"]') return this._submit ? [this._submit] : [];
        return [];
    }
    querySelector(sel) { return this.querySelectorAll(sel)[0] ?? null; }
}

test('normalizzaDecimaleScheda valida i limiti di 13 cifre intere e 6 decimali senza perdere precisione', () => {
    assert.equal(normalizzaDecimaleScheda('1234567890123.123456', 'ore'), '1234567890123.123456');
    assert.equal(normalizzaDecimaleScheda('9999999999999.999999', 'costo'), '9999999999999.999999');
    assert.equal(normalizzaDecimaleScheda('12,50', 'ore'), '12.50');
    assert.equal(normalizzaDecimaleScheda('0', 'ore'), '0');
    assert.equal(normalizzaDecimaleScheda('0,00', 'ore'), '0.00');
    assert.equal(normalizzaDecimaleScheda('007.5', 'ore'), '7.5');

    for (const invalido of ['', '   ', '-1', '10000000000000', '1.1234567', '1e2', 'abc']) {
        assert.throws(() => normalizzaDecimaleScheda(invalido, 'ore'), Error);
    }
});

test('serializzaInvioScheda inserisce ore e costoOrarioLordo come numeri JSON esatti e serializza i testi normalmente', () => {
    const raw = serializzaInvioScheda({
        nome: 'Test "1234567890123.123456"',
        cognome: 'Rossi',
        luogoNascita: null,
        dataNascita: '1985-02-10',
        codiceFiscale: null,
        qualifica: null,
        tipologiaContratto: null,
        areaLivello: null,
        ore: '1234567890123.123456',
        costoOrarioLordo: '9999999999999.999999',
        conteggi: { minori: 4 }
    });
    assert.match(raw, /"ore":1234567890123\.123456,/);
    assert.match(raw, /"costoOrarioLordo":9999999999999\.999999,/);
    assert.match(raw, /"nome":"Test \\"1234567890123\.123456\\""/);
});

test('invio dal form schede-operatore trasmette nel corpo HTTP grezzo i decimali ad alta precisione non quotati', async t => {
    const form = new FakeNode('form', 'scheda');
    const campiInvio = new FakeNode('fieldset', 'scheda-campi-invio');
    const infoConfig = new FakeNode('p', 'scheda-configurazione-info');
    const esitoForm = new FakeNode('p', 'scheda-esito');
    const voci = new FakeNode('div', 'voci-scheda');
    const storico = new FakeNode('div', 'schede-mie');
    const esitoStorico = new FakeNode('p', 'schede-mie-esito');
    const btnAggiorna = new FakeNode('button', 'schede-mie-aggiorna');
    const submitBtn = new FakeNode('button'); submitBtn.type = 'submit';
    form._submit = submitBtn;

    const controlli = {
        anno: Object.assign(new FakeNode('input'), { value: '2026' }),
        trimestre: Object.assign(new FakeNode('select'), { value: '3' }),
        nome: Object.assign(new FakeNode('input'), { value: 'Ada "9999999999999.999999"' }),
        cognome: Object.assign(new FakeNode('input'), { value: 'Verdi' }),
        luogoNascita: Object.assign(new FakeNode('input'), { value: '' }),
        dataNascita: Object.assign(new FakeNode('input'), { value: '' }),
        codiceFiscale: Object.assign(new FakeNode('input'), { value: '' }),
        qualifica: Object.assign(new FakeNode('input'), { value: '' }),
        tipologiaContratto: Object.assign(new FakeNode('input'), { value: '' }),
        areaLivello: Object.assign(new FakeNode('input'), { value: '' }),
        ore: Object.assign(new FakeNode('input'), { value: '1234567890123.123456' }),
        costoOrarioLordo: Object.assign(new FakeNode('input'), { value: '9999999999999,999999' })
    };
    form.elements = controlli;

    const byId = new Map([
        ['scheda', form],
        ['scheda-campi-invio', campiInvio],
        ['scheda-configurazione-info', infoConfig],
        ['scheda-esito', esitoForm],
        ['voci-scheda', voci],
        ['schede-mie', storico],
        ['schede-mie-esito', esitoStorico],
        ['schede-mie-aggiorna', btnAggiorna]
    ]);
    const doc = {
        body: { dataset: {} },
        activeElement: null,
        getElementById: id => byId.get(id) ?? null,
        createElement: tag => new FakeNode(tag),
        createTextNode: text => Object.assign(new FakeNode('#text'), { textContent: String(text) })
    };
    const prevDoc = Object.getOwnPropertyDescriptor(globalThis, 'document');
    Object.defineProperty(globalThis, 'document', { value: doc, configurable: true });
    t.after(() => {
        if (prevDoc) Object.defineProperty(globalThis, 'document', prevDoc);
        else delete globalThis.document;
    });

    let invioHttp = null;
    t.mock.method(globalThis, 'fetch', async (url, options = {}) => {
        if (url.startsWith('/api/monitoraggio/schede-operatore/modulo')) {
            return Response.json({
                trimestre: { anno: 2026, numero: 3 },
                versione: '0.1',
                campi: [{ nome: 'ore', obbligatorio: true }, { nome: 'costoOrarioLordo', obbligatorio: true }],
                configurazioneCategorie: {
                    codiceVersione: 'v1',
                    stato: 'PUBBLICATA',
                    categorie: [{ codice: 'minori', descrizione: 'Minori', definizione: 'Def', ordine: 0 }]
                }
            });
        }
        if (url === '/api/monitoraggio/schede-operatore/mie') {
            return Response.json([]);
        }
        if (url === '/api/auth/csrf') {
            return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf-scheda' });
        }
        if (options.method === 'POST') {
            invioHttp = { url, options };
            return Response.json({ id: 'sch-1', trimestre: { anno: 2026, numero: 3 } }, { status: 201 });
        }
        throw new Error('URL imprevisto: ' + url);
    });

    await initSchedeOperatore();
    controlli.ore.value = '1234567890123.123456';
    controlli.costoOrarioLordo.value = '9999999999999,999999';
    voci.querySelectorAll('input[data-categoria]')[0].value = '2';

    await form.dispatch('submit', { preventDefault() {} });

    assert.ok(invioHttp, 'POST inviato');
    assert.equal(invioHttp.options.headers['Content-Type'], 'application/json');
    assert.equal(typeof invioHttp.options.body, 'string');
    assert.match(invioHttp.options.body, /"ore":1234567890123\.123456,/);
    assert.match(invioHttp.options.body, /"costoOrarioLordo":9999999999999\.999999,/);
    assert.match(invioHttp.options.body, /"nome":"Ada \\"9999999999999\.999999\\""/);
    assert.match(invioHttp.options.body, /"conteggi":\{"minori":2\}/);
});
