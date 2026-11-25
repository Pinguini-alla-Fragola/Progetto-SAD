import { api } from './area-client.js';
import { renderList } from './list-view.js';

const MASSIMO_CONTEGGIO = 9_223_372_036_854_775_807n;

export function valoreConteggio(testo) {
    if (!/^[0-9]{1,19}$/.test(testo)) {
        throw new Error('Inserisci un conteggio intero non negativo con al massimo 19 cifre.');
    }
    const valore = BigInt(testo);
    if (valore > MASSIMO_CONTEGGIO) {
        throw new Error('Il conteggio supera il limite di 9223372036854775807.');
    }
    return valore <= BigInt(Number.MAX_SAFE_INTEGER) ? Number(valore) : valore.toString();
}

export function aggiungiFinePeriodo(params, annoFine, trimestreFine, annoInizio, trimestreInizio) {
    if (Boolean(annoFine) !== Boolean(trimestreFine)) {
        throw new Error("Indica sia l'anno sia il trimestre finale.");
    }
    if (annoFine) {
        if (annoInizio !== undefined && trimestreInizio !== undefined) {
            const aInizio = Number(annoInizio);
            const tInizio = Number(trimestreInizio);
            const aFine = Number(annoFine);
            const tFine = Number(trimestreFine);
            if (aFine < aInizio || (aFine === aInizio && tFine < tInizio)) {
                throw new Error('Il periodo finale non può precedere quello iniziale.');
            }
        }
        params.set('annoFine', annoFine);
        params.set('trimestreFine', trimestreFine);
    }
}

function leggiOptDaRubrica(vociServer = null) {
    const mappa = new Map();
    const conteggioNomi = new Map();
    const righe = document.querySelectorAll?.('#account .account-row[data-ruolo="OPT"]') || [];
    for (const riga of righe) {
        const id = riga.dataset?.id;
        if (!id) continue;
        const nome = (riga.dataset.nome || '').trim();
        const riferimento = (riga.dataset.riferimento || '').trim();
        const stato = (riga.dataset.stato || '').trim();
        mappa.set(id, { id, nome, riferimento, stato });
    }
    if (Array.isArray(vociServer)) {
        for (const voce of vociServer) {
            if (!voce?.id) continue;
            mappa.set(String(voce.id), {
                id: String(voce.id),
                nome: String(voce.nome || '').trim(),
                riferimento: String(voce.riferimento || '').trim(),
                stato: String(voce.stato || '').trim()
            });
        }
    }
    for (const opt of mappa.values()) {
        const chiaveNome = opt.nome.toLocaleLowerCase('it-IT');
        if (chiaveNome) {
            conteggioNomi.set(chiaveNome, (conteggioNomi.get(chiaveNome) || 0) + 1);
        }
    }
    for (const opt of mappa.values()) {
        const chiaveNome = opt.nome.toLocaleLowerCase('it-IT');
        opt.omonimo = chiaveNome ? (conteggioNomi.get(chiaveNome) || 0) > 1 : false;
    }
    return mappa;
}

function descriviPeriodo(periodo) {
    const { anno, trimestre, annoFine, trimestreFine } = periodo;
    if (annoFine && trimestreFine) {
        return `Periodo consultato: dal ${trimestre}º trimestre ${anno} al ${trimestreFine}º trimestre ${annoFine} (cumulativo)`;
    }
    return `Periodo consultato: ${anno} · ${trimestre}º trimestre`;
}

