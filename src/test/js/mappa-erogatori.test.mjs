import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { geoArea, geoMercator, geoPath } from '../../main/resources/static/js/vendor/d3-geo.js';
import { avviaMappaErogatori, classificaComuneSuMappa } from '../../main/resources/static/js/mappa-erogatori.js';
import { avviaCoordinamento } from '../../main/resources/static/js/richieste-area.js';

const GEOJSON_REALE = JSON.parse(
    fs.readFileSync(new URL('../../main/resources/static/dati/comuni-campania-istat-2026.json', import.meta.url), 'utf8')
);

function creaNodo({ tagName = 'DIV', id = '' } = {}) {
    const nodo = {
        tagName: tagName.toUpperCase(),
        id,
        children: [],
        dataset: {},
        attributes: {},
        eventi: {},
        hidden: false,
        disabled: false,
        value: '',
        textContent: '',
        tabIndex: -1,
        parentElement: null,
        classList: {
            _set: new Set(),
            add(...nomi) { nomi.forEach(n => this._set.add(n)); },
            remove(...nomi) { nomi.forEach(n => this._set.delete(n)); },
            toggle(nome, stato) {
                if (stato === undefined) {
                    if (this._set.has(nome)) this._set.delete(nome);
                    else this._set.add(nome);
                } else if (stato) this._set.add(nome);
                else this._set.delete(nome);
            },
            contains(nome) { return this._set.has(nome); }
        },
        setAttribute(k, v) {
            this.attributes[k] = String(v);
            if (k === 'class') {
                this.className = String(v);
                this.classList._set = new Set(String(v).split(/\s+/).filter(Boolean));
            }
        },
        getAttribute(k) { return this.attributes[k] ?? null; },
        removeAttribute(k) { delete this.attributes[k]; },
        addEventListener(nome, fn) {
            this.eventi[nome] ??= [];
            this.eventi[nome].push(fn);
        },
        dispatchEvent(evt) {
            for (const fn of this.eventi[evt.type] || []) fn(evt);
        },
        add(opt) {
            this.append(opt);
        },
        append(...nodi) {
            for (const n of nodi) {
                if (typeof n === 'string') {
                    this.children.push({ tagName: '#text', textContent: n, children: [], contains: () => false });
                } else if (n && typeof n === 'object') {
                    if (n.nodeType === 11) {
                        for (const c of n.children) this.append(c);
                    } else {
                        n.parentElement = this;
                        this.children.push(n);
                    }
                }
            }
        },
        prepend(...nodi) {
            for (const n of nodi.slice().reverse()) {
                if (n && typeof n === 'object') {
                    n.parentElement = this;
                    this.children.unshift(n);
                }
            }
        },
        remove() {
            if (this.parentElement) {
                this.parentElement.children = this.parentElement.children.filter(c => c !== this);
                this.parentElement = null;
            }
        },
        replaceChildren(...nodi) {
            this.children = [];
            this.append(...nodi);
        },
        contains(target) {
            if (target === this) return true;
            return this.children.some(c => c === target || (c.contains && c.contains(target)));
        },
        focus() {
            globalThis.document.activeElement = this;
        },
        closest() { return null; },
        querySelector(sel) { return this.querySelectorAll(sel)[0] || null; },
        querySelectorAll(sel) {
            const risultati = [];
            const visita = n => {
                for (const c of n.children || []) {
                    if (combacia(c, sel)) risultati.push(c);
                    if (c.children) visita(c);
                }
            };
            visita(this);
            return risultati;
        }
    };
    Object.defineProperty(nodo, 'className', {
        get() { return Array.from(nodo.classList._set).join(' '); },
        set(val) { nodo.classList._set = new Set(String(val || '').split(/\s+/).filter(Boolean)); }
    });
    Object.defineProperty(nodo, 'options', {
        get() { return nodo.children.filter(c => c.tagName === 'OPTION'); }
    });
    return nodo;
}

