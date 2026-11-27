import { api } from './area-client.js';
import { avviaCoordinamento } from './richieste-area.js';
import { avviaEsitoRichiesta } from './accordo.js';
import { confermaOperazione } from './conferma.js';
import { segnalaFunzioniInattive } from './funzioni-inattive.js';

const LIMITE_MOTIVO = 500;
const SOLO_SPAZI = /^[\s\u00A0\u202F]*$/u;

export function avviaAnnullamentoRichiesta(pagina) {
    const form = document.getElementById('form-annullamento');
    if (!pagina || !form) return;

    const id = pagina.dataset.richiestaId;
    const textarea = document.getElementById('annullamento-motivo');
    const erroreMotivo = document.getElementById('errore-annullamento-motivo');
    const submitBtn = document.getElementById('annulla-richiesta');
    const rileggiLink = document.getElementById('annullamento-rileggi');
    const esito = document.getElementById('esito-annullamento');
    if (!id || !textarea || !submitBtn || !esito) return;

    const motivoObbligatorio = form.dataset.motivoObbligatorio === 'true';
    let invioInCorso = false;

    const pulisciErrori = () => {
        textarea.removeAttribute('aria-invalid');
        if (erroreMotivo) erroreMotivo.textContent = '';
        esito.classList.remove('picus-errore');
        esito.replaceChildren();
    };

    textarea.addEventListener('input', () => {
        if (textarea.hasAttribute('aria-invalid') || erroreMotivo?.textContent) {
            textarea.removeAttribute('aria-invalid');
            if (erroreMotivo) erroreMotivo.textContent = '';
        }
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (invioInCorso) return;

        pulisciErrori();
        const motivo = textarea.value;
        const vuoto = SOLO_SPAZI.test(motivo);

        if (motivoObbligatorio && vuoto) {
            if (erroreMotivo) erroreMotivo.textContent = 'Motivo obbligatorio';
            textarea.setAttribute('aria-invalid', 'true');
            textarea.focus();
            return;
        }
        if (motivo.length > LIMITE_MOTIVO) {
            if (erroreMotivo) erroreMotivo.textContent = `Il motivo può contenere al massimo ${LIMITE_MOTIVO} caratteri`;
            textarea.setAttribute('aria-invalid', 'true');
            textarea.focus();
            return;
        }

        invioInCorso = true;
        submitBtn.disabled = true;
        textarea.disabled = true;

        try {
            const confermata = await confermaOperazione({
                titolo: 'Annullare la richiesta?',
                richiesta: [pagina.dataset.misura, pagina.dataset.opt].filter(Boolean).join(' · '),
                testo: 'La richiesta passerà allo stato Annullata, chiuderà eventuali contatti aperti e conserverà i dati e lo storico registrati.',
                azione: 'Conferma annullamento',
                pericolo: true
            });
            if (!confermata) {
                invioInCorso = false;
                submitBtn.disabled = false;
                textarea.disabled = false;
                submitBtn.focus();
                return;
            }

            esito.textContent = 'Annullamento della richiesta in corso…';
            const opzioni = vuoto
                ? { method: 'POST' }
                : { method: 'POST', body: { motivo } };
            await api(`/api/richieste/${encodeURIComponent(id)}/annullamento`, opzioni);
            location.reload();
        } catch (causa) {
            invioInCorso = false;
            submitBtn.disabled = false;
            textarea.disabled = false;
            if (rileggiLink) rileggiLink.hidden = false;

            const testoErrore = causa?.stato || causa?.campi
                ? causa.message
                : 'Connessione interrotta. Verifica lo stato corrente della pratica prima di riprovare.';
            esito.classList.add('picus-errore');
            esito.replaceChildren(document.createTextNode(testoErrore));

            if (causa?.stato === 401) {
                const loginLink = document.createElement('a');
                loginLink.className = 'btn btn-secondary btn-sm';
                loginLink.href = '/login';
                loginLink.textContent = 'Accedi di nuovo';
                esito.append(' ', loginLink);
            }

            const messaggioCampo = causa?.campi?.motivo || causa?.campi?.comando;
            if (messaggioCampo && erroreMotivo) {
                erroreMotivo.textContent = messaggioCampo;
                textarea.setAttribute('aria-invalid', 'true');
                textarea.focus();
            } else {
                esito.focus();
            }
        }
    });
}

if (typeof document !== 'undefined') {
    const contenuto = document.getElementById('contenuto');
    if (contenuto) {
        segnalaFunzioniInattive();
        avviaEsitoRichiesta(contenuto);
        avviaCoordinamento(contenuto);
        avviaAnnullamentoRichiesta(contenuto);
    }
}
