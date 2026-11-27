import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const TEMPLATE_DETTAGLIO = new URL('../../main/resources/templates/richieste/dettaglio.html', import.meta.url);
const MOTIVO_SEGRETO = 'Motivo riservato di verifica 123';

class FakeNode {
    children = [];
    attributes = new Map();
    dataset = {};
    eventi = {};
    hidden = false;
    disabled = false;
    value = '';
    textContent = '';
    classList = {
        set: new Set(),
        add: (...c) => c.forEach(x => this.classList.set.add(x)),
        remove: (...c) => c.forEach(x => this.classList.set.delete(x)),
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
                this.children.push(n);
                if (n.textContent) this.textContent += n.textContent;
            }
        }
    }
    replaceChildren(...nodes) { this.children = []; this.textContent = ''; this.append(...nodes); }
    remove() {}
    setAttribute(k, v) { this.attributes.set(k, String(v)); }
    hasAttribute(k) { return this.attributes.has(k); }
    removeAttribute(k) { this.attributes.delete(k); }
    focus() { document.activeElement = this; }
    showModal() { document._dialog = this; }
    addEventListener(type, fn) { (this.eventi[type] ??= []).push(fn); }
    async dispatch(type, ev = {}) {
        for (const fn of this.eventi[type] || []) await fn(ev);
    }
}

test('il template di annullamento usa method=post e controlli disabilitati prima del JS', () => {
    const html = readFileSync(TEMPLATE_DETTAGLIO, 'utf8');
    const sezione = html.match(/<form id="form-annullamento"[\s\S]*?<\/form>/)?.[0];
    assert.ok(sezione, 'form #form-annullamento presente in dettaglio.html');
    assert.match(sezione, /<form id="form-annullamento"[^>]*method="post"/);
    assert.match(sezione, /<fieldset id="campi-annullamento"[^>]*\bdisabled\b/);
    assert.match(sezione, /<textarea id="annullamento-motivo"[^>]*\bdisabled\b/);
    assert.match(sezione, /<button id="annulla-richiesta"[^>]*type="submit"[^>]*\bdisabled\b/);
});

async function setupAnnullamento(t, { motivoObbligatorio = 'true', fetchHandler }) {
    const nodi = new Map([
        ['contenuto', new FakeNode('main', 'contenuto')],
        ['form-annullamento', new FakeNode('form', 'form-annullamento')],
        ['campi-annullamento', new FakeNode('fieldset', 'campi-annullamento')],
        ['annullamento-motivo', new FakeNode('textarea', 'annullamento-motivo')],
        ['errore-annullamento-motivo', new FakeNode('p', 'errore-annullamento-motivo')],
        ['annulla-richiesta', new FakeNode('button', 'annulla-richiesta')],
        ['annullamento-rileggi', new FakeNode('a', 'annullamento-rileggi')],
        ['esito-annullamento', new FakeNode('p', 'esito-annullamento')]
    ]);
    const contenuto = nodi.get('contenuto');
    contenuto.dataset = { richiestaId: 'req-123', misura: 'M01 · Accoglienza', opt: 'OPT Alfa' };
    const form = nodi.get('form-annullamento');
    form.dataset = { motivoObbligatorio };
    const campi = nodi.get('campi-annullamento');
    const textarea = nodi.get('annullamento-motivo');
    const submitBtn = nodi.get('annulla-richiesta');
    const rileggi = nodi.get('annullamento-rileggi');
    campi.disabled = true;
    textarea.disabled = true;
    submitBtn.disabled = true;
    rileggi.hidden = true;

    const body = new FakeNode('body');
    let reloaded = false;
    const doc = {
        body,
        activeElement: null,
        addEventListener() {},
        querySelectorAll: () => [],
        getElementById: id => (id === 'contenuto' ? null : (nodi.get(id) ?? null)),
        createElement: tag => new FakeNode(tag),
        createTextNode: text => {
            const n = new FakeNode('#text');
            n.textContent = String(text);
            return n;
        }
    };
    const prevDoc = Object.getOwnPropertyDescriptor(globalThis, 'document');
    const prevLoc = Object.getOwnPropertyDescriptor(globalThis, 'location');
    Object.defineProperty(globalThis, 'document', { value: doc, configurable: true });
    Object.defineProperty(globalThis, 'location', {
        value: { reload() { reloaded = true; } },
        configurable: true
    });
    t.after(() => {
        if (prevDoc) Object.defineProperty(globalThis, 'document', prevDoc);
        else delete globalThis.document;
        if (prevLoc) Object.defineProperty(globalThis, 'location', prevLoc);
        else delete globalThis.location;
    });
    t.mock.method(globalThis, 'fetch', fetchHandler);

    const { avviaAnnullamentoRichiesta } = await import(
        '../../main/resources/static/js/dettaglio-richiesta.js?test=' + Math.random()
    );
    doc.getElementById = id => nodi.get(id) ?? null;
    assert.equal(campi.disabled, true);
    avviaAnnullamentoRichiesta(contenuto);
    assert.equal(campi.disabled, false);
    assert.equal(textarea.disabled, false);
    assert.equal(submitBtn.disabled, false);

    return {
        form,
        campi,
        textarea,
        submitBtn,
        rileggi,
        esito: nodi.get('esito-annullamento'),
        erroreMotivo: nodi.get('errore-annullamento-motivo'),
        wasReloaded: () => reloaded,
        chiudiConferma: async returnValue => {
            const dlg = doc._dialog;
            assert.ok(dlg, 'dialog di conferma aperto');
            dlg.returnValue = returnValue;
            await dlg.dispatch('close');
        }
    };
}

