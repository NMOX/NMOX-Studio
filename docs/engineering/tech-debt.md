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

## Open — deferred deliberately, with reasons (added v1.39.0)

The v1.39.0 idiom review put five senior-RCP lenses on the codebase (Lookup/
services/actions, window system, FileSystems/DataObjects, threading/platform
utilities, module wiring). Twelve cheap-and-clearly-right fixes shipped; what
follows is what the review *deliberately did not fix*.

### 29. The rack IS the context system — not OpenProjects/actionsGlobalContext
The platform models "the current project" as `OpenProjects` plus selection via
`Utilities.actionsGlobalContext()`; NMOX models it as ONE globally aimed rack
(`RackService.getRack().getProjectDir()`), read directly by every module.
**Worked as its own release in v1.45.0** — the core of the migration shipped:
a real aim now publishes to `OpenProjects` (the bridge in RackService:
findProject → open + setMainProject on a background lane, with a re-entrancy
flag so WebProjectOpenedHook's echo terminates on OUR guard, passive aims —
fresh-boot ~/NMOX, persisted window state, the follower — provably never
resolving platform projects, and a never-close law source-gated); the three
aim-owning windows (Task Rack, Project Studio, Workbench) publish the aimed
directory's DataFolder node via `setActivatedNodes` (AimNodePublisher:
off-EDT resolve, EDT delivery, equality-guarded, componentShowing-gated so
hidden boot tabs resolve nothing); and the git chip's Show Changes / Diff /
Annotate returned as `createContextAwareInstance` invocations against that
same node, with an honest Team-menu fallback. **Still open, deliberately**:
context-sensitive action registrations (PWA Kit, Standards Kit, Classic Kit
still always-enabled and scolding at runtime) and per-TC lookups for the
seven studio/tool windows that don't own the aim (NpmExplorer still reads
the registry by hand) — each is now an incremental follow-on with the
pattern established, not a coordinated big-bang.

