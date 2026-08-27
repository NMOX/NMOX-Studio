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
/* The hero note tells the truth about WHO is serving this page: the
   local text ("served to you by the app itself") is only true when
   the app is serving it — on the public deploy (GitHub Pages) the
   web variant shows instead. */
const LOCAL_HOSTS = ['127.0.0.1', 'localhost', '[::1]', '::1'];
if (!LOCAL_HOSTS.includes(location.hostname)) {
  const local = document.getElementById('note-local');
  const web = document.getElementById('note-web');
  if (local && web) {
    local.hidden = true;
    web.hidden = false;
  }
}
