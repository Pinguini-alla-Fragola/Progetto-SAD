import { api, esci, pagina } from './area-client.js';
import { setupWorkspace } from './workspace.js';
import { renderList, listRow } from './list-view.js';
import { operatorReports, optReports } from './report-views.js';
import { communications } from './communication-view.js';

const area = document.body.dataset.area;
const utente = document.body.dataset.utente;
const byId = (id) => document.getElementById(id);
const formati = {
    IN_PROGRAMMAZIONE: 'In programmazione', CONFERMATA: 'Confermata',
    RIFIUTATA: 'Rifiutata', ANNULLATA: 'Annullata',
    IN_ATTESA: 'In attesa di risposta', DISPONIBILE: 'Disponibile', NON_DISPONIBILE: 'Non disponibile',
    RITIRATO: 'Contatto ritirato', OPT: 'OPT', OPERATORE_INTERNO: 'Operatore interno',
    OPERATORE_ESTERNO: 'Operatore esterno', ATTIVO: 'Attivo', REVOCATO: 'Revocato',
    IN_ATTESA_DI_ATTIVAZIONE: 'In attesa di attivazione',
    INTERNO_BANCO: 'Interno del Banco', ESTERNO_PAGATO_BANCO: 'Esterno pagato dal Banco',
    TUTTE_LE_OPT: 'Tutte le OPT', GRUPPO_OPT: 'Gruppo di OPT',
    TUTTI_GLI_OPERATORI: 'Tutti gli operatori', GRUPPO_OPERATORI: 'Gruppo di operatori', TUTTI: 'Tutti'
};
const etichetta = (codice) => formati[codice] || codice;
const data = (iso) => iso ? new Date(iso).toLocaleString('it-IT', { dateStyle: 'short', timeStyle: 'short' }) : '';
let misure = [];
let account = [];

function elemento(tag, testo, classe) {
    const el = document.createElement(tag);
    if (testo !== undefined && testo !== null) el.textContent = testo;
    if (classe) el.className = classe;
    return el;
}

function messaggio(box, testo, errore = false) {
    box.textContent = testo;
    box.classList.toggle('picus-errore', errore);
}

function pulsante(testo, azione, classe = 'secondary') {
    const button = elemento('button', testo, 'btn btn-' + classe + ' btn-sm');
    button.type = 'button';
    button.addEventListener('click', async () => {
        button.disabled = true;
        try {
            await azione();
        } catch (err) {
            messaggio(byId('area-esito'), err.message, true);
        } finally {
            button.disabled = false;
        }
    });
    return button;
}

function link(testo, url, classe = 'secondary') {
    const a = elemento('a', testo, 'btn btn-' + classe + ' btn-sm');
    a.href = pagina(url);
    return a;
}

function riga(titolo, dettaglio, azioni = [], options) {
    const el = elemento('article', null, 'picus-list-row');
    const info = elemento('div');
    info.append(elemento('strong', titolo));
    if (dettaglio) info.append(elemento('p', dettaglio, 'picus-meta'));
    el.append(info);
    if (azioni.length) {
        const controlli = elemento('div', null, 'picus-actions');
        controlli.append(...azioni);
        el.append(controlli);
    }
    return options ? listRow(el, options) : el;
}