### 30. `catch (LinkageError)` as the soft-dependency mechanism (~40 sites)
project/infra/apiclient/web3/dbstudio/ui treat rack as optional by importing
its concrete classes and catching `RuntimeException | LinkageError` around
every call. It works and it is defensive — but the idiomatic shape is an
interface published via `@ServiceProvider`, looked up with
`Lookup.getDefault().lookup(...)`, branching on null. Catching classloader
failures for control flow hides real breakage (a genuinely mismatched rack
surfaces as a silently skipped feature). **Deferred**: converting means
designing a rack facade interface and touching six modules; batch it with #29
— they are the same surgery. Related hardening: suite-internal deps are
spec-version only, so a mismatched rack *loads* and fails at call time;
implementation-version dependencies would make the module loader refuse it
up front (batch with ledger #20's version scheme).

### 31. rack's six public packages have no OpenIDE-Module-Friends
`model, devices, service, engine, projectstudio, docker` are world-exported;
every actual consumer is a first-party sibling. Today that's harmless. The day
an external plugin SDK ships, those packages become a public API we must
version-support forever — and `rack.model` has zero external production
importers at all (one test), so it may not need exporting once transitive
signatures are checked. **Deferred with a tripwire**: MUST be narrowed to a
friends list (and model possibly unexported) before any plugin story ships.

### 32. DiagnosticsBus duplicates the platform's editor-hints/task-list plumbing
Rack tools push diagnostics over a bespoke bus and editor draws its own
squiggles (`RackSquiggler`); the platform's `HintsController`/TaskList would
put the same findings in the standard Action Items window with standard
navigation. **Deferred**: the bus works, is storm-law-tested, and the payoff
is integration polish, not correctness. Candidate for an editor-intelligence
sprint (pairs with the JS/TS lexer item #5).

### 33. All seven studios live in the `editor` mode
Documents opened later interleave with seven permanently-open tool tabs in one
tab well; idiomatic RCP reserves `editor` for documents and docks tool windows
in their own modes. A custom `studios` wsmode (plus a TopComponentGroup for
the Docker/DB/Contract runtime cluster, and a look at whether three default-
open explorer-side trees are two too many) is the direction. **Deferred**: the
suite-tabs-first layout IS the discovery design (v1.29.0), and moving modes
churns every user's persisted layout — do it deliberately, with migration,
or not at all.

### 34. ProgressHandle gaps — MOSTLY CLOSED (v1.44.0)
DB Studio connect and infra cloud sync run under finally-guarded
ProgressHandles (per-provider ticks on sync); no cancel wiring —
neither op has an interrupt seam (DB cancel aborts statements, not
connects; commented in code). The debounce half closed with #16.
Remaining sliver: web3 artifact scan (a fast Files.walk) is still
status-text-only — lowest value, deferred.

### 35. No @OnStop seam — all shutdown work rides JVM hooks
Blessed for what we use it for: process reaping must survive System.exit and
SIGTERM, which skip @OnStop, so hooks strictly dominate there. But any FUTURE
teardown that needs platform APIs still alive (flushing through NetBeans IO,
keyring handles) has no home today. **Noted so the first such need adds a
ModuleInstall/@OnStop rather than misusing a hook.**

### 36. FileTreePanel remains a raw JTree over java.io.File
A Nodes/BeanTreeView/ExplorerManager tree would give file-type icons, the
platform file actions, and editor-synced CRUD for free. The v1.39.0 review
took the correctness half (CRUD now routes through DataObject so open editors
follow deletes/renames; hyperlink paths normalized); the UI half stays custom.
Also here: kit wizards still raw-write possibly-open `index.html` (bounded —
wire-in flows on files rarely open at that moment), and three mtime pollers
(FileWatcher/ArtifactPulse/WorkspaceFilePulse) share a shape a `StampPoller`
seam could unify. **Deferred**: the tree is careful (off-EDT, lazy, TCC-safe)
and the rewrite is real work with visual-regression risk.

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

## Open — deferred deliberately, with reasons (added v1.43.0)

### 39. Browser debugging: a page's Web Workers sit paused, not undebugged
Recon-proven (v1.43.0 transcripts): for `pwa-chrome` the page target's
`startDebugging` arrives on the parent link and `DapProxy` splices it —
but Web Worker targets arrive as further `startDebugging` reverse
requests on the CHILD link, and a worker whose request is answered but
never attached **never starts running** (verified live: zero worker
messages in 8s, whether the proxy answers success or failure — there is
no browser-side equivalent of `autoAttachChildProcesses: false` to
suppress the target). Node children at least run undebugged (item 25);
browser workers stall. Consequence: a page whose core logic lives in a
worker will appear hung under "Debug in Chrome (breakpoints)". Same root
cause as item 25 — the platform's single-session DAP client — and the
same fix: the N-session client/multiplexer sprint. Recorded so the first
"my page hangs in the debugger" report finds its reason.

### 40. On Windows, only the product's Stop reaps the browser — not disconnect
The v1.43.0 recon pinned that a DAP `disconnect` alone reaps the whole
browser via js-debug's `cleanUp: wholeBrowser` default (zero Chrome procs
3s after disconnect). That holds on macOS and Linux; the Windows CI lane
proved it does NOT hold there. js-debug renames the launched browser
process, which snaps the parent-PID chain its forceful cleanup — and our
own `descendants()` walk — relies on (the same MSYS/rename genealogy break
recorded in item 38), so Chrome's detached tree outlives `disconnect`.
This is **not** a product bug: `BrowserDebugAction`'s session cleanup runs
`JsDebugServer.stop() -> ProcessSupport.killTreeAndWait` on every teardown
path, so Stop leaves zero orphans on Windows too. The debt is that the
platform reaper, not js-debug, is load-bearing on Windows — a real fix for
the underlying rename-breaks-the-tree problem needs Job Objects (outside
pure Java), tracked jointly with item 38. `RealChromeIntegrationTest` pins
the honest split: mac/Linux prove disconnect-alone; every OS proves Stop.

## Open — deferred deliberately, with reasons (added v1.42.0)

### 37. Windows runs the tests, not the assembled-app probes
The boot smoke test and rendering probe run on Linux (xvfb) and macOS
only. Windows would need the .exe launcher path in boot-smoke-test.sh
(it drives bin/nmoxstudio, a POSIX script) and an answer for the
runner's non-interactive desktop. The windows-installer-check workflow
already byte-verifies the installer; the missing piece is booting the
assembled cluster. **Deferred**: the test suite is the payload of the
Windows lane; the launcher work is its own small sprint.

### 38. killTree cannot see through Git Bash's process genealogy
On Windows, MSYS breaks the parent-PID chain at exec, so a grandchild
spawned via Git Bash sh is invisible to ProcessHandle.descendants() —
the same reason taskkill /T fails on such trees. Proven on the runner
(the one Windows test disable, evidence in the test's comment).
runBounded still returns bounded (worst case the drain tail waits
2×5s). The real fix is Windows Job Objects via JNA/FFM. **Deferred**:
matters only if rack/SOLDER-style Git-Bash commands run under
runBounded timeouts on Windows; no such path ships today. Documented
in killTree's javadoc so nobody trusts the sweep there.

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
session hangs." Browser/Chrome debugging shipped in v1.43.0 on the same
one-child splice; it hits the same ceiling, with the harsher worker
consequence recorded as item 39.

## Open — deferred deliberately, with reasons (added v1.36.0)

The v1.36.0 senior review fixed everything cheap-and-clearly-right its
six audit lenses confirmed (see CHANGELOG). What follows is what the
audit found and the sprint *deliberately did not fix*, each with the
reason it can wait.

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

## Closed by v1.44.0 (the debt sweep)

### 15. panic() blocked the EDT on Stop All and the switch guard — CLOSED
Interactive stops now run on `RequestProcessor("Rack Stop", 4)` via
`Rack.stopAllAsync`/`stopAsync`; completion marshals to the EDT, the
switch swap proceeds only in the callback (dialog unchanged), Stop All
disables while a pass is in flight, and the status line says "Stopping
N tools…". The old path held the EDT 1,513ms per SIGTERM-proof device —
measured by the regression test, which fails on the synchronous code.
The shutdown reaper's panics stay synchronous, source-gate-pinned.

### 16. Studio workspace saves ran on the EDT debounce — CLOSED
The one careful pass: all four studios snapshot workspace JSON on the
EDT and write on a dedicated single-throughput SaveLane; write+stamp
are one lane task and every foreign-vs-own verdict rides the same lane
behind pending writes, so a poll can never see a write without its
stamp; componentClosed and every workspace read drain the lane
(bounded ms). No existing module RP was safe (throughput >1, or shared
with multi-second cloud ops) — hence the named lanes. DbWorkspaceIO,
the last non-atomic workspace writer, went AtomicFiles in passing.
(The ledger text was imprecise: dbstudio/web3 had no debounce timers —
they saved synchronously per action; same treatment.) 4× SaveLaneTest
storm/EDT/close-flush tests + 4× wiring source gates.

### 17. RackPanel / RackTopComponent constructor-wired listeners — CLOSED
Attach moved to addNotify/componentOpened with a rebuild/re-label
re-sync, detach to removeNotify/componentClosed (the v1.35 symmetry
idiom); lifecycle tests in the Workbench shape prove open/close/reopen
keeps exactly one registration and a preset loaded into a closed
window renders on reopen. Mutation-proven against the ctor wiring.

### 22. netbeans.default_userdir_root boot warning — CLOSED
Launcher-free fix: an @OnStart setter (UpdateCheck) derives the value
from Places.getUserDirectory().getParent() before any autoupdate
consumer touches it. Both conf attempts word-split on "Application
Support"; code sidesteps every launcher parser on all three OSes.

### 26. Vendored js-debug invisible to the SBOM — CLOSED
The release workflow appends a hand-written CycloneDX component (purl,
MIT, the NOTICE's sha256) to the aggregate BOM. Still invisible to
Dependabot — bumps stay manual per NOTICE — but the SBOM no longer has
a blind spot. Bump the workflow constant and the NOTICE together.

### 28. Window-menu items showed no accelerator — CLOSED
Every advertised chord now has a Keymaps/NetBeans shadow invoking the
same action as its Shortcuts registration, so Window-menu (and Open
Folder's File-menu) items show their accelerators. WindowShortcutsTest
pins each shadow's chord AND target instance against the source
registration — the two mechanisms can no longer drift (the v1.38.1
failure class, now structurally impossible for our windows).

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
