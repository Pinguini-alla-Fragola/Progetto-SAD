/*
 * #145 · Vista dei riscontri del Banco (codice della #144, PR #193), lato pagina.
 *
 * I test Java verificano via HTTP che la rotta /api/comunicazioni/{id}/riscontri distingua le conferme dalle
 * attese. Questi casi verificano che la scheda del Banco costruita da communicationCards mostri quella
 * distinzione: pulsante «Mostra riscontri», riepilogo, stato e data di ogni destinatario, omonimi, filtro per
 * stato ed errori. È il controllo automatico del criterio «in browser» della #145.
 *
 * Il DOM finto copre solo ciò che usano communication-view.js e list-view.js per questa scheda: createElement,
 * Option, append, replaceChildren, remove, textContent, classList, dataset, attributi, eventi e focus. fetch è
 * simulato e risponde con dati sintetici.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { setImmediate } from 'node:timers/promises';

/* ---------------------------------------------------------------- DOM finto minimo */

class Testo {
    constructor(testo) {
        this.testo = String(testo);
        this.parentElement = null;
    }

    get textContent() { return this.testo; }
}

class Elemento {
    constructor(tag) {
        this.tagName = tag;
        this.figli = [];
        this.parentElement = null;
        this.attributi = new Map();
        this.ascoltatori = new Map();
        this.className = '';
        this.hidden = false;
        this.disabled = false;
        this.value = '';
        this.dataset = {};
        const elemento = this;
        this.classList = {
            elenco: () => elemento.className.split(/\s+/).filter(Boolean),
            contains: classe => this.classList.elenco().includes(classe),
            add: (...classi) => { elemento.className = [...new Set([...this.classList.elenco(), ...classi])].join(' '); },
            remove: (...classi) => {
                elemento.className = this.classList.elenco().filter(c => !classi.includes(c)).join(' ');
            },
            toggle: classe => {
                const presente = this.classList.contains(classe);
                presente ? this.classList.remove(classe) : this.classList.add(classe);
                return !presente;
            },
            replace: (vecchia, nuova) => {
                elemento.className = this.classList.elenco().map(c => c === vecchia ? nuova : c).join(' ');
            }
        };
    }

    get textContent() {
        return this.figli.map(figlio => figlio.textContent).join('');
    }

    set textContent(testo) {
        this.figli = testo === '' || testo == null ? [] : [new Testo(testo)];
    }

    append(...nodi) {
        for (const nodo of nodi) {
            const figlio = typeof nodo === 'string' ? new Testo(nodo) : nodo;
            if (figlio.parentElement) figlio.remove();
            figlio.parentElement = this;
            this.figli.push(figlio);
        }
    }

    replaceChildren(...nodi) {
        for (const figlio of this.figli) figlio.parentElement = null;
        this.figli = [];
        this.append(...nodi);
    }

    remove() {
        if (!this.parentElement) return;
        const fratelli = this.parentElement.figli;
        fratelli.splice(fratelli.indexOf(this), 1);
        this.parentElement = null;
    }

    add(opzione) { this.append(opzione); }

    contains(nodo) {
        return nodo === this || this.figli.some(figlio => figlio instanceof Elemento && figlio.contains(nodo));
    }

    closest() { return null; }
    focus() {}

    setAttribute(nome, valore) { this.attributi.set(nome, String(valore)); }
    getAttribute(nome) { return this.attributi.has(nome) ? this.attributi.get(nome) : null; }
    removeAttribute(nome) { this.attributi.delete(nome); }
    hasAttribute(nome) { return this.attributi.has(nome); }

    addEventListener(evento, azione) {
        if (!this.ascoltatori.has(evento)) this.ascoltatori.set(evento, []);
        this.ascoltatori.get(evento).push(azione);
    }

    /** Esegue gli ascoltatori dell'evento e attende quelli asincroni. */
    async scatena(evento) {
        await Promise.all((this.ascoltatori.get(evento) || []).map(azione => azione({ key: '', preventDefault() {} })));
    }

    discendenti() {
        return this.figli.filter(figlio => figlio instanceof Elemento)
            .flatMap(figlio => [figlio, ...figlio.discendenti()]);
    }

    /** Primo discendente con il tag indicato e, se dato, il testo esatto. */
    trova(tag, testo) {
        return this.discendenti().find(nodo => nodo.tagName === tag && (testo === undefined || nodo.textContent === testo));
    }

