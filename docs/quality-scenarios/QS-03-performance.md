# QS-03 Risposta durante i picchi trimestrali

**Area e priorità:** Performance / P2

## Sorgente

OPT, Banco e Operatori che usano contemporaneamente le funzioni dell’applicazione.

## Stimolo

Un aumento di consultazioni e invii vicino alla scadenza trimestrale mentre continua il coordinamento delle richieste.

## Ambiente

Prova proposta sull’applicazione completa con 50 sessioni concorrenti, 450 OPT, 2.000 richieste sintetiche, le schede di 100 operatori interni ed esterni pagati dal Banco e un inserimento di dati quantitativi per ciascuna OPT. Il sistema parte a freddo: i primi due minuti, a funzionamento nominale, servono a riscaldare la cache; seguono 15 minuti di carico. Configurazione, risorse e rete sono registrate prima della prova.

## Artefatto

Consultazione delle richieste, invio dei moduli, compilazione delle schede degli operatori interni ed esterni pagati dal Banco, inserimento dei dati quantitativi delle OPT e persistenza.

## Risposta

Le operazioni restano utilizzabili e ogni invio registrato dal sistema produce un solo record persistito. I rifiuti autorizzativi attesi sono distinti dagli errori del servizio.

## Misura della risposta

Soglie proposte: 95° percentile entro 2 secondi per consultazioni ed entro 3 secondi per invii; errori di servizio non oltre l’1%; nessun invio registrato perso o duplicato. Mix di carico: 60% consultazioni, 20% richieste, 20% schede e dati quantitativi.

## Stato delle soglie

Carichi e soglie interamente proposti da validare in Q-07. Non sono dedotti dal numero di OPT o dai 230.000 utenti.

## Metodo di verifica previsto

Eseguire un test di carico riproducibile sull’applicazione completa, a fine costruzione, conservando tempi, errori, profilo e conteggi. Nessun test di carico è dichiarato eseguito in S1.
