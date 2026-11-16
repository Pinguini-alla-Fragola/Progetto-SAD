const toggle = document.getElementById('sidebar-toggle');
const sidebar = document.getElementById('workspace-sidebar');
const backdrop = document.querySelector('.sidebar-backdrop');
const narrow = window.matchMedia('(max-width: 900px)');
const links = [...sidebar.querySelectorAll('a')];
const panels = [...document.querySelectorAll('.area-panel')];
let desktopOpen = true;

try {
    desktopOpen = localStorage.getItem('picus.sidebar') !== 'closed';
} catch {
    // La navigazione resta utilizzabile senza persistenza della preferenza.
}

function setSidebar(open, restoreFocus = false) {
    if (!open && (restoreFocus || sidebar.contains(document.activeElement))) {
        toggle.focus();
    }
    sidebar.hidden = !open;
    document.body.classList.toggle('sidebar-open', open);
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? 'Chiudi menu di navigazione' : 'Apri menu di navigazione');
    backdrop.hidden = !(open && narrow.matches);
}

function showSection() {
    const selected = links.find(link => link.hash === location.hash) || links[0];
    for (const link of links) {
        if (link === selected) {
            link.setAttribute('aria-current', 'location');
        } else {
            link.removeAttribute('aria-current');
        }
    }
    for (const panel of panels) {
        panel.hidden = '#' + panel.id !== selected.hash;
    }
}

toggle.addEventListener('click', () => {
    const open = sidebar.hidden;
    if (!narrow.matches) {
        desktopOpen = open;
        try {
            localStorage.setItem('picus.sidebar', open ? 'open' : 'closed');
        } catch {
            // La preferenza è facoltativa.
        }
    }
    setSidebar(open);
    if (open && narrow.matches) {
        sidebar.querySelector('[aria-current]').focus();
    }
});

backdrop.addEventListener('click', () => setSidebar(false, true));
document.addEventListener('keydown', event => {
    if (!narrow.matches || sidebar.hidden) {
        return;
    }
    if (event.key === 'Escape') {
        event.preventDefault();
        setSidebar(false, true);
    } else if (event.key === 'Tab') {
        const controls = [toggle, ...links];
        const current = controls.indexOf(document.activeElement);
        const next = current < 0 ? (event.shiftKey ? controls.length - 1 : 0)
            : (current + (event.shiftKey ? -1 : 1) + controls.length) % controls.length;
        event.preventDefault();
        controls[next].focus();
    }
});

for (const link of links) {
    link.addEventListener('click', event => {
        if (!narrow.matches || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        event.preventDefault();
        const panel = document.getElementById(link.hash.slice(1));
        location.hash = link.hash;
        showSection();
        setSidebar(false);
        panel.focus();
    });
}

narrow.addEventListener('change', () => setSidebar(!narrow.matches && desktopOpen));
window.addEventListener('hashchange', showSection);
showSection();
setSidebar(!narrow.matches && desktopOpen);
