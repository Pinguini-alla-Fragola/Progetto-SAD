import test from 'node:test';
import assert from 'node:assert/strict';
import { setImmediate } from 'node:timers/promises';
import { communicationCards } from '../../main/resources/static/js/communication-view.js';

class Elemento {
    figli = [];
    classList = { replace() {}, add() {} };

    append(...elementi) {
        for (const elemento of elementi) {
            elemento.padre = this;
            this.figli.push(elemento);
        }
    }

    querySelector(selettore) {
        for (const figlio of this.figli) {
            if ('.' + figlio.className === selettore) return figlio;
            const discendente = figlio.querySelector(selettore);
            if (discendente) return discendente;
        }
    }

    remove() {
        this.padre.figli.splice(this.padre.figli.indexOf(this), 1);
    }

    setAttribute() {}
    addEventListener(evento, azione) {
        if (evento === 'click') this.clicca = azione;
    }
}

test('aggiorna il contatore a zero solo dopo una conferma riuscita', async t => {
    const voce = new Elemento();
    const documento = Object.assign(new EventTarget(), {
        createElement: () => new Elemento(),
        querySelector: () => voce
    });
    for (const [nome, valore] of Object.entries({ document: documento, window: new EventTarget() })) {
        const precedente = Object.getOwnPropertyDescriptor(globalThis, nome);
        Object.defineProperty(globalThis, nome, { value: valore, configurable: true });
        t.after(() => precedente ? Object.defineProperty(globalThis, nome, precedente) : delete globalThis[nome]);
    }

    let letture = 0;
    let tentativi = 0;
    let ricariche = 0;
    t.mock.method(globalThis, 'fetch', (url, opzioni) => {
        if (url === '/api/comunicazioni/da-confermare') {
            letture++;
            return Response.json({ daConfermare: tentativi < 2 ? 1 : 0 });
        }
        if (url === '/api/auth/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'prova' });
        assert.equal(url, '/api/comunicazioni/messaggio/ricezione');
        assert.equal(opzioni.method, 'POST');
        assert.equal(opzioni.headers['X-CSRF-TOKEN'], 'prova');
        tentativi++;
        return tentativi === 1 ? new Response(null, { status: 503 }) : Response.json('2026-03-01T09:00:00Z');
    });

    await import('../../main/resources/static/js/notifiche-comunicazioni.js');
    await setImmediate();
    assert.equal(voce.querySelector('.contatore-notifiche').figli[0].textContent, '1');

    const [scheda] = communicationCards([{
        id: 'messaggio', contenuto: 'Testo sintetico', allegati: []
    }], {
        banco: false, conConferma: true, formatDate: () => '', onConfirm: () => { ricariche++; }
    });
    const pulsante = scheda.querySelector('.message-actions').figli[0];
    await pulsante.clicca();
    assert.equal(pulsante.disabled, false);
    assert.equal(letture, 1);
    assert.equal(ricariche, 0);

    await pulsante.clicca();
    await setImmediate();
    assert.equal(ricariche, 1);
    assert.equal(letture, 2);
    assert.equal(voce.querySelector('.contatore-notifiche'), undefined);
});
