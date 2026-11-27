import { api, pagina } from './area-client.js';
import { renderList, listRow } from './list-view.js';

const audiences = { TUTTE_LE_OPT: 'Tutte le OPT', GRUPPO_OPT: 'Gruppo di OPT',
    TUTTI_GLI_OPERATORI: 'Tutti gli operatori', GRUPPO_OPERATORI: 'Gruppo di operatori', TUTTI: 'Tutti' };
const formats = { 'application/pdf': 'PDF', 'image/png': 'PNG', 'image/jpeg': 'JPEG', 'text/plain': 'TXT' };

function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = text;
    if (className) node.className = className;
    return node;
}
function button(text) {
    const node = el('button', text, 'btn btn-secondary btn-sm');
    node.type = 'button';
    return node;
}
function size(bytes) {
    if (bytes == null) return '';
    if (bytes < 1024) return bytes + ' B';
    return new Intl.NumberFormat('it-IT', { maximumFractionDigits: 1 }).format(bytes / (bytes < 1048576 ? 1024 : 1048576)) + (bytes < 1048576 ? ' KB' : ' MB');
}

/**
 * Crea le schede delle comunicazioni di una pagina. Quando richiesto, il Banco può aprire lo stato
 * di ricezione e i destinatari possono registrare la propria conferma.
 */
