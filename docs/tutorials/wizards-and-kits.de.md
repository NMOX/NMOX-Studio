# Tutorial: Assistenten und Kits

<!-- languages -->
[English](wizards-and-kits.md) · [Español](wizards-and-kits.es.md) · [Français](wizards-and-kits.fr.md) · **Deutsch** · [Русский](wizards-and-kits.ru.md) · [Українська](wizards-and-kits.uk.md) · [Polski](wizards-and-kits.pl.md) · [Português (Brasil)](wizards-and-kits.pt.md) · [Bahasa Indonesia](wizards-and-kits.id.md) · [Filipino](wizards-and-kits.tl.md) · [Tiếng Việt](wizards-and-kits.vi.md) · [简体中文](wizards-and-kits.zh.md) · [हिन्दी](wizards-and-kits.hi.md) · [עברית](wizards-and-kits.he.md) · [العربية](wizards-and-kits.ar.md)
<!-- /languages -->

![Der Assistent des Standards-Kits — robots.txt, Sitemap, Web-Manifest, security.txt nach RFC 9116 und humans.txt, erzeugt aus Ihren Antworten](../images/tabs/wizards-and-kits.png)

NMOX Studio bringt mehrere Generatoren mit, die einem bestehenden Projekt
produktionstaugliche Gerüste hinzufügen, ohne Ihre Dateien zu
überschreiben. Dieses Tutorial ergänzt ein Webprojekt um eine PWA; die
anderen funktionieren genauso.

<!-- screenshot: the PWA Kit wizard, then the generated icons/manifest/sw.js in the tree -->

## Die Kits

- **PWA-Kit** — Gerüst für installierbare Apps: eine Java2D-**Symbolschmiede**
  (einschließlich des maskierbaren Satzes), ein lesbarer Service Worker
  (App-Shell / Netz zuerst), eine Offline-Seite und idempotente
  Verdrahtung in `index.html`.
- **Standards-Kit** — die Grundausstattung des Webs: `robots.txt`,
  `sitemap.xml`, das Web-App-`manifest`, `security.txt` nach RFC 9116 und
  `humans.txt`.
- **Klassik-Kit** — ergänzt beliebigen Code um jQuery / MooTools /
  Prototype / Backbone / Knockout, mitgeliefert oder über npm, dazu
  Gerüste für webpack/grunt/gulp/bower.

## Schritte (PWA-Kit)

1. **Richten Sie ein Webprojekt aus** (eines mit einer `index.html`).

2. **Starten Sie den Assistenten.** `Datei ▸ Zum Projekt hinzufügen ▸ PWA Kit…`.
   Zeigen Sie auf Ihr Web-Wurzelverzeichnis und legen Sie App-Namen und
   Designfarbe fest.

3. **Abschließen.** Der Assistent erzeugt den Symbolsatz,
   `manifest.webmanifest`, `sw.js` und `offline.html` und verdrahtet sie in
   `index.html` — und er **überschreibt nie**: Existiert eine Datei, schreibt
   er stattdessen eine `.suggested`-Datei daneben.

4. **Prüfen.** Liefern Sie das Projekt aus (IGNITION im Rack) und laden Sie
   es — die App ist jetzt installierbar und funktioniert offline.

## Was Sie gerade gelernt haben

- Die Kits erzeugen echte, lesbare Ergebnisse, die Ihnen gehören — keine
  Blackbox.
- Jeder Generator ist idempotent und überschreibt nie Ihre Arbeit.
- Dieselbe Sorgfalt beim Speichern gilt auch anderswo: `.editorconfig` wird
  im ganzen Editor beim Speichern beachtet.

## Weiter

- Das Standards-Kit für `security.txt` sowie `robots`/`sitemap`.
- Bewerten Sie die Header des Ergebnisses im Tab „Standards“ des
  [API-Studios](api-studio.de.md).