const filtro = (key, label) => ({ key, label, labels: formati });
const listOptions = {
    account: { searchLabel: 'Cerca account', placeholder: 'Nome, email, comune o codice fiscale',
        filters: [filtro('ruolo', 'Ruolo'), filtro('stato', 'Stato'), filtro('comune', 'Comune')],
        columns: ['Account', 'Email', 'Comune', 'Stato', 'Azioni'] },
    coda: { searchLabel: 'Cerca richieste', filters: [filtro('stato', 'Stato'), filtro('comune', 'Comune')] },
    archivio: { searchLabel: 'Cerca nell’archivio', filters: [filtro('stato', 'Esito'), filtro('comune', 'Comune')] },
    assegnazioni: { searchLabel: 'Cerca incarichi', filters: [filtro('stato', 'Stato'), filtro('comune', 'Comune')] },
    'richieste-mie': { searchLabel: 'Cerca richieste', filters: [filtro('stato', 'Stato'), filtro('comune', 'Comune')] },
    'catalogo-lista': { searchLabel: 'Cerca misure', placeholder: 'Codice, titolo o descrizione',
        filters: [filtro('stato', 'Stato'), filtro('remoto', 'A distanza')] },
    messaggi: { searchLabel: 'Cerca comunicazioni', placeholder: 'Oggetto, testo o nome allegato',
        filters: [filtro('destinatari', 'Destinatari'), filtro('allegati', 'Allegati'), ...(area === 'banco' ? [] : [filtro('ricezione', 'Ricezione')])] },
    'offerta-banco': { searchLabel: 'Cerca nell’offerta', filters: [filtro('erogazione', 'Erogazione')] },
    'offerta-opt': { searchLabel: 'Cerca misure dichiarate', filters: [filtro('erogazione', 'Erogazione')] },
    'conteggi-lista': { searchLabel: 'Cerca negli invii', filters: [filtro('anno', 'Anno'), filtro('trimestre', 'Trimestre')] },
    'schede-mie': { searchLabel: 'Cerca schede inviate', filters: [filtro('anno', 'Anno'), filtro('trimestre', 'Trimestre')] }
};

function mostra(id, righe, vuoto, options = {}) {
    const defaults = id.startsWith('candidati-')
        ? { searchLabel: 'Cerca erogatori', filters: [filtro('tipo', 'Tipo'), filtro('comune', 'Comune')] } : listOptions[id];
    return renderList(byId(id), righe, { ...defaults, ...options, empty: vuoto });
}

function opzioni(select, lista, testo) {
    select.replaceChildren(new Option('Seleziona', ''));
    for (const r of lista) select.add(new Option(testo(r), r.id));
}

function scelteMultiple(gruppo, lista, nome, testo) {
    const contenitore = gruppo.querySelector('.picus-choices');
    const selected = new Set([...contenitore.querySelectorAll('input[name]:checked')].map(input => input.value));
    const scelte = lista.map(voce => {
        const input = elemento('input');
        input.type = 'checkbox';
        input.name = nome;
        input.value = voce.id;
        input.checked = selected.has(String(voce.id));
        const label = elemento('label', null, 'picus-choice');
        const identity = elemento('span', null, 'choice-identity');
        identity.append(elemento('strong', testo(voce)));
        if (nome === 'destinatari') identity.append(elemento('span', [voce.email, voce.comune, etichetta(voce.tipoOperatore)].filter(Boolean).join(' · '), 'picus-meta'));
        label.append(input, identity);
        return listRow(label, { text: [testo(voce), voce.email, voce.comune, etichetta(voce.tipoOperatore)].filter(Boolean).join(' '),
            filters: { comune: voce.comune, tipo: voce.tipoOperatore } });
    });
    renderList(contenitore, scelte, { selectable: true, searchLabel: nome === 'destinatari' ? 'Cerca destinatari' : 'Cerca misure',
        placeholder: nome === 'destinatari' ? 'Nome, email o comune' : 'Codice o titolo',
        filters: nome === 'destinatari' ? [filtro('comune', 'Comune'), filtro('tipo', 'Tipologia')] : [], empty: 'Nessuna voce disponibile.' });
}

function collegaForm(id, azione) {
    const form = byId(id);
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const button = form.querySelector('button[type="submit"]');
        const esito = form.querySelector('.form-submit [role="status"]');
        messaggio(esito, '');
        const gruppoVuoto = Array.from(form.querySelectorAll('.campo-scelte:not([hidden])'))
            .find(gruppo => !gruppo.querySelector('input[name]:checked'));
        if (gruppoVuoto) {
            messaggio(esito, 'Seleziona almeno una voce in «' + gruppoVuoto.querySelector('legend').textContent + '».', true);
            gruppoVuoto.querySelector('input')?.focus();
            return;
        }
        button.disabled = true;
        try {
            const dati = new FormData(form);
            const conferma = await azione(Object.fromEntries(dati), dati, form);
            if (conferma) messaggio(esito, conferma);
        } catch (err) {
            messaggio(esito, err.message, true);
        } finally {
            button.disabled = false;
        }
    });
}

