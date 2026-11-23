let token = window.location.hash.slice(1);
if (window.location.hash) {
    history.replaceState(null, "", "/invito");
}
window.addEventListener("hashchange", () => {
    if (window.location.hash) window.location.reload();
});

const status = document.querySelector("#stato");
const form = document.querySelector("#accesso");
const button = form.querySelector("button");
const field = document.querySelector("#campo-codice");
const input = document.querySelector("#codice");
const isCode = (value) => /^[a-zA-Z0-9_-]{43}$/.test(value);

if (isCode(token)) {
    status.textContent = "Conferma l’invito per proseguire al login di PICUS.";
    field.hidden = true;
    input.disabled = true;
} else if (token) {
    status.textContent = "Link d’invito non valido. Puoi inserire un altro codice.";
    token = "";
}
form.hidden = false;

function showReturnCode(access) {
    const expires = document.querySelector("#scadenza");
    const date = new Date(access.scadenza);
    expires.dateTime = access.scadenza;
    expires.textContent = date.toLocaleString("it-IT", { dateStyle: "long", timeStyle: "short" });
    document.querySelector("#codice-rientro").value = access.codice;
    document.querySelector("#link-rientro").value = access.url;
    form.hidden = true;
    document.querySelector("#rientro").hidden = false;
    status.textContent = "Invito attivato.";
    document.querySelector("#vai-al-login").focus();
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submittedCode = token || input.value.trim();
    if (!isCode(submittedCode)) {
        status.textContent = "Il codice inserito non è valido.";
        input.focus();
        return;
    }
    button.disabled = true;
    status.textContent = "Verifica dell’invito in corso…";

    try {
        const response = await fetch("/invito/riscatta", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ token: submittedCode }),
        });
        if (response.status === 200) {
            showReturnCode(await response.json());
            return;
        }
        if (response.status === 204) {
            window.location.replace("/login");
            return;
        }
        if (response.status === 410) {
            token = "";
            field.hidden = false;
            input.disabled = false;
            button.disabled = false;
            status.textContent = "Codice non disponibile. Verificalo o chiedi un nuovo invito.";
            input.focus();
            return;
        }
        if (response.status === 429) {
            const wait = Number(response.headers.get("Retry-After"));
            status.textContent = wait > 0 ? `Troppi tentativi. Riprova tra ${wait} secondi.` : "Troppi tentativi. Riprova tra poco.";
            button.disabled = false;
            return;
        }
    } catch {
        // Anche dopo un errore di rete, verifica lo stato dell'invito al prossimo tentativo.
    }
    status.textContent = "Verifica temporaneamente indisponibile. Riprova.";
    button.disabled = false;
});
