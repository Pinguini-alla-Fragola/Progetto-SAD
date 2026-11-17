export function setupWorkspace() {
    const toggle = document.getElementById('sidebar-toggle');
    const sidebar = document.getElementById('workspace-sidebar');
    const backdrop = document.querySelector('.sidebar-backdrop');
    const narrow = window.matchMedia('(max-width: 900px)');
    let desktopOpen = true;
    try { desktopOpen = localStorage.getItem('picus.sidebar') !== 'closed'; } catch { /* Storage may be disabled. */ }

    function setSidebar(open, restoreFocus = false) {
        if (!toggle || !sidebar) return;
        if (!open && (restoreFocus || sidebar.contains(document.activeElement))) toggle.focus();
        sidebar.hidden = !open;
        document.body.classList.toggle('sidebar-open', open);
        toggle.setAttribute('aria-expanded', String(open));
        toggle.setAttribute('aria-label', open ? 'Chiudi menu di navigazione' : 'Apri menu di navigazione');
        backdrop.hidden = !(open && narrow.matches);
    }

    toggle?.addEventListener('click', () => {
        const open = toggle.getAttribute('aria-expanded') !== 'true';
        if (!narrow.matches) {
            desktopOpen = open;
            try { localStorage.setItem('picus.sidebar', open ? 'open' : 'closed'); } catch { /* Optional preference. */ }
        }
        setSidebar(open);
        if (open && narrow.matches) sidebar.querySelector('[aria-current]')?.focus();
    });
    backdrop?.addEventListener('click', () => setSidebar(false, true));
    document.addEventListener('keydown', event => {
        if (!narrow.matches || toggle?.getAttribute('aria-expanded') !== 'true') return;
        if (event.key === 'Escape') {
            event.preventDefault();
            setSidebar(false, true);
        } else if (event.key === 'Tab') {
            const links = [...sidebar.querySelectorAll('a')];
            const controls = [toggle, ...links];
            const index = controls.indexOf(document.activeElement);
            const next = index < 0 ? (event.shiftKey ? controls.length - 1 : 0)
                : (index + (event.shiftKey ? -1 : 1) + controls.length) % controls.length;
            event.preventDefault();
            controls[next].focus();
        }
    });
    narrow.addEventListener('change', () => setSidebar(!narrow.matches && desktopOpen));
    setSidebar(!narrow.matches && desktopOpen);

    for (const group of document.querySelectorAll('[data-tabs]')) {
        const tabs = [...group.querySelectorAll(':scope > [role=tablist] > [role=tab]')];
        function activate(tab, focus = false) {
            for (const item of tabs) {
                const selected = item === tab;
                item.setAttribute('aria-selected', String(selected));
                item.tabIndex = selected ? 0 : -1;
                document.getElementById(item.getAttribute('aria-controls')).hidden = !selected;
            }
            if (focus) tab.focus();
        }
        for (const [index, tab] of tabs.entries()) {
            tab.addEventListener('click', () => activate(tab));
            tab.addEventListener('keydown', event => {
                let next;
                if (event.key === 'ArrowRight') next = tabs[(index + 1) % tabs.length];
                if (event.key === 'ArrowLeft') next = tabs[(index - 1 + tabs.length) % tabs.length];
                if (event.key === 'Home') next = tabs[0];
                if (event.key === 'End') next = tabs.at(-1);
                if (next) { event.preventDefault(); activate(next, true); }
            });
        }
        activate(tabs[0]);
    }

    function updateSection() {
        const current = sidebar?.querySelector('[aria-current]');
        const title = document.getElementById('workspace-title');
        if (current && title) {
            title.textContent = current.textContent.trim();
            document.title = title.textContent + ' · PICUS';
        }
    }
    for (const link of sidebar?.querySelectorAll('a') || []) {
        link.addEventListener('click', event => {
            if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
            if (narrow.matches) setSidebar(false);
            // area.js activates the section in the same event dispatch.
            queueMicrotask(updateSection);
        });
    }
    window.addEventListener('hashchange', updateSection);
    queueMicrotask(updateSection);
}
