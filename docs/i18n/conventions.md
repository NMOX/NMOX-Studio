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
- Technical tokens (`npm`, `package.json`, `{0}`) stay Latin inside the
  Hebrew sentence. The bidi algorithm places them; a translation never
  reorders them by hand.

## ar — العربية (مصري)

- Register: Egyptian. Everyday words are the ones an Egyptian developer
  says (`مفيش` where a formal text would write `لا يوجد`). Menu commands
  stay action nouns (`حفظ`, `فتح`), the form Arabic software uses in
  every register.
- Punctuation is Arabic: `،` `؛` `؟`.
- Arrows point the way the reader reads, `◂` and `←`, as in Hebrew.
- Quotes: «علامات التنصيص». Guillemets are bidi-mirrored characters, so
  they render correctly in a right-to-left line.
- Mnemonic: appended Latin letter before the ellipsis. The same
  keyboard-layout reason as Hebrew applies, and an underline beneath a
  joined Arabic letter breaks the letter's shape.
- Digits: decided and recorded in the Arabic release. Under `ar`,
  `String.format` and `MessageFormat` produce Arabic-Indic digits, so the
  line between a number a person reads and a number a person retypes (a
  port, a version, a line number) becomes a line between scripts
  (ledger 93).
