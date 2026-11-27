import { api, ErroreHttp } from './area-client.js';
import { renderList, listRow } from './list-view.js';
import { leggiRubricaAccount } from './rubrica-banco.js';
import { confermaOperazione } from './conferma.js';

let aggiornamentoCatalogoBanco = null;
let aggiornamentoOptBanco = null;

function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = String(text);
    if (className) node.className = className;
    return node;
}

function mostraEsito(nodo, testo, { errore = false, mostraLogin = false } = {}) {
    if (!nodo) return;
    nodo.classList.toggle('picus-errore', errore);
    nodo.replaceChildren();
    if (testo) nodo.append(document.createTextNode(testo));
    if (mostraLogin) {
        const link = el('a', 'Accedi di nuovo', 'btn btn-secondary btn-sm');
        link.href = '/login';
        nodo.append(' ', link);
    }
}

function pulisciErroriForm(form) {
    for (const err of form.querySelectorAll('.picus-errore:not(.picus-esito)')) err.remove();
    for (const inv of form.querySelectorAll('[aria-invalid]')) inv.removeAttribute('aria-invalid');
}

function mostraErroriForm(form, campi) {
    let primo = null;
    for (const [campo, testo] of Object.entries(campi || {})) {
        const controllo = form.elements?.[campo] ?? form.querySelector(`[name="${campo}"]`);
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

function descrizioneModalita(tramiteProfessionista) {
    return tramiteProfessionista ? 'Tramite professionista esterno' : 'Direttamente dall’organizzazione';
}

function risolviEtichettaMisura(misuraId, mappaMisure) {
    const id = String(misuraId);
    const trovata = mappaMisure.get(id);
    if (trovata) {
        return {
            titolo: `${trovata.dati.codice} · ${trovata.dati.nome}`,
            attiva: Boolean(trovata.attiva)
        };
    }
    return {
        titolo: `Misura #${id} (non attiva nel catalogo)`,
        attiva: false
    };
}

async function risolviMisureSconosciute(offerte, mappaMisure) {
    const mancanti = [...new Set(
        (offerte || [])
            .map(o => String(o.misuraId))
            .filter(id => id && !mappaMisure.has(id))
    )];
    await Promise.allSettled(mancanti.map(async id => {
        const misura = await api(`/api/catalogo/misure/${encodeURIComponent(id)}`);
        if (misura) mappaMisure.set(id, misura);
    }));
}

/** Notifica al pannello Banco l'elenco aggiornato di tutte le misure caricato dal catalogo. */
export function aggiornaCatalogoOfferteBanco(misure) {
    aggiornamentoCatalogoBanco?.(misure);
}

/** Notifica al pannello Banco l'elenco aggiornato delle OPT registrate o importate. */
export function aggiornaOptOfferteBanco(vociRubrica = null) {
    return aggiornamentoOptBanco?.(vociRubrica);
}

/** Collega le viste delle offerte OPT lato organizzazione partner e lato Banco (#167). */
export function initOfferteOpt(area) {
    if (area === 'opt') {
        return initOfferteLatoOpt();
    }
    if (area === 'banco') {
        return initOfferteLatoBanco();
    }
}

function initOfferteLatoOpt() {
    const optId = document.body?.dataset?.utente;
    const listaEl = document.getElementById('offerta-opt');
    const esitoLista = document.getElementById('offerta-opt-esito');
    const btnAggiorna = document.getElementById('offerta-opt-aggiorna');
    const form = document.getElementById('dichiara-offerta');
    if (!listaEl || !form || !optId) return;
    if (form.dataset?.offertaOptAttiva === 'true') return;
    if (form.dataset) form.dataset.offertaOptAttiva = 'true';

    const fieldset = form.querySelector('fieldset.campi-form');
    const selectMisura = form.elements.misuraId;
    const selectMod = form.elements.tramiteProfessionista;
    const submitBtn = form.querySelector('button[type="submit"]');
    const esitoForm = form.querySelector('.picus-esito');

    if (document.body?.dataset?.skeleton === 'true') {
        if (fieldset) fieldset.disabled = true;
        mostraEsito(esitoLista, 'Gestione dell’offerta non disponibile nel profilo skeleton.');
        mostraEsito(esitoForm, 'Gestione dell’offerta non disponibile nel profilo skeleton.');
        return;
    }

    let misureAttive = [];
    const mappaMisure = new Map();
    const mappaOfferte = new Map();
    let progressivo = 0;
    let invioFormInCorso = false;

    const sincronizzaModalitaForm = () => {
        if (!selectMisura || !selectMod) return;
        const corrente = mappaOfferte.get(String(selectMisura.value));
        if (corrente) {
            selectMod.value = String(Boolean(corrente.tramiteProfessionista));
        }
    };

    const popolaSelectMisure = () => {
        if (!selectMisura) return;
        const precedente = String(selectMisura.value || '');
        const attiveOrdinate = [...misureAttive].sort((a, b) =>
            String(a.dati.codice).localeCompare(String(b.dati.codice), 'it', { sensitivity: 'base' })
            || a.id - b.id
        );
        if (attiveOrdinate.length === 0) {
            selectMisura.replaceChildren(new Option('Nessuna misura attiva disponibile in catalogo', ''));
            selectMisura.disabled = true;
            if (submitBtn) submitBtn.disabled = true;
            return;
        }
        const opzioni = attiveOrdinate.map(m => {
            const giaDichiarata = mappaOfferte.has(String(m.id));
            const label = `${m.dati.codice} · ${m.dati.nome}` + (giaDichiarata ? ' (già dichiarata)' : '');
            return new Option(label, String(m.id));
        });
        selectMisura.replaceChildren(...opzioni);
        selectMisura.disabled = false;
        if (precedente && attiveOrdinate.some(m => String(m.id) === precedente)) {
            selectMisura.value = precedente;
        }
        if (submitBtn) submitBtn.disabled = false;
        sincronizzaModalitaForm();
    };

    const costruisciRigaOfferta = offerta => {
        const id = String(offerta.misuraId);
        const info = risolviEtichettaMisura(id, mappaMisure);
        const nodo = el('article', null, 'record');
        nodo.dataset.misuraId = id;

        const heading = el('div', null, 'record-heading');
        const identita = el('div');
        const testata = el('div', null, 'availability-heading');
        testata.append(
            el('strong', info.titolo),
            el(
                'span',
                info.attiva ? 'Attiva' : 'Disattivata',
                'badge-stato ' + (info.attiva ? 'badge-confermata' : 'badge-annullata')
            )
        );
        const descModalita = descrizioneModalita(offerta.tramiteProfessionista);
        const notaDisattivata = info.attiva
            ? ''
            : ' · Misura disattivata: non modificabile, ma ritirabile dall’offerta';
        identita.append(testata, el('p', descModalita + notaDisattivata, 'picus-meta'));

        const azioni = el('div', null, 'picus-actions');
        const esitoRiga = el('p', '', 'picus-esito');
        esitoRiga.setAttribute('role', 'status');

        let occupata = false;
        const toggleAzioni = disabled => {
            occupata = disabled;
            for (const b of azioni.querySelectorAll('button')) b.disabled = disabled;
        };

        if (info.attiva) {
            const prossimoValore = !offerta.tramiteProfessionista;
            const labelBtn = prossimoValore
                ? 'Passa a tramite professionista'
                : 'Passa a erogazione diretta';
            const btnCambia = el('button', labelBtn, 'btn btn-secondary btn-sm');
            btnCambia.type = 'button';
            btnCambia.addEventListener('click', async () => {
                if (occupata) return;
                toggleAzioni(true);
                mostraEsito(esitoRiga, `Aggiornamento di ${info.titolo}…`);
                try {
                    await api(`/api/catalogo/offerte-opt/misure/${encodeURIComponent(id)}`, {
                        method: 'PUT',
                        body: { tramiteProfessionista: prossimoValore }
                    });
                    const msg = `${info.titolo}: ${descrizioneModalita(prossimoValore)}.`;
                    mostraEsito(esitoLista, msg);
                    await caricaStato({ preservaMessaggio: msg });
                } catch (causa) {
                    mostraEsito(esitoRiga, causa?.campi?.misuraId || causa?.message || 'Aggiornamento non riuscito.', {
                        errore: true,
                        mostraLogin: causa?.stato === 401
                    });
                    toggleAzioni(false);
                }
            });
            azioni.append(btnCambia);
        }

        const btnRitira = el('button', 'Ritira', 'btn btn-danger btn-sm');
        btnRitira.type = 'button';
        btnRitira.addEventListener('click', async () => {
            if (occupata) return;
            toggleAzioni(true);
            const confermata = await confermaOperazione({
                titolo: 'Ritirare questa misura dall’offerta?',
                richiesta: info.titolo,
                testo: 'La misura non risulterà più nell’offerta corrente per le nuove ricerche, conservando lo storico delle pratiche.',
                azione: 'Ritira misura',
                pericolo: true
            });
            if (!confermata) {
                toggleAzioni(false);
                btnRitira.focus();
                return;
            }
            mostraEsito(esitoRiga, `Ritiro di ${info.titolo} in corso…`);
            try {
                await api(`/api/catalogo/offerte-opt/misure/${encodeURIComponent(id)}`, {
                    method: 'DELETE'
                });
                const msg = `${info.titolo} ritirata dall’offerta.`;
                mostraEsito(esitoLista, msg);
                await caricaStato({ preservaMessaggio: msg });
            } catch (causa) {
                mostraEsito(esitoRiga, causa?.message || 'Impossibile ritirare la misura.', {
                    errore: true,
                    mostraLogin: causa?.stato === 401
                });
                toggleAzioni(false);
            }
        });
        azioni.append(btnRitira);

        heading.append(identita, azioni);
        nodo.append(heading, esitoRiga);
        return listRow(nodo, {
            text: `${info.titolo} ${descModalita} ${info.attiva ? 'Attiva' : 'Disattivata'}`,
            filters: { modalita: offerta.tramiteProfessionista ? 'PROFESSIONISTA' : 'DIRETTA' },
            data: offerta
        });
    };

    const renderOfferte = () => {
        const righe = [...mappaOfferte.values()]
            .sort((a, b) => a.misuraId - b.misuraId)
            .map(costruisciRigaOfferta);
        renderList(listaEl, righe, {
            searchLabel: 'Cerca misura dichiarata',
            placeholder: 'Codice o denominazione',
            filters: [{
                key: 'modalita',
                label: 'Modalità',
                labels: {
                    DIRETTA: 'Diretta',
                    PROFESSIONISTA: 'Tramite professionista'
                }
            }],
            empty: 'Nessuna misura dichiarata nell’offerta corrente. Usa «Dichiara misure» per aggiungere una misura attiva.'
        });
    };

    const caricaStato = async ({ preservaMessaggio = '' } = {}) => {
        const richiesta = ++progressivo;
        if (btnAggiorna) btnAggiorna.disabled = true;
        listaEl.setAttribute('aria-busy', 'true');
        if (!preservaMessaggio) mostraEsito(esitoLista, 'Caricamento dell’offerta in corso…');
        try {
            const [attiveRes, offertaRes] = await Promise.all([
                api('/api/catalogo/misure'),
                api(`/api/catalogo/offerte-opt/${encodeURIComponent(optId)}`)
            ]);
            if (richiesta !== progressivo) return;

            misureAttive = Array.isArray(attiveRes) ? attiveRes : [];
            mappaMisure.clear();
            for (const m of misureAttive) mappaMisure.set(String(m.id), m);

            const offerte = Array.isArray(offertaRes) ? offertaRes : [];
            await risolviMisureSconosciute(offerte, mappaMisure);
            if (richiesta !== progressivo) return;

            mappaOfferte.clear();
            for (const voce of offerte) mappaOfferte.set(String(voce.misuraId), voce);

            renderOfferte();
            popolaSelectMisure();
            if (fieldset) fieldset.disabled = false;
            mostraEsito(esitoLista, preservaMessaggio);
        } catch (causa) {
            if (richiesta !== progressivo) return;
            mostraEsito(esitoLista, causa?.message || 'Impossibile caricare l’offerta corrente.', {
                errore: true,
                mostraLogin: causa?.stato === 401
            });
        } finally {
            if (richiesta === progressivo) {
                listaEl.setAttribute('aria-busy', 'false');
                if (btnAggiorna) btnAggiorna.disabled = false;
            }
        }
    };

    selectMisura?.addEventListener('change', () => {
        pulisciErroriForm(form);
        mostraEsito(esitoForm, '');
        sincronizzaModalitaForm();
    });

    btnAggiorna?.addEventListener('click', () => caricaStato());
    document.querySelector('.area-nav a[href="#offerta"]')?.addEventListener('click', () => caricaStato());
    document.getElementById('offerta-tab-attuale')?.addEventListener('click', () => caricaStato());

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (invioFormInCorso) return;

        pulisciErroriForm(form);
        mostraEsito(esitoForm, '');

        const misuraVal = String(selectMisura?.value || '').trim();
        if (!misuraVal) {
            mostraErroriForm(form, { misuraId: 'Seleziona una misura attiva da dichiarare.' });
            return;
        }
        const tramiteProfessionista = selectMod?.value === 'true';
        const info = risolviEtichettaMisura(misuraVal, mappaMisure);

        invioFormInCorso = true;
        if (submitBtn) submitBtn.disabled = true;
        mostraEsito(esitoForm, `Salvataggio di ${info.titolo} in corso…`);

        try {
            await api(`/api/catalogo/offerte-opt/misure/${encodeURIComponent(misuraVal)}`, {
                method: 'PUT',
                body: { tramiteProfessionista }
            });
            const conferma = `Offerta salvata per ${info.titolo} (${descrizioneModalita(tramiteProfessionista)}).`;
            mostraEsito(esitoForm, conferma);
            await caricaStato({ preservaMessaggio: conferma });
        } catch (causa) {
            mostraEsito(esitoForm, causa?.message || 'Impossibile salvare l’offerta.', {
                errore: true,
                mostraLogin: causa?.stato === 401
            });
            mostraErroriForm(form, causa?.campi);
            if (causa instanceof ErroreHttp && causa.stato === 400 && causa.campi?.misuraId) {
                await caricaStato({ preservaMessaggio: '' });
            }
        } finally {
            invioFormInCorso = false;
            if (submitBtn && selectMisura && !selectMisura.disabled) submitBtn.disabled = false;
        }
    });

    return caricaStato();
}

