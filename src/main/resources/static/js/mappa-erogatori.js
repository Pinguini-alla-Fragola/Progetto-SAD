import { geoMercator, geoPath } from './vendor/d3-geo.js';

export const SVG_NS = 'http://www.w3.org/2000/svg';
export const URL_CONFINI_CAMPANIA = '/dati/comuni-campania-istat-2026.json';
export const VIEWBOX_COMPLETO = { x: 0, y: 0, width: 800, height: 600 };

const ALIAS_STORICI_A_CATALOGO = new Map([
    ['063049', 'napoli'],
    ['063059', 'portici'],
    ['063060', 'pozzuoli']
]);

export function normalizzaChiaveComune(codice) {
    if (!codice) return '';
    const pulito = String(codice).trim();
    return ALIAS_STORICI_A_CATALOGO.get(pulito) ?? pulito;
}

export function formattaTerritorio(props) {
    if (!props?.provincia) return '';
    const prefisso = props.cittaMetropolitana ? 'Città metropolitana di ' : 'Provincia di ';
    return props.siglaProvincia
        ? `${prefisso}${props.provincia} (${props.siglaProvincia})`
        : `${prefisso}${props.provincia}`;
}

export function classificaComuneSuMappa(voce) {
    if (!voce) {
        return {
            classeCss: 'comune-forma',
            statoTesto: 'Comune senza erogatori compatibili',
            haConfermato: false
        };
    }
    const haConfermato = voce.confermati > 0;
    const classi = ['comune-forma', 'comune-interattivo'];
    if (voce.isRichiesta) {
        classi.push('comune-richiesta');
        if (voce.locali.length > 0) classi.push('comune-stesso-con-candidati');
    } else if (voce.locali.length > 0) {
        const soloDeclinati = voce.nonDisponibili === voce.locali.length;
        classi.push(soloDeclinati ? 'comune-limitrofo-non-disponibile' : 'comune-limitrofo-con-candidati');
    }
    if (haConfermato) {
        classi.push('comune-confermato');
    }

    const parti = [];
    if (voce.isRichiesta) {
        parti.push(voce.locali.length > 0 ? 'Comune della richiesta (stesso comune)' : 'Comune della richiesta (nessun erogatore locale)');
    } else {
        parti.push(voce.nonDisponibili === voce.locali.length
            ? 'Comune limitrofo (indisponibilità già comunicata)'
            : 'Comune limitrofo compatibile');
    }
    if (haConfermato) {
        parti.push('disponibilità confermata');
    } else if (voce.inAttesa > 0) {
        parti.push('riscontro in attesa');
    } else if (voce.daVerificare > 0) {
        parti.push('compatibilità territoriale da verificare');
    }
    return {
        classeCss: classi.join(' '),
        statoTesto: parti.join(' · '),
        haConfermato
    };
}

