export function filterItems(items, query = '', filters = {}) {
    const terms = normalize(query).split(/\s+/).filter(Boolean);
    return items.filter(item => terms.every(term => normalize(item.text).includes(term)) &&
        Object.entries(filters).every(([key, expected]) => !expected ||
            (Array.isArray(item.filters?.[key]) ? item.filters[key] : [item.filters?.[key]])
                .some(value => String(value ?? '') === String(expected))));
}

export function paginate(items, page = 1, pageSize = 10) {
    const size = Math.max(1, Math.floor(Number(pageSize)) || 10);
    const pages = Math.max(1, Math.ceil(items.length / size));
    const current = Math.min(pages, Math.max(1, Math.floor(Number(page)) || 1));
    const offset = (current - 1) * size;
    return { items: items.slice(offset, offset + size), page: current, pages, total: items.length,
        start: items.length ? offset + 1 : 0, end: Math.min(offset + size, items.length) };
}

function normalize(value) {
    return String(value ?? '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase('it-IT').trim();
}
