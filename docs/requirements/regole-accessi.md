# Regole comuni per account e credenziali

Versione 0.1 del **04/11/2026**. Specifica di supporto alle storie #24/#25/#26/#27/#28/#30/#33/#44. I valori numerici sono proposte tecniche da validare nella #34/Q-16, non una verifica normativa o un’approvazione del cliente.

## Account e permessi

Un account e una email per OPT; account personali degli operatori interni Banco, esterni pagati Banco ed esterni OPT. Appartenenza OPT obbligatoria per l’ultima categoria, distinta da volontari e personale proprio non censiti. Creazione manuale OPT #44 indipendente da CSV #24. Operatore interno e ruolo di coordinamento Banco distinti. [Matrice dei permessi](regole-funzionali.md).

Stati: In attesa di attivazione, Attivo, Revocato. La revoca invalida sessioni e link ancora validi e non cancella i dati. Cambi referente/email, riattivazione e subentro nelle pratiche sono temi distinti (Q-01/Q-15/Q-18); nessuna riattivazione o revoca a cascata automatica.

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

OPT, campi proposti: codice struttura, nome, comune, codice fiscale ed email. Operatore: nome/cognome, email, telefono, codice fiscale, tipologia organizzativa, misure e territorio; per esterno OPT, organizzazione esistente obbligatoria. Campi completi e obbligatorietà ancora da validare.

CSV proposto UTF-8, intestazioni `codice_struttura`, `nome_opt`, `comune`, `codice_fiscale`, `email`:

- File illeggibile o colonna mancante: nessuna riga importata.
- Righe invalide scartate, valide importate; report con riga e motivo. Tutte le righe coinvolte in duplicati interni scartate.
- Nuova OPT valida: account in attesa e link di attivazione.
- OPT esistente non revocata: aggiornare soltanto nome/comune, conservando password, stato ed email. Email differente segnalata per gestione manuale.
- OPT assente dal file: nessuna revoca. OPT revocata: nessuna riattivazione e segnalazione nel report.

Tracciato e regole di aggiornamento sono proposte del team per la #24. Prima della costruzione confermarle con il PO e stimare il perimetro; la creazione ordinaria #44 non dipende da questa funzione opzionale.

## Verifiche per incremento

Server: identità, ruolo, titolarità e assegnazione attendibili; nessun permesso ricavato da campi alterati dal client. Convenzione tecnica: 401 senza autenticazione valida, 403 con autenticazione e permesso mancante; interfaccia con messaggi comprensibili. Le operazioni pubbliche di login, recupero e reinvio seguono le rispettive regole. Prove con dati e account sintetici, senza identificativi di assistiti.