function initOfferteLatoBanco() {
    const form = document.getElementById('consulta-offerta');
    const listaEl = document.getElementById('offerta-banco');
    if (!form || !listaEl) return;
    if (form.dataset?.offertaBancoAttiva === 'true') return;
    if (form.dataset) form.dataset.offertaBancoAttiva = 'true';

    const selectOpt = form.elements.optId;
    const submitBtn = form.querySelector('button[type="submit"]');
    const esitoEl = form.querySelector('.picus-esito');
    const fieldset = form.querySelector('fieldset.campi-form');

    if (document.body?.dataset?.skeleton === 'true') {
        if (fieldset) fieldset.disabled = true;
        mostraEsito(esitoEl, 'Consultazione offerte non disponibile nel profilo skeleton.');
        return;
    }

    const mappaCatalogo = new Map();
    let catalogoCaricato = false;
    let progressivo = 0;

    const applicaCatalogo = misure => {
        if (!Array.isArray(misure)) return;
        mappaCatalogo.clear();
        for (const m of misure) mappaCatalogo.set(String(m.id), m);
        catalogoCaricato = true;
    };
    aggiornamentoCatalogoBanco = applicaCatalogo;

    const popolaSelectOpt = (vociServer = null) => {
        if (!selectOpt) return;
        const rubrica = leggiRubricaAccount('OPT', vociServer);
        const selezionata = String(selectOpt.value || '');
        const ordinate = [...rubrica.values()].sort((a, b) =>
            a.nome.localeCompare(b.nome, 'it', { sensitivity: 'base' }) || a.id.localeCompare(b.id)
        );
        if (ordinate.length === 0 && selectOpt.options.length > 1) return;

        const opzioni = [new Option('Seleziona un’organizzazione', '')];
        for (const opt of ordinate) {
            const dettagli = [];
            if (opt.riferimento) dettagli.push(opt.riferimento);
            if (opt.stato === 'REVOCATO') dettagli.push('Account revocato');
            if (opt.omonimo) dettagli.push(`ID ${opt.id}`);
            const label = dettagli.length ? `${opt.nome} · ${dettagli.join(' · ')}` : opt.nome;
            opzioni.push(new Option(label, opt.id));
        }
        selectOpt.replaceChildren(...opzioni);
        if (selezionata && rubrica.has(selezionata)) {
            selectOpt.value = selezionata;
        }
    };

    aggiornamentoOptBanco = async (vociServer = null) => {
        if (Array.isArray(vociServer)) {
            popolaSelectOpt(vociServer);
            return;
        }
        const voci = await api('/api/utenze/rubrica?ruolo=OPT').catch(() => null);
        popolaSelectOpt(voci);
    };

    popolaSelectOpt();

    const caricaOffertaOpt = async optId => {
        const richiesta = ++progressivo;
        pulisciErroriForm(form);
        listaEl.hidden = true;
        listaEl.replaceChildren();

        if (!optId) {
            mostraErroriForm(form, { optId: 'Seleziona un’organizzazione per consultarne l’offerta.' });
            return;
        }

        const labelOpt = selectOpt.options[selectOpt.selectedIndex]?.textContent || optId;
        if (submitBtn) submitBtn.disabled = true;
        mostraEsito(esitoEl, `Caricamento offerta di ${labelOpt}…`);

        try {
            const [offerte, misureRes] = await Promise.all([
                api(`/api/catalogo/offerte-opt/${encodeURIComponent(optId)}`),
                catalogoCaricato
                    ? Promise.resolve(null)
                    : api('/api/catalogo/misure/tutte').catch(() => null)
            ]);
            if (richiesta !== progressivo) return;
            if (misureRes) applicaCatalogo(misureRes);

            const elenco = Array.isArray(offerte) ? offerte : [];
            await risolviMisureSconosciute(elenco, mappaCatalogo);
            if (richiesta !== progressivo) return;

            const righe = elenco.map(voce => {
                const info = risolviEtichettaMisura(voce.misuraId, mappaCatalogo);
                const nodo = el('article', null, 'account-row');
                const cellaMisura = el('div', null, 'account-cell');
                cellaMisura.append(el('span', 'Misura', 'cell-label'), el('strong', info.titolo));

                const cellaMod = el('div', null, 'account-cell');
                cellaMod.append(
                    el('span', 'Modalità dichiarata', 'cell-label'),
                    el('span', descrizioneModalita(voce.tramiteProfessionista))
                );

                const cellaStato = el('div', null, 'account-cell');
                cellaStato.append(
                    el('span', 'Stato in catalogo', 'cell-label'),
                    el(
                        'span',
                        info.attiva ? 'Attiva' : 'Disattivata',
                        'badge-stato ' + (info.attiva ? 'badge-confermata' : 'badge-annullata')
                    )
                );
                nodo.append(cellaMisura, cellaMod, cellaStato);
                return listRow(nodo, {
                    text: `${info.titolo} ${descrizioneModalita(voce.tramiteProfessionista)} ${info.attiva ? 'Attiva' : 'Disattivata'}`,
                    data: voce
                });
            });

            listaEl.hidden = false;
            renderList(listaEl, righe, {
                searchLabel: 'Cerca nell’offerta',
                placeholder: 'Misura o modalità',
                columns: ['Misura', 'Modalità dichiarata', 'Stato in catalogo'],
                empty: 'Nessuna misura attualmente dichiarata dall’organizzazione selezionata.'
            });
            mostraEsito(
                esitoEl,
                elenco.length === 0
                    ? `Nessuna misura dichiarata da ${labelOpt}.`
                    : `${labelOpt}: ${elenco.length} ${elenco.length === 1 ? 'misura dichiarata' : 'misure dichiarate'}.`
            );
        } catch (causa) {
            if (richiesta !== progressivo) return;
            mostraEsito(esitoEl, causa?.message || 'Impossibile consultare l’offerta selezionata.', {
                errore: true,
                mostraLogin: causa?.stato === 401
            });
        } finally {
            if (submitBtn && richiesta === progressivo) submitBtn.disabled = false;
        }
    };

    selectOpt?.addEventListener('change', () => {
        progressivo++;
        pulisciErroriForm(form);
        if (!selectOpt.value) {
            mostraEsito(esitoEl, '');
            listaEl.hidden = true;
            listaEl.replaceChildren();
            return;
        }
        caricaOffertaOpt(selectOpt.value);
    });

    form.addEventListener('submit', event => {
        event.preventDefault();
        return caricaOffertaOpt(selectOpt?.value || '');
    });

    document.getElementById('catalogo-tab-offerte')?.addEventListener('click', () => {
        aggiornamentoOptBanco?.();
    });
}