    tutti(tag, classe) {
        return this.discendenti().filter(nodo => nodo.tagName === tag && (!classe || nodo.classList.contains(classe)));
    }

    /** Vero se il nodo e tutti i suoi antenati sono visibili. */
    visibile() {
        for (let nodo = this; nodo; nodo = nodo.parentElement) if (nodo.hidden) return false;
        return true;
    }
}

function preparaPagina(t) {
    const documento = Object.assign(new EventTarget(), {
        createElement: tag => new Elemento(tag),
        activeElement: null
    });
    function Option(testo, valore) {
        const opzione = new Elemento('option');
        opzione.textContent = testo;
        opzione.value = valore;
        return opzione;
    }
    for (const [nome, valore] of Object.entries({ document: documento, Option })) {
        const precedente = Object.getOwnPropertyDescriptor(globalThis, nome);
        Object.defineProperty(globalThis, nome, { value: valore, configurable: true, writable: true });
        t.after(() => precedente ? Object.defineProperty(globalThis, nome, precedente) : delete globalThis[nome]);
    }
}

/* ---------------------------------------------------------------- dati e scheda */

const COMUNICAZIONE = {
    id: 'comunicazione-sintetica',
    titolo: 'Avviso di prova',
    contenuto: 'Testo sintetico',
    destinatariTipo: 'GRUPPO_OPT',
    inviataIl: '2026-11-22T08:00:00Z',
    allegati: []
};
const OPT_A = '00000000-0000-4000-8000-0000000001a1';
const OPT_B = '00000000-0000-4000-8000-0000000001b2';
const ROSSI_1 = '00000000-0000-4000-8000-0000000001c3';
const ROSSI_2 = '00000000-0000-4000-8000-0000000001d4';
const CONFERMA_A = '2026-11-22T10:15:30.123456Z';
const formatDate = iso => 'data:' + iso;

/** Simula il server: ogni risposta della coda serve una lettura dei riscontri, nell'ordine. */
function simulaServer(t, ...risposte) {
    const chiamate = [];
    t.mock.method(globalThis, 'fetch', async (url, opzioni = {}) => {
        chiamate.push({ url, opzioni });
        const risposta = risposte.shift();
        assert.ok(risposta, 'richiesta non prevista: ' + url);
        return typeof risposta === 'number' ? new Response(null, { status: risposta }) : Response.json(risposta);
    });
    return chiamate;
}

async function moduloVista(t) {
    preparaPagina(t);
    return import('../../main/resources/static/js/communication-view.js');
}

function schedaBanco(communicationCards, opzioni = {}) {
    const [scheda] = communicationCards([COMUNICAZIONE], {
        banco: true, conRiscontri: true, formatDate, onConfirm: () => {}, ...opzioni
    });
    return scheda;
}

function pannello(scheda) {
    return scheda.tutti('section', 'receipt-panel')[0];
}

function righe(scheda) {
    return pannello(scheda).tutti('div', 'receipt-row');
}

/** Riga del destinatario: nome, eventuale riferimento all'account, stato e data. */
function leggiRiga(riga) {
    const [destinatario, dettaglio] = riga.figli;
    const tempo = dettaglio.trova('time');
    return {
        nome: destinatario.trova('strong').textContent,
        idAccount: destinatario.trova('p')?.textContent ?? null,
        stato: dettaglio.figli[0].textContent,
        data: tempo ? { testo: tempo.textContent, dateTime: tempo.dateTime } : null,
        visibile: riga.visibile()
    };
}

function riepilogo(scheda) {
    return pannello(scheda).trova('div').trova('p').textContent;
}

function esito(scheda) {
    return pannello(scheda).figli[1];
}

async function apri(pulsante) {
    await pulsante.scatena('click');
    await setImmediate();
}

/* ---------------------------------------------------------------- casi */

