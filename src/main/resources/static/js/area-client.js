export class ErroreHttp extends Error {
    constructor(stato, dati) {
        const messaggi = {
            401: 'La sessione è terminata. Accedi di nuovo per continuare.',
            403: 'Non hai il permesso di eseguire questa operazione. Ricarica la pagina se la sessione è cambiata.',
            404: 'La risorsa richiesta non è disponibile.',
            409: 'I dati sono cambiati. Aggiorna la vista prima di riprovare.',
            429: 'Troppi tentativi. Attendi un minuto prima di riprovare.'
        };
        super(messaggi[stato] || dati?.messaggio || 'Operazione non riuscita. Riprova tra poco.');
        this.stato = stato;
        this.campi = dati?.campi || {};
    }
}

export async function api(url, { method = 'GET', body, contentType = 'application/json', headers: aggiuntive = {}, csrf } = {}) {
    const headers = { ...aggiuntive, Accept: 'application/json' };
    if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
        const { headerName, token } = csrf ?? await api('/api/auth/csrf');
        headers[headerName] = token;
    }
    if (body !== undefined) {
        headers['Content-Type'] = contentType;
    }
    const res = await fetch(url, {
        method, headers,
        body: body !== undefined && contentType === 'application/json' ? JSON.stringify(body) : body
    });
    if (res.status === 204) return null;
    const dati = await res.json().catch(() => null);
    if (!res.ok) {
        throw new ErroreHttp(res.status, dati);
    }
    if (dati === null || res.redirected) throw new ErroreHttp(res.redirected ? 401 : 502);
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