function combacia(nodo, selettore) {
    if (!nodo || nodo.tagName === '#text') return false;
    return selettore.split(',').some(parte => {
        const s = parte.trim();
        if (s === 'button') return nodo.tagName === 'BUTTON';
        if (s === 'input[type=checkbox]') return nodo.tagName === 'INPUT' && nodo.type === 'checkbox';
        if (s.startsWith('.')) return nodo.classList?.contains(s.slice(1));
        if (s.startsWith('[data-mappa-azione=')) {
            const attrMatch = s.match(/data-mappa-azione="([^"]+)"/);
            return attrMatch && nodo.dataset?.mappaAzione === attrMatch[1];
        }
        return false;
    });
}

function creaDomDettaglio(t, { comuneCodice = '061005', comuneIstat = '061005', comuneNome = 'Aversa', comuneTerritorio = 'Provincia di Caserta (CE)' } = {}) {
    const originali = Object.fromEntries(['document', 'fetch', 'location'].map(n => [n, Object.getOwnPropertyDescriptor(globalThis, n)]));
    const idMap = new Map();
    const reg = (id, tag = 'div', attrs = {}) => {
        const nodo = creaNodo({ tagName: tag, id });
        Object.assign(nodo, attrs);
        idMap.set(id, nodo);
        return nodo;
    };

    const pagina = reg('contenuto', 'main');
    pagina.dataset = { richiestaId: 'rich-1', stato: 'PRESA_IN_CARICO', banco: 'true', misura: 'M01', opt: 'OPT' };
    reg('coordinamento-azioni', 'div');
    reg('coordinamento-esito', 'p');
    reg('rifiuta-richiesta', 'button', { disabled: false });
    reg('rifiuto-motivo', 'p');
    reg('titolo-candidati', 'h3', { hidden: true });
    reg('candidati', 'div');

    const sezioneMappa = reg('sezione-mappa-erogatori', 'section', { hidden: true });
    sezioneMappa.dataset = { comuneCodice, comuneIstat, comuneNome, comuneTerritorio };
    reg('mappa-sottotitolo', 'p');
    const controlli = reg('mappa-controlli', 'div');
    for (const [azione, testo] of [['zona', 'Zona richiesta'], ['regione', 'Tutta la Campania'], ['zoom-in', '+'], ['zoom-out', '−']]) {
        const b = creaNodo({ tagName: 'button' });
        b.dataset.mappaAzione = azione;
        b.textContent = testo;
        controlli.append(b);
    }
    reg('mappa-quadro', 'div');
    const svg = reg('mappa-campania-svg', 'svg');
    svg.setAttribute('viewBox', '0 0 800 600');
    reg('mappa-strato-comuni', 'g');
    reg('mappa-strato-evidenza', 'g');
    reg('mappa-tooltip', 'p', { hidden: true });
    reg('mappa-descrizione-sr', 'p');
    reg('mappa-errore', 'p', { hidden: true });
    reg('mappa-stato-vuoto', 'p', { hidden: true });
    reg('mappa-legenda', 'ul');
    reg('elenco-territoriale-comuni', 'div');
    reg('filtro-comune-attivo', 'div', { hidden: true });

    const fakeDoc = {
        activeElement: null,
        getElementById: id => idMap.get(id) ?? null,
        createElement: tag => {
            const el = creaNodo({ tagName: tag });
            return el;
        },
        createElementNS: (_ns, tag) => creaNodo({ tagName: tag }),
        createTextNode: text => ({ tagName: '#text', textContent: text, children: [], contains: () => false })
    };
    const fakeOption = (text = '', value = '') => {
        const op = creaNodo({ tagName: 'option' });
        op.textContent = text;
        op.value = value;
        return op;
    };
    const origOption = globalThis.Option;
    globalThis.Option = function Option(text, value) { return fakeOption(text, value); };
    t.after(() => {
        if (origOption) globalThis.Option = origOption;
        else delete globalThis.Option;
    });

    Object.defineProperty(globalThis, 'document', { value: fakeDoc, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'location', { value: { pathname: '/richieste/rich-1', reload() {} }, configurable: true, writable: true });
    t.after(() => {
        for (const [k, desc] of Object.entries(originali)) {
            if (desc) Object.defineProperty(globalThis, k, desc);
            else delete globalThis[k];
        }
    });

    return { pagina, idMap };
}

test('GeoJSON reale della Campania: 550 comuni con zeri iniziali, multipoligoni e area sferica positiva in d3-geo', () => {
    assert.equal(GEOJSON_REALE.features.length, 550);
    const proiezione = geoMercator().fitExtent([[12, 12], [788, 588]], GEOJSON_REALE);
    const pathGen = geoPath(proiezione);

    for (const f of GEOJSON_REALE.features) {
        assert.match(f.properties.codiceIstat, /^[0-9]{6}$/);
        assert.ok(geoArea(f) > 0 && geoArea(f) < 0.01, `area anomala per ${f.properties.codiceIstat}`);
        assert.ok(pathGen(f)?.length > 4, `path SVG vuoto per ${f.properties.codiceIstat}`);
    }
    const napoli = GEOJSON_REALE.features.find(f => f.properties.codiceIstat === '063049');
    assert.equal(napoli.properties.codiceCatalogo, 'napoli');
    assert.equal(napoli.geometry.type, 'MultiPolygon');
});

test('evidenzia comune richiesta, limitrofi, disponibilità confermata e filtra #candidati da mappa ed elenco tastiera', async t => {
    const { pagina, idMap } = creaDomDettaglio(t);
    const candidati = [
        {
            erogatore: { accountId: 'e-stesso', nomeVisualizzato: 'OPT Aversa Confermata', tipo: 'OPT', comuneCodice: '061005', ambito: 'STESSO_COMUNE' },
            caricoAppuntamenti: 0
        },
        {
            erogatore: { accountId: 'e-limitrofo', nomeVisualizzato: 'Operatore Trentola', tipo: 'INTERNO_BANCO', comuneCodice: '061094', ambito: 'COMUNE_LIMITROFO' },
            caricoAppuntamenti: 1
        },
        {
            erogatore: { accountId: 'e-remoto', nomeVisualizzato: 'Centro PICUS Remoto', tipo: 'INTERNO_BANCO', comuneCodice: 'napoli', ambito: 'CENTRO_PICUS_A_DISTANZA' },
            caricoAppuntamenti: 2
        }
    ];
    const contatti = [
        { id: 'cnt-1', erogatoreId: 'e-stesso', ambito: 'STESSO_COMUNE', esito: 'DISPONIBILE' }
    ];

    globalThis.fetch = async url => {
        if (url.endsWith('/contatti')) return Response.json(contatti);
        if (url.endsWith('/candidati')) return Response.json(candidati);
        if (url.endsWith('/dati/comuni-campania-istat-2026.json')) return Response.json(GEOJSON_REALE);
        throw new Error('URL imprevisto: ' + url);
    };

    await avviaCoordinamento(pagina);

    assert.equal(idMap.get('sezione-mappa-erogatori').hidden, false);
    assert.equal(idMap.get('mappa-stato-vuoto').hidden, true);

    const poligoniEvidenza = idMap.get('mappa-strato-evidenza').children;
    assert.equal(poligoniEvidenza.length, 2);
    const polyAversa = poligoniEvidenza.find(p => p.getAttribute('data-codice-istat') === '061005');
    const polyTrentola = poligoniEvidenza.find(p => p.getAttribute('data-codice-istat') === '061094');
    assert.ok(polyAversa.classList.contains('comune-richiesta'));
    assert.ok(polyAversa.classList.contains('comune-confermato'));
    assert.ok(polyTrentola.classList.contains('comune-limitrofo-con-candidati'));
    assert.equal(polyTrentola.classList.contains('comune-confermato'), false);

    // Cliccando su Trentola via tastiera (Invio) sul poligono SVG filtra i candidati locali.
    polyTrentola.dispatchEvent({ type: 'keydown', key: 'Enter', preventDefault() {} });
    const visibiliDopoClick = idMap.get('candidati').querySelectorAll('.picus-list-row').filter(r => !r.hidden);
    assert.equal(visibiliDopoClick.length, 1);
    assert.match(visibiliDopoClick[0].children[0].children[0].textContent, /Operatore Trentola/);
    assert.equal(idMap.get('filtro-comune-attivo').hidden, false);

    // Rimozione filtro dall'elenco da tastiera "Tutti i candidati" ripristina tutte le righe.
    const btnTutti = idMap.get('elenco-territoriale-comuni').children[0];
    btnTutti.dispatchEvent({ type: 'click' });
    const visibiliRipristinati = idMap.get('candidati').querySelectorAll('.picus-list-row').filter(r => !r.hidden);
    assert.equal(visibiliRipristinati.length, 3);
});

test('nessun candidato locale: mostra stato vuoto, nessun punto remoto fittizio, opzione a distanza consultabile', async t => {
    const { pagina, idMap } = creaDomDettaglio(t, {
        comuneCodice: '063061',
        comuneIstat: '063061',
        comuneNome: 'Procida',
        comuneTerritorio: 'Città metropolitana di Napoli (NA)'
    });
    const candidati = [
        {
            erogatore: { accountId: 'e-centro', nomeVisualizzato: 'Centro PICUS Napoli', tipo: 'INTERNO_BANCO', comuneCodice: 'napoli', ambito: 'CENTRO_PICUS_A_DISTANZA' },
            caricoAppuntamenti: 0
        }
    ];
    globalThis.fetch = async url => {
        if (url.endsWith('/contatti')) return Response.json([]);
        if (url.endsWith('/candidati')) return Response.json(candidati);
        if (url.endsWith('/dati/comuni-campania-istat-2026.json')) return Response.json(GEOJSON_REALE);
        throw new Error('URL imprevisto: ' + url);
    };

    await avviaCoordinamento(pagina);

    assert.equal(idMap.get('mappa-stato-vuoto').hidden, false);
    assert.match(idMap.get('mappa-stato-vuoto').textContent, /Centro PICUS a distanza/);
    const evidenza = idMap.get('mappa-strato-evidenza').children;
    assert.equal(evidenza.length, 1, 'solo Procida evidenziata sulla mappa, nessun marker o comune per il remoto');
    assert.equal(evidenza[0].getAttribute('data-codice-istat'), '063061');
    assert.equal(idMap.get('candidati').querySelectorAll('.picus-list-row').length, 1);
});

test('fallimento caricamento cartografia: avviso fallback e azioni coordinamento intatte', async t => {
    const { pagina, idMap } = creaDomDettaglio(t);
    const candidati = [
        {
            erogatore: { accountId: 'e-1', nomeVisualizzato: 'Operatore Test', tipo: 'INTERNO_BANCO', comuneCodice: '061005', ambito: 'STESSO_COMUNE' },
            caricoAppuntamenti: 0
        }
    ];
    globalThis.fetch = async url => {
        if (url.endsWith('/contatti')) return Response.json([]);
        if (url.endsWith('/candidati')) return Response.json(candidati);
        if (url.endsWith('/dati/comuni-campania-istat-2026.json')) return new Response(null, { status: 500 });
        throw new Error('URL imprevisto: ' + url);
    };

    await avviaCoordinamento(pagina);

    assert.equal(idMap.get('mappa-errore').hidden, false);
    assert.equal(idMap.get('mappa-quadro').hidden, true);
    assert.equal(idMap.get('mappa-controlli').hidden, true);
    const righe = idMap.get('candidati').querySelectorAll('.picus-list-row');
    assert.equal(righe.length, 1);
    assert.equal(idMap.get('elenco-territoriale-comuni').children.length, 2);
});

test('classificaComuneSuMappa distingue compatibilità territoriale da disponibilità confermata', () => {
    const soloCompatibile = classificaComuneSuMappa({
        isRichiesta: false, locali: [{}], confermati: 0, inAttesa: 0, daVerificare: 1, nonDisponibili: 0
    });
    const confermato = classificaComuneSuMappa({
        isRichiesta: false, locali: [{}], confermati: 1, inAttesa: 0, daVerificare: 0, nonDisponibili: 0
    });
    assert.equal(soloCompatibile.haConfermato, false);
    assert.equal(confermato.haConfermato, true);
    assert.notEqual(soloCompatibile.classeCss, confermato.classeCss);
});