async function caricaMisure() {
    if (area === 'banco') misure = await api('/api/catalogo/misure/gestione');
    else if (area === 'opt') misure = await api('/api/catalogo/misure/storico');
    else return;
    for (const el of document.querySelectorAll('[data-misure]')) {
        scelteMultiple(el, misure.filter(m => m.attiva), 'misure', m => m.codice + ' · ' + m.titolo);
    }
    for (const el of document.querySelectorAll('[data-misure-storiche]')) {
        opzioni(el, misure, m => m.codice + ' · ' + m.titolo + (m.attiva ? '' : ' (storica)'));
    }
    if (area === 'banco') mostraCatalogo();
}

function nomeMisura(id) {
    return misure.find(m => m.id === id)?.titolo || id;
}

function titoloRichiesta(r) {
    return r.titoloMisura || r.misuraId;
}

function datiRichiesta(r) {
    return [etichetta(r.stato), r.comune, data(r.inviataIl), r.id].filter(Boolean).join(' · ');
}

async function caricaAssegnazioni() {
    const incarichi = await api('/api/richieste/assegnazioni');
    mostra('assegnazioni', incarichi.map(r => {
        const azioni = [link('Dettaglio', '/richieste/' + r.id)];
        if (r.esitoContatto === 'IN_ATTESA') azioni.unshift(link('Rispondi', '/richieste/' + r.id + '/rispondi', 'primary'));
        const dettaglio = [etichetta(r.esitoContatto || r.stato), r.comune, r.contattoRemoto && 'A distanza', data(r.inviataIl)]
            .filter(Boolean).join(' · ');
        return riga(titoloRichiesta(r), dettaglio, azioni, { filters: { stato: r.esitoContatto || r.stato, comune: r.comune } });
    }), 'Nessuna richiesta di disponibilità o assegnazione.');
}

async function caricaMieRichieste() {
    const richieste = await api('/api/richieste/mie');
    mostra('richieste-mie', richieste.map(r => riga(titoloRichiesta(r), datiRichiesta(r),
        [link('Dettaglio', '/richieste/' + r.id)], { filters: { stato: r.stato, comune: r.comune } })), 'Nessuna richiesta inviata.');
}

function nomeAccount(id) {
    const a = account.find(u => u.id === id);
    return a ? [a.nome, a.cognome].filter(Boolean).join(' ') : id;
}

async function caricaCoda() {
    const [coda, archivio] = await Promise.all([api('/api/richieste/coda'), api('/api/richieste/archivio')]);
    mostra('coda', coda.map(r => {
        const azioni = [link('Dettaglio', '/richieste/' + r.id)];
        if (r.esitoContatto === 'IN_ATTESA') {
            azioni.push(pulsante('Ritira contatto', async () => {
                if (!confirm('Ritirare questo contatto in attesa?')) return;
                await api('/api/richieste/' + r.id + '/ritira-contatto', { method: 'POST' });
                await caricaCoda();
            }));
        } else {
            if (r.esitoContatto === 'DISPONIBILE') {
                azioni.push(pulsante('Registra accordo', () => apriAccordo(r), 'primary'));
            } else {
                azioni.push(pulsante('Cerca erogatori', () => mostraCandidati(r), 'primary'));
            }
            azioni.push(pulsante('Rifiuta richiesta', async () => {
                if (!confirm('Hai verificato tutte le alternative, incluso il remoto se compatibile?')) return;
                await api('/api/richieste/' + r.id + '/rifiuta', { method: 'POST' });
                await caricaCoda();
            }));
        }
        const esito = r.esitoContatto ? ' · ' + nomeAccount(r.erogatoreContattatoId) + ' · ' + etichetta(r.esitoContatto) : '';
        const scheda = riga(titoloRichiesta(r), datiRichiesta(r) + esito, azioni);
        const contenitore = elemento('div', null, 'picus-list');
        contenitore.id = 'candidati-' + r.id;
        if (r.storicoContatti.length) {
            const storico = elemento('div', null, 'contact-history');
            storico.append(elemento('h4', 'Storico dei contatti'));
            for (const c of r.storicoContatti) {
                storico.append(elemento('p', nomeAccount(c.erogatoreId) + ' · ' + etichetta(c.esito), 'picus-meta'));
            }
            scheda.firstElementChild.append(storico);
        }
        const gruppo = elemento('div', null, 'picus-list');
        gruppo.append(scheda, contenitore);
        return listRow(gruppo, { filters: { stato: r.esitoContatto || r.stato, comune: r.comune } });
    }), 'La coda è vuota.');
    mostra('archivio', archivio.map(r => riga(titoloRichiesta(r), datiRichiesta(r), [
        link('Dettaglio', '/richieste/' + r.id)
    ], { filters: { stato: r.stato, comune: r.comune } })), 'Nessun esito registrato.');
}

