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
        contains: c => this.classList.set.has(c)
    };
    constructor(tag = 'div', className = '') {
        this.tagName = tag.toUpperCase();
        if (className) className.split(/\s+/).forEach(c => this.classList.add(c));
    }
    set className(v) { this.classList.set = new Set(String(v || '').split(/\s+/).filter(Boolean)); }
    get className() { return [...this.classList.set].join(' '); }
    set textContent(v) { this._text = String(v ?? ''); this.children = []; }
    get textContent() { return this.children.length ? this.children.map(c => c.textContent).join('') : (this._text ?? ''); }
    append(...nodes) {
        for (const n of nodes) {
            const node = typeof n === 'string' ? Object.assign(new FakeNode('#text'), { _text: n }) : n;
            node.parentElement = this;
            this.children.push(node);
        }
    }
    add(opt) { this.append(opt); }
    replaceChildren(...nodes) { this.children = []; this._text = ''; this.append(...nodes); }
    remove() {
        if (this.parentElement) {
            this.parentElement.children = this.parentElement.children.filter(c => c !== this);
        }
    }
    setAttribute(k, v) { this.attributes.set(k, String(v)); }
    hasAttribute(k) { return this.attributes.has(k); }
    removeAttribute(k) { this.attributes.delete(k); }
    focus() {}
    contains(n) { return this === n || this.children.some(c => c.contains?.(n)); }
    matchesSingle(sel) {
        if (sel === 'button[data-reinvia-invito]') return this.tagName === 'BUTTON' && this.attributes.has('data-reinvia-invito');
        if (sel === 'button[data-revoca-opt]') return this.tagName === 'BUTTON' && this.attributes.has('data-revoca-opt');
        if (sel === 'button[data-revoca-operatore]') return this.tagName === 'BUTTON' && this.attributes.has('data-revoca-operatore');
        if (sel === '.status-label') return this.classList.contains('status-label');
        if (sel === '.account-row') return this.classList.contains('account-row');
        return this.tagName.toLowerCase() === sel.toLowerCase();
    }
    matchesSelector(sel) {
        return sel.split(',').some(p => this.matchesSingle(p.trim()));
    }
    querySelectorAll(sel) {
        return this.children.flatMap(c => [
            ...(c.matchesSelector?.(sel) ? [c] : []),
            ...(c.querySelectorAll ? c.querySelectorAll(sel) : [])
        ]);
    }
    querySelector(sel) { return this.querySelectorAll(sel)[0] ?? null; }
    closest(sel) {
        for (let cur = this; cur; cur = cur.parentElement) {
            if (cur.matchesSelector?.(sel)) return cur;
        }
        return null;
    }
    addEventListener(type, fn) { (this.eventi[type] ??= []).push(fn); }
    async dispatch(type, ev) {
        for (const fn of this.eventi[type] || []) await fn(ev);
    }
}

function creaRigaAccount({ id = 'opt-1', nome = 'OPT Alfa', stato = 'IN_ATTESA_DI_ATTIVAZIONE', conInvito = true } = {}) {
    const riga = new FakeNode('article', 'account-row');
    riga.dataset = { id, nome, riferimento: 'OPT-01', ruolo: 'OPT', stato, email: 'opt@picus.test' };
    const statoEl = new FakeNode('span', 'status-label');
    statoEl.textContent = stato === 'REVOCATO' ? 'Revocato' : (stato === 'ATTIVO' ? 'Attivo' : 'In attesa di attivazione');
    const btnRevoca = new FakeNode('button');
    btnRevoca.setAttribute('data-revoca-opt', '');
    btnRevoca.textContent = stato === 'REVOCATO' ? 'Revocato' : 'Revoca';
    btnRevoca.disabled = stato === 'REVOCATO';
    riga.append(statoEl);
    if (conInvito && stato === 'IN_ATTESA_DI_ATTIVAZIONE') {
        const btnInvito = new FakeNode('button');
        btnInvito.setAttribute('data-reinvia-invito', '');
        btnInvito.textContent = 'Reinvia invito';
        riga.append(btnInvito);
    }
    riga.append(btnRevoca);
    return riga;
}

function setupBancoAccount(t, fetchHandler) {
    const accountBox = new FakeNode('div');
    accountBox.id = 'account';
    const esitoEl = new FakeNode('p');
    accountBox.append(creaRigaAccount());

    const snapshots = new Map();
    const doc = {
        activeElement: null,
        addEventListener() {},
        getElementById: id => (id === 'account' ? accountBox : null),
        createElement: tag => new FakeNode(tag),
        importNode: n => n
    };
    class FakeDOMParser {
        parseFromString(html) {
            const box = new FakeNode('div');
            box.id = 'account';
            for (const cfg of snapshots.get(html) || []) box.append(creaRigaAccount(cfg));
            return { getElementById: id => (id === 'account' ? box : null) };
        }
    }

    const prevDoc = Object.getOwnPropertyDescriptor(globalThis, 'document');
    const prevOpt = Object.getOwnPropertyDescriptor(globalThis, 'Option');
    const prevDomParser = Object.getOwnPropertyDescriptor(globalThis, 'DOMParser');
    Object.defineProperty(globalThis, 'document', { value: doc, configurable: true });
    Object.defineProperty(globalThis, 'Option', {
        value: class extends FakeNode { constructor(t, v) { super('option'); this._text = t; this.value = v; } },
        configurable: true
    });
    Object.defineProperty(globalThis, 'DOMParser', { value: FakeDOMParser, configurable: true });
    t.after(() => {
        if (prevDoc) Object.defineProperty(globalThis, 'document', prevDoc); else delete globalThis.document;
        if (prevOpt) Object.defineProperty(globalThis, 'Option', prevOpt); else delete globalThis.Option;
        if (prevDomParser) Object.defineProperty(globalThis, 'DOMParser', prevDomParser); else delete globalThis.DOMParser;
    });

    t.mock.method(globalThis, 'fetch', fetchHandler);

    const registraSnapshot = (chiave, righe) => {
        snapshots.set(chiave, righe);
        return chiave;
    };

    const rigaCorrente = (id = 'opt-1') => accountBox.querySelectorAll('.account-row').find(r => r.dataset.id === id);

    return import('../../main/resources/static/js/area.js?test=' + Math.random()).then(({ collegaElencoAccountBanco }) => {
        const aggiorna = collegaElencoAccountBanco(accountBox, {
            esitoEl,
            conferma: () => true,
            aggiornaOfferte: async () => {}
        });
        return { accountBox, esitoEl, aggiorna, registraSnapshot, rigaCorrente };
    });
}

