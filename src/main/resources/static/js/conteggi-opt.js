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

export function aggiungiFinePeriodo(params, annoFine, trimestreFine) {
    if (Boolean(annoFine) !== Boolean(trimestreFine)) {
        throw new Error("Indica sia l'anno sia il trimestre finale.");
    }
    if (annoFine) {
        params.set('annoFine', annoFine);
        params.set('trimestreFine', trimestreFine);
    }
}

export async function initConteggiOpt(area) {
    const utenteId = document.body.dataset.utente;
    let categorie = [];
    let misureMap = new Map();

    try {
        const [catRes, misRes] = await Promise.all([
            api('/api/monitoraggio/categorie'),
            api('/api/catalogo/misure').catch(() => [])
        ]);
        categorie = Array.isArray(catRes) ? catRes : [];
        if (Array.isArray(misRes)) {
            for (const m of misRes) misureMap.set(String(m.id), m.dati?.nome || m.nome || `Misura #${m.id}`);
        }
    } catch {
        categorie = [];
    }

    const formattaMisura = id => misureMap.get(String(id)) || `Misura #${id}`;

    const creaCellaTesto = (etichetta, valore, inGrassetto = false) => {
        const cell = document.createElement('div');
        cell.className = 'account-cell';

        const labelEl = document.createElement('span');
        labelEl.className = 'cell-label';
        labelEl.textContent = etichetta;

        const valEl = document.createElement(inGrassetto ? 'strong' : 'span');
        valEl.textContent = String(valore ?? '');

        cell.append(labelEl, valEl);
        return cell;
    };

    const renderRigheConteggi = (listaEl, dati, mostraOpt) => {
        const righe = (dati || []).map(item => {
            const art = document.createElement('article');
            art.className = 'account-row';
            const c = item.conteggio;
            const labelVoce = categorie.find(cat => cat.codice === c.voce)?.descrizione || c.voce;

            if (mostraOpt) {
                art.append(creaCellaTesto('OPT', item.optId, false));
            }
            art.append(
                creaCellaTesto('Misura', formattaMisura(c.misuraId), true),
                creaCellaTesto('Voce', labelVoce, false),
                creaCellaTesto('Conteggio', c.valore, true)
            );
            return art;
        });
        renderList(listaEl, righe, {
            columns: mostraOpt ? ['OPT', 'Misura', 'Voce', 'Conteggio'] : ['Misura', 'Voce', 'Conteggio'],
            empty: 'Nessun conteggio registrato per il periodo selezionato.'
        });
    };

    if (area === 'opt') {
        const form = document.getElementById('conteggi');
        const contenitoreVoci = document.getElementById('voci-conteggi');
        const btnAggiungi = document.getElementById('altra-voce-opt');
        const selectMisura = form?.querySelector('select[name="misuraId"]');

        if (selectMisura && misureMap.size > 0 && selectMisura.options.length === 0) {
            for (const [id, nome] of misureMap.entries()) {
                const opt = document.createElement('option');
                opt.value = id;
                opt.textContent = nome;
                selectMisura.append(opt);
            }
        }

        const aggiungiRigaVoce = () => {
            if (!contenitoreVoci) return;
            const riga = document.createElement('div');
            riga.className = 'count-row';

            const labelVoce = document.createElement('label');
            labelVoce.className = 'campo';
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
            labelVoce.append(select);

            const labelVal = document.createElement('label');
            labelVal.className = 'campo';
            const input = document.createElement('input');
            input.className = 'form-control';
            input.type = 'text';
            input.name = 'valore';
            input.inputMode = 'numeric';
            input.pattern = '[0-9]+';
            input.required = true;
            labelVal.append(input);

            riga.append(labelVoce, labelVal);
            contenitoreVoci.append(riga);
        };

        if (contenitoreVoci) {
            contenitoreVoci.innerHTML = '';
            aggiungiRigaVoce();
        }
        btnAggiungi?.addEventListener('click', aggiungiRigaVoce);

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
                esito.textContent = 'Conteggi trimestrali salvati correttamente.';
                await aggiornaListaOpt(anno, trimestre);
            } catch (err) {
                const dettagliCampi = Object.values(err.campi || {}).join(' ');
                esito.textContent = dettagliCampi ? `${err.message} ${dettagliCampi}` : err.message;
            } finally {
                submitBtn.disabled = false;
            }
        });

        const filtroForm = document.getElementById('filtro-conteggi-opt');
        const listaEl = document.getElementById('conteggi-lista');

        const aggiornaListaOpt = async (anno, trimestre, annoFine, trimestreFine) => {
            const params = new URLSearchParams({ optId: utenteId, anno, trimestre });
            aggiungiFinePeriodo(params, annoFine, trimestreFine);
            const dati = await api(`/api/monitoraggio/conteggi-opt?${params.toString()}`);
            renderRigheConteggi(listaEl, dati, false);
        };

        filtroForm?.addEventListener('submit', async event => {
            event.preventDefault();
            const anno = filtroForm.elements.anno.value;
            const trimestre = filtroForm.elements.trimestre.value;
            const annoFine = filtroForm.elements.annoFine.value;
            const trimestreFine = filtroForm.elements.trimestreFine.value;
            const esito = filtroForm.querySelector('.picus-esito');
            esito.textContent = '';
            try {
                await aggiornaListaOpt(anno, trimestre, annoFine, trimestreFine);
            } catch (err) {
                esito.textContent = err.message;
            }
        });
    }

    if (area === 'banco') {
        const formBanco = document.getElementById('riepilogo-opt');
        const listaBanco = document.getElementById('riepilogo-opt-lista');

        formBanco?.addEventListener('submit', async event => {
            event.preventDefault();
            const esito = formBanco.querySelector('.picus-esito');
            esito.textContent = '';

            const anno = formBanco.elements.anno.value;
            const trimestre = formBanco.elements.trimestre.value;
            const annoFine = formBanco.elements.annoFine.value;
            const trimestreFine = formBanco.elements.trimestreFine.value;

            const params = new URLSearchParams({ anno, trimestre });
            try {
                aggiungiFinePeriodo(params, annoFine, trimestreFine);
                const dati = await api(`/api/monitoraggio/conteggi-opt?${params.toString()}`);
                renderRigheConteggi(listaBanco, dati, true);
            } catch (err) {
                esito.textContent = err.message;
            }
        });
    }
}