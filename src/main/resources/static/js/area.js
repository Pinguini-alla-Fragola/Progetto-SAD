import { esci, api } from './area-client.js';
import { setupWorkspace } from './workspace.js';
import { renderList } from './list-view.js';
import { caricaRichieste, caricaAvvisi } from './richieste-area.js';
import { segnalaFunzioniInattive } from './funzioni-inattive.js';
import { initConteggiOpt } from './conteggi-opt.js';
import { initImportazioneOpt } from './importazione-opt.js';
import { initOfferteOpt, aggiornaOptOfferteBanco } from './offerte-opt.js';
import { initSchedeOperatore } from './schede-operatore.js';

const OPZIONI_ACCOUNT = {
    searchLabel: 'Cerca account',
    placeholder: 'Nome, codice o email',
    filters: [{ key: 'ruolo', label: 'Ruolo' }, { key: 'stato', label: 'Stato' }],
    columns: ['Account', 'Email', 'Comune', 'Stato', 'Azioni'],
    empty: 'Nessun account registrato.'
};

/**
 * Collega la rubrica Account del Banco mantenendo allineato lo stato delle revoche e dei reinvii
 * anche quando un refresh HTML sostituisce i nodi della lista o termina con ritardo.
 */
export function collegaElencoAccountBanco(
    utenti,
    {
        esitoEl = null,
        conferma = messaggio => window.confirm(messaggio),
        caricaHtmlDashboard = async () => {
            const res = await fetch('/banco/dashboard', { headers: { Accept: 'text/html' } });
            if (!res.ok || res.redirected) {
                throw new Error('Impossibile aggiornare l’elenco account');
            }
            return res.text();
        },
        aggiornaOfferte = () => aggiornaOptOfferteBanco()
    } = {}
) {
    if (!utenti) return async () => {};

    let righeUtenti = Array.from(utenti.children);
    let progressivoRefresh = 0;
    const revocheInCorso = new Set();
    const revocheConfermate = new Set();
    const reinviiInCorso = new Set();

    const trovaRigaPerId = id => righeUtenti.find(r => r.dataset?.id === id) ?? null;

    const sincronizzaRiga = riga => {
        const id = riga?.dataset?.id;
        if (!id) return;
        const btnRevoca = riga.querySelector('button[data-revoca-opt], button[data-revoca-operatore]');
        const btnInvito = riga.querySelector('button[data-reinvia-invito]');

        if (riga.dataset.stato === 'REVOCATO' || revocheConfermate.has(id)) {
            riga.dataset.stato = 'REVOCATO';
            const statoLabel = riga.querySelector('.status-label');
            if (statoLabel) statoLabel.textContent = 'Revocato';
            if (btnInvito) btnInvito.remove();
            if (btnRevoca) {
                btnRevoca.textContent = 'Revocato';
                btnRevoca.disabled = true;
            }
            return;
        }
        if (btnRevoca) {
            btnRevoca.disabled = revocheInCorso.has(id);
        }
        if (btnInvito) {
            btnInvito.disabled = revocheInCorso.has(id) || reinviiInCorso.has(id);
        }
    };

    const aggiornaLista = () => {
        for (const riga of righeUtenti) sincronizzaRiga(riga);
        renderList(utenti, righeUtenti, OPZIONI_ACCOUNT);
    };

    const aggiornaAccountBanco = async () => {
        const richiesta = ++progressivoRefresh;
        const html = await caricaHtmlDashboard();
        if (richiesta !== progressivoRefresh) return;
        const parsed = new DOMParser().parseFromString(html, 'text/html');
        const nuovoElenco = parsed.getElementById('account');
        if (!nuovoElenco) return;
        righeUtenti = Array.from(nuovoElenco.children).map(riga => document.importNode(riga, true));
        aggiornaLista();
        await Promise.resolve(aggiornaOfferte?.()).catch(() => {});
    };

    utenti.addEventListener('click', async event => {
        const btnReinvia = event.target.closest('button[data-reinvia-invito]');
        if (btnReinvia && !btnReinvia.disabled) {
            const riga = btnReinvia.closest('.account-row');
            const id = riga?.dataset?.id;
            const nome = riga?.dataset?.nome || 'questa organizzazione';
            const email = riga?.dataset?.email || '';
            if (!id || reinviiInCorso.has(id) || revocheInCorso.has(id) || revocheConfermate.has(id)) return;

            reinviiInCorso.add(id);
            sincronizzaRiga(trovaRigaPerId(id) ?? riga);
            if (esitoEl) esitoEl.textContent = '';
            try {
                const risorsa = riga.dataset.ruolo === 'OPERATORE' ? 'operatori' : 'opt';
                const risultato = await api(`/api/${risorsa}/${encodeURIComponent(id)}/reinvia-invito`, { method: 'POST' });
                if (esitoEl) esitoEl.textContent = risultato?.messaggio || `Invito reinviato a ${email || nome}.`;
            } catch (causa) {
                if (esitoEl) {
                    esitoEl.textContent = causa?.messaggioServer || causa?.message || 'Impossibile reinviare l\'invito. Riprova.';
                }
            } finally {
                reinviiInCorso.delete(id);
                const corrente = trovaRigaPerId(id);
                if (corrente) sincronizzaRiga(corrente);
            }
            return;
        }

        const button = event.target.closest('button[data-revoca-opt], button[data-revoca-operatore]');
        if (!button || button.disabled) return;
        const isOperatore = button.hasAttribute('data-revoca-operatore');
        const riga = button.closest('.account-row');
        const id = riga?.dataset?.id;
        if (!id || revocheInCorso.has(id) || revocheConfermate.has(id)) return;

        const nome = riga.dataset.nome || (isOperatore ? 'questo operatore' : 'questa organizzazione');
        const riferimento = riga.dataset.riferimento;
        const email = riga.dataset.email;
        const dettagli = [riferimento, email].filter(Boolean).join(' · ');
        const etichettaUnivoca = dettagli ? `${nome} (${dettagli})` : nome;

        const messaggioConferma = isOperatore
            ? `Confermi la revoca dell'accesso per l'operatore "${etichettaUnivoca}"?\nL'operatore non potrà più accedere, ma le schede già inviate resteranno consultabili.`
            : `Confermi la revoca dell'accesso per "${etichettaUnivoca}"?\nL'organizzazione non potrà più accedere, ma i dati già registrati resteranno consultabili.`;

        if (!conferma(messaggioConferma)) {
            return;
        }

        const endpoint = isOperatore
            ? `/api/operatori/${encodeURIComponent(id)}/revoca`
            : `/api/opt/${encodeURIComponent(id)}/revoca`;

        revocheInCorso.add(id);
        sincronizzaRiga(trovaRigaPerId(id) ?? riga);
        if (esitoEl) esitoEl.textContent = '';
        try {
            const risultato = await api(endpoint, { method: 'POST' });
            revocheInCorso.delete(id);
            revocheConfermate.add(id);
            aggiornaLista();
            if (esitoEl) esitoEl.textContent = risultato?.messaggio || `Accesso revocato per ${etichettaUnivoca}.`;
            await Promise.resolve(aggiornaOfferte?.()).catch(() => {});
        } catch (causa) {
            revocheInCorso.delete(id);
            const corrente = trovaRigaPerId(id);
            if (corrente) sincronizzaRiga(corrente);
            if (esitoEl) esitoEl.textContent = causa?.message || 'Impossibile completare la revoca. Riprova.';
        }
    });

    aggiornaLista();
    return aggiornaAccountBanco;
}

if (typeof document !== 'undefined' && document.getElementById('logout')) {
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
    const aggiornaAccountBanco = utenti
        ? collegaElencoAccountBanco(utenti, { esitoEl: byId('area-esito') })
        : async () => {};

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
                await aggiornaAccountBanco().catch(() => {});
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
    if (area === 'banco') {
        lavori.push(
            initConteggiOpt(area),
            caricaRichieste(area),
            import('./catalogo-banco.js').then(({ initCatalogoBanco }) => initCatalogoBanco()),
            initImportazioneOpt({ onMutazioneConfermata: () => aggiornaAccountBanco() }),
            initOfferteOpt('banco')
        );
    }
    else {
        lavori.push(caricaAvvisi());
        if (area === 'opt') lavori.push(initConteggiOpt(area), caricaRichieste(area), initOfferteOpt('opt'));
        if (area === 'operatore') lavori.push(initSchedeOperatore());
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
}
