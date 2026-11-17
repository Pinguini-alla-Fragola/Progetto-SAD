/** Le funzioni future sono distinte dai comandi operativi temporaneamente bloccati. */
export function segnalaFunzioniInattive() {
    for (const [indice, contenitore] of [...document.querySelectorAll('[data-funzione-inattiva]')].entries()) {
        if (contenitore.querySelector(':scope > .unavailable-note')) continue;
        const nota = document.createElement('span');
        nota.className = 'unavailable-note';
        nota.id = 'funzione-inattiva-' + indice;
        nota.textContent = 'Funzione non ancora implementata';
        contenitore.append(nota);
        for (const controllo of contenitore.querySelectorAll('button, input, select, textarea')) controllo.disabled = true;
        if (contenitore.classList.contains('azione-inattiva')) {
            contenitore.tabIndex = 0;
            contenitore.setAttribute('role', 'group');
            contenitore.setAttribute('aria-disabled', 'true');
            contenitore.setAttribute('aria-describedby', nota.id);
        }
    }
}
