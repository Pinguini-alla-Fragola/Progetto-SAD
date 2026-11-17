/** Dialog nativo: tastiera, Escape e focus restano gestiti dal browser. */
export function confermaPresaInCarico(misura, opt) {
    return confermaOperazione({
        titolo: 'Prendere in carico questa richiesta?', richiesta: [misura, opt].filter(Boolean).join(' · '),
        testo: 'La richiesta passerà da Creata a Presa in carico. Potrai quindi cercare un erogatore e chiedere la sua disponibilità.',
        azione: 'Conferma presa in carico'
    });
}

export function confermaOperazione({ titolo: intestazione, richiesta: riepilogo, testo: descrizione, azione, pericolo = false }) {
    const dialog = document.createElement('dialog');
    dialog.className = 'picus-dialog confirmation-dialog';
    dialog.setAttribute('aria-labelledby', 'titolo-conferma-presa');
    dialog.setAttribute('aria-describedby', 'testo-conferma-presa');
    const titolo = document.createElement('h2');
    titolo.id = 'titolo-conferma-presa';
    titolo.textContent = intestazione;
    const richiesta = document.createElement('p');
    richiesta.className = 'confirmation-request';
    richiesta.textContent = riepilogo;
    const testo = document.createElement('p');
    testo.id = 'testo-conferma-presa';
    testo.textContent = descrizione;
    const form = document.createElement('form');
    form.method = 'dialog';
    form.className = 'picus-actions';
    for (const [testo, valore, classe] of [['Annulla', 'annulla', 'secondary'], [azione, 'conferma', pericolo ? 'danger' : 'primary']]) {
        const button = document.createElement('button');
        button.type = 'submit';
        button.value = valore;
        button.className = 'btn btn-' + classe;
        button.textContent = testo;
        if (valore === 'annulla') button.autofocus = true;
        form.append(button);
    }
    dialog.append(titolo, richiesta, testo, form);
    document.body.append(dialog);
    return new Promise(resolve => {
        dialog.addEventListener('close', () => {
            const confermata = dialog.returnValue === 'conferma';
            dialog.remove();
            resolve(confermata);
        }, { once: true });
        dialog.showModal();
    });
}
