import { api } from './area-client.js';
import { renderList, listRow } from './list-view.js';
import { confermaPresaInCarico } from './conferma.js';

const etichette = {
    CREATA: 'Creata', PRESA_IN_CARICO: 'Presa in carico', IN_PROGRAMMAZIONE: 'In programmazione',
    CONFERMATA: 'Confermata', RIFIUTATA: 'Rifiutata', ANNULLATA: 'Annullata',
    IN_ATTESA: 'In attesa di risposta', DISPONIBILE: 'Disponibile', NON_DISPONIBILE: 'Non disponibile',
    STESSO_COMUNE: 'Stesso comune', COMUNE_LIMITROFO: 'Comune limitrofo',
    CENTRO_PICUS_A_DISTANZA: 'Centro PICUS a distanza',
    OPT: 'OPT', INTERNO_BANCO: 'Operatore interno del Banco', ESTERNO_PAGATO_BANCO: 'Operatore esterno del Banco'
};
export const etichetta = valore => etichette[valore] || valore;
const data = valore => new Date(valore).toLocaleString('it-IT', { dateStyle: 'short', timeStyle: 'short' });
export function elemento(tag, testo, classe) {
    const nodo = document.createElement(tag);
    if (testo != null) nodo.textContent = testo;
    if (classe) nodo.className = classe;
    return nodo;
}
export function link(testo, href) {
    const nodo = elemento('a', testo, 'btn btn-secondary btn-sm');
    nodo.href = href;
    return nodo;
}
export function pulsante(testo, azione, esito, conferma) {
    const nodo = elemento('button', testo, 'btn btn-primary btn-sm');
    nodo.type = 'button';
    nodo.addEventListener('click', async () => {
        nodo.disabled = true;
        let annullata = false;
        try {
            if (conferma && !await conferma()) { annullata = true; return; }
            esito.textContent = 'Operazione in corso…';
            esito.classList.remove('picus-errore');
            await azione();
        } catch (causa) {
            esito.textContent = causa.stato ? causa.message : 'Connessione interrotta. Aggiorna la vista per verificare l’esito.';
            esito.classList.add('picus-errore');
        } finally {
            nodo.disabled = false;
            if (annullata) nodo.focus();
        }
    });
    return nodo;
}
export function riga(titolo, dettagli, azioni, filtri = {}) {
    const nodo = elemento('article', null, 'picus-list-row');
    const testo = elemento('div');
    testo.append(elemento('strong', titolo), elemento('p', dettagli, 'picus-meta'));
    const controlli = elemento('div', null, 'picus-actions');
    controlli.append(...azioni);
    nodo.append(testo, controlli);
    return listRow(nodo, { filters: filtri });
}
async function caricaLista(id, carica, vuoto, filtri = [], opzioni = {}) {
    const box = document.getElementById(id);
    if (!box) return;
    box.setAttribute('aria-busy', 'true');
    const caricamento = elemento('p', 'Caricamento in corso…', 'picus-meta');
    caricamento.setAttribute('role', 'status');
    box.prepend(caricamento);
    try {
        renderList(box, await carica(), {
            empty: vuoto, searchLabel: 'Cerca nell’elenco',
            filters: filtri.map(key => ({ key, label: key === 'stato' ? 'Stato' : 'Ambito', labels: etichette })), ...opzioni
        });
    } catch (causa) {
        const messaggio = elemento('p', causa.stato ? causa.message : 'Connessione interrotta. Riprova a caricare l’elenco.', 'picus-errore');
        messaggio.setAttribute('role', 'alert');
        const retry = elemento('button', 'Riprova', 'btn btn-secondary btn-sm');
        retry.type = 'button';
        retry.addEventListener('click', () => caricaLista(id, carica, vuoto, filtri, opzioni));
        box.replaceChildren(messaggio, retry);
        if (causa.stato === 401) box.append(link('Accedi di nuovo', '/login'));
    } finally {
        caricamento.remove();
        box.setAttribute('aria-busy', 'false');
    }
}
export async function caricaRichieste(area) {
    const id = area === 'banco' ? 'coda' : 'richieste-mie';
    return caricaLista(id, async () => {
        const dati = await api(area === 'banco' ? '/banco/richieste/coda' : '/api/richieste/mie');
        const richieste = area === 'banco' ? dati.map(r => ({ ...r.richiesta, optRichiedente: r.optRichiedente, comune: r.comune })) : dati;
        return richieste.map(r => {
            const azioni = [link('Dettaglio', '/richieste/' + r.id)];
            if (area === 'banco') {
                if (r.stato === 'CREATA') azioni.push(pulsante('Prendi in carico', async () => {
                    await api('/api/richieste/' + r.id + '/presa-in-carico', { method: 'POST' });
                    await caricaRichieste(area);
                    document.getElementById('area-esito').textContent = 'Richiesta presa in carico.';
                }, document.getElementById('area-esito'), () => confermaPresaInCarico(r.nomeMisura, r.optRichiedente)));
            }
            return rigaRichiesta(r, azioni, area === 'banco');
        });
    }, area === 'banco' ? 'La coda è vuota. Le nuove richieste delle OPT compariranno qui.'
        : 'Non hai ancora inviato richieste. Seleziona «Nuova richiesta» per iniziare.', ['stato'], {
            searchLabel: area === 'banco' ? 'Cerca misura o OPT' : 'Cerca una richiesta',
            placeholder: area === 'banco' ? 'Misura, organizzazione o comune' : 'Misura o stato',
            columns: area === 'banco' ? ['Misura', 'OPT richiedente', 'Stato', 'Invio', 'Azioni'] : [],
            sort: {
                initial: 'recenti', options: [{ value: 'recenti', label: 'Più recenti' }, { value: 'meno-recenti', label: 'Meno recenti' }],
                compare: (a, b, ordine) => (new Date(a.inviataIl) - new Date(b.inviataIl)) * (ordine === 'recenti' ? -1 : 1)
            }
        });
}

