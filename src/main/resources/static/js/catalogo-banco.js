import { api, ErroreHttp } from './area-client.js';
import { renderList, listRow } from './list-view.js';
import { aggiornaScelteMisureOperatore } from './operatori.js';
import { aggiornaCatalogoOfferteBanco } from './offerte-opt.js';

const MAPPA_CAMPI_NUOVA = {
    codice: 'codice',
    nome: 'titolo',
    descrizione: 'descrizione',
    erogabileADistanza: 'remoto'
};

function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = String(text);
    if (className) node.className = className;
    return node;
}

function messaggioErrore(causa, predefinito) {
    if (causa instanceof ErroreHttp) {
        return causa.stato === 409 && causa.messaggioServer ? causa.messaggioServer : causa.message;
    }
    return predefinito;
}

function pulisciErroriForm(form) {
    for (const err of form.querySelectorAll('.picus-errore:not(.picus-esito)')) err.remove();
    for (const inv of form.querySelectorAll('[aria-invalid]')) inv.removeAttribute('aria-invalid');
}

function mostraErroriForm(form, campi, mappaCampi = null) {
    let primo = null;
    for (const [campoServer, testo] of Object.entries(campi || {})) {
        const nomeControllo = mappaCampi ? (mappaCampi[campoServer] || campoServer) : campoServer;
        const controllo = form.elements?.[nomeControllo] ?? form.querySelector(`[name="${nomeControllo}"]`);
        const msg = el('span', testo, 'picus-errore');
        msg.setAttribute('role', 'alert');
        if (controllo) {
            controllo.setAttribute('aria-invalid', 'true');
            (controllo.closest?.('label') ?? controllo.parentElement ?? form).append(msg);
            if (!primo) primo = controllo;
        } else {
            form.querySelector('.form-submit')?.append(msg);
        }
    }
    primo?.focus?.();
}

function creaCampoInput(etichetta, nome, valore, tipo = 'text', esteso = false) {
    const label = el('label', etichetta, esteso ? 'campo campo-esteso' : 'campo');
    let control;
    if (tipo === 'textarea') {
        control = document.createElement('textarea');
        control.rows = 3;
    } else {
        control = document.createElement('input');
        control.type = tipo;
    }
    control.className = 'form-control';
    control.name = nome;
    control.value = valore ?? '';
    label.append(control);
    return { label, control };
}

function creaCheckbox(etichetta, nome, checked) {
    const label = el('label', null, 'picus-check');
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.name = nome;
    input.checked = Boolean(checked);
    label.append(input, ' ' + etichetta);
    return { label, input };
}

function confermaDisattivazione(codice) {
    const messaggio = `Disattivare la misura ${codice}? Non sarà più selezionabile per nuove richieste o nuovi operatori.`;
    return typeof globalThis.confirm === 'function' ? globalThis.confirm(messaggio) : true;
}

