import { api, ErroreHttp } from './area-client.js';

const TIPOLOGIA_INTERNA = 'INTERNO_BANCO';
const MESSAGGIO_RETE = 'Connessione non disponibile. Riprova tra poco.';
let versioneMisure = 0;

/** Un errore del server ha già un messaggio leggibile; un errore di rete no. */
function messaggioErrore(causa) {
    return causa instanceof ErroreHttp ? causa.message : MESSAGGIO_RETE;
}

/** Il Centro PICUS riguarda solo gli operatori interni del Banco. */
function aggiornaCentroPicus(form) {
    const interno = form.elements.tipo.value === TIPOLOGIA_INTERNA;
    form.elements.centroPicus.disabled = !interno;
    if (!interno) form.elements.centroPicus.checked = false;
}

function applicaScelteMisure(elenco, misure) {
    if (!elenco) return;
    const selezionate = new Set(
        Array.from(elenco.querySelectorAll('input[name="misure"]:checked'), input => input.value)
    );
    elenco.replaceChildren();
    for (const misura of (Array.isArray(misure) ? misure : []).filter(m => m && m.attiva !== false)) {
        const etichetta = document.createElement('label');
        etichetta.className = 'picus-check';
        const scelta = document.createElement('input');
        scelta.type = 'checkbox';
        scelta.name = 'misure';
        scelta.value = String(misura.id);
        scelta.checked = selezionate.has(scelta.value);
        etichetta.append(scelta, ' ' + misura.dati.codice + ' · ' + misura.dati.nome);
        elenco.append(etichetta);
    }
}

export function aggiornaScelteMisureOperatore(misure) {
    versioneMisure++;
    const formOperatore = document.getElementById('nuovo-operatore');
    applicaScelteMisure(formOperatore?.querySelector('[data-misure] .picus-choices'), misure);
}

/** Comuni e misure arrivano dal catalogo: il Banco sceglie solo valori esistenti. */
function popolaCatalogo(form, comuni, misure, versioneIniziale) {
    for (const comune of comuni) {
        const etichetta = comune.provincia
            ? `${comune.nome} — ${comune.cittaMetropolitana ? 'Città metropolitana di ' : 'Provincia di '}${comune.provincia}${comune.siglaProvincia ? ' (' + comune.siglaProvincia + ')' : ''}`
            : comune.nome;
        form.elements.comune.append(new Option(etichetta, comune.codice));
    }
    if (versioneIniziale === versioneMisure) {
        applicaScelteMisure(form.querySelector('[data-misure] .picus-choices'), misure);
    }
}

/** Il server dice se il link di attivazione è partito: se non è partito, il Banco lo può reinviare dall'elenco. */
function descriviInvito(creato) {
    if (creato.erroreInvio) return "L'invio dell'email è fallito: puoi reinviare l'invito dall'elenco Account.";
    if (creato.invitoInviato) return 'Email di attivazione inviata.';
    return 'Email non inviata (modalità demo).';
}

function leggiDati(form) {
    return {
        nome: form.elements.nome.value,
        cognome: form.elements.cognome.value,
        codiceFiscale: form.elements.codiceFiscale.value,
        email: form.elements.email.value,
        telefono: form.elements.telefono.value,
        comuneCodice: form.elements.comune.value,
        tipologia: form.elements.tipo.value,
        misure: Array.from(form.querySelectorAll('input[name="misure"]:checked'), scelta => Number(scelta.value)),
        centroPicus: form.elements.centroPicus.checked
    };
}

function pulisciErrori(form) {
    for (const nodo of form.querySelectorAll('.picus-errore')) nodo.remove();
    for (const nodo of form.querySelectorAll('[aria-invalid]')) nodo.removeAttribute('aria-invalid');
}

/** Un gruppo di scelte, come le misure, si riconosce dall'attributo data-<nome>; gli altri campi dal nome. */
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
        (controllo.closest('label') ?? controllo.parentElement).append(errore);
        if (controllo.matches('input, select')) controllo.setAttribute('aria-invalid', 'true');
    }
}

async function invia(form, esito, bottone) {
    pulisciErrori(form);
    const dati = leggiDati(form);
    bottone.disabled = true;
    esito.textContent = 'Creazione in corso…';
    try {
        const creato = await api('/api/utenze/operatori', { method: 'POST', body: dati });
        form.reset();
        aggiornaCentroPicus(form);
        esito.textContent = 'Account creato per ' + creato.nomeVisualizzato + ' (' + creato.email
            + '). Stato: in attesa di attivazione. ' + descriviInvito(creato);
    } catch (causa) {
        esito.textContent = messaggioErrore(causa);
        mostraErrori(form, causa.campi ?? {});
    } finally {
        bottone.disabled = false;
    }
}

/**
 * Il form arriva disattivato dalla pagina. Il gestore di invio viene agganciato per primo, così il browser
 * non usa mai l'invio predefinito; i campi si attivano solo quando il catalogo è caricato.
 */
async function avvia(form) {
    const campi = form.querySelector('fieldset.campi-form');
    const esito = form.querySelector('.picus-esito');
    const bottone = form.querySelector('button[type="submit"]');
    let pronto = false;
    form.addEventListener('submit', event => {
        event.preventDefault();
        if (pronto) invia(form, esito, bottone);
    });
    form.elements.tipo.addEventListener('change', () => aggiornaCentroPicus(form));
    try {
        const versioneIniziale = versioneMisure;
        const [comuni, misure] = await Promise.all([api('/api/catalogo/comuni'), api('/api/catalogo/misure')]);
        popolaCatalogo(form, comuni, misure, versioneIniziale);
    } catch (causa) {
        esito.textContent = 'Il catalogo non è disponibile, quindi il modulo resta disattivato. ' + messaggioErrore(causa);
        return;
    }
    aggiornaCentroPicus(form);
    campi.disabled = false;
    esito.textContent = '';
    pronto = true;
}

const form = document.getElementById('nuovo-operatore');
if (form) avvia(form);
