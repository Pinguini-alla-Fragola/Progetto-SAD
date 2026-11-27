import { api, ErroreHttp } from './area-client.js';
import { renderList, listRow } from './list-view.js';

const DIMENSIONE_MASSIMA_CSV = 1024 * 1024;

const ETICHETTE_ESITO = {
    CREATA: 'Creata',
    AGGIORNATA: 'Aggiornata',
    INVARIATA: 'Invariata',
    SCARTATA: 'Scartata'
};

const CLASSI_ESITO = {
    CREATA: 'badge-confermata',
    AGGIORNATA: 'badge-presa_in_carico',
    INVARIATA: 'badge-annullata',
    SCARTATA: 'badge-rifiutata'
};

function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = String(text);
    if (className) node.className = className;
    return node;
}

function impostaErroreCampo(input, messaggio) {
    const contenitore = input?.closest?.('label') ?? input?.parentElement;
    if (!input || !contenitore) return;
    input.setAttribute('aria-invalid', 'true');
    const err = el('span', messaggio, 'picus-errore');
    err.setAttribute('role', 'alert');
    contenitore.append(err);
    input.focus?.();
}

function pulisciErroriForm(form) {
    for (const err of form.querySelectorAll('.picus-errore:not(.picus-esito)')) err.remove();
    for (const inv of form.querySelectorAll('[aria-invalid]')) inv.removeAttribute('aria-invalid');
}

function mostraEsito(esitoEl, testo, { errore = false, mostraLogin = false } = {}) {
    if (!esitoEl) return;
    esitoEl.classList.toggle('picus-errore', errore);
    esitoEl.replaceChildren();
    if (testo) esitoEl.append(document.createTextNode(testo));
    if (mostraLogin) {
        const link = el('a', 'Accedi di nuovo', 'btn btn-secondary btn-sm');
        link.href = '/login';
        esitoEl.append(' ', link);
    }
}

function renderReport(rapportoEl, righe) {
    if (!rapportoEl) return;
    const rows = (Array.isArray(righe) ? righe : []).map(voce => {
        const riga = el('article', null, 'account-row');
        const numero = voce?.numeroRiga ?? '—';
        const esitoCode = String(voce?.esito || 'SCARTATA');
        const motivi = Array.isArray(voce?.motivi) && voce.motivi.length > 0
            ? voce.motivi.join(' · ')
            : 'Nessuna segnalazione';

        const cellaRiga = el('div', null, 'account-cell');
        cellaRiga.append(el('span', 'Riga', 'cell-label'), el('strong', `Riga ${numero}`));

        const cellaEsito = el('div', null, 'account-cell');
        cellaEsito.append(
            el('span', 'Esito', 'cell-label'),
            el('span', ETICHETTE_ESITO[esitoCode] || esitoCode, 'badge-stato ' + (CLASSI_ESITO[esitoCode] || ''))
        );

        const cellaMotivi = el('div', null, 'account-cell');
        cellaMotivi.append(el('span', 'Motivi e segnalazioni', 'cell-label'), el('span', motivi));

        riga.append(cellaRiga, cellaEsito, cellaMotivi);
        return listRow(riga, {
            text: `Riga ${numero} ${ETICHETTE_ESITO[esitoCode] || esitoCode} ${motivi}`,
            filters: { esito: esitoCode },
            data: voce
        });
    });

    rapportoEl.hidden = false;
    renderList(rapportoEl, rows, {
        searchLabel: 'Cerca nel report CSV',
        placeholder: 'Numero riga, esito o motivo',
        filters: [{ key: 'esito', label: 'Esito', labels: ETICHETTE_ESITO }],
        columns: ['Riga', 'Esito', 'Motivi e segnalazioni'],
        empty: 'Il file CSV non contiene righe dati da importare.'
    });
}

function riepilogoConteggi(righe) {
    if (!Array.isArray(righe) || righe.length === 0) {
        return 'File elaborato: nessuna riga dati presente.';
    }
    const totali = { CREATA: 0, AGGIORNATA: 0, INVARIATA: 0, SCARTATA: 0 };
    for (const voce of righe) {
        const chiave = String(voce?.esito || '');
        if (Object.hasOwn(totali, chiave)) totali[chiave]++;
    }
    return `Importazione completata su ${righe.length} ${righe.length === 1 ? 'riga' : 'righe'}: `
        + `${totali.CREATA} create, ${totali.AGGIORNATA} aggiornate, `
        + `${totali.INVARIATA} invariate, ${totali.SCARTATA} scartate.`;
}

