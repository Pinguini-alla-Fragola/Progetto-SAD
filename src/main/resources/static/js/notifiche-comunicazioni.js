import { api } from './area-client.js';

const MASSIMO_MOSTRATO = 99;

function testoAccessibile(numero) {
    return numero === 1 ? '1 comunicazione da confermare' : numero + ' comunicazioni da confermare';
}

/**
 * Mostra accanto alla voce «Comunicazioni» quante comunicazioni ricevute aspettano la conferma di ricezione.
 * Aprire una comunicazione non la toglie dal conteggio: serve il pulsante «Confermo la ricezione».
 */
async function aggiornaContatore(voce) {
    let numero;
    try {
        ({ daConfermare: numero } = await api('/api/comunicazioni/da-confermare'));
    } catch (causa) {
        // Il contatore è solo un aiuto: se non si carica, la pagina resta usabile e il problema resta nel log.
        console.warn('Contatore delle comunicazioni non disponibile', causa);
        return;
    }
    voce.querySelector('.contatore-notifiche')?.remove();
    if (!numero) return;
    const contatore = document.createElement('span');
    contatore.className = 'contatore-notifiche';
    const visibile = document.createElement('span');
    visibile.setAttribute('aria-hidden', 'true');
    visibile.textContent = numero > MASSIMO_MOSTRATO ? MASSIMO_MOSTRATO + '+' : String(numero);
    const perLettoriSchermo = document.createElement('span');
    perLettoriSchermo.className = 'sr-only';
    perLettoriSchermo.textContent = testoAccessibile(numero);
    contatore.append(visibile, perLettoriSchermo);
    voce.append(contatore);
}

const voce = document.querySelector('.area-nav a[href="#comunicazioni"]');
if (voce) aggiornaContatore(voce);