export async function initConteggiOpt(area) {
    const utenteId = document.body.dataset.utente;
    let optRubrica = area === 'banco' ? leggiOptDaRubrica() : new Map();
    let categorie = [];
    const misureMap = new Map();
    const misureNonTrovate = new Set();
    const misureAttive = [];
    const MASSIMA_CONCORRENZA_MISURE = 4;

    const formattaEtichettaMisura = (m, fallbackId) => {
        const nome = m.dati?.nome || m.nome || `Misura #${fallbackId ?? m.id}`;
        return m.attiva === false ? `${nome} (disattivata)` : nome;
    };

    const aggiornaMappaCatalogo = misRes => {
        if (!Array.isArray(misRes)) return false;
        const presenti = new Set();
        for (const m of misRes) {
            const id = String(m.id);
            presenti.add(id);
            misureNonTrovate.delete(id);
            misureMap.set(id, formattaEtichettaMisura(m, id));
        }
        if (area === 'banco') {
            for (const id of [...misureMap.keys()]) {
                if (!presenti.has(id)) misureMap.delete(id);
            }
        }
        return true;
    };

    const fetchCatalogoArea = () => (
        area === 'banco'
            ? api('/api/catalogo/misure/tutte')
            : api('/api/catalogo/misure')
    );

    try {
        const [catRes, misRes] = await Promise.all([
            api('/api/monitoraggio/categorie'),
            fetchCatalogoArea().catch(() => [])
        ]);
        categorie = Array.isArray(catRes) ? catRes : [];
        if (aggiornaMappaCatalogo(misRes)) {
            for (const m of misRes) {
                if (m.attiva !== false) {
                    const nome = m.dati?.nome || m.nome || `Misura #${m.id}`;
                    misureAttive.push({ id: String(m.id), nome });
                }
            }
        }
    } catch {
        categorie = [];
    }

    const risolviMisureMancanti = async dati => {
        const mancanti = [...new Set(
            (dati || [])
                .map(item => String(item?.conteggio?.misuraId ?? ''))
                .filter(id => id && !misureMap.has(id) && !misureNonTrovate.has(id))
        )];
        if (mancanti.length === 0) return [];
        const nonRisolte = [];
        let indice = 0;
        const worker = async () => {
            while (indice < mancanti.length) {
                const id = mancanti[indice++];
                try {
                    const m = await api(`/api/catalogo/misure/${encodeURIComponent(id)}`);
                    if (m) {
                        misureMap.set(id, formattaEtichettaMisura(m, id));
                    } else {
                        nonRisolte.push(id);
                    }
                } catch (err) {
                    if (err?.stato === 404) {
                        misureNonTrovate.add(id);
                    } else {
                        nonRisolte.push(id);
                    }
                }
            }
        };
        await Promise.all(
            Array.from({ length: Math.min(MASSIMA_CONCORRENZA_MISURE, mancanti.length) }, () => worker())
        );
        return nonRisolte;
    };

    const formattaMisura = id => misureMap.get(String(id)) || `Misura #${id}`;

    const formattaOpt = optId => {
        const chiave = String(optId ?? '');
        const op = optRubrica.get(chiave);
        if (!op) {
            return { principale: `OPT (${chiave})`, dettaglio: '' };
        }
        const partiDettaglio = [];
        if (op.riferimento && (op.omonimo || op.stato === 'REVOCATO')) {
            partiDettaglio.push(op.riferimento);
        }
        if (op.stato === 'REVOCATO') {
            partiDettaglio.push('Account revocato');
        }
        if (op.omonimo) {
            partiDettaglio.push(`ID ${op.id}`);
        }
        return {
            principale: op.nome || `OPT (${chiave})`,
            dettaglio: partiDettaglio.join(' · ')
        };
    };

    const creaCellaTesto = (etichetta, valore, inGrassetto = false, sottotitolo = '') => {
        const cell = document.createElement('div');
        cell.className = 'account-cell';

        const labelEl = document.createElement('span');
        labelEl.className = 'cell-label';
        labelEl.textContent = etichetta;

        const valEl = document.createElement(inGrassetto ? 'strong' : 'span');
        valEl.textContent = String(valore ?? '');

        cell.append(labelEl, valEl);
        if (sottotitolo) {
            const subEl = document.createElement('p');
            subEl.className = 'picus-meta';
            subEl.textContent = sottotitolo;
            cell.append(subEl);
        }
        return cell;
    };

    const nodoAvvertenza = mostraOpt => document.getElementById(mostraOpt ? 'riepilogo-opt-avvertenza' : 'conteggi-avvertenza');

    const renderRigheConteggi = (listaEl, dati, mostraOpt, periodo, avvisiFallback = {}) => {
        const righe = (dati || []).map(item => {
            const art = document.createElement('article');
            art.className = 'account-row';
            const c = item.conteggio;
            const labelVoce = categorie.find(cat => cat.codice === c.voce)?.descrizione || c.voce;

            if (mostraOpt) {
                const optInfo = formattaOpt(item.optId);
                art.append(creaCellaTesto('OPT', optInfo.principale, false, optInfo.dettaglio));
            }
            art.append(
                creaCellaTesto('Misura', formattaMisura(c.misuraId), true),
                creaCellaTesto('Voce', labelVoce, false),
                creaCellaTesto('Conteggio dichiarato', String(c.valore ?? ''), true)
            );
            return art;
        });
        renderList(listaEl, righe, {
            columns: mostraOpt ? ['OPT', 'Misura', 'Voce', 'Conteggio dichiarato'] : ['Misura', 'Voce', 'Conteggio dichiarato'],
            empty: 'Nessun conteggio registrato per il periodo selezionato.'
        });
        let notaTesto = `${descriviPeriodo(periodo)} · Conteggi dichiarati: le categorie sovrapponibili restano distinte (non indicano persone uniche); lo 0 è un valore dichiarato, le voci non dichiarate sono assenti.`;
        if (avvisiFallback.catalogoNonAggiornato || (avvisiFallback.misureNonRisolte?.length ?? 0) > 0) {
            const elenco = avvisiFallback.misureNonRisolte?.length
                ? ` (${avvisiFallback.misureNonRisolte.map(id => `#${id}`).join(', ')})`
                : '';
            notaTesto += ` Avviso: alcuni nomi misura non sono stati aggiornati per un errore temporaneo${elenco}; riprova la consultazione per aggiornare.`;
        }
        const avvertenza = nodoAvvertenza(mostraOpt);
        if (avvertenza) {
            avvertenza.hidden = false;
            avvertenza.textContent = notaTesto;
        }
    };

    const collegaConsultazione = (form, lista, esito, riprova, mostraOpt) => {
        const submitBtn = form.querySelector?.('button[type="submit"]');
        let progressivo = 0;
        let ultimoPeriodo;
        let filtroInModifica = false;

        const pulisciConsultazione = () => {
            esito.textContent = '';
            riprova.hidden = true;
            lista.hidden = true;
            const avvertenza = nodoAvvertenza(mostraOpt);
            if (avvertenza) {
                avvertenza.hidden = true;
                avvertenza.textContent = '';
            }
        };

        const consulta = async periodo => {
            const richiesta = ++progressivo;
            ultimoPeriodo = periodo;
            filtroInModifica = false;
            pulisciConsultazione();

            const { anno, trimestre, annoFine, trimestreFine } = periodo;
            const params = new URLSearchParams({ anno, trimestre });
            if (!mostraOpt) params.set('optId', utenteId);
            try {
                aggiungiFinePeriodo(params, annoFine, trimestreFine, anno, trimestre);
            } catch (err) {
                esito.textContent = err.message;
                return;
            }

            if (submitBtn) submitBtn.disabled = true;
            try {
                let catalogoNonAggiornato = false;
                const [dati, misRes, rubricaRes] = await Promise.all([
                    api(`/api/monitoraggio/conteggi-opt?${params}`),
                    fetchCatalogoArea().catch(() => {
                        catalogoNonAggiornato = true;
                        return null;
                    }),
                    mostraOpt ? api('/api/utenze/rubrica?ruolo=OPT').catch(() => null) : Promise.resolve(null)
                ]);
                if (richiesta !== progressivo) return;
                aggiornaMappaCatalogo(misRes);
                if (mostraOpt) optRubrica = leggiOptDaRubrica(rubricaRes);
                const misureNonRisolte = await risolviMisureMancanti(dati);
                if (richiesta !== progressivo) return;
                renderRigheConteggi(lista, dati, mostraOpt, periodo, { catalogoNonAggiornato, misureNonRisolte });
                lista.hidden = false;
            } catch (err) {
                if (richiesta !== progressivo) return;
                esito.textContent = err.message;
                riprova.hidden = false;
            } finally {
                if (submitBtn && richiesta === progressivo) submitBtn.disabled = false;
            }
        };

        form.addEventListener('submit', event => {
            event.preventDefault();
            return consulta({
                anno: form.elements.anno.value,
                trimestre: form.elements.trimestre.value,
                annoFine: form.elements.annoFine.value,
                trimestreFine: form.elements.trimestreFine.value
            });
        });
        form.addEventListener('input', () => {
            progressivo++;
            ultimoPeriodo = undefined;
            filtroInModifica = true;
            if (submitBtn) submitBtn.disabled = false;
            pulisciConsultazione();
        });
        riprova.addEventListener('click', () => {
            if (!riprova.hidden && ultimoPeriodo) return consulta(ultimoPeriodo);
        });

        return (anno, trimestre) => {
            if (filtroInModifica) return;
            if (ultimoPeriodo) return consulta(ultimoPeriodo);
            form.elements.anno.value = anno;
            form.elements.trimestre.value = trimestre;
            form.elements.annoFine.value = '';
            form.elements.trimestreFine.value = '';
            return consulta({ anno, trimestre });
        };
    };

    if (area === 'opt') {
        const form = document.getElementById('conteggi');
        const contenitoreVoci = document.getElementById('voci-conteggi');
        const btnAggiungi = document.getElementById('altra-voce-opt');
        const selectMisura = form?.querySelector('select[name="misuraId"]');

        if (selectMisura && misureAttive.length > 0 && selectMisura.options.length === 0) {
            for (const { id, nome } of misureAttive) {
                const opt = document.createElement('option');
                opt.value = id;
                opt.textContent = nome;
                selectMisura.append(opt);
            }
        }

        const aggiornaRimozione = () => {
            const ultimaVoce = contenitoreVoci.childElementCount === 1;
            for (const pulsante of contenitoreVoci.querySelectorAll('.rimuovi-voce')) {
                pulsante.hidden = ultimaVoce;
            }
        };

        const aggiungiRigaVoce = () => {
            if (!contenitoreVoci) return;
            const riga = document.createElement('div');
            riga.className = 'count-row';

            const labelVoce = document.createElement('label');
            labelVoce.className = 'campo';
            const titoloVoce = document.createElement('span');
            titoloVoce.textContent = 'Voce';
            const select = document.createElement('select');
            select.className = 'form-control';
            select.name = 'voce';
            select.required = true;
            for (const cat of categorie) {
                const opt = document.createElement('option');
                opt.value = cat.codice;
                opt.textContent = cat.descrizione;
                select.append(opt);
            }
            labelVoce.append(titoloVoce, select);

            const labelVal = document.createElement('label');
            labelVal.className = 'campo';
            const titoloValore = document.createElement('span');
            titoloValore.textContent = 'Conteggio';
            const input = document.createElement('input');
            input.className = 'form-control';
            input.type = 'text';
            input.name = 'valore';
            input.inputMode = 'numeric';
            input.pattern = '[0-9]+';
            input.required = true;
            labelVal.append(titoloValore, input);

            const rimuovi = document.createElement('button');
            rimuovi.type = 'button';
            rimuovi.className = 'btn btn-secondary btn-sm rimuovi-voce';
            rimuovi.textContent = 'Rimuovi';
            rimuovi.setAttribute('aria-label', 'Rimuovi voce');
            rimuovi.addEventListener('click', () => {
                const vicina = riga.nextElementSibling ?? riga.previousElementSibling;
                if (!vicina) return;
                riga.remove();
                aggiornaRimozione();
                vicina.querySelector('select[name="voce"]').focus();
            });

            riga.append(labelVoce, labelVal, rimuovi);
            contenitoreVoci.append(riga);
            aggiornaRimozione();
            return select;
        };

        if (contenitoreVoci) {
            contenitoreVoci.replaceChildren();
            aggiungiRigaVoce();
        }
        btnAggiungi?.addEventListener('click', () => aggiungiRigaVoce()?.focus());

        const aggiornaListaOpt = collegaConsultazione(
            document.getElementById('filtro-conteggi-opt'),
            document.getElementById('conteggi-lista'),
            document.getElementById('conteggi-lettura-esito'),
            document.getElementById('conteggi-riprova'),
            false
        );

        form?.addEventListener('submit', async event => {
            event.preventDefault();
            const esito = form.querySelector('.picus-esito');
            const submitBtn = form.querySelector('button[type="submit"]');
            esito.textContent = '';

            const anno = Number(form.elements.anno.value);
            const trimestre = Number(form.elements.trimestre.value);
            const misuraId = Number(form.elements.misuraId.value);
            submitBtn.disabled = true;
            try {
                const righe = Array.from(contenitoreVoci.querySelectorAll('.count-row')).map(r => ({
                    misuraId,
                    voce: r.querySelector('select[name="voce"]').value,
                    valore: valoreConteggio(r.querySelector('input[name="valore"]').value)
                }));
                await api('/api/monitoraggio/conteggi-opt', {
                    method: 'POST',
                    body: { anno, trimestre, voci: righe }
                });
            } catch (err) {
                const dettagliCampi = Object.values(err.campi || {}).join(' ');
                esito.textContent = dettagliCampi ? `${err.message} ${dettagliCampi}` : err.message;
                return;
            } finally {
                submitBtn.disabled = false;
            }
            esito.textContent = 'Conteggi trimestrali salvati correttamente.';
            await aggiornaListaOpt(anno, trimestre);
        });
    }

    if (area === 'banco') {
        collegaConsultazione(
            document.getElementById('riepilogo-opt'),
            document.getElementById('riepilogo-opt-lista'),
            document.getElementById('riepilogo-opt-esito'),
            document.getElementById('riepilogo-opt-riprova'),
            true
        );
    }
}
