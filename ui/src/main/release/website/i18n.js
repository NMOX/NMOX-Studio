/* i18n.js — internationalization starter (NMOX I18n Kit)
   Dependency-free and readable: a catalog per locale in
   ./locales/<tag>.json, applied to every element carrying
   data-i18n="key". Own this file: the kit never rewrites it.

   Markup:   <h1 data-i18n="app.title"></h1>
   Switch:   setLocale('es')
   Add one:  copy locales/en.json, translate the values. */

const I18N = {
  locale: 'en',
  messages: {},
};

async function setLocale(tag) {
  const res = await fetch(`locales/${tag}.json`);
  if (!res.ok) {
    console.warn(`i18n: no catalog for "${tag}", staying on "${I18N.locale}"`);
    return;
  }
  I18N.messages = await res.json();
  I18N.locale = tag;
  // keep the document's language claim TRUE — screen readers
  // pick pronunciation from it, and CSS/:lang() keys off it
  document.documentElement.lang = tag;
  applyTranslations();
}

function t(key) {
  // a missing key returns the key itself — visible in the UI,
  // never a silent blank
  return I18N.messages[key] ?? key;
}

function applyTranslations() {
  for (const el of document.querySelectorAll('[data-i18n]')) {
    el.textContent = t(el.getAttribute('data-i18n'));
  }
}

// first load: the visitor's language if a catalog exists,
// English otherwise
setLocale((navigator.language || 'en').split('-')[0]);
