# Internationalization and localization: what is done, and what the ceilings are

*Written at v2.147.0, the close of the arc that began at v2.97.0. This is a
record, not a plan. Every claim below was measured, and the measurement is
named beside it so a reader can disagree with evidence rather than opinion.*

NMOX Studio ships in **fifteen languages**: English, Español, Français,
Deutsch, Русский, Українська, Polski, Português (Brasil), Bahasa Indonesia,
Filipino, Tiếng Việt, 简体中文, हिन्दी, עברית, العربية. Hebrew joined at v2.151.0,
the first right-to-left language, and Arabic at v2.152.0, the second, with its
digits kept Latin; every gate below derives its population from
`UiLocale.SUPPORTED`, so it was held to the same laws the day it arrived.

## What is covered

| Surface | How it is held honest |
|---|---|
| 2,830 product chrome keys × 12 translations, 71 packages | `LocaleBundleParityTest` over the assembled cluster |
| The platform menu bar — rows the layer declares | `MenuRowsSpeakTest`, population derived from the layer |
| The platform menu bar — rows named in action code | `CodeNamedMenuRowsTest`, a checked hand-kept ledger |
| The editor's right-click menus, every mime | `PopupRowsSpeakTest`, population derived from the layer |
| The platform toolbar | `ToolbarOverlayGateTest`, every MessageFormat branch rendered |
| Platform dialogs (Options, Plugin Manager, About, …) | `PlatformDialogLedgerTest`, a checked hand-kept ledger |
| Dialog buttons (OK / Cancel / wizard chrome) | `DialogChromeOverlayGateTest` |
| Module names in the Plugin Manager and About ▸ Details | `ModuleDescriptorsSpeakTest` |
| Catalogues: devices, learning spaces, templates, palettes, chains | `CatalogueProseLedgerTest`, keyed per ENUM |
| Seed data a new project is born with | `SeedNamesAreNotLiteralsTest` |
| Directions that name a door | `WayfindingVocabularyTest` |
| A row translated by halves | `HalfTranslatedRowGateTest` |
| Quotation marks, spacing, register, punctuation, mnemonic form — each language's own | `NativeTypographyGateTest`, rules in [`docs/i18n/conventions.md`](../i18n/conventions.md) |
| An English word in a non-Latin script | `OwnScriptGateTest`, threshold measured |
| Text that outgrows its pane | `NarrowPaneHintBudgetTest`, over the shipped jars |
| Time, sort order, number formatting | `core.util.Clocks` / `Collate` / `Numbers` |
| Typing what the product now names | accent-folded search at the one tokenizing point |
| The user guide, the website, the installers, the desktop entry | `TranslatedGuideGateTest`, `SiteShipsTest`, and their siblings |
| Right-to-left layout of every window and dialog (Hebrew, v2.151.0) | `RightToLeftWiringTest` — one toolkit seam, nothing else orients |
| Arabic numbers in Western digits (`ar-u-nu-latn`, v2.152.0) | `UiLocaleTest.arabicFormatsLatinDigits`, `readableDigitsLeavesLatinLanguagesAlone` |
| Each module named as itself, never with another module's words | `ModuleDescriptorsSpeakTest.everyModuleHasItsOwnName`, every language |
| Surfaces that paint themselves: geometry kept, text mirrored by hand | `PaintedSurfaceLedgerTest`, population derived from the source; a mirrored surface names no absolute side |
| The website's and the I18n Kit's page direction | `I18nKitTest`, `SiteShipsTest` byte parity, logical CSS sides |
| The forge's tab shots in every translated guide and tutorial (v2.161.0) | `TranslatedShotsGateTest` — a translated document names `docs/images/<lang>/tabs/`, painted by `scripts/docs-shots.sh <dir> <lang>` with the app booted `--locale <lang>` (mirrored for he/ar); `ImageRefsTest` keeps dead refs and orphans out |
| The user guide's six staged pictures in every translation (v2.162.0) — the rack front and rear, the editor, an experiment's walkthrough, KVASIR's diagnosis, the learning-space shelf | `TranslatedShotsGateTest` — a translated document names its language's copy of every staged shot that exists, and every language with forge tabs has all six; painted by `NMOX_SHOTS_STAGED=1 scripts/docs-shots.sh <dir> <lang>` from fixtures the product generates (`DocsStagingTest`). Ceiling as v2.162.0 recorded it named a database grid, sprint history and Presentation Mode as scenes a throwaway boot could not honestly produce — v2.163.0 produced all three for real (next row), so that part of the ceiling was wrong; and a mirrored rack still clips roughly eight pixels of its leftmost column (the rail gutter's width — the rack's mirrored layout, measured in v2.162.0, after `Scrolls.toContentStart` recovered the device faces) |
| The 21 tutorials in `docs/tutorials`, every translation (v2.153.0) | `TranslatedTutorialsGateTest` — tutorials and languages derived; commands, headings, screenshots and the language bar identical to English |
| Menu paths in every document, English and translated | `DocsMenuDoorsTest` — the doors of each language's own menu bar; one-OS rows must say where they are on macOS |
| The Window menu unambiguous in every language | `WindowMenuIsUnambiguousTest`, per language through the platform's bundle chain |
| The Standup report | `StandupReportTest.speaksTheReadersLanguage` |
| The tutorials' data-bearing windows in every translation (v2.163.0) — the Task Board, its sprint Overview and Standup, the Infra Designer, DB Studio, API Studio, Presentation Mode, the editor tab | `ForgeFixturesGateTest` — every guide language carries complete scene content of its own, with the SQL and order counts identical by design; `TranslatedShotsGateTest` as above. Each scene is staged by the module that owns it (`core.spi.DocsScene`): a real SQLite database built with DB Studio's bundled driver, a real loopback `/health` for API Studio, the board written by the product's own writer. `DocsFixturesTest` holds that no org.json type crosses a module boundary — each module loads its own. Ceiling as v2.163.0 recorded it named four live scenes; v2.164.0 staged all four (next row) |
| The tutorials' live scenes in every translation (v2.164.0) — the Docker Panel on a real container, Contract Studio compiled and connected to anvil, the DevTools pick on a served page, a Node run paused on its breakpoint | `TranslatedShotsGateTest` (widened to eighteen staged shots per language); `DocsDockerViewGateTest` — the forge's Docker view shows only containers labelled `org.nmox.docs=1` and forwards no write, run against a fake daemon; `DocsBrowserTest` holds the served directory to the one the scene writes; `ForgeFixturesGateTest` over the new `inventory` and `page` sections. Ceiling as v2.164.0 recorded it: the Browser painted Arabic unjoined (ledger 99) — closed by v2.165.0, next row |
| Complex scripts in the in-app Browser (v2.165.0) — Arabic, Persian and the Indic scripts paint shaped, where OpenJFX's WebKit paints each character's plain glyph on every release | `ComplexScriptsTest` (reordering, alignment, refusals), `ComplexTextShapingTest` (the rewritten draw run against a stand-in), `ShapingInstallReferenceTest`, `PackagedConfGateTest` (the attach flags); proven in the assembled app by the forge's Arabic and Hindi DevTools pictures. Ceiling as v2.165.0 recorded it: a shaped phrase in a line of the other direction sat apart — v2.166.0 measures these scripts near their shaped width (marks zero, Arabic a quarter of the way from medial to final, Indic at 0.72, calibrated on running text) and keeps each shaped run on its reading edge, leaving a few pixels per word; v2.167.0 checked Persian and Urdu: digits no longer reversed, vowel marks and Nastaliq drawn from x/y positions (Nastaliq widths within 9-10px a word); v2.168.0 checked Kurdish, Pashto, Sindhi and Uyghur (correct) and spreads a line's width miss across its spaces; v2.169.0 checked the other eight Indic scripts (correct) and measures each at its own width, calibrated in the Browser; v2.170.0 composes decomposed accents (Vietnamese NFD and the like) onto their letters; Hebrew checked and correct unshaped; update-center installs need a reinstall for the conf flags |

