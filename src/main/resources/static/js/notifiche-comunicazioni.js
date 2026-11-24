import { api } from './area-client.js';

const MASSIMO_MOSTRATO = 99;

function testoAccessibile(numero) {
    return numero === 1 ? '1 comunicazione da confermare' : numero + ' comunicazioni da confermare';
}

function mostra(voce, numero) {
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

/**
 * Mostra accanto alla voce «Comunicazioni» quante comunicazioni ricevute aspettano la conferma di ricezione.
 * Aprire una comunicazione non la toglie dal conteggio: serve il pulsante «Confermo la ricezione».
 * Il conteggio si aggiorna all'apertura della pagina, quando si entra nella sezione e quando la scheda del
 * browser torna visibile, così una comunicazione arrivata nel frattempo compare senza ricaricare la pagina.
 */
function avvia(voce) {
    let inCorso = false;
    let daRipetere = false;
    // Una sola lettura alla volta: una richiesta che arriva durante una lettura non va persa, ma ne accoda
    // un'altra da fare subito dopo, perché la lettura in corso potrebbe essere partita prima della novità.
    async function aggiorna() {
        if (inCorso) {
            daRipetere = true;
            return;
        }
        inCorso = true;
        try {
            const { daConfermare } = await api('/api/comunicazioni/da-confermare');
            mostra(voce, daConfermare);
        } catch (causa) {
            // Il contatore è solo un aiuto: se non si carica, la pagina resta usabile e il problema resta nel log.
            console.warn('Contatore delle comunicazioni non disponibile', causa);
        } finally {
            inCorso = false;
            if (daRipetere) {
                daRipetere = false;
                aggiorna();
            }
        }
    }
    voce.addEventListener('click', aggiorna);
    document.addEventListener('comunicazioni:ricezione-confermata', aggiorna);
    window.addEventListener('hashchange', () => {
        if (location.hash === voce.hash) aggiorna();
    });
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') aggiorna();
    });
    aggiorna();
}

const voce = document.querySelector('.area-nav a[href="#comunicazioni"]');
if (voce) avvia(voce);