export function initCatalogoBanco() {
    const lista = document.getElementById('catalogo-lista');
    const esitoLista = document.getElementById('catalogo-esito');
    const btnAggiorna = document.getElementById('catalogo-aggiorna');
    const formNuova = document.getElementById('nuova-misura');
    if (!lista || !esitoLista || !formNuova) return;

    const esitoNuova = document.getElementById('nuova-misura-esito') || formNuova.querySelector('.picus-esito');
    let misureCorrenti = [];
    let progressivoLista = 0;
    let modificaApertaId = null;
    const bozzeModifica = new Map();

    const isFocusabile = nodo => Boolean(nodo && !nodo.hidden && !nodo.disabled && !nodo.closest?.('[hidden]'));

    const catturaTargetFocus = () => {
        const attivo = document.activeElement;
        if (!attivo || !lista.contains(attivo)) return null;
        if (attivo.id === 'catalogo-lista-search') return { search: true };
        const riga = attivo.closest?.('[data-misura-id]');
        if (!riga) return null;
        const misuraId = Number(riga.dataset.misuraId);
        if (attivo.dataset?.azione) return { misuraId, azione: attivo.dataset.azione };
        if (attivo.name) return { misuraId, campo: attivo.name };
        return { misuraId, azione: 'modifica' };
    };

    const ripristinaTargetFocus = target => {
        if (!target) return;
        let destinazione = null;
        if (target.search) {
            destinazione = document.getElementById('catalogo-lista-search');
        } else if (target.misuraId != null) {
            const righe = lista.querySelectorAll('[data-misura-id]');
            const riga = Array.from(righe).find(r => String(r.dataset?.misuraId) === String(target.misuraId));
            if (riga && !riga.hidden && !riga.closest?.('[hidden]')) {
                if (target.campo) {
                    destinazione = riga.querySelector(`[name="${target.campo}"]`);
                } else if (target.azione) {
                    destinazione = riga.querySelector(`[data-azione="${target.azione}"]`);
                }
            }
        }
        if (!isFocusabile(destinazione)) {
            const ricerca = document.getElementById('catalogo-lista-search');
            destinazione = isFocusabile(ricerca) ? ricerca : btnAggiorna;
        }
        if (isFocusabile(destinazione)) destinazione.focus({ preventScroll: true });
    };

    const focusInternoOPage = () => {
        const attivo = document.activeElement;
        return !attivo || attivo === document.body || lista.contains(attivo);
    };

    const mostraEsitoLista = (testo, { errore = false, mostraLogin = false, azioneRiprova = null } = {}) => {
        esitoLista.classList.toggle('picus-errore', errore);
        esitoLista.replaceChildren();
        if (testo) esitoLista.append(document.createTextNode(testo));
        if (azioneRiprova) {
            const btn = el('button', 'Riprova', 'btn btn-secondary btn-sm');
            btn.type = 'button';
            btn.addEventListener('click', azioneRiprova);
            esitoLista.append(' ', btn);
        }
        if (mostraLogin) {
            const link = el('a', 'Accedi di nuovo', 'btn btn-secondary btn-sm');
            link.href = '/login';
            esitoLista.append(' ', link);
        }
    };

    const salvaBozza = (id, form) => {
        bozzeModifica.set(id, {
            codice: form.elements.codice.value,
            nome: form.elements.nome.value,
            descrizione: form.elements.descrizione.value,
            erogabileADistanza: form.elements.erogabileADistanza.checked,
            attiva: form.elements.attiva.checked
        });
    };

    const costruisciRiga = misura => {
        const nodo = el('article', null, 'record');
        nodo.dataset.misuraId = String(misura.id);

        const heading = el('div', null, 'record-heading');
        const identita = el('div');
        const titolo = el('strong', `${misura.dati.codice} · ${misura.dati.nome}`);
        const badge = el(
            'span',
            misura.attiva ? 'Attiva' : 'Disattivata',
            'badge-stato ' + (misura.attiva ? 'badge-confermata' : 'badge-annullata')
        );
        const testata = el('div', null, 'availability-heading');
        testata.append(titolo, badge);

        const dettagli = [
            misura.dati.descrizione || 'Nessuna descrizione',
            misura.dati.erogabileADistanza ? 'Disponibile anche a distanza' : 'Solo in presenza',
            `ID ${misura.id}`
        ];
        identita.append(testata, el('p', dettagli.join(' · '), 'picus-meta'));

        const azioni = el('div', null, 'picus-actions');
        const btnModifica = el('button', modificaApertaId === misura.id ? 'Chiudi modifica' : 'Modifica', 'btn btn-secondary btn-sm');
        btnModifica.type = 'button';
        btnModifica.dataset.azione = 'modifica';
        btnModifica.setAttribute('aria-expanded', String(modificaApertaId === misura.id));

        const editor = el('form', null, 'catalog-editor picus-form');
        editor.noValidate = true;
        editor.hidden = modificaApertaId !== misura.id;

        const bozza = bozzeModifica.get(misura.id) || {
            codice: misura.dati.codice,
            nome: misura.dati.nome,
            descrizione: misura.dati.descrizione ?? '',
            erogabileADistanza: Boolean(misura.dati.erogabileADistanza),
            attiva: Boolean(misura.attiva)
        };
        const campiGrid = el('div', null, 'picus-fields fields-two');
        const fCodice = creaCampoInput('Codice misura', 'codice', bozza.codice);
        const fNome = creaCampoInput('Titolo', 'nome', bozza.nome);
        const fDesc = creaCampoInput('Descrizione', 'descrizione', bozza.descrizione, 'textarea', true);
        const fRemoto = creaCheckbox('Compatibile con il remoto', 'erogabileADistanza', bozza.erogabileADistanza);
        const fAttiva = creaCheckbox('Misura attiva per nuove selezioni', 'attiva', bozza.attiva);
        campiGrid.append(fCodice.label, fNome.label, fDesc.label, fRemoto.label, fAttiva.label);

        const editorAzioni = el('div', null, 'picus-actions');
        const btnSalva = el('button', 'Salva modifiche', 'btn btn-primary btn-sm');
        btnSalva.type = 'submit';
        btnSalva.dataset.azione = 'salva';
        const btnAnnulla = el('button', 'Annulla', 'btn btn-secondary btn-sm');
        btnAnnulla.type = 'button';
        btnAnnulla.dataset.azione = 'annulla';
        const esitoEditor = el('p', '', 'picus-esito');
        esitoEditor.setAttribute('role', 'status');
        editorAzioni.append(btnSalva, btnAnnulla, esitoEditor);
        editor.append(campiGrid, editorAzioni);

        editor.addEventListener('input', () => salvaBozza(misura.id, editor));
        editor.addEventListener('change', () => salvaBozza(misura.id, editor));

        btnModifica.addEventListener('click', () => {
            const apri = editor.hidden;
            modificaApertaId = apri ? misura.id : null;
            if (!apri) bozzeModifica.delete(misura.id);
            renderMisure(apri ? { misuraId: misura.id, campo: 'codice' } : { misuraId: misura.id, azione: 'modifica' });
        });

        btnAnnulla.addEventListener('click', () => {
            modificaApertaId = null;
            bozzeModifica.delete(misura.id);
            renderMisure({ misuraId: misura.id, azione: 'modifica' });
        });

        let salvataggioInCorso = false;
        editor.addEventListener('submit', async event => {
            event.preventDefault();
            if (salvataggioInCorso) return;

            const payload = {
                codice: editor.elements.codice.value,
                nome: editor.elements.nome.value,
                descrizione: editor.elements.descrizione.value,
                erogabileADistanza: Boolean(editor.elements.erogabileADistanza.checked),
                attiva: Boolean(editor.elements.attiva.checked)
            };
            if (misura.attiva && !payload.attiva && !confermaDisattivazione(misura.dati.codice)) {
                return;
            }

            salvataggioInCorso = true;
            btnSalva.disabled = true;
            btnAnnulla.disabled = true;
            pulisciErroriForm(editor);
            esitoEditor.classList.remove('picus-errore');
            esitoEditor.textContent = 'Salvataggio in corso…';

            try {
                const aggiornata = await api(`/api/catalogo/misure/${encodeURIComponent(misura.id)}`, {
                    method: 'PUT',
                    body: payload
                });
                bozzeModifica.delete(misura.id);
                modificaApertaId = null;
                applicaAggiornamentoLocale(aggiornata, focusInternoOPage() ? { misuraId: aggiornata.id, azione: 'modifica' } : null);
                await aggiornaDopoSalvataggio(`Misura ${aggiornata.dati.codice} aggiornata (ID ${aggiornata.id}).`);
            } catch (causa) {
                salvaBozza(misura.id, editor);
                esitoEditor.classList.add('picus-errore');
                esitoEditor.textContent = messaggioErrore(causa, 'Impossibile aggiornare la misura. Riprova.');
                mostraErroriForm(editor, causa?.campi);
            } finally {
                salvataggioInCorso = false;
                btnSalva.disabled = false;
                btnAnnulla.disabled = false;
            }
        });

        azioni.append(btnModifica);
        if (misura.attiva) {
            const btnDisattiva = el('button', 'Disattiva', 'btn btn-danger btn-sm');
            btnDisattiva.type = 'button';
            btnDisattiva.dataset.azione = 'disattiva';
            let disattivazioneInCorso = false;
            btnDisattiva.addEventListener('click', async () => {
                if (disattivazioneInCorso) return;
                if (!confermaDisattivazione(misura.dati.codice)) return;
                disattivazioneInCorso = true;
                btnDisattiva.disabled = true;
                btnModifica.disabled = true;
                mostraEsitoLista(`Disattivazione di ${misura.dati.codice} in corso…`);
                try {
                    const disattivata = await api(`/api/catalogo/misure/${encodeURIComponent(misura.id)}/disattivazione`, {
                        method: 'POST'
                    });
                    if (modificaApertaId === misura.id) {
                        bozzeModifica.delete(misura.id);
                        modificaApertaId = null;
                    }
                    applicaAggiornamentoLocale(disattivata, focusInternoOPage() ? { misuraId: disattivata.id, azione: 'modifica' } : null);
                    await aggiornaDopoSalvataggio(`Misura ${disattivata.dati.codice} disattivata per le nuove selezioni.`);
                } catch (causa) {
                    mostraEsitoLista(messaggioErrore(causa, 'Impossibile disattivare la misura. Riprova.'), {
                        errore: true,
                        mostraLogin: causa?.stato === 401
                    });
                } finally {
                    disattivazioneInCorso = false;
                    btnDisattiva.disabled = false;
                    btnModifica.disabled = false;
                }
            });
            azioni.append(btnDisattiva);
        }

        heading.append(identita, azioni);
        nodo.append(heading, editor);

        const statoEtichetta = misura.attiva ? 'Attiva' : 'Disattivata';
        return listRow(nodo, {
            text: [misura.dati.codice, misura.dati.nome, misura.dati.descrizione, statoEtichetta, String(misura.id)].join(' '),
            filters: { stato: statoEtichetta },
            data: misura
        });
    };

    const renderMisure = (targetFocus = catturaTargetFocus()) => {
        const ordinate = [...misureCorrenti].sort((a, b) =>
            String(a.dati.codice).localeCompare(String(b.dati.codice), 'it', { sensitivity: 'base' })
            || a.id - b.id
        );
        renderList(lista, ordinate.map(costruisciRiga), {
            searchLabel: 'Cerca misura',
            placeholder: 'Codice, nome o descrizione',
            filters: [{ key: 'stato', label: 'Stato' }],
            empty: 'Nessuna misura presente in catalogo.'
        });
        ripristinaTargetFocus(targetFocus);
    };

    const applicaAggiornamentoLocale = (misura, targetFocus = undefined) => {
        const idx = misureCorrenti.findIndex(m => m.id === misura.id);
        if (idx >= 0) misureCorrenti[idx] = misura;
        else misureCorrenti.push(misura);
        renderMisure(targetFocus);
    };

    const caricaMisure = async ({ messaggioSuccesso = '' } = {}) => {
        const richiesta = ++progressivoLista;
        lista.setAttribute('aria-busy', 'true');
        if (btnAggiorna) btnAggiorna.disabled = true;
        if (!misureCorrenti.length) {
            mostraEsitoLista('Caricamento del catalogo in corso…');
        }
        try {
            const dati = await api('/api/catalogo/misure/tutte');
            if (richiesta !== progressivoLista) return true;
            misureCorrenti = Array.isArray(dati) ? dati : [];
            aggiornaCatalogoOfferteBanco(misureCorrenti);
            renderMisure();
            mostraEsitoLista(messaggioSuccesso);
            return true;
        } catch (causa) {
            if (richiesta !== progressivoLista) return false;
            const errore = messaggioErrore(causa, 'Connessione interrotta durante il caricamento del catalogo.');
            mostraEsitoLista(errore, {
                errore: true,
                mostraLogin: causa?.stato === 401,
                azioneRiprova: () => caricaMisure()
            });
            return false;
        } finally {
            if (richiesta === progressivoLista) {
                lista.setAttribute('aria-busy', 'false');
                if (btnAggiorna) btnAggiorna.disabled = false;
            }
        }
    };

    const aggiornaDopoSalvataggio = async messaggioSalvataggio => {
        aggiornaScelteMisureOperatore(misureCorrenti);
        aggiornaCatalogoOfferteBanco(misureCorrenti);
        const ok = await caricaMisure({ messaggioSuccesso: messaggioSalvataggio });
        if (!ok) {
            mostraEsitoLista(
                `${messaggioSalvataggio} Aggiornamento completo dell’elenco non riuscito: usa Riprova per sincronizzare.`,
                {
                    errore: true,
                    azioneRiprova: () => caricaMisure()
                }
            );
        } else {
            aggiornaScelteMisureOperatore(misureCorrenti);
            aggiornaCatalogoOfferteBanco(misureCorrenti);
        }
    };

    btnAggiorna?.addEventListener('click', () => caricaMisure());

    let creazioneInCorso = false;
    formNuova.addEventListener('submit', async event => {
        event.preventDefault();
        if (creazioneInCorso) return;
        creazioneInCorso = true;
        const submitBtn = formNuova.querySelector('button[type="submit"]');
        if (submitBtn) submitBtn.disabled = true;
        pulisciErroriForm(formNuova);
        if (esitoNuova) {
            esitoNuova.classList.remove('picus-errore');
            esitoNuova.textContent = 'Salvataggio misura in corso…';
        }

        const payload = {
            codice: formNuova.elements.codice.value,
            nome: formNuova.elements.titolo.value,
            descrizione: formNuova.elements.descrizione.value,
            erogabileADistanza: Boolean(formNuova.elements.remoto.checked)
        };

        try {
            const creata = await api('/api/catalogo/misure', {
                method: 'POST',
                body: payload
            });
            formNuova.reset();
            applicaAggiornamentoLocale(creata);
            const msg = `Misura ${creata.dati.codice} · ${creata.dati.nome} creata (ID ${creata.id}).`;
            if (esitoNuova) esitoNuova.textContent = msg;
            await aggiornaDopoSalvataggio(msg);
        } catch (causa) {
            if (esitoNuova) {
                esitoNuova.classList.add('picus-errore');
                esitoNuova.textContent = messaggioErrore(causa, 'Impossibile salvare la misura. Riprova.');
            }
            mostraErroriForm(formNuova, causa?.campi, MAPPA_CAMPI_NUOVA);
        } finally {
            creazioneInCorso = false;
            if (submitBtn) submitBtn.disabled = false;
        }
    });

    return caricaMisure();
}
