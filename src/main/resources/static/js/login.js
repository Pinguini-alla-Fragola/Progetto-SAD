const form = document.getElementById('formLogin');
const email = document.getElementById('email');
const password = document.getElementById('password');
const button = document.getElementById('btnLogin');

for (const profilo of document.querySelectorAll('.demo-pill-btn[data-email]')) {
    profilo.addEventListener('click', () => {
        email.value = profilo.dataset.email;
        password.focus();
    });
}

form.addEventListener('submit', () => {
    button.disabled = true;
    button.textContent = 'Accesso in corso…';
});

window.addEventListener('pageshow', () => {
    button.disabled = false;
    button.textContent = 'Accedi';
});
