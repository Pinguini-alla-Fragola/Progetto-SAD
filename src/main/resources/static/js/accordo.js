import { api } from './area-client.js';
import { confermaOperazione } from './conferma.js';

/** Il controllo dei componenti evita di normalizzare silenziosamente date inesistenti. */
export function preparaAccordo(dati) {
    const data = new Date(dati.dataOra);
    const parti = (dati.dataOra || '').match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/);
    const locali = [data.getFullYear(), data.getMonth() + 1, data.getDate(), data.getHours(), data.getMinutes()];
    if (!parti || locali.some((v, i) => v !== Number(parti[i + 1]))) {
        throw Object.assign(new Error('Controlla data e ora dell’appuntamento.'), {
            campi: { dataOra: 'Inserisci una data e un orario esistenti nel fuso indicato.' }
        });
    }
    const corpo = { modalita: dati.modalita, dataOra: data.toISOString() };
    const campi = dati.modalita === 'A_DISTANZA' ? ['link'] : ['luogo', 'nomeReferente', 'cognomeReferente', 'telefonoReferente'];
    for (const campo of campi) corpo[campo] = (dati[campo] || '').trim();
    return corpo;
}

export function motivoBloccoRifiuto(contatti, candidati) {
    if (contatti.some(c => c.esito === 'IN_ATTESA')) return 'Attendi la risposta al contatto ancora aperto.';
    const ultimi = new Map(contatti.map(c => [c.erogatoreId + '|' + c.ambito, c]));
    const daValutare = candidati.some(({ erogatore: e }) =>
        ultimi.get(e.accountId + '|' + e.ambito)?.esito !== 'NON_DISPONIBILE');
    return daValutare ? 'Valuta tutte le opzioni compatibili, comprese quelle a distanza, prima del rifiuto definitivo.' : '';
}

export function aggiornaRifiuto(contatti, candidati) {
    const button = document.getElementById('rifiuta-richiesta');
    if (!button) return;
    const motivo = motivoBloccoRifiuto(contatti, candidati);
    button.disabled = !!motivo;
    document.getElementById('rifiuto-motivo').textContent = motivo || 'Tutte le opzioni compatibili sono state valutate. Puoi chiudere la ricerca.';
}

export function avviaEsitoRichiesta(pagina) {
    if (pagina.dataset.banco !== 'true') return;
    const id = encodeURIComponent(pagina.dataset.richiestaId);
    const rifiuta = document.getElementById('rifiuta-richiesta');
    const form = document.getElementById('form-accordo');
    let occupato = false;
    const blocca = valore => {
        occupato = valore;
        if (form) form.querySelector('fieldset').disabled = valore;
        if (rifiuta) rifiuta.disabled = valore;
    };
    const esito = document.getElementById('esito-accordo') || document.getElementById('coordinamento-esito');
    const recupero = document.getElementById('esito-recupero');
    function mostraErrore(causa) {
        esito.textContent = causa.stato || causa.campi ? causa.message
            : 'Connessione interrotta. Verifica l’esito prima di riprovare.';
        esito.classList.add('picus-errore');
        for (const [campo, testo] of Object.entries(causa.campi || {})) {
            const input = form?.elements.namedItem(campo);
            const messaggio = document.getElementById('errore-' + campo);
            if (messaggio) messaggio.textContent = testo;
            input?.setAttribute('aria-invalid', 'true');
        }
        // Dopo un esito incerto o uno stato cambiato, la lettura precede un nuovo comando.
        const verifica = !causa.campi && !causa.stato || [401, 403, 404, 409].includes(causa.stato) || causa.stato >= 500;
        if (verifica) {
            recupero.hidden = false;
            recupero.focus();
        } else {
            blocca(false);
            (form?.querySelector('[aria-invalid]') || esito).focus();
        }
    }
    rifiuta?.addEventListener('click', async () => {
        if (occupato) return;
        blocca(true);
        const confermata = await confermaOperazione({
            titolo: 'Rifiutare definitivamente la richiesta?', richiesta: pagina.dataset.misura,
            testo: 'Conferma di aver esaurito le alternative e le trattative. La OPT richiedente vedrà «Nessun erogatore disponibile». La pratica verrà chiusa come Rifiutata.',
            azione: 'Rifiuta definitivamente', pericolo: true
        });
        if (!confermata) { blocca(false); rifiuta.focus(); return; }
        esito.textContent = 'Registrazione del rifiuto…';
        esito.classList.remove('picus-errore');
        try {
            await api('/api/richieste/' + id + '/rifiuta', { method: 'POST' });
            location.reload();
        } catch (causa) { mostraErrore(causa); }
    });
    if (!form) return;
    const modalita = form.elements.modalita;
    const aggiornaCampi = () => {
        const remoto = modalita.value === 'A_DISTANZA';
        for (const [nome, visibile] of [['campi-presenza', !remoto], ['campi-remoto', remoto]]) {
            const gruppo = document.getElementById(nome);
            gruppo.hidden = !visibile;
            for (const input of gruppo.querySelectorAll('input')) {
                input.disabled = !visibile;
                input.required = visibile;
            }
        }
    };
    modalita.addEventListener('change', aggiornaCampi);
    aggiornaCampi();
    const fuso = Intl.DateTimeFormat().resolvedOptions().timeZone;
    document.getElementById('accordo-fuso').textContent = 'Data e ora nel fuso del dispositivo: ' + fuso + '.';
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (occupato) return;
        for (const campo of form.querySelectorAll('[aria-invalid]')) campo.removeAttribute('aria-invalid');
        for (const errore of form.querySelectorAll('.field-error')) errore.textContent = '';
        esito.textContent = '';
        esito.classList.remove('picus-errore');
        let corpo;
        try { corpo = preparaAccordo(Object.fromEntries(new FormData(form))); }
        catch (causa) { mostraErrore(causa); return; }
        blocca(true);
        const quando = new Date(corpo.dataOra).toLocaleString('it-IT', { dateStyle: 'long', timeStyle: 'short' });
        const confermata = await confermaOperazione({
            titolo: 'Confermare l’accordo?', richiesta: quando + ' · ' + (corpo.modalita === 'A_DISTANZA' ? 'A distanza' : 'In presenza'),
            testo: 'I dettagli saranno visibili alla OPT richiedente e all’erogatore assegnato. Dopo la conferma l’accordo non potrà essere modificato.',
            azione: 'Conferma accordo'
        });
        if (!confermata) { blocca(false); form.querySelector('[type="submit"]').focus(); return; }
        esito.textContent = 'Registrazione dell’accordo…';
        try {
            await api('/api/richieste/' + id + '/conferma', { method: 'POST', body: corpo });
            location.reload();
        } catch (causa) { mostraErrore(causa); }
    });
}
