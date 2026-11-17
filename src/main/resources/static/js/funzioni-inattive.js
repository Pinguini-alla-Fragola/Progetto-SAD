/** Le funzioni future sono distinte dai comandi operativi temporaneamente bloccati. */
export function segnalaFunzioniInattive() {
    for (const [indice, contenitore] of [...document.querySelectorAll('[data-funzione-inattiva]')].entries()) {
        if (contenitore.querySelector(':scope > .unavailable-note')) continue;
        const nota = document.createElement('span');
        nota.className = 'unavailable-note';
        nota.setAttribute('role', 'tooltip');
        nota.id = 'funzione-inattiva-' + indice;
        nota.textContent = 'Funzione non ancora implementata';
        contenitore.append(nota);
        contenitore.tabIndex = 0;
        contenitore.setAttribute('aria-describedby', nota.id);
        for (const evento of ['pointerenter', 'focus']) {
            contenitore.addEventListener(evento, () => contenitore.classList.remove('tooltip-dismissed'));
        }
        for (const controllo of contenitore.querySelectorAll('button, input, select, textarea')) controllo.disabled = true;
        if (contenitore.classList.contains('azione-inattiva')) {
            contenitore.setAttribute('role', 'group');
            contenitore.setAttribute('aria-disabled', 'true');
        }
    }
}

document.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
        for (const contenitore of document.querySelectorAll('[data-funzione-inattiva]')) {
            contenitore.classList.add('tooltip-dismissed');
        }
    }
});
