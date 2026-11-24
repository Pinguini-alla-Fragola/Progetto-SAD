/*
 * #55 CA-06 · Avviso di scadenza della scheda trimestrale dal modulo del Banco.
 *
 * I casi Java CA-06 (InvioComunicazioniTest) verificano via HTTP che l'avviso arrivi agli operatori. Questi casi
 * verificano il collegamento lato pagina: il pulsante [data-modello-scadenza] di comunicazioni.js compila il modulo
 * con MODELLO_SCADENZA e l'invio passa dal normale POST /api/comunicazioni. Il controllo positivo (compilazione
 * manuale senza modello) distingue il modello dal semplice invio.
 *
 * comunicazioni.js non esporta nulla e si avvia all'import: per ogni caso si prepara in globalThis un DOM finto
 * con form#comunicazione costruito leggendo il template reale templates/banco/dashboard.html, si simula fetch e si
 * importa il modulo con un parametro di query diverso, così ogni caso ha un'istanza nuova. Pulsante del modello,
 * campi, opzioni e gruppo sono quindi quelli della dashboard: nessun controllo del form è scritto nel test. Solo
 * #messaggi e body[data-area] sono ricreati. Il lettore del template copre il solo HTML del form (tag bilanciati,
 * attributi tra virgolette doppie o senza valore) e fallisce se il markup non è di questa forma.
 *
 * Il DOM finto copre solo ciò che usano comunicazioni.js, elenco-comunicazioni.js e communication-view.js
 * all'avvio e nell'invio: createElement, Option, append, replaceChildren, remove, textContent, classList,
 * dataset, attributi, eventi, querySelector/querySelectorAll/matches/closest su selettori semplici (tag, #id,
 * .classe, [attributo], [attributo="valore"], :checked, discendenti), form.elements con namedItem, reset e focus.
 * Solo dati sintetici.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

/** Stessi titolo e testo delle costanti TITOLO_SCADENZA e CONTENUTO_SCADENZA di InvioComunicazioniTest. */
const TITOLO_SCADENZA = 'Scadenza della scheda trimestrale';
const CONTENUTO_SCADENZA = 'Vi ricordiamo di compilare e inviare la scheda trimestrale entro la scadenza prevista.\n'
    + 'Per qualsiasi dubbio potete rispondere a questa comunicazione contattando il Banco.';

const CSRF = { headerName: 'X-CSRF-TOKEN', token: 'token-sintetico-55' };
const ACCOUNT = [
    { id: '00000000-0000-4000-8000-000000000551', nome: 'OPT sintetica A' },
    { id: '00000000-0000-4000-8000-000000000552', nome: 'OPT sintetica B' }
];
const PAGINA_VUOTA = { elementi: [], pagina: 0, dimensione: 20, totale: 0 };
const MODULO = new URL('../../main/resources/static/js/comunicazioni.js', import.meta.url);
const DASHBOARD = new URL('../../main/resources/templates/banco/dashboard.html', import.meta.url);

/* ---------------------------------------------------------------- DOM finto minimo */

const PROPRIETA_ATTRIBUTO = ['id', 'name', 'type', 'href'];

function inKebab(nome) {
    return nome.replace(/[A-Z]/g, lettera => '-' + lettera.toLowerCase());
}

/** Selettore composto senza combinatori: tag, #id, .classe, [attr], [attr="valore"], :checked. */
function composto(testo) {
    const parti = { tag: null, id: null, classi: [], attributi: [], checked: false };
    const token = /^([a-z]+)|#([\w-]+)|\.([\w-]+)|\[([\w-]+)(?:="([^"]*)")?\]|(:checked)/g;
    let resto = testo;
    while (resto) {
        token.lastIndex = 0;
        const trovato = token.exec(resto);
        assert.ok(trovato && trovato.index === 0, 'selettore non supportato dal DOM finto: ' + testo);
        const [intero, tag, id, classe, attributo, valore, checked] = trovato;
        if (tag) parti.tag = tag;
        if (id) parti.id = id;
        if (classe) parti.classi.push(classe);
        if (attributo) parti.attributi.push([attributo, valore]);
        if (checked) parti.checked = true;
        resto = resto.slice(intero.length);
    }
    return parti;
}

