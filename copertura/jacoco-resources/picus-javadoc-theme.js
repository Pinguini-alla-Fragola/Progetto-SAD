(() => {
    const root = document.documentElement;
    const preferenceKey = 'picus-javadoc-theme';
    let preferredTheme;
    try {
        preferredTheme = localStorage.getItem(preferenceKey);
    } catch {
        // Alcuni browser disabilitano lo storage nelle anteprime locali.
    }
    root.dataset.picusTheme = preferredTheme === 'light' ? 'light' : 'dark';

    function initializeThemeToggle() {
        const button = document.querySelector('.picus-theme-toggle');
        if (!button) return;

        function updateButton() {
            button.textContent = root.dataset.picusTheme === 'dark' ? 'Tema chiaro' : 'Tema scuro';
            button.setAttribute('aria-label', 'Attiva ' + button.textContent.toLowerCase());
        }

        button.addEventListener('click', () => {
            const theme = root.dataset.picusTheme === 'dark' ? 'light' : 'dark';
            root.dataset.picusTheme = theme;
            updateButton();
            try {
                localStorage.setItem(preferenceKey, theme);
            } catch {
                // Il tema viene applicato alla pagina anche se non si può salvare.
            }
        });

        updateButton();
        button.hidden = false;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeThemeToggle, { once: true });
    } else {
        initializeThemeToggle();
    }
})();
