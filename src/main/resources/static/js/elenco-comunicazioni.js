import { api, ErroreHttp } from './area-client.js';
import { communicationCards } from './communication-view.js';

const MESSAGGIO_RETE = 'Connessione non disponibile. Riprova tra poco.';
const ATTESA_RICERCA_MS = 300;
const DESTINATARI = [['TUTTI', 'Tutti'], ['TUTTE_LE_OPT', 'Tutte le OPT'], ['GRUPPO_OPT', 'Gruppo di OPT'],
    ['TUTTI_GLI_OPERATORI', 'Tutti gli operatori'], ['GRUPPO_OPERATORI', 'Gruppo di operatori']];
const ALLEGATI = [['con', 'Con allegati'], ['senza', 'Senza allegati']];
const RICEZIONE = [['da-confermare', 'Da confermare'], ['confermata', 'Confermata']];

const formatoData = valore => new Date(valore).toLocaleString('it-IT',
    { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Rome' });

function el(tag, testo, classe) {
    const nodo = document.createElement(tag);
    if (testo != null) nodo.textContent = testo;
    if (classe) nodo.className = classe;
    return nodo;
}

function pulsante(testo, azione) {
    const nodo = el('button', testo, 'btn btn-secondary btn-sm');
    nodo.type = 'button';
    nodo.addEventListener('click', azione);
    return nodo;
}

function sceltaFiltro(etichetta, chiave, voci) {
    const contenitore = el('label', null, 'campo');
    const scelta = el('select', null, 'form-control');
    scelta.dataset.filtro = chiave;
    scelta.add(new Option('Tutti', ''));
    for (const [valore, testo] of voci) scelta.add(new Option(testo, valore));
    contenitore.append(el('span', etichetta), scelta);
    return { contenitore, scelta };
}

/**
 * Tiene conto di quale richiesta è l'ultima partita: una risposta arrivata in ritardo, di successo o di errore,
 * non deve sostituire quella di una richiesta più recente.
 */
export function ultimaRichiesta() {
    let ultima = 0;
    return () => {
        const questa = ++ultima;
        return () => questa === ultima;
    };
}

/**
 * Elenco delle comunicazioni chiesto al server una pagina alla volta: ricerca, filtri e numero della pagina
 * diventano parametri della richiesta, così valgono su tutto lo storico e il browser riceve solo la pagina
 * mostrata. Usa le stesse classi grafiche delle altre liste dell'area.
 */
export class ElencoComunicazioni {

    constructor(contenitore, { banco }) {
        this.contenitore = contenitore;
        this.banco = banco;
        this.pagina = 0;
        this.nuovaRichiesta = ultimaRichiesta();
        this.costruisci();
    }

    costruisci() {
        const strumenti = el('div', null, 'list-tools');
        const ricerca = el('label', null, 'list-search campo');
        this.ricerca = el('input', null, 'form-control');
        this.ricerca.type = 'search';
        this.ricerca.autocomplete = 'off';
        this.ricerca.maxLength = 100;
        this.ricerca.placeholder = 'Oggetto, testo o nome allegato';
        ricerca.append(el('span', 'Cerca comunicazioni'), this.ricerca);
        const filtri = el('div', null, 'list-filters');
        this.filtri = [sceltaFiltro('Destinatari', 'destinatari', DESTINATARI),
            sceltaFiltro('Allegati', 'allegati', ALLEGATI)];
        if (!this.banco) this.filtri.push(sceltaFiltro('Ricezione', 'ricezione', RICEZIONE));
        filtri.append(...this.filtri.map(filtro => filtro.contenitore));
        strumenti.append(ricerca, filtri);

        this.riepilogo = el('p', null, 'list-summary');
        this.riepilogo.setAttribute('role', 'status');
        this.azzera = pulsante('Cancella filtri', () => this.cancellaFiltri());
        this.azzera.classList.add('btn-quiet');
        const stato = el('div', null, 'list-state');
        stato.append(this.riepilogo, this.azzera);

        this.voci = el('div', null, 'list-items');
        this.voci.setAttribute('role', 'list');
        this.vuoto = el('p', null, 'picus-empty');
        this.pagine = el('div', null, 'list-pagination');
        this.precedente = pulsante('Precedente', () => this.vaiA(this.pagina - 1));
        this.successiva = pulsante('Successiva', () => this.vaiA(this.pagina + 1));
        this.etichettaPagina = el('span', null, 'picus-meta');
        const bottoni = el('div', null, 'page-buttons');
        bottoni.append(this.precedente, this.etichettaPagina, this.successiva);
        this.pagine.append(bottoni);

        let attesa;
        this.ricerca.addEventListener('input', () => {
            clearTimeout(attesa);
            attesa = setTimeout(() => this.vaiA(0), ATTESA_RICERCA_MS);
        });
        this.ricerca.addEventListener('keydown', evento => {
            if (evento.key === 'Enter') evento.preventDefault();
        });
        for (const { scelta } of this.filtri) scelta.addEventListener('change', () => this.vaiA(0));

        this.contenitore.removeAttribute('aria-live');
        this.contenitore.classList.add('list-view');
        this.contenitore.replaceChildren(strumenti, stato, this.voci, this.vuoto, this.pagine);
    }

    parametri() {
        const parametri = new URLSearchParams({ pagina: String(this.pagina) });
        const testo = this.ricerca.value.trim();
        if (testo) parametri.set('testo', testo);
        for (const { scelta } of this.filtri) {
            if (scelta.value) parametri.set(scelta.dataset.filtro, scelta.value);
        }
        return parametri;
    }

    vaiA(pagina) {
        this.pagina = Math.max(0, pagina);
        return this.carica();
    }

    cancellaFiltri() {
        this.ricerca.value = '';
        for (const { scelta } of this.filtri) scelta.value = '';
        this.ricerca.focus();
        return this.vaiA(0);
    }

    /** Chiede la pagina corrente; se nel frattempo è partita un'altra richiesta, questa risposta si scarta. */
    async carica() {
        const attuale = this.nuovaRichiesta();
        const indirizzo = (this.banco ? '/api/comunicazioni/inviate?' : '/api/comunicazioni/ricevute?')
            + this.parametri();
        this.contenitore.setAttribute('aria-busy', 'true');
        try {
            const risposta = await api(indirizzo);
            if (attuale()) this.mostra(risposta);
        } catch (causa) {
            if (attuale()) this.mostraProblema(causa instanceof ErroreHttp ? causa.message : MESSAGGIO_RETE);
        } finally {
            if (attuale()) this.contenitore.removeAttribute('aria-busy');
        }
    }

    mostra({ elementi, pagina, dimensione, totale }) {
        const pagineTotali = Math.max(1, Math.ceil(totale / dimensione));
        // Se la pagina chiesta non esiste più (per esempio dopo un filtro), si torna all'ultima disponibile.
        if (!elementi.length && totale > 0 && pagina >= pagineTotali) {
            this.vaiA(pagineTotali - 1);
            return;
        }
        this.pagina = pagina;
        this.voci.replaceChildren(...communicationCards(elementi, {
            banco: this.banco, formatDate: formatoData, onConfirm: () => this.carica(),
            conRiscontri: this.banco, conConferma: !this.banco
        }));
        const filtrato = Boolean(this.ricerca.value.trim()) || this.filtri.some(({ scelta }) => scelta.value);
        const primo = pagina * dimensione + 1;
        this.riepilogo.textContent = totale
            ? `${primo}–${primo + elementi.length - 1} di ${totale} ${totale === 1 ? 'risultato' : 'risultati'}`
            : '0 risultati';
        this.vuoto.textContent = filtrato ? 'Nessun risultato con questi filtri.' : 'Nessuna comunicazione.';
        this.vuoto.hidden = totale > 0;
        this.azzera.hidden = !filtrato;
        this.precedente.disabled = pagina === 0;
        this.successiva.disabled = pagina + 1 >= pagineTotali;
        this.etichettaPagina.textContent = `Pagina ${pagina + 1} di ${pagineTotali}`;
        this.pagine.hidden = pagineTotali === 1;
    }

    mostraProblema(testo) {
        const paragrafo = el('p', testo, 'picus-esito picus-errore');
        this.voci.replaceChildren(paragrafo);
        this.riepilogo.textContent = '';
        this.vuoto.hidden = true;
        this.pagine.hidden = true;
    }
}