test('chiudere la conferma non invia richieste, conserva il motivo e la conferma invia POST solo nel corpo JSON', async t => {
    const chiamate = [];
    const ui = await setupAnnullamento(t, {
        fetchHandler: async (url, options = {}) => {
            chiamate.push({ url, options });
            if (url === '/api/auth/csrf') {
                return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf-annulla' });
            }
            return Response.json({ id: 'req-123', stato: 'ANNULLATA' });
        }
    });

    ui.textarea.value = MOTIVO_SEGRETO;
    let prevented = false;
    const primoSubmit = ui.form.dispatch('submit', { preventDefault() { prevented = true; } });
    assert.equal(prevented, true);
    assert.equal(ui.campi.disabled, true);
    await ui.chiudiConferma('annulla');
    await primoSubmit;
    assert.equal(chiamate.length, 0);
    assert.equal(ui.textarea.value, MOTIVO_SEGRETO);
    assert.equal(ui.campi.disabled, false);

    const secondoSubmit = ui.form.dispatch('submit', { preventDefault() {} });
    await ui.chiudiConferma('conferma');
    await secondoSubmit;

    assert.equal(ui.wasReloaded(), true);
    assert.equal(chiamate.length, 2);
    assert.equal(chiamate[1].url, '/api/richieste/req-123/annullamento');
    assert.equal(chiamate[1].url.includes('motivo'), false);
    assert.equal(chiamate[1].options.method, 'POST');
    assert.deepEqual(JSON.parse(chiamate[1].options.body), { motivo: MOTIVO_SEGRETO });
});

test('un errore del server mantiene il motivo nel form e rende visibile il collegamento per rileggere lo stato', async t => {
    const ui = await setupAnnullamento(t, {
        fetchHandler: async url => {
            if (url === '/api/auth/csrf') {
                return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf-annulla' });
            }
            return Response.json({ codice: 'CONFLICT', messaggio: 'Stato aggiornato nel frattempo' }, { status: 409 });
        }
    });

    ui.textarea.value = MOTIVO_SEGRETO;
    const invio = ui.form.dispatch('submit', { preventDefault() {} });
    await ui.chiudiConferma('conferma');
    await invio;

    assert.equal(ui.wasReloaded(), false);
    assert.equal(ui.textarea.value, MOTIVO_SEGRETO);
    assert.equal(ui.rileggi.hidden, false);
    assert.equal(ui.campi.disabled, false);
    assert.equal(ui.textarea.disabled, false);
    assert.equal(ui.submitBtn.disabled, false);
});
