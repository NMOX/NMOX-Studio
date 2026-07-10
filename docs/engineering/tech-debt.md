# Technical Debt Ledger

The **current** debt record. Rewritten during the v1.22.0 Snow Leopard
sprint, extended by the v1.23.0 completeness sprint, worked through
end-to-end by the v1.26.0 complete-system sprint (2026-07-03), and
re-audited whole by the v1.36.0 senior-review sprint (2026-07-05: a
six-lens read-only architecture audit, then fixes for everything it
proved). Every entry is either open with a reason it was deferred, or
closed with the version that closed it. The v0.x-era debt documents in
`docs/hack/` are archaeology; this file is the truth.

The v1.26.0 sprint took a rule to the whole ledger: **build every
feature-shaped item; re-examine every refactor-shaped item with fresh
evidence rather than a remembered reason.** The feature items (0a–0f)
all shipped. The refactor items (1–7) were each re-inspected — most
verdicts held and two sharpened into outright won't-fix once the code
was read again rather than recalled. A deferral you can defend after
re-reading the code is a decision; one you only remember making is a
guess. These are decisions.

## Open — deferred deliberately, with reasons

### 1. Rack faceplate boilerplate (~250–300 LOC across 25+ devices)
Every CommandDevice subclass hand-places its transport cluster (GO/STOP
buttons, tool knobs, status LCD). Re-examined in v1.26.0: the devices
do **not** place that cluster at shared coordinates — TAIL puts FOLLOW
at x366, TEMPO puts CLOCK at x124, others differ by faceplate width and
label. A single base helper can only serve them by taking (label, x, y)
per call, which is barely shorter than the explicit `place(new
RackButton(...), x, y)` it would replace. So the "duplication" is a
repeated *idiom*, not repeated *values* — and the 241-assertion
DeviceContractTest exists precisely because that geometry is load-bearing
and per-device. Consolidating would touch 25+ constructors to save ~2
LOC each while risking the exact regressions the contract test guards.
**Verdict: won't fix as boilerplate.** If it's ever done, it's a
visual-QA sprint with before/after screenshots per device, not a
mechanical extraction. (Re-audited v1.26.0.)

