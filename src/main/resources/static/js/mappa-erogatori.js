import { geoMercator, geoPath } from './vendor/d3-geo.js';

export const SVG_NS = 'http://www.w3.org/2000/svg';
export const URL_CONFINI_CAMPANIA = '/dati/comuni-campania-istat-2026.json';
export const VIEWBOX_COMPLETO = { x: 0, y: 0, width: 960, height: 540 };

const RAPPORTO_ASPETTO = VIEWBOX_COMPLETO.height / VIEWBOX_COMPLETO.width;
const LARGHEZZA_MIN_VIEWBOX = 24;
const SOGLIA_TRASCINAMENTO_PX = 5;

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

function vincolaViewBox(vb) {
    const width = Math.min(VIEWBOX_COMPLETO.width, Math.max(LARGHEZZA_MIN_VIEWBOX, vb.width));
    const height = width * RAPPORTO_ASPETTO;
    return {
        x: Math.max(0, Math.min(VIEWBOX_COMPLETO.width - width, vb.x)),
        y: Math.max(0, Math.min(VIEWBOX_COMPLETO.height - height, vb.y)),
        width,
        height
    };
}

function metricaViewportSvg(svg, vb) {
    const rect = typeof svg?.getBoundingClientRect === 'function' ? svg.getBoundingClientRect() : null;
    const w = rect?.width > 0 ? rect.width : VIEWBOX_COMPLETO.width;
    const h = rect?.height > 0 ? rect.height : VIEWBOX_COMPLETO.height;
    const left = Number.isFinite(rect?.left) ? rect.left : 0;
    const top = Number.isFinite(rect?.top) ? rect.top : 0;
    const scale = Math.min(w / vb.width, h / vb.height) || 1;
    const offsetX = (w - vb.width * scale) / 2;
    const offsetY = (h - vb.height * scale) / 2;
    return { left, top, scale, offsetX, offsetY };
}

function puntoClientInSvg(svg, clientX, clientY, vb) {
    const m = metricaViewportSvg(svg, vb);
    return {
        x: vb.x + (clientX - m.left - m.offsetX) / m.scale,
        y: vb.y + (clientY - m.top - m.offsetY) / m.scale
    };
}

