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
const perId = id => document.getElementById(id);
const pagina = perId('contenuto');
const caricamento = perId('caricamento');
const errore = perId('errore-contatto');

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

async function mostraAssegnazione(contatto) {
    if (contatto.esito !== 'DISPONIBILE') return;
    perId('attesa-assegnazione').hidden = false;
    try {
        const dettaglio = await api('/api/richieste/' + encodeURIComponent(contatto.richiestaId));
        if (dettaglio.erogatoreAssegnatoId !== contatto.erogatoreId) return;
        perId('link-pratica-assegnata').href = '/richieste/' + encodeURIComponent(contatto.richiestaId);
        perId('assegnazione-contatto').hidden = false;
        perId('attesa-assegnazione').hidden = true;
    } catch (causa) {
        // Prima dell'assegnazione il candidato non ha accesso al dettaglio completo.
        if (causa.stato !== 403) {
            perId('attesa-assegnazione').firstChild.textContent = 'Non è stato possibile aggiornare l’assegnazione. ';
        }
    }
}

async function collegaRisposta(contattoId) {
    const percorso = '/api/contatti-disponibilita/' + encodeURIComponent(contattoId);
    const azioni = perId('sezione-azioni');
    const pulsanti = [...azioni.querySelectorAll('button')];
    const esito = perId('esito-contatto');
    try {
        const dettaglio = await api(percorso);
        perId('misura-titolo').textContent = dettaglio.nomeMisura;
        perId('comune-intervento').textContent = dettaglio.comuneCodice;
        perId('tipo-destinatario').textContent = etichetteDestinatario[dettaglio.tipoDestinatario];
        perId('richiesta-id').textContent = dettaglio.contatto.richiestaId;
        perId('contatto-remoto').hidden = dettaglio.contatto.ambito !== 'CENTRO_PICUS_A_DISTANZA';
        perId('dati-contatto').hidden = false;
        mostraEsito(esito, dettaglio.contatto.esito);
        azioni.hidden = dettaglio.contatto.esito !== 'IN_ATTESA';
        perId('risposta-presente').hidden = !azioni.hidden;
        await mostraAssegnazione(dettaglio.contatto);
    } catch (causa) {
        mostraErrore(causa, 'Il contatto non è disponibile.');
        return;
    } finally {
        caricamento.hidden = true;
    }

    for (const pulsante of pulsanti) {
        pulsante.addEventListener('click', async () => {
            pulsanti.forEach(azione => { azione.disabled = true; });
            errore.hidden = true;
            try {
                const disponibile = pulsante.dataset.disponibile === 'true';
                const contatto = await api(percorso + '/risposta', {
                    method: 'POST', body: { disponibile }
                });
                mostraEsito(esito, contatto.esito);
                perId('testo-esito').textContent = disponibile
                    ? 'Hai comunicato la tua disponibilità al Banco. La richiesta resta in presa in carico fino all’assegnazione.'
                    : 'Hai comunicato la tua indisponibilità. Il Banco può interpellare un altro erogatore.';
                perId('messaggio-conferma').hidden = false;
                azioni.hidden = true;
                await mostraAssegnazione(contatto);
            } catch (causa) {
                mostraErrore(causa, 'Il contatto non è disponibile.');
                if ([401, 403, 404, 409].includes(causa.stato)) azioni.hidden = true;
            } finally {
                pulsanti.forEach(azione => { azione.disabled = false; });
            }
        });
    }
}

async function caricaStorico(richiestaId) {
    const elenco = perId('storico-contatti');
    const modello = perId('modello-contatto');
    try {
        const contatti = await api('/api/richieste/' + encodeURIComponent(richiestaId) + '/contatti');
        for (const contatto of contatti) {
            const riga = modello.content.cloneNode(true);
            riga.querySelector('[data-campo=erogatore]').textContent = contatto.erogatoreId;
            riga.querySelector('[data-campo=ambito]').textContent = etichetteAmbito[contatto.ambito];
            mostraEsito(riga.querySelector('[data-campo=esito]'), contatto.esito);
            elenco.append(riga);
        }
        perId('nessun-contatto').hidden = contatti.length !== 0;
    } catch (causa) {
        mostraErrore(causa, 'Lo storico dei contatti non è disponibile.');
    } finally {
        caricamento.hidden = true;
    }
}

if (pagina.dataset.contattiDisponibili === 'true') {
    if (pagina.dataset.contattoId) {
        await collegaRisposta(pagina.dataset.contattoId);
    } else {
        await caricaStorico(pagina.dataset.richiestaId);
    }
}
