import { esci } from './area-client.js';
import { setupWorkspace } from './workspace.js';
import { renderList } from './list-view.js';
import { caricaRichieste, caricaAvvisi, caricaCatalogo } from './richieste-area.js';
import { segnalaFunzioniInattive } from './funzioni-inattive.js';

const byId = id => document.getElementById(id);
const area = document.body.dataset.area;

byId('logout').addEventListener('click', async () => {
    try { await esci(); }
    catch (causa) { byId('area-esito').textContent = causa.message; }
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

// Le sezioni future mantengono l'anteprima senza chiamare API non disponibili.
for (const lista of document.querySelectorAll('.picus-list[data-funzione-inattiva]')) {
    renderList(lista, [], { empty: '' });
    for (const controllo of lista.querySelectorAll('input, select, button')) controllo.disabled = true;
}
const utenti = byId('account');
if (utenti) renderList(utenti, Array.from(utenti.children), {
    searchLabel: 'Cerca account', placeholder: 'Nome visualizzato',
    filters: [{ key: 'ruolo', label: 'Ruolo' }, { key: 'stato', label: 'Stato' }],
    columns: ['Account', 'Email', 'Comune', 'Stato', 'Azioni'], empty: 'Nessun account attivo.'
});
for (const contenitore of document.querySelectorAll('#voci-conteggi, #voci-scheda')) {
    const riga = document.createElement('div');
    riga.className = 'count-row';
    for (const [label, type] of [['Voce', 'text'], ['Conteggio', 'number']]) {
        const campo = document.createElement('label');
        campo.className = 'campo';
        const etichetta = document.createElement('span');
        etichetta.textContent = label;
        campo.append(etichetta);
        const input = document.createElement('input');
        input.className = 'form-control';
        input.type = type;
        input.disabled = true;
        campo.append(input);
        riga.append(campo);
    }
    contenitore.append(riga);
}
for (const form of document.querySelectorAll('[data-form-anteprima]')) {
    form.addEventListener('submit', event => event.preventDefault());
}
segnalaFunzioniInattive();
const lavori = [];
if (area === 'banco') lavori.push(caricaRichieste(area), caricaCatalogo());
else {
    lavori.push(caricaAvvisi());
    if (area === 'opt') lavori.push(caricaRichieste(area));
}
Promise.allSettled(lavori);
for (const button of document.querySelectorAll('[data-aggiorna]')) {
    button.addEventListener('click', async () => {
        button.disabled = true;
        try {
            if (button.dataset.aggiorna === 'avvisi') await caricaAvvisi();
            else await caricaRichieste(area);
        } finally { button.disabled = false; }
    });
}
