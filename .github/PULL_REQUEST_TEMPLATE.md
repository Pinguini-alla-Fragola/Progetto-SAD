# Pull Request

## Problema e risultato

<!-- Quale problema risolve? Quale comportamento o artefatto cambia? -->

## Issue e criteri di accettazione

<!-- Usa Closes #123 solo per un'issue effettivamente completata.
Per contributi parziali usa Refs #123. Sostituisci gli esempi con issue reali.
Elenca i criteri soddisfatti e le eventuali parti ancora aperte. -->

## Modifiche e motivazioni

<!-- Riporta le scelte utili alla review, i file/artefatti coinvolti e le
eventuali alternative o decisioni che richiedono confronto nel team. -->

## Verifiche effettuate

<!-- Specifica comandi, risultati ed evidenze. Per una modifica documentale
descrivi controllo delle fonti, coerenza e tracciabilità. Per il codice indica
build e test. Se un controllo non è applicabile o non è stato eseguito,
dichiaralo e motivalo: non presentarlo come superato. -->

## Impatto e punti aperti

<!-- Requisiti, ADR/UML, confini dei moduli, configurazione, privacy,
dipendenze o attività successive. Scrivi "Nessuno" se pertinente. -->

## Checklist comune

- [ ] La PR ha uno scopo chiaro ed è collegata alle issue pertinenti.
- [ ] I criteri di accettazione soddisfatti e quelli ancora aperti sono espliciti.
- [ ] Documenti, esempi e log non contengono credenziali o dati personali reali.
- [ ] Fonti, requisiti concordati, proposte e ipotesi sono distinguibili.
- [ ] La documentazione e gli altri artefatti coinvolti sono coerenti.
- [ ] Ho esaminato il diff, inclusi eventuali contenuti preparati con assistenza IA.

## Verifiche specifiche

<!-- Spunta soltanto ciò che è applicabile e completato. Spiega sopra gli N/A.
Le caselle non applicabili non devono essere spuntate come se fossero verificate. -->

- [ ] Codice: compilazione e test pertinenti superati con Maven Wrapper.
- [ ] Codice: JUnit 5 senza Mockito; per un bug è provata la regressione.
- [ ] Architettura: MVC, DTO, isolamento dei moduli e decisioni GRASP/GoF rispettati.
- [ ] Architettura: ADR e diagrammi Visual Paradigm aggiornati quando necessario.
- [ ] Documentazione: fonti, terminologia, link e tracciabilità verificati.
- [ ] `.github/` o `.gitignore`: richiesta la review a `@CapHello` o `@giob22`.
