import { api, ErroreHttp } from './area-client.js';
import { communications } from './communication-view.js';

const MESSAGGIO_RETE = 'Connessione non disponibile. Riprova tra poco.';
const MASSIMO_ALLEGATI = 5;
const DIMENSIONE_MASSIMA = 5 * 1024 * 1024;
const TIPI_GRUPPO = new Set(['GRUPPO_OPT', 'GRUPPO_OPERATORI']);
/** Modello per l'avviso di scadenza della scheda trimestrale: stesso canale delle altre comunicazioni. */
const MODELLO_SCADENZA = {
    titolo: 'Scadenza della scheda trimestrale',
    contenuto: 'Vi ricordiamo di compilare e inviare la scheda trimestrale entro la scadenza prevista.\n'
        + 'Per qualsiasi dubbio potete rispondere a questa comunicazione contattando il Banco.',
    destinatariTipo: 'TUTTI_GLI_OPERATORI'
};

const formatoData = valore => new Date(valore).toLocaleString('it-IT',
    { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Rome' });

function messaggioErrore(causa) {
    return causa instanceof ErroreHttp ? causa.message : MESSAGGIO_RETE;
}

function mostraProblema(contenitore, testo) {
    const paragrafo = document.createElement('p');
    paragrafo.className = 'picus-esito picus-errore';
    paragrafo.textContent = testo;
    contenitore.replaceChildren(paragrafo);
}

/** Carica e disegna l'elenco: inviate per il Banco, ricevute per OPT e Operatori. */
async function caricaElenco(contenitore, banco) {
    try {
        const elenco = await api(banco ? '/api/comunicazioni/inviate' : '/api/comunicazioni/ricevute');
        communications(contenitore, elenco, {
            banco, formatDate: formatoData, onConfirm: () => caricaElenco(contenitore, banco)
        });
    } catch (causa) {
        mostraProblema(contenitore, messaggioErrore(causa));
    }
}

/* ---------------------------------------------------------------- modulo di invio del Banco */

function pulisciErrori(form) {
    for (const nodo of form.querySelectorAll('.picus-errore')) nodo.remove();
    for (const nodo of form.querySelectorAll('[aria-invalid]')) nodo.removeAttribute('aria-invalid');
}

/** Un gruppo di scelte si riconosce dall'attributo data-<nome>; gli altri campi dal nome. */
function trovaControllo(form, nome) {
    return form.querySelector('[data-' + nome + ']') ?? form.elements.namedItem(nome);
}

function mostraErrori(form, campi) {
    for (const [nome, messaggio] of Object.entries(campi)) {
        const controllo = trovaControllo(form, nome);
        if (!controllo) continue;
        const errore = document.createElement('span');
        errore.className = 'picus-errore';
        errore.setAttribute('role', 'alert');
        errore.textContent = messaggio;
        (controllo.closest('label') ?? controllo).append(errore);
        if (controllo.matches('input, select, textarea')) controllo.setAttribute('aria-invalid', 'true');
    }
}

/** Controllo anticipato sui file, per non caricare inutilmente allegati che il server rifiuterebbe. */
function problemaAllegati(files) {
    if (files.length > MASSIMO_ALLEGATI) return 'Si possono allegare al massimo ' + MASSIMO_ALLEGATI + ' file';
    if ([...files].some(file => file.size > DIMENSIONE_MASSIMA)) return 'Ogni allegato può pesare al massimo 5 MB';
    return null;
}

async function aggiornaGruppo(form) {
    const gruppo = form.querySelector('[data-destinatari]');
    const scelte = gruppo.querySelector('.picus-choices');
    const tipo = form.elements.destinatariTipo.value;
    scelte.replaceChildren();
    const perGruppo = TIPI_GRUPPO.has(tipo);
    gruppo.hidden = !perGruppo;
    gruppo.disabled = !perGruppo;
    if (!perGruppo) return;
    try {
        const account = await api('/api/comunicazioni/destinatari?tipo=' + encodeURIComponent(tipo));
        if (form.elements.destinatariTipo.value !== tipo) return;
        for (const voce of account) {
            const etichetta = document.createElement('label');
            etichetta.className = 'picus-check';
            const scelta = document.createElement('input');
            scelta.type = 'checkbox';
            scelta.name = 'gruppo';
            scelta.value = voce.id;
            etichetta.append(scelta, ' ' + voce.nome);
            scelte.append(etichetta);
        }
        if (!account.length) mostraProblema(scelte, 'Non ci sono account attivi da scegliere.');
    } catch (causa) {
        mostraProblema(scelte, messaggioErrore(causa));
    }
}

function applicaModelloScadenza(form) {
    form.elements.titolo.value = MODELLO_SCADENZA.titolo;
    form.elements.contenuto.value = MODELLO_SCADENZA.contenuto;
    form.elements.destinatariTipo.value = MODELLO_SCADENZA.destinatariTipo;
    aggiornaGruppo(form);
    form.elements.contenuto.focus();
}

/** Invio con FormData: il browser imposta da solo il formato multipart con i file. */
async function inviaComunicazione(form, esito, bottone, elenco) {
    pulisciErrori(form);
    const problema = problemaAllegati(form.elements.files.files);
    if (problema) {
        mostraErrori(form, { files: problema });
        return;
    }
    const dati = new FormData(form);
    bottone.disabled = true;
    esito.textContent = 'Invio in corso…';
    try {
        const { headerName, token } = await api('/api/auth/csrf');
        const risposta = await fetch('/api/comunicazioni', {
            method: 'POST', headers: { Accept: 'application/json', [headerName]: token }, body: dati
        });
        const corpo = await risposta.json().catch(() => null);
        if (!risposta.ok) throw new ErroreHttp(risposta.status, corpo);
        form.reset();
        await aggiornaGruppo(form);
        esito.textContent = corpo.destinatari === 1
            ? 'Comunicazione inviata a 1 destinatario.'
            : 'Comunicazione inviata a ' + corpo.destinatari + ' destinatari.';
        caricaElenco(elenco, true);
    } catch (causa) {
        esito.textContent = messaggioErrore(causa);
        mostraErrori(form, causa.campi ?? {});
    } finally {
        bottone.disabled = false;
    }
}

/**
 * Il modulo arriva disattivato dalla pagina: il gestore di invio viene agganciato per primo, così il browser non
 * usa mai l'invio predefinito, e i campi si attivano solo quando tutto è pronto.
 */
function avviaModulo(form, elenco) {
    const campi = form.querySelector('fieldset.campi-form');
    const esito = form.querySelector('.picus-esito');
    const bottone = form.querySelector('button[type="submit"]');
    form.addEventListener('submit', event => {
        event.preventDefault();
        inviaComunicazione(form, esito, bottone, elenco);
    });
    form.elements.destinatariTipo.addEventListener('change', () => aggiornaGruppo(form));
    form.querySelector('[data-modello-scadenza]')?.addEventListener('click', () => applicaModelloScadenza(form));
    aggiornaGruppo(form);
    campi.disabled = false;
    esito.textContent = '';
}

/* ---------------------------------------------------------------- avvio */

const elenco = document.getElementById('messaggi');
if (elenco) {
    const banco = document.body.dataset.area === 'banco';
    caricaElenco(elenco, banco);
    // Chi apre la sezione vede subito anche le comunicazioni arrivate dopo il caricamento della pagina.
    document.querySelector('.area-nav a[href="#comunicazioni"]')
        ?.addEventListener('click', () => caricaElenco(elenco, banco));
    const form = document.getElementById('comunicazione');
    if (banco && form) avviaModulo(form, elenco);
}
