# Урок: Мастера и наборы

<!-- languages -->
[English](wizards-and-kits.md) · [Español](wizards-and-kits.es.md) · [Français](wizards-and-kits.fr.md) · [Deutsch](wizards-and-kits.de.md) · **Русский** · [Українська](wizards-and-kits.uk.md) · [Polski](wizards-and-kits.pl.md) · [Português (Brasil)](wizards-and-kits.pt.md) · [Bahasa Indonesia](wizards-and-kits.id.md) · [Filipino](wizards-and-kits.tl.md) · [Tiếng Việt](wizards-and-kits.vi.md) · [简体中文](wizards-and-kits.zh.md) · [हिन्दी](wizards-and-kits.hi.md) · [עברית](wizards-and-kits.he.md) · [العربية](wizards-and-kits.ar.md)
<!-- /languages -->

![Мастер Standards Kit — robots.txt, sitemap, веб-манифест, security.txt по RFC 9116 и humans.txt, созданные из ваших ответов](../images/tabs/wizards-and-kits.png)

В NMOX Studio есть несколько разовых генераторов, которые добавляют в
существующий проект заготовки промышленного качества и не затирают ваши
файлы. В этом уроке мы добавим PWA к веб-проекту; остальные наборы
работают так же.

<!-- screenshot: the PWA Kit wizard, then the generated icons/manifest/sw.js in the tree -->

## Наборы

- **PWA Kit** — заготовка устанавливаемого приложения: **кузница значков**
  на Java2D (включая маскируемый набор), читаемый сервис-воркер
  (оболочка приложения или сеть сначала), страница для работы без сети и
  идемпотентная обвязка `index.html`.
- **Standards Kit** — обязательный минимум веба: `robots.txt`,
  `sitemap.xml`, `manifest` веб-приложения, `security.txt` по RFC 9116,
  `humans.txt`.
- **Classic Kit** — расширяет любой код библиотеками jQuery / MooTools /
  Prototype / Backbone / Knockout, положенными в репозиторий или
  подключёнными через npm, плюс заготовки webpack/grunt/gulp/bower.

## Шаги (PWA Kit)

1. **Наведитесь на веб-проект** (такой, где есть `index.html`).

2. **Запустите мастер.** `Файл ▸ Добавить в проект ▸ PWA Kit…`. Укажите
   корень сайта, задайте имя приложения и цвет темы.

3. **Завершите.** Мастер создаёт набор значков, `manifest.webmanifest`,
   `sw.js` и `offline.html` и подключает их в `index.html` — и он
   **никогда не затирает**: если файл уже есть, рядом появляется
   `.suggested`.

4. **Проверьте.** Раздайте проект (IGNITION в стойке) и откройте его —
   теперь приложение можно установить, и оно работает без сети.

## Что вы узнали

- Наборы выдают настоящий читаемый результат, который принадлежит вам, а
  не чёрный ящик.
- Каждый генератор идемпотентен и никогда не перезаписывает вашу работу.
- Та же дисциплина при сохранении действует и в других местах:
  `.editorconfig` соблюдается при сохранении во всём редакторе.

## Дальше

- Standards Kit — для `security.txt` и `robots`/`sitemap`.
- Оцените заголовки получившегося сайта на вкладке «Стандарты» в
  [Студии API](api-studio.ru.md).
