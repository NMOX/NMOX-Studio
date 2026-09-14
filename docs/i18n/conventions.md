# How each language is written

A translation can be correct word by word and still read like a
translation. The tell is almost never vocabulary. It is the small
conventions a native reader never thinks about: which quotation marks,
where the space goes before a colon, whether the software says *du* or
*Sie*, whether a Chinese sentence ends in `?` or `？`. Get one wrong and
the reader feels a machine behind the words, even when every word is
right.

This page is the style sheet each shipped language follows. The
mechanical half is held by `NativeTypographyGateTest`, which reads the
assembled cluster, so a value that breaks its language's convention fails
the build by key. Register (formal or informal address) is gated by a small
word list per language. Idiom is not gated, and cannot be: that half is a
reader's job, done again for every language that ships.

Two rules hold for every language:

- **The ellipsis is one character, `…` (U+2026).** The platform's own
  English writes three periods, and a translated menu that mixes `...`
  from the platform with `…` from the product reads as two sources.
- **Quotation marks are the language's own.** A straight `"` pair around
  a name is a typewriter habit. It stays straight only inside markup,
  code, or examples the user types (`"?" "*"` in a search pattern).

## es — Español

- Address: **tú**, throughout (`Pulsa`, `Elige`, `tu proyecto`). Until
  v2.150.0 one module spoke *usted* and the rest *tú*, which reads as two
  translators. Modern Spanish software uses *tú*.
- Quotes: «comillas latinas», no inner spaces.
- Questions and exclamations open with ¿ and ¡.

## fr — Français

- Address: **vous**.
- Quotes: « guillemets » with a no-break space (U+00A0) inside.
- A no-break space (U+00A0) goes before `:` `;` `!` `?` and `»` and after
  `«`. An ordinary space lets a line wrap strand the colon at the start of
  the next line. French typography uses a narrow no-break space (U+202F)
  before `;` `!` `?`. We use U+00A0 everywhere because not every Swing
  font carries U+202F, and a box in place of a space is worse than a space
  slightly too wide.

## de — Deutsch

- Address: **Sie**, throughout. Two Standards Kit sentences said *du*
  until v2.150.0.
- Quotes: „deutsche Anführungszeichen“ (U+201E … U+201C).

## ru — Русский · uk — Українська

- Address: **вы / ви**.
- Quotes: «ёлочки», no inner spaces.
- Ukrainian apostrophes are `’` (U+2019), which MessageFormat leaves alone
  (v2.98.0).

## pl — Polski

- Address: the impersonal and imperative forms Polish software uses
  (`Zapisz`, `Nie udało się…`), never *Pan/Pani*.
- Quotes: „cudzysłów” (U+201E … U+201D).

## pt — Português (Brasil)

- Address: **você**.
- Quotes: “aspas curvas”.

## id — Bahasa Indonesia · tl — Filipino · vi — Tiếng Việt

- Quotes: “curly double quotes”.
- Vietnamese addresses the reader as *bạn*.

## zh — 简体中文

- Punctuation next to Chinese is full-width: `，` `：` `；` `？` `！`, with no
  space after it. `错误：{0}`, not `错误: {0}`.
- Parentheses whose content includes Chinese are full-width `（）`, with no
  surrounding spaces. Parentheses holding only Latin text, such as
  `(frame-ancestors)`, stay half-width, the Microsoft Chinese style guide
  rule.
- Quotes: “中文引号”.
- A mnemonic is the Latin letter in parentheses, and it comes **before**
  the ellipsis: `浏览(&B)…`, the order Chinese Windows uses. It is never
  `浏览…(&B)`.
- Options keyword lists (`KW_*` keys) keep ASCII commas, because the
  platform splits them on `,`.

## hi — हिन्दी

- Address: **आप**.
- Sentences end with the purna viram `।`.
- Quotes: “curly double quotes”.
- The mnemonic is appended Latin, before the ellipsis:
  `खोजें(&F)…`.

## he — עברית

