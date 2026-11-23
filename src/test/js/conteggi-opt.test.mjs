import test from 'node:test';
import assert from 'node:assert/strict';
import { aggiungiFinePeriodo, initConteggiOpt, valoreConteggio } from '../../main/resources/static/js/conteggi-opt.js';

test('mantiene esatti i conteggi nelle richieste JSON', () => {
    assert.equal(valoreConteggio('0'), 0);
    assert.equal(valoreConteggio('9007199254740991'), Number.MAX_SAFE_INTEGER);
    assert.equal(valoreConteggio('9007199254740993'), '9007199254740993');
    assert.equal(valoreConteggio('9223372036854775807'), '9223372036854775807');
    for (const valore of ['', '-1', '1.5', '1e2', '9223372036854775808']) {
        assert.throws(() => valoreConteggio(valore));
    }
});

test('aggiunge il periodo finale solo quando entrambi i campi sono presenti', () => {
    const params = new URLSearchParams({ anno: '2025', trimestre: '2' });
    aggiungiFinePeriodo(params, '', '');
    assert.equal(params.toString(), 'anno=2025&trimestre=2');
    assert.throws(() => aggiungiFinePeriodo(params, '2026', ''));
    assert.throws(() => aggiungiFinePeriodo(params, '', '1'));
    assert.equal(params.toString(), 'anno=2025&trimestre=2');
    aggiungiFinePeriodo(params, '2026', '1');
    assert.equal(params.toString(), 'anno=2025&trimestre=2&annoFine=2026&trimestreFine=1');
});

class Elemento {
    children = [];
    eventi = {};
    dataset = {};
    value = '';
    classList = { add() {} };

    get options() { return this.children; }
    get textContent() { return this.testo ?? this.children.map(figlio => figlio.textContent).join(''); }
    set textContent(valore) { this.testo = String(valore); this.children = []; }
    set innerHTML(valore) { assert.equal(valore, ''); this.children = []; }
    append(...nodi) { this.children.push(...nodi); }
    add(nodo) { this.append(nodo); }
    replaceChildren(...nodi) { this.children = nodi; }
    setAttribute() {}
    removeAttribute() {}
    closest() { return null; }
    contains(nodo) { return this === nodo || this.children.some(figlio => figlio.contains(nodo)); }
    addEventListener(evento, azione) { this.eventi[evento] = azione; }
    invia() { return this.eventi.submit({ preventDefault() {} }); }
}

async function schermata(t, area, rispostaConteggi) {
    const nodi = new Map();
    const nodo = id => {
        if (!nodi.has(id)) nodi.set(id, new Elemento());
        return nodi.get(id);
    };
    const idForm = area === 'opt' ? 'filtro-conteggi-opt' : 'riepilogo-opt';
    const filtro = nodo(idForm);
    filtro.elements = { anno: { value: '2025' }, trimestre: { value: '1' },
        annoFine: { value: '' }, trimestreFine: { value: '' } };
    const salva = nodo('conteggi');
    salva.elements = { anno: { value: '2025' }, trimestre: { value: '2' }, misuraId: { value: '1' } };
    const dettagliSalvataggio = new Map([
        ['select[name="misuraId"]', new Elemento()],
        ['button[type="submit"]', new Elemento()],
        ['.picus-esito', new Elemento()]
    ]);
    salva.querySelector = selettore => dettagliSalvataggio.get(selettore);
    nodo('voci-conteggi').querySelectorAll = () => [
        { querySelector: selettore => ({ value: selettore.startsWith('select') ? 'MINORI' : '7' }) }
    ];

    const globali = {
        document: { body: { dataset: { utente: 'opt-di-prova' } }, getElementById: nodo,
            createElement: () => new Elemento() },
        Option: Elemento
    };
    for (const [nome, valore] of Object.entries(globali)) {
        const prima = Object.getOwnPropertyDescriptor(globalThis, nome);
        Object.defineProperty(globalThis, nome, { value: valore, configurable: true });
        t.after(() => prima ? Object.defineProperty(globalThis, nome, prima) : delete globalThis[nome]);
    }
    t.mock.method(globalThis, 'fetch', (url, opzioni) => {
        if (url === '/api/monitoraggio/categorie') {
            return Response.json([{ codice: 'MINORI', descrizione: 'Minori' }]);
        }
        if (url === '/api/catalogo/misure') return Response.json([{ id: 1, nome: 'Misura sintetica' }]);
        if (url === '/api/auth/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'prova' });
        return rispostaConteggi(new URL(url, 'http://picus.test'), opzioni);
    });

    await initConteggiOpt(area);
    return {
        filtro, salva, messaggioSalvataggio: dettagliSalvataggio.get('.picus-esito'),
        lista: nodo(area === 'opt' ? 'conteggi-lista' : 'riepilogo-opt-lista'),
        esito: nodo(area === 'opt' ? 'conteggi-lettura-esito' : 'riepilogo-opt-esito'),
        riprova: nodo(area === 'opt' ? 'conteggi-riprova' : 'riepilogo-opt-riprova')
    };
}

function conteggio(valore) {
    return Response.json([{ optId: 'opt-di-prova', conteggio: { misuraId: 1, voce: 'MINORI', valore } }]);
}

for (const area of ['banco', 'opt']) {
    test(`${area} mostra solo l’ultima consultazione anche con risposte fuori ordine`, async t => {
        const risposte = new Map(['1', '2', '3'].map(trimestre => [trimestre, Promise.withResolvers()]));
        const ui = await schermata(t, area, url => risposte.get(url.searchParams.get('trimestre')).promise);

        const prima = ui.filtro.invia();
        ui.filtro.elements.trimestre.value = '2';
        const seconda = ui.filtro.invia();
        ui.filtro.elements.trimestre.value = '3';
        const ultima = ui.filtro.invia();

        risposte.get('3').resolve(conteggio(333));
        await ultima;
        risposte.get('1').resolve(conteggio(111));
        risposte.get('2').resolve(Response.json({}, { status: 503 }));
        await Promise.all([prima, seconda]);

        assert.match(ui.lista.textContent, /333/);
        assert.doesNotMatch(ui.lista.textContent, /111/);
        assert.equal(ui.lista.hidden, false);
        assert.equal(ui.esito.textContent, '');
        assert.equal(ui.riprova.hidden, true);
    });
}

test('un GET fallito lascia confermato il POST e il pulsante ripete solo la lettura', async t => {
    const chiamate = [];
    const ui = await schermata(t, 'opt', (url, { method }) => {
        chiamate.push({ method, trimestre: url.searchParams.get('trimestre') });
        if (method === 'POST') return new Response(null, { status: 204 });
        return chiamate.length === 2 ? Response.json({}, { status: 503 }) : conteggio(707);
    });

    await ui.salva.invia();
    assert.match(ui.messaggioSalvataggio.textContent, /salvati correttamente/);
    assert.match(ui.esito.textContent, /Riprova/);
    assert.equal(ui.riprova.hidden, false);
    assert.equal(ui.lista.hidden, true);

    await ui.riprova.eventi.click();
    assert.deepEqual(chiamate, [
        { method: 'POST', trimestre: null },
        { method: 'GET', trimestre: '2' },
        { method: 'GET', trimestre: '2' }
    ]);
    assert.match(ui.messaggioSalvataggio.textContent, /salvati correttamente/);
    assert.equal(ui.esito.textContent, '');
    assert.equal(ui.riprova.hidden, true);
    assert.equal(ui.lista.hidden, false);
    assert.match(ui.lista.textContent, /707/);
});
