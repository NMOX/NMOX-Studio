# Урок: Навчальні простори

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · **Українська** · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![Вибір «Новий навчальний простір» — пошук серед вбудованих уроків, а перевірка доступності наперед каже, чи є на цій машині потрібний інструмент](../images/tabs/learning-spaces.png)

Навчальний простір — самодостатня пісочниця для вивчення мови, фреймворку
чи бібліотеки: NMOX Studio створює зразковий код, покроковий урок і стійку
з уже підключеним **справжнім REPL у стійці**, у який ви друкуєте.
Вбудованих просторів — 93.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## Як відкрити

`Файл ▸ Новий навчальний простір…` (запускач перелічує всі вбудовані простори).

## Кроки

1. **Оберіть простір.** Python, Rust, Solid, htmx, Solidity, Elm, REPL
   для системної мови, простір E2E/Playwright тощо. Вибір спершу
   перевіряє, чи доступні інтерпретатор або інструментарій.

2. **Дайте йому створитися.** NMOX Studio створює простір у
   `~/.nmox/learn/<slug>`: мінімальний робочий зразок і урок, що веде вас
   через нього й указує на потрібну консоль чи прилад.

3. **Друкуйте в REPL.** Підготовлена стійка містить прилад **REPL**, чия
   ручка ENGINE налаштована на мову простору (по одному рушію на кожну мову REPL
   у каталозі, у кожного вже задано прапорці примусової інтерактивності). Наберіть вираз, натисніть
   Enter — вивід потече на екран REPL. Немає інтерпретатора?
   Кнопка **INSTALL** встановить його просто зі стійки.

4. **Пройдіть урок.** Виконуйте кроки: зразковий код справжній і
   запускається, а простір — ваш, змінюйте його як завгодно.

## Що ви щойно дізналися

- Навчальний простір — це повноцінний проєкт + урок + підключена стійка, а
  не просто фрагмент коду.
- REPL — справжній інтерактивний процес, а не записане відтворення.
- Можна додати власні: покладіть `*.json` у `~/.nmox/learn-catalog.d/`
  — і він з’явиться у виборі (схему див. у [learning-spaces.md](../learning-spaces.md)).

## Далі

- Простори фреймворків (Astro/SvelteKit/Nuxt/Next) указують на свою консоль
  у стійці (COSMOS/KINETIC/NIMBUS/NEXUS).
