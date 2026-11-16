import { filterItems, paginate } from './list-state.js';

const views = new WeakMap();
const collator = new Intl.Collator('it', { numeric: true, sensitivity: 'base' });
let nextId = 0;

function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = text;
    if (className) node.className = className;
    return node;
}

function button(text, action) {
    const node = el('button', text, 'btn btn-secondary btn-sm');
    node.type = 'button';
    node.addEventListener('click', action);
    return node;
}

class ListView {
    constructor(container) {
        this.container = container;
        this.id = container.id || 'lista-' + ++nextId;
        this.page = 1;
        this.pageSize = 10;
        this.filterControls = new Map();
        this.tools = el('div', null, 'list-tools');
        const searchLabel = el('label', null, 'list-search campo');
        this.searchText = el('span');
        this.search = el('input', null, 'form-control');
        this.search.type = 'search';
        this.search.autocomplete = 'off';
        this.search.placeholder = 'Nome o parola chiave';
        this.search.id = this.id + '-search';
        searchLabel.append(this.searchText, this.search);
        this.filterBox = el('div', null, 'list-filters');
        this.tools.append(searchLabel, this.filterBox);
        this.search.addEventListener('input', () => { this.page = 1; this.render(); });
        this.search.addEventListener('keydown', event => {
            if (event.key === 'Enter') event.preventDefault();
        });

        this.summary = el('p', null, 'list-summary');
        this.summary.setAttribute('role', 'status');
        this.summary.setAttribute('aria-live', 'polite');
        this.reset = button('Cancella filtri', () => this.clearFilters());
        this.reset.classList.add('btn-quiet');
        const state = el('div', null, 'list-state');
        state.append(this.summary, this.reset);
        this.selection = el('div', null, 'selection-tools');
        this.selectedCount = el('strong');
        this.selectedCount.setAttribute('role', 'status');
        this.selectResults = button('Seleziona risultati', () => {
            for (const entry of this.matching()) this.checkbox(entry.row).checked = true;
            this.render();
        });
        this.clearSelection = button('Azzera selezione', () => {
            for (const entry of this.entries) this.checkbox(entry.row).checked = false;
            this.render();
        });
        const only = el('label', null, 'picus-check');
        this.onlySelected = el('input');
        this.onlySelected.type = 'checkbox';
        only.append(this.onlySelected, el('span', 'Solo selezionati'));
        this.onlySelected.addEventListener('change', () => { this.page = 1; this.render(); });
        this.selection.append(this.selectedCount, only, this.selectResults, this.clearSelection);
        this.columns = el('div', null, 'list-columns');
        this.columns.setAttribute('aria-hidden', 'true');
        this.items = el('div', null, 'list-items');
        this.items.setAttribute('role', 'list');
        this.items.addEventListener('change', () => this.render());
        this.empty = el('p', null, 'picus-empty');
        this.pager = el('div', null, 'list-pagination');
        this.previous = button('Precedente', () => { this.page--; this.render(true); });
        this.next = button('Successiva', () => { this.page++; this.render(true); });
        this.pageLabel = el('span', null, 'picus-meta');
        const pageSizeLabel = el('label', null, 'page-size');
        this.size = el('select', null, 'form-control');
        [10, 25, 50].forEach(size => this.size.add(new Option(String(size), String(size))));
        this.size.addEventListener('change', () => { this.pageSize = Number(this.size.value); this.page = 1; this.render(); });
        pageSizeLabel.append(el('span', 'Per pagina'), this.size);
        this.pageButtons = el('div', null, 'page-buttons');
        this.pageButtons.append(this.previous, this.pageLabel, this.next);
        this.pager.append(pageSizeLabel, this.pageButtons);
        container.removeAttribute('aria-live');
        container.classList.add('list-view');
        container.replaceChildren(this.tools, this.selection, state, this.columns, this.items, this.empty, this.pager);
        container.closest('form')?.addEventListener('reset', () => queueMicrotask(() => {
            this.pageSize = Number(this.size.value) || 10;
            this.page = 1;
            this.render();
        }));
    }

    checkbox(row) { return row.querySelector('input[type=checkbox]'); }

