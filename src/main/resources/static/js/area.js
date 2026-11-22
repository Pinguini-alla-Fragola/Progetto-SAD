import { esci, api } from './area-client.js';
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
if (utenti) {
    const righeUtenti = Array.from(utenti.children);
    const opzioniUtenti = {
        searchLabel: 'Cerca account', placeholder: 'Nome, codice o email',
        filters: [{ key: 'ruolo', label: 'Ruolo' }, { key: 'stato', label: 'Stato' }],
        columns: ['Account', 'Email', 'Comune', 'Stato', 'Azioni'], empty: 'Nessun account registrato.'
    };

    utenti.addEventListener('click', async (event) => {
        const btnReinvia = event.target.closest('button[data-reinvia-invito]');
        if (btnReinvia && !btnReinvia.disabled) {
            const riga = btnReinvia.closest('.account-row');
            const id = riga?.dataset.id;
            const nome = riga?.dataset.nome || 'questa organizzazione';
            const email = riga?.dataset.email || '';
            if (!id) return;

            const esito = byId('area-esito');
            btnReinvia.disabled = true;
            esito.textContent = '';
            try {
                const risorsa = riga.dataset.ruolo === 'OPERATORE' ? 'operatori' : 'opt';
                const risultato = await api(`/api/${risorsa}/${encodeURIComponent(id)}/reinvia-invito`, { method: 'POST' });
                esito.textContent = risultato?.messaggio || `Invito reinviato a ${email || nome}.`;
            } catch (causa) {
                esito.textContent = causa?.message || 'Impossibile reinviare l\'invito. Riprova.';
            } finally {
                btnReinvia.disabled = false;
            }
            return;
        }

        const button = event.target.closest('button[data-revoca-opt], button[data-revoca-operatore]');
        if (!button || button.disabled) return;
        const isOperatore = button.hasAttribute('data-revoca-operatore');
        const riga = button.closest('.account-row');
        const id = riga?.dataset.id;
        const nome = riga?.dataset.nome || (isOperatore ? 'questo operatore' : 'questa organizzazione');
        const riferimento = riga?.dataset.riferimento;
        const email = riga?.dataset.email;
        const dettagli = [riferimento, email].filter(Boolean).join(' · ');
        const etichettaUnivoca = dettagli ? `${nome} (${dettagli})` : nome;
        if (!id) return;

        const messaggioConferma = isOperatore
            ? `Confermi la revoca dell'accesso per l'operatore "${etichettaUnivoca}"?\nL'operatore non potrà più accedere, ma le schede già inviate resteranno consultabili.`
            : `Confermi la revoca dell'accesso per "${etichettaUnivoca}"?\nL'organizzazione non potrà più accedere, ma i dati già registrati resteranno consultabili.`;

        if (!window.confirm(messaggioConferma)) {
            return;
        }

        const endpoint = isOperatore
            ? `/api/operatori/${encodeURIComponent(id)}/revoca`
            : `/api/opt/${encodeURIComponent(id)}/revoca`;

        const esito = byId('area-esito');
        button.disabled = true;
        esito.textContent = '';
        try {
            const risultato = await api(endpoint, { method: 'POST' });
            riga.dataset.stato = 'REVOCATO';
            const statoLabel = riga.querySelector('.status-label');
            if (statoLabel) statoLabel.textContent = 'Revocato';
            const btnInvito = riga.querySelector('button[data-reinvia-invito]');
            if (btnInvito) btnInvito.remove();
            button.textContent = 'Revocato';
            renderList(utenti, righeUtenti, opzioniUtenti);
            esito.textContent = risultato?.messaggio || `Accesso revocato per ${etichettaUnivoca}.`;
        } catch (causa) {
            button.disabled = false;
            esito.textContent = causa?.message || 'Impossibile completare la revoca. Riprova.';
        }
    });

    renderList(utenti, righeUtenti, opzioniUtenti);
}

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

const formNuovaOpt = byId('nuova-opt');
if (formNuovaOpt) {
    formNuovaOpt.addEventListener('submit', async (event) => {
        event.preventDefault();
        const button = formNuovaOpt.querySelector('button[type="submit"]');
        const esito = formNuovaOpt.querySelector('.picus-esito');
        for (const input of formNuovaOpt.querySelectorAll('.form-control')) {
            input.removeAttribute('aria-invalid');
        }
        const dati = Object.fromEntries(new FormData(formNuovaOpt));
        button.disabled = true;
        esito.textContent = '';
        try {
            const risultato = await api('/api/opt', { method: 'POST', body: dati });
            esito.textContent = risultato.messaggio || 'Organizzazione partner registrata.';
            if (!risultato.erroreInvio) {
                formNuovaOpt.reset();
            }
        } catch (causa) {
            esito.textContent = causa?.message || 'Connessione non disponibile. Riprova.';
            const campiErrati = causa?.campi ? Object.keys(causa.campi) : [];
            if (campiErrati.length > 0) {
                const inputErrato = formNuovaOpt.querySelector(`[name="${campiErrati[0]}"]`);
                if (inputErrato) {
                    inputErrato.setAttribute('aria-invalid', 'true');
                    inputErrato.focus();
                }
            }
        } finally {
            button.disabled = false;
        }
    });
}

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