import { api } from './area-client.js';

const TIPOLOGIA_INTERNA = 'INTERNO_BANCO';

/** Il Centro PICUS riguarda solo gli operatori interni del Banco. */
function aggiornaCentroPicus(form) {
    const interno = form.elements.tipo.value === TIPOLOGIA_INTERNA;
    form.elements.centroPicus.disabled = !interno;
    if (!interno) form.elements.centroPicus.checked = false;
}

/** Comuni e misure arrivano dal catalogo: il Banco sceglie solo valori esistenti. */
function popolaCatalogo(form, comuni, misure) {
    for (const comune of comuni) form.elements.comune.append(new Option(comune.nome, comune.codice));
    const elenco = form.querySelector('[data-misure] .picus-choices');
    for (const misura of misure) {
        const etichetta = document.createElement('label');
        etichetta.className = 'picus-check';
        const scelta = document.createElement('input');
        scelta.type = 'checkbox';
        scelta.name = 'misure';
        scelta.value = String(misura.id);
        etichetta.append(scelta, ' ' + misura.dati.codice + ' · ' + misura.dati.nome);
        elenco.append(etichetta);
    }
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
            + '). Stato: in attesa di attivazione.';
    } catch (causa) {
        esito.textContent = causa.message;
        mostraErrori(form, causa.campi);
    } finally {
        bottone.disabled = false;
    }
}

async function avvia(form) {
    const esito = form.querySelector('.picus-esito');
    const bottone = form.querySelector('button[type="submit"]');
    form.elements.tipo.addEventListener('change', () => aggiornaCentroPicus(form));
    aggiornaCentroPicus(form);
    try {
        const [comuni, misure] = await Promise.all([api('/api/catalogo/comuni'), api('/api/catalogo/misure')]);
        popolaCatalogo(form, comuni, misure);
    } catch (causa) {
        esito.textContent = causa.message;
        bottone.disabled = true;
        return;
    }
    form.addEventListener('submit', event => {
        event.preventDefault();
        invia(form, esito, bottone);
    });
}

const form = document.getElementById('nuovo-operatore');
if (form) avvia(form);