function corrisponde(nodo, parti) {
    return (!parti.tag || nodo.tagName === parti.tag)
        && (!parti.id || nodo.getAttribute('id') === parti.id)
        && parti.classi.every(classe => nodo.classList.contains(classe))
        && parti.attributi.every(([nome, valore]) => valore === undefined
            ? nodo.hasAttribute(nome) : nodo.getAttribute(nome) === valore)
        && (!parti.checked || nodo.checked === true);
}

/** Selettori separati da virgole; dentro ciascuno, spazi come combinatore discendente. */
function soddisfa(nodo, selettore) {
    return selettore.split(',').some(alternativa => {
        const passi = alternativa.trim().split(/\s+/).map(composto);
        if (!corrisponde(nodo, passi.at(-1))) return false;
        let antenato = nodo.parentNode;
        for (let i = passi.length - 2; i >= 0; i--) {
            while (antenato && !corrisponde(antenato, passi[i])) antenato = antenato.parentNode;
            if (!antenato) return false;
            antenato = antenato.parentNode;
        }
        return true;
    });
}

class Testo {
    constructor(testo) {
        this.textContent = String(testo);
        this.parentNode = null;
    }
}

class Elemento {
    constructor(tag) {
        this.tagName = tag;
        this.childNodes = [];
        this.parentNode = null;
        this.attributi = new Map();
        this.ascoltatori = new Map();
        this.hidden = false;
        this.disabled = false;
        if (tag === 'input' || tag === 'textarea') this.value = '';
        const elemento = this;
        this.dataset = new Proxy({}, {
            get: (_, nome) => elemento.getAttribute('data-' + inKebab(String(nome))) ?? undefined,
            set: (_, nome, valore) => {
                elemento.setAttribute('data-' + inKebab(String(nome)), valore);
                return true;
            }
        });
        this.classList = {
            contains: classe => elemento.className.split(/\s+/).includes(classe),
            add: (...classi) => {
                const attuali = new Set(elemento.className.split(/\s+/).filter(Boolean));
                classi.forEach(classe => attuali.add(classe));
                elemento.className = [...attuali].join(' ');
            }
        };
    }

    get className() {
        return this.attributi.get('class') ?? '';
    }

    set className(valore) {
        this.attributi.set('class', String(valore));
    }

    /** Il valore di una select è quello scelto, altrimenti quello della prima opzione. */
    get value() {
        if (this.tagName !== 'select') return this._valore;
        return this._valore ?? this.figli().find(figlio => figlio.tagName === 'option')?.value ?? '';
    }

    set value(valore) {
        this._valore = valore;
    }

    get textContent() {
        return this.childNodes.map(figlio => figlio.textContent).join('');
    }

    set textContent(valore) {
        this.replaceChildren(String(valore ?? ''));
    }

    figli() {
        return this.childNodes.filter(figlio => figlio instanceof Elemento);
    }

    getAttribute(nome) {
        if (this.attributi.has(nome)) return this.attributi.get(nome);
        return PROPRIETA_ATTRIBUTO.includes(nome) && this[nome] != null ? String(this[nome]) : null;
    }

    hasAttribute(nome) {
        return this.getAttribute(nome) !== null;
    }

    setAttribute(nome, valore) {
        this.attributi.set(nome, String(valore));
    }

    removeAttribute(nome) {
        this.attributi.delete(nome);
    }

    append(...nodi) {
        for (const nodo of nodi) {
            const figlio = nodo instanceof Elemento || nodo instanceof Testo ? nodo : new Testo(nodo);
            figlio.parentNode?.childNodes.splice(figlio.parentNode.childNodes.indexOf(figlio), 1);
            figlio.parentNode = this;
            this.childNodes.push(figlio);
        }
    }

    add(opzione) {
        this.append(opzione);
    }