export function riepilogaTerritoriCandidati({
    comuneCodice = '',
    comuneIstat = '',
    comuneNome = '',
    comuneTerritorio = '',
    candidati = [],
    contatti = []
} = {}) {
    const ultimi = new Map(contatti.map(c => [c.erogatoreId + '|' + c.ambito, c]));
    const chiaveRichiesta = normalizzaChiaveComune(comuneCodice || comuneIstat);
    const mappaPerChiave = new Map();

    const assicuratiVoce = (chiave, base = {}) => {
        if (!mappaPerChiave.has(chiave)) {
            mappaPerChiave.set(chiave, {
                chiave,
                codiceIstat: base.codiceIstat || '',
                nome: base.nome || chiave,
                territorio: base.territorio || '',
                isRichiesta: chiave === chiaveRichiesta,
                ambitoPrincipale: chiave === chiaveRichiesta ? 'STESSO_COMUNE' : 'COMUNE_LIMITROFO',
                locali: [],
                confermati: 0,
                inAttesa: 0,
                daVerificare: 0,
                nonDisponibili: 0
            });
        }
        return mappaPerChiave.get(chiave);
    };

    if (chiaveRichiesta) {
        assicuratiVoce(chiaveRichiesta, {
            codiceIstat: comuneIstat || '',
            nome: comuneNome || chiaveRichiesta,
            territorio: comuneTerritorio || ''
        });
    }

    let localiTotali = 0;
    let remotiTotali = 0;
    let confermatiLocaliTotali = 0;

    for (const candidato of candidati) {
        const erogatore = candidato?.erogatore;
        if (!erogatore) continue;
        const ultimo = ultimi.get(erogatore.accountId + '|' + erogatore.ambito) || null;
        if (erogatore.ambito === 'CENTRO_PICUS_A_DISTANZA') {
            remotiTotali++;
            continue;
        }
        localiTotali++;
        const chiave = normalizzaChiaveComune(erogatore.comuneCodice);
        const voce = assicuratiVoce(chiave, {
            nome: chiave === chiaveRichiesta ? (comuneNome || chiave) : chiave,
            territorio: chiave === chiaveRichiesta ? (comuneTerritorio || '') : ''
        });
        voce.locali.push({ candidato, ultimo });
        if (ultimo?.esito === 'DISPONIBILE') {
            voce.confermati++;
            confermatiLocaliTotali++;
        } else if (ultimo?.esito === 'IN_ATTESA') {
            voce.inAttesa++;
        } else if (ultimo?.esito === 'NON_DISPONIBILE') {
            voce.nonDisponibili++;
        } else {
            voce.daVerificare++;
        }
    }

    const comuniRilevanti = Array.from(mappaPerChiave.values()).sort((a, b) => {
        if (a.isRichiesta !== b.isRichiesta) return a.isRichiesta ? -1 : 1;
        if (b.confermati !== a.confermati) return b.confermati - a.confermati;
        const attiviA = a.locali.length - a.nonDisponibili;
        const attiviB = b.locali.length - b.nonDisponibili;
        if (attiviB !== attiviA) return attiviB - attiviA;
        if (b.locali.length !== a.locali.length) return b.locali.length - a.locali.length;
        return a.nome.localeCompare(b.nome, 'it', { sensitivity: 'base' });
    });

    return {
        chiaveRichiesta,
        comuniRilevanti,
        mappaPerChiave,
        localiTotali,
        remotiTotali,
        confermatiLocaliTotali
    };
}

function descriviConteggiVoce(voce) {
    const n = voce.locali.length;
    if (n === 0) return '0 erogatori locali compatibili';
    const parti = [n === 1 ? '1 erogatore compatibile' : `${n} erogatori compatibili`];
    if (voce.confermati > 0) {
        parti.push(voce.confermati === 1 ? '1 disponibilità confermata' : `${voce.confermati} disponibilità confermate`);
    } else if (voce.inAttesa > 0) {
        parti.push(`${voce.inAttesa} in attesa di riscontro`);
    } else if (voce.daVerificare > 0) {
        parti.push('compatibilità territoriale (da contattare)');
    } else if (voce.nonDisponibili > 0) {
        parti.push(`${voce.nonDisponibili} non disponibili`);
    }
    return parti.join(' · ');
}

function calcolaViewBoxZona(boundsList) {
    if (!boundsList.length) return { ...VIEWBOX_COMPLETO };
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    for (const [[x0, y0], [x1, y1]] of boundsList) {
        if (!Number.isFinite(x0) || !Number.isFinite(y0) || !Number.isFinite(x1) || !Number.isFinite(y1)) continue;
        minX = Math.min(minX, x0);
        minY = Math.min(minY, y0);
        maxX = Math.max(maxX, x1);
        maxY = Math.max(maxY, y1);
    }
    if (!Number.isFinite(minX)) return { ...VIEWBOX_COMPLETO };

    const cx = (minX + maxX) / 2;
    const cy = (minY + maxY) / 2;
    let spanW = Math.max((maxX - minX) * 1.9, 150);
    let spanH = Math.max((maxY - minY) * 1.9, spanW * 0.75);
    spanW = Math.max(spanW, spanH * (4 / 3));
    spanH = spanW * 0.75;
    if (spanW >= VIEWBOX_COMPLETO.width) return { ...VIEWBOX_COMPLETO };

    const x = Math.max(0, Math.min(VIEWBOX_COMPLETO.width - spanW, cx - spanW / 2));
    const y = Math.max(0, Math.min(VIEWBOX_COMPLETO.height - spanH, cy - spanH / 2));
    return { x, y, width: spanW, height: spanH };
}