test('refresh sostituisce la riga prima della conferma della revoca: pulsante resta bloccato in volo e la nuova riga diventa Revocato', async t => {
    const revocaRes = Promise.withResolvers();
    const ui = await setupBancoAccount(t, async url => {
        if (url === '/api/auth/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        if (url === '/api/opt/opt-1/revoca') return revocaRes.promise;
        if (url === '/banco/dashboard') {
            return new Response(ui.registraSnapshot('snap-attiva', [{ id: 'opt-1', stato: 'IN_ATTESA_DI_ATTIVAZIONE' }]));
        }
        throw new Error('URL imprevisto: ' + url);
    });

    const btnIniziale = ui.rigaCorrente().querySelector('button[data-revoca-opt]');
    const revocaPromise = ui.accountBox.dispatch('click', { target: btnIniziale });
    await Promise.resolve();

    await ui.aggiorna();
    const rigaDopoRefresh = ui.rigaCorrente();
    const btnDopoRefresh = rigaDopoRefresh.querySelector('button[data-revoca-opt]');
    assert.notEqual(btnDopoRefresh, btnIniziale, 'il refresh ha sostituito il nodo della riga');
    assert.equal(btnDopoRefresh.disabled, true, 'il pulsante sulla nuova riga resta disabilitato durante la revoca in volo');

    revocaRes.resolve(Response.json({ messaggio: 'Accesso revocato' }));
    await revocaPromise;

    const rigaFinale = ui.rigaCorrente();
    assert.equal(rigaFinale.dataset.stato, 'REVOCATO');
    assert.equal(rigaFinale.querySelector('.status-label').textContent, 'Revocato');
    assert.equal(rigaFinale.querySelector('button[data-reinvia-invito]'), null);
    assert.equal(rigaFinale.querySelector('button[data-revoca-opt]').textContent, 'Revocato');
    assert.equal(rigaFinale.querySelector('button[data-revoca-opt]').disabled, true);
});

test('refresh tardivo con snapshot precedente arriva dopo il successo della revoca: account resta Revocato e senza Reinvia invito', async t => {
    const refreshDeferred = Promise.withResolvers();
    const ui = await setupBancoAccount(t, async url => {
        if (url === '/api/auth/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        if (url === '/api/opt/opt-1/revoca') return Response.json({ messaggio: 'Accesso revocato' });
        if (url === '/banco/dashboard') return refreshDeferred.promise;
        throw new Error('URL imprevisto: ' + url);
    });

    const refreshInVolo = ui.aggiorna();
    const btn = ui.rigaCorrente().querySelector('button[data-revoca-opt]');
    await ui.accountBox.dispatch('click', { target: btn });
    assert.equal(ui.rigaCorrente().dataset.stato, 'REVOCATO');

    refreshDeferred.resolve(new Response(ui.registraSnapshot('snap-vecchio', [{ id: 'opt-1', stato: 'IN_ATTESA_DI_ATTIVAZIONE' }])));
    await refreshInVolo;

    const rigaFinale = ui.rigaCorrente();
    assert.equal(rigaFinale.dataset.stato, 'REVOCATO');
    assert.equal(rigaFinale.querySelector('.status-label').textContent, 'Revocato');
    assert.equal(rigaFinale.querySelector('button[data-reinvia-invito]'), null);
    assert.equal(rigaFinale.querySelector('button[data-revoca-opt]').textContent, 'Revocato');
    assert.equal(rigaFinale.querySelector('button[data-revoca-opt]').disabled, true);
});

test('revoca fallita durante un refresh riabilita il pulsante sulla riga corrente per consentire un nuovo tentativo', async t => {
    const revocaRes = Promise.withResolvers();
    const ui = await setupBancoAccount(t, async url => {
        if (url === '/api/auth/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        if (url === '/api/opt/opt-1/revoca') return revocaRes.promise;
        if (url === '/banco/dashboard') {
            return new Response(ui.registraSnapshot('snap-attiva', [{ id: 'opt-1', stato: 'ATTIVO' }]));
        }
        throw new Error('URL imprevisto: ' + url);
    });

    const btnIniziale = ui.rigaCorrente().querySelector('button[data-revoca-opt]');
    const revocaPromise = ui.accountBox.dispatch('click', { target: btnIniziale });
    await Promise.resolve();

    await ui.aggiorna();
    const btnSostituito = ui.rigaCorrente().querySelector('button[data-revoca-opt]');
    assert.equal(btnSostituito.disabled, true);

    revocaRes.resolve(Response.json({ messaggio: 'Errore temporaneo' }, { status: 503 }));
    await revocaPromise;

    const rigaCorrente = ui.rigaCorrente();
    const btnCorrente = rigaCorrente.querySelector('button[data-revoca-opt]');
    assert.equal(rigaCorrente.dataset.stato, 'ATTIVO');
    assert.equal(btnCorrente.disabled, false);
    assert.equal(btnCorrente.textContent, 'Revoca');
});