async function mostraCandidati(r) {
    const lista = await api('/api/richieste/' + r.id + '/candidati');
    mostra('candidati-' + r.id, lista.map(c => {
        const rifiutato = r.storicoContatti.some(s => s.erogatoreId === c.id && s.remoto === c.remoto && s.esito === 'NON_DISPONIBILE');
        const fuoriZona = !(c.stessoComune || c.limitrofo || c.remoto);
        const azioni = rifiutato || fuoriZona ? [] : [pulsante('Interpella', async () => {
            await api('/api/richieste/' + r.id + '/contatta', { method: 'POST', body: { erogatoreId: c.id } });
            await caricaCoda();
        }, 'primary')];
        const tipo = c.remoto ? 'Centro PICUS · a distanza' : etichetta(c.tipo);
        const dettaglio = [tipo, c.comune, 'Carico: ' + c.caricoAttuale,
            rifiutato ? 'Non disponibile' : '', fuoriZona ? 'Fuori zona' : ''].filter(Boolean).join(' · ');
        return riga(c.nome, dettaglio, azioni, { filters: { tipo: etichetta(c.tipo), comune: c.comune } });
    }), 'Nessun erogatore compatibile.');
}

function aggiornaModalita(form) {
    const remoto = form.elements.modalita.value === 'A_DISTANZA';
    for (const name of ['luogo', 'nomeReferente', 'cognomeReferente', 'telefonoReferente', 'link']) {
        const input = form.elements[name];
        const attivo = name === 'link' ? remoto : !remoto;
        input.closest('label').hidden = !attivo;
        input.required = attivo;
        input.disabled = !attivo;
    }
}

function apriAccordo(r) {
    const form = byId('form-accordo');
    form.reset();
    messaggio(form.querySelector('[role="status"]'), '');
    const erogatore = account.find(u => u.id === r.erogatoreContattatoId);
    if (!erogatore) throw new Error('Erogatore non disponibile');
    form.dataset.richiesta = r.id;
    form.dataset.erogatore = erogatore.id;
    form.dataset.tipo = erogatore.ruolo === 'OPT' ? 'OPT' :
        erogatore.tipoOperatore === 'INTERNO_BANCO' ? 'OPERATORE_INTERNO' : 'OPERATORE_ESTERNO';
    form.elements.modalita.options[0].disabled = r.contattoRemoto;
    form.elements.modalita.options[1].disabled = !misure.find(m => m.id === r.misuraId)?.remoto;
    form.elements.modalita.value = r.contattoRemoto ? 'A_DISTANZA' : 'IN_PRESENZA';
    byId('accordo-erogatore').textContent = nomeAccount(erogatore.id);
    aggiornaModalita(form);
    byId('accordo').showModal();
}

