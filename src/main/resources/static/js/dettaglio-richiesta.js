import { avviaCoordinamento } from './richieste-area.js';
import { avviaEsitoRichiesta } from './accordo.js';
import { segnalaFunzioniInattive } from './funzioni-inattive.js';

segnalaFunzioniInattive();

avviaEsitoRichiesta(document.getElementById('contenuto'));

avviaCoordinamento(document.getElementById('contenuto'));
