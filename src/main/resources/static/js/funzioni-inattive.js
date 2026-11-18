/** Le funzioni future sono distinte dai comandi operativi temporaneamente bloccati. */
export function segnalaFunzioniInattive() {
    for (const form of document.querySelectorAll('[data-form-anteprima]')) form.setAttribute('data-funzione-inattiva', '');
    for (const [indice, contenitore] of [...document.querySelectorAll('[data-funzione-inattiva]')].entries()) {
        if (contenitore.parentElement.closest('[data-funzione-inattiva]')) continue;
        if (contenitore.querySelector(':scope > .unavailable-note')) continue;
        const nota = document.createElement('span');
        nota.className = 'unavailable-note';
        nota.setAttribute('role', 'tooltip');
        nota.id = 'funzione-inattiva-' + indice;
        nota.textContent = 'Funzione non ancora implementata';
        contenitore.append(nota);
        contenitore.tabIndex = 0;
        contenitore.setAttribute('aria-describedby', nota.id);
        let chiusura;
        const posiziona = (x, y) => {
            const margine = 12;
            const { width, height } = nota.getBoundingClientRect();
            nota.style.left = Math.max(margine, Math.min(x, window.innerWidth - width - margine)) + 'px';
            nota.style.top = Math.max(margine, Math.min(y, window.innerHeight - height - margine)) + 'px';
        };
        const vicinoAlPuntatore = event => {
            if (event.pointerType === 'touch' || contenitore.classList.contains('tooltip-dismissed')) return;
            const altezza = nota.getBoundingClientRect().height;
            const y = event.clientY + 18 + altezza <= window.innerHeight - 12
                ? event.clientY + 18 : event.clientY - altezza - 18;
            posiziona(event.clientX + 16, y);
        };
        const mostra = () => {
            clearTimeout(chiusura);
            contenitore.classList.remove('tooltip-dismissed');
            contenitore.classList.add('tooltip-open');
        };
        const chiudi = () => {
            chiusura = setTimeout(() => {
                if (!contenitore.matches(':hover, :focus-visible')) contenitore.classList.remove('tooltip-open');
            }, 150);
        };
        contenitore.addEventListener('pointerenter', event => {
            if (event.pointerType === 'touch') return;
            mostra();
            vicinoAlPuntatore(event);
        });
        contenitore.addEventListener('pointermove', vicinoAlPuntatore);
        contenitore.addEventListener('pointerleave', chiudi);
        contenitore.addEventListener('focus', () => {
            if (!contenitore.matches(':focus-visible')) return;
            mostra();
            const riquadro = contenitore.getBoundingClientRect();
            const altezza = nota.getBoundingClientRect().height;
            const y = riquadro.height > altezza + 48 ? Math.max(riquadro.top, 0) + 16
                : riquadro.top >= altezza + 24 ? riquadro.top - altezza - 12 : riquadro.bottom + 12;
            posiziona(riquadro.left + 12, y);
        });
        contenitore.addEventListener('blur', chiudi);
        window.addEventListener('scroll', () => contenitore.classList.remove('tooltip-open'), { passive: true });
        window.addEventListener('resize', () => contenitore.classList.remove('tooltip-open'));
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