/** Collega il modulo Banco di importazione CSV delle organizzazioni partner (#200). */
export function initImportazioneOpt({ onMutazioneConfermata } = {}) {
    const form = document.getElementById('csv');
    const rapportoEl = document.getElementById('rapporto-csv');
    if (!form || !rapportoEl) return;
    if (form.dataset?.importazioneAttiva === 'true') return;
    if (form.dataset) form.dataset.importazioneAttiva = 'true';

    const fieldset = form.querySelector('fieldset.campi-form');
    const fileInput = form.querySelector('input[type="file"][name="file"]') || form.querySelector('input[type="file"]');
    const submitBtn = form.querySelector('button[type="submit"]');
    const esitoEl = form.querySelector('.picus-esito');

    const skeleton = typeof document !== 'undefined' && document.body?.dataset?.skeleton === 'true';
    if (skeleton) {
        if (fieldset) fieldset.disabled = true;
        mostraEsito(esitoEl, 'Importazione CSV non disponibile nel profilo skeleton.');
        return;
    }
    if (fieldset) fieldset.disabled = false;

    let invioInCorso = false;

    fileInput?.addEventListener('change', () => {
        pulisciErroriForm(form);
        mostraEsito(esitoEl, '');
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (invioInCorso) return;

        pulisciErroriForm(form);
        mostraEsito(esitoEl, '');

        const file = fileInput?.files?.[0];
        if (!file) {
            const msg = 'Seleziona un file CSV UTF-8 prima di avviare l’importazione.';
            mostraEsito(esitoEl, msg, { errore: true });
            impostaErroreCampo(fileInput, msg);
            return;
        }
        if (file.size > DIMENSIONE_MASSIMA_CSV) {
            const msg = 'Il file CSV può pesare al massimo 1 MB. Nessuna riga è stata importata.';
            mostraEsito(esitoEl, msg, { errore: true });
            impostaErroreCampo(fileInput, msg);
            return;
        }

        const corpo = new FormData();
        corpo.append('file', file, file.name);

        invioInCorso = true;
        if (submitBtn) submitBtn.disabled = true;
        if (fileInput) fileInput.disabled = true;
        mostraEsito(esitoEl, 'Importazione in corso…');

        let risultato = null;
        try {
            risultato = await api('/api/opt/importazioni', {
                method: 'POST',
                body: corpo
            });
        } catch (causa) {
            if (causa instanceof ErroreHttp) {
                const dettaglio = causa.messaggioServer || causa.message;
                if (causa.stato === 400 || causa.stato === 413) {
                    const msg = `${dettaglio} Nessuna riga è stata importata.`;
                    mostraEsito(esitoEl, msg, { errore: true });
                    impostaErroreCampo(fileInput, msg);
                    rapportoEl.hidden = true;
                    rapportoEl.replaceChildren();
                } else if (causa.stato === 401 || causa.stato === 403) {
                    mostraEsito(esitoEl, causa.message, { errore: true, mostraLogin: causa.stato === 401 });
                } else {
                    mostraEsito(
                        esitoEl,
                        'Risposta non confermata dal server durante l’importazione: l’esito è incerto. Verifica l’elenco Account prima di ripetere il caricamento.',
                        { errore: true }
                    );
                    await Promise.resolve(onMutazioneConfermata?.()).catch(() => {});
                }
            } else {
                mostraEsito(
                    esitoEl,
                    'Connessione interrotta durante l’importazione: l’esito è incerto. Verifica l’elenco Account prima di ripetere il caricamento.',
                    { errore: true }
                );
                await Promise.resolve(onMutazioneConfermata?.()).catch(() => {});
            }
            return;
        } finally {
            invioInCorso = false;
            if (submitBtn) submitBtn.disabled = false;
            if (fileInput) fileInput.disabled = false;
        }

        const righe = Array.isArray(risultato?.righe) ? risultato.righe : [];
        mostraEsito(esitoEl, riepilogoConteggi(righe));
        renderReport(rapportoEl, righe);
        form.reset();

        const haMutazioni = righe.some(voce => voce?.esito === 'CREATA' || voce?.esito === 'AGGIORNATA');
        if (haMutazioni) {
            try {
                await onMutazioneConfermata?.();
            } catch {
                mostraEsito(
                    esitoEl,
                    `${riepilogoConteggi(righe)} Elenco Account non aggiornato automaticamente: ricarica la pagina se necessario.`
                );
            }
        }
    });
}
