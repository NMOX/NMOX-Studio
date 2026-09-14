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
| Surfaces that paint themselves: geometry kept, text mirrored by hand | `PaintedSurfaceLedgerTest`, population derived from the source; a mirrored surface names no absolute side |
| The website's and the I18n Kit's page direction | `I18nKitTest`, `SiteShipsTest` byte parity, logical CSS sides |

## The ceilings, each measured

These are not gaps. Each was investigated, measured, and decided; the decision
is recorded where the code is, and repeated here so the arc's end is honest.

1. **Tutorial prose stays English** — 130,863 characters, ~1.57M across twelve
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