async function caricaAccount() {
    account = await api('/api/utenze');
    account.sort((a, b) => nomeAccount(a.id).localeCompare(nomeAccount(b.id), 'it'));
    mostra('account', account.map(a => {
        const revocabile = a.stato !== 'REVOCATO' && a.ruolo !== 'BANCO';
        const azioni = revocabile ? [pulsante('Revoca', async () => {
            if (!confirm('Revocare l’accesso di ' + nomeAccount(a.id) + '?')) return;
            await api('/api/utenze/' + a.id + '/revoca', { method: 'POST' });
            await caricaAccount();
        }, 'danger')] : [];
        const row = elemento('article', null, 'account-row');
        const identity = elemento('div', null, 'account-identity');
        identity.append(elemento('strong', nomeAccount(a.id)), elemento('p', [a.ruolo, etichetta(a.tipoOperatore)].filter(Boolean).join(' · '), 'picus-meta'));
        row.append(identity);
        for (const [label, value] of [['Email', a.email], ['Comune', a.comune || '—'], ['Stato', etichetta(a.stato)]]) {
            const cell = elemento('div', null, 'account-cell');
            cell.append(elemento('span', label, 'cell-label'), elemento('span', value, label === 'Stato' ? 'status-label' : ''));
            row.append(cell);
        }
        const actions = elemento('div', null, 'picus-actions');
        actions.append(...azioni);
        row.append(actions);
        return listRow(row, { text: [nomeAccount(a.id), a.email, a.comune, a.codiceStruttura, a.telefono, a.codiceFiscale, etichetta(a.tipoOperatore)].join(' '),
            filters: { ruolo: a.ruolo, stato: a.stato, comune: a.comune } });
    }), 'Nessuna utenza registrata.');
    opzioni(byId('consulta-offerta').elements.optId, account.filter(a => a.ruolo === 'OPT'), a => nomeAccount(a.id));
    aggiornaDestinatari();
}

function mostraCatalogo() {
    mostra('catalogo-lista', misure.map(m => {
        const form = elemento('form', null, 'picus-form catalog-editor');
        form.id = 'editor-misura-' + m.id;
        form.hidden = true;
        form.setAttribute('aria-label', 'Modifica ' + m.codice);
        const campi = elemento('div', null, 'picus-fields');
        for (const [name, label, value, type] of [
            ['titolo', 'Titolo', m.titolo, 'text'], ['descrizione', 'Descrizione', m.descrizione, 'text'],
            ['remoto', 'Compatibile con il remoto', m.remoto, 'checkbox'], ['attiva', 'Attiva', m.attiva, 'checkbox']
        ]) {
            const campo = elemento('label', label, type === 'checkbox' ? 'picus-check' :
                'campo' + (name === 'descrizione' ? ' campo-esteso' : ''));
            const input = elemento(name === 'descrizione' ? 'textarea' : 'input', null,
                type === 'checkbox' ? '' : 'form-control');
            if (name === 'descrizione') input.rows = 3;
            else input.type = type;
            input.name = name;
            if (type === 'checkbox') input.checked = input.defaultChecked = Boolean(value);
            else input.value = input.defaultValue = value || '';
            input.required = name === 'titolo';
            campo.append(input);
            campi.append(campo);
        }
        const salva = elemento('button', 'Salva modifiche', 'btn btn-primary btn-sm');
        salva.type = 'submit';
        const esito = elemento('p', null, 'picus-esito');
        esito.setAttribute('role', 'status');
        const modifica = pulsante('Modifica', () => {
            const open = form.hidden;
            for (const editor of document.querySelectorAll('.catalog-editor')) editor.hidden = true;
            for (const button of document.querySelectorAll('[data-misura-edit]')) button.setAttribute('aria-expanded', 'false');
            form.hidden = !open;
            modifica.setAttribute('aria-expanded', String(open));
            if (open) form.elements.titolo.focus();
        });
        modifica.dataset.misuraEdit = m.id;
        modifica.setAttribute('aria-controls', form.id);
        modifica.setAttribute('aria-expanded', 'false');
        const azioni = elemento('div', null, 'picus-actions');
        azioni.append(salva, pulsante('Annulla', () => {
            form.reset();
            messaggio(esito, '');
            form.hidden = true;
            modifica.setAttribute('aria-expanded', 'false');
            modifica.focus();
        }));
        form.append(campi, azioni, esito);
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            salva.disabled = true;
            try {
                const body = Object.fromEntries(new FormData(form));
                body.attiva = form.elements.attiva.checked;
                body.remoto = form.elements.remoto.checked;
                await api('/api/catalogo/misure/' + m.id, { method: 'PUT', body });
                await caricaMisure();
                document.querySelector('[data-misura-edit="' + m.id + '"]')?.focus();
                messaggio(byId('area-esito'), 'Misura aggiornata.');
            } catch (err) {
                messaggio(esito, err.message, true);
                salva.disabled = false;
            }
        });
        const record = elemento('article', null, 'record');
        record.append(riga(m.codice + ' · ' + m.titolo,
            [m.attiva ? 'Attiva' : 'Disattivata', m.remoto ? 'Disponibile anche a distanza' : 'In presenza', m.descrizione].filter(Boolean).join(' · '),
            [modifica]), form);
        return listRow(record, { text: [m.codice, m.titolo, m.descrizione].join(' '),
            filters: { stato: m.attiva ? 'Attiva' : 'Disattivata', remoto: m.remoto ? 'Sì' : 'No' } });
    }), 'Il catalogo è vuoto.');
}

