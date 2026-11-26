/**
 * Costruisce la mappa degli account per ruolo (id -> { id, nome, riferimento, stato, omonimo })
 * usando l'elenco aggiornato restituito da /api/utenze/rubrica quando disponibile e non vuoto,
 * oppure ripiegando sulle righe già renderizzate in #account nella dashboard Banco.
 */
export function leggiRubricaAccount(ruolo, vociServer = null) {
    const sorgente = Array.isArray(vociServer) && vociServer.length > 0
        ? vociServer
        : Array.from(
            document.querySelectorAll?.(`#account .account-row[data-ruolo="${ruolo}"]`) || [],
            riga => riga.dataset
        );
    const mappa = new Map();
    for (const voce of sorgente) {
        if (!voce?.id) continue;
        const id = String(voce.id);
        mappa.set(id, {
            id,
            nome: String(voce.nome || '').trim(),
            riferimento: String(voce.riferimento || '').trim(),
            stato: String(voce.stato || '').trim()
        });
    }
    const conteggioNomi = new Map();
    for (const account of mappa.values()) {
        const chiaveNome = account.nome.toLocaleLowerCase('it-IT');
        if (chiaveNome) {
            conteggioNomi.set(chiaveNome, (conteggioNomi.get(chiaveNome) || 0) + 1);
        }
    }
    for (const account of mappa.values()) {
        const chiaveNome = account.nome.toLocaleLowerCase('it-IT');
        account.omonimo = chiaveNome ? (conteggioNomi.get(chiaveNome) || 0) > 1 : false;
    }
    return mappa;
}
