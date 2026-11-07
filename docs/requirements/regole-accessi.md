# Regole comuni per account e credenziali

Versione 0.2 del **07/11/2026**. Specifica di supporto a #24/#25/#26/#27/#28/#30/#33/#44. Le soglie tecniche restano da validare in #34/Q-16.

## Account e permessi

Ogni OPT usa un account organizzativo e una email, anche quando eroga tramite un professionista esterno. Gli account personali di ruolo Operatore spettano agli interni del Banco e agli esterni pagati dal Banco. La tipologia organizzativa è distinta dalla qualifica professionale e il server accetta soltanto queste due tipologie. La creazione manuale OPT #44 è indipendente da CSV #24. Il coordinamento Banco usa un account distinto. [Matrice dei permessi](regole-funzionali.md).

Stati ammessi per l’utenza sono In attesa di attivazione, Attivo e Revocato. #26 revoca il solo account organizzativo OPT e #30 il singolo account personale del Banco, invalidando sessioni e link ancora validi e conservando i dati. Subentro nelle pratiche in #67/Q-18, conservazione in #42 e continuità/riattivazione in #65/Q-01/Q-15.

## Policy proposta e link

| Aspetto | Proposta da validare |
| --- | --- |
| Password | Almeno 12 caratteri; stessa policy per attivazione e recupero |
| Attivazione | Link email monouso valido 72 ore; l’utente sceglie la password |
| Reinvio attivazione | Solo account in attesa; nuovo link invalida il precedente |
| Recupero | Solo account attivo, link monouso di 15 minuti |
| Password cambiata | Nuova password valida, precedente non più utilizzabile |
| Tentativi ripetuti | Cinque fallimenti in cinque minuti; attesa proposta di 60 secondi |
| Messaggi pubblici | Stesso riscontro per email esistente o non abilitata, senza rivelare l’account |
| Logout | Sessione terminata sul server; successivo riuso della sessione negato |

Account in attesa usano il reinvio attivazione, non il recupero. Link scaduti/usati o associati a un revocato non cambiano la password né attivano l’account. Non si inviano password temporanee. Durata complessiva delle sessioni e ulteriori dettagli tecnici da definire.

## Dati e import CSV

Campi proposti per la OPT sono codice struttura, nome, comune, codice fiscale ed email. Per l’operatore sono nome/cognome, email, telefono, codice fiscale, tipologia (interno del Banco o esterno pagato dal Banco), misure e territorio. Elenco completo e obbligatorietà restano da validare.

CSV proposto UTF-8, intestazioni `codice_struttura`, `nome_opt`, `comune`, `codice_fiscale`, `email`:

- File illeggibile o colonna mancante: nessuna riga importata.
- Righe invalide scartate, valide importate; report con riga e motivo. Tutte le righe coinvolte in duplicati interni scartate.
- Nuova OPT valida: account in attesa e link di attivazione.
- OPT esistente non revocata: aggiornare soltanto nome/comune, conservando password, stato ed email. Email differente segnalata per gestione manuale.
- OPT assente dal file: nessuna revoca. OPT revocata: nessuna riattivazione e segnalazione nel report.

Tracciato e regole di aggiornamento sono proposte del team per la #24. Prima della costruzione confermarle con il PO e stimare il perimetro; la creazione ordinaria #44 non dipende da questa funzione opzionale.

## Verifiche per incremento

Server: identità, ruolo, titolarità e assegnazione attendibili; nessun permesso ricavato da campi alterati dal client. Convenzione tecnica: 401 senza autenticazione valida, 403 con autenticazione e permesso mancante; interfaccia con messaggi comprensibili. Le operazioni pubbliche di login, recupero e reinvio seguono le rispettive regole. Prove con dati e account sintetici, senza identificativi di assistiti.