async function mostraOfferte(id, contenitore, modificabile) {
    const lista = await api('/api/catalogo/offerte/' + id);
    mostra(contenitore, lista.map(o => riga(nomeMisura(o.misuraId),
        o.erogaTramiteProfessionista ? 'Erogazione tramite professionista' : 'Erogazione diretta',
        modificabile ? [pulsante('Ritira', async () => {
            if (!confirm('Ritirare questa misura dall’offerta?')) return;
            await api('/api/catalogo/offerte/' + o.misuraId, { method: 'DELETE' });
            await mostraOfferte(utente, contenitore, true);
        })] : [], { filters: { erogazione: o.erogaTramiteProfessionista ? 'Tramite professionista' : 'Diretta' } }
    )), 'Nessuna offerta dichiarata.');
}

function aggiornaDestinatari() {
    const form = byId('comunicazione');
    const tipo = form.elements.destinatariTipo.value;
    const gruppo = form.querySelector('[data-destinatari]');
    gruppo.hidden = gruppo.disabled = !tipo.startsWith('GRUPPO_');
    const ruolo = tipo === 'GRUPPO_OPT' ? 'OPT' : 'OPERATORE';
    scelteMultiple(gruppo, account.filter(a => a.ruolo === ruolo && a.stato === 'ATTIVO'), 'destinatari', a => nomeAccount(a.id));
}

async function allega(file) {
    if (file.size > 5 * 1024 * 1024) throw new Error('Gli allegati possono pesare al massimo 5 MB ciascuno');
    const contenuto = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onerror = reject;
        reader.onload = () => resolve(reader.result.split(',')[1]);
        reader.readAsDataURL(file);
    });
    const tipi = { pdf: 'application/pdf', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', txt: 'text/plain' };
    return (await api('/api/comunicazioni/allegati', { method: 'POST', body: {
        nomeFile: file.name, tipoMime: tipi[file.name.split('.').pop().toLowerCase()], base64Contenuto: contenuto
    } })).id;
}

async function caricaMessaggi() {
    const messages = await api('/api/comunicazioni');
    communications(byId('messaggi'), messages, { banco: area === 'banco', formatDate: data, onConfirm: caricaMessaggi });
}

function query(form) {
    return new URLSearchParams(Object.entries(form).filter(([, valore]) => valore !== ''));
}


async function caricaConteggi() {
    const lista = await api('/api/monitoraggio/opt/' + utente);
    mostra('conteggi-lista', lista.map(r => riga(nomeMisura(r.misuraId),
        [r.anno + ' T' + r.trimestre, r.categoria, r.valore].join(' · '), [],
        { filters: { anno: r.anno, trimestre: r.trimestre } })), 'Nessun dato registrato.');
}

async function caricaSchede() {
    const lista = await api('/api/monitoraggio/schede/mie');
    operatorReports(byId('schede-mie'), lista, []);
}

function altraVoce(id, campoVoce) {
    const r = elemento('div', null, 'count-row');
    for (const [name, label, type] of [[campoVoce, 'Voce', 'text'], ['valore', 'Conteggio', 'number']]) {
        const campo = elemento('label', null, 'campo');
        campo.append(elemento('span', label));
        const input = elemento('input', null, 'form-control');
        input.type = type;
        input.name = name;
        input.setAttribute('aria-label', label);
        input.required = true;
        if (type === 'number') {
            input.min = '0';
            input.step = '1';
        } else {
            input.maxLength = 250;
        }
        campo.append(input);
        r.append(campo);
    }
    const contenitore = byId(id);
    if (contenitore.children.length) r.append(pulsante('Rimuovi voce', () => r.remove()));
    contenitore.append(r);
}

