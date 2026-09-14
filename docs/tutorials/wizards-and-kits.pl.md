# Samouczek: Kreatory i zestawy

<!-- languages -->
[English](wizards-and-kits.md) · [Español](wizards-and-kits.es.md) · [Français](wizards-and-kits.fr.md) · [Deutsch](wizards-and-kits.de.md) · [Русский](wizards-and-kits.ru.md) · [Українська](wizards-and-kits.uk.md) · **Polski** · [Português (Brasil)](wizards-and-kits.pt.md) · [Bahasa Indonesia](wizards-and-kits.id.md) · [Filipino](wizards-and-kits.tl.md) · [Tiếng Việt](wizards-and-kits.vi.md) · [简体中文](wizards-and-kits.zh.md) · [हिन्दी](wizards-and-kits.hi.md) · [עברית](wizards-and-kits.he.md) · [العربية](wizards-and-kits.ar.md)
<!-- /languages -->

![Kreator Standards Kit — robots.txt, mapa witryny, manifest aplikacji webowej, security.txt zgodny z RFC 9116 i humans.txt utworzone z twoich odpowiedzi](../images/tabs/wizards-and-kits.png)

NMOX Studio ma kilka jednorazowych generatorów, które dodają do
istniejącego projektu rusztowanie klasy produkcyjnej, nie nadpisując
twoich plików. Ten samouczek dodaje PWA do projektu webowego; pozostałe
działają tak samo.

<!-- screenshot: the PWA Kit wizard, then the generated icons/manifest/sw.js in the tree -->

## Zestawy

- **PWA Kit** — rusztowanie aplikacji do zainstalowania: **kuźnia ikon**
  w Java2D (razem z wariantami maskowalnymi), czytelny service worker
  (powłoka aplikacji / sieć najpierw), strona na czas bez sieci
  i idempotentne okablowanie `index.html`.
- **Standards Kit** — webowe minimum przyzwoitości: `robots.txt`,
  `sitemap.xml`, `manifest` aplikacji webowej, `security.txt` zgodny
  z RFC 9116, `humans.txt`.
- **Classic Kit** — rozszerza dowolny kod o jQuery / MooTools / Prototype
  / Backbone / Knockout, w samym repozytorium albo z npm, plus rusztowania
  webpacka, grunta, gulpa i bowera.

## Kroki (PWA Kit)

1. **Wyceluj w projekt webowy** (taki, który ma `index.html`).

2. **Uruchom kreatora.** `Plik ▸ Dodaj do projektu ▸ PWA Kit…`. Wskaż
   katalog główny witryny i ustaw nazwę aplikacji oraz kolor motywu.

3. **Zakończ.** Kreator tworzy komplet ikon, `manifest.webmanifest`,
   `sw.js` i `offline.html` i wpina je do `index.html` — i **nigdy nie
   nadpisuje**: jeśli plik istnieje, zapisuje zamiast niego plik
   `.suggested` obok.

4. **Sprawdź.** Serwuj projekt (IGNITION na stojaku) i wczytaj go —
   aplikację da się teraz zainstalować i działa bez sieci.

## Czego się właśnie nauczyłeś

- Zestawy dają prawdziwy, czytelny wynik, który należy do ciebie — a nie
  czarną skrzynkę.
- Każdy generator jest idempotentny i nigdy nie nadpisuje twojej pracy.
- To samo obywatelstwo przy zapisie obowiązuje gdzie indziej:
  `.editorconfig` jest honorowany przy zapisie w całym edytorze.

## Dalej

- Standards Kit dla `security.txt` + `robots`/`sitemap`.
- Oceń nagłówki wyniku na karcie Standardy w
  [Studiu API](api-studio.pl.md).
