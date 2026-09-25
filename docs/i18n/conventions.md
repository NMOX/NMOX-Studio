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

Three rules hold for every language:

- **The ellipsis is one character, `…` (U+2026).** The platform's own
  English writes three periods, and a translated menu that mixes `...`
  from the platform with `…` from the product reads as two sources.
- **Quotation marks are the language's own.** A straight `"` pair around
  a name is a typewriter habit. It stays straight only inside markup,
  code, or examples the user types (`"?" "*"` in a search pattern).
- **A mnemonic is a letter the platform can map: `A`–`Z` or `0`–`9`.**
  Measured in 3.2.0 on the shipped `org.openide.awt.Mnemonics`: `&Editor`
  gives the key E, but `&Éditeur`, `Pozosta&łe`, `&Đóng` and `&Файл` give
  NO mnemonic at all. Any other character is looked up in a branded
  `Mnemonics.properties` table the product does not ship; the lookup fails,
  logs an INFO line every time the menu is built, and assigns nothing.
- **Where the label is written in letters, the mnemonic is one of them,
  underlined in place**: `Edito&r`, `Dokum&ente…`, `Tài &liệu…`. The Latin
  letter appended in parentheses, `Editor(&J)`, is the convention of
  scripts with no Latin letter to underline (Chinese, Hindi, Hebrew,
  Arabic, below). In a language written in Latin letters it is the lazy
  answer to a collision, and on macOS, where Swing shows no mnemonics, the
  reader sees a stray `(J)` after the word. A label keeps an appended
  letter only when every mappable letter it contains is already claimed by
  another row of the same menu — which is why Russian and Ukrainian keep
  theirs: a Cyrillic letter cannot be mapped (the first rule), so their
  appended Latin letter is the only mnemonic that works. `MenuRowsSpeakTest`
  holds both rules over every platform menu row.

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
- **Mnemonics: open.** The overlays embed a Cyrillic letter (`&Файл`), and
  measured in 3.2.0 no Cyrillic letter maps to a key (the mapping rule
  above), so about 260 values per language carry a mnemonic that does
  nothing (138 Russian and 136 Ukrainian menu rows among them). The fix is
  either a shipped Cyrillic-to-keycode table for `org.openide.awt.Mnemonics`
  (a key by keyboard position, as the platform's own l10n once did) or
  Latin letters; until one is chosen, `MenuRowsSpeakTest` records these two
  languages as the one exception to the mapping law, and the appended Latin
  letters they carry stay, because they are the only mnemonics that work.

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
- **A path or name that begins or ends with a neutral character takes an
  LRM (U+200E) on that side** (decided in 3.2.0, ledger 121). `.` `~` `/`
  and a glob's `*` have no direction of their own, so between a Hebrew
  word and a Latin letter they take the sentence's direction and move to
  the far side of the name. Measured with `java.text.Bidi` in a
  right-to-left paragraph: `שלום ~/NMOX/app` is drawn `NMOX/app/~`,
  `./app/src` as `app/src/.`, `../shared/lib` as `shared/lib/..`,
  `/usr/local/bin` as `usr/local/bin/`, `.env` as `env.` and `*.json` as
  `json.*`; with an LRM before the path each is drawn whole. The end
  detaches too: `~/.nmox/devices.d/` before a Hebrew word (or at the end
  of a right-to-left line) is drawn with its last `/` on the far side, and
  `nmox .` as `. nmox`; an LRM after the path keeps it. So: an LRM before
  a path beginning with `.` `~` `/` (or a glob's `*`) when the nearest
  strong character before it is right to left (a letter or an RLM), and
  an LRM after one ending with `/` (or, in a code span, `.`) when the
  nearest strong character after it is right to left or the right-to-left
  line ends there. The mark goes **outside** the code span, before its
  opening backtick or after its closing one, so a reader who copies the
  path copies no invisible character. `RtlDocsPathDirectionGateTest` derives the paths from every
  Hebrew and Arabic document and pins the measurement itself.

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
- **A dotfile name after an Arabic word takes an LRM before its dot**:
  `في ‎.env`, `(‎.nmoxdb.json)`. Without it the dot takes the sentence's
  direction and is drawn after the name. The gate holds this for Hebrew too.
  In the documents the rule is the Hebrew section's wider one, measured in
  3.2.0: any path beginning with `.` `~` `/` (or a glob's `*`) takes the
  LRM before it, and one ending with `/` takes one after it
  (`RtlDocsPathDirectionGateTest`).
- **Machine text is kept in one direction by the code, not the translation.**
  An Arabic clock ends in a letter (`2:14 م`), so a history row that begins
  with the time would run right to left and move a SQL statement's semicolon
  to the front; such rows start with an LRM. A URL at the end of a line is
  wrapped in LRE…PDF so its trailing `/` stays with it.