byId('logout').addEventListener('click', async () => {
    try {
        await esci();
    } catch (err) {
        messaggio(byId('area-esito'), err.message, true);
    }
});

for (const input of document.querySelectorAll('[data-anno]')) input.value = new Date().getFullYear();

const sezioni = Array.from(document.querySelectorAll('.area-nav a'), link => ({
    link, pannello: byId(link.hash.slice(1))
}));

function selezionaSezione(hash) {
    const attiva = sezioni.find(sezione => sezione.link.hash === hash) || sezioni[0];
    for (const sezione of sezioni) {
        sezione.pannello.hidden = sezione !== attiva;
        if (sezione === attiva) sezione.link.setAttribute('aria-current', 'location');
        else sezione.link.removeAttribute('aria-current');
    }
    return attiva.pannello;
}

selezionaSezione(location.hash);
window.addEventListener('hashchange', () => selezionaSezione(location.hash));
document.querySelector('.skip-link').addEventListener('click', event => {
    event.preventDefault();
    byId('contenuto').focus();
});
for (const { link } of sezioni) {
    link.addEventListener('click', event => {
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        if (link.hash !== location.hash) history.pushState(null, '', link.hash);
        const pannello = selezionaSezione(link.hash);
        pannello.focus({ preventScroll: true });
        if (pannello.getBoundingClientRect().top < 0) window.scrollTo(0, 0);
    });
}

setupWorkspace();

