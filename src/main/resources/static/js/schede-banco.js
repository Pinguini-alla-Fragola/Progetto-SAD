import { api } from './area-client.js';
import { renderList, listRow } from './list-view.js';
import { leggiRubricaAccount } from './rubrica-banco.js';

const TIPOLOGIE = {
    INTERNO_BANCO: 'Interno del Banco',
    ESTERNO_PAGATO_BANCO: 'Esterno pagato dal Banco'
};

let prossimoDettaglio = 0;

/**
 * Nel dettaglio di sola lettura i numeri vanno mostrati con le cifre emesse dal server:
 * JSON.parse converte i token numerici in Number IEEE-754 a 64 bit prima di restituire l'oggetto,
 * arrotondando gli interi Long oltre Number.MAX_SAFE_INTEGER (es. 9223372036854775807).
 * L'espressione regolare riconosce per prime le stringhe JSON (così da non alterare eventuali cifre
 * dentro i valori testuali) e quota i soli token numerici prima del parsing.
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

export function initSchedeBanco() {
    const form = document.getElementById('riepilogo-schede');
    const lista = document.getElementById('riepilogo-schede-lista');
    const esito = document.getElementById('riepilogo-schede-esito');
    const riprova = document.getElementById('riepilogo-schede-riprova');
    if (!form || !lista || !esito || !riprova) return;
    if (form.dataset?.schedeBancoAttivo === 'true') return;
    if (form.dataset) form.dataset.schedeBancoAttivo = 'true';

    const submitBtn = form.querySelector('button[type="submit"]');
    const dettagliCache = new Map();
    let progressivo = 0;
    let ultimoPeriodo;

    const pulisciCampiErrati = () => {
        for (const campo of form.querySelectorAll('[aria-invalid]')) {
            campo.removeAttribute('aria-invalid');
        }
    };

    const mostraEsito = (testo, { errore = false, mostraLogin = false } = {}) => {
        esito.classList.toggle('picus-errore', errore);
        esito.replaceChildren();
        if (testo) esito.append(document.createTextNode(testo));
        if (mostraLogin) {
            const linkLogin = el('a', 'Accedi di nuovo', 'btn btn-secondary btn-sm');
            linkLogin.href = '/login';
            esito.append(' ', linkLogin);
        }
    };

    const pulisciRisultati = () => {
        mostraEsito('');
        riprova.hidden = true;
        lista.hidden = true;
        lista.replaceChildren();
        dettagliCache.clear();
        pulisciCampiErrati();
    };

    const renderRisultato = (anno, trimestre, riepilogo, richiestaCorrente, operatori) => {
        const schede = Array.isArray(riepilogo?.schede) ? riepilogo.schede : [];
        const conteggi = riepilogo?.conteggi && typeof riepilogo.conteggi === 'object' ? riepilogo.conteggi : {};

        const intestazionePeriodo = el(
            'p',
            `Periodo consultato: ${anno} · ${trimestre}º trimestre (${schede.length} ${schede.length === 1 ? 'scheda ricevuta' : 'schede ricevute'})`,
            'picus-meta'
        );

        const boxLista = el('div', null, 'report-results');
        boxLista.id = `${lista.id}-risultati`;
        const boxTotali = el('section', null, 'report-summary');

        lista.replaceChildren(intestazionePeriodo, boxLista, boxTotali);

        const righe = schede.map(sintesi => {
            const op = operatori.get(sintesi.operatoreId);
            const nomeOperatore = op?.nome || `Operatore (${sintesi.operatoreId})`;
            const metaParti = [`${sintesi.trimestre?.anno ?? anno} · ${sintesi.trimestre?.numero ?? trimestre}º trimestre`];
            if (op?.riferimento) metaParti.push(TIPOLOGIE[op.riferimento] || op.riferimento);
            if (op?.stato === 'REVOCATO') metaParti.push('Account revocato');
            if (!op || op.omonimo) metaParti.push(`ID account ${sintesi.operatoreId}`);

            const articolo = el('article', null, 'report-record');
            const heading = el('div', null, 'report-heading');
            const identita = el('div', null, 'report-identity');
            identita.append(el('h3', nomeOperatore), el('p', metaParti.join(' · '), 'picus-meta'));

            const metriche = el('dl', null, 'report-metrics');
            const invioPair = el('div');
            const timeEl = el('time', formattaIstante(sintesi.inviataIl));
            if (sintesi.inviataIl) timeEl.dateTime = sintesi.inviataIl;
            const ddInvio = el('dd');
            ddInvio.append(timeEl);
            invioPair.append(el('dt', 'Inviata il'), ddInvio);
            metriche.append(invioPair);

            const toggle = el('button', 'Apri scheda', 'btn btn-secondary btn-sm');
            toggle.type = 'button';

            const detail = el('div', null, 'record-details');
            detail.id = `scheda-dettaglio-${++prossimoDettaglio}`;
            detail.hidden = true;
            toggle.setAttribute('aria-controls', detail.id);
            toggle.setAttribute('aria-expanded', 'false');

            let caricamentoInCorso = false;
            const caricaDettaglio = async () => {
                if (caricamentoInCorso) return;
                if (dettagliCache.has(sintesi.id)) {
                    renderDettaglioScheda(detail, dettagliCache.get(sintesi.id));
                    return;
                }
                caricamentoInCorso = true;
                detail.setAttribute('aria-busy', 'true');
                detail.replaceChildren(el('p', 'Caricamento dettaglio scheda…', 'picus-meta'));
                try {
                    const dettaglio = await api(
                        `/api/monitoraggio/schede-operatore/${encodeURIComponent(sintesi.id)}`,
                        { decodificaJson: decodificaDettaglio }
                    );
                    if (richiestaCorrente !== progressivo) return;
                    dettagliCache.set(sintesi.id, dettaglio);
                    renderDettaglioScheda(detail, dettaglio);
                } catch (err) {
                    if (richiestaCorrente !== progressivo) return;
                    const errMsg = el('p', err.message || 'Impossibile caricare la scheda.', 'picus-errore');
                    errMsg.setAttribute('role', 'alert');
                    const retryBtn = el('button', 'Riprova', 'btn btn-secondary btn-sm');
                    retryBtn.type = 'button';
                    retryBtn.addEventListener('click', () => caricaDettaglio());
                    detail.replaceChildren(errMsg, retryBtn);
                    if (err.stato === 401) {
                        const login = el('a', 'Accedi di nuovo', 'btn btn-secondary btn-sm');
                        login.href = '/login';
                        detail.append(' ', login);
                    }
                } finally {
                    caricamentoInCorso = false;
                    detail.removeAttribute('aria-busy');
                }
            };

            toggle.addEventListener('click', () => {
                detail.hidden = !detail.hidden;
                toggle.textContent = detail.hidden ? 'Apri scheda' : 'Chiudi scheda';
                toggle.setAttribute('aria-expanded', String(!detail.hidden));
                if (!detail.hidden && !dettagliCache.has(sintesi.id)) {
                    return caricaDettaglio();
                }
            });

            heading.append(identita, metriche, toggle);
            articolo.append(heading, detail);

            return listRow(articolo, {
                text: [nomeOperatore, ...metaParti, sintesi.id].join(' '),
                data: sintesi
            });
        });

        renderList(boxLista, righe, {
            searchLabel: 'Cerca operatore nell’elenco',
            placeholder: 'Nome operatore o identificativo',
            empty: 'Nessuna scheda ricevuta per il trimestre selezionato.'
        });

        const vociTotali = Object.entries(conteggi).sort(([a], [b]) => a.localeCompare(b, 'it'));
        if (schede.length === 0) {
            boxTotali.hidden = true;
        } else {
            boxTotali.hidden = false;
            boxTotali.append(
                el('h3', 'Riepilogo trimestrale dei conteggi dichiarati'),
                el(
                    'p',
                    'Conteggi dichiarati aggregati per voce: le categorie sovrapponibili restano distinte e non rappresentano persone uniche tra operatori.',
                    'picus-meta'
                )
            );
            if (vociTotali.length === 0) {
                boxTotali.append(
                    el(
                        'p',
                        'Schede ricevute presenti per il trimestre selezionato, ma nessuna voce rendicontabile nei totali.',
                        'picus-empty'
                    )
                );
            } else {
                boxTotali.append(tabellaConteggi(['Voce dichiarata', 'Totale dichiarato'], vociTotali));
            }
        }
        lista.hidden = false;
    };

    const consulta = async periodo => {
        const richiesta = ++progressivo;
        ultimoPeriodo = periodo;
        pulisciRisultati();

        if (submitBtn) submitBtn.disabled = true;
        mostraEsito('Caricamento schede e riepilogo…');

        try {
            const params = new URLSearchParams({
                anno: String(periodo.anno),
                trimestre: String(periodo.trimestre)
            });
            const dati = await api('/api/monitoraggio/schede-operatore/ricevute/riepilogo?' + params);
            if (richiesta !== progressivo) return;
            const vociRubrica = dati.schede?.length
                ? await api('/api/utenze/rubrica?ruolo=OPERATORE').catch(() => null)
                : [];
            if (richiesta !== progressivo) return;
            mostraEsito('');
            renderRisultato(periodo.anno, periodo.trimestre, dati, richiesta, leggiRubricaAccount('OPERATORE', vociRubrica));
        } catch (err) {
            if (richiesta !== progressivo) return;
            mostraEsito(err.message || 'Impossibile consultare il riepilogo.', {
                errore: true,
                mostraLogin: err.stato === 401
            });
            const campiErrati = err.campi ? Object.keys(err.campi) : [];
            if (campiErrati.length > 0) {
                for (const nome of campiErrati) {
                    form.elements[nome]?.setAttribute('aria-invalid', 'true');
                }
                form.elements[campiErrati[0]]?.focus();
            } else if (err.stato !== 401 && err.stato !== 403) {
                riprova.hidden = false;
            }
        } finally {
            if (submitBtn && richiesta === progressivo) submitBtn.disabled = false;
        }
    };

    form.addEventListener('submit', event => {
        event.preventDefault();
        return consulta({
            anno: form.elements.anno.value,
            trimestre: form.elements.trimestre.value
        });
    });

    form.addEventListener('input', () => {
        progressivo++;
        ultimoPeriodo = undefined;
        if (submitBtn) submitBtn.disabled = false;
        pulisciRisultati();
    });

    riprova.addEventListener('click', () => {
        if (!riprova.hidden && ultimoPeriodo) {
            return consulta(ultimoPeriodo);
        }
    });
}

if (typeof document !== 'undefined' && document.getElementById('riepilogo-schede')) {
    initSchedeBanco();
}
