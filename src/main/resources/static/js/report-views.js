import { renderList, listRow } from './list-view.js';

const money = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' });
const types = { INTERNO_BANCO: 'Interno del Banco', ESTERNO_PAGATO_BANCO: 'Esterno pagato dal Banco' };
let nextDetail = 0;

function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = text;
    if (className) node.className = className;
    return node;
}

function table(headers, rows) {
    const wrapper = el('div', null, 'report-table-wrap');
    const node = el('table', null, 'report-table');
    const head = el('thead');
    const tr = el('tr');
    headers.forEach(text => { const cell = el('th', text); cell.scope = 'col'; tr.append(cell); });
    head.append(tr);
    const body = el('tbody');
    rows.forEach(values => {
        const row = el('tr');
        values.forEach(value => row.append(el('td', value)));
        body.append(row);
    });
    node.append(head, body);
    wrapper.append(node);
    return wrapper;
}

function metric(label, value) {
    const pair = el('div');
    pair.append(el('dt', label), el('dd', value));
    return pair;
}

function expandable(identity, metrics, detail, noun) {
    const row = el('article', null, 'report-record');
    const header = el('div', null, 'report-heading');
    const toggle = el('button', 'Apri ' + noun, 'btn btn-secondary btn-sm');
    toggle.type = 'button';
    detail.id = 'report-detail-' + ++nextDetail;
    detail.classList.add('record-details');
    detail.hidden = true;
    toggle.setAttribute('aria-controls', detail.id);
    toggle.setAttribute('aria-expanded', 'false');
    toggle.addEventListener('click', () => {
        detail.hidden = !detail.hidden;
        toggle.textContent = (detail.hidden ? 'Apri ' : 'Chiudi ') + noun;
        toggle.setAttribute('aria-expanded', String(!detail.hidden));
    });
    header.append(identity, metrics, toggle);
    row.append(header, detail);
    return row;
}

function shell(host) {
    let list = host.querySelector(':scope > .report-results');
    let summary = host.querySelector(':scope > .report-summary');
    if (!list) {
        host.removeAttribute('aria-live');
        list = el('div', null, 'report-results');
        list.id = host.id + '-results';
        summary = el('section', null, 'report-summary');
        host.replaceChildren(list, summary);
    }
    return { list, summary };
}

export function operatorReports(host, sheets, accounts) {
    const { list, summary } = shell(host);
    const rows = sheets.map(sheet => {
        const account = accounts.find(a => a.id === sheet.operatoreId);
        const name = [sheet.nome, sheet.cognome].filter(Boolean).join(' ');
        const identity = el('div', null, 'report-identity');
        identity.append(el('h3', name), el('p', [sheet.qualifica, `${sheet.anno} · ${sheet.trimestre}º trimestre`, account?.comune].filter(Boolean).join(' · '), 'picus-meta'));
        const metrics = el('dl', null, 'report-metrics');
        metrics.append(metric('Ore', sheet.ore), metric('Costo orario lordo', money.format(sheet.costoOrario)),
            metric('Voci dichiarate', Object.keys(sheet.aggregati || {}).length));
        const detail = el('div');
        const facts = el('dl', null, 'picus-dati');
        for (const [label, value] of [
            ['Codice fiscale', sheet.codiceFiscale], ['Luogo di nascita', sheet.luogoNascita],
            ['Data di nascita', sheet.dataNascita], ['Qualifica', sheet.qualifica], ['Contratto', sheet.tipoContratto],
            ['Area e livello', sheet.areaLivello], ['Firma dichiarata', sheet.firma]
        ]) if (value != null && value !== '') facts.append(el('dt', label), el('dd', value));
        detail.append(facts, table(['Voce dichiarata', 'Conteggio'], Object.entries(sheet.aggregati || {})));
        return listRow(expandable(identity, metrics, detail, 'scheda'), {
            text: [name, sheet.codiceFiscale, sheet.qualifica, sheet.tipoContratto, sheet.anno, sheet.trimestre, account?.comune].join(' '),
            filters: { comune: account?.comune, tipologia: types[account?.tipoOperatore], qualifica: sheet.qualifica,
                anno: sheet.anno, trimestre: sheet.trimestre }, data: sheet
        });
    });
    renderList(list, rows, { searchLabel: 'Cerca schede', placeholder: 'Nome, codice fiscale o qualifica', empty: 'Nessuna scheda per il periodo selezionato.',
        filters: accounts.length ? [{ key: 'comune', label: 'Comune' }, { key: 'tipologia', label: 'Tipologia' }, { key: 'qualifica', label: 'Qualifica' }]
            : [{ key: 'anno', label: 'Anno' }, { key: 'trimestre', label: 'Trimestre' }],
        onFilter: matching => {
            const totals = new Map();
            matching.forEach(row => Object.entries(row.listData.aggregati || {}).forEach(([label, value]) => {
                const key = label.toLocaleLowerCase('it-IT');
                const total = totals.get(key) || { label, value: 0 };
                total.value += Number(value);
                totals.set(key, total);
            }));
            summary.hidden = !matching.length;
            summary.replaceChildren(el('h3', 'Totali delle schede filtrate'),
                el('p', `${matching.length} ${matching.length === 1 ? 'scheda' : 'schede'} · tutti i risultati della ricerca, anche nelle altre pagine`, 'picus-meta'),
                table(['Voce dichiarata', 'Totale'], [...totals.values()]
                    .sort((a, b) => a.label.localeCompare(b.label, 'it')).map(total => [total.label, total.value])));
        }
    });
}

export function optReports(host, counts, accounts) {
    const { list, summary } = shell(host);
    summary.hidden = true;
    const groups = new Map();
    for (const count of counts) {
        if (!groups.has(count.optId)) groups.set(count.optId, []);
        groups.get(count.optId).push(count);
    }
    const rows = [...groups].sort(([, a], [, b]) => a[0].organizzazione.localeCompare(b[0].organizzazione, 'it')).map(([id, values]) => {
        const name = values[0].organizzazione;
        const account = accounts.find(a => a.id === id);
        const identity = el('div', null, 'report-identity');
        identity.append(el('h3', name), el('p', account?.comune || '', 'picus-meta'));
        const metrics = el('dl', null, 'report-metrics');
        metrics.append(metric('Misure', new Set(values.map(v => v.misuraId)).size), metric('Voci dichiarate', values.length));
        const detail = el('div');
        const detailsList = el('div');
        const detailRows = values.map(value => {
            const row = el('div', null, 'count-report-row');
            row.append(el('strong', value.misura), el('span', value.voce), el('strong', String(value.valore), 'count-value'));
            return listRow(row, { filters: { misura: value.misura, voce: value.voce } });
        });
        renderList(detailsList, detailRows, { searchLabel: 'Cerca nei conteggi di ' + name, placeholder: 'Misura o voce dichiarata',
            filters: [{ key: 'misura', label: 'Misura' }, { key: 'voce', label: 'Voce' }], columns: ['Misura', 'Voce dichiarata', 'Conteggio'] });
        detail.append(detailsList);
        return listRow(expandable(identity, metrics, detail, 'conteggi'), {
            text: [name, account?.comune, ...values.flatMap(v => [v.misura, v.voce])].join(' '), filters: { comune: account?.comune }
        });
    });
    renderList(list, rows, { searchLabel: 'Cerca organizzazioni', placeholder: 'OPT, comune, misura o voce',
        filters: [{ key: 'comune', label: 'Comune' }], empty: 'Nessun conteggio per il periodo selezionato.' });
}
