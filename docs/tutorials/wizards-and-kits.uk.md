# Урок: Майстри та набори

<!-- languages -->
[English](wizards-and-kits.md) · [Español](wizards-and-kits.es.md) · [Français](wizards-and-kits.fr.md) · [Deutsch](wizards-and-kits.de.md) · [Русский](wizards-and-kits.ru.md) · **Українська** · [Polski](wizards-and-kits.pl.md) · [Português (Brasil)](wizards-and-kits.pt.md) · [Bahasa Indonesia](wizards-and-kits.id.md) · [Filipino](wizards-and-kits.tl.md) · [Tiếng Việt](wizards-and-kits.vi.md) · [简体中文](wizards-and-kits.zh.md) · [हिन्दी](wizards-and-kits.hi.md) · [עברית](wizards-and-kits.he.md) · [العربية](wizards-and-kits.ar.md)
<!-- /languages -->

![Майстер Standards Kit — robots.txt, sitemap, вебманіфест, security.txt за RFC 9116 і humans.txt, створені з ваших відповідей](../images/tabs/wizards-and-kits.png)

NMOX Studio має кілька одноразових генераторів, що додають до наявного
проєкту заготовки промислового рівня, не затираючи ваших файлів. Цей урок
додає PWA до вебпроєкту; решта працюють так само.

<!-- screenshot: the PWA Kit wizard, then the generated icons/manifest/sw.js in the tree -->

## Набори

- **PWA Kit** — заготовка встановлюваного застосунку: **кузня піктограм**
  на Java2D (разом із масковним набором), читабельний сервіс-воркер
  (оболонка застосунку / мережа спершу), сторінка для роботи без мережі та
  ідемпотентна обв’язка `index.html`.
- **Standards Kit** — обов’язковий мінімум вебу: `robots.txt`, `sitemap.xml`,
  `manifest` вебзастосунку, `security.txt` за RFC 9116, `humans.txt`.
- **Classic Kit** — додає до будь-якого коду jQuery / MooTools / Prototype /
  Backbone / Knockout (у самому репозиторії або через npm) плюс заготовки
  webpack/grunt/gulp/bower.

## Кроки (PWA Kit)

1. **Націльтеся на вебпроєкт** (такий, що має `index.html`).

2. **Запустіть майстер.** `Файл ▸ Додати до проєкту ▸ PWA Kit…`. Вкажіть корінь сайту,
   задайте назву застосунку й колір теми.

3. **Завершіть.** Майстер створює набір піктограм, `manifest.webmanifest`,
   `sw.js` і `offline.html` та підключає їх до `index.html` — і
   **ніколи не затирає**: якщо файл уже існує, поряд з’являється
   `.suggested`.

4. **Перевірте.** Запустіть роздачу проєкту (IGNITION у стійці) й
   відкрийте його — застосунок тепер можна встановити, і він працює без мережі.

## Що ви щойно дізналися

- Набори дають справжній читабельний результат, що належить вам, — а не
  чорну скриньку.
- Кожен генератор ідемпотентний і ніколи не переписує вашу роботу.
- Та сама дисципліна при збереженні діє й деінде: `.editorconfig`
  враховується при збереженні в усьому редакторі.

## Далі

- Standards Kit для `security.txt` + `robots`/`sitemap`.
- Оцініть заголовки результату на вкладці «Стандарти» в
  [Студії API](api-studio.uk.md).
