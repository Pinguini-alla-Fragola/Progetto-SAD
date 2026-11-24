let token = window.location.hash.slice(1);
let verifica = "";
const codiceValido = (value) => /^[a-zA-Z0-9_-]{43}$/.test(value);
if (window.location.hash) history.replaceState(null, "", "/invito");
window.addEventListener("hashchange", () => {
    if (window.location.hash) window.location.reload();
});

const status = document.querySelector("#stato");
const testoInvito = Array.from(status.childNodes, (node) => node.cloneNode(true));
const formInvito = document.querySelector("#accesso");
const codice = document.querySelector("#codice");
const campoCodice = document.querySelector("#campo-codice");
const email = document.querySelector("#email-nuova");
const password = document.querySelector("#password-nuova");
const forms = {
    invito: formInvito,
    registrazione: document.querySelector("#registrazione"),
    conferma: document.querySelector("#conferma"),
    login: document.querySelector("#login-demo"),
};

function apri(pagina, messaggio, focus = true) {
    for (const [id, form] of Object.entries(forms)) form.hidden = id !== pagina;
    campoCodice.hidden = pagina === "invito" && codiceValido(token);
    codice.disabled = campoCodice.hidden;
    if (messaggio) status.textContent = messaggio;
    else status.replaceChildren(...testoInvito.map((node) => node.cloneNode(true)));
    if (focus) forms[pagina].querySelector("input:not(:disabled), button[type=submit]").focus();
}

document.querySelectorAll("[data-apri]").forEach((button) => {
    button.addEventListener("click", () => {
        const pagina = button.dataset.apri;
        if (pagina === "invito") {
            token = "";
            verifica = "";
            codice.value = "";
            password.value = "";
            apri("invito");
        } else if (pagina === "registrazione") {
            apri(pagina, "Conferma email e password per richiedere un nuovo codice.");
        } else {
            apri(pagina, "Accedi con le credenziali che hai scelto per la demo.");
        }
    });
});

async function invia(form, endpoint, body) {
    const controls = Array.from(form.querySelectorAll("button"));
    for (const button of controls) button.disabled = true;
    status.textContent = "Verifica in corso…";
    try {
        const response = await fetch(endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
        if (response.status === 204) return {};
        const result = (response.headers.get("Content-Type") || "").includes("application/json")
            ? await response.json()
            : {};
        if (response.ok) return result;
        const retry = Number(response.headers.get("Retry-After"));
        if (response.status === 410 && form !== forms.login) {
            token = "";
            verifica = "";
            codice.value = "";
            password.value = "";
            apri("invito", result.errore || "L’invito non è disponibile.");
        } else {
            status.textContent = result.errore || (response.status === 429 ? "Troppi tentativi." : "Verifica temporaneamente indisponibile.");
            if (retry > 0) status.textContent += " Riprova tra " + retry + " secondi.";
        }
    } catch {
        status.textContent = "Connessione interrotta. Riprova.";
    } finally {
        for (const button of controls) button.disabled = false;
    }
    return null;
}

formInvito.addEventListener("submit", async (event) => {
    event.preventDefault();
    const valore = codiceValido(token) ? token : codice.value.trim();
    if (!codiceValido(valore)) {
        status.textContent = "Il codice inserito non è valido.";
        codice.focus();
        return;
    }
    if (await invia(formInvito, "/invito/riscatta", { token: valore })) {
        token = valore;
        apri("registrazione", "Invito valido. Inserisci email e password per accedere alla demo.");
    }
});

forms.registrazione.addEventListener("submit", async (event) => {
    event.preventDefault();
    const result = await invia(forms.registrazione, "/invito/registra", { token, email: email.value, password: password.value });
    if (result) {
        verifica = result.verifica;
        password.value = "";
        document.querySelector("#otp").value = "";
        apri("conferma", "Abbiamo inviato un codice di 6 cifre a " + email.value + ". Controlla anche la posta indesiderata.");
    }
});

forms.conferma.addEventListener("submit", async (event) => {
    event.preventDefault();
    const otp = document.querySelector("#otp").value;
    if (await invia(forms.conferma, "/invito/conferma", { verifica, otp })) window.location.replace("/login");
});

forms.login.addEventListener("submit", async (event) => {
    event.preventDefault();
    const email = document.querySelector("#email-login").value;
    const password = document.querySelector("#password-login").value;
    if (await invia(forms.login, "/invito/login", { email, password })) window.location.replace("/login");
});

if (codiceValido(token)) {
    apri("invito", "Conferma l’invito per accedere alla demo.", false);
} else if (token) {
    token = "";
    apri("invito", "Link d’invito non valido. Puoi inserire un altro codice.", false);
} else {
    apri("invito", "", false);
}
