# The Plan

*Written 2026-07-06, at v1.36.0, as a capstone digest by the assistant that
built v1.8→v1.36 with David. This is the current-reality companion to
[tech-debt.md](tech-debt.md) (the itemized ledger): where the project
stands, what's genuinely not done, what's worth doing next, and the working
method that got it here. Unlike most of docs/engineering/, this file is NOT
historical — keep it true or delete it.*

## Where the project stands

NMOX Studio is a shipping NetBeans RCP IDE (v1.36.0, 2,120 tests, six
release assets per tag, Homebrew cask) whose identity is the **Reason-style
task rack**: 44 hardware-styled devices wired with patch cables, backed by
real process execution, session resurrection, and CI export. Around it: a
48-grammar polyglot editor with LSP, four studios (API, DB, Contract/Web3,
Infra), the classic-web-first-class layer, Learning Spaces, and the v1.35
"connections" spine (ServingRegistry + ManifestPulse) that keeps every
surface live-synced to every other.

The v1.36.0 senior review is the trust anchor: six adversarial audit lenses
found the house laws **held** — the orphan-process guarantee, the storm
laws, the Keyring boundaries, the listener symmetry. What needed fixing was
concentrated in the two oldest surfaces and is fixed. The codebase's
verified state is the most valuable asset this project has; every section
below is written to protect it.

## Not done (the honest gaps)

Itemized with reasons in [tech-debt.md](tech-debt.md); the digest, ranked
by how much they'd matter to a daily driver:

1. ~~**Windows is build-verified, never test-executed.**~~ **Closed in v1.42.0** — windows-latest runs the full verify as a blocking gate (one evidenced disable; assembled-app probes remain POSIX, ledger 37). The original text, for the record: The release ships a
   real installer (bundled runtime, branded, byte-checked by
   windows-installer-check), but all 2,120 tests run on ubuntu+macos only.
   The suite leans on POSIX (`sh`, `sleep`, lsof fixtures). A Windows test
   lane is its own sprint: per-OS guards or fixtures for process tests,
   and expect the process-tree/charset laws to surface Windows-shaped bugs
   the moment they run there.
2. **`panic()` still blocks the EDT on Stop All and project-switch** (up to
   ~2.5s per stubborn device). The shutdown-hook path is correct and must
   stay synchronous; the interactive paths need an async redesign of the
   switch guard. Deferred because half-doing it risks the orphan guarantee.
3. **Studio workspace JSON writes ride the EDT debounce** (apiclient/infra/
   dbstudio, small local writes). Wrapping them in RP interacts with
   SelfWriteTracker self-write stamps and the close-flush ordering — needs
   one careful pass, not five scattered ones.
4. ~~**Module spec versions are frozen at 1.0**~~ **Closed in v1.47.0** —
   manifests carry the product version via one root `<spec.version>`
   property (filtered source manifests + nbm's `sourceManifestFile` seam;
   the reactor-version scheme was rejected to keep the tag the only
   version source), inter-module deps carry real ranges so mismatched
   modules refuse to load, and the release workflow stamps the property
   alongside branding. Gate: SpecVersionGateTest. Ledger 20 has the
   full design record.
5. **The autoupdate modules ship with no update center.** Either remove
   them from the cluster (Plugins menu implications) or build a real UC
   fed by the release workflow. Decide when there's a reason; the current
   state is dead weight, not breakage.
6. **JS/TS ride a custom lexer pipeline** while 46 other grammars ride
   TextMate+CSL — two code paths for one concern. Migrating JS/TS to
   TextMate would delete the lexer but lose the regex-aware token
   subtleties and the completion hooks built on it. Only worth it bundled
   with an editor-intelligence sprint (below).
7. **i18n**: ~450 hardcoded UI strings. A reality note, not a plan — do it
   only if a non-English audience actually materializes.
