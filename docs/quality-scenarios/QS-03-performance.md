# QS-03 Risposta durante i picchi trimestrali

Versione 0.1 del 25/10/2026. Stato: **proposta da validare**; nessuna prova software già attestata.

**Area e priorità proposta:** Performance / P2

**Fonti e collegamenti:** F02 §7; US-D05–US-D09 e, quando disponibile, US-D13.

## Sorgente

OPT, Banco e Operatori che usano contemporaneamente le funzioni disponibili.

## Stimolo

Un aumento di consultazioni e invii vicino alla scadenza trimestrale mentre continua il coordinamento delle richieste.

## Ambiente

Prova proposta con 50 sessioni concorrenti, 450 OPT e 2.000 richieste sintetiche; aggiungere 100 schede quando il modulo esiste. Due minuti di riscaldamento e 15 minuti di carico. Configurazione, risorse e rete sono registrate prima della prova.

## Artefatto

Consultazione delle richieste, invio dei moduli e persistenza; successivamente compilazione delle schede.

## Risposta

Le operazioni restano utilizzabili e ogni invio confermato produce un solo record persistito. I rifiuti autorizzativi attesi sono distinti dagli errori del servizio.

## Misura della risposta

Soglie proposte: 95° percentile entro 2 secondi per consultazioni ed entro 3 secondi per invii; errori di servizio non oltre l’1%; nessun invio confermato perso o duplicato. Nel profilo esteso: 60% consultazioni, 20% richieste, 20% schede.

## Stato delle soglie

Carichi e soglie interamente proposti da validare in Q-07. Non sono dedotti dal numero di OPT o dai 230.000 utenti. L’MVP prova il sottoinsieme delle richieste e non dichiara superato il futuro carico sulle schede.

## Metodo di verifica previsto

Eseguire un test di carico riproducibile dopo l’implementazione, conservando tempi, errori, profilo e conteggi. Ripetere il profilo esteso quando esiste la rendicontazione; nessun test di carico è dichiarato eseguito in S1.
