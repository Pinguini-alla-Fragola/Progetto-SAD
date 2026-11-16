export class ErroreHttp extends Error {
    constructor(stato, dati) {
        super(dati?.message || dati?.errore || 'Operazione non riuscita');
        this.stato = stato;
    }
}

export async function api(url, { method = 'GET', body, contentType = 'application/json' } = {}) {
    const headers = { Accept: 'application/json' };
    if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
        const csrf = await api('/api/auth/csrf');
        headers[csrf.headerName] = csrf.token;
    }
    if (body !== undefined) {
        headers['Content-Type'] = contentType;
    }
    const res = await fetch(url, {
        method, headers,
        body: body !== undefined && contentType === 'application/json' ? JSON.stringify(body) : body
    });
    const dati = res.status === 204 ? null : await res.json().catch(() => null);
    if (!res.ok) {
        throw new ErroreHttp(res.status, dati);
    }
    return dati;
}

export function pagina(url) {
    return url;
}

export async function esci() {
    try {
        await api('/api/auth/logout', { method: 'POST' });
    } catch (err) {
        if (err.stato !== 401) throw err;
    }
    location.assign('/login?logout');
}