### 2. Build/Test/Run toolchain switches (~60–80 LOC across three devices)
BuildDevice, TestDevice, and RunDevice each carry a ProjectKind switch.
Re-examined in v1.26.0 by reading all three: they map the **same enum
to three different verbs** — build commands, test commands, run targets
— with no command string shared between them. There is no cross-device
duplication to remove; each device owns its own verb's commands and
nothing else. The repeated `case RUST/GO/BUN/...` skeleton is the
compiler enforcing exhaustiveness, which is the feature that guarantees
a new language can't be added to one verb and silently forgotten in the
others. A `ToolchainCommands` class would relocate three unrelated
methods into one file and dedupe nothing. **Verdict: won't fix — the
premise (shared command logic) does not survive reading the code.**
(Re-audited v1.26.0; superseded the v1.22.0 "consolidate when the next
language lands" note, which assumed a duplication that isn't there.)

### 3. JSON persistence boilerplate (RackIO / GraphIO / WorkspaceIO)
Same save/load *shape* three times. NOT consolidated into core on
purpose: each NBM module wraps its own org.json copy, so a shared helper
returning JSONObject would pass org.json types across module
classloaders — ClassCastException territory. The String-only helpers
that were safe to share already moved (JsonUtil, closed below). What's
left is per-module glue that must stay per-module. Re-confirmed against
the module classloader boundary in v1.26.0. **Verdict: won't fix —
architectural constraint, not laziness.**

### 4. Hardcoded project templates (ProjectTemplates.java)
Templates live as Java string literals; data-driven templates (resources
+ substitution) would open the door to *user* templates. Big refactor,
zero user-visible payoff until user templates are a roadmapped feature.
Wait for the feature — the refactor is that feature's first task, not a
standalone debt item.

### 5. JS/TS ride a custom lexer; everything else rides TextMate+CSL
Two editor pipelines to maintain. Unifying JS/TS onto TextMate would
delete the custom lexer but lose its regex-awareness (the reason it
exists) unless carefully matched. Architectural change; needs its own
sprint with fixture-based before/after highlighting comparisons across a
JS/TS corpus. Not mechanical, not blind.

### 6. .sass (indented dialect) shares the SCSS grammar
Approximate highlighting for the indented dialect. The correct fix is a
dedicated indented-sass TextMate grammar (a curated upstream fetch +
scope-mapping pass), not a code change here. Demand has not justified
the grammar-sourcing work.

### 7. Startup: rack UI construction (~200–400ms EDT during restore)
The palette builds all 39 device entries during window-system restore.
**Measured in v1.26.0**: `scripts/boot-smoke-test.sh` reports a 7-second
cold boot-to-exit on a fresh userdir — dominated by JVM warm-up and
first-run module install/enable, not by the palette. The rack shelf's
~0.3s is under 5% of cold boot and sits inside platform window restore.
Deferring shelf population to first paint would shave a fraction of a
second off a 7s boot in exchange for lazy-init complexity and real
regression risk. **Verdict: won't fix until a profiler names the palette
specifically** — the boot-smoke number says it isn't the bottleneck.

## Open — deferred deliberately, with reasons (added v1.38.1)

### 27. The Breakpoints window never lists DAP breakpoints
Found by the v1.38.1 DX pass: set a JS breakpoint, hit it inside a live HTTP
request — Window ▸ Debugging ▸ Breakpoints stays empty, during and after the
session. Reproduced identically with a **Python** breakpoint, which runs
entirely on the platform's own DAP path and touches none of our code, so this
is not an NMOX regression. The machinery all appears present: spi-debugger-ui
registers BreakpointsTreeModel/NodeModel under `Debugger/BreakpointsView`, and
lsp-client registers its own `BreakpointModel` there plus a
`DAPBreakpointActionProvider` that calls `DebuggerManager.addBreakpoint`. Yet
the view shows nothing. Consequence for users: the editor gutter is the only
breakpoint manager — no disable, no conditions, no delete-all, no overview.
**Deferred**: fixing it means either finding the upstream defect (a day of
platform archaeology in a view-model chain we don't own) or shipping our own
TreeModel/NodeModel for DAPLineBreakpoint — a real sprint, and one that would
fork behaviour from stock NetBeans. Worth reporting upstream first. The user
guide now says plainly that breakpoints are managed in the gutter, because a
silently empty window is worse than a documented limit.

### 28. Window-menu items for our studios show no accelerator
`@ActionReference(path = "Shortcuts", …)` binds the chord but does not put it
next to the Window-menu item, so the studios' ⌥⌘ chords are discoverable only
from the Welcome launchpad and the docs. The platform's own windows get their
accelerators from Keymaps-profile shadows. Cosmetic, and fixing it means
duplicating each registration into a keymap profile — deferred until someone
actually reports missing them.

## Open — deferred deliberately, with reasons (added v1.37.0)

### 25. One debug session per run: child processes run undebugged
js-debug is a *multi-session* adapter: after `launch` it sends a
`startDebugging` reverse request per debug target, expecting the client
to open another socket. The platform's `DAPConfiguration` is
single-session and cannot dial a second one, so `DapProxy` splices
exactly **one** child session onto the client's connection and the
launch config sets `autoAttachChildProcesses: false`. Consequence: a
script that forks (a worker, a `child_process.spawn`) debugs the parent
only — the children run at full speed instead of stopping at a
breakpoint. This is the honest failure: the alternative (leaving
auto-attach on) makes js-debug ask for sessions the client can't open,
and the debuggee pauses forever waiting for an attach that never comes.
Fixing it properly means either a platform change (a DAP client that
accepts N sessions) or the proxy synthesizing multiple pseudo-clients
and multiplexing their UIs — the second is a sprint, not a patch.
**Deferred**: single-target debugging is the overwhelmingly common case,
and the current behaviour is "children don't break" rather than "the
session hangs." Browser/Chrome debugging (same adapter, different launch
type) hits the same ceiling and waits on the same fix.

### 26. Vendored js-debug is invisible to Dependabot and the SBOM
`editor/src/main/release/jsdebug/` carries js-debug v1.117.0 (MIT, 2.5MB)
as committed bytes, because upstream publishes no npm package — the
GitHub release tarball is the only artifact. It therefore has no
coordinate for Dependabot to watch and does not appear in the CycloneDX
SBOM the release workflow generates, which is exactly the blind spot the
SBOM exists to remove. Mitigation today is manual and written down: the
version, source URL, and sha256 live in the vendored `NOTICE`, which
also states the invisibility outright so nobody trusts the SBOM to cover
it. **Deferred**: the real fixes are to either teach the release workflow
to append a manual SBOM component (small, but hand-maintained metadata is
its own staleness risk) or to fetch the tarball at build time (trades a
reproducible committed artifact for a network dependency in CI). Revisit
when a second vendored non-Maven artifact appears — one is a note, two is
a mechanism.

## Open — deferred deliberately, with reasons (added v1.36.0)

The v1.36.0 senior review fixed everything cheap-and-clearly-right its
six audit lenses confirmed (see CHANGELOG). What follows is what the
audit found and the sprint *deliberately did not fix*, each with the
reason it can wait.

### 15. panic() still blocks the EDT on Stop All and the switch guard
`RackDevice.panic()` = synchronous `killAndWait(1500)` — correct in the
shutdown hook (async escalation threads never run at shutdown), but the
EDT paths (Stop All, `stopLiveForSwitch`) can freeze up to ~2.5s per
stubborn live device. The fix is an async stop flow with the switch
decision waiting on a callback — a redesign of the confirm-then-switch
sequence, not a wrapper. Bounded, rare (only stubborn processes), and
the freeze reads as "stopping tools" — deferred until the switch flow
is next opened.

### 16. Studio workspace saves run on the EDT debounce
ApiClient/Infra/DbStudio write their small workspace JSONs from Swing
debounce timers on the EDT (reads got the off-EDT care; the write half
didn't). Local files, milliseconds each — but wrapping them in an RP
interacts with the close-flush ("stop(); save()") ordering and each
studio's SelfWriteTracker stamp discrimination, so it needs its own
careful pass with storm tests rather than a drive-by wrap. The v1.36.0
sprint fixed the *recurring* EDT write (session snapshots, 5s cadence);
these are per-edit debounces.

### 17. RackPanel / RackTopComponent constructor-wired rack listeners
Both singletons attach in their constructors and never detach, so
presets/Learning Spaces rebuild faceplates into a closed rack window.
Bounded (singletons, no leak) and purely offscreen-CPU; the fix needs a
reopen-resync path that rebuilds state on attach, which carries real
regression risk for a cost that is idle repaints. The Workbench got the
fix (its listener drove per-project disk walks); these two wait.

### 18. CommandExecutor exit detection and stale-run guards
Two hardening ideas from the lifecycle audit: drive exit from
`process.onExit()` with a bounded drain (today a forcibly-killed
process whose pipes linger can delay `onFinished`), and a per-launch
generation counter so a stale run's `onFinished` can't drop the gate of
the run that replaced it. Both are engine-core changes under the
device-contract tests; neither has a reproduced failure in the wild.
Queued behind a reproduction or the next engine sprint.

### 19. Rack polish cluster: undo across presets, trigger bookkeeping
Loading a preset/patch doesn't clear undo history (⌘Z can "undo" into
the previous patch's structure), `lastTriggerAt` entries survive device
removal, and TAIL/TEMPO don't re-sync their displays on undo re-attach.
All small, none data-loss, all in one undo/presets neighborhood — one
housekeeping slice when the rack is next open.

### 20. Module spec versions are frozen at 1.0
Every NBM ships OpenIDE-Module-Specification-Version 1.0 because the
reactor POM version is `1.0-SNAPSHOT` and nbm-maven-plugin derives from
it. Nothing consumes the spec versions today (no update center, no
inter-module version ranges). The clean fix is a coordinated reactor
version scheme (all 13 poms tracking the release train); hardcoding
1.36 per manifest would just re-freeze one release later. Do it as part
of any future update-center story (see 21).

### 21. Platform autoupdate modules ship with no update center
The Plugins infrastructure is in the cluster but no UC is configured —
dead weight in the download, ~harmless at runtime. Trimming the
autoupdate modules changes the Plugins menu and needs a release-size
check on all three OS packages; the unused build-side update-site
generation (a dead `deployment` profile) was already removed in
v1.36.0. Revisit with 20 if an update-center story ever lands.

### 22. `netbeans.default_userdir_root` boot warning
One cosmetic WARNING per boot. The conf-file route word-splits on
macOS's "Application Support" path; the safe fix is launcher-side and
per-OS. Not worth launcher risk for a log line — batch it with the next
installer/launcher sprint.

### 23. org.json rides in 8 module copies (~710 KB total)
Re-confirmed as the correct architecture (module classloaders make a
shared wrapper ClassCastException territory — see ledger item 3). The
one real improvement is a single `<orgjson.version>` root property so
Dependabot bumps all eight in one PR. Trivial, but touches all module
poms — batched with the next dependency sweep.

### 24. i18n: ~450 user-visible strings are hardcoded
The house style is deliberate English-only UI (Bundle.properties exists
only where the platform requires it). Recording the reality: NMOX
Studio is not localizable today, and making it so is a dedicated
sprint's worth of @Messages migration, not incremental cleanup.

## Open — deferred deliberately, with reasons (added v1.35.0)

### 14. Connections: what the corpus callosum deliberately doesn't carry
v1.35.0 wired the parts together (ServingRegistry, ManifestPulse, studio
auto-reload, Docker→DB offers); v1.35.1 closed its three small IOUs
(php -S serving registration, SelfWriteTracker→core, API Studio re-aim
following). Still deferred, with reasons:
- **A public plugin-facing event API** — the registry and pulse stay
  module-internal until an external consumer exists.
- **Docker offers beyond databases** (redis/rabbitmq/…) — DB engines
  only; other services have no studio to offer into yet.
- **WebSocket/live push** — polling registries are honest and simple;
  revisit only if a real lag complaint appears.

## Open — deferred deliberately, with reasons (added v1.34.0)

### 13. Classic web: the second shelf stays deferred
v1.34.0 made jQuery/MooTools/Prototype/Backbone/Knockout, Webpack/Grunt/
Gulp/Bower, CoffeeScript, and manifest-less script-tag sites first-class.
Deliberately NOT built, with reasons:
- **YUI / Dojo / ExtJS completion + kit entries** — genuinely rarer in
  surviving codebases, and ExtJS licensing complicates bundling; the
  script-tag detection still names them in no way that misleads (they
  simply aren't badged). Add per demand, one catalog JSON block each.
- **Dedicated AngularJS 1.x tooling** — such projects open fine via the
  script-tag/bower/grunt support; HALO stays Angular 2+. A 1.x-specific
  device would imply migration tooling we don't have.
- **RequireJS / Browserify build lanes** — in the wild these run via
  package.json scripts, which FORGE already executes; a dedicated lane
  would duplicate the npm-script path for near-zero reach.
- **jQuery 1.x→3.x migration assistant** — the EOL chip is honest
  awareness; automated rewriting (deprecated API scan + jquery-migrate
  wiring) is a real feature for a future sprint, not a checkbox.
- **Literate CoffeeScript (.litcoffee)** — rides the source.coffee
  grammar approximately (the .sass/SCSS-style honesty note applies).

## Open — deferred deliberately, with reasons (added v1.33.0)

### 12. Contract Studio never signs — and that's the design, not the debt
No private-key handling of any kind: sends/deploys work only against a
devnet's unlocked accounts (eth_sendTransaction); remote networks are
read-only in the Studio. Revisit only with a hardware-wallet story
where the key still never enters the IDE. What IS deferred:
- **Tuple/struct ABI parameters** — parsed (functions list fine) but
  refused at encode time with a pointer to `cast`. Build when a real
  project needs it; the encoding is mechanical but the form UX isn't.
- **eth_subscribe websockets** — the Watch pane polls at 2s, honest and
  simple; the shared HttpClient has no WS. Revisit if devnet watching
  ever feels laggy.
- **Vyper / non-EVM chains (Solana, Move, ink!)** — grammar-only
  support would mislead without a toolchain behind it.
- **slither as a rack lane** — Doctor probes it and hints the install;
  running it well needs a Python-env story. TYPEGUARD's solhint lane
  covers day-to-day linting.
- **Foundry project template** — `forge init` does it better (pulls
  forge-std, sets remappings); a wizard shelling out to it is a later
  nicety.

## Open — deferred deliberately, with reasons (added v1.29.0)

### 10. DB Studio: Mongo cancel is a no-op; cursors read firstBatch only
Driver-level operation kill and `getMore` continuation are real work with
a small v1 audience; both are documented in the backend javadoc and the
UI truncation flag is honest about partial reads.

### 11. DB Studio: no live Mongo/Couch integration tests
The parse/command/flatten logic is seam-tested against canned responses
(the DigitalOceanClient idiom); SQLite carries the real end-to-end JDBC
burden in CI. Live-server tests would need containers in CI — revisit if
a regression ever slips through the seams.

## Open — deferred deliberately, with reasons (added v1.28.0)

### 8. No MySQL/MariaDB learning space
Learning-space REPLs launch a local interpreter the user types into;
`mysql`/`mariadb` clients need a live server to connect to, which breaks
the zero-setup type-in-and-learn model. The SQLite space already teaches
SQL against a real engine, and the Database Explorer (ships in the box)
covers working with live MySQL. Revisit only if a self-contained embedded
option (e.g. a bundled mariadb --no-defaults sandbox) proves practical.

## Closed by v1.36.0 (the senior review sprint)

A six-lens read-only audit (platform/module shape, device/process
lifecycle, listener symmetry, timers, EDT & process hygiene, API
quality & house laws) followed by fixes for everything it proved. The
audit's strongest result was negative space: the orphan-process
guarantee, the storm laws, the Keyring boundaries, and the v1.35
listener-symmetry pass all **held** under adversarial reading. What it
caught clustered in the two oldest surfaces and in the *mutation* half
of code whose read half was fixed in earlier wars. Highlights (full
list in CHANGELOG 1.36.0):

- ~~Infra Designer attached its listeners in the constructor and removed
  them on close — one close/reopen killed auto-save, disabled the
  never-clobber dirty guard, and could write project A's design into
  project B's `.nmoxinfra.json`~~ — listeners attach per-open, reopen
  re-loads, close flushes the debounce, save failures warn once.
- ~~DockerClient read stdout to EOF before starting its 15s timeout —
  a wedged daemon pinned all four pool threads forever and silently
  bricked every Docker feature until restart~~ — drain threads + real
  timeout (regression test fails on the old code). The same
  read-before-timeout idiom died at four more sites via the new
  `ProcessSupport.runBounded` (PortScanner was eating commonPool
  threads app-wide; CommandProbe, ProjectTemplates, NpmService).
- ~~New Project ran template writes + four git spawns on the EDT;
  Experiments discard/promote ran recursive deletes and git on the
  EDT; file-tree delete could beachball for minutes on node_modules~~
  — all on RequestProcessors with the dialogs honestly locked.
- ~~A corrupt studio workspace file loaded as empty and the first edit
  autosaved emptiness over the user's original (sharpest case: web3's
  deployment address book)~~ — all four studios keep a `.bak` of the
  unreadable original and say so.
- ~~Infra cloud API tokens lived in plaintext Preferences while every
  sibling secret rode the OS keychain~~ — Keyring with a migration
  that deletes the pref only after the keychain save succeeds.
- ~~The editor layer.xml's hand-written JS loader actions collided with
  the annotation-generated set (three same-position warnings, Tools/
  Properties rendered mid-menu); duplicate `Editors` folders; a dead
  type-mismatched editor-kit entry; four ⌘I category position
  collisions~~ — annotations own loader wiring; one merged folder;
  renumbered categories.
- ~~Dead code shipped: the never-consumed `tools.build` service (with a
  latent pipe deadlock), v0.x `CodeIndexService` (two executors, a
  watch loop that died on first exception), the sample module in the
  product, a dead `deployment`/update-site profile, a wildcard ui
  export, a dead ui→tools dependency~~ — all deleted; net −3,000 lines.
- ~~A deleted-mid-serve device could leave a ghost ⇄ serving entry, and
  a queued signal could launch a process into a disposed device~~ —
  dispose deregisters; a disposed flag (cleared on undo re-attach)
  guards the router and exec.
- ~~The v1.22 JOptionPane eviction missed exactly one; 15
  printStackTrace sites could pop the red exception dialog for routine
  races (worst: a malformed package.json the user is mid-edit on)~~ —
  DialogDisplayer / named-logger INFO/FINE, with the 11 identical
  completion bodies collapsed into one tested helper.

Also closed here: **v1.33's REPL-INSTALL item** (the argv-split entry
below the classic-web section) was actually fixed in v1.35.1 — compound
catalog commands run via `/bin/sh -lc`; SOLDER keeps its no-shell
stance for user-typed commands. Removed from the open list where it had
lingered.

## Closed by v1.32.0 (DB Studio 2, the working-DBA sprint)

### 9. DB Studio results are read-only — CLOSED
Built as its own sprint, exactly as the deferral prescribed. The
primary-key plumbing is `EditGate` (single-table SELECT parse +
column-metadata PK check, off-EDT, with an honest reason string for
every refusal); the dirty-state UX is `EditSession`/`EditableResultsModel`
(tinted cells, pending-edits chip, Revert); the write path is
`UpdateBuilder` (dialect-quoted, PK-addressed UPDATEs shown verbatim in
an Apply preview before running, stop-on-first-failure, re-run-for-truth
after). Document engines stay read-only by design — the gate says so in
words rather than silently refusing. Deliberate v1 limits, recorded
here: PK cells are not editable (the key addresses the row), NULL PK
originals are refused (grid can't distinguish SQL NULL from a 'NULL'
string), typing `NULL` into a cell means SQL NULL, and INSERT/DELETE
remain console work.

## Closed by v1.27.0 (the coverage sprint)

- ~~Thin/absent test coverage in several modules (ui at 0.4%, project at
  12%, tools at 21%) and no coverage floor at all on ui/project~~ — ~320
  new unit tests raised every module's testable-surface coverage; all
  eight code modules now carry an enforced JaCoCo floor. Coverage is now
  measured on the **testable surface**: pure-Swing windows/dialogs/canvases
  are excluded at the root pom, each a named class with a written reason
  (see the `<excludes>` block and [[coverage-measured-on-testable-surface]]).
- ~~`WorkspaceTrust` persisted all trusted paths as one joined preference
  value that could exceed java.util.prefs' 8 KB per-value cap and break
  trust on a long-lived install~~ — **v1.27.0**, rewritten to one entry
  per path (hash key + path value) with legacy-key migration; regression
  test trusts 200 long paths without overflow. Invisible on CI (fresh
  prefs each run); surfaced only once the local suite accumulated enough
  trusted paths.

## Closed by v1.26.0 (the complete-system sprint)

- ~~**0a.** Quick Search didn't index API Studio requests or infra
  nodes~~ — ⌘I now finds both: `ApiRequestSearchProvider` walks every
  collection's requests (method + name) and opens the request in API
  Studio; `InfraNodeSearchProvider` finds infra nodes by name/kind and
  selects them on the canvas. Registered via each module's layer.xml
  QuickSearch category. Cross-tab search is real now, not deferred.
- ~~**0b.** "Sync from DigitalOcean" was DO-only~~ — now **"Sync from
  cloud"**, multi-provider: DigitalOcean (13 endpoints), Hetzner Cloud
  (servers/networks/load-balancers/volumes/firewalls/floating-ips), and
  Cloudflare (zones → DNS records). Per-provider failure isolation —
  one cloud's outage or bad token never aborts the others; outcomes
  surface honestly per provider. Dedupe-by-id refreshes existing nodes
  in place rather than stacking duplicates. R2 buckets stay out (no
  addressable id), consistent with v1.23.
- ~~**0c (SOLDER half).** CI export covered step devices but not
  SOLDER~~ — SOLDER (CMD) now exports as a GitHub Actions `run:` step.
  PREFLIGHT deliberately stays out: it's the local ship-gate, and in CI
  the workflow *is* the gate — exporting it would be a check auditing
  itself. That half is a decision, written here, not an omission.
- ~~**0d.** TAIL and TEMPO didn't resurrect~~ — both now report
  `isResumable()` from their arm switch (FOLLOW / CLOCK) and re-arm on
  `resume()`. They resurrect after a crash without reporting `isLive()`
  (which stays process-only, so the status line's "running" count never
  swells with timers). SessionState.capture() now snapshots
  `isResumable()` devices, not just live processes.
- ~~**0e.** Outline coverage stopped at 35 mimes~~ — added heuristic
  outline extraction for Haskell, OCaml, R, Perl, Julia, F#, Crystal,
  and Zig. Navigator (⌘7) now populates for 43 mimes.
- ~~**0f.** The update check's opt-out had no Options UI~~ — Tools >
  Options > NMOX Studio now has a "Check for updates on startup"
  checkbox bound to the `updateCheck` preference. No more hidden pref.

Plus one capability that wasn't on the ledger but belonged in a
complete system:

- **Rack undo/redo** — every structural rack edit (add/remove/move
  device, connect/disconnect cable) is now reversible. ⌘Z / ⇧⌘Z, a
  100-deep bounded history, inverse ops that restore a removed device
  *with its cables re-patched*. Bulk operations (default-rack load,
  patch autoload) are explicitly non-undoable so the history starts
  clean. Guarded by RackUndoTest.

## Closed by v1.22.0 (Snow Leopard)

- ~~UTF-8 charsets implicit at 12 I/O sites (session state, CI export,
  package.json parsing)~~ — explicit everywhere.
- ~~Wizard disk I/O + PNG encoding on the EDT~~ — RequestProcessor +
  EDT hop for the report.
- ~~API Studio autosave failed silently (chronic failure = silent data
  loss)~~ — warns once per failure streak + logs.
- ~~Status-line and session-snapshot timers never stopped~~ —
  lifecycle-bound.
- ~~Command relaunch skewed a running command's elapsed readout~~ —
  per-launch capture.
- ~~PING held 50 uncapped response bodies (~3.5MB worst case)~~ —
  capped at record time.
- ~~FlightRecorder journal read+parsed synchronously at boot~~ —
  deferred off the startup path.
- ~~JOptionPane in 12 rack files (26 sites) bypassing platform theming;
  raw ex.getMessage() shown to users~~ — DialogDisplayer everywhere
  with task-oriented messages.
- ~~ProcessSupport/ToolLocator lived in rack; editor depended on rack
  for process launching; tools used raw ProcessBuilder (no PATH
  augmentation — npm "missing" when launched from Finder)~~ — promoted
  to core.process, adopted in tools; the Windows-breaking hardcoded
  /dev/null in ProjectTemplates died with it.
- ~~Four HttpClient pools; looksJson/pretty duplicated three times~~ —
  core.http.HttpClientFactory + core.util.JsonUtil.
- ~~core/ui/tools/project effectively untested; apiclient engine
  half-tested~~ — backfill + floors (see module poms).
- ~~CLAUDE.md four releases stale; ~30 v0.x aspirational docs posing as
  current; three test scripts checking classes that never existed~~ —
  truth pass: banners, updates, deletions.