test('#145 · il Banco vede «Mostra riscontri» e non il pulsante di conferma; il destinatario il contrario', async t => {
    const { communicationCards } = await moduloVista(t);

    const banco = schedaBanco(communicationCards, { conConferma: true });
    const [destinatario] = communicationCards([COMUNICAZIONE], {
        banco: false, conRiscontri: true, conConferma: true, formatDate, onConfirm: () => {}
    });

    assert.ok(banco.trova('button', 'Mostra riscontri'));
    assert.equal(banco.trova('button', 'Confermo la ricezione'), undefined);
    assert.ok(destinatario.trova('button', 'Confermo la ricezione'));
    assert.equal(destinatario.trova('button', 'Mostra riscontri'), undefined);
    assert.equal(destinatario.tutti('section', 'receipt-panel').length, 0);
});

test('#145 · il pannello resta chiuso e non chiede nulla al server finché il Banco non lo apre', async t => {
    const { communicationCards } = await moduloVista(t);
    const chiamate = simulaServer(t);

    const scheda = schedaBanco(communicationCards);
    const pulsante = scheda.trova('button', 'Mostra riscontri');

    assert.equal(pannello(scheda).hidden, true);
    assert.equal(pulsante.getAttribute('aria-expanded'), 'false');
    assert.equal(pulsante.getAttribute('aria-controls'), pannello(scheda).id);
    assert.equal(chiamate.length, 0);
});

test('AC2 · aperto il pannello, il Banco distingue la conferma con data e ora dal destinatario in attesa', async t => {
    const { communicationCards } = await moduloVista(t);
    const chiamate = simulaServer(t, [
        { destinatarioId: OPT_A, nomeVisualizzato: 'OPT A', confermataIl: CONFERMA_A },
        { destinatarioId: OPT_B, nomeVisualizzato: 'OPT B', confermataIl: null }
    ]);
    const scheda = schedaBanco(communicationCards);
    const pulsante = scheda.trova('button', 'Mostra riscontri');

    await apri(pulsante);

    assert.equal(chiamate.length, 1);
    assert.equal(chiamate[0].url, '/api/comunicazioni/comunicazione-sintetica/riscontri');
    assert.equal(chiamate[0].opzioni.method, 'GET');
    assert.equal(pannello(scheda).hidden, false);
    assert.equal(pulsante.textContent, 'Nascondi riscontri');
    assert.equal(pulsante.getAttribute('aria-expanded'), 'true');
    assert.equal(riepilogo(scheda), '1 di 2 ricezioni confermate');
    assert.deepEqual(righe(scheda).map(leggiRiga), [
        { nome: 'OPT A', idAccount: null, stato: 'Confermata',
            data: { testo: formatDate(CONFERMA_A), dateTime: CONFERMA_A }, visibile: true },
        { nome: 'OPT B', idAccount: null, stato: 'In attesa di conferma', data: null, visibile: true }
    ]);
    assert.equal(esito(scheda).textContent, '');
});

test('AC3 · tutti in attesa: nessuna data e riepilogo a zero', async t => {
    const { communicationCards } = await moduloVista(t);
    simulaServer(t, [
        { destinatarioId: OPT_A, nomeVisualizzato: 'OPT A', confermataIl: null },
        { destinatarioId: OPT_B, nomeVisualizzato: 'OPT B', confermataIl: null }
    ]);
    const scheda = schedaBanco(communicationCards);

    await apri(scheda.trova('button', 'Mostra riscontri'));

    assert.equal(riepilogo(scheda), '0 di 2 ricezioni confermate');
    assert.ok(righe(scheda).map(leggiRiga).every(riga => riga.stato === 'In attesa di conferma' && riga.data === null));
});

test('AC2 · omonimi: il riferimento all\'account compare solo per i nomi ripetuti, anche con maiuscole diverse', async t => {
    const { communicationCards } = await moduloVista(t);
    simulaServer(t, [
        { destinatarioId: OPT_A, nomeVisualizzato: 'OPT A', confermataIl: null },
        { destinatarioId: ROSSI_1, nomeVisualizzato: 'Operatore Rossi', confermataIl: CONFERMA_A },
        { destinatarioId: ROSSI_2, nomeVisualizzato: 'operatore rossi', confermataIl: null }
    ]);
    const scheda = schedaBanco(communicationCards);

    await apri(scheda.trova('button', 'Mostra riscontri'));

    const [opt, primo, secondo] = righe(scheda).map(leggiRiga);
    assert.equal(opt.idAccount, null);
    assert.equal(primo.idAccount, 'ID account ' + ROSSI_1);
    assert.equal(secondo.idAccount, 'ID account ' + ROSSI_2);
    assert.equal(primo.stato, 'Confermata');
    assert.equal(secondo.stato, 'In attesa di conferma');
});