function calcolaViewBoxZoomSuPunto(svg, nuovaLarghezza, clientX, clientY, ancoraSvg, vbRiferimento) {
    const width = Math.min(VIEWBOX_COMPLETO.width, Math.max(LARGHEZZA_MIN_VIEWBOX, nuovaLarghezza));
    const height = width * RAPPORTO_ASPETTO;
    const m = metricaViewportSvg(svg, { ...vbRiferimento, width, height });
    return vincolaViewBox({
        x: ancoraSvg.x - (clientX - m.left - m.offsetX) / m.scale,
        y: ancoraSvg.y - (clientY - m.top - m.offsetY) / m.scale,
        width,
        height
    });
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
    let spanW = Math.max((maxX - minX) * 1.9, 180);
    let spanH = Math.max((maxY - minY) * 1.9, spanW * RAPPORTO_ASPETTO);
    spanW = Math.max(spanW, spanH / RAPPORTO_ASPETTO);
    spanH = spanW * RAPPORTO_ASPETTO;
    if (spanW >= VIEWBOX_COMPLETO.width) return { ...VIEWBOX_COMPLETO };

    return vincolaViewBox({ x: cx - spanW / 2, y: cy - spanH / 2, width: spanW, height: spanH });
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
    sezione._mappaAbortController?.abort?.();
    const controller = new AbortController();
    sezione._mappaAbortController = controller;
    const { signal } = controller;

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
    let ignoraClickDopoTrascinamento = false;

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
        viewBoxCorrente = vincolaViewBox(vb);
        svg?.setAttribute('viewBox', formatViewBox(viewBoxCorrente));
        if (controlli) {
            for (const b of controlli.querySelectorAll('[data-mappa-azione="zona"], [data-mappa-azione="regione"]')) {
                b.setAttribute('aria-pressed', b.dataset.mappaAzione === attivaAzione ? 'true' : 'false');
            }
        }
    };

    const scalaViewBox = (fattore, clientX, clientY) => {
        const nuovaLarghezza = viewBoxCorrente.width * fattore;
        if (Number.isFinite(clientX) && Number.isFinite(clientY) && svg) {
            const ancora = puntoClientInSvg(svg, clientX, clientY, viewBoxCorrente);
            applicaViewBox(calcolaViewBoxZoomSuPunto(svg, nuovaLarghezza, clientX, clientY, ancora, viewBoxCorrente), '');
            return;
        }
        const w = Math.min(VIEWBOX_COMPLETO.width, Math.max(LARGHEZZA_MIN_VIEWBOX, nuovaLarghezza));
        const h = w * RAPPORTO_ASPETTO;
        const cx = viewBoxCorrente.x + viewBoxCorrente.width / 2;
        const cy = viewBoxCorrente.y + viewBoxCorrente.height / 2;
        applicaViewBox({ x: cx - w / 2, y: cy - h / 2, width: w, height: h }, '');
    };

    const collegaInterazionePuntatore = () => {
        if (!svg) return;
        const puntatori = new Map();
        let statoGesto = null;
        let mossoNelGesto = false;

        const distanzaPuntatori = () => {
            const [a, b] = Array.from(puntatori.values());
            return {
                dist: Math.hypot(b.clientX - a.clientX, b.clientY - a.clientY),
                cx: (a.clientX + b.clientX) / 2,
                cy: (a.clientY + b.clientY) / 2
            };
        };

        const avviaPinch = () => {
            const { dist, cx, cy } = distanzaPuntatori();
            if (dist <= 0) return;
            mossoNelGesto = true;
            svg.classList?.add?.('mappa-trascinamento');
            statoGesto = {
                tipo: 'pinch',
                distanzaIniziale: dist,
                viewBoxIniziale: { ...viewBoxCorrente },
                ancoraSvg: puntoClientInSvg(svg, cx, cy, viewBoxCorrente)
            };
        };

        svg.addEventListener('wheel', event => {
            event.preventDefault();
            const moltiplicatore = event.deltaMode === 1 ? 32 : event.deltaMode === 2 ? 300 : 1;
            const delta = Math.max(-240, Math.min(240, (event.deltaY || 0) * moltiplicatore));
            if (!delta) return;
            const fattore = Math.exp(delta * (event.ctrlKey ? 0.008 : 0.0022));
            scalaViewBox(fattore, event.clientX, event.clientY);
        }, { passive: false, signal });

        svg.addEventListener('pointerdown', event => {
            if (event.pointerType === 'mouse' && event.button !== 0) return;
            ignoraClickDopoTrascinamento = false;
            puntatori.set(event.pointerId, { clientX: event.clientX, clientY: event.clientY });
            if (puntatori.size === 1) {
                mossoNelGesto = false;
                statoGesto = {
                    tipo: 'pan',
                    clientX: event.clientX,
                    clientY: event.clientY,
                    viewBoxIniziale: { ...viewBoxCorrente }
                };
            } else if (puntatori.size === 2) {
                try { svg.setPointerCapture?.(event.pointerId); } catch {}
                avviaPinch();
            }
        }, { signal });

        svg.addEventListener('pointermove', event => {
            if (!puntatori.has(event.pointerId)) return;
            puntatori.set(event.pointerId, { clientX: event.clientX, clientY: event.clientY });
            if (puntatori.size >= 2) {
                if (statoGesto?.tipo !== 'pinch') {
                    avviaPinch();
                    return;
                }
                const { dist, cx, cy } = distanzaPuntatori();
                if (dist <= 0 || statoGesto.distanzaIniziale <= 0) return;
                const nuovaLarghezza = statoGesto.viewBoxIniziale.width * (statoGesto.distanzaIniziale / dist);
                applicaViewBox(
                    calcolaViewBoxZoomSuPunto(svg, nuovaLarghezza, cx, cy, statoGesto.ancoraSvg, statoGesto.viewBoxIniziale),
                    ''
                );
                return;
            }
            if (statoGesto?.tipo === 'pan') {
                const dx = event.clientX - statoGesto.clientX;
                const dy = event.clientY - statoGesto.clientY;
                if (!mossoNelGesto) {
                    if (Math.hypot(dx, dy) < SOGLIA_TRASCINAMENTO_PX) return;
                    mossoNelGesto = true;
                    svg.classList?.add?.('mappa-trascinamento');
                    try { svg.setPointerCapture?.(event.pointerId); } catch {}
                }
                const m = metricaViewportSvg(svg, statoGesto.viewBoxIniziale);
                applicaViewBox({
                    x: statoGesto.viewBoxIniziale.x - dx / m.scale,
                    y: statoGesto.viewBoxIniziale.y - dy / m.scale,
                    width: statoGesto.viewBoxIniziale.width,
                    height: statoGesto.viewBoxIniziale.height
                }, '');
            }
        }, { signal });

        const terminaPuntatore = event => {
            if (!puntatori.has(event.pointerId)) return;
            puntatori.delete(event.pointerId);
            if (puntatori.size === 1) {
                const [rimasto] = puntatori.values();
                statoGesto = {
                    tipo: 'pan',
                    clientX: rimasto.clientX,
                    clientY: rimasto.clientY,
                    viewBoxIniziale: { ...viewBoxCorrente }
                };
                return;
            }
            if (puntatori.size === 0) {
                svg.classList?.remove?.('mappa-trascinamento');
                if (mossoNelGesto) ignoraClickDopoTrascinamento = true;
                mossoNelGesto = false;
                statoGesto = null;
            }
        };

        svg.addEventListener('pointerup', terminaPuntatore, { signal });
        svg.addEventListener('pointercancel', terminaPuntatore, { signal });
        svg.addEventListener('lostpointercapture', terminaPuntatore, { signal });
        svg.addEventListener('click', () => { ignoraClickDopoTrascinamento = false; }, { signal });
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
            }, { signal });
        }
    }
    collegaInterazionePuntatore();

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
                    if (ignoraClickDopoTrascinamento) {
                        ignoraClickDopoTrascinamento = false;
                        return;
                    }
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
