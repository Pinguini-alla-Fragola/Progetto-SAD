export class ErroreHttp extends Error {
    constructor(stato) {
        super('Richiesta non riuscita');
        this.stato = stato;
    }
}

export async function api(percorso, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    if (method === 'POST') {
        const csrf = await api('/api/auth/csrf');
        headers[csrf.headerName] = csrf.token;
    }
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    const risposta = await fetch(percorso, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!risposta.ok) {
        throw new ErroreHttp(risposta.status);
    }
    return risposta.status === 204 ? null : risposta.json();
}