function rigaRichiesta(r, azioni, banco) {
    const nodo = elemento('article', null, 'request-row' + (banco ? '' : ' request-row-own'));
    nodo.dataset.richiestaId = r.id;
    const campo = (label, valore, classe) => {
        const box = elemento('div', null, classe);
        box.append(elemento('span', label, 'request-field-label'), valore);
        return box;
    };
    const misura = elemento('div');
    misura.append(elemento('strong', r.nomeMisura));
    if (r.comune) misura.append(elemento('span', r.comune, 'picus-meta'));
    nodo.append(campo('Misura', misura, 'request-measure'));
    if (banco) nodo.append(campo('OPT richiedente', elemento('strong', r.optRichiedente), 'request-organization'));
    const stato = elemento('span', etichetta(r.stato), 'badge-stato badge-' + r.stato.toLowerCase());
    nodo.append(campo('Stato', stato, 'request-status'));
    const orario = elemento('time');
    orario.dateTime = r.inviataIl;
    orario.append(elemento('span', new Date(r.inviataIl).toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'Europe/Rome' })),
        elemento('span', 'ore ' + new Date(r.inviataIl).toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Rome' }), 'picus-meta'));
    nodo.append(campo('Data e ora', orario, 'request-date'));
    const controlli = elemento('div', null, 'picus-actions request-actions');
    controlli.append(...azioni);
    nodo.append(controlli);
    return listRow(nodo, { filters: { stato: r.stato }, data: r });
}
export async function caricaAvvisi() {
    return caricaLista('assegnazioni', async () => {
        const avvisi = await api('/api/avvisi');
        const risultati = await Promise.allSettled(avvisi.map(a => api('/api/contatti-disponibilita/' + a.contattoId)));
        return avvisi.map((avviso, index) => {
            const risultato = risultati[index];
            if (risultato.status === 'rejected') {
                return riga('Contatto non disponibile', risultato.reason.message,
                    [link('Apri contatto', '/richieste/contatti/' + avviso.contattoId)]);
            }
            const dettaglio = risultato.value;
            const contatto = dettaglio.contatto;
            return riga(dettaglio.nomeMisura,
                [etichetta(contatto.esito), dettaglio.comuneCodice, etichetta(contatto.ambito), data(avviso.registratoIl), contatto.richiestaId].join(' · '),
                [link(contatto.esito === 'IN_ATTESA' ? 'Rispondi' : 'Apri contatto', '/richieste/contatti/' + contatto.id)],
                { stato: contatto.esito, ambito: contatto.ambito });
        });
    }, 'Nessuna richiesta di disponibilità ricevuta.', ['stato', 'ambito']);
}
export async function caricaCatalogo() {
    return caricaLista('catalogo-lista', async () => (await api('/api/catalogo/misure')).map(m =>
        riga(m.dati.codice + ' · ' + m.dati.nome,
            m.dati.descrizione + (m.dati.erogabileADistanza ? ' · Disponibile anche a distanza' : ''), [])),
        'Nessuna misura attiva disponibile.');
}
export async function avviaCoordinamento(pagina) {
    if (pagina.dataset.banco !== 'true') return;
    const id = encodeURIComponent(pagina.dataset.richiestaId);
    const stato = pagina.dataset.stato;
    const azioni = document.getElementById('coordinamento-azioni');
    const esito = document.getElementById('coordinamento-esito');
    if (stato === 'CREATA') {
        azioni.append(pulsante('Prendi in carico', async () => {
            await api('/api/richieste/' + id + '/presa-in-carico', { method: 'POST' });
            location.reload();
        }, esito, () => confermaPresaInCarico(pagina.dataset.misura, pagina.dataset.opt)));
        return;
    }
    if (stato === 'IN_PROGRAMMAZIONE') {
        esito.textContent = 'Erogatore assegnato. Il coordinamento dell’accordo è in corso.';
        return;
    }
    if (stato !== 'PRESA_IN_CARICO') return;
    esito.textContent = 'Caricamento dei contatti…';
    try {
        const contatti = await api('/api/richieste/' + id + '/contatti');
        const ultimi = new Map(contatti.map(c => [c.erogatoreId + '|' + c.ambito, c]));
        const attesa = contatti.find(c => c.esito === 'IN_ATTESA');
        if (attesa) {
            esito.textContent = 'Richiesta di disponibilità inviata. Attendi il riscontro dell’erogatore prima di contattarne un altro.';
        } else {
            esito.textContent = 'Scegli un erogatore compatibile oppure assegna uno dei candidati disponibili.';
            document.getElementById('titolo-candidati').hidden = false;
            await caricaLista('candidati', async () => {
                const candidati = await api('/api/richieste/' + id + '/candidati');
                const localiDaValutare = candidati.some(({ erogatore: e }) => e.ambito !== 'CENTRO_PICUS_A_DISTANZA'
                    && ultimi.get(e.accountId + '|' + e.ambito)?.esito !== 'NON_DISPONIBILE');
                return candidati.map(c => {
                    const e = c.erogatore;
                    const ultimo = ultimi.get(e.accountId + '|' + e.ambito);
                    const precedente = ultimo?.esito === 'DISPONIBILE' ? ultimo : null;
                    const remotoBloccato = e.ambito === 'CENTRO_PICUS_A_DISTANZA' && localiDaValutare;
                    const azione = precedente ? [pulsante('Assegna erogatore', async () => {
                        await api('/api/contatti-disponibilita/' + precedente.id + '/assegnazione', { method: 'POST' });
                        location.reload();
                    }, esito)] : [pulsante('Chiedi disponibilità', async () => {
                        await api('/api/richieste/' + id + '/contatti', {
                            method: 'POST', body: { erogatoreId: e.accountId, ambito: e.ambito }
                        });
                        location.reload();
                    }, esito)];
                    if (remotoBloccato) azione.forEach(button => { button.disabled = true; });
                    return riga(e.nomeVisualizzato,
                        [etichetta(e.tipo), e.comuneCodice, etichetta(e.ambito), 'Appuntamenti confermati: ' + c.caricoAppuntamenti,
                            ultimo ? 'Ultima risposta: ' + etichetta(ultimo.esito) : '',
                            remotoBloccato ? 'Valuta prima le disponibilità locali.' : ''].filter(Boolean).join(' · '),
                        azione, { ambito: e.ambito });
                });
            }, 'Nessun erogatore compatibile al momento.', ['ambito']);
        }
        azioni.append(link('Aggiorna stato', location.pathname));
    } catch (causa) {
        esito.textContent = causa.stato ? causa.message : 'Connessione interrotta. Ricarica per aggiornare i contatti.';
        esito.classList.add('picus-errore');
        azioni.append(link('Riprova', location.pathname));
    }
}
