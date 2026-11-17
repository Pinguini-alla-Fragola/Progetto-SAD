import test from 'node:test';
import assert from 'node:assert/strict';

async function schermata(t, esito = 'IN_ATTESA', { assegnata = false, errorePost = false } = {}) {
    const elementi = new Map();
    const nodo = id => {
        if (!elementi.has(id)) elementi.set(id, {
            hidden: true, textContent: '', dataset: {}, attributi: {}, eventi: {},
            setAttribute(nome, valore) { this.attributi[nome] = valore; },
            removeAttribute(nome) { delete this.attributi[nome]; },
            focus() { documento.activeElement = this; },
            addEventListener(nome, callback) { this.eventi[nome] = callback; }
        });
        return elementi.get(id);
    };
    const documento = { getElementById: nodo, querySelectorAll: () => [], addEventListener() {}, activeElement: null };
    nodo('contenuto').dataset = { contattiDisponibili: 'true', contattoId: 'contatto' };
    const pulsanti = ['btnDisponibile', 'btnNonDisponibile'].map((id, i) => {
        const pulsante = nodo(id);
        pulsante.dataset.disponibile = String(i === 0);
        pulsante.textContent = i === 0 ? 'Conferma la disponibilità' : 'Comunica indisponibilità';
        pulsante.disabled = false;
        return pulsante;
    });
    nodo('sezione-azioni').querySelectorAll = () => pulsanti;
    const contatto = { id: 'contatto', richiestaId: 'richiesta', erogatoreId: 'operatore', esito, ambito: 'STESSO_COMUNE' };
    let duranteInvio;
    t.mock.method(globalThis, 'fetch', async (url, options) => {
        if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        if (options.method === 'POST') {
            duranteInvio = { disabilitati: pulsanti.every(p => p.disabled), busy: nodo('sezione-azioni').attributi['aria-busy'] };
            if (errorePost) return Response.json({}, { status: 500 });
            contatto.esito = JSON.parse(options.body).disponibile ? 'DISPONIBILE' : 'NON_DISPONIBILE';
            return Response.json(contatto);
        }
        if (url.includes('/api/richieste/')) return assegnata
            ? Response.json({ erogatoreAssegnatoId: 'operatore' })
            : Response.json({}, { status: 403 });
        return Response.json({ contatto, nomeMisura: 'Accoglienza e ascolto', comuneCodice: 'napoli', tipoDestinatario: 'PERSONA' });
    });
    const precedente = Object.getOwnPropertyDescriptor(globalThis, 'document');
    Object.defineProperty(globalThis, 'document', { value: documento, configurable: true });
    t.after(() => {
        if (precedente) Object.defineProperty(globalThis, 'document', precedente);
        else delete globalThis.document;
    });
    await import('../../main/resources/static/js/disponibilita.js?test=' + crypto.randomUUID());
    return { nodo, pulsanti, documento, invia: async disponibile => {
        await pulsanti[disponibile ? 0 : 1].eventi.click();
        return duranteInvio;
    } };
}

for (const disponibile of [true, false]) {
    test(`la risposta ${disponibile ? 'positiva' : 'negativa'} mostra l'esito, sposta il focus e impedisce doppi invii`, async t => {
        const ui = await schermata(t);
        assert.equal(ui.nodo('sezione-azioni').hidden, false);
        assert.equal(ui.nodo('messaggio-conferma').hidden, true);
        assert.deepEqual(await ui.invia(disponibile), { disabilitati: true, busy: 'true' });
        assert.equal(ui.nodo('sezione-azioni').hidden, true);
        assert.equal(ui.nodo('messaggio-conferma').hidden, false);
        assert.equal(ui.nodo('messaggio-conferma').dataset.esito, disponibile ? 'DISPONIBILE' : 'NON_DISPONIBILE');
        assert.equal(ui.documento.activeElement, ui.nodo('titolo-esito'));
        assert.equal(ui.nodo('attesa-assegnazione').hidden, !disponibile);
        assert.equal(ui.nodo('assegnazione-contatto').hidden, true);
        assert.match(ui.nodo('testo-esito').textContent, disponibile ? /disponibilità al Banco/ : /indisponibilità/);
    });

    test(`una risposta ${disponibile ? 'positiva' : 'negativa'} già salvata conserva il riscontro senza spostare il focus`, async t => {
        const ui = await schermata(t, disponibile ? 'DISPONIBILE' : 'NON_DISPONIBILE');
        assert.equal(ui.nodo('sezione-azioni').hidden, true);
        assert.equal(ui.nodo('messaggio-conferma').hidden, false);
        assert.equal(ui.nodo('attesa-assegnazione').hidden, !disponibile);
        assert.equal(ui.documento.activeElement, null);
    });
}

test('un errore di invio conserva la possibilità di rispondere e porta il focus all’errore', async t => {
    const ui = await schermata(t, 'IN_ATTESA', { errorePost: true });
    await ui.invia(true);
    assert.equal(ui.nodo('errore-contatto').hidden, false);
    assert.equal(ui.nodo('messaggio-conferma').hidden, true);
    assert.equal(ui.nodo('sezione-azioni').hidden, false);
    assert.equal(ui.documento.activeElement, ui.nodo('errore-contatto'));
    assert.ok(ui.pulsanti.every(p => !p.disabled));
    assert.equal(ui.nodo('sezione-azioni').attributi['aria-busy'], undefined);
    assert.equal(ui.pulsanti[0].textContent, 'Conferma la disponibilità');
});

test('una richiesta assegnata mostra il collegamento alla pratica al posto dell’attesa', async t => {
    const ui = await schermata(t, 'DISPONIBILE', { assegnata: true });
    assert.equal(ui.nodo('messaggio-conferma').hidden, false);
    assert.equal(ui.nodo('assegnazione-contatto').hidden, false);
    assert.equal(ui.nodo('attesa-assegnazione').hidden, true);
    assert.equal(ui.nodo('link-pratica-assegnata').href, '/richieste/richiesta');
});

test('un dettaglio non consultabile conferma la disponibilità senza dedurre lo stato della pratica', async t => {
    const ui = await schermata(t, 'DISPONIBILE');
    assert.match(ui.nodo('testo-attesa').textContent, /disponibilità.*registrata/i);
    assert.doesNotMatch(ui.nodo('testo-attesa').textContent, /presa in carico|in programmazione|attesa di assegnazione/i);
    assert.equal(ui.nodo('assegnazione-contatto').hidden, true);
});