    replaceChildren(...nodi) {
        this.childNodes.forEach(figlio => { figlio.parentNode = null; });
        this.childNodes = [];
        this.append(...nodi);
    }

    remove() {
        this.parentNode?.childNodes.splice(this.parentNode.childNodes.indexOf(this), 1);
        this.parentNode = null;
    }

    discendenti() {
        return this.figli().flatMap(figlio => [figlio, ...figlio.discendenti()]);
    }

    querySelectorAll(selettore) {
        return this.discendenti().filter(nodo => soddisfa(nodo, selettore));
    }

    querySelector(selettore) {
        return this.querySelectorAll(selettore)[0] ?? null;
    }

    matches(selettore) {
        return soddisfa(this, selettore);
    }

    closest(selettore) {
        for (let nodo = this; nodo; nodo = nodo.parentNode) {
            if (nodo.matches(selettore)) return nodo;
        }
        return null;
    }

    addEventListener(tipo, ascoltatore) {
        if (!this.ascoltatori.has(tipo)) this.ascoltatori.set(tipo, []);
        this.ascoltatori.get(tipo).push(ascoltatore);
    }

    dispatchEvent(evento) {
        for (const ascoltatore of this.ascoltatori.get(evento.type) ?? []) ascoltatore(evento);
    }

    click() {
        this.dispatchEvent({ type: 'click', preventDefault() {} });
    }

    focus() {}

    /** Solo per form: i controlli con un nome, come form.elements del browser. */
    get elements() {
        const form = this;
        const namedItem = nome => form.discendenti()
            .find(nodo => ['input', 'select', 'textarea', 'button', 'fieldset'].includes(nodo.tagName)
                && nodo.getAttribute('name') === nome) ?? null;
        return new Proxy({}, { get: (_, nome) => nome === 'namedItem' ? namedItem : namedItem(String(nome)) ?? undefined });
    }

    /** Solo per form: riporta i campi al valore iniziale. */
    reset() {
        for (const nodo of this.discendenti()) {
            if (nodo.tagName === 'select') nodo._valore = undefined;
            else if (nodo.type === 'checkbox') nodo.checked = false;
            else if ((nodo.tagName === 'input' && nodo.type !== 'file') || nodo.tagName === 'textarea') nodo.value = '';
        }
    }
}

function crea(tag, { attributi = {}, ...proprieta } = {}, ...figli) {
    const elemento = new Elemento(tag);
    Object.entries(attributi).forEach(([nome, valore]) => elemento.setAttribute(nome, valore));
    Object.assign(elemento, proprieta);
    elemento.append(...figli);
    return elemento;
}

/* ---------------------------------------------------------------- form della dashboard reale */

const SENZA_CHIUSURA = new Set(['input', 'br', 'hr', 'img']);
const PROPRIETA_BOOLEANE = ['hidden', 'disabled', 'checked'];

