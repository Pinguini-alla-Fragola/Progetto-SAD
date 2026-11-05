# Quality scenarios

Catalogo di lavoro degli scenari di qualità. La sezione A5 del PDF pubblicato nella repository raccoglie gli scenari della versione **v0.7** del documento. Ogni scheda segue il template con Source, Stimulus, Environment, Artifact, Response e Response measure, accompagnato da attributo di qualità, priorità, motivazione e approccio di verifica.

**Stato:** proposte da validare. Le soglie introdotte dal team non sono SLA concordati o risultati di prove software già eseguite.

| Scenario | Area | Priorità proposta |
| --- | --- | --- |
| [QS-01A Consultazione non autorizzata di una richiesta](QS-01A-consultazione-richieste.md) | Sicurezza | P1 |
| [QS-01B Annullamento non autorizzato di una richiesta confermata](QS-01B-annullamento-richieste.md) | Sicurezza | P1 |
| [QS-01C Revoca di un account con sessione già aperta](QS-01C-revoca-sessione.md) | Sicurezza | P1 |
| [QS-02A Esclusione degli identificativi degli assistiti dalle richieste](QS-02A-identificativi-assistiti.md) | Privacy | P1 |
| [QS-02B Visibilità dei dati dell’operatore nel dettaglio della richiesta](QS-02B-visibilita-dati-operatore.md) | Privacy | P1 |
| [QS-03 Consultazione delle richieste durante il picco trimestrale](QS-03-performance.md) | Performance | P2 |
| [QS-04A Inviare una richiesta da smartphone](QS-04A-invio-richiesta-smartphone.md) | Usabilità | P1 |
| [QS-04B Ritrovare i dettagli di un appuntamento da smartphone](QS-04B-dettagli-appuntamento-smartphone.md) | Usabilità | P1 |
| [QS-04C Rispondere a una richiesta di disponibilità da smartphone](QS-04C-risposta-disponibilita-smartphone.md) | Usabilità | P1 |
| [QS-05A Ripristino dopo il fermo dell’applicazione](QS-05A-ripristino-applicazione.md) | Disponibilità | P2 |
| [QS-05B Ripristino da backup dopo la perdita del database](QS-05B-ripristino-da-backup.md) | Disponibilità | P2 |
| [QS-06 Aggiornamento del catalogo senza rilascio software](QS-06-modificabilita.md) | Modificabilità | P2 |
| [QS-07 Ripetere un invio dopo un’interruzione della connessione](QS-07-ritentativo-invio.md) | Affidabilità | P2 |

Gli scenari si verificano a fine costruzione, sull’applicazione completa. QS-01A e QS-01B riguardano i permessi di consultazione e annullamento delle richieste. QS-01C riguarda la revoca di un account con sessione già aperta. QS-02A e QS-02B riguardano l’esclusione degli identificativi degli assistiti dalle richieste e la visibilità dei dati degli operatori. QS-03 riguarda il carico trimestrale. QS-04A, QS-04B e QS-04C riguardano l’uso da smartphone. QS-05A e QS-05B riguardano il ripristino dopo il fermo dell’applicazione o la perdita del database. QS-06 riguarda la configurazione del catalogo. QS-07 riguarda la ripetizione di un invio dopo un’interruzione della connessione. Le schede descrivono le prove previste per il prodotto.

Q-07 raccoglie la validazione dei carichi e delle soglie. Il numero delle OPT (circa 450) dimensiona i dati di prova, non la concorrenza; i profili non sono dedotti dal dato 230.000 utenti. QS-06 valuta la modificabilità limitatamente agli aggiornamenti del catalogo tramite configurazione.

[Documento PDF](../Documentazione_SAD_2026.pdf) · [Proposta di MVP](../requirements/proposta-mvp.md) · [Registro dei chiarimenti](../requirements/open-questions.md)
