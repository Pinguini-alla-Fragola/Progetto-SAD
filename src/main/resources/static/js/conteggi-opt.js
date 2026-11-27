import { api } from './area-client.js';
import { renderList } from './list-view.js';
import { leggiRubricaAccount } from './rubrica-banco.js';

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

function descriviPeriodo(periodo) {
    const { anno, trimestre, annoFine, trimestreFine } = periodo;
    if (annoFine && trimestreFine) {
        return `Periodo consultato: dal ${trimestre}º trimestre ${anno} al ${trimestreFine}º trimestre ${annoFine} (cumulativo)`;
    }
    return `Periodo consultato: ${anno} · ${trimestre}º trimestre`;
}

export async function initConteggiOpt(area) {
    const utenteId = document.body.dataset.utente;
    let optRubrica = area === 'banco' ? leggiRubricaAccount('OPT') : new Map();
    let categorie = [];
    const misureMap = new Map();
    const misureAttive = new Map();
    const misureNonTrovate = new Set();
    let selectMisura = null;
    let formConteggi = null;
    let contenitoreVoci = null;
    let avvisoMisuraEl = null;
    let cambioMisuraBoxEl = null;
    let cambioMisuraTestoEl = null;
    let catalogoCaricato = false;
    let misuraSelezionataId = '';
    let misuraSelezionataNome = '';
    let bozzaOrfana = null;
    let transizioneInAttesa = null;
    let erroreRefreshCatalogo = false;
    let versioneCatalogo = 0;
    const MASSIMA_CONCORRENZA_MISURE = 4;

    const formattaEtichettaMisura = (m, fallbackId) => {
        const nome = m.dati?.nome || m.nome || `Misura #${fallbackId ?? m.id}`;
        return m.attiva === false ? `${nome} (disattivata)` : nome;
    };

    const haValoriBozza = () => {
        if (!contenitoreVoci?.querySelectorAll) return false;
        return Array.from(contenitoreVoci.querySelectorAll('input[name="valore"]'))
            .some(input => String(input.value ?? '').trim() !== '');
    };

    const sincronizzaValoreSelect = valore => {
        if (!selectMisura) return;
        selectMisura.value = valore;
        if (formConteggi?.elements?.misuraId && formConteggi.elements.misuraId !== selectMisura) {
            formConteggi.elements.misuraId.value = valore;
        }
    };

    const costruisciOpzioneMisura = (valore, testo) => {
        const opt = document.createElement('option');
        opt.value = valore;
        opt.textContent = testo;
        return opt;
    };

    const applicaOpzioniSelect = (opzioni, valoreSelezionato) => {
        if (!selectMisura) return;
        const correnti = Array.from(selectMisura.options || [], o => `${o.value}\u0000${o.textContent}`);
        const nuove = opzioni.map(o => `${o.value}\u0000${o.textContent}`);
        const opzioniIdentiche = correnti.length === nuove.length && correnti.every((v, i) => v === nuove[i]);
        if (!opzioniIdentiche) {
            selectMisura.replaceChildren(...opzioni);
        }
        if (String(selectMisura.value ?? '') !== valoreSelezionato || !opzioniIdentiche) {
            sincronizzaValoreSelect(valoreSelezionato);
        }
    };

    const aggiornaStatoMisuraUi = () => {
        if (area !== 'opt') return;
        const messaggi = [];
        if (bozzaOrfana) {
            messaggi.push(
                `La misura precedente («${bozzaOrfana.misuraNome}») non è più disponibile: i conteggi in bozza appartengono a tale misura. Seleziona una misura attiva e conferma lo svuotamento prima di salvare.`
            );
        } else if (catalogoCaricato && misureAttive.size === 0) {
            messaggi.push('Nessuna misura attiva disponibile nel catalogo: il salvataggio è bloccato.');
        } else if (misuraSelezionataId === '' && catalogoCaricato && misureAttive.size > 0) {
            messaggi.push('La misura selezionata non è più disponibile. Seleziona una misura attiva per continuare.');
        }
        if (erroreRefreshCatalogo) {
            messaggi.push("Impossibile aggiornare il catalogo misure: mantenuto l'ultimo elenco valido.");
        }

        const testoAvviso = messaggi.join(' ');
        if (avvisoMisuraEl) {
            avvisoMisuraEl.textContent = testoAvviso;
            avvisoMisuraEl.hidden = testoAvviso.length === 0;
        } else if (testoAvviso && formConteggi?.querySelector) {
            const esitoFallback = formConteggi.querySelector('.picus-esito');
            if (esitoFallback) esitoFallback.textContent = testoAvviso;
        }

        if (cambioMisuraBoxEl) {
            const mostraConferma = Boolean(bozzaOrfana && transizioneInAttesa && haValoriBozza());
            cambioMisuraBoxEl.hidden = !mostraConferma;
            if (cambioMisuraTestoEl) {
                cambioMisuraTestoEl.textContent = mostraConferma
                    ? `I conteggi inseriti si riferiscono a «${bozzaOrfana.misuraNome}». Passando a «${transizioneInAttesa.misuraNome}», i soli campi conteggio verranno svuotati (anno e trimestre restano invariati).`
                    : '';
            }
        }
    };

    const aggiornaSelectMisure = misRes => {
        if (area !== 'opt' || !selectMisura || !Array.isArray(misRes)) return;
        const precedentiMisure = new Map(misureAttive);
        const attive = [];
        misureAttive.clear();
        for (const m of misRes) {
            if (m.attiva !== false) {
                const id = String(m.id);
                const nome = m.dati?.nome || m.nome || `Misura #${m.id}`;
                attive.push({ id, nome });
                misureAttive.set(id, nome);
            }
        }
        const catalogoEraCaricato = catalogoCaricato;
        catalogoCaricato = true;
        erroreRefreshCatalogo = false;

        const opzioniPrima = Array.from(selectMisura.options || []);
        const opzionePrecedente = opzioniPrima.find(o => String(o.value ?? '') === String(selectMisura.value ?? ''));
        const precedente = String(selectMisura.value ?? '').trim();
        const nomePrecedente = precedentiMisure.get(precedente)
            || (misuraSelezionataId === precedente ? misuraSelezionataNome : '')
            || opzionePrecedente?.textContent
            || `Misura #${precedente}`;

        if (attive.length === 0) {
            if (precedente && !bozzaOrfana && haValoriBozza()) {
                bozzaOrfana = { misuraId: precedente, misuraNome: nomePrecedente };
            }
            transizioneInAttesa = null;
            misuraSelezionataId = '';
            misuraSelezionataNome = '';
            applicaOpzioniSelect(
                [costruisciOpzioneMisura('', 'Nessuna misura attiva disponibile')],
                ''
            );
            aggiornaStatoMisuraUi();
            return;
        }

        const ancoraAttiva = precedente && attive.some(m => m.id === precedente);
        if (ancoraAttiva) {
            const opzioni = [];
            if (bozzaOrfana) {
                opzioni.push(costruisciOpzioneMisura('', 'Seleziona una misura'));
            }
            for (const { id, nome } of attive) {
                opzioni.push(costruisciOpzioneMisura(id, nome));
            }
            applicaOpzioniSelect(opzioni, precedente);
            misuraSelezionataId = precedente;
            misuraSelezionataNome = misureAttive.get(precedente) || '';
            if (transizioneInAttesa?.misuraId === precedente) {
                transizioneInAttesa = { misuraId: precedente, misuraNome: misuraSelezionataNome };
            }
            aggiornaStatoMisuraUi();
            return;
        }

        if (precedente) {
            if (transizioneInAttesa?.misuraId === precedente) {
                transizioneInAttesa = null;
            } else if (!bozzaOrfana && haValoriBozza()) {
                bozzaOrfana = { misuraId: precedente, misuraNome: nomePrecedente };
            }
            misuraSelezionataId = '';
            misuraSelezionataNome = '';
            applicaOpzioniSelect(
                [
                    costruisciOpzioneMisura('', 'Seleziona una misura'),
                    ...attive.map(({ id, nome }) => costruisciOpzioneMisura(id, nome))
                ],
                ''
            );
            aggiornaStatoMisuraUi();
            return;
        }

        const primoPopolamentoIniziale = !catalogoEraCaricato && opzioniPrima.length === 0 && !bozzaOrfana && !haValoriBozza();
        if (primoPopolamentoIniziale) {
            const iniziale = attive[0];
            applicaOpzioniSelect(
                attive.map(({ id, nome }) => costruisciOpzioneMisura(id, nome)),
                iniziale.id
            );
            misuraSelezionataId = iniziale.id;
            misuraSelezionataNome = iniziale.nome;
            aggiornaStatoMisuraUi();
            return;
        }

        misuraSelezionataId = '';
        misuraSelezionataNome = '';
        applicaOpzioniSelect(
            [
                costruisciOpzioneMisura('', 'Seleziona una misura'),
                ...attive.map(({ id, nome }) => costruisciOpzioneMisura(id, nome))
            ],
            ''
        );
        aggiornaStatoMisuraUi();
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
        for (const id of [...misureMap.keys()]) {
            if (!presenti.has(id)) misureMap.delete(id);
        }
        if (area === 'opt') {
            aggiornaSelectMisure(misRes);
        }
        return true;
    };

    const fetchCatalogoArea = () => (
        area === 'banco'
            ? api('/api/catalogo/misure/tutte')
            : api('/api/catalogo/misure')
    );

    const ricaricaCatalogoOpt = async () => {
        if (area !== 'opt') return;
        const versione = ++versioneCatalogo;
        const misRes = await fetchCatalogoArea().catch(() => null);
        if (versione !== versioneCatalogo) return;
        if (Array.isArray(misRes)) {
            aggiornaMappaCatalogo(misRes);
        } else {
            erroreRefreshCatalogo = true;
            aggiornaStatoMisuraUi();
        }
    };

    let misureIniziali = null;
    try {
        const [catRes, misRes] = await Promise.all([
            api('/api/monitoraggio/categorie'),
            fetchCatalogoArea().catch(() => null)
        ]);
        categorie = Array.isArray(catRes) ? catRes : [];
        if (aggiornaMappaCatalogo(misRes)) {
            misureIniziali = misRes;
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
                const versioneCat = ++versioneCatalogo;
                const [dati, misRes, rubricaRes] = await Promise.all([
                    api(`/api/monitoraggio/conteggi-opt?${params}`),
                    fetchCatalogoArea().catch(() => {
                        catalogoNonAggiornato = true;
                        return null;
                    }),
                    mostraOpt ? api('/api/utenze/rubrica?ruolo=OPT').catch(() => null) : Promise.resolve(null)
                ]);
                if (richiesta !== progressivo) return;
                if (versioneCat === versioneCatalogo && misRes) aggiornaMappaCatalogo(misRes);
                if (mostraOpt) optRubrica = leggiRubricaAccount('OPT', rubricaRes);
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
        formConteggi = form;
        contenitoreVoci = document.getElementById('voci-conteggi');
        const btnAggiungi = document.getElementById('altra-voce-opt');
        selectMisura = form?.querySelector?.('select[name="misuraId"]') ?? null;
        avvisoMisuraEl = document.getElementById('conteggi-misura-avviso') ?? null;
        cambioMisuraBoxEl = document.getElementById('conteggi-cambio-misura') ?? null;
        cambioMisuraTestoEl = document.getElementById('conteggi-cambio-misura-testo') ?? null;
        const btnSvuotaBozza = document.getElementById('conteggi-svuota-bozza') ?? null;
        const btnAnnullaCambio = document.getElementById('conteggi-annulla-cambio') ?? null;

        if (selectMisura && selectMisura.options.length === 0) {
            if (Array.isArray(misureIniziali)) {
                aggiornaSelectMisure(misureIniziali);
            } else {
                erroreRefreshCatalogo = true;
                aggiornaStatoMisuraUi();
            }
        }

        const rimuoviOpzioneVuotaSeValida = valore => {
            if (!selectMisura || !valore) return;
            const attive = Array.from(selectMisura.options || [])
                .filter(o => String(o.value ?? '') !== '')
                .map(o => costruisciOpzioneMisura(String(o.value), o.textContent));
            if (attive.some(o => o.value === valore)) {
                applicaOpzioniSelect(attive, valore);
            }
        };

        selectMisura?.addEventListener?.('change', () => {
            const nuovaId = String(selectMisura.value ?? '').trim();
            if (!nuovaId) {
                transizioneInAttesa = null;
                misuraSelezionataId = '';
                misuraSelezionataNome = '';
                aggiornaStatoMisuraUi();
                return;
            }
            const nomeNuova = misureAttive.get(nuovaId) || `Misura #${nuovaId}`;
            if (bozzaOrfana && haValoriBozza()) {
                transizioneInAttesa = { misuraId: nuovaId, misuraNome: nomeNuova };
                aggiornaStatoMisuraUi();
                return;
            }
            bozzaOrfana = null;
            transizioneInAttesa = null;
            misuraSelezionataId = nuovaId;
            misuraSelezionataNome = nomeNuova;
            rimuoviOpzioneVuotaSeValida(nuovaId);
            aggiornaStatoMisuraUi();
        });

        btnSvuotaBozza?.addEventListener?.('click', () => {
            if (!transizioneInAttesa && !bozzaOrfana) return;
            const targetId = transizioneInAttesa?.misuraId || String(selectMisura?.value ?? '').trim();
            if (!targetId || !misureAttive.has(targetId)) {
                transizioneInAttesa = null;
                sincronizzaValoreSelect('');
                aggiornaStatoMisuraUi();
                return;
            }
            if (contenitoreVoci?.querySelectorAll) {
                for (const input of contenitoreVoci.querySelectorAll('input[name="valore"]')) {
                    input.value = '';
                }
            }
            bozzaOrfana = null;
            transizioneInAttesa = null;
            misuraSelezionataId = targetId;
            misuraSelezionataNome = misureAttive.get(targetId) || '';
            rimuoviOpzioneVuotaSeValida(targetId);
            aggiornaStatoMisuraUi();
            if (avvisoMisuraEl) {
                avvisoMisuraEl.textContent = `Conteggi in bozza svuotati. Ora puoi compilare per «${misuraSelezionataNome}».`;
                avvisoMisuraEl.hidden = false;
            }
            contenitoreVoci?.querySelector?.('input[name="valore"]')?.focus?.();
        });

        btnAnnullaCambio?.addEventListener?.('click', () => {
            transizioneInAttesa = null;
            misuraSelezionataId = '';
            misuraSelezionataNome = '';
            const opzioniConVuota = [
                costruisciOpzioneMisura('', 'Seleziona una misura'),
                ...Array.from(misureAttive.entries(), ([id, nome]) => costruisciOpzioneMisura(id, nome))
            ];
            applicaOpzioniSelect(opzioniConVuota, '');
            aggiornaStatoMisuraUi();
            selectMisura?.focus?.();
        });

        document.getElementById('conteggi-tab-compila')?.addEventListener?.('click', () => ricaricaCatalogoOpt());
        document.querySelector?.('#workspace-sidebar a[href="#monitoraggio"]')?.addEventListener?.('click', () => ricaricaCatalogoOpt());
        globalThis.window?.addEventListener?.('hashchange', () => {
            if (globalThis.location?.hash === '#monitoraggio') {
                return ricaricaCatalogoOpt();
            }
        });
        document.addEventListener?.('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                return ricaricaCatalogoOpt();
            }
        });

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
            const esito = form.querySelector('.form-submit .picus-esito') || form.querySelector('.picus-esito');
            const submitBtn = form.querySelector('button[type="submit"]');
            esito.textContent = '';

            if (bozzaOrfana || transizioneInAttesa) {
                esito.textContent = bozzaOrfana
                    ? `I conteggi in bozza appartengono a «${bozzaOrfana.misuraNome}». Svuota i conteggi o annulla il cambio misura prima di salvare.`
                    : 'Conferma il cambio misura prima di salvare i conteggi.';
                aggiornaStatoMisuraUi();
                return;
            }

            const rawSelect = selectMisura ? String(selectMisura.value ?? '').trim() : '';
            const rawElem = form.elements?.misuraId ? String(form.elements.misuraId.value ?? '').trim() : '';
            const misuraValore = (selectMisura && form.elements?.misuraId && form.elements.misuraId !== selectMisura)
                ? (rawSelect && rawElem ? rawSelect : '')
                : (rawSelect || rawElem);

            if (!misuraValore || !/^[1-9][0-9]*$/.test(misuraValore)) {
                esito.textContent = 'Seleziona una misura attiva prima di salvare i conteggi.';
                selectMisura?.focus?.();
                return;
            }
            if (catalogoCaricato && !misureAttive.has(misuraValore)) {
                esito.textContent = 'La misura selezionata non è più disponibile. Seleziona una misura attiva per continuare.';
                aggiornaStatoMisuraUi();
                return;
            }

            const anno = Number(form.elements.anno.value);
            const trimestre = Number(form.elements.trimestre.value);
            const misuraId = Number(misuraValore);
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
                ricaricaCatalogoOpt();
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