8. **Small residue**: `.sass` indented dialect approximated by the SCSS
   grammar; project templates hardcoded in ProjectTemplates.java (fine at
   this scale; extract to data when a second consumer appears); Docker
   connection-offer balloon fades in ~1s (LOW priority balloon — bump
   persistence); the `netbeans.default_userdir_root` boot warning (launcher
   conf word-splits on "Application Support"; needs a launcher-safe fix);
   MySQL learning space skipped (REPL model needs a live server).

## Could be done (opportunities, in the product's own voice)

Ideas that fit the identity — the rack as the honest, visible automation
surface. Each earns its place only as a full vertical slice (device +
tests + docs + live verify), never as a checkbox:

- ~~**Debugging completes the loop.**~~ **Done in v1.37.0** — JS/TS now
  debug in-IDE through the vendored js-debug adapter and the `DapProxy`
  multiplexer. What's left of this thread: **browser/Chrome debugging**
  (js-debug speaks it; needs a launch config and a target picker) and
  **multi-process sessions** (today one child session per run; child
  processes run undebugged).
- **Git as a surface, not just a device.** The ide cluster already ships
  git internals; the GIT device runs commands. A diff/blame/history
  experience (or PR review via `gh`) is the second-largest gap for a
  daily driver. Start from what the platform gives for free.
- **The IDE's own accessibility.** The rack's knobs, LEDs, and LCDs are
  custom-painted Swing — visually rich, screen-reader opaque. VITALS gates
  WCAG for the *user's* project while the IDE itself is unaudited. Honest
  fix: accessible names/roles on every control (the widget library is one
  file — one sweep covers all 44 devices), keyboard operation for knobs.
- ~~**Performance: the 7s cold start**~~ **Re-measured and largely done in
  v1.38.0.** The 7s figure (v1.26) no longer reproduces — the v1.33.x storm
  fixes removed it. Fresh numbers (M-series, JDK 23, startup-log + JFR):
  window painted in **1.4s** (warm OS cache) to **2.7s** (cold), fully
  quiet ~4.5s. ~90% of time-to-window is the platform's module-system
  Preparation — I/O-bound scanning of 519 cluster jars, the deliberate
  price of shipping editors/git/db (310 modules on). What v1.38.0 DID fix:
  every piece of boot work hidden default-open tabs were doing — `npm ls
  -g` spawned twice per boot (the only processes the IDE launched at boot,
  per JFR), Contract Studio's artifact tree walk, the dockerize detect
  walk, and the ctor+componentOpened double-fires — all now gated on
  componentShowing or deduped, JFR-verified zero boot spawns. What's left
  is cluster trimming for ~100ms/30 jars — not worth the feature risk.
