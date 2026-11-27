import test from 'node:test';
import assert from 'node:assert/strict';
import { api, ErroreHttp } from '../../main/resources/static/js/area-client.js';

test('conserva messaggio e campi controllati restituiti dal backend', () => {
    const errore = new ErroreHttp(400, { messaggio: 'Controlla i campi della richiesta', campi: { comuneCodice: 'Seleziona un comune' } });
    assert.equal(errore.message, 'Controlla i campi della richiesta');
    assert.deepEqual(errore.campi, { comuneCodice: 'Seleziona un comune' });
});

test('invia Idempotency-Key e CSRF senza aggiungere campi al corpo', async t => {
    const chiamate = [];
    t.mock.method(globalThis, 'fetch', async (url, options) => {
        chiamate.push({ url, ...options });
        return Response.json(url.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'token' } : { id: 'pratica' });
    });
    const body = { misuraId: 1, tipoDestinatario: 'PERSONA', comuneCodice: '063049', description: null };
    await api('/api/richieste', { method: 'POST', body, headers: { 'Idempotency-Key': 'riferimento' } });
    assert.equal(chiamate[1].headers['Idempotency-Key'], 'riferimento');
    assert.equal(chiamate[1].headers['X-CSRF-TOKEN'], 'token');
    assert.deepEqual(JSON.parse(chiamate[1].body), body);
});

test('una pagina HTML dopo un redirect non viene scambiata per un salvataggio riuscito', async t => {
    t.mock.method(globalThis, 'fetch', async () => new Response('<html>Login</html>', { headers: { 'Content-Type': 'text/html' } }));
    await assert.rejects(api('/api/richieste/mie'), ErroreHttp);
});

test('traduce la sessione scaduta e accetta una risposta 204', async t => {
    t.mock.method(globalThis, 'fetch', async () => new Response(null, { status: 401 }));
    await assert.rejects(api('/api/richieste/mie'), err => err.stato === 401 && /sessione/i.test(err.message));
    globalThis.fetch = async () => new Response(null, { status: 204 });
    assert.equal(await api('/api/auth/logout'), null);
});

test('passa il corpo JSON integro alla decodifica opzionale', async t => {
    const grezzo = '{"lungo":9223372036854775807}';
    t.mock.method(globalThis, 'fetch', async () => new Response(
        grezzo,
        { status: 200, headers: { 'Content-Type': 'application/json' } }
    ));
    const parsed = await api('/api/monitoraggio/schede-operatore/sch-1', {
        decodificaJson: testo => ({ testo })
    });
    assert.deepEqual(parsed, { testo: grezzo });
});

test('bodyJson invia una stringa JSON già serializzata con Content-Type e CSRF senza doppia codifica', async t => {
    const chiamate = [];
    t.mock.method(globalThis, 'fetch', async (url, options) => {
        chiamate.push({ url, ...options });
        return Response.json(url.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'token-json' } : { id: 'ok' });
    });
    const grezzo = '{"ore":1234567890123.123456,"costoOrarioLordo":9999999999999.999999}';
    await api('/api/monitoraggio/schede-operatore', { method: 'POST', body: grezzo, bodyJson: true });
    assert.equal(chiamate[1].headers['Content-Type'], 'application/json');
    assert.equal(chiamate[1].headers['X-CSRF-TOKEN'], 'token-json');
    assert.equal(chiamate[1].body, grezzo);
});
