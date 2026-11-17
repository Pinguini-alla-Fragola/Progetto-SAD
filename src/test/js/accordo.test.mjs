import test from 'node:test';
import assert from 'node:assert/strict';
import { preparaAccordo, motivoBloccoRifiuto } from '../../main/resources/static/js/accordo.js';

test('converte l’ora locale in un istante e invia solo i campi della modalità scelta', () => {
    const dati = { modalita: 'IN_PRESENZA', dataOra: '2026-12-03T10:30', luogo: ' Sede ',
        nomeReferente: ' Nome ', cognomeReferente: ' Referente ', telefonoReferente: '+39 081 0000000', link: 'https://example.test' };
    const presenza = preparaAccordo(dati);
    assert.equal(presenza.dataOra, new Date(dati.dataOra).toISOString());
    assert.equal(presenza.luogo, 'Sede');
    assert.equal('link' in presenza, false);
    const remoto = preparaAccordo({ ...dati, modalita: 'A_DISTANZA' });
    assert.deepEqual(Object.keys(remoto).sort(), ['dataOra', 'link', 'modalita']);
});

test('rifiuta date non valide senza inviare il comando', () => {
    assert.throws(() => preparaAccordo({ modalita: 'A_DISTANZA', dataOra: '' }), e => !!e.campi.dataOra);
    assert.throws(() => preparaAccordo({ modalita: 'A_DISTANZA', dataOra: '2026-02-30T12:00' }), e => !!e.campi.dataOra);
});

test('rispetta il fuso del dispositivo anche nel cambio dell’ora legale', () => {
    const precedente = process.env.TZ;
    process.env.TZ = 'Europe/Rome';
    try {
        assert.equal(preparaAccordo({ modalita: 'A_DISTANZA', dataOra: '2026-07-03T10:30' }).dataOra, '2026-07-03T08:30:00.000Z');
        assert.equal(preparaAccordo({ modalita: 'A_DISTANZA', dataOra: '2026-12-03T10:30' }).dataOra, '2026-12-03T09:30:00.000Z');
        assert.throws(() => preparaAccordo({ modalita: 'A_DISTANZA', dataOra: '2026-03-29T02:30' }), e => !!e.campi.dataOra);
    } finally {
        if (precedente === undefined) delete process.env.TZ;
        else process.env.TZ = precedente;
    }
});

test('il rifiuto richiede l’ultimo diniego di ogni opzione e nessun contatto in attesa', () => {
    const opzione = { erogatore: { accountId: 'a', ambito: 'STESSO_COMUNE' } };
    const remoto = { erogatore: { accountId: 'a', ambito: 'CENTRO_PICUS_A_DISTANZA' } };
    const contatto = esito => ({ erogatoreId: 'a', ambito: 'STESSO_COMUNE', esito });
    assert.ok(motivoBloccoRifiuto([], [opzione]));
    assert.ok(motivoBloccoRifiuto([contatto('NON_DISPONIBILE'), contatto('DISPONIBILE')], [opzione]));
    assert.ok(motivoBloccoRifiuto([contatto('NON_DISPONIBILE')], [opzione, remoto]));
    assert.ok(motivoBloccoRifiuto([contatto('IN_ATTESA')], []));
    assert.equal(motivoBloccoRifiuto([contatto('DISPONIBILE'), contatto('NON_DISPONIBILE')], [opzione]), '');
    assert.equal(motivoBloccoRifiuto([], []), '');
});
