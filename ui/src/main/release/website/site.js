/* The site's one behavior: copy the install line, honestly. */
const btn = document.getElementById('copy-install');
if (btn) {
  btn.addEventListener('click', async () => {
    const line = document.getElementById('install-line').textContent;
    try {
      await navigator.clipboard.writeText(line);
      btn.textContent = 'copied';
    } catch (e) {
      btn.textContent = 'select it';
    }
    setTimeout(() => { btn.textContent = 'copy'; }, 2000);
  });
}
/* Language buttons ride the I18n Kit's setLocale. */
for (const b of document.querySelectorAll('[data-setlocale]')) {
  b.addEventListener('click', () => setLocale(b.getAttribute('data-setlocale')));
}