/** Markup del solo form id="comunicazione" della dashboard del Banco, esclusi i commenti HTML. */
function markupForm() {
    const pagina = readFileSync(DASHBOARD, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    const form = pagina.match(/<form\s(?:[^>]*\s)?id="comunicazione"[^>]*>[\s\S]*?<\/form>/g) ?? [];
    assert.equal(form.length, 1, 'la dashboard deve avere un solo form id="comunicazione"');
    return form[0];
}

/** Elemento finto con gli attributi del tag; quelli letti come proprietà dagli script diventano proprietà. */
function creaDalTag(tag, testoAttributi) {
    const attributi = {};
    const proprieta = tag === 'input' ? { files: [] } : {};
    for (const [, nome, valore = ''] of testoAttributi.matchAll(/([\w:-]+)(?:="([^"]*)")?/g)) {
        if (PROPRIETA_BOOLEANE.includes(nome)) proprieta[nome] = true;
        else if (nome === 'value' || PROPRIETA_ATTRIBUTO.includes(nome)) proprieta[nome] = valore;
        else attributi[nome] = valore;
    }
    return crea(tag, { attributi, ...proprieta });
}

/** Albero del DOM finto corrispondente al form del template. */
function formDellaDashboard() {
    const radice = new Elemento('template');
    let corrente = radice;
    for (const [intero, chiuso, aperto, attributi] of
        markupForm().matchAll(/<\/([a-z]\w*)\s*>|<([a-z]\w*)((?:\s+[\w:-]+(?:="[^"]*")?)*)\s*\/?>|[^<]+|</g)) {
        assert.notEqual(intero, '<', 'markup del form non supportato dal lettore del test');
        if (chiuso) {
            assert.equal(chiuso, corrente.tagName, 'tag non bilanciati nel form della dashboard');
            corrente = corrente.parentNode;
        } else if (aperto) {
            const elemento = creaDalTag(aperto, attributi);
            corrente.append(elemento);
            if (!SENZA_CHIUSURA.has(aperto)) corrente = elemento;
        } else {
            corrente.append(intero);
        }
    }
    assert.equal(corrente, radice, 'tag non bilanciati nel form della dashboard');
    const [form] = radice.figli();
    form.remove();
    return form;
}

/** Pagina del Banco con il form reale della dashboard. */
function preparaPagina() {
    const form = formDellaDashboard();
    const messaggi = crea('div', { id: 'messaggi', attributi: { class: 'picus-list', 'aria-live': 'polite' } });
    const body = crea('body', { attributi: { 'data-area': 'banco' } }, messaggi, form);
    globalThis.document = {
        body,
        createElement: tag => new Elemento(tag),
        getElementById: id => body.querySelector('#' + id),
        querySelector: selettore => body.querySelector(selettore)
    };
    globalThis.Option = function Option(testo, valore) {
        return crea('option', { value: valore }, testo);
    };
    return form;
}

/* ---------------------------------------------------------------- server simulato */

/** Simula le rotte usate dalla pagina e registra tutte le chiamate. */
function simulaServer(t) {
    const chiamate = [];
    t.mock.method(globalThis, 'fetch', async (url, opzioni = {}) => {
        const metodo = opzioni.method ?? 'GET';
        chiamate.push({ url, metodo, headers: opzioni.headers ?? {}, body: opzioni.body });
        if (metodo === 'GET' && url === '/api/auth/csrf') return Response.json(CSRF);
        if (metodo === 'GET' && url.startsWith('/api/comunicazioni/destinatari?tipo=')) return Response.json(ACCOUNT);
        if (metodo === 'GET' && /^\/api\/comunicazioni\/(inviate|ricevute)\?/.test(url)) return Response.json(PAGINA_VUOTA);
        if (metodo === 'POST' && url === '/api/comunicazioni') {
            const dati = JSON.parse(await opzioni.body.get('dati').text());
            return Response.json({ id: '00000000-0000-4000-8000-000000005501', titolo: dati.titolo,
                inviataIl: '2026-11-23T10:00:00Z', destinatariTipo: dati.destinatariTipo, destinatari: 2 },
            { status: 201 });
        }
        throw new Error('richiesta non prevista dal test: ' + metodo + ' ' + url);
    });
    t.after(() => {
        delete globalThis.document;
        delete globalThis.Option;
    });
    return chiamate;
}

let istanza = 0;

/** Importa un'istanza nuova del modulo, che si avvia sul DOM finto appena preparato. */
async function avvia() {
    await import(MODULO.href + '?caso=' + (++istanza));
}

/** Attende i passi asincroni del modulo (fetch simulate, aggiornamento del gruppo, invio). */
async function finche(condizione, descrizione) {
    for (let giro = 0; giro < 200; giro++) {
        if (condizione()) return;
        await new Promise(risolvi => setTimeout(risolvi, 0));
    }
    assert.fail('condizione non raggiunta: ' + descrizione);
}

async function invia(form, chiamate) {
    const esito = form.querySelector('.picus-esito');
    form.dispatchEvent({ type: 'submit', preventDefault() {} });
    await finche(() => esito.textContent.startsWith('Comunicazione inviata'), 'invio completato');
    const post = chiamate.filter(chiamata => chiamata.metodo === 'POST');
    assert.equal(post.length, 1, 'esattamente un invio');
    return post[0];
}

/** L'invio è la POST normale delle comunicazioni, con il CSRF letto dal server, i soli dati e nessun file. */
async function verificaInvio(chiamate, post, attesi) {
    assert.equal(post.url, '/api/comunicazioni', 'stesso endpoint delle altre comunicazioni');
    assert.equal(post.headers[CSRF.headerName], CSRF.token, 'header CSRF letto da /api/auth/csrf');
    assert.ok(chiamate.findIndex(chiamata => chiamata.url === '/api/auth/csrf') < chiamate.indexOf(post));
    assert.ok(post.body instanceof FormData, 'corpo multipart del modulo');
    assert.deepEqual([...new Set([...post.body.keys()])], ['dati'], 'solo la parte dati, nessuna parte files');
    assert.deepEqual(JSON.parse(await post.body.get('dati').text()), attesi);
}

async function moduloPronto(t) {
    const form = preparaPagina();
    const chiamate = simulaServer(t);
    await avvia();
    await finche(() => form.querySelector('fieldset.campi-form').disabled === false, 'modulo attivato');
    return { form, chiamate };
}

async function scegliGruppoOpt(form) {
    const tipo = form.elements.destinatariTipo;
    tipo.value = 'GRUPPO_OPT';
    tipo.dispatchEvent({ type: 'change' });
    await finche(() => form.querySelectorAll('input[name="gruppo"]').length === ACCOUNT.length, 'account del gruppo');
    form.querySelector('input[name="gruppo"]').checked = true;
}

/* ---------------------------------------------------------------- casi */

test('CA-06 · il form #comunicazione della dashboard contiene il pulsante del modello usato dal modulo', () => {
    const pulsanti = formDellaDashboard().querySelectorAll('button[data-modello-scadenza]');

    assert.equal(pulsanti.length, 1, 'un pulsante con data-modello-scadenza nel form della dashboard');
    assert.equal(pulsanti[0].type, 'button', 'il pulsante del modello non invia il form');
});

test('CA-06 · il modello di scadenza si invia con la POST normale delle comunicazioni', async t => {
    const { form, chiamate } = await moduloPronto(t);

    form.querySelector('[data-modello-scadenza]').click();
    const post = await invia(form, chiamate);

    await verificaInvio(chiamate, post, {
        titolo: TITOLO_SCADENZA, contenuto: CONTENUTO_SCADENZA, destinatariTipo: 'TUTTI_GLI_OPERATORI', gruppo: null
    });
});

test('CA-06 · il modello sostituisce un gruppo di OPT già scelto con tutti gli operatori', async t => {
    const { form, chiamate } = await moduloPronto(t);
    form.elements.titolo.value = 'Oggetto manuale sintetico';
    form.elements.contenuto.value = 'Testo manuale sintetico';
    await scegliGruppoOpt(form);

    form.querySelector('[data-modello-scadenza]').click();
    await finche(() => form.querySelector('[data-destinatari]').disabled, 'gruppo disattivato');
    const post = await invia(form, chiamate);

    assert.ok(chiamate.some(chiamata => chiamata.url === '/api/comunicazioni/destinatari?tipo=GRUPPO_OPT'));
    await verificaInvio(chiamate, post, {
        titolo: TITOLO_SCADENZA, contenuto: CONTENUTO_SCADENZA, destinatariTipo: 'TUTTI_GLI_OPERATORI', gruppo: null
    });
});

test('CA-06 · controllo positivo: senza il modello si inviano i valori inseriti dal Banco', async t => {
    const { form, chiamate } = await moduloPronto(t);
    form.elements.titolo.value = 'Oggetto manuale sintetico';
    form.elements.contenuto.value = 'Testo manuale sintetico';
    await scegliGruppoOpt(form);

    const post = await invia(form, chiamate);

    await verificaInvio(chiamate, post, {
        titolo: 'Oggetto manuale sintetico', contenuto: 'Testo manuale sintetico', destinatariTipo: 'GRUPPO_OPT',
        gruppo: [ACCOUNT[0].id]
    });
});
