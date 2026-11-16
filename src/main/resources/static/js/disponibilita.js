import { api } from './area-client.js';

const etichetteEsito = {
    IN_ATTESA: 'In attesa',
    DISPONIBILE: 'Disponibile',
    NON_DISPONIBILE: 'Non disponibile'
};
const etichetteAmbito = {
    STESSO_COMUNE: 'Stesso comune',
    COMUNE_LIMITROFO: 'Comune limitrofo',
    CENTRO_PICUS_A_DISTANZA: 'Centro PICUS a distanza'
};
const etichetteDestinatario = { PERSONA: 'Persona', NUCLEO: 'Nucleo familiare' };

const pagina = document.getElementById('contenuto');
const caricamento = document.getElementById('caricamento');
const errore = document.getElementById('errore-contatto');

function mostraErrore(causa, assente) {
    const messaggi = {
        401: 'La sessione è terminata. Accedi di nuovo dall’area di lavoro.',
        403: 'Non puoi accedere a questo contatto.',
        404: assente,
        409: 'Il contatto ha già ricevuto una risposta o la richiesta non è più in presa in carico.'
    };
    errore.textContent = messaggi[causa.stato] || 'Operazione non riuscita. Riprova.';
    errore.hidden = false;
}

function mostraEsito(elemento, esito) {
    elemento.textContent = etichetteEsito[esito] || esito;
    elemento.dataset.esito = esito;
    elemento.hidden = false;
}

async function collegaRisposta(contattoId) {
    const percorso = '/api/contatti-disponibilita/' + encodeURIComponent(contattoId);
    const azioni = document.getElementById('azioni');
    const pulsanti = [...azioni.querySelectorAll('button')];
    const esito = document.getElementById('esito-contatto');
    const conferma = document.getElementById('risposta-registrata');
    const assente = 'Il contatto non è disponibile.';
    try {
        const dettaglio = await api(percorso);
        document.getElementById('misura').textContent = dettaglio.nomeMisura;
        document.getElementById('comune').textContent = dettaglio.comuneCodice;
        document.getElementById('destinatario').textContent = etichetteDestinatario[dettaglio.tipoDestinatario];
        document.getElementById('ambito').textContent = etichetteAmbito[dettaglio.contatto.ambito];
        document.getElementById('richiesta-id').textContent = dettaglio.contatto.richiestaId;
        document.getElementById('dati-contatto').hidden = false;
        mostraEsito(esito, dettaglio.contatto.esito);
        azioni.hidden = dettaglio.contatto.esito !== 'IN_ATTESA';
    } catch (causa) {
        mostraErrore(causa, assente);
        return;
    } finally {
        caricamento.hidden = true;
    }

    for (const pulsante of pulsanti) {
        pulsante.addEventListener('click', async () => {
            pulsanti.forEach(azione => { azione.disabled = true; });
            errore.hidden = true;
            try {
                const contatto = await api(percorso + '/risposta', {
                    method: 'POST', body: { disponibile: pulsante.dataset.disponibile === 'true' }
                });
                mostraEsito(esito, contatto.esito);
                conferma.textContent = 'Risposta registrata.';
                conferma.hidden = false;
                azioni.hidden = true;
            } catch (causa) {
                mostraErrore(causa, assente);
                if ([401, 403, 404, 409].includes(causa.stato)) {
                    azioni.hidden = true;
                }
            } finally {
                pulsanti.forEach(azione => { azione.disabled = false; });
            }
        });
    }
}

async function caricaStorico(richiestaId) {
    const elenco = document.getElementById('storico-contatti');
    const modello = document.getElementById('modello-contatto');
    try {
        const contatti = await api('/api/richieste/' + encodeURIComponent(richiestaId) + '/contatti');
        for (const contatto of contatti) {
            const riga = modello.content.cloneNode(true);
            riga.querySelector('[data-campo=erogatore]').textContent = contatto.erogatoreId;
            riga.querySelector('[data-campo=ambito]').textContent = etichetteAmbito[contatto.ambito];
            mostraEsito(riga.querySelector('[data-campo=esito]'), contatto.esito);
            elenco.append(riga);
        }
        document.getElementById('nessun-contatto').hidden = contatti.length !== 0;
    } catch (causa) {
        mostraErrore(causa, 'Lo storico dei contatti non è disponibile.');
    } finally {
        caricamento.hidden = true;
    }
}

if (pagina.dataset.contattoId) {
    await collegaRisposta(pagina.dataset.contattoId);
} else {
    await caricaStorico(pagina.dataset.richiestaId);
}
