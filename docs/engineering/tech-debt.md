# Technical Debt Ledger

The **current** debt record, rewritten during the v1.22.0 Snow Leopard
sprint (2026-07-03). Every entry is either open with a reason it was
deferred, or closed with the version that closed it. The v0.x-era debt
documents in `docs/hack/` are archaeology; this file is the truth.

## Open — deferred deliberately, with reasons

### 1. Rack faceplate boilerplate (~250–300 LOC across 25+ devices)
Every CommandDevice subclass hand-places the same transport cluster
(GO/STOP buttons, tool knobs, status LCD). A base-class helper would
kill the duplication, but migrating 25+ device constructors risks
subtle geometry regressions for purely cosmetic payoff, and a partial
migration would leave two idioms — worse than one verbose one.
**Do it as its own focused sprint with before/after screenshots per
device.** (Audited v1.22.0.)

### 2. Build/Test/Run toolchain switches (~60–80 LOC triplicated)
BuildDevice, TestDevice, and RunDevice each carry a ProjectKind→command
switch. One `ToolchainBuilder` table would make a new language a
single edit. Deferred from v1.22.0 because the three switches differ
in shape (prod/watch branches, per-kind dirs) and the devices are the
IDE's hot path — consolidate when the next language lands and the
change can prove itself against the string-exact device tests.

### 3. JSON persistence boilerplate (RackIO / GraphIO / WorkspaceIO)
Same save/load shape three times. NOT consolidated into core on
purpose: each NBM module wraps its own org.json copy, so a shared
helper returning JSONObject would pass org.json types across module
classloaders — ClassCastException territory. The String-only helpers
that were safe to share did move (see JsonUtil, closed below).

### 4. Hardcoded project templates (ProjectTemplates.java)
Templates live as Java string literals; data-driven templates
(resources + substitution) would open the door to user templates.
Big refactor, zero user-visible payoff until user templates are a
feature. Wait for the feature.

### 5. JS/TS ride a custom lexer; everything else rides TextMate+CSL
Two editor pipelines to maintain. Unifying JS/TS onto TextMate would
delete the custom lexer but lose its regex-awareness unless carefully
matched. Architectural change; needs its own sprint with fixture-based
before/after highlighting comparisons.

### 6. .sass (indented dialect) shares the SCSS grammar
Approximate highlighting for the indented dialect. Correct fix is a
dedicated grammar; demand has not justified it.

### 7. Startup: rack UI construction (~200–400ms EDT during restore)
The palette builds all 39 device entries during window-system restore.
Could defer shelf population to first paint. Measured as the largest
remaining self-inflicted startup cost after v1.22.0's journal fix, but
it's inside platform window restore — optimize only with profiler
evidence, not vibes. `scripts/boot-smoke-test.sh` now prints wall-clock
boot time so CI can watch the trend.

## Closed

- ~~UTF-8 charsets implicit at 12 I/O sites (session state, CI export,
  package.json parsing)~~ — **v1.22.0**, explicit everywhere.
- ~~Wizard disk I/O + PNG encoding on the EDT~~ — **v1.22.0**,
  RequestProcessor + EDT hop for the report.
- ~~API Studio autosave failed silently (chronic failure = silent data
  loss)~~ — **v1.22.0**, warns once per failure streak + logs.
- ~~Status-line and session-snapshot timers never stopped~~ —
  **v1.22.0**, lifecycle-bound.
- ~~Command relaunch skewed a running command's elapsed readout~~ —
  **v1.22.0**, per-launch capture.
- ~~PING held 50 uncapped response bodies (~3.5MB worst case)~~ —
  **v1.22.0**, capped at record time.
- ~~FlightRecorder journal read+parsed synchronously at boot~~ —
  **v1.22.0**, deferred off the startup path.
- ~~JOptionPane in 12 rack files (26 sites) bypassing platform
  theming; raw ex.getMessage() shown to users~~ — **v1.22.0**,
  DialogDisplayer everywhere with task-oriented messages.
- ~~ProcessSupport/ToolLocator lived in rack; editor depended on rack
  for process launching; tools used raw ProcessBuilder (no PATH
  augmentation — npm "missing" when launched from Finder)~~ —
  **v1.22.0**, promoted to core.process, adopted in tools; the
  Windows-breaking hardcoded /dev/null in ProjectTemplates died with it.
- ~~Four HttpClient pools; looksJson/pretty duplicated three times~~ —
  **v1.22.0**, core.http.HttpClientFactory + core.util.JsonUtil.
- ~~core/ui/tools/project effectively untested; apiclient engine
  half-tested~~ — **v1.22.0** backfill + floors (see module poms).
- ~~CLAUDE.md four releases stale; ~30 v0.x aspirational docs posing as
  current; three test scripts checking classes that never existed~~ —
  **v1.22.0**, truth pass: banners, updates, deletions.
