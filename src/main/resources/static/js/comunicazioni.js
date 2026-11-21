import { api, ErroreHttp } from './area-client.js';
import { ElencoComunicazioni, ultimaRichiesta } from './elenco-comunicazioni.js';

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

function messaggioErrore(causa) {
    return causa instanceof ErroreHttp ? causa.message : MESSAGGIO_RETE;
}

function mostraProblema(contenitore, testo) {
    const paragrafo = document.createElement('p');
    paragrafo.className = 'picus-esito picus-errore';
    paragrafo.textContent = testo;
    contenitore.replaceChildren(paragrafo);
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

/**
 * Mostra gli account da scegliere per il gruppo selezionato. Se il Banco cambia gruppo mentre una lettura è in
 * corso, conta solo l'ultima: le risposte precedenti, anche di errore, vengono scartate e non toccano le scelte.
 */
function creaScelteGruppo(form) {
    const gruppo = form.querySelector('[data-destinatari]');
    const scelte = gruppo.querySelector('.picus-choices');
    const nuovaRichiesta = ultimaRichiesta();

    return async function aggiorna() {
        const attuale = nuovaRichiesta();
        const tipo = form.elements.destinatariTipo.value;
        scelte.replaceChildren();
        const perGruppo = TIPI_GRUPPO.has(tipo);
        gruppo.hidden = !perGruppo;
        gruppo.disabled = !perGruppo;
        if (!perGruppo) return;
        try {
            const account = await api('/api/comunicazioni/destinatari?tipo=' + encodeURIComponent(tipo));
            if (!attuale()) return;
            scelte.replaceChildren(...account.map(voce => {
                const etichetta = document.createElement('label');
                etichetta.className = 'picus-check';
                const scelta = document.createElement('input');
                scelta.type = 'checkbox';
                scelta.name = 'gruppo';
                scelta.value = voce.id;
                etichetta.append(scelta, ' ' + voce.nome);
                return etichetta;
            }));
            if (!account.length) mostraProblema(scelte, 'Non ci sono account attivi da scegliere.');
        } catch (causa) {
            if (attuale()) mostraProblema(scelte, messaggioErrore(causa));
        }
    };
}

/**
 * Tutti i campi viaggiano in una sola parte JSON, «dati», con la stessa forma dell'invio senza allegati: anche un
 * gruppo di 1000 account resta una parte sola. Ogni file è una parte «files».
 */
function datiModulo(form) {
    const gruppo = form.querySelector('[data-destinatari]');
    const dati = {
        titolo: form.elements.titolo.value,
        contenuto: form.elements.contenuto.value,
        destinatariTipo: form.elements.destinatariTipo.value,
        gruppo: gruppo.disabled ? null
            : [...gruppo.querySelectorAll('input[name="gruppo"]:checked')].map(scelta => scelta.value)
    };
    const corpo = new FormData();
    corpo.append('dati', new Blob([JSON.stringify(dati)], { type: 'application/json' }));
    for (const file of form.elements.files.files) corpo.append('files', file, file.name);
    return corpo;
}

async function inviaComunicazione(form, { esito, bottone, aggiornaGruppo, elenco }) {
    pulisciErrori(form);
    const problema = problemaAllegati(form.elements.files.files);
    if (problema) {
        mostraErrori(form, { files: problema });
        return;
    }
    const corpo = datiModulo(form);
    bottone.disabled = true;
    esito.textContent = 'Invio in corso…';
    try {
        const { headerName, token } = await api('/api/auth/csrf');
        const risposta = await fetch('/api/comunicazioni', {
            method: 'POST', headers: { Accept: 'application/json', [headerName]: token }, body: corpo
        });
        const dati = await risposta.json().catch(() => null);
        if (!risposta.ok) throw new ErroreHttp(risposta.status, dati);
        form.reset();
        await aggiornaGruppo();
        esito.textContent = dati.destinatari === 1
            ? 'Comunicazione inviata a 1 destinatario.'
            : 'Comunicazione inviata a ' + dati.destinatari + ' destinatari.';
        elenco.vaiA(0);
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
    const parti = {
        esito: form.querySelector('.picus-esito'),
        bottone: form.querySelector('button[type="submit"]'),
        aggiornaGruppo: creaScelteGruppo(form),
        elenco
    };
    form.addEventListener('submit', evento => {
        evento.preventDefault();
        inviaComunicazione(form, parti);
    });
    form.elements.destinatariTipo.addEventListener('change', parti.aggiornaGruppo);
    form.querySelector('[data-modello-scadenza]')?.addEventListener('click', () => {
        form.elements.titolo.value = MODELLO_SCADENZA.titolo;
        form.elements.contenuto.value = MODELLO_SCADENZA.contenuto;
        form.elements.destinatariTipo.value = MODELLO_SCADENZA.destinatariTipo;
        parti.aggiornaGruppo();
        form.elements.contenuto.focus();
    });
    parti.aggiornaGruppo();
    campi.disabled = false;
    parti.esito.textContent = '';
}

/* ---------------------------------------------------------------- avvio */

const contenitore = document.getElementById('messaggi');
if (contenitore) {
    const banco = document.body.dataset.area === 'banco';
    const elenco = new ElencoComunicazioni(contenitore, { banco });
    elenco.carica();
    // Chi apre la sezione vede subito anche le comunicazioni arrivate dopo il caricamento della pagina.
    document.querySelector('.area-nav a[href="#comunicazioni"]')?.addEventListener('click', () => elenco.carica());
    const form = document.getElementById('comunicazione');
    if (banco && form) avviaModulo(form, elenco);
}