async function preparaArea() {
    if (area === 'banco') {
        const accordo = byId('form-accordo');
        accordo.elements.modalita.addEventListener('change', () => aggiornaModalita(accordo));
        byId('accordo').querySelector('[data-chiudi]').addEventListener('click', () => byId('accordo').close());
        collegaForm('form-accordo', async (dati, _, form) => {
            dati.dataOra = new Date(dati.dataOra).toISOString();
            dati.assegnatarioId = form.dataset.erogatore;
            dati.tipoAssegnatario = form.dataset.tipo;
            await api('/api/richieste/' + form.dataset.richiesta + '/conferma', { method: 'POST', body: dati });
            byId('accordo').close();
            await caricaCoda();
            messaggio(byId('area-esito'), 'Accordo confermato.');
        });
        collegaForm('nuova-opt', async (body, _, form) => {
            await api('/api/utenze/opt', { method: 'POST', body });
            form.reset();
            await caricaAccount();
            return 'Utenza creata. Link di attivazione emesso.';
        });
        collegaForm('nuovo-operatore', async (body, dati, form) => {
            body.misure = dati.getAll('misure');
            body.centroPicus = form.elements.centroPicus.checked;
            await api('/api/utenze/operatori', { method: 'POST', body });
            form.reset();
            await caricaAccount();
            return 'Operatore creato. Link di attivazione emesso.';
        });
        collegaForm('csv', async ({ csv }) => {
            const report = await api('/api/utenze/importa-csv', { method: 'POST', contentType: 'text/plain; charset=UTF-8', body: await csv.text() });
            const esiti = Object.values(report).find(Array.isArray) || [];
            mostra('rapporto-csv', esiti.map(t => elemento('p', t)), 'Nessuna segnalazione.');
            await caricaAccount();
            return ['Importate: ' + report.importate, 'Aggiornate: ' + report.aggiornate, 'Scartate: ' + report.scartate].join(' · ');
        });
        collegaForm('nuova-misura', async (body, _, form) => {
            body.remoto = form.elements.remoto.checked;
            await api('/api/catalogo/misure', { method: 'POST', body });
            form.reset();
            await caricaMisure();
            return 'Misura creata e selezionabile.';
        });
        collegaForm('consulta-offerta', async ({ optId }) => mostraOfferte(optId, 'offerta-banco', false));
        byId('comunicazione').elements.destinatariTipo.addEventListener('change', aggiornaDestinatari);
        collegaForm('comunicazione', async (body, dati, form) => {
            delete body.files;
            body.destinatari = dati.getAll('destinatari');
            body.allegati = await Promise.all(Array.from(form.elements.files.files, allega));
            await api('/api/comunicazioni', { method: 'POST', body });
            form.reset();
            aggiornaDestinatari();
            await caricaMessaggi();
            return 'Comunicazione inviata.';
        });
        collegaForm('riepilogo-schede', async (dati) => {
            const { schede } = await api('/api/monitoraggio/schede/riepilogo?' + query(dati));
            operatorReports(byId('riepilogo-schede-lista'), schede, account);
        });
        collegaForm('riepilogo-opt', async (dati) => {
            const { indicatore, dati: righe } = await api('/api/monitoraggio/opt/riepilogo?' + query(dati));
            optReports(byId('riepilogo-opt-lista'), righe, account);
            return indicatore + '. Le voci senza invio sono assenti.';
        });
        aggiornaDestinatari();
        await Promise.all([caricaAccount(), caricaMisure(), caricaMessaggi()]);
        await caricaCoda();
    } else if (area === 'opt') {
        altraVoce('voci-conteggi', 'categoria');
        byId('altra-voce-opt').addEventListener('click', () => altraVoce('voci-conteggi', 'categoria'));
        collegaForm('dichiara-offerta', async (_, dati, form) => {
            await api('/api/catalogo/offerte', { method: 'POST',
                body: { misure: dati.getAll('misure'), erogaTramiteProfessionista: form.elements.professionista.checked } });
            await mostraOfferte(utente, 'offerta-opt', true);
            return 'Offerta aggiornata.';
        });
        collegaForm('conteggi', async (dati, formData) => {
            const valori = formData.getAll('valore');
            const body = formData.getAll('categoria').map((categoria, i) => ({
                anno: Number(dati.anno), trimestre: Number(dati.trimestre), misuraId: dati.misuraId,
                categoria, valore: Number(valori[i])
            }));
            await api('/api/monitoraggio/opt', { method: 'POST', body });
            await caricaConteggi();
            return 'Conteggi salvati.';
        });
        await Promise.all([caricaMieRichieste(), caricaAssegnazioni(), caricaMessaggi(), caricaMisure()]);
        await Promise.all([mostraOfferte(utente, 'offerta-opt', true), caricaConteggi()]);
    } else {
        altraVoce('voci-scheda', 'voce');
        byId('altra-voce').addEventListener('click', () => altraVoce('voci-scheda', 'voce'));
        collegaForm('scheda', async (body, dati) => {
            for (const key of ['anno', 'trimestre', 'ore', 'costoOrario']) body[key] = Number(body[key]);
            if (!body.dataNascita) delete body.dataNascita;
            delete body.voce;
            delete body.valore;
            const voci = dati.getAll('voce').map(v => v.trim());
            if (new Set(voci.map(v => v.toLowerCase())).size !== voci.length) throw new Error('Una voce è ripetuta');
            body.aggregati = Object.fromEntries(voci.map((voce, i) => [voce, Number(dati.getAll('valore')[i])]));
            await api('/api/monitoraggio/schede', { method: 'POST', body });
            await caricaSchede();
            return 'Scheda trimestrale inviata.';
        });
        await Promise.all([caricaAssegnazioni(), caricaMessaggi(), caricaSchede()]);
    }
}

function preparaAnteprima() {
    const utenti = byId('account');
    if (utenti) {
        mostra('account', Array.from(utenti.children), 'Nessun account attivo.', {
            placeholder: 'Nome visualizzato',
            filters: [filtro('ruolo', 'Ruolo'), filtro('stato', 'Stato')]
        });
    }
    if (area === 'opt') altraVoce('voci-conteggi', 'categoria');
    if (area === 'operatore') altraVoce('voci-scheda', 'voce');

    for (const lista of document.querySelectorAll('.picus-list[data-funzione-inattiva]')) {
        mostra(lista.id, [], '');
        for (const controllo of lista.querySelectorAll('input, select, button')) {
            controllo.disabled = true;
        }
    }
}

if (document.body.hasAttribute('data-anteprima-dashboard')) {
    preparaAnteprima();
} else {
    preparaArea().catch(err => messaggio(byId('area-esito'), err.message, true));
}