test('AC2 · il filtro «Ricezione» mostra solo i destinatari ancora in attesa', async t => {
    const { communicationCards } = await moduloVista(t);
    simulaServer(t, [
        { destinatarioId: OPT_A, nomeVisualizzato: 'OPT A', confermataIl: CONFERMA_A },
        { destinatarioId: OPT_B, nomeVisualizzato: 'OPT B', confermataIl: null }
    ]);
    const scheda = schedaBanco(communicationCards);
    await apri(scheda.trova('button', 'Mostra riscontri'));

    const filtro = pannello(scheda).tutti('select').find(select => select.dataset.filter === 'stato');
    assert.deepEqual(filtro.figli.map(opzione => opzione.value), ['', 'Confermata', 'In attesa di conferma']);
    filtro.value = 'In attesa di conferma';
    await filtro.scatena('change');

    assert.deepEqual(righe(scheda).map(leggiRiga).map(({ nome, visibile }) => ({ nome, visibile })), [
        { nome: 'OPT A', visibile: false },
        { nome: 'OPT B', visibile: true }
    ]);
});

test('#145 · comunicazione senza destinatari: riepilogo a zero e messaggio di elenco vuoto', async t => {
    const { communicationCards } = await moduloVista(t);
    simulaServer(t, []);
    const scheda = schedaBanco(communicationCards);

    await apri(scheda.trova('button', 'Mostra riscontri'));

    assert.equal(riepilogo(scheda), '0 di 0 ricezioni confermate');
    assert.equal(righe(scheda).length, 0);
    const vuoto = pannello(scheda).tutti('p', 'picus-empty')[0];
    assert.equal(vuoto.textContent, 'Nessun destinatario.');
    assert.equal(vuoto.visibile(), true);
});

test('#145 · errore del server: messaggio con l\'invito a riprovare, nessuna riga e nessun riepilogo', async t => {
    const { communicationCards } = await moduloVista(t);
    simulaServer(t, 403);
    const scheda = schedaBanco(communicationCards);

    await apri(scheda.trova('button', 'Mostra riscontri'));

    assert.match(esito(scheda).textContent, /Chiudi e riapri i riscontri per riprovare\.$/);
    assert.ok(esito(scheda).classList.contains('picus-errore'));
    assert.equal(esito(scheda).getAttribute('role'), 'status');
    assert.equal(riepilogo(scheda), '');
    assert.equal(righe(scheda).length, 0);
});

test('#145 · chiudere non interroga il server; riaprire rilegge i riscontri aggiornati e toglie l\'errore', async t => {
    const { communicationCards } = await moduloVista(t);
    const chiamate = simulaServer(t, 503,
        [
            { destinatarioId: OPT_A, nomeVisualizzato: 'OPT A', confermataIl: null },
            { destinatarioId: OPT_B, nomeVisualizzato: 'OPT B', confermataIl: null }
        ],
        [
            { destinatarioId: OPT_A, nomeVisualizzato: 'OPT A', confermataIl: CONFERMA_A },
            { destinatarioId: OPT_B, nomeVisualizzato: 'OPT B', confermataIl: null }
        ]);
    const scheda = schedaBanco(communicationCards);
    const pulsante = scheda.trova('button', 'Mostra riscontri');

    await apri(pulsante);
    assert.ok(esito(scheda).classList.contains('picus-errore'));

    await apri(pulsante);
    assert.equal(pannello(scheda).hidden, true);
    assert.equal(pulsante.textContent, 'Mostra riscontri');
    assert.equal(pulsante.getAttribute('aria-expanded'), 'false');
    assert.equal(chiamate.length, 1);

    await apri(pulsante);
    assert.equal(chiamate.length, 2);
    assert.equal(esito(scheda).textContent, '');
    assert.equal(esito(scheda).classList.contains('picus-errore'), false);
    assert.equal(riepilogo(scheda), '0 di 2 ricezioni confermate');

    await apri(pulsante);
    await apri(pulsante);
    assert.equal(chiamate.length, 3);
    assert.equal(riepilogo(scheda), '1 di 2 ricezioni confermate');
    assert.equal(leggiRiga(righe(scheda)[0]).stato, 'Confermata');
});