function formatViewBox(vb) {
    return `${vb.x.toFixed(1)} ${vb.y.toFixed(1)} ${vb.width.toFixed(1)} ${vb.height.toFixed(1)}`;
}

function elementoHtml(tag, testo, classe) {
    const el = document.createElement(tag);
    if (testo != null) el.textContent = testo;
    if (classe) el.className = classe;
    return el;
}

export async function avviaMappaErogatori({
    sezione,
    candidati = [],
    contatti = [],
    fetchFn = typeof fetch === 'function' ? (...args) => fetch(...args) : null,
    onSelezionaComune = () => {}
} = {}) {
    if (!sezione) return null;
    sezione.hidden = false;

    const riepilogo = riepilogaTerritoriCandidati({
        comuneCodice: sezione.dataset?.comuneCodice || '',
        comuneIstat: sezione.dataset?.comuneIstat || '',
        comuneNome: sezione.dataset?.comuneNome || '',
        comuneTerritorio: sezione.dataset?.comuneTerritorio || '',
        candidati,
        contatti
    });

    const sottotitolo = document.getElementById('mappa-sottotitolo');
    const quadro = document.getElementById('mappa-quadro');
    const controlli = document.getElementById('mappa-controlli');
    const svg = document.getElementById('mappa-campania-svg');
    const stratoComuni = document.getElementById('mappa-strato-comuni');
    const stratoEvidenza = document.getElementById('mappa-strato-evidenza');
    const tooltip = document.getElementById('mappa-tooltip');
    const descrizioneSr = document.getElementById('mappa-descrizione-sr');
    const erroreEl = document.getElementById('mappa-errore');
    const statoVuoto = document.getElementById('mappa-stato-vuoto');
    const legendaEl = document.getElementById('mappa-legenda');
    const elencoComuniEl = document.getElementById('elenco-territoriale-comuni');
    const filtroAttivoEl = document.getElementById('filtro-comune-attivo');

    const voceRichiesta = riepilogo.mappaPerChiave.get(riepilogo.chiaveRichiesta);
    if (sottotitolo) {
        const desc = [voceRichiesta?.nome || riepilogo.chiaveRichiesta, voceRichiesta?.territorio].filter(Boolean).join(' · ');
        sottotitolo.textContent = desc ? `Comune della richiesta: ${desc}` : '';
    }

    if (statoVuoto) {
        if (riepilogo.localiTotali === 0) {
            statoVuoto.hidden = false;
            statoVuoto.textContent = riepilogo.remotiTotali > 0
                ? 'Nessun erogatore locale compatibile nel comune della richiesta o nei comuni limitrofi. Le opzioni del Centro PICUS a distanza restano consultabili nell’elenco sottostante.'
                : 'Nessun erogatore locale o a distanza compatibile al momento per questa richiesta.';
        } else {
            statoVuoto.hidden = true;
            statoVuoto.textContent = '';
        }
    }

    if (legendaEl) {
        const voci = [
            { classe: 'mappa-campione-richiesta', testo: 'Comune della richiesta' },
            { classe: 'mappa-campione-stesso', testo: 'Erogatori nello stesso comune' },
            { classe: 'mappa-campione-limitrofo', testo: 'Comune limitrofo compatibile (trama obliqua)' },
            { classe: 'mappa-campione-confermato', testo: 'Disponibilità confermata per la richiesta (trama a punti)' }
        ];
        if (riepilogo.comuniRilevanti.some(v => !v.isRichiesta && v.locali.length > 0 && v.nonDisponibili === v.locali.length)) {
            voci.push({ classe: 'mappa-campione-non-disponibile', testo: 'Comune limitrofo con indisponibilità comunicata' });
        }
        legendaEl.replaceChildren(...voci.map(v => {
            const li = elementoHtml('li', null, 'mappa-legenda-voce');
            li.append(elementoHtml('span', null, 'mappa-campione ' + v.classe), document.createTextNode(v.testo));
            return li;
        }));
    }

    let comuneSelezionato = null;
    const poligoniPerChiave = new Map();
    const bottoniElenco = new Map();
    let viewBoxZona = { ...VIEWBOX_COMPLETO };
    let viewBoxCorrente = { ...VIEWBOX_COMPLETO };

    const aggiornaStatoVisualeSelezione = () => {
        for (const [chiave, pathEl] of poligoniPerChiave) {
            const selezionato = chiave === comuneSelezionato;
            pathEl.setAttribute('aria-pressed', selezionato ? 'true' : 'false');
            pathEl.classList?.toggle?.('comune-selezionato', selezionato);
        }
        for (const [chiave, btn] of bottoniElenco) {
            btn.setAttribute('aria-pressed', chiave === (comuneSelezionato ?? '') ? 'true' : 'false');
        }
        if (filtroAttivoEl) {
            if (!comuneSelezionato) {
                filtroAttivoEl.hidden = true;
                filtroAttivoEl.replaceChildren();
            } else {
                const voce = riepilogo.mappaPerChiave.get(comuneSelezionato);
                const label = voce ? `${voce.nome}${voce.territorio ? ' · ' + voce.territorio : ''}` : comuneSelezionato;
                const ruolo = voce?.isRichiesta ? 'Stesso comune' : 'Comune limitrofo';
                const testi = elementoHtml('span', `Filtro attivo su ${label} (${ruolo} · ${descriviConteggiVoce(voce)}).`);
                const resetBtn = elementoHtml('button', 'Mostra tutti i candidati', 'btn btn-secondary btn-sm');
                resetBtn.type = 'button';
                resetBtn.addEventListener('click', () => selezionaComune(null));
                filtroAttivoEl.replaceChildren(testi, resetBtn);
                filtroAttivoEl.hidden = false;
            }
        }
    };

    const selezionaComune = (chiave, notifica = true) => {
        const normalizzata = chiave ? normalizzaChiaveComune(chiave) : null;
        comuneSelezionato = normalizzata && comuneSelezionato === normalizzata ? null : normalizzata;
        aggiornaStatoVisualeSelezione();
        if (notifica) onSelezionaComune(comuneSelezionato);
    };

    const mostraTooltip = voce => {
        if (!tooltip || !voce) return;
        const info = classificaComuneSuMappa(voce);
        tooltip.textContent = `${voce.nome}${voce.territorio ? ' (' + voce.territorio + ')' : ''} — ${info.statoTesto} · ${descriviConteggiVoce(voce)}`;
        tooltip.hidden = false;
    };

    const costruisciElencoTastiera = () => {
        if (!elencoComuniEl) return;
        bottoniElenco.clear();
        const pulsanti = [];

        const btnTutti = elementoHtml('button', null, 'mappa-comune-btn');
        btnTutti.type = 'button';
        btnTutti.dataset.comuneChiave = '';
        btnTutti.append(
            elementoHtml('span', `Tutti i candidati (${candidati.length})`, 'mappa-comune-titolo'),
            elementoHtml('span', riepilogo.remotiTotali > 0
                ? `${riepilogo.localiTotali} locali · ${riepilogo.remotiTotali} a distanza`
                : `${riepilogo.localiTotali} locali`, 'picus-meta')
        );
        btnTutti.addEventListener('click', () => selezionaComune(null));
        bottoniElenco.set('', btnTutti);
        pulsanti.push(btnTutti);

        for (const voce of riepilogo.comuniRilevanti) {
            const btn = elementoHtml('button', null, 'mappa-comune-btn');
            btn.type = 'button';
            btn.dataset.comuneChiave = voce.chiave;
            const info = classificaComuneSuMappa(voce);
            const titolo = [
                voce.nome,
                voce.territorio ? voce.territorio : '',
                voce.isRichiesta ? 'Comune della richiesta' : 'Comune limitrofo'
            ].filter(Boolean).join(' · ');
            const dettaglio = `${info.haConfermato ? 'Disponibilità confermata · ' : ''}${descriviConteggiVoce(voce)}`;
            btn.append(
                elementoHtml('span', titolo, 'mappa-comune-titolo'),
                elementoHtml('span', dettaglio, 'picus-meta')
            );
            btn.addEventListener('click', () => selezionaComune(voce.chiave));
            bottoniElenco.set(voce.chiave, btn);
            pulsanti.push(btn);
        }

        elencoComuniEl.onkeydown = event => {
            if (!['ArrowDown', 'ArrowUp', 'Home', 'End', 'Escape'].includes(event.key)) return;
            if (event.key === 'Escape') {
                selezionaComune(null);
                return;
            }
            const lista = Array.from(elencoComuniEl.querySelectorAll('button'));
            const corrente = lista.indexOf(document.activeElement);
            if (corrente < 0 || !lista.length) return;
            event.preventDefault();
            let prossimo = corrente;
            if (event.key === 'ArrowDown') prossimo = (corrente + 1) % lista.length;
            else if (event.key === 'ArrowUp') prossimo = (corrente - 1 + lista.length) % lista.length;
            else if (event.key === 'Home') prossimo = 0;
            else if (event.key === 'End') prossimo = lista.length - 1;
            lista[prossimo]?.focus?.();
        };

        elencoComuniEl.replaceChildren(...pulsanti);
        aggiornaStatoVisualeSelezione();
    };

    const applicaViewBox = (vb, attivaAzione) => {
        viewBoxCorrente = {
            x: Math.max(0, Math.min(VIEWBOX_COMPLETO.width - vb.width, vb.x)),
            y: Math.max(0, Math.min(VIEWBOX_COMPLETO.height - vb.height, vb.y)),
            width: Math.min(VIEWBOX_COMPLETO.width, Math.max(60, vb.width)),
            height: Math.min(VIEWBOX_COMPLETO.height, Math.max(45, vb.height))
        };
        svg?.setAttribute('viewBox', formatViewBox(viewBoxCorrente));
        if (controlli) {
            for (const b of controlli.querySelectorAll('[data-mappa-azione="zona"], [data-mappa-azione="regione"]')) {
                b.setAttribute('aria-pressed', b.dataset.mappaAzione === attivaAzione ? 'true' : 'false');
            }
        }
    };

    const scalaViewBox = fattore => {
        const w = Math.min(VIEWBOX_COMPLETO.width, Math.max(80, viewBoxCorrente.width * fattore));
        const h = w * 0.75;
        const cx = viewBoxCorrente.x + viewBoxCorrente.width / 2;
        const cy = viewBoxCorrente.y + viewBoxCorrente.height / 2;
        applicaViewBox({ x: cx - w / 2, y: cy - h / 2, width: w, height: h }, '');
    };

    costruisciElencoTastiera();

    if (controlli) {
        for (const btn of controlli.querySelectorAll('button[data-mappa-azione]')) {
            btn.addEventListener('click', () => {
                const azione = btn.dataset.mappaAzione;
                if (azione === 'zona') applicaViewBox(viewBoxZona, 'zona');
                else if (azione === 'regione') applicaViewBox(VIEWBOX_COMPLETO, 'regione');
                else if (azione === 'zoom-in') scalaViewBox(0.72);
                else if (azione === 'zoom-out') scalaViewBox(1.38);
            });
        }
    }

    if (!svg || !stratoComuni || !stratoEvidenza || !fetchFn) {
        if (quadro) quadro.hidden = true;
        if (controlli) controlli.hidden = true;
        if (erroreEl) {
            erroreEl.textContent = 'La cartografia non è disponibile al momento. L’elenco territoriale e le opzioni di coordinamento restano utilizzabili qui sotto.';
            erroreEl.hidden = false;
        }
        return { selezionaComune, getStato: () => riepilogo };
    }

    try {
        const risposta = await fetchFn(URL_CONFINI_CAMPANIA, { headers: { Accept: 'application/json' } });
        if (!risposta?.ok) {
            throw new Error('HTTP ' + (risposta?.status ?? 'errore'));
        }
        const geojson = await risposta.json();
        if (!geojson || geojson.type !== 'FeatureCollection' || !Array.isArray(geojson.features)) {
            throw new Error('GeoJSON non valido');
        }

        const proiezione = geoMercator().fitExtent(
            [[16, 16], [VIEWBOX_COMPLETO.width - 16, VIEWBOX_COMPLETO.height - 16]],
            geojson
        );
        const generatorePath = geoPath(proiezione);

        const basePaths = [];
        const evidenzaPaths = [];
        const boundsEvidenza = [];
        poligoniPerChiave.clear();

        for (const feature of geojson.features) {
            const props = feature?.properties || {};
            const chiave = normalizzaChiaveComune(props.codiceCatalogo || props.codiceIstat);
            const voce = riepilogo.mappaPerChiave.get(chiave);
            if (voce) {
                voce.nome = props.nome || voce.nome;
                voce.codiceIstat = props.codiceIstat || voce.codiceIstat;
                voce.territorio = formattaTerritorio(props) || voce.territorio;
            }
            const d = generatorePath(feature);
            if (!d) continue;

            const pathEl = document.createElementNS(SVG_NS, 'path');
            pathEl.setAttribute('d', d);
            pathEl.setAttribute('data-codice-istat', props.codiceIstat || '');
            pathEl.setAttribute('data-codice-catalogo', chiave);

            if (!voce) {
                pathEl.setAttribute('class', 'comune-forma');
                pathEl.setAttribute('aria-hidden', 'true');
                basePaths.push(pathEl);
            } else {
                const info = classificaComuneSuMappa(voce);
                pathEl.setAttribute('class', info.classeCss);
                pathEl.setAttribute('tabindex', '0');
                pathEl.setAttribute('role', 'button');
                pathEl.setAttribute('aria-pressed', 'false');
                pathEl.setAttribute(
                    'aria-label',
                    `${voce.nome}${voce.territorio ? ', ' + voce.territorio : ''} · ${info.statoTesto} · ${descriviConteggiVoce(voce)}`
                );
                pathEl.addEventListener('click', () => {
                    mostraTooltip(voce);
                    selezionaComune(voce.chiave);
                });
                pathEl.addEventListener('keydown', event => {
                    if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        mostraTooltip(voce);
                        selezionaComune(voce.chiave);
                    } else if (event.key === 'Escape') {
                        event.preventDefault();
                        selezionaComune(null);
                    }
                });
                pathEl.addEventListener('mouseenter', () => mostraTooltip(voce));
                pathEl.addEventListener('focus', () => mostraTooltip(voce));
                poligoniPerChiave.set(voce.chiave, pathEl);
                evidenzaPaths.push(pathEl);
                boundsEvidenza.push(generatorePath.bounds(feature));
            }
        }

        stratoComuni.replaceChildren(...basePaths);
        stratoEvidenza.replaceChildren(...evidenzaPaths);
        costruisciElencoTastiera();

        viewBoxZona = calcolaViewBoxZona(boundsEvidenza);
        applicaViewBox(boundsEvidenza.length ? viewBoxZona : VIEWBOX_COMPLETO, boundsEvidenza.length ? 'zona' : 'regione');
        if (quadro) quadro.hidden = false;
        if (controlli) controlli.hidden = false;
        if (erroreEl) {
            erroreEl.hidden = true;
            erroreEl.textContent = '';
        }
        if (descrizioneSr) {
            descrizioneSr.textContent = `Mappa dei ${geojson.features.length} comuni campani. Evidenziati ${evidenzaPaths.length} comuni per questa richiesta.`;
        }
    } catch {
        if (quadro) quadro.hidden = true;
        if (controlli) controlli.hidden = true;
        if (erroreEl) {
            erroreEl.textContent = 'La cartografia non è disponibile al momento. L’elenco territoriale e le opzioni di coordinamento restano utilizzabili qui sotto.';
            erroreEl.hidden = false;
        }
    }

    return { selezionaComune, getStato: () => riepilogo };
}
