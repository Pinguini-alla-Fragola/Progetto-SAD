import { api } from './area-client.js';

const idPratica = document.getElementById('contenuto').dataset.richiestaId;
const box = document.getElementById('boxAnnulla');

document.getElementById('toggle-annulla')?.addEventListener('click', () => {
    box.hidden = !box.hidden;
});

document.getElementById('conferma-annulla')?.addEventListener('click', async () => {
    const input = document.getElementById('motivoAnnulla');
    const motivo = input.value.trim();
    if (input.required && !motivo) {
        input.reportValidity();
        return;
    }
    try {
        await api('/api/richieste/' + encodeURIComponent(idPratica) + '/annulla', {
            method: 'POST', body: { motivo: motivo || null }
        });
        window.location.reload();
    } catch (causa) {
        alert(causa.stato ? causa.message : 'Errore di connessione con il server.');
    }
});