export function communicationCards(messages, { banco, formatDate, onConfirm, conRiscontri = false, conConferma = false }) {
    return messages.map(message => {
        const card = el('article', null, 'message-entry');
        const header = el('header', null, 'message-heading');
        const title = el('h3', message.titolo || 'Comunicazione senza oggetto');
        const time = el('time', formatDate(message.inviataIl), 'message-date');
        time.dateTime = message.inviataIl;
        header.append(title, time);
        const audience = audiences[message.destinatariTipo] || message.destinatariTipo;
        card.append(header, el('p', banco ? 'Destinatari: ' + audience : (message.confermataIl ? 'Ricezione confermata' : 'Da confermare'), 'message-meta'));
        const body = el('p', message.contenuto, 'message-body picus-testo');
        body.id = 'message-text-' + message.id;
        card.append(body);
        if (message.contenuto.length > 320) {
            body.classList.add('message-preview');
            const expand = button('Leggi tutto');
            expand.classList.add('btn-quiet');
            expand.setAttribute('aria-controls', body.id);
            expand.setAttribute('aria-expanded', 'false');
            expand.addEventListener('click', () => {
                const collapsed = body.classList.toggle('message-preview');
                expand.textContent = collapsed ? 'Leggi tutto' : 'Riduci testo';
                expand.setAttribute('aria-expanded', String(!collapsed));
            });
            card.append(expand);
        }
        const metadata = new Map((message.dettagliAllegati || []).map(file => [file.id, file]));
        if (message.allegati.length) {
            const files = el('section', null, 'message-attachments');
            files.setAttribute('aria-label', 'Allegati');
            files.append(el('h4', 'Allegati'));
            const list = el('ul', null, 'attachment-list');
            for (const id of message.allegati) {
                const file = metadata.get(id);
                const item = el('li');
                const link = el('a', null, 'attachment-link');
                const path = '/api/comunicazioni/allegati/' + id;
                link.href = pagina(path);
                if (pagina(path) === path && file) link.download = file.nomeFile;
                link.append(el('span', 'Scarica ', 'sr-only'), el('strong', file?.nomeFile || 'Allegato'));
                const details = [formats[file?.tipoMime], size(file?.dimensione)].filter(Boolean).join(' · ');
                if (details) link.append(el('span', details, 'picus-meta'));
                item.append(link);
                list.append(item);
            }
            files.append(list);
            card.append(files);
        }
        const footer = el('div', null, 'message-actions');
        if (banco && conRiscontri) {
            const toggle = button('Mostra riscontri');
            const panel = el('section', null, 'receipt-panel');
            panel.id = 'receipts-' + message.id;
            panel.hidden = true;
            toggle.setAttribute('aria-controls', panel.id);
            toggle.setAttribute('aria-expanded', 'false');
            const heading = el('div', null, 'receipt-heading');
            const summary = el('p', null, 'picus-meta');
            heading.append(el('h4', 'Stato di ricezione'), summary);
            const status = el('p', null, 'picus-esito');
            status.setAttribute('role', 'status');
            const receipts = el('div');
            panel.append(heading, status, receipts);
            toggle.addEventListener('click', async () => {
                panel.hidden = !panel.hidden;
                toggle.setAttribute('aria-expanded', String(!panel.hidden));
                toggle.textContent = panel.hidden ? 'Mostra riscontri' : 'Nascondi riscontri';
                if (panel.hidden || panel.getAttribute('aria-busy') === 'true') return;
                panel.setAttribute('aria-busy', 'true');
                summary.textContent = '';
                receipts.hidden = true;
                status.classList.remove('picus-errore');
                status.textContent = 'Caricamento riscontri…';
                try {
                    const data = await api('/api/comunicazioni/' + message.id + '/riscontri');
                    const confirmed = data.filter(r => r.confermataIl).length;
                    summary.textContent = `${confirmed} di ${data.length} ricezioni confermate`;
                    const nameCounts = new Map();
                    for (const receipt of data) {
                        const key = receipt.nomeVisualizzato.toLocaleLowerCase('it');
                        nameCounts.set(key, (nameCounts.get(key) ?? 0) + 1);
                    }
                    const items = data.map(receipt => {
                        const confirmedAt = receipt.confermataIl;
                        const state = confirmedAt ? 'Confermata' : 'In attesa di conferma';
                        const row = el('div', null, 'receipt-row');
                        const name = receipt.nomeVisualizzato;
                        const recipient = el('div');
                        recipient.append(el('strong', name));
                        const homonym = nameCounts.get(name.toLocaleLowerCase('it')) > 1;
                        if (homonym) {
                            recipient.append(el('p', 'ID account ' + receipt.destinatarioId, 'picus-meta'));
                        }
                        const detail = el('span', state, 'picus-meta');
                        if (confirmedAt) {
                            const time = el('time', formatDate(confirmedAt));
                            time.dateTime = confirmedAt;
                            detail.append(' · ', time);
                        }
                        row.append(recipient, detail);
                        return listRow(row, {
                            text: homonym ? name + ' ' + receipt.destinatarioId : name,
                            filters: { stato: state }
                        });
                    });
                    renderList(receipts, items, {
                        searchLabel: 'Cerca destinatari', filters: [{ key: 'stato', label: 'Ricezione' }],
                        empty: 'Nessun destinatario.'
                    });
                    receipts.hidden = false;
                    status.textContent = '';
                } catch (error) {
                    status.textContent = error.message + ' Chiudi e riapri i riscontri per riprovare.';
                    status.classList.add('picus-errore');
                } finally {
                    panel.removeAttribute('aria-busy');
                }
            });
            footer.append(toggle);
            card.append(footer, panel);
        } else if (!banco && conConferma && !message.confermataIl) {
            const confirm = button('Confermo la ricezione');
            confirm.classList.replace('btn-secondary', 'btn-primary');
            const status = el('p', null, 'picus-esito');
            status.setAttribute('role', 'status');
            confirm.addEventListener('click', async () => {
                confirm.disabled = true;
                try {
                    await api('/api/comunicazioni/' + message.id + '/ricezione', { method: 'POST' });
                    document.dispatchEvent(new Event('comunicazioni:ricezione-confermata'));
                    await onConfirm();
                } catch (error) {
                    status.textContent = error.message;
                    status.classList.add('picus-errore');
                    confirm.disabled = false;
                }
            });
            footer.append(confirm, status);
            card.append(footer);
        }
        card.setAttribute('role', 'listitem');
        return card;
    });
}