- **A public device SPI.** Deliberately deferred (v1.35 ledger): third
  parties writing rack devices. The internal `RackDevice` contract is
  stable and storm-law-tested now; opening it means freezing it. Do this
  only after spec versions (gap #4) are real, or every plugin breaks on
  every release.
- **Learning Spaces as a community catalog.** 52 built-in; the catalog is
  already data-driven JSON. A `~/.nmox/learn-catalog.d/` drop-in dir plus
  a documented schema is a small change with outsized reach.
- **AI assistance, if ever, through the rack's metaphor.** An "ORACLE"
  device that explains the error currently on the MONITOR bus would fit;
  a chat sidebar would not. The product's differentiation is tactile
  honesty — anything added should be visible, wired, and unpluggable.

## Planned (the method — keep doing this)

The cadence that produced 28 releases without a broken main:

1. **Sprint → gated PR → tag → live-verify.** One background fail-closed
   pipeline script per release (see the scratchpad templates and the
   `gated-ship-pipeline` memory): local `mvn verify` before push, CI green
   before merge, main green before tag, 6 assets before done. Never touch
   the tree while it runs.
2. **Review-then-fix with evidence.** The v1.36.0 shape worked: read-only
   audit lenses first, findings with file:line proof, then triage into
   FIX-NOW / FIX-LATER-with-reason / FINE-AS-IS-blessed. Fixes carry
   regression tests proven against the old code where possible.
3. **The ledger is decisions, not wishes.** Every deferred item in
   tech-debt.md has a reason; every blessed oddity has a written rationale
   (SpotBugs exclusions, coverage exclusions, SOLDER's no-shell stance,
   org.json's 8-copy isolation). If a future session can't find the
   reason, it will "fix" something deliberate.
4. **Docs truth pass every ship.** CHANGELOG + CLAUDE.md status/history +
   README claims + ledger. devices.md is generated and CI-gated — the
   model for any future generated doc.
5. **Live-verify before claiming fixed.** Hard rule since v1.33.2. Boot
   the real assembled app; user-visible fixes get a click-through.
6. **Walk a persona's whole journey, not the feature you just built.**
   v1.38.1: debugging a real server end-to-end (breakpoint inside a live
   HTTP request) confirmed the engine — and the *surroundings* were where
   the bugs were: shortcuts that opened the wrong window, a console that
   never surfaced, a file chooser rooted in a folder we never create.
   Every one of them sat one keypress off the path any feature test takes.
   Press the keys the docs promise; open the windows the feature implies.

**The house laws** (each earned by a real incident — enforcement lives in
CI gates and regression tests, this list is the index): no EDT I/O and no
EDT process spawns, including the *mutation* half (v1.33.x, v1.36.0);
listeners bounded + equality-guarded + attach/detach symmetric per open
(v1.33.2, v1.35, v1.36.0); process timeouts are waitFor-first with both
streams drained on threads and the **whole tree** killed — dash spawns
grandchildren where bash execs (v1.36.0 ubuntu CI); UTF-8 explicit at
every byte↔char boundary; secrets in the OS Keyring only, never prefs or
files, RPC URLs never serialized; DialogDisplayer/NotificationDisplayer,
never JOptionPane; prefs values under 8KB, lists as one-entry-per-item;
coverage floors on the testable surface with pure-Swing excluded by name
with reasons; rack tests drain the EDT *and* the router
(`awaitRouterIdle`) before asserting; workspace files are written atomically —
temp sibling + `ATOMIC_MOVE` via core `AtomicFiles`, never truncate-then-write
(v1.39.0); file create/rename/delete goes through DataObject/FileObject so open
editors follow (v1.39.0).

**Failure patterns to grep for in new code** — every bug class that
actually shipped, once: constructor-attached listeners on TopComponents
(the remove-half retrofitted without the re-add half); read-to-EOF before
a timed waitFor; a corrupt file loading as empty then autosaving emptiness
over the original; `invokeLater(this::self)` while a component is 0×0;
full-refresh listeners fanning out per event across default-open tabs;
filesystem walks of `$HOME` on any thread the user waits on (macOS TCC);
unverified `pkill`; **a keyboard shortcut registered but never pressed** —
`Shortcuts/`-folder registrations lose silently to the platform's Keymaps
profile, so four studio chords opened Properties/Tasks/Palette/Editor for
three releases while the Welcome screen advertised them (v1.38.1); **a UI
affordance documented but never exercised** — the Breakpoints window was
empty from the day debugging shipped; **a test that spawns a process into
its `@TempDir` must confirm the process dead — or point its cwd elsewhere —
before cleanup** (Windows file locking; three incidents in v1.42.0).

## What I'd do next, in order

If the next session asks "what now": **(1)** ~~the git surface~~ — v1.40.0 shipped the branch chip + History; the
remaining depth (Show Changes/Diff from our surfaces) is blocked on the
ledger-29 context migration, which now has its first concrete customer; **(2)**
~~the rack accessibility sweep~~ — shipped in v1.41.0 (roles, keyboard
knobs, focus rings, the accessible-name law CI-gated in DeviceContractTest); **(3)** Windows test lane before any of the
above grows Windows-specific code paths; **(4)** browser debugging,
extending v1.37.0's adapter. (Startup performance was measured and
closed out in v1.38.0 — window in 1.4–2.7s, zero boot spawns; see the
struck item above before reopening it.) Resist: new studios (six tabs is the discovery ceiling),
new languages (48 grammars is past diminishing returns), and any feature
that can't be drawn as a device with an honest control surface.