## The ceilings, each measured

These are not gaps. Each was investigated, measured, and decided; the decision
is recorded where the code is, and repeated here so the arc's end is honest.

1. **Learning-space tutorial prose stays English** (the `TUTORIAL.md` a space
   generates; the 21 walkthroughs in `docs/tutorials` are translated since v2.153.0)
   — 130,863 characters, ~1.57M across twelve
   languages. The catalogue's `tutorial.<lang>` slot is open and a language is
   one line; the size is the reason nobody has filled it. A catalogue is
   scanned to choose, a tutorial read after choosing (v2.133.0).
2. **The NetBeans Platform ships no UI localization.** Measured: every `it`,
   `ja`, `ko`, `pt_BR`, `zh_CN`, `zh_TW` bundle in the cluster belongs to a
   third party (flatlaf, jaxb-xjc, simplevalidation). So platform surfaces we
   do not overlay read English in every build — which is why naming one in the
   reader's language points at a door that is not there (v2.101.1, v2.118.0).
3. **Three platform windows are named verbatim on purpose**: Output, IDE Tools,
   Services. They ship no overlay, so a translated name would be a wrong
   direction. Held by `WayfindingVocabularyTest`.
4. **The update catalog cannot name a module per language.** DTD 2.8 has no
   per-language element and the parser knows none (v2.141.0).
5. **Rack faceplates stay English by decision** — the silkscreen on a hardware
   panel. Shelf cards and section headings are catalogue prose and ARE
   translated (v2.132.0, v2.147.0); the faceplate is not.
6. **The menu bar and toolbar keep their language until a restart.** They are
   built once at startup; Options says so. Everything else re-languages live
   (v2.103.0).
7. **macOS per-app `AppleLanguages` never reaches the JVM** — proven with a
   probe bundle of the launcher's exact shape, so declaring
   `CFBundleLocalizations` would advertise a control the IDE ignores
   (ledger 94).
8. **Five mimes carry context-menu rows no file can reach** — `.less`/`.scss`
   lose to css-prep, `.dtd` to our own grammar, `.env` to the properties mime,
   `.markdown` to `text/markdown`. They paint nothing in any language and are
   recorded with what they lose to (v2.146.0).

## The instruments, and what each can see

- **The gates** read the assembled cluster. They see whether a key exists, is
  overlaid, is complete, and is shaped correctly. They cannot see the screen.
- **The census** (`PopupCensus`) asks the platform to build a menu and reads the
  rows, including ones no gesture of ours can open. It sees what a surface
  WOULD paint. It cannot tell you a heading is missing from its population.
- **The walk** (`DocsShots`, `-J-Dnmox.shots.dir`) paints real windows into PNGs
  with no screen permission, and a person reads them. It is the only instrument
  that has ever found a word nobody thought to look for.

Every one of the three has found something the other two called clean. The
arc's habit — *the gate and the walk are two instruments, and the one that
finds a thing is not always the one you were holding* (v2.139.0) — is the
reason this record can be written at all.
