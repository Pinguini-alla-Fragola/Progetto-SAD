import test from 'node:test';
import assert from 'node:assert/strict';
import { aggiungiFinePeriodo, valoreConteggio } from '../../main/resources/static/js/conteggi-opt.js';

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