- **Gender-neutral by construction.** A Hebrew imperative has a gender,
  so software does not use the singular imperative:
  - Commands and menu items are **action nouns**: `שמירה`, `פתיחה`,
    `הוספת ספרייה`. This is how Office and Windows name commands in
    Hebrew.
  - Instructions addressed to the reader use the **plural imperative**:
    `פתחו תיקייה`, `כוונו את האולפן`. This is the common inclusive form
    in Israeli software and on the web.
- Abbreviations and acronyms use geresh `׳` and gershayim `״`, never ASCII
  `'` or `"`. Both are MessageFormat-inert, so the v2.97.0 eaten-quote
  class cannot occur.
- Quotation marks stay straight `"`. Curly quotes are not mirrored by the
  bidi algorithm, so `“…”` renders backwards inside a right-to-left line.
  The straight mark is symmetric, and it is what Hebrew software ships.
  The gate records this as the language's convention, not as an exception.
- Mnemonic: appended Latin letter before the ellipsis, `שמירה בשם(&A)…`.
  Israeli developers switch keyboard layouts constantly and most code in
  the English layout. A Latin accelerator is reachable in both layouts; a
  Hebrew-letter accelerator only in one.
- **Arrows point the way the reader reads.** `▸` and `→` are not mirrored
  by the bidi algorithm (neither is Bidi_Mirrored), so in a right-to-left
  sentence they point backwards. A Hebrew menu path is `כלים ◂ אבחון
  הסביבה`, and a sequence is `install ← build ← test`.
- **An arrow between two Latin words is wrapped in RLM** (U+200F). Neutral
  characters between two left-to-right runs take the left-to-right
  direction, so `חלון ◂ IDE Tools ◂ Terminal` would lay `IDE Tools ◂
  Terminal` out as one left-to-right run and reverse the path. An RLM on each
  side keeps every arrow in the Hebrew flow and every name its own run.
- Technical tokens (`npm`, `package.json`, `{0}`) stay Latin inside the
  Hebrew sentence. The bidi algorithm places them; a translation never
  reorders them by hand.

## ar — العربية (مصري)

- Register: Egyptian. Everyday words are the ones an Egyptian developer
  says (`مفيش` where a formal text would write `لا يوجد`). Menu commands
  stay action nouns (`حفظ`, `فتح`), the form Arabic software uses in
  every register.
- **Gender-neutral instructions, as in Hebrew.** An Arabic imperative has a
  gender, so a sentence addressed to the reader uses the plural imperative:
  `افتحوا المجلد`, `اختاروا ملف`. The gate holds the common singular forms
  (`افتح`, `اختار`, `اضغط`…) as a register rule.
- Punctuation is Arabic: `،` `؛` `؟`.
- Arrows point the way the reader reads, `◂` and `←`, as in Hebrew.
- Quotes: «علامات التنصيص». Guillemets are bidi-mirrored characters, so
  they render correctly in a right-to-left line.
- Mnemonic: appended Latin letter before the ellipsis. The same
  keyboard-layout reason as Hebrew applies, and an underline beneath a
  joined Arabic letter breaks the letter's shape.
- **Digits are Western (`0-9`), decided in v2.152.0.** Under `ar` and
  `ar-EG` the JDK formats every number in Arabic-Indic digits:
  `String.format("%d", 8080)` is `٨٠٨٠`. In an IDE that puts a port reading
  `٨٠٨٠` beside a URL reading `localhost:8080`, and turns a line number or a
  version into something a reader cannot type back (ledger 93). The product
  runs Arabic as `ar-u-nu-latn`: the words, the bundles (`Bundle_ar`) and the
  right-to-left layout stay Arabic, and only the digits change.
  `UiLocale.readableDigits` applies the rule at startup and on a live switch,
  and it reads the locale's own zero digit rather than a language list.
- **A label with a keyboard chord and no Arabic letter is wrapped in RLE…PDF**
  (U+202B…U+202C). Swing runs bidi only over text containing a right-to-left
  letter or an embedding mark; RLM and RLI are neither (measured on JDK 25
  while shipping Hebrew), so `IRC  ⌥⌘3` would otherwise be drawn in logical
  order with the chord on the wrong side. The Hebrew section's RLM and LRM
  rules apply to Arabic unchanged.