    update(rows, options) {
        this.options = options;
        this.entries = rows.map(row => ({ row, text: row.listText ?? row.textContent,
            filters: row.listFilters || { ...row.dataset } }));
        rows.forEach(row => row.setAttribute('role', 'listitem'));
        this.items.replaceChildren(...rows);
        this.searchText.textContent = options.searchLabel || 'Cerca nell’elenco';
        this.search.placeholder = options.placeholder || 'Nome o parola chiave';
        this.selection.hidden = !options.selectable;
        this.columns.replaceChildren(...(options.columns || []).map(text => el('span', text)));
        this.columns.hidden = !options.columns?.length;
        const activeKeys = new Set();
        for (const definition of options.filters || []) {
            activeKeys.add(definition.key);
            let control = this.filterControls.get(definition.key);
            if (!control) {
                const label = el('label', null, 'campo');
                control = el('select', null, 'form-control');
                control.dataset.filter = definition.key;
                label.append(el('span', definition.label), control);
                this.filterBox.append(label);
                this.filterControls.set(definition.key, control);
                control.addEventListener('change', () => { this.page = 1; this.render(); });
            }
            const selected = control.value;
            const values = [...new Set(this.entries.flatMap(entry => {
                const value = entry.filters[definition.key];
                return Array.isArray(value) ? value : [value];
            }).filter(value => value != null && value !== '').map(String))].sort(collator.compare);
            control.replaceChildren(new Option(definition.all || 'Tutti', ''));
            values.forEach(value => control.add(new Option(definition.labels?.[value] || value, value)));
            control.value = values.includes(selected) ? selected : '';
            control.parentElement.hidden = !values.length;
        }
        for (const [key, control] of this.filterControls) {
            if (!activeKeys.has(key)) { control.parentElement.remove(); this.filterControls.delete(key); }
        }
        this.filterBox.hidden = [...this.filterControls.values()].every(control => control.parentElement.hidden);
        this.render();
        return this;
    }

    get filters() { return Object.fromEntries([...this.filterControls].map(([key, control]) => [key, control.value])); }

    matching() {
        const entries = filterItems(this.entries, this.search.value, this.filters);
        return this.options.selectable && this.onlySelected.checked
            ? entries.filter(entry => this.checkbox(entry.row).checked) : entries;
    }

    clearFilters() {
        this.search.value = '';
        this.filterControls.forEach(control => { control.value = ''; });
        this.onlySelected.checked = false;
        this.page = 1;
        this.render();
        this.search.focus();
    }

    render(focusSummary = false) {
        if (!this.entries) return;
        const matching = this.matching();
        const slice = paginate(matching, this.page, this.pageSize);
        const focusedRow = this.entries.find(entry => entry.row.contains(document.activeElement));
        this.page = slice.page;
        const visible = new Set(slice.items);
        this.entries.forEach(entry => { entry.row.hidden = !visible.has(entry); });
        this.empty.hidden = slice.total > 0;
        this.empty.textContent = this.entries.length ? 'Nessun risultato con questi filtri.' : this.options.empty || 'Nessun elemento disponibile.';
        this.summary.textContent = slice.total
            ? `${slice.start}–${slice.end} di ${slice.total} ${slice.total === 1 ? 'risultato' : 'risultati'}${matching.length !== this.entries.length ? ' · ' + this.entries.length + ' totali' : ''}`
            : `0 risultati${this.entries.length ? ' · ' + this.entries.length + ' totali' : ''}`;
        this.reset.hidden = !this.search.value && !Object.values(this.filters).some(Boolean) && !this.onlySelected.checked;
        this.previous.disabled = slice.page === 1;
        this.next.disabled = slice.page === slice.pages;
        this.pageLabel.textContent = `Pagina ${slice.page} di ${slice.pages}`;
        this.pager.hidden = this.entries.length <= 10;
        this.pageButtons.hidden = slice.pages === 1;
        if (this.options.selectable) {
            const selected = this.entries.filter(entry => this.checkbox(entry.row).checked).length;
            this.selectedCount.textContent = selected + (selected === 1 ? ' selezionato' : ' selezionati');
            this.selectResults.disabled = !matching.length;
            this.clearSelection.disabled = selected === 0;
        }
        this.options.onFilter?.(matching.map(entry => entry.row), this.filters);
        if (focusedRow?.row.hidden) {
            this.search.focus({ preventScroll: true });
        }
        if (focusSummary) {
            this.summary.tabIndex = -1;
            this.summary.focus({ preventScroll: true });
        }
    }
}

export function renderList(container, rows, options = {}) {
    let view = views.get(container);
    if (!view) { view = new ListView(container); views.set(container, view); }
    return view.update(rows, options);
}

export function listRow(row, { text, filters = {}, data } = {}) {
    if (text != null) row.listText = text;
    row.listFilters = filters;
    row.listData = data;
    return row;
}
