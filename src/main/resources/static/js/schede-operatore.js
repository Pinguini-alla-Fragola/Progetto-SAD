import { api, ErroreHttp } from './area-client.js';
import { renderList, listRow } from './list-view.js';

let prossimoDettaglio = 0;

/**
 * Nel dettaglio di sola lettura preserva le cifre esatte dei numeri Long/BigDecimal
 * emessi dal server prima del parsing IEEE-754.
 */
function decodificaDettaglio(testo) {
    const tokenNumericiOStringhe = /"(?:[^"\\]|\\[\s\S])*"|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/g;
    return JSON.parse(testo.replace(tokenNumericiOStringhe, token =>
        token.startsWith('"') ? token : '"' + token + '"'
    ));
}

function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = String(text);
    if (className) node.className = className;
    return node;
}

function formattaIstante(istante) {
    if (!istante) return '—';
    return new Date(istante).toLocaleString('it-IT', {
        dateStyle: 'short',
        timeStyle: 'short',
        timeZone: 'Europe/Rome'
    });
}

function ultimoTrimestreConcluso() {
    try {
        const parti = new Intl.DateTimeFormat('en-CA', {
            timeZone: 'Europe/Rome',
            year: 'numeric',
            month: 'numeric'
        }).formatToParts(new Date());
        const anno = Number(parti.find(p => p.type === 'year')?.value);
        const mese = Number(parti.find(p => p.type === 'month')?.value);
        const trimestreCorrente = Math.floor((mese - 1) / 3) + 1;
        return trimestreCorrente === 1
            ? { anno: anno - 1, trimestre: 4 }
            : { anno, trimestre: trimestreCorrente - 1 };
    } catch {
        const oggi = new Date();
        const trimestreCorrente = Math.floor(oggi.getMonth() / 3) + 1;
        return trimestreCorrente === 1
            ? { anno: oggi.getFullYear() - 1, trimestre: 4 }
            : { anno: oggi.getFullYear(), trimestre: trimestreCorrente - 1 };
    }
}

function tabellaConteggi(intestazioni, righe) {
    const wrap = el('div', null, 'report-table-wrap');
    const table = el('table', null, 'report-table');
    const thead = el('thead');
    const trHead = el('tr');
    for (const titolo of intestazioni) {
        const th = el('th', titolo);
        th.scope = 'col';
        trHead.append(th);
    }
    thead.append(trHead);
    const tbody = el('tbody');
    for (const [voce, valore] of righe) {
        const tr = el('tr');
        tr.append(el('td', voce), el('td', String(valore)));
        tbody.append(tr);
    }
    table.append(thead, tbody);
    wrap.append(table);
    return wrap;
}

