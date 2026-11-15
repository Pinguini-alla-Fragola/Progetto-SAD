(function () {
  var form = document.getElementById('login-form');
  if (!form) return;
  var pw = document.getElementById('password');
  var toggle = document.getElementById('toggle-pw');
  var submit = form.querySelector('button[type=submit]');
  var label = submit.querySelector('span');
  var original = label.textContent;

  toggle.addEventListener('click', function () {
    var show = pw.type === 'password';
    pw.type = show ? 'text' : 'password';
    toggle.setAttribute('aria-pressed', String(show));
    toggle.setAttribute('aria-label', show ? 'Nascondi password' : 'Mostra password');
  });

  // evita doppi invii; ripristina il pulsante se si torna indietro con il browser
  form.addEventListener('submit', function () {
    submit.disabled = true;
    label.textContent = 'Accesso in corso…';
  });
  window.addEventListener('pageshow', function () {
    submit.disabled = false;
    label.textContent = original;
  });
})();
