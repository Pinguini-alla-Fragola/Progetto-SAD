import test from 'node:test';
import assert from 'node:assert/strict';
import { collegaRicercaComune, normalizzaTestoComune } from '../../main/resources/static/js/nuova-richiesta.js';

function creaElemento(tag) {
    const el = {
        tagName: tag.toUpperCase(),
        children: [],
        dataset: {},
        attributes: {},
        eventi: {},
        value: '',
        textContent: '',
        hidden: false,
        classList: {
            _set: new Set(),
            toggle(nome, stato) {
                if (stato) this._set.add(nome);
                else this._set.delete(nome);
            },
            contains(nome) {
                return this._set.has(nome);
            }
        },
        setAttribute(k, v) { this.attributes[k] = String(v); },
        getAttribute(k) { return this.attributes[k] ?? null; },
        addEventListener(nome, fn) {
            this.eventi[nome] ??= [];
            this.eventi[nome].push(fn);
        },
        dispatchEvent(evt) {
            for (const fn of this.eventi[evt.type] || []) fn(evt);
        },
        append(...nodi) {
            for (const n of nodi) {
                if (typeof n === 'object' && n !== null) this.children.push(n);
            }
        },
        replaceChildren(...nodi) {
            this.children = [];
            this.append(...nodi);
        },
        querySelectorAll(sel) {
            if (sel === '[role="option"]') {
                return this.children.filter(c => c.getAttribute('role') === 'option');
            }
            return [];
        }
    };
    Object.defineProperty(el, 'options', {
        get() { return el.children; }
    });
    return el;
}

function setupRicerca(comuni) {
    const originali = Object.getOwnPropertyDescriptor(globalThis, 'document');
    Object.defineProperty(globalThis, 'document', {
        value: { createElement: tag => creaElemento(tag) },
        configurable: true,
        writable: true
    });

    const select = creaElemento('select');
    const placeholder = creaElemento('option');
    placeholder.value = '';
    placeholder.textContent = 'Seleziona un comune';
    select.append(placeholder);
    for (const c of comuni) {
        const op = creaElemento('option');
        op.value = c.codice;
        op.dataset = { nome: c.nome, territorio: c.territorio };
        op.textContent = `${c.nome} — ${c.territorio}`;
        select.append(op);
    }

    const input = creaElemento('input');
    const suggerimenti = creaElemento('div');
    const stato = creaElemento('p');
    const form = creaElemento('form');
    const apiRicerca = collegaRicercaComune({ input, select, suggerimenti, stato, form });
    return {
        input,
        select,
        suggerimenti,
        stato,
        form,
        apiRicerca,
        ripristina: () => {
            if (originali) Object.defineProperty(globalThis, 'document', originali);
            else delete globalThis.document;
        }
    };
}

const CAMPIONE = [
    { codice: 'napoli', nome: 'Napoli', territorio: 'Città metropolitana di Napoli (NA)' },
    { codice: '061005', nome: 'Aversa', territorio: 'Provincia di Caserta (CE)' },
    { codice: '064085', nome: 'San Nicola Baronia', territorio: 'Provincia di Avellino (AV)' },
    { codice: '061078', nome: 'San Nicola la Strada', territorio: 'Provincia di Caserta (CE)' },
    { codice: '062067', nome: 'San Nicola Manfredi', territorio: 'Provincia di Benevento (BN)' },
    { codice: '063074', nome: "Sant'Antonio Abate", territorio: 'Città metropolitana di Napoli (NA)' }
];

test('normalizzaTestoComune rimuove accenti e normalizza spazi', () => {
    assert.equal(normalizzaTestoComune('  Forìo  d’Ischia '), 'forio d’ischia');
});

test('filtra i comuni per nome distinguendo provincia e città metropolitana e seleziona da tastiera', t => {
    const ctx = setupRicerca(CAMPIONE);
    t.after(ctx.ripristina);

    ctx.input.value = 'San Nicola';
    ctx.input.dispatchEvent({ type: 'input' });

    const opzioniVisibili = ctx.suggerimenti.querySelectorAll('[role="option"]');
    assert.equal(ctx.suggerimenti.hidden, false);
    assert.equal(opzioniVisibili.length, 3);
    assert.match(opzioniVisibili[0].children[1].textContent, /Provincia di Avellino \(AV\)/);
    assert.match(opzioniVisibili[1].children[1].textContent, /Provincia di Caserta \(CE\)/);
    assert.match(opzioniVisibili[2].children[1].textContent, /Provincia di Benevento \(BN\)/);
    assert.equal(ctx.select.options.length, 4);

    let prevenuto = false;
    ctx.input.dispatchEvent({ type: 'keydown', key: 'ArrowDown', preventDefault() { prevenuto = true; } });
    assert.equal(prevenuto, true);
    assert.equal(opzioniVisibili[1].getAttribute('aria-selected'), 'true');

    ctx.input.dispatchEvent({ type: 'keydown', key: 'Enter', preventDefault() {} });
    assert.equal(ctx.select.value, '061078');
    assert.equal(ctx.input.value, 'San Nicola la Strada');
    assert.equal(ctx.suggerimenti.hidden, true);
    assert.match(ctx.stato.textContent, /Provincia di Caserta \(CE\)/);

    ctx.form.dispatchEvent({ type: 'reset' });
    assert.equal(ctx.input.value, '');
    assert.equal(ctx.select.value, '');
    assert.equal(ctx.select.options.length, CAMPIONE.length + 1);
});

test('una ricerca senza riscontri svuota i suggerimenti e segnala l’assenza in stato', t => {
    const ctx = setupRicerca(CAMPIONE);
    t.after(ctx.ripristina);

    ctx.input.value = 'ComuneInesistente';
    ctx.input.dispatchEvent({ type: 'input' });

    assert.equal(ctx.suggerimenti.hidden, true);
    assert.equal(ctx.select.options.length, 1);
    assert.match(ctx.stato.textContent, /Nessun comune campano trovato/);
});