function renderDettaglioScheda(contenitore, scheda) {
    contenitore.replaceChildren();
    const dati = scheda?.dati || {};
    const anagrafica = el('dl', null, 'picus-dati');
    const campi = [
        ['Nome', dati.nome],
        ['Cognome', dati.cognome],
        ['Codice fiscale', dati.codiceFiscale],
        ['Luogo di nascita', dati.luogoNascita],
        ['Data di nascita', dati.dataNascita],
        ['Qualifica', dati.qualifica],
        ['Tipologia contratto', dati.tipologiaContratto],
        ['Area e livello', dati.areaLivello],
        ['Ore lavorate', dati.ore != null ? String(dati.ore) : null],
        ['Costo orario lordo', dati.costoOrarioLordo != null ? `${dati.costoOrarioLordo} €` : null]
    ];
    for (const [etichetta, valore] of campi) {
        if (valore == null || valore === '') continue;
        anagrafica.append(el('dt', etichetta), el('dd', String(valore)));
    }
    if (anagrafica.children.length > 0) {
        contenitore.append(anagrafica);
    } else {
        contenitore.append(el('p', 'Nessun dato anagrafico o contrattuale aggiuntivo indicato.', 'picus-meta'));
    }

    const voci = Object.entries(dati.conteggi || {})
        .sort(([a], [b]) => a.localeCompare(b, 'it'));
    if (voci.length === 0) {
        contenitore.append(el('p', 'Nessuna voce di conteggio dichiarata in questa scheda.', 'picus-meta'));
    } else {
        contenitore.append(tabellaConteggi(['Voce dichiarata', 'Conteggio dichiarato'], voci));
    }
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

function mostraErroreSuControllo(controllo, messaggio) {
    if (!controllo) return;
    controllo.setAttribute('aria-invalid', 'true');
    const err = el('span', messaggio, 'picus-errore');
    err.setAttribute('role', 'alert');
    (controllo.closest?.('label') ?? controllo.parentElement).append(err);
}

function parseDecimaleNonNegativo(testo, etichetta) {
    const pulito = String(testo ?? '').trim().replace(',', '.');
    if (!/^(?:0|[1-9][0-9]*)(?:\.[0-9]+)?$/.test(pulito)) {
        throw new Error(`Indica un numero non negativo valido per ${etichetta}`);
    }
    const [parteIntera, parteDecimale = ''] = pulito.split('.');
    if (parteIntera.length > 13 || parteDecimale.length > 6) {
        throw new Error('Usa al massimo 13 cifre intere e 6 decimali');
    }
    const numero = Number(pulito);
    if (!Number.isFinite(numero) || numero < 0) {
        throw new Error(`Indica un numero non negativo valido per ${etichetta}`);
    }
    return numero;
}

/** Collega compilazione e consultazione delle schede personali trimestrali dell'Operatore (#179). */
export function initSchedeOperatore() {
    const form = document.getElementById('scheda');
    const vociContenitore = document.getElementById('voci-scheda');
    const storicoEl = document.getElementById('schede-mie');
    if (!form || !vociContenitore || !storicoEl) return;
    if (form.dataset?.schedeOperatoreAttivo === 'true') return;
    if (form.dataset) form.dataset.schedeOperatoreAttivo = 'true';

    const campiInvioFieldset = document.getElementById('scheda-campi-invio');
    const infoConfigEl = document.getElementById('scheda-configurazione-info');
    const esitoFormEl = document.getElementById('scheda-esito') || form.querySelector('.picus-esito');
    const esitoStoricoEl = document.getElementById('schede-mie-esito');
    const btnAggiornaStorico = document.getElementById('schede-mie-aggiorna');
    const submitBtn = form.querySelector('button[type="submit"]');
    const inputAnno = form.elements.anno;
    const selectTrimestre = form.elements.trimestre;

    if (document.body?.dataset?.skeleton === 'true') {
        if (campiInvioFieldset) campiInvioFieldset.disabled = true;
        if (inputAnno) inputAnno.disabled = true;
        if (selectTrimestre) selectTrimestre.disabled = true;
        if (btnAggiornaStorico) btnAggiornaStorico.disabled = true;
        mostraEsito(esitoFormEl, 'Compilazione scheda trimestrale non disponibile nel profilo skeleton.');
        mostraEsito(esitoStoricoEl, 'Storico schede non disponibile nel profilo skeleton.');
        return;
    }

    const periodoIniziale = ultimoTrimestreConcluso();
    if (inputAnno) inputAnno.value = String(periodoIniziale.anno);
    if (selectTrimestre) selectTrimestre.value = String(periodoIniziale.trimestre);

    let moduloCorrente = null;
    let periodoModuloCaricato = '';
    let progressivoModulo = 0;
    let progressivoStorico = 0;
    let invioInCorso = false;
    const dettagliCache = new Map();

    const leggiPeriodoForm = () => ({
        annoRaw: String(inputAnno?.value ?? '').trim(),
        trimestreRaw: String(selectTrimestre?.value ?? '').trim()
    });

    const renderVociCategorie = categorie => {
        vociContenitore.replaceChildren();
        if (!Array.isArray(categorie) || categorie.length === 0) {
            vociContenitore.append(el(
                'p',
                'Nessuna categoria predefinita per il tracciato provvisorio v0.1: l’invio registrerà conteggi vuoti.',
                'picus-meta'
            ));
            return;
        }
        const ordinate = [...categorie].sort((a, b) => (a.ordine ?? 0) - (b.ordine ?? 0));
        for (const cat of ordinate) {
            const riga = el('div', null, 'count-row');
            const infoBox = el('div');
            infoBox.append(el('strong', `${cat.descrizione} (${cat.codice})`));
            if (cat.definizione) infoBox.append(el('p', cat.definizione, 'picus-meta'));

            const labelVal = el('label', null, 'campo');
            labelVal.append(el('span', `Conteggio per ${cat.descrizione}`));
            const input = document.createElement('input');
            input.className = 'form-control';
            input.type = 'text';
            input.inputMode = 'numeric';
            input.pattern = '[0-9]+';
            input.placeholder = '0';
            input.dataset.categoria = cat.codice;
            input.name = `conteggio_${cat.codice}`;
            labelVal.append(input);

            riga.append(infoBox, labelVal);
            vociContenitore.append(riga);
        }
    };

    const sincronizzaCampiDaModulo = campiModulo => {
        if (!Array.isArray(campiModulo)) return;
        for (const campo of campiModulo) {
            const controllo = form.elements?.[campo.nome];
            if (controllo && 'required' in controllo) {
                controllo.required = Boolean(campo.obbligatorio);
            }
        }
    };

    const caricaModuloPeriodo = async () => {
        const { annoRaw, trimestreRaw } = leggiPeriodoForm();
        const richiesta = ++progressivoModulo;
        moduloCorrente = null;
        periodoModuloCaricato = '';
        pulisciErroriForm(form);
        mostraEsito(esitoFormEl, '');

        if (!/^[1-9][0-9]*$/.test(annoRaw) || !['1', '2', '3', '4'].includes(trimestreRaw)) {
            if (campiInvioFieldset) campiInvioFieldset.disabled = true;
            if (infoConfigEl) infoConfigEl.textContent = 'Indica anno positivo e trimestre da 1 a 4.';
            vociContenitore.replaceChildren();
            return false;
        }

        if (campiInvioFieldset) campiInvioFieldset.disabled = true;
        if (infoConfigEl) infoConfigEl.textContent = `Verifica tracciato e categorie per ${annoRaw} · ${trimestreRaw}º trimestre…`;

        try {
            const params = new URLSearchParams({ anno: annoRaw, trimestre: trimestreRaw });
            const modulo = await api(`/api/monitoraggio/schede-operatore/modulo?${params}`);
            if (richiesta !== progressivoModulo) return false;

            moduloCorrente = modulo;
            periodoModuloCaricato = `${annoRaw}-${trimestreRaw}`;
            sincronizzaCampiDaModulo(modulo.campi);

            const conf = modulo.configurazioneCategorie || {};
            if (infoConfigEl) {
                infoConfigEl.textContent = conf.stato === 'PUBBLICATA'
                    ? `Periodo ${annoRaw} · ${trimestreRaw}º trimestre — Configurazione categorie pubblicata: ${conf.codiceVersione}.`
                    : `Periodo ${annoRaw} · ${trimestreRaw}º trimestre — Tracciato provvisorio ${conf.codiceVersione || modulo.versione}.`;
            }
            renderVociCategorie(conf.categorie || []);
            if (campiInvioFieldset) campiInvioFieldset.disabled = false;
            return true;
        } catch (causa) {
            if (richiesta !== progressivoModulo) return false;
            if (campiInvioFieldset) campiInvioFieldset.disabled = true;
            vociContenitore.replaceChildren();
            if (causa instanceof ErroreHttp && causa.stato === 409) {
                const msg409 = causa.messaggioServer || causa.message;
                if (infoConfigEl) infoConfigEl.textContent = msg409;
                mostraEsito(esitoFormEl, msg409, { errore: true });
            } else {
                const msg = causa?.message || 'Impossibile caricare il modulo del trimestre selezionato.';
                if (infoConfigEl) infoConfigEl.textContent = msg;
                mostraEsito(esitoFormEl, msg, { errore: true, mostraLogin: causa?.stato === 401 });
            }
            if (causa?.campi) {
                for (const [campo, testo] of Object.entries(causa.campi)) {
                    mostraErroreSuControllo(form.elements?.[campo], testo);
                }
            }
            return false;
        }
    };

    const caricaStorico = async () => {
        const richiesta = ++progressivoStorico;
        dettagliCache.clear();
        if (btnAggiornaStorico) btnAggiornaStorico.disabled = true;
        storicoEl.setAttribute('aria-busy', 'true');
        mostraEsito(esitoStoricoEl, 'Caricamento schede inviate…');

        try {
            const schede = await api('/api/monitoraggio/schede-operatore/mie');
            if (richiesta !== progressivoStorico) return;
            const elenco = Array.isArray(schede) ? schede : [];
            const ordinate = [...elenco].sort((a, b) =>
                new Date(b.inviataIl || 0) - new Date(a.inviataIl || 0) || String(a.id).localeCompare(String(b.id))
            );
            const righe = ordinate.map(sintesi => {
                const nodo = el('article', null, 'report-record');
                const heading = el('div', null, 'report-heading');
                const identita = el('div', null, 'report-identity');
                const periodoTesto = `${sintesi.trimestre?.anno ?? '—'} · ${sintesi.trimestre?.numero ?? '—'}º trimestre`;
                identita.append(
                    el('h3', `Scheda ${periodoTesto}`),
                    el('p', `ID ${sintesi.id}`, 'picus-meta')
                );

                const metriche = el('dl', null, 'report-metrics');
                const pair = el('div');
                const timeEl = el('time', formattaIstante(sintesi.inviataIl));
                if (sintesi.inviataIl) timeEl.dateTime = sintesi.inviataIl;
                const dd = el('dd');
                dd.append(timeEl);
                pair.append(el('dt', 'Inviata il'), dd);
                metriche.append(pair);

                const toggle = el('button', 'Apri scheda', 'btn btn-secondary btn-sm');
                toggle.type = 'button';

                const detail = el('div', null, 'record-details');
                detail.id = `operatore-scheda-dettaglio-${++prossimoDettaglio}`;
                detail.hidden = true;
                toggle.setAttribute('aria-controls', detail.id);
                toggle.setAttribute('aria-expanded', 'false');

                let dettaglioInCorso = false;
                const caricaDettaglio = async () => {
                    if (dettaglioInCorso) return;
                    if (dettagliCache.has(sintesi.id)) {
                        renderDettaglioScheda(detail, dettagliCache.get(sintesi.id));
                        return;
                    }
                    dettaglioInCorso = true;
                    detail.setAttribute('aria-busy', 'true');
                    detail.replaceChildren(el('p', 'Caricamento dettaglio scheda…', 'picus-meta'));
                    try {
                        const dettaglio = await api(
                            `/api/monitoraggio/schede-operatore/${encodeURIComponent(sintesi.id)}`,
                            { decodificaJson: decodificaDettaglio }
                        );
                        if (richiesta !== progressivoStorico) return;
                        dettagliCache.set(sintesi.id, dettaglio);
                        renderDettaglioScheda(detail, dettaglio);
                    } catch (causa) {
                        if (richiesta !== progressivoStorico) return;
                        const pErr = el('p', causa?.message || 'Impossibile aprire il dettaglio della scheda.', 'picus-errore');
                        pErr.setAttribute('role', 'alert');
                        const retry = el('button', 'Riprova', 'btn btn-secondary btn-sm');
                        retry.type = 'button';
                        retry.addEventListener('click', caricaDettaglio);
                        detail.replaceChildren(pErr, retry);
                        if (causa?.stato === 401) {
                            const login = el('a', 'Accedi di nuovo', 'btn btn-secondary btn-sm');
                            login.href = '/login';
                            detail.append(' ', login);
                        }
                    } finally {
                        dettaglioInCorso = false;
                        detail.removeAttribute('aria-busy');
                    }
                };

                toggle.addEventListener('click', () => {
                    detail.hidden = !detail.hidden;
                    toggle.textContent = detail.hidden ? 'Apri scheda' : 'Chiudi scheda';
                    toggle.setAttribute('aria-expanded', String(!detail.hidden));
                    if (!detail.hidden && !dettagliCache.has(sintesi.id)) {
                        caricaDettaglio();
                    }
                });

                heading.append(identita, metriche, toggle);
                nodo.append(heading, detail);
                return listRow(nodo, {
                    text: `${periodoTesto} ${sintesi.id}`,
                    filters: { anno: String(sintesi.trimestre?.anno ?? '') },
                    data: sintesi
                });
            });

            renderList(storicoEl, righe, {
                searchLabel: 'Cerca tra le schede inviate',
                placeholder: 'Anno o identificativo',
                filters: [{ key: 'anno', label: 'Anno' }],
                empty: 'Non hai ancora inviato schede personali trimestrali.'
            });
            mostraEsito(esitoStoricoEl, '');
        } catch (causa) {
            if (richiesta !== progressivoStorico) return;
            mostraEsito(esitoStoricoEl, causa?.message || 'Impossibile caricare le schede inviate.', {
                errore: true,
                mostraLogin: causa?.stato === 401
            });
        } finally {
            if (richiesta === progressivoStorico) {
                storicoEl.setAttribute('aria-busy', 'false');
                if (btnAggiornaStorico) btnAggiornaStorico.disabled = false;
            }
        }
    };

    inputAnno?.addEventListener('input', () => {
        if (/^[1-9][0-9]{3}$/.test(String(inputAnno.value ?? '').trim())) {
            caricaModuloPeriodo();
        } else {
            pulisciErroriForm(form);
            mostraEsito(esitoFormEl, '');
            if (campiInvioFieldset) campiInvioFieldset.disabled = true;
        }
    });
    inputAnno?.addEventListener('change', () => caricaModuloPeriodo());
    selectTrimestre?.addEventListener('change', () => caricaModuloPeriodo());
    btnAggiornaStorico?.addEventListener('click', () => caricaStorico());
    document.getElementById('scheda-tab-inviate')?.addEventListener('click', () => caricaStorico());

    const testoFacoltativo = nome => {
        const valore = String(form.elements?.[nome]?.value ?? '').trim();
        return valore === '' ? null : valore;
    };

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (invioInCorso) return;

        const { annoRaw, trimestreRaw } = leggiPeriodoForm();
        if (periodoModuloCaricato !== `${annoRaw}-${trimestreRaw}` || !moduloCorrente) {
            const ok = await caricaModuloPeriodo();
            if (!ok) return;
        }

        pulisciErroriForm(form);
        mostraEsito(esitoFormEl, '');

        const erroriLocali = [];
        let ore;
        try {
            ore = parseDecimaleNonNegativo(form.elements.ore?.value, 'le ore lavorate');
        } catch (err) {
            erroriLocali.push([form.elements.ore, err.message]);
        }
        let costoOrarioLordo;
        try {
            costoOrarioLordo = parseDecimaleNonNegativo(form.elements.costoOrarioLordo?.value, 'il costo orario lordo');
        } catch (err) {
            erroriLocali.push([form.elements.costoOrarioLordo, err.message]);
        }

        const dataNascitaRaw = String(form.elements.dataNascita?.value ?? '').trim();
        if (dataNascitaRaw && !/^\d{4}-\d{2}-\d{2}$/.test(dataNascitaRaw)) {
            erroriLocali.push([form.elements.dataNascita, 'Indica una data valida (AAAA-MM-GG)']);
        }

        const conteggi = {};
        const ammesse = new Set((moduloCorrente?.configurazioneCategorie?.categorie || []).map(c => c.codice));
        for (const input of vociContenitore.querySelectorAll('input[data-categoria]')) {
            const codice = input.dataset.categoria;
            if (!ammesse.has(codice)) continue;
            const raw = String(input.value ?? '').trim();
            if (raw === '') continue;
            if (!/^[0-9]+$/.test(raw)) {
                erroriLocali.push([input, 'Inserisci un conteggio intero non negativo']);
                continue;
            }
            const num = Number(raw);
            if (!Number.isSafeInteger(num) || num < 0) {
                erroriLocali.push([input, 'Valore numerico intero fuori intervallo']);
                continue;
            }
            conteggi[codice] = num;
        }

        if (erroriLocali.length > 0) {
            for (const [ctrl, msg] of erroriLocali) mostraErroreSuControllo(ctrl, msg);
            mostraEsito(esitoFormEl, 'Controlla i campi evidenziati della scheda.', { errore: true });
            erroriLocali[0][0]?.focus?.();
            return;
        }

        const payload = {
            nome: testoFacoltativo('nome'),
            cognome: testoFacoltativo('cognome'),
            luogoNascita: testoFacoltativo('luogoNascita'),
            dataNascita: dataNascitaRaw || null,
            codiceFiscale: testoFacoltativo('codiceFiscale'),
            qualifica: testoFacoltativo('qualifica'),
            tipologiaContratto: testoFacoltativo('tipologiaContratto'),
            areaLivello: testoFacoltativo('areaLivello'),
            ore,
            costoOrarioLordo,
            conteggi
        };

        invioInCorso = true;
        if (submitBtn) submitBtn.disabled = true;
        if (campiInvioFieldset) campiInvioFieldset.disabled = true;
        if (inputAnno) inputAnno.disabled = true;
        if (selectTrimestre) selectTrimestre.disabled = true;
        mostraEsito(esitoFormEl, 'Invio della scheda in corso…');

        try {
            const params = new URLSearchParams({ anno: annoRaw, trimestre: trimestreRaw });
            const ricevuta = await api(`/api/monitoraggio/schede-operatore?${params}`, {
                method: 'POST',
                body: payload
            });
            for (const input of vociContenitore.querySelectorAll('input[data-categoria]')) {
                input.value = '';
            }
            const trimReg = ricevuta?.trimestre;
            mostraEsito(
                esitoFormEl,
                `Scheda registrata per ${trimReg?.anno ?? annoRaw} · ${trimReg?.numero ?? trimestreRaw}º trimestre (ID ${ricevuta?.id ?? '—'}).`
            );
            await caricaStorico();
        } catch (causa) {
            if (causa instanceof ErroreHttp && causa.stato === 409) {
                mostraEsito(esitoFormEl, causa.messaggioServer || causa.message, { errore: true });
            } else {
                mostraEsito(esitoFormEl, causa?.message || 'Impossibile inviare la scheda.', {
                    errore: true,
                    mostraLogin: causa?.stato === 401
                });
            }
            if (causa?.campi) {
                let primo = null;
                for (const [campo, testo] of Object.entries(causa.campi)) {
                    const controllo = form.elements?.[campo];
                    if (controllo) {
                        mostraErroreSuControllo(controllo, testo);
                        if (!primo) primo = controllo;
                    } else if (campo === 'conteggi') {
                        mostraEsito(esitoFormEl, testo, { errore: true });
                    }
                }
                primo?.focus?.();
            }
        } finally {
            invioInCorso = false;
            if (inputAnno) inputAnno.disabled = false;
            if (selectTrimestre) selectTrimestre.disabled = false;
            if (campiInvioFieldset && moduloCorrente) campiInvioFieldset.disabled = false;
            if (submitBtn) submitBtn.disabled = false;
        }
    });

    return Promise.all([caricaModuloPeriodo(), caricaStorico()]);
}
