# Angular support — the parity scorecard

*Angular-top arc, 2026-08-11. The benchmark is the daily loop in the two
tools Angular developers actually use: WebStorm (the paid ceiling) and
VS Code with the Angular Language Service extension (the common floor).
Every NMOX cell states its proof; a claim without a live proof is
written as a claim.*

David's standing call (v1.214.0 era): stop investing in React, be
excellent at Angular. This document is the honest measure of that bet,
kept current every time the Angular surface changes.

## The daily loop

| Gesture | NMOX Studio | WebStorm | VS Code + ALS |
|---|---|---|---|
| Template syntax highlighting (`.component.html`) | ✅ the Angular team's own grammars, v1.217.0, live-proven | ✅ | ✅ |
| Suffixless template highlighting (content-detected) | ✅ v1.346.0 (@Component sniff) — with the ledger-77 caveat below | ✅ | ⚠️ needs the naming convention |
| Template type-checking against the component class | ✅ via ngserver, v1.218.0, live-proven ("Did you mean 'loggedIn'?") | ✅ built in | ✅ |
| **Selector navigation** — `<app-hero>` → its component | ✅ native ⌘B (CSL DeclarationFinder) + ⌥⌘B + ⌘-click, **works WITHOUT the language service** (own selector index, decorator-gated, comma-lists + attribute directives), live-proven | ✅ | ✅ (needs ALS running) |
| Go to definition on `{{ user.name }}` | ⚠️ ngserver-backed via popup; routing the ⌘B identifier case through CSL is queued (ledger 78 remainder) | ✅ | ✅ |
| Component ↔ template ↔ styles ↔ spec switching | ✅ v1.313.0, all four files, both decorator spellings | ✅ | ⚠️ extension-dependent |
| **Emmet in templates** | ✅ ⌥⌘E, v1.329.0, grammar-pinned | ✅ | ✅ |
| **Emmet in INLINE templates** (`template:` backticks in .ts) | ✅ Angular-top arc, decorator-gated, refuses outside the literal — live-proven both directions | ✅ | ⚠️ needs settings |
| Routes outline (app.routes.ts) | ✅ v1.314.0 — eager/lazy/redirect targets, children nested | ⚠️ generic structure | ❌ |
| `ng generate` as an IDE gesture | ✅ File ▸ New Angular Schematic…, v1.239.0; opens the created file, v1.346.0 (3 live rounds) | ✅ | ⚠️ third-party extensions |
| `ng serve` → browser loop in-app | ✅ v1.318.0 — Run → serving chip → in-app Browser at the CLI's own URL (loopback + h2c fixes underneath) | ⚠️ external browser | ⚠️ external browser |
| DevTools component tree of the running app | ✅ v1.222.0 — dev builds; prod build honestly says so | ❌ (browser extension) | ❌ (browser extension) |
| Angular-aware Run Focused Test | ✅ v1.223.0 — `ng test --include`, file-level (Karma has no name filter) | ✅ method-level where runner allows | ⚠️ extension-dependent |
| Language-service install friction | ✅ one click on the notification installs it INTO the project, trust-gated (Angular-top arc, mutation-proven); versions match the workspace by construction | ✅ bundled | ⚠️ extension global, version skew possible |
| Starter that actually installs | ✅ template npm-proven on default node (v1.241.0), node-floor refusals translated to human (v1.318.0) | ✅ | n/a |
| Rename symbol across template + class | ❌ not offered | ✅ | ✅ |
| Quick-fixes / code actions in templates | ❌ not offered | ✅ | ✅ |

## Where we are genuinely ahead

- **Selector navigation without a language service.** On a bare install
  (no `@angular/language-server`, no `node_modules` even half-warm) the
  #1 navigation gesture still works, from the project's own sources.
  Both competitors go dark without their service.
- **The serve → see → inspect loop never leaves the app**: serving chip,
  in-app Browser (with the loopback/h2c engine fixes no other embedded
  browser has needed to make `ng serve` render at all), DevTools Angular
  pane.
- **Starter truth**: our Angular template and learning space are
  npm-proven against the live Angular line before every pin moves.

## Honest gaps, in priority order

1. **Ledger 78 remainder — ⌘B on identifiers.** Tags now jump natively
   (the DeclarationFinder unit); the identifier case should route to
   ngserver inside the same CSL flow, queued with rename.
2. **Ledger 77 — suffixless template panes.** Content-detected templates
   get coloring and completion but not chord gestures (and the resolver's
   verdict looked session-dependent in the dev build). The
   `.component.html` majority case is unaffected.
3. **Rename across template + class, template quick-fixes.** ngserver
   exposes these over LSP; the platform client supports rename — this is
   wiring work, not research, and it is the next competitive tranche.
4. **Karma name-level test focus** is structurally capped at file level.

## Method note

Every ✅ in the NMOX column traces to a release with a live proof in the
shipped or dev app — the version numbers are the receipts. When a row
regresses, the fix starts by re-running that proof, not by re-reading
this table.
