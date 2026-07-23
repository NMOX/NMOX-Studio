# Changelog

All notable changes to NMOX Studio are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [1.124.0] - 2026-07-23

### Ledger 56 closed — one capped HTTP-body read for the whole product

- New `core.http.HttpBodies`: `read`/`readUtf8` return
  `Capped(text, byteLength, truncated)` — read at most the cap, probe one
  byte for the truncation bit, decode. A gigabyte body costs the cap, not
  the gigabyte (proven with a counting stream; cap mutation fatal).
- All seven former `ofString` sites migrated: API Studio's ApiClient,
  Contract Studio's JsonRpcClient, DB Studio's CouchBackend, the rack's
  OracleClient and HTTP console, the infra DigitalOceanClient, and the
  boot UpdateCheck. Truncation POLICY stays at each site — flag (API
  Studio), refuse (JSON-RPC/CouchDB), shrug (display-only) — because
  that is where the seven genuinely differ.
- The v1.104.0 per-module source gates now pin routing through HttpBodies,
  and a cross-module gate in core fails the build if any site re-inlines
  the mechanics or reverts to `ofString`.
- **The deferred ledger now holds no actionable open items** — 51 and 45
  remain deferred with standing reasons (additive-when-needed /
  waits-on-platform). This time the claim is checked against the ledger's
  section headers, not asserted from memory.

## [1.123.0] - 2026-07-23

### Ledger 55 closed — the editor debug/format remainder, all six items

A docs-truth correction first: v1.122.0's notes claimed the deferred
ledger was empty. It wasn't — items 55 (this release) and 56 (the
capped-HTTP-read helper unification) were still open; 51 and 45 remain
deferred with standing reasons (additive-when-needed / waits-on-platform).
The claim is corrected in this release's docs.

- **M1** — the DapProxy loopback socket pair leaked once per debug
  session (production never calls `close()`; `endSession` only
  half-closes). The pair is now reaped when the client pump hits clean
  EOF — the client has closed its socket, so nothing unread can be
  discarded and the v1.37.0 half-close law still holds. Mutation-proven.
- **M2** — Prettier's timeout used `destroyForcibly()` (a node wrapper's
  grandchild survives); now `killTreeAndWait`. The stdout drain is a
  daemon and bounded: it reads a capped prefix (8 MB) then discards to
  EOF, and output past the cap is REFUSED outright — a truncated format
  result written into the document would destroy the file's tail.
  Cap refusal mutation-proven.
- **L3** — a malformed `startDebugging` reverse request was acked
  success before its configuration parsed, leaving js-debug believing a
  child launched that never would. Parse-before-ack: malformed = honest
  failure response, no child dialed. Mutation-proven.
- **L1** — the Go-debug free-port probe bound `new ServerSocket(0)` to
  every interface; now loopback-only like JsDebugServer's.
- **L4** — the completion identifier harvest re-lexed the WHOLE document
  on every query (O(file) per keystroke); now a 200k-char window around
  the caret — ordinary files see no change, 1 MB files stop paying.
- **L5** — IDE force-quit leaked the throwaway Chrome debug profile dir;
  live profiles now ride a shutdown-hook live-set (the JsDebugServer
  reaper idiom), best-effort by design.
- editor 442 green (+8: 2 DapProxy behavior, 2 exec behavior, 4 gates).

## [1.122.0] - 2026-07-23

### CouchDB speaks TLS — the deferred ledger is empty

- `ConnectionSpec` gains a `secure` flag: the connection dialog offers
  "Use TLS (https)" for CouchDB, and `CouchBackend.baseUrl()` picks the
  scheme from it — credentials stop traversing cleartext when the server
  supports TLS (port 6984 by convention).
- Compatibility by construction: a delegating 8-arg constructor keeps every
  existing call site compiling, and a pre-v1.122.0 `.nmoxdb.json` without
  the key loads as `secure=false` — the exact behavior those workspaces
  always had. No migration. Round-trip, old-file, and scheme cases
  test-pinned; dbstudio 383 green.
- **With this, ledger 54 is fully closed — and the deferred ledger is
  empty**: 53 (infra) and 54 (dbstudio) were the last open items.

## [1.121.0] - 2026-07-23

### Infra Designer: the canvas locks during live cloud operations (ledger 53 closed)

- While a deploy / sync / refresh / destroy runs, the canvas now refuses
  structural edits — node delete, wire connect, palette drop — under an
  unmistakable red banner ("CLOUD OPERATION RUNNING — canvas locked"), every
  cloud-op button is disabled, and a rack re-aim defers until the operation
  finishes instead of loading another project's graph mid-plan. Deleting a
  node mid-deploy could orphan a created-and-billed resource with no id
  recorded; that whole class is now structurally impossible.
- Property/label edits stay live during an op — they aren't structural and
  can't orphan anything (documented scoping).
- `runExclusive` was already the single choke point every cloud op rode
  (throughput-1 RP, trigger disabled); the lock arms there and releases in
  its marshalled finally, so no op path can bypass it. Source-gated; infra
  211 green. **Ledger 53 is fully closed** — (a) v1.98.0, (c)/(d)/(e)
  v1.120.0, (b) here.

## [1.120.0] - 2026-07-23

### Infra Designer: re-aim edit loss, worker-thread CME, and impostor 404s (ledger 53 c/d/e)

- **(c) — the debounced save binds to its file**: `save()` wrote to the LIVE
  aim's path, so a debounce window still holding the old project's edits
  after a re-aim would have written the old graph into the NEW project's
  file, and the plain re-aim dropped the last second of edits. The design
  file now binds at load (`boundDesignFile`, the apiclient v1.35.1 idiom)
  and `onProjectReaimed` stops the debounce and force-saves to the OLD file
  before loading the new. Source-gated.
- **(d) — cloud workers stop mutating the EDT-confined graph**: deploy id/ip
  recording, drift's ip refresh and doId-clear, import placement (the
  `props.putAll` that raced `GraphIO.toJson`'s autosave iteration into a
  ConcurrentModificationException), and destroy's doId-clear all cross to
  the EDT via `onModel` (invokeAndWait, so the deploy sequence still sees
  each id before the next step's placeholders resolve).
- **(e) — only a real 404 severs the deploy linkage**: drift matched
  `"404"` anywhere in the error text; a proxy error page, a resource named
  `web-404`, or a retry-after value would sever `doId` and orphan the
  linkage. `deletedInCloud` now matches the `HTTP 404:` status prefix that
  `send` reports. Test-pinned with impostor cases.
- infra 210 green. Ledger 53 is down to (b) alone — the op-in-flight state
  machine (block structural edits + defer re-aim during a live cloud op).

## [1.119.0] - 2026-07-23

### DB Studio reload leaves the paint thread (ledger 54 M5 — the deferred MED)

- `reloadWorkspace()` read `.nmoxdb.json` (plus its save-lane drain and the
  own-write stamp's file stat) and scanned the project `.env` synchronously
  on the EDT, from `componentOpened` and every re-aim — a slow or networked
  filesystem stalled the paint thread. All of it now rides RP (the web3
  v1.100.0 idiom): drain → read → stamp off-EDT, then the parsed workspace
  marshals back under a newest-wins `reloadSeq` so an overlapping
  re-aim/external-edit burst applies only the newest read; state teardown
  waits for the read, so the tab never shows an empty in-between.
- `offerEnvConnection` keeps its once-per-project guard on the EDT and moves
  the `.env` stat + read + parse to RP, marshalling the suggestion back for
  the already-configured check and the balloon.
- `ReloadOffEdtGateTest` pins the structure (I/O inside `RP.post`,
  seq-guarded apply) — mutation-proven; dbstudio 380 green. Ledger 54 is now
  down to L2 alone (Couch TLS opt-in, a connection-spec schema change).

## [1.118.0] - 2026-07-22

### The Workbench detection lane widens — one hung mount no longer starves all rows

- The toolchain-detection `RequestProcessor` goes from one lane to four
  (ledger 61). A project directory on a hung network mount blocks its
  `File.list` walk in uninterruptible kernel I/O; on a single lane that
  starved every other row's detection for the session (chips stuck at
  "detecting…"). Four lanes bound the damage to itself — it takes four
  simultaneously wedged mounts to stall detection. Concurrent completion is
  safe: each result targets its own chip/row and applies only if that
  component is still in the tree. Test-pinned (a wedged lane no longer blocks
  a fast detection — the test hangs on the old single-thread RP).

## [1.117.0] - 2026-07-22

### DB Studio connection hardening — ledger 54 L3 + L5

- **L3 — refuse LOAD DATA LOCAL INFILE**: MySQL/MariaDB connects now set
  `allowLoadLocalInfile=false` + `allowLocalInfile=false`, so a
  malicious/compromised server can't answer a benign query with a
  local-infile request that reads a file off THIS client's disk. PostgreSQL
  (not vulnerable to the MySQL-ism) is left untouched.
- **L5 — zero the password clone**: `DbClient.close()` wipes its in-memory
  password copy with `Arrays.fill`, shrinking the window a heap dump can
  recover the secret. close() is disposal (the backend is discarded from the
  per-spec map), so no reopen re-reads it; the caller's own array is never
  touched. Both test-pinned. Ledger 54's M5 (off-EDT reload) + L2 (Couch TLS)
  remain — they touch the connection-spec schema.

## [1.116.0] - 2026-07-22

### DB Studio: LOB cells can't OOM the grid, and Mongo peeks are valid JSON

- **M4 — per-cell LOB cap**: a result grid materialized every cell via
  `getString`, so a single multi-hundred-MB BLOB/CLOB/`bytea` could OOM the
  IDE despite the row cap. New `JdbcCore.cell` caps text at 64k chars
  (CLOB/NCLOB read only a capped prefix via `getSubString`), renders binary
  as `[N bytes]` without ever stringifying it, and marks oversize text
  honestly — live-SQLite test-pinned.
- **L4 — JSON-safe Mongo peeks**: the auto-run `find` console text inlined
  the collection name by string concatenation; a name with `"`/`\` produced
  malformed JSON. It now builds via `JSONObject.quote` (parse-round-trip
  pinned).
- From the dbstudio review's deferred remainder (ledger 54); the M5 off-EDT
  reload and L2/L3/L5 (Couch TLS, `allowLocalInfile`, password zeroing) stay
  open as they touch the connection-spec schema.

## [1.115.0] - 2026-07-22

### The tools EDT polish — ledger 62 closed same-day

- Wizard Finish scaffolds off the EDT: `WebProjectWizardIterator` is now an
  `AsynchronousInstantiatingIterator`, so the platform runs `instantiate()`'s
  file writes on a background thread with the wizard's own progress UI.
- `WebProject.Info.getDisplayName` caches the parsed package.json name keyed
  by mtime — a Projects-window paint costs one stat, not a read+parse; a
  half-saved (malformed) package.json falls back to the directory name
  instead of breaking the label.
- `NpmService.runCommand(File,String)` dispatches on its RP: the lockfile
  probes behind package-manager detection leave the EDT (the trust prompt
  marshals its own dialog, so the hop is safe).

## [1.114.0] - 2026-07-22

### The tools review lands — enablement off the directory walk, script runs off the RP lane

- **HIGH — menu enablement no longer walks the project directory on the EDT**:
  `isActionEnabled` (called at every menu/toolbar/selection refresh) resolved
  the project kind via an uncached `ProjectInspector.detectKind` — dozens of
  `listFiles` passes per call, a UI stall on network mounts. A 3s-TTL
  `KindCache` turns a menu-paint storm into one scan per window (a project's
  toolchain doesn't change between two paints); cache behavior test-pinned.
- **MED — NPM Explorer script runs can no longer pin the service lane**:
  `NpmService.runCommand` drained stdout to EOF *before* its 60s `waitFor` —
  the EnvironmentDoctor bug class — so one double-clicked `npm run dev`
  pinned a thread of the throughput-3 RP forever, and three wedged the lane
  for the whole session. Runs now route through `CommandExecutor` (named
  daemon pumps, future completes from onExit, kill/orphan guarantee at
  shutdown); the trust gate stays ahead of the spawn (source-gate updated),
  the returned accumulator stays capped at 4 MB.
- **MED — the visible NPM Explorer coalesces re-aim storms**: `projectChanged`
  refreshed per event while the tab was showing (the storm guard only covered
  the hidden case) — N events meant N npm spawns + tree rebuilds. A 300ms
  trailing-edge timer collapses a storm to one refresh, the v1.33.2
  RefreshCoalescer law applied to the visible case.
- From the first full dedicated tools review, which also verified all five
  prior fix families HOLD (trust gates + 4MB cap, zero-boot-spawns,
  node-publication self-echo guard, package-manager delegation, named RP);
  its three LOWs (wizard EDT scaffolding, getDisplayName read,
  install-path probes) → ledger 62.

## [1.113.0] - 2026-07-22

### The core LOW sweep — ledger 57, 58, 59 closed

- **57 — atomic rewrites keep an existing file's permissions**: the temp
  sibling is created 0600 and the `ATOMIC_MOVE` carried that onto the target,
  silently narrowing a previously shared workspace file; an existing target's
  POSIX permissions are now captured before the write and re-applied after
  the move (fresh files stay 0600 — tighter is the safe default; non-POSIX
  filesystems skip both steps).
- **58 — `Versions.compare` survives raw/suffixed input**: segments now
  compare by their leading digits (`"1.24.0-rc1"` vs `"1.24.0"` no longer
  throws `NumberFormatException`; suffixes carry no ordering by documented
  law), and absurd digit runs clamp instead of overflowing. All normalized
  comparisons — every existing caller — behave identically.
- **59 — the git first-line read is bounded**: `GitFacts.readFirstLine`
  reads a 4 KB prefix instead of slurping the file (the class already treats
  a crafted `.git` FILE as adversarial — the gitdir confinement); a
  cap-filling line with no newline degrades to null rather than showing a
  truncated branch name on the chip — hiding beats lying, test-pinned.
- CoreLowSweepTest (7) + the pre-existing GitFacts/Versions/AtomicFiles
  suites all green — no behavior change on any honest input.

## [1.112.0] - 2026-07-22

### The last unbounded read is bounded (ledger 60 closed)

- **`CommandExecutor.pumpStream` read lines with no per-line cap**: the pump
  streams (each line dispatches and drops — no accumulator), but
  `BufferedReader.readLine()` itself buffered one logical line unbounded, so
  a pathological child emitting gigabytes with no line terminator grew a
  single String until OOM. New `readLineBounded` keeps `readLine`'s exact
  terminator handling (`\n`, `\r`, `\r\n`) but truncates past 200k chars with
  an honest ` …[line truncated]` marker, draining the remainder of the
  physical line so the child keeps writing into a moving pipe (no deadlock)
  while the IDE's memory stays capped (~400 KB worst case per pump).
- Terminator parity, flood truncation, post-flood continuation, and the
  `\r\n`-at-the-truncation-boundary case all test-pinned
  (`BoundedLineReadTest`, 6 tests); the whole rack engine package re-run
  green. With this, zero unbounded reads remain in the product — HTTP
  (v1.104.0's ofString sweep), process capture (v1.106.0's runBounded cap),
  and now the pump's line lane are all ceilinged.

## [1.111.0] - 2026-07-22

### The recent-files trail leaves the paint thread (first dedicated project-module review)

- **`RecentFiles.record()` ran disk I/O on the EDT on every tab switch**: the
  window-registry tracker fires on `PROP_ACTIVATED` (the EDT), and record did
  a file stat, a pref rewrite, and a synchronous `prefs.flush()` — a
  backing-store disk write — right there, on the hot path of ordinary
  editing. All of it now rides a dedicated single-thread
  `RequestProcessor("nmox-recent-files")` lane (FIFO keeps trail order true);
  the EDT pays only for posting.
- **Workbench refresh no longer stats the trail on the EDT**: rendering used
  `RecentFiles.list()`, which stat()ed up to 20 paths — any one on a hung
  network mount could freeze a refresh for seconds. New `listRaw()` renders
  from a pure pref parse (zero filesystem I/O); new `pruneAsync()` sweeps
  vanished files off-EDT and re-requests one coalesced refresh only when
  something actually dropped, so prune → refresh → prune converges instead of
  storming — test-pinned (`cleanPruneDoesNotNotify`).
- The review verified the module's prior laws all HOLD (v1.33.1 off-EDT
  detection, v1.33.2 RefreshCoalescer, v1.33.3 no-repost, listener symmetry)
  and the rest CLEAN; its one LOW (a hung mount can wedge the single-thread
  detection lane — degraded chips, no UI hang) → ledger 61.

## [1.110.0] - 2026-07-22

### The lexer-recursion guard — a live-caught session-poisoning crash, killed as a class

- **The bug, found live in the running app** (a red "Unexpected Exception —
  Could not initialize class org.openide.util.Exceptions$OwnLevel" over a Nim
  learning space): every CSL language config answered `getLexerLanguage()`
  with `Language.find(<its own mime>)`. When the CSL provider is consulted
  before the TextMate provider has claimed that mime (a document-load ordering
  race), `Language.find` falls back into the CSL editor kit, which asks the
  config again — `getLexerLanguage → find → kit → getLexerLanguage → …` — and
  the stack blows. Worse than losing one file's lexer: the
  `StackOverflowError` struck inside the static initializer of the platform's
  `Exceptions$OwnLevel` logging Level, poisoning it for the whole session, so
  every later benign notification surfaced as that scary red error.
- **The fix, applied as a class**: new `Lexers.find(mime)` — a ThreadLocal
  cycle guard that returns `null` on a same-thread re-entry for the same mime
  instead of recursing (the CSL kit handles a null lexer gracefully; TextMate
  highlighting is a separate layer and unaffected). All **50** language
  configs now ride it; bare `Language.find` in a config fails the build
  (`LexerIdiomGateTest`).
- **Mutation-proven**: neutering the guard reproduces the exact production
  `StackOverflowError` in `LexersTest`; restoring it passes. The guard clears
  its in-flight set per call, so later resolutions retry for real.

## [1.109.0] - 2026-07-20

### The docs screenshot forge, illustrated tutorials, and in-app install truth

- **DocsShots — screenshots as a product capability.** Boot the app with
  `-J-Dnmox.shots.dir=<dir>` and it cycles each suite tab, paints the main
  window into a crisp 2x PNG per tab (pure Swing painting — no OS
  screen-recording permission, no compositor), and exits.
  `scripts/docs-shots.sh` wraps it: rebuild from current sources (a stale
  assembly silently captures yesterday's UI), throwaway userdir + cachedir
  for a deterministic first-launch state, update checks suppressed so no
  balloon photobombs a shot. The forge owns `docs/images/tabs/`; the
  hand-staged shots in `docs/images/` are never touched. Zero boot cost
  without the property (one getProperty), shot-to-tutorial mapping
  test-pinned and mutation-proven.
- **All 13 tutorials illustrated (11 with images, 2 honestly pending).**
  Eight slots use the curated staged library (a hit breakpoint with live
  V8 variables, ANVIL live on chain 31337, a running postgres container,
  a security-header grade, ORACLE's diagnosis, the rack front and rear);
  three use fresh forge shots (Workbench, Project Studio, Block Studio).
- **Learning-space tutorials install in-app, not in a terminal.** The
  generated Install section told the user to run `brew install <tool>`
  themselves; NMOX Studio already runs installs for you — the REPL's
  INSTALL button (streamed onto the screen) for REPL spaces, SOLDER for
  run spaces. The section now points at the right in-app affordance per
  driver kind and shows the command only for transparency ("No terminal
  needed"). Found in a live review of the Gleam space.

## [1.108.0] - 2026-07-20

### Rack-panel mouse handlers are owner-scoped, and Load/Export don't block the EDT

The first dedicated **rack UI-layer review** (RackTopComponent, RackPanel,
the cable-patch gesture, palette, controls). It verified the v1.95.2
armed-gesture-survives-rebuild class holds, the paint path is CME-safe by
construction (defensive-copy snapshots, EDT-marshaled listeners), and every
timer/listener in the controls and the panel is attach/detach-symmetric —
and found two MED findings, both fixed with source gates.

- **RackPanel installed its device mouse handlers by type, not identity.**
  The devices are shared and outlive the panel (closing the window keeps the
  rack alive), so `installInteraction`'s `l instanceof DeviceMouse` guard
  matched a *different* panel's handler — a second `RackPanel` over the same
  rack would skip its own wiring (dead selection/reorder/patching) and route
  events into the stale panel, which every device would pin in memory
  (leak). The guard is now identity-aware (`dm.owner() == this`), and
  `removeNotify` detaches this panel's handlers from every device — symmetric
  with install.

- **Load Patch and Export CI did blocking disk I/O on the EDT.** The Save
  button was moved onto `SAVE_RP` (snapshot on the EDT, write on the lane),
  but its two siblings weren't: Load read + parsed the patch file, and
  Export CI ran `createDirectories` + `writeString`, synchronously on the
  paint thread. Both now ride `SAVE_RP`. Load needed a read/apply split —
  the new `RackIO.readDocument` reads + parses (and `.bak`s a corrupt file,
  the v1.107.0 guarantee) off the EDT, and `fromJson` mutates the device
  components back on the EDT.

The review's CLEAN list is broad: `CablePatchGesture` (the sticky armed patch
still can't connect a disposed device across a rebuild or flip),
`RackTopComponent`'s own listener lifecycle, the palette, and every control
(`Led`/`VuMeter`/`LcdDisplay`/`Knob`/`RackButton`/`ToggleSwitch`) — timers
self-stop and stop on `removeNotify`, off-thread callers marshal to the EDT,
and the LCD line buffer paints a synchronized snapshot (CME-safe).

## [1.107.0] - 2026-07-20

### The flight recorder writes off the hot path, and a corrupt patch is never clobbered

The first dedicated **rack-engine review** (the engine/persistence/service
core, not the 51 device faceplates) confirmed the headline worry CLEAN —
`CommandExecutor` does NOT share the `ProcessSupport.runBounded`
uncapped-accumulator bug, because it dispatches a dev server's output
line-by-line with no growing buffer — and surfaced two MED findings, both
fixed here with tests.

- **FlightRecorder did blocking disk I/O under the singleton monitor.**
  `record()` ran its per-event journal append (`Files.writeString`,
  APPEND) and its rotate (`readAllLines` + `write`, up to 1.5 MB) inside
  `synchronized (this)` — the same monitor that gates every bus publish
  (so every device's output-pump thread funnels through it) and every EDT
  reader (`timeline`/`export`/`statistics`). One slow or full disk could
  therefore serialize and stall the output pumps of *all* devices at once,
  and block a UI read behind a pump's write. The append + rotate now ride a
  dedicated single-thread FIFO `RequestProcessor` (`JOURNAL_RP`), off the
  monitor; only the fast in-memory `add()` stays under the lock. Single-
  threaded so the journal on disk keeps event order. A new
  `awaitJournalIdle()` barrier (the `awaitRouterIdle`/`awaitDeviceBgIdle`
  idiom) drains it for tests and clean shutdown; a source gate proves the
  append is posted, never inlined.

- **A corrupt `.nmoxrack.json` was clobbered without a backup.**
  `RackIO.load` parsed the JSON *before* `fromJson` cleared anything, so a
  hand-edit syntax error threw early — leaving the *previous* project's
  devices mounted against this project on a switch, and the untouched
  corrupt file in place for the next atomic Save Patch to silently
  overwrite. Now `load()` preserves the user's file as `<name>.bak` (the
  BlockStudio idiom) and resets the rack to a known-empty state before
  rethrowing, so no stale device survives and no hand-edit is lost.

The review verified `Rack`, `RackDevice`, `WorkspaceTrust`, `RackService`,
`FileWatcher`, `RackBus`, `Cable`, and `AtomicFiles` CLEAN. The one LOW
finding — `CommandExecutor.pumpStream`'s `readLine()` has no per-line cap
(a child emitting huge bytes with no newline could grow one String) — is
recorded as ledger 60; real dev servers always emit line terminators, so
likelihood is low.

## [1.106.0] - 2026-07-20

### The process-probe is bounded in time AND memory, and Discard defaults to No

The first dedicated **ui-module review** surfaced two MED findings, and
the follow-on **core-module review** surfaced the HIGH primitive bug the
first fix leaned on. All three are fixed here, each verified against the
code and mutation-proven.

- **EnvironmentDoctor's per-probe timeout was illusory.** `probe()` read
  the tool's first output line, then drained the rest to EOF via
  `transferTo(nullWriter())`, and *only then* called `waitFor(4s)`. A
  tool that launches, prints nothing, and holds its pipe open blocks at
  `transferTo` forever — the 4s leash was unreachable, hanging the whole
  sequential Doctor sweep and leaking the reader thread. `probe()` now
  delegates to `ProcessSupport.runBounded` (drains both streams on their
  own threads while `waitFor` runs first), taking the first non-blank
  line of stdout, then stderr for the `nginx -v` / `apachectl -v`
  holdouts. A wedged-pipe test (prints, then sleeps 30s) asserts the
  probe returns under 15s — the mutation proof.

- **`ProcessSupport.runBounded`'s output accumulator was uncapped
  (HIGH).** Both stream drains appended every byte into a `StringBuilder`
  with no ceiling — a runaway or hostile child that floods stdout (`yes`,
  a build stuck in a reload loop, a probe that hit a spewing endpoint)
  would grow the heap without limit until the timeout fired, and
  `OutOfMemoryError` takes down the entire IDE. This is the shared
  primitive every module's process launches route through, and the
  EnvironmentDoctor fix above now leans on it (the old Doctor discarded
  its tail; routing through `runBounded` would otherwise re-introduce the
  unboundedness on the memory axis). Now each capture is held at a 4 M
  char (~8 MB) ceiling while the drains keep reading to EOF — so a chatty
  child still can't deadlock on a full pipe — and `BoundedResult` gains a
  `truncated` flag so the dropped tail is reported, not hidden. A
  20 MB-flood test asserts the capture is held at the ceiling and flagged.

- **The "Discard experiment" confirmation defaulted to Yes.**
  `Experiments.discard(dir)` stops anything running there and deletes the
  tree — irreversible — yet the dialog let a reflexive Enter/Space land
  on the destructive Yes (`NotifyDescriptor.Confirmation` hard-codes
  `initialValue = OK_OPTION`). Switched to the full `NotifyDescriptor`
  constructor with `NO_OPTION` as initialValue (the v1.98.0 infra
  dialog-safety idiom); a new `ManageExperimentsSafetyTest` source-gates
  it, mirroring infra's `DialogSafetyTest`.

Three LOW core-review findings are recorded in the debt ledger (56–58):
`AtomicFiles` narrowing perms to 0600 on rewrite, `Versions.compare`
throwing on non-normalized public input, and `GitFacts.readFirstLine`
reading a whole (attacker-influenceable) `.git` file to get one line.
The core review verified `HttpClientFactory`, `JsonUtil`,
`SelfWriteTracker`, `ToolLocator`, `AtomicFiles`, `TerminalPhosphor`,
and the entire frozen Device SPI CLEAN.

## [1.105.0] - 2026-07-20

### Device background work is drainable in tests (DYNAMO knob flake fixed)

`ClassicWebDevicesTest.dynamoRunnerKnobSettlesBoth` flaked on loaded
macOS CI (twice this cycle), blocking otherwise-green releases. Root
cause: changing a device knob can fire a listener that schedules an
async reload via `RackDevice.offEdt` (DYNAMO's RUNNER knob reloads the
task list this way), and that work runs on the single-threaded
`DEVICE_BG` RequestProcessor — which the test's `settle` helper never
drained. `settle` flushed the EDT and the router but not the device
background lane, so the async reload raced the assertion.

- New `RackDevice.awaitDeviceBgIdle()` — a FIFO barrier on `DEVICE_BG`,
  the device-lane counterpart to `Rack.awaitRouterIdle()`. The test's
  `settle` now drains in dependency order: device background → EDT →
  router. Fixed for the whole class, not just this test — any device
  using `offEdt` is now deterministically drainable.
- No production behavior change: `offEdt` is unchanged; the reload is
  eventually-consistent and correct in the running IDE (the race was
  test-only, from an explicit reload running concurrently with the
  listener's). rack 1154 green, repeat-run-verified.

## [1.104.0] - 2026-07-20

### Every HTTP response read is bounded (the ofString sweep)

A repo-wide sweep for the unbounded-buffering bug already fixed in
apiclient (v1.99.0), web3 (v1.100.0), and dbstudio (v1.101.0) found the
last four `HttpResponse.BodyHandlers.ofString()` sites — each reads a
remote or user-pointed response into heap with no ceiling, so a
hostile, misconfigured, or redirected endpoint could OOM the IDE.
All four now stream through a capped reader (`ofInputStream` +
`readNBytes`):

- **rack `HttpDevice`** (the HTTP console) — the sharpest: it fires at
  a URL the user typed. Capped at 1 MB before the existing 64 KB
  history truncation (which previously ran only AFTER `ofString` had
  already buffered the whole body).
- **rack `OracleClient`** — the Anthropic Messages API response, 8 MB.
- **infra `DigitalOceanClient`** — cloud API responses behind a secret
  token, 8 MB.
- **ui `UpdateCheck`** — the boot-time GitHub release check, 2 MB.

Source-gated in each module (these clients use the shared HttpClient
with no injectable URL seam). With this, no `BodyHandlers.ofString()`
remains anywhere in the product's main sources. Ledger: unifying the
seven capped-read sites into one `core.http` helper is a future
cleanup.

## [1.103.0] - 2026-07-20

### The IDE won't run a cloned repo's code without asking (tools RCE gates)

The editor security review (v1.102.0) exposed a systemic class — the
IDE spawning a cloned repo's code with no Workspace Trust gate — and a
follow-up review of the **tools** module found two more instances,
both closed here:

- **Run/Build/Test/Clean (F6/F11) is trust-gated.**
  `WebProjectActionProvider.invokeAction` handed a project-controlled
  command — the `package.json` "scripts" body, `make`/`cargo` (which
  runs `build.rs`)/`gradle` build scripts, `npx`-resolved
  `node_modules/.bin` binaries — straight to `CommandExecutor.run`
  with no gate. Opening a hostile cloned repo and pressing Run
  executed its code. It now calls `WorkspaceTrust.requestTrust(dir)`
  (prompt-once, the debug-action idiom) before spawning.
- **The NPM Explorer's run-script is trust-gated.**
  `NpmService.runCommand` (reached by double-clicking a script in the
  NPM panel, and by `install()`/`runScript()`) spawned `npm run
  <script>` / `npm install` — arbitrary attacker script bodies and
  pre/postinstall hooks — ungated. Same gate now guards it; the
  fixed-tool paths (`npm ls -g`, `npm --version`) don't route through
  it and never prompt. Its output accumulator is also capped so a
  runaway build can't OOM.

Root cause pinned in a comment: `CommandExecutor.run` and
`ProcessSupport.builder` are deliberately un-gated primitives — trust
is the caller's responsibility, which the rack devices and debug
actions honor and these two paths skipped. Both gates mutation-proven.

Also fixed in passing: a hand-written `Bundle.properties` in the npm
package was missing the NPM Explorer's `@Messages` keys, so its
registered name/tooltip could throw `MissingResourceException` under an
unlucky classloader order (surfaced by the new test's fork ordering) —
the keys are now present in both bundles.

## [1.102.0] - 2026-07-20

### Editor security: LSP/Prettier trust-gated, DAP frame bounded (first dedicated review)

The first dedicated **editor** review found the two most serious
defects of the module-review arc — remote code execution — plus an
OOM. All three fixed:

- **RCE on file-open (LSP) is closed.** `LanguageServers.launchNpm`
  ran a project's committed `node_modules/.bin/<server>` — attacker-
  controlled code in a cloned repo — the moment you opened a `.ts`/
  `.js` file, with no Workspace Trust gate (the debug actions gate
  their spawns; the LSP layer never did). The project-local binary is
  now used only when the workspace is trusted (a SILENT
  `WorkspaceTrust.isTrusted` check — the LSP client calls this
  constantly, so it must never prompt); untrusted, it falls back to
  the user's own global tool on PATH.
- **RCE on save (Prettier) is closed.** Format-on-save ran
  `node_modules/.bin/prettier` on Ctrl+S with the same missing gate.
  Same fix: the project-local binary is used only when the workspace
  is trusted, else the user's global prettier.
- **The DAP frame read is bounded.** `DapFrames` read
  `readNBytes(contentLength)` with an uncapped `Content-Length` —
  payloads carry debuggee-controlled data, so a debugged program doing
  `console.log('x'.repeat(2e9))` made the adapter emit a multi-GB
  frame that OOM'd the IDE in one allocation. Frames over 64 MB are
  now refused before allocation (session ends), and a malformed
  `Content-Length` is a protocol error rather than a crash.

Each fix is mutation-proven; the review verified the debug spawns'
trust gate, the process-tree kills, the DAP half-close teardown, and
the JavaScript lexer's progress-on-malformed-input all CLEAN. Ledger
55 records the lower-severity remainder (per-session proxy socket
leak, Prettier timeout kill-tree, all-interface probe port, per-
completion re-lex).

## [1.101.0] - 2026-07-20

### DB Studio: safe quoting, bounded reads, honest Apply (first dedicated review)

The first dedicated **dbstudio** review found seven defects worth
shipping now (two HIGH, four MED, one LOW); the remaining lower-
severity items are recorded as ledger 54.

- **MySQL/MariaDB string values escape the backslash.** `UpdateBuilder`
  doubled only the single quote; on MySQL/MariaDB (backslash is a
  string escape by default) a cell ending in `\` — `C:\` — rendered
  `'C:\'`, the server read `\'` as an escaped quote, the literal never
  terminated and the trailing `WHERE …` was swallowed into the string:
  broken at best, a break-out at worst. Value escaping is now
  dialect-aware — MySQL/MariaDB double the backslash too, PostgreSQL
  and SQLite (backslash is literal) keep quote-doubling only.
  Mutation-proven across all three families.
- **The CouchDB read is bounded.** `CouchBackend` buffered the whole
  HTTP body via `ofString()`; a `_find` against a huge collection (or
  a hostile endpoint) could OOM the IDE before the grid ever applied a
  row cap. Bodies now stream through an 8 MB capped reader and refuse
  past the cap — the apiclient v1.99.0 fix, here. Proven against a
  real in-JVM server.
- **The two destructive dialogs default to the safe button.** The
  Apply-edits preview (which runs real UPDATEs) defaulted its Enter key
  to **Apply**, and Remove-connection (which deletes the connection and
  its keychain password) used the OK-defaulting `Confirmation`
  shortcut. Both now default to Cancel via the full constructor's
  initialValue (the v1.98.0 idiom) — a reflexive keypress can't run
  UPDATEs or delete a saved password.
- **EXPLAIN can no longer execute a trailing write.** The button
  prefixed `EXPLAIN` to the whole console text and re-split it, so
  `SELECT …; DELETE …` explained the SELECT and **ran the DELETE**
  under a button the user believes is read-only. EXPLAIN now refuses
  multi-statement text.
- **Apply treats a 0-row UPDATE as a failure.** A PK-scoped UPDATE that
  matched nothing (the row changed under the grid) was counted as
  "applied" — a silent lost edit reported as success. A 0-row match
  now stops with "0 rows matched — re-run the query."
- **CSV export neutralizes formula injection.** A cell beginning
  `= + - @` (attacker-controllable from a shared DB) executed as a
  formula when the CSV opened in Excel/Sheets; such cells now get a
  leading apostrophe.

The review's clean list held: passwords Keyring-only and never on
disk, identifier quoting injection-safe, the Apply preview shows
exactly what runs, atomic save-lane writes, correct Statement/
ResultSet/Connection lifecycle (the NB-bridge never closes NetBeans'
own connection), and row caps on every grid.

## [1.100.0] - 2026-07-20

### Contract Studio: bounded RPC, clamped watch, confirmed broadcasts

The first dedicated **web3** review, all six findings fixed in one
release:

- **RPC responses are bounded.** `JsonRpcClient`'s transport buffered
  the whole body via `ofString()` — and the Watch pane drives it every
  2 seconds against arbitrary user-added endpoints, so a hostile or
  misconfigured gateway could OOM the IDE. Bodies now stream through
  an 8 MB capped reader; oversize is a refusal with a redacted
  message, never a buffer. Proven against a real in-JVM server
  (mutation: `readAllBytes` fails by name). `URI.create` also moved
  inside the redacting try — its parse error echoes the full input
  URL, the one path that bypassed the v1.33.0 redaction seam.
- **The watch's log lane is clamped.** Blocks were capped to a
  50-block catch-up window from day one, but the `eth_getLogs` range
  was open-ended below: a failing fetch never advances the log cursor,
  so every retry widened the range — and the response — without
  bound, feeding the unbounded read above. The new pure `WatchCursor`
  core plans both lanes inside the same window (outage case
  mutation-proven), and a generation guard makes a re-armed watch
  revoke cursor ownership from any tick still blocked in an RPC —
  STOP→START during a slow poll can no longer tear the cursors.
- **SEND and Deploy confirm before broadcasting to a non-local
  chain.** A remote endpoint with unlocked accounts (a self-hosted
  geth `--dev`, a mainnet fork holding value-equivalent state) accepts
  a real, irreversible transaction on one stray click. Both now pass a
  safe-default confirmation (the v1.98.0 `NO_OPTION` idiom) unless the
  endpoint is provably loopback — the tutorial's ANVIL loop stays
  frictionless. Loopback classification unit-tested; the raw URL never
  leaves the client.
- **Workspace reloads read off the EDT.** `reloadWorkspace` ran
  `loadGuarded` (file read + corrupt-path `.bak` copy) on the paint
  thread on every re-aim and external-edit pulse; the read now rides
  RP with a newest-wins sequence, the apiclient idiom, and the EDT
  only applies the result.

The review's clean list, for the record: secret networks never
serialize URLs, keyring-only with wiped `char[]`s; atomic save-lane
writes with `.bak`-before-fallback; watch/pulse lifecycle symmetric;
feed ring-capped at 500; no signing code anywhere (the no-private-keys
boundary holds); Keccak/ABI/receipt cores sound.

## [1.99.0] - 2026-07-20

### API Studio: bounded, cancellable, honest sends (ledger 52 closed)

The remaining four findings from the first dedicated apiclient review,
all in the original v1.19.0 send surface:

- **The response body capture is bounded.** `ofString()` buffered an
  unbounded body, so a runaway endpoint (a misconfigured download, a
  log-streaming route) could OOM the IDE. Bodies now stream through a
  capped reader (8 MB), the transfer is aborted past the cap, the
  declared charset is still honored, and the truncation is flagged on
  the status line — proven against a real in-JVM HTTP server serving
  9 MB (mutation: unbounded `readAllBytes` fails the test).
- **The pretty re-parse left the EDT.** Every send re-parsed the body
  on the paint thread; a megabyte body froze the UI for the parse and
  a deeply-nested one threw `StackOverflowError` (an Error the parse's
  RuntimeException guard never catches) straight through it. The
  guarded `prettyForDisplay` (size gate + SOE degrade-to-raw, proven
  with a 100k-deep body) runs on the send worker; `showResponse`
  renders precomputed text.
- **Sends ride their own interruptible lane, and Cancel is real.** Two
  hung sends used to occupy both slots of the shared two-slot
  RequestProcessor, silently wedging re-aim follows, workspace loads,
  and serving refreshes behind the network. Sends now run on a
  dedicated interruptible lane, the Send button becomes **Cancel**
  while in flight, and an interrupt lands as the grey "Cancelled"
  verdict, not a red network error (live-thread test).
- **A no-op open/close no longer rewrites `.nmoxapi.json`.**
  `componentClosed` saved unconditionally, round-tripping the file
  through the unknown-key-dropping parser — fields written by a NEWER
  NMOX version vanished on a tab you merely looked at. Close now saves
  only when the debounce says dirty (the `onProjectReaimed` idiom);
  the v1.97.0 plaintext-token migration still rewrites immediately via
  its own direct save.
- **Split CSP grades correctly.** The security-header grader read only
  the first value of a multi-valued header; Content-Security-Policy is
  legitimately sent as multiple fields, so `frame-ancestors` in the
  second field looked absent (false MISS) and `unsafe-inline` in the
  second looked clean (false PASS). CSP now grades the union of all
  values; HSTS deliberately keeps first-field-wins (RFC 6797 §8.1).
  Both directions mutation-proven.

## [1.98.0] - 2026-07-20

### Infra Designer: destructive dialogs default to the safe button

The first dedicated **infra** review found its highest-real-world-risk
defect: the confirmations that delete or create real paid cloud
resources defaulted their Enter/Space button to the destructive
option. A reflexive keypress on a Destroy Stack, Destroy Resource, or
Deploy dialog fired the action.

- `NotifyDescriptor.Confirmation` hard-codes `initialValue = OK_OPTION`,
  and `setValue(...)` writes only the current value — neither moves the
  default button. Both destructive confirms now build through the full
  `NotifyDescriptor` constructor with `NO_OPTION` as the initial value,
  and the live Deploy dialog through the `DialogDescriptor` constructor
  with **Cancel** as the initial value. Enter/Space now hits the safe
  button; the destructive action needs a real click.
- Pinned by a source gate (DialogSafetyTest, mutation-proven: reverting
  to the `Confirmation(...)` shortcut or `dd.setValue("Cancel")` fails
  the build by name). Default-button semantics were confirmed from
  NbPresenter bytecode by the review; the exact dialogs only surface
  with a live deployed cloud node, so the constructor-seam gate is the
  standing proof.

Recorded for the next infra release (ledger 53 remainder): the canvas
+ rack aim stay live during a deploy/destroy (a mid-plan node deletion
can orphan a billed resource), re-aim drops the last debounce window
of edits, cross-thread `node.props` mutation can abort the autosave,
and drift's `"404"` substring match severs the deploy linkage.

## [1.97.0] - 2026-07-20

### API Studio auth tokens move to the OS keychain

A dedicated senior review of the never-before-audited **apiclient**
module found the highest-value defect the codebase still carried: auth
tokens typed into API Studio's Auth tab were serialized **plaintext**
into `.nmoxapi.json` — a file the module's own warning label calls
committable — contradicting the Keyring-only law every other studio
has always honored (DB Studio passwords, web3 RPC URLs, ORACLE keys).

- **Tokens are keychain-only now.** Each request carries a stable `id`;
  its bearer/basic secret lives in the OS keychain via the new
  `ApiSecrets` (the `dbstudio.Passwords` idiom: in-memory fallback +
  warn-once when no backend, tests force the fallback). `WorkspaceIO`
  no longer writes `authToken` at all, and the Auth field is a
  `JPasswordField` — the secret neither echoes on screen nor hits disk.
- **Automatic migration.** A pre-v1.97.0 file's plaintext token is
  read once on load, pushed to the keychain, and dropped from the file
  on the next save — no user action, no lost credentials.
- **Basic auth with a single `{{var}}` credential now works.** The
  `user:password` colon was checked on the RAW token *before* variable
  resolution, so a `{{creds}}` credential (the documented usage) sent
  no Authorization header at all. The colon is now checked after
  resolution. Mutation-proven, as is the no-token-in-JSON law.

Tests: ApiSecretsTest (2), ApiClientBuildTest basic-auth single-var
case, WorkspaceIORoundTrip token-never-serialized; apiclient 112.
Recorded for follow-up (ledger 52): the module's unbounded response
buffer + on-EDT re-parse, the no-cancel/shared-pool hang, the lossy
close-save, and the HeaderGrader multi-value miss.

## [1.96.0] - 2026-07-19

### Resurrection survives an unsaved rack

The night journey gauntlet re-walked the oldest shipped surfaces with
real tools. Two passed clean, one found a structural hole:

- **Update center (v1.51.0), PASS across 12 releases of drift**: a real
  v1.90.0 install offered all 11 modules at 1.95.2 from the live GitHub
  catalog, verified their SHA-512 digests against the per-tag absolute
  URLs, showed the MIT license (not "Unknown"), installed, restarted,
  and ran the new code — confirmed by a v1.95.1-only string live.
- **Device SPI (v1.55.0), PASS against current bits**: the example
  plugin compiled unchanged against today's core, installed through
  Tools ▸ Plugins into the upgraded app **without restart**, joined the
  shelf via the live catalog merge, mounted, fired the host-enforced
  Workspace Trust prompt on its exec, and put real `uptime` output on
  its LCD.
- **Resurrection (v1.3.0), FAIL — fixed here**: kill -9 mid-serve
  wrote a perfect session snapshot, but on relaunch the resume offer
  silently skipped: `matchAgainst` only matches devices that exist in
  the rack, and an **unsaved rack** (no Save Patch, the common case)
  restores as the starter patch — nothing to match, no toast, despite
  fresh intent on disk. Now `SessionState.unmatchedAgainst` names what
  the rack lost, the offer includes those devices, and the user's
  CLICK re-creates them from the DeviceCatalog at their recorded slots
  before resuming. Uninstalled plugin types stay unresurrectable
  (ledger 44). Both halves mutation-proven
  (SessionStateTest.unmatchedAgainstFindsUnsavedPatchEntries /
  resumeSessionRecreatesLostDevices).

## [1.95.2] - 2026-07-19

### The seventh review — the gesture must die with its context

Two read-only lenses over the v1.93.0–v1.95.1 surface (serving-gate
guards, rear jack relayout, click-to-click glue, jump-to-component).
The streak holds: real bugs, every fix mutation-proven.

- **HIGH — an armed click-patch survived a rack flip invisibly.** The
  preview only paints on the rear, so after flip-to-front the armed
  state showed nothing; flipping back and clicking any compatible jack
  silently patched a cable the user believed cancelled (with trigger
  jacks: spontaneous device actions on the next signal). A flip now
  cancels the gesture — exactly what `CablePatchGesture.escape()`'s
  javadoc always promised.
- **MED — the armed gesture survived structural rebuilds** (device
  removal, preset/patch load, undo): connect could land on a Port of a
  disposed, un-racked device, painting an undeletable ghost cable.
  Every rebuild now drops the gesture. Both pinned by
  RackPanelGestureCancelTest, each proven failing-first by mutation.
- **MED — INSPECTOR kept the exact v1.93.0 bug shape on a DATA jack**:
  it emitted its ENDPOINT attach address and armed the blinking WIRED
  LED *before* `launch()`, so a Keep Safe answer advertised a debugger
  nothing listens on and left the LED blinking forever. Endpoint, LED,
  and LIVE gate now all wait for launched-for-real
  (ServingTruthTest.inspectorRefusalEmitsNoEndpoint, mutation-proven).
- **LOW — `launch()` could return true on a disposed device** (a
  queued trigger after removal), raising a phantom gate in inverted
  order with exec's synthetic exit. `launchWithEnv` now reports false
  when disposed (mutation-proven).
- Polish: an armed preview now follows the cursor over the rails and
  empty rack, not just device bounds; WORMHOLE's safe-only-by-accident
  pre-launch state got its blessing comment; **ledger 51** records the
  Device SPI's missing launched-for-real signal (additive method when
  a real plugin needs it).

## [1.95.1] - 2026-07-19

### Docs truth repair + the patching hint tells the whole story

The v1.92.0–v1.95.0 run outpaced the per-ship docs law: six releases
shipped with no changelog entry and a CLAUDE.md/plan.md status frozen
at v1.91.0. This release backfills the record (the six entries below
are written from the merged PRs and the shipped code) and brings every
status surface current to v1.95.0, including the ORACLE live proof:
on 2026-07-19 both consult paths ran against the real Anthropic API in
the shipped app — the button path (one-time consent dialog naming
exactly what is sent, correct diagnosis on the LCD) and the hands-free
cable path (VERITAS FAIL → EXPLAIN auto-consulted with zero faceplate
interaction; VIEW showed a correct 4-step fix for the planted bug).

Product fix: the rack palette hint still read "drag jacks to patch
cables" — it now advertises the v1.95.0 gesture too ("drag or click").

## [1.95.0] - 2026-07-19

### Click-to-click cable patching

Found by real use during the ORACLE live drive: the rear rack is wider
than a default window, so a press-drag-release between far-apart jacks
is physically awkward — and out-jacks lay right-to-left from the
device's right edge, putting OK/FAIL/DONE off-window at default width.
Now a **click on a jack arms it** (the cable preview follows the
cursor, surviving scrolls), a **click on a compatible jack connects**,
empty space or Escape cancels, and clicking another same-side jack
re-arms from there. The classic drag is unchanged — both gestures run
through one pure state machine (`CablePatchGesture`), every transition
unit-tested (CablePatchGestureTest, 5).

## [1.94.0] - 2026-07-19

### Block Studio jump-to-component

Double-click (or F3) on an Element piece naming a **sibling
component's tag** switches the studio to that component — riding
`switchToComponent`, so the jump is a patch boundary (force-save,
fresh undo). The active component's own tag and unknown tags fall
through to the usual edit-params gesture. `Host.openComponentWithTag`
is a default-false seam so the pure canvas stays headless-testable.
With this, the Block Studio idea backlog is empty.

## [1.93.1] - 2026-07-19

### Rear jacks compress instead of colliding

Jack-heavy consoles (base ports + the serve family) crowded their rear
labels into collisions — the "ENABSERVING" observation from the
v1.90.0 live drive. The jack pitch now compresses uniformly (82px down
to fit) once a device's port count would collide the INPUTS/OUTPUTS
groups; ports re-lay on every add because the pitch depends on the
final count. A catalog-wide gate (ConsoleJackContractTest) asserts
≥44px clear air between the groups on every device — proven
failing-first (it named HALO on the old layout).

## [1.93.0] - 2026-07-19

### The gate never lies through the trust prompt

Serve verbs raised SERVING/RUNNING/LIVE **before** `launch()`, whose
Workspace Trust gate returns early on "Keep Safe" — so declining trust
left a phantom high gate wired downstream forever (the v1.90.0
live-drive observation, confirmed real). `launch()`/`launchWithEnv()`
now return launched-for-real, and all 18 gate emitters across 14
devices are guarded (`if (launch(...)) { emit … }`). A static
`trustCheck` seam makes the Keep Safe path testable; ServingTruthTest
proven failing-first, teardown via the v1.90.0 STOP jack.

## [1.92.1] - 2026-07-19

### Sixth review: the vendor tripwire covers all eight libraries

One lens over the v1.90–v1.92 surface (clean on 10 of 11 questions).
The find: VendorResourcesTest's marker scan never covered the two
v1.92.0 additions and read only the first 2KB — Alpine's version
property sits past 21KB. Now alpine (`version:"3.14.9"`) and htmx
(`version:"2.0.4"`) markers gate the vendored bytes with a full-file
scan; a wrong-version mutation fails 3 checks.

## [1.92.0] - 2026-07-19

### The modern lightweights — Solid, Preact, Qwik, Lit, Alpine, htmx, Ember, Remix

The framework-coverage matrix answered ("cover the ones we don't"):
- **Six new learning spaces** (72 → 78): Solid/Preact/Qwik as real
  Vite counter apps teaching each framework's core idea, Lit pointing
  at Block Studio's generated components, Alpine/htmx as honest
  no-build classic-web pages. All six live-proven with real installs
  and dev servers before ship.
- **Vite + Solid project template** (14th).
- **Classic Kit grows to 8 libraries**: Alpine.js 3.14.9 (MIT) and
  htmx 2.0.4 (0BSD, LICENSE verified by hand) vendored sha256-pinned,
  with detection and 22 API completion entries. The script-tag law
  loosened to attributes-allowed (Alpine requires `defer`) while still
  pinning loads-exactly-its-own-file.
- **ember-cli-build.js and remix.config.js** join the manifest set (53
  recognized).

## [1.91.0] - 2026-07-19

### ORACLE joins the patch bay — auto-explain by cable

The AI error explainer becomes composable (the plan.md "REFLEX wiring"
direction, chosen): ORACLE gains an **EXPLAIN trigger in-jack** and an
**OUT data out-jack**. Patch VERITAS FAIL → ORACLE EXPLAIN and the
explanation arrives hands-free the moment a suite fails; patch OUT →
MONITOR/PHOSPHOR to read the full text in the rack.

The cable path keeps every safety law and adds two of its own:
- **A cable never prompts**: if the one-time consent hasn't been
  granted by a human button press, the jack refuses honestly on the
  LCD ("AUTO-EXPLAIN NEEDS CONSENT — PRESS EXPLAIN ONCE") — no dialog
  storms from automation, no API call (mutation-proven: removing the
  gate fails cableNeverPrompts).
- **Consults are rate-limited** (30s cooldown): a flapping suite or a
  REFLEX save loop cannot hammer a paid API.
- The key gate and the no-network-without-signal law hold unchanged;
  a bounded grace wait covers the FAIL-cable-vs-FlightRecorder-tap
  ordering race.

Tests: OracleDeviceTest +4 (cable consult, never-prompts, cooldown
window arithmetic, OUT emission through a real rack router).

## [1.90.1] - 2026-07-19

### SPECTER faceplate polish

The ENGINE knob (a two-unit control) visually overlapped the RUN
button — found by the v1.90.0 live cable drive, invisible to the fit
law (which checks the bottom edge, not overlap). The action row moved
below the knob's extent; verified clean on a live faceplate.

## [1.90.0] - 2026-07-19

### The jacks tell the truth (ledger 50 closed)

Five devices (VELOCITY, COSMOS, NIMBUS, KINETIC, SPECTER) declared
STOP and ENABLE input jacks — VELOCITY's family also SERVE — that no
receive() handled: cabled signals died at the faceplate while the
buttons worked (inert since each console shipped, surfaced by the
v1.89.0 review). All five now speak the NEXUS shape: serve → dev,
stop → stop, enable → serve-while-high/stop-on-drop. Patch VELOCITY
SERVING → SPECTER ENABLE and the E2E suite dies with the dev server.

`ConsoleJackContractTest` pins the law catalog-wide: any device
declaring a stop/enable IN jack whose receive() comes from the base
class fails the build with its name — proven failing-first on the
unwired VELOCITY and NIMBUS.

## [1.89.0] - 2026-07-18

### The fifth senior review (v1.83–v1.88 surface)

Two read-only lenses (the E2E surface; the Block Studio tail), eight
proven fixes, two blessings:

- **CI export leaked `--headed`** (HIGH): SPECTER's CI step read the
  live HEADED toggle — a headed export aborts on X-less runners. New
  `ciCommand()` seam in CommandDevice; SPECTER exports always-headless
  while the button still honors the toggle (test pins both).
- **RECORD could aim codegen at SPECTER's own report server**: with the
  HTML report as the only WEB serving, codegen opened the static report
  page. liveServingUrl now skips the device's own registry entry.
- **Monorepo engine resolution**: config files are now read beside the
  NODE manifest (the same kindDir the dependency fallback and the
  launched command use) — "config beats dependencies" can no longer
  invert when e2e lives in packages/web.
- **readyFired lifecycle**: reset in onFinished — RUN can also serve
  (Playwright's report-on-failure), and a latched one-shot let a later
  serving emit URL without ever firing READY.
- **Preview library was walking live EDT-mutable docs on the HTTP
  thread** (CME / torn generation on refresh-during-drag): the harness
  and /lib now read a volatile snapshot rebuilt on the EDT by
  regenerate() — the lastResult staleness contract, applied to the
  whole library.
- **Leading-space TEXT generated an unimportable file**: validate()
  refuses it (the parser's TEXT branch reads indented-looking lines as
  structure; reachable via hand-edited workspace files).
- Stale ⌘I entry when the active component goes invalid (clear on the
  invalid branch); insertKind NPE when the add-piece popup outlives a
  re-aim (refused no-op).
- Blessed to ledger 50: the console family's inert STOP/ENABLE in-jacks
  (pre-existing since v1.65.0, own-sweep unit) and SPECTER's
  serving-gate no-op on non-serving verbs.

## [1.88.0] - 2026-07-18

### Block Studio v5 — the last dialect gap, and Save All

- **TEXT starting with `<` round-trips** (the documented pre-v1.88.0
  one-way limitation, closed): TEXT content is entity-escaped by the
  generator (`&` then `<`, reversible order) and unescaped by the
  parser at the TEXT call site only — LOG and attribute paths
  untouched. This also makes TEXT honestly literal in the browser: a
  text piece shows its characters; markup is what Element pieces are
  for. Text that literally says `&lt;` survives the trip
  (escape/unescape order proven). New round-trip corpus doc
  `markupishText` pins all three shapes; the unescape mutation fails 18
  checks.
- **Save All**: one button writes every valid component in the
  workspace through the same never-clobber path as Save Component —
  invalid components sit out, foreign files refuse, and the status line
  counts each outcome ("Saved 2 · refused 1 (foreign: my-widget) · 1
  invalid skipped").

## [1.87.0] - 2026-07-18

### The E2E capstone

SPECTER gets its discoverability pieces, plus a startup polish:

- **E2E Loop preset** (15th): VELOCITY serves and its READY jack fires
  SPECTER's RUN — the suite runs the moment the app is up; SCOPE follows
  whatever is serving (the app first, the Playwright HTML report when
  REPORT announces its URL); MONITOR taps the output.
- **Playwright learning space** (72nd): a self-contained starter (two
  tests against a `data:` page — first green run with no server), a
  config whose presence makes SPECTER's ENGINE=auto resolve to
  playwright, and a tutorial that walks RUN/HEADED/REPORT/RECORD/
  BROWSERS and the E2E Loop preset.
- **Startup warnings fixed**: the two menu-position collisions the
  v1.86.0 boot log showed — New Learning Space… shared `Menu/File` 119
  with the Classic Kit (→ 112, beside New Project), and Block Studio
  shared `Menu/Window` 259 with Contract Studio (→ 257) — verified
  gone on a throwaway-userdir boot of the assembled app.

## [1.86.0] - 2026-07-18

### SPECTER — the E2E console (51st device)

End-to-end suites become first-class rack citizens. VERITAS runs unit
suites; SPECTER speaks the E2E workflow:

- **ENGINE=auto** resolves Playwright vs Cypress from the project's own
  config file (`playwright.config.*` / `cypress.config.*`, Playwright
  first), then its dependencies; no E2E setup greys honestly
  ("NO E2E CONFIG — ADD PLAYWRIGHT OR CYPRESS").
- **RUN** drives the suite (`npx playwright test` / `npx cypress run`);
  the **HEADED** toggle shows the browser (`--headed`, or
  `cypress open` — Cypress's interactive runner).
- **REPORT** serves the Playwright HTML report as a real serving —
  URL/READY/SERVING jacks out, the ⇄ chip, ⌘I Live Servers, and
  deregistration when it stops. Cypress has no report server: the verb
  greys with the reason instead of running the wrong thing.
- **RECORD** launches Playwright codegen aimed at the project's **live
  dev server from the ServingRegistry** (the VITALS auto-target idea) —
  serve with VELOCITY, press RECORD, click around, keep the generated
  test. On Cypress it points you at HEADED (cypress open IS its
  recorder).
- **BROWSERS** installs the runtime browsers; the version cluster
  tracks `@playwright/test`/`cypress` against the registry like every
  console.
- CI export: SPECTER's RUN is a step (`DeviceCatalog` pin updated);
  both contract gates proved failing-first (faceplate fit, CI-step
  exactness) before passing.

Tests: `SpecterDeviceTest` (5 — engine precedence incl.
config-beats-deps, both engines' verb shapes, honest greys, currency
package); the 365-case contract suite and docs gate cover the rest.
docs/devices.md regenerated (51 devices).

## [1.85.0] - 2026-07-18

### Block Studio v5 — the composition loop

Components can nest each other, and the preview shows it:

- An Element piece whose tag names a **sibling component**
  (`my-badge` inside `my-widget`) always generated honest markup —
  now it renders: the preview harness imports every other valid
  component's module from `/lib/<tag>.js` (read live per request,
  like the active module), so nested custom tags upgrade instead of
  sitting inert. The active component is excluded from the library
  imports (a double `customElements.define` throws) — mutation-proven.
- Broken components sit the pass out (only components that validate
  join the library); an unknown `/lib` tag is an honest 404.
- Docs: user guide + README now describe the composed preview.

Tests: `BlocksV2Test.previewServesTheLibrary` (harness imports, /lib
serving, active-exclusion, 404).

## [1.84.0] - 2026-07-18

### Block Studio v4 — multi-component workspaces

One `.nmoxblocks.json` now holds any number of components:

- **`BlockWorkspace`** (pure core): an ordered component list with one
  active slot. v1 single-doc files load verbatim as a one-component
  workspace and the next save writes the v2 shape (`version: 2`,
  `active`, `components: [...]`) — forward-only migration, the wrap
  branch mutation-proven. Every doc still loads through
  `BlockDoc.fromJson`, so the interlock law re-checks on load for free.
  The workspace is never empty (removing the last component leaves a
  fresh doc) and `add()` mints unique tags (`my-widget-2`, ...).
- **Toolbar switcher**: a combo of the workspace's tags plus **+** /
  **−** (remove confirms first and never touches a saved
  `src/components` file). A rename via F2 renames the switcher row.
- **A switch is a patch boundary** (the v1.50 law): pending edits
  force-save first and the undo history starts fresh — ⌘Z can never
  peel one component's edits onto another (mutation-proven). An undo
  restore swaps the workspace's active slot too, so the next save
  writes what you see.
- **Open Component… lands in the workspace**: a parsed file replaces
  the same-tag component in place (one undo step back to what it
  replaced) or joins as a new component; either way it becomes active.
- **Preview follows the active component**, and the status line says
  where you are (`5 pieces → my-widget.js · component 2/3`).
- Docs truth: Block Studio finally enters the user guide (§6, the
  studio had been absent since v1.78.0) and the README feature list.

Tests: `BlockWorkspaceTest` (6, pure core), `BlockMultiComponentTest`
(3, live TC: v1-file migration + v2 persist, switch-clears-undo +
whole-workspace save, import replace-vs-append). Three mutations
proven fatal: the v1-wrap branch, the switch's `undo.clear()`, the
unique-tag loop.

## [1.83.0] - 2026-07-17

### Block Studio without a mouse (ledger 48 closed)

The canvas — the studio's primary control — now honors the v1.41 law:

- **Traversal**: Up/Down walk the pieces in canvas order, Left selects
  the parent, Right the first child; selection scrolls into view and
  Escape clears it.
- **Editing**: Enter opens an "add piece" menu of exactly the kinds the
  interlock law admits under the selected piece (Shift+Enter inserts a
  sibling after it), Alt+Up/Down reorder within the parent, F2 opens the
  param editor, Delete removes — every path the same doc operations as
  the mouse gestures, so undo, persistence, and regeneration are
  identical.
- **Assistive tech sees the pieces**: the canvas reports a LIST of
  LIST_ITEM children — kind, face summary, level, position, and live
  SELECTED state — and its accessible description names every key.

`BlockCanvasKeyboardTest` drives the real key handler with synthesized
events: traversal, reorder, delete, F2, the legal-kinds seam against
`BlockRules`, insert-and-select, and the accessible-children contract.

## [1.82.0] - 2026-07-17

### The Block Studio review — two lenses over v1.78–v1.81, fourteen fixes

The post-burst review discipline applied to four releases of brand-new
surface; one lens even compiled the pure cores standalone and
probe-proved every finding before reporting it. Everything proven got a
fix with a test that fails on the old code:

- **Reopening the tab reloads for the current aim.** Close removed the
  rack listener, so a re-aim while closed was missed and the reopened
  canvas kept editing — and auto-persisting — the OLD project's blocks
  (the Infra-Designer inverted-lifecycle class). The pulse also stayed
  permanently dead after a reopen.
- **The round-trip law now truly holds.** Listener blocks are emitted in
  canonical host-grouped order (each element's listeners together, hosts
  in template order) — the one order the parser's rebuild preserves. An
  ON_EVENT placed before a sibling with its own listener used to come
  back reordered; the corpus gained exactly that case.
- **validate() covers everything generate() would throw on.** A ternary
  in a Set-state expression (or a 20-digit timer interval) crashed the
  EDT past an empty validation; both are problem sentences now, plus new
  refusals for reserved `data-b` attributes, newline-smuggling params
  (invalid JS via hand-edited workspaces), and leading-zero literals
  (a module SyntaxError the preview would serve).
- **Corrupt workspaces are kept as `.nmoxblocks.json.bak`** before the
  fresh-doc fallback can overwrite them (the house law; BlockIO's javadoc
  had promised it all along).
- **The self-write stamp race is closed** with the API Studio idiom: a
  pulse tick landing between the atomic move and the stamp re-checks on
  the IO lane, where any in-flight save has already stamped.
- Re-aims inside the 800 ms save debounce force-save the old project
  first; `lastResult` and the ⌘I snapshot clear on re-aim so Preview can
  never serve the previous project; same-parent downward drags land on
  the previewed slot (off-by-one); imports validate before touching the
  canvas; clicking Set-attribute/Style pieces highlights the parent's
  lines; the harness tag supplier degrades to the last valid tag; the
  preview thread is daemon; aborted palette drags clear their drop line;
  insert() refuses aliasing an attached block.

Ledger 48 (keyboard-operable canvas — real work, own release) and 49
(preview deregister-on-exit, blessed residue) record the rest. rack 1096.

## [1.81.0] - 2026-07-17

### Block Studio v3 — the round trip

The stretch goal: generated code now parses BACK into blocks, making
the code pane a genuinely two-way surface.

- **BlockParser** — a strict parser for the Block Studio dialect (never
  general JavaScript): the exact canonical shape the generator emits,
  with line-numbered refusals for anything else and no half-imports.
  Hand edits that stay inside the dialect import fine — the honest slice
  of two-way editing. Identity survives: element ids ride the `data-b`
  anchors and listener ids ride the `const` variables, so
  **`generate(parse(code))` reproduces the file byte for byte** —
  pinned by `BlockRoundTripTest` across a six-doc corpus covering every
  piece, deep nesting, and hostile escaping (backticks, `${`,
  backslashes, quotes). Consistency is verified, not assumed: a
  re-render without a Set-state, a class/tag mismatch, or a tampered
  skeleton line all refuse with the offending line.
- **Open Component…** in the toolbar: pick any Block-Studio-generated
  file (chooser starts at `src/components`) and it loads onto the
  canvas — read+parse on the IO lane, undo snapshot, workspace persist;
  an off-dialect file explains itself in a dialog.
- **Prop piece** — observed HTML attributes as first-class blocks:
  emits `static get observedAttributes()`, an `attributeChangedCallback`
  re-render, and a private `#prop_name()` accessor carrying the default
  (private methods can't collide with HTMLElement members the way public
  getters would). `{@name}` interpolates props everywhere `{state}`
  works — text, attributes, log/dispatch templates, and expressions —
  and defaults round-trip through the accessor even when never
  interpolated.

## [1.80.0] - 2026-07-17

### Block Studio v2 — the live loop

The canvas now closes the loop to a real browser, and three new pieces
teach the rest of the web-component lifecycle:

- **Live preview.** A Preview button serves the component from an
  in-memory loopback HTTP server (the JDK's own — zero processes, zero
  temp files; the generator is read per request so a browser refresh
  shows your latest blocks). The harness page mounts the component
  twice — bare, and with slotted light-DOM content — and logs every
  CustomEvent it dispatches, so the new pieces are visible without
  devtools. The serving registers on the ⇄ chip and ⌘I Live Servers,
  and Stop (or closing the tab, or re-aiming) deregisters it — the
  serve-device law.
- **Slot** (structure): `<slot>`/`<slot name="…">` — composition from
  the light DOM.
- **Timer** (logic): child actions run every N ms via a generated
  `connectedCallback`/`disconnectedCallback` pair with honest
  `clearInterval` cleanup — the lifecycle taught by example. A
  toggle-class under a timer acts on the host element itself.
- **Dispatch event** (logic): a bubbling, composed `CustomEvent` whose
  detail interpolates state (`count is {count}`) with full
  template-literal escaping.

All three interlock lawfully (slots in structure, timers only on the
component, dispatch wherever actions live), carry code ranges for the
click-to-highlight mapping, and are validated with human sentences
(interval floor, event-name shape, slot-name shape). The palette
gained them by construction — it derives from the kind enum, and the
exhaustive canvas-face switch forced honest faces at compile time.

## [1.79.0] - 2026-07-17

### The debt sprint — ledger 46 closed, Block Studio reaches studio-law parity

- **CI export never fails silently again (ledger 46).** Every toolchain an
  exported lane can speak now gets its ecosystem's setup action in the
  generated workflow — erlef/setup-beam (+gleam-version), setup-julia,
  vlang/setup-v, fortran-lang/setup-fpm, alire-project/setup-alire,
  setup-nim, setup-dlang, setup-racket, setup-zig, setup-dart,
  setup-dotnet, haskell-actions/setup, ocaml/setup-ocaml,
  install-crystal — with the npm-riding functional web (Elm/ReScript/
  PureScript) deduped to a single setup-node, and an honest `# NOTE:`
  comment for the two kinds with no trustworthy action (scala/swift).
  Test-pinned including the dedup.
- **Block Studio honors the two studio laws its v1 deferred.**
  `.nmoxblocks.json` now reloads on external edits (1.5 s stat pulse,
  self-write-stamp discrimination via the v1.35 idiom; a foreign edit
  landing while a debounced in-studio save is pending is overridden and
  said out loud, never silently clobbered) — and ⌘I reaches the studio:
  type "blocks", "component", or your component's tag and Enter opens
  it, with a live "<tag> (N pieces)" label published on every
  regeneration. Position 350, audited free.
- **Docs truth**: ledger 43/44 finally marked CLOSED (v1.58.0 closed
  them; the ledger lagged).
- One collision found and fixed in passing: a hand-written
  `Bundle.properties` in a package that also uses `@NbBundle.Messages`
  clobbers the generated bundle (the studio's window title vanished) —
  the ⌘I category bundle moved to a `search` subpackage, the layout the
  other studios already use.

## [1.78.0] - 2026-07-17

### Block Studio — compose web components from interlocking pieces

A new studio tab (⌥⌘5, Window ▸ Block Studio): a Scratch-like canvas
where typed pieces snap together to compose a real Web Component, with
the generated code beside it as a live, *mapped* projection — click a
piece and its exact lines highlight, because the generator records a
character range per block.

- **Eleven piece kinds** across four hue families: Component, Element,
  Text, Attribute, Style (structure/content), State (fields), On-event,
  Set-state, Toggle-class, Log, If-state (logic). The interlock law
  (which pieces snap inside which) is a pure, matrix-tested rule set —
  illegal drops never light a slot, hand-edited workspace files that
  smuggle an illegal nesting are refused wholesale on load.
- **Real code out**: a self-contained ES module — class extending
  HTMLElement, shadow DOM, private state fields, a render() that
  rebuilds the template and re-wires listeners, customElements.define.
  `{state}` interpolates declared fields; everything else is escaped so
  a block parameter can never break out of the template literal. The
  custom-element tag law (lowercase + hyphen) is validated with honest
  messages in the code pane.
- **Save Component** writes `src/components/<tag>.js` into the aimed
  project — atomically, and never-clobber: a file without the
  generated-by marker is refused with an explanation.
- **House laws held**: zero boot cost (all work rides first show), IO on
  a named RequestProcessor with atomic writes, the workspace persists to
  `.nmoxblocks.json` per project and follows re-aims, ⌘Z undoes
  structural edits (JSON snapshots, capped), rack listener symmetric
  across open/close, every control accessibly named — the lifecycle test
  caught the canvas lacking an AccessibleContext before ship.
- **Deliberate v1 scope** (recorded): blocks→code is one-way (no parser
  from hand-written code back to pieces), no external-edit reload pulse
  on `.nmoxblocks.json`, no ⌘I provider yet.

## [1.77.2] - 2026-07-16

### Every new learning space live-verified

Installed the real toolchains (brew: vlang, fpm, gnucobol, fpc, odin,
haxe, janet, guile, swi-prolog, gnu-smalltalk) and drove all twelve
v1.72–v1.77 spaces' actual driver commands against their generated
files. **Eleven of twelve pass exactly as their tutorials claim**: the
Fortran/Pascal/Odin/COBOL/Haxe run-spaces compile and print, and the
Janet/Guile/Prolog/GNU-Smalltalk/Tcl REPLs echo every snippet — the
v1.76.0 self-printing snippet fix doing precisely its job on piped
stdin (Ada untestable here: alr has no brew formula, already documented).

The one find: **Homebrew's V can't link libgc out of the box on this
machine** (`ld: library 'gc' not found` from V's own cc invocation —
`-L$(brew --prefix)/lib` fixes it). That's a V-packaging quirk, not an
NMOX lane bug, but it hits the exact install path our space recommends —
so the V tutorial now carries a Troubleshooting section with the
one-line workaround.

## [1.77.1] - 2026-07-16

### INSPECTOR greys honestly (ledger 47 closed)

The debug launcher's AUTO no longer falls through to a doomed
`node --inspect` command on toolchains with no wired debugger: the node
default now applies only to the web family, the six wired debuggers
(python/go/maven/gradle/ruby/php) map as before, and everything else —
Rust, Zig, V, Fortran, Ada, and friends — shows "NO DEBUGGER FOR <KIND>
— DIAL TARGET" on the LCD with no spawn and no gate. An explicit knob
position still always resolves. DebugDeviceGreyTest pins all three
behaviors, mutation-proven.

## [1.77.0] - 2026-07-16

### The polyglot tail — Haxe and Janet

- **Haxe** (`.hx`, `text/x-haxe`): the official vshaxe grammar (MIT,
  plist→JSON, sha256 in NOTICE), `//` comments, keyword completion,
  brace-family Navigator outline, and a `haxe --main Main --interp`
  run-space (compile + execute in Haxe's interpreter — no target
  needed). `.hxml` build files deliberately unclaimed (they keep their
  own upstream grammar).
- **Janet** (`.janet`/`.jdn`, `text/x-janet`): the official janet-lang
  grammar (MIT, plist→JSON), `#` comments, keyword completion, the
  Navigator outline rides the Clojure extractor (same `(defn …)` shape),
  a `janet-lsp` LSP entry, and a `janet` REPL space with self-printing
  snippets from day one (the v1.76.0 piped-echo lesson applied).
- **Raku and Forth honestly skipped**: no cleanly-licensed TextMate
  grammar exists for either (documented in NOTICE) — the Odin-manifest
  style of skip.

The v1.76.0 OutlineNavigatorGateTest caught its first real regression
during this very release — the new family() mappings landed without
their Navigator registrations, and the gate failed the build until they
did. Doctor probes `haxe`/`janet`. 67 grammars, 71 learning spaces.

## [1.76.0] - 2026-07-16

### Senior review of the v1.72–v1.75 surface

Two read-only lenses (stack-lane consistency + editor registrations)
over the four stack releases, then fixes for what they proved. The lane
lens came back CLEAN on the new verticals — every reverse map,
grey-not-mutate verb, precedence slot, and append-only knob array held,
with the guard tests doing their job. The registration lens found the
release's headline bug:

- **The Navigator outline was dead code for ten languages.** The
  Structure panel's registration list froze at its v1.34.0 shape while
  OutlineModel kept gaining extractors — so the Fortran outline
  (a v1.73.0 headline), the V/Odin brace outlines, Scheme, and the six
  v1.69/v1.70 language outlines were built, unit-tested, and unreachable
  in the product. Fixed: 11 registrations added (+ text/markdown), and a
  new OutlineNavigatorGateTest parses both source files and fails when
  family() gains a mime the panel doesn't register (mutation-proven —
  it fails on the old panel naming text/x-fortran).
- **File ▸ Open Project now recognizes eight more manifests.**
  WebProjectFactory never learned gleam.toml, pubspec.yaml, build.sbt,
  stack.yaml, cabal.project, build.zig, dune-project, or shard.yml —
  pure Gleam/Dart/Scala/Haskell/Zig/OCaml/Crystal projects couldn't open
  as platform projects (only the rack worked). Test-pinned.
- **The Ada learning space can actually build now**: it was missing its
  hello.gpr project file (alr requires one) and carried a nonexistent
  `brew install alire` hint — a real GPR + complete manifest shipped,
  hints corrected in both the space and Doctor (alr has no brew formula).
- **The Prolog tutorial's load step works**: `[family].` only resolves
  .pl files in SWI — now `['family.pro'].` (the file dodges Perl's .pl
  on purpose). A bogus `succ_or_zero` keyword dropped.
- **REPL snippets are self-printing** for guile/gst/swipl — piped stdin
  suppresses interactive echo (the v1.25.1 bug class), so every snippet
  now prints its own result (display/printNl/writeln).
- **Doctor probes v-analyzer + ada_language_server** (the two catalogued
  LSPs it didn't cover).

Blessed to the ledger with reasons: CiExporter setup steps for the
post-v1.59 toolchains (item 46) and INSPECTOR's node-lane default for
undebuggable kinds (item 47) — both pre-existing classes the new kinds
merely joined. Inherited grammar quirks (tcl's bare `regexp` include,
cobol's `source.openesql`) noted, upstream-pinned, tolerable.

## [1.75.0] - 2026-07-16

### The systems-heritage release — Ada (full vertical), Pascal, Odin, COBOL

- **Ada is a full vertical.** `alire.toml` → `ProjectKind.ADA`; IGNITION
  `alr run`, FORGE `alr build`, CRATE fetches deps via `alr build`
  (update `alr update`; CHECK greys — no outdated query); VERITAS greys
  honestly — Ada has no universal test verb, and the lane test pins the
  grey so the kind can never fall through to `npm test` (the
  ReScript-bug class). ROSETTA lists `ada`; the IDE-native Run/Build
  speak `alr`. Grammar from textmate/ada.tmbundle (permissive TextMate
  bundle license — AdaCore's own grammar is GPL-3.0 and deliberately not
  used, documented in NOTICE); `--` comments; keyword completion;
  `ada_language_server` LSP entry; an `alr run` learning space over a
  real Alire crate.
- **Pascal** (`.pas`/`.pp`/`.lpr`): alefragnani grammar (MIT,
  plist→JSON), `//` comments, keyword completion, an `instantfpc`
  run-space (Free Pascal's compile-and-execute).
- **Odin** (`.odin`): the official ols grammar (MIT), `//` comments,
  keyword completion, brace-family outline, `ols` LSP entry, an
  `odin run .` run-space. No manifest exists (the long-standing honest
  skip) — editor + space + Doctor carry the experience.
- **COBOL** (`.cob`/`.cbl`/`.cpy`): spgennard grammar (MIT), `*>`
  comments, keyword completion, a `cobc -xj` run-space (GnuCOBOL
  compile-and-run).

Environment Doctor probes `alr`/`odin`/`instantfpc`/`cobc`. 65 grammars,
69 learning spaces. Grammar/comment/completion gates extended; Ada lane
+ IDE-action tests added.

## [1.74.0] - 2026-07-16

### The classics release — GNU Smalltalk, Prolog, Tcl, Scheme

Four foundational languages join the editor as first-class citizens,
each with a real REPL learning space. None has a standard project
manifest or maintained language server, so the honest scope is editor
citizenship + REPL + Doctor — no invented build lanes.

- **GNU Smalltalk** (`.st`, `text/x-smalltalk`): the leocamello
  gnu-smalltalk grammar (MIT, converted YAML→JSON, sha256 in NOTICE),
  CSL language, keyword/message completion (printNl, collect:, ifTrue:,
  subclass: …), spellcheck + typing intelligence. Deliberately absent
  from the comment-toggle map — Smalltalk has no line comment, only
  "double-quoted" blocks (test-pinned). A `gst` REPL learning space.
- **Prolog** (`.pro`/`.prolog`/`.plt`, `text/x-prolog`): the
  arthwang/vsc-prolog SWI grammar (MIT), `%` comments, directive/builtin
  completion. Deliberately does NOT claim `.pl` — Perl owns it here; a
  wrong-language editor is worse than asking for the unambiguous
  spelling (documented in the registration). A `swipl` REPL space with
  the classic family-tree sample.
- **Tcl** (`.tcl`/`.tk`, `text/x-tcl`): the bitwisecook grammar (MIT,
  converted YAML→JSON), `#` comments, command completion (proc, foreach,
  lappend, regexp …). A `tclsh` REPL space.
- **Scheme** (`.scm`/`.ss`/`.sld`/`.sps`, `text/x-scheme`): the sjhuangx
  grammar (MIT, converted plist→JSON), `;` comments, R7RS keyword
  completion, and the Navigator outline rides the Racket extractor —
  same `(define …)` shape (test-pinned). A `guile` REPL space; Racket
  keeps its own `.rkt` grammar and space.

Environment Doctor probes `gst`/`swipl`/`tclsh`/`guile`. 61 grammars,
65 learning spaces. Grammar/comment/completion/outline gates extended;
the comment gate pins Smalltalk's absence as deliberate.

## [1.73.0] - 2026-07-16

### Fortran — a full stack vertical (fpm)

The original high-performance language joins as a first-class citizen —
still the backbone of scientific and HPC computing, and now with a
modern cargo-like build tool in **fpm** (the Fortran Package Manager).

- **Editor citizenship.** Pinned `fortran` (free-form) TextMate grammar
  (fortran-lang/vscode-fortran-support, MIT, sha256 in NOTICE) on
  `text/x-fortran` for `.f90`/`.f95`/`.f03`/`.f08`/`.f18` (fixed-form
  `.f`/`.for` deliberately unclaimed — the grammar is free-form); a CSL
  `FortranLanguage` (`!` comment toggle); the mime threaded through
  typing pairs, deletion, spellcheck, comment toggling, and Fortran
  keyword completion. A dedicated Navigator outline extracts
  program/module/subroutine/function blocks and derived types — and
  correctly does NOT mistake a `type(point) :: p` variable declaration
  for a type definition (test-pinned).
- **Toolchain lanes.** `ProjectKind.FORTRAN` detects `fpm.toml`;
  IGNITION runs `fpm run`, FORGE builds `fpm build`, VERITAS tests
  `fpm test`, CRATE fetches deps via `fpm build` (update `fpm update`;
  CHECK greys — fpm has no outdated query); ROSETTA lists `fortran`; the
  IDE-native Run/Build/Test speak the `fpm` CLI (no clean). `fpm.toml`
  is a recognized project manifest.
- **LSP + Doctor.** `fortls` wired as the Fortran language server + added
  to the catalog + Environment Doctor probes (`fpm` and `fortls`).
- **Learning Space.** A "Fortran" space — a run-driver (`fpm run`) over a
  minimal fpm project (fpm.toml + app/main.f90 showing `program`,
  internal `function`s, array returns) with a walked tutorial. 61 spaces.

Full test coverage: detection + every AUTO lane + the IDE lanes proven
(PolyglotDevicesTest.fortranLanes, WebProjectCommandsTest.fortranRunsBuildsTests),
grammar/mime/comment/completion/LSP/outline gates extended.

## [1.72.0] - 2026-07-16

### V (vlang) — a full stack vertical

V joins the first-class languages, end to end. V compiles itself in
under a second into small, dependency-free native binaries, reads like
Go with the sharp edges filed off, and ships `vweb` — a web story that
earns it a place in a web IDE.

- **Editor citizenship.** Pinned `vlang` TextMate grammar
  (vlang/vscode-vlang, MIT, sha256 in NOTICE-grammars.md) on
  `text/x-vlang` for `.v`/`.vsh`/`.vv`; a CSL `VLanguage` (`//` comment
  toggle); the mime threaded through typing pairs, deletion, spellcheck,
  comment toggling, keyword completion (V keywords + builtin types), and
  a brace-family Navigator outline.
- **Toolchain lanes.** `ProjectKind.VLANG` detects `v.mod`; IGNITION runs
  `v run .`, FORGE builds `v .`, VERITAS tests `v test .`, CRATE installs
  `v install` (update `v update`; CHECK greys — vpm has no outdated
  query); ROSETTA lists `vlang`; the IDE-native Run/Build/Test speak the
  `v` CLI (no clean action). `v.mod` is a recognized project manifest.
- **LSP + Doctor.** `v-analyzer` wired as the V language server and added
  to the catalog + Environment Doctor probes.
- **Learning Space.** A "V" space (`v repl` driver + a `hello.v` sample +
  a walked tutorial pointing at IGNITION) — 60 built-in spaces.

Full test coverage: detection + every AUTO lane + the IDE lanes proven
(PolyglotDevicesTest.vlangLanes, WebProjectCommandsTest.vlangRunsBuildsTests),
grammar/mime/comment/completion/LSP gates extended, learning-catalog
launchability check. The TestDevice AUTO map's missing VLANG case was
caught by the new lane test before ship.

## [1.71.0] - 2026-07-16

### Senior review of the v1.64–v1.70 surface

A read-only two-lens audit (framework consoles + stack lanes) over the
day's ~10 releases, then fixes for what it proved. The console lens came
back clean — all 8 framework devices had correct serving-registry parity,
readyFired resets, and version-package/LCD matching (no copy-adaptation
slips). The stack-lane lens found three real issues, all fixed and
mutation-proven:

- **Racket build compiled the wrong file.** FORGE and the IDE's Build ran
  `raco make info.rkt` — but info.rkt is Racket's package *metadata*, not
  the program; it compiled nothing useful and failed on script-style
  projects. Now `raco make main.rkt`, matching the run/test lanes.
- **CRATE's CHECK silently mutated for five tools.** The outdated/CHECK
  button fell through to the install/download default for Gleam, Nim, D,
  Racket, and PureScript (none have an "outdated" query) — a "check for
  updates" button that installed. Now returns null so CHECK greys, while
  install/update stay live.
- **ReScript fell through to node.** A ReScript project (build-only, no
  run or test) resolved IGNITION to `npm start` and VERITAS to `npm test`
  instead of greying — the rack devices lacked the RESCRIPT case the IDE
  lane already had. Both now grey honestly; FORGE still builds.

Blessed, not fixed (recorded): NextDevice's START button doesn't reset the
ready/serving state the way the newer PREVIEW paths do (original reference
behavior, minor); the same outdated-fallthrough exists for older
single-command kinds (Scala/Gradle/Clojure/Zig/OCaml) — pre-existing,
outside this review's surface.

## [1.70.0] - 2026-07-16

### The functional web — Elm, ReScript, PureScript

The compile-to-JS functional languages, squarely on-mission for a web
studio, each get the full vertical:

- **Elm**: `elm.json` ProjectKind; IGNITION serves `elm reactor` (the
  framework's own dev server), FORGE `elm make src/Main.elm`, VERITAS
  `elm-test`; elm-language-server entry; module/type/annotation outline;
  a learning space on `elm repl` (the REPL that prints types beside
  values) with a real elm.json + Main.elm sample.
- **ReScript**: `rescript.json` + legacy `bsconfig.json` kinds;
  `rescript build`/`clean` lanes; rescript-language-server entry; rides
  the generic brace outline. No standard test runner — the action greys
  honestly.
- **PureScript**: `spago.yaml`/`spago.dhall` kinds; spago
  run/build/test/install lanes; purescript-language-server entry; rides
  the Haskell-family outline.
- **Detection honesty**: these projects almost always sit beside a
  package.json, so NODE outranks them in primary detection (the
  WEBPACK-family rule, test-pinned) while detectKinds still lists them
  for ROSETTA and explicit targets speak their toolchains.
- ELM/RESCRIPT dependencies live in package.json — CRATE's Node lane
  (npm/yarn/pnpm detection) already covers them; PureScript gets real
  `spago install`/`upgrade` verbs.
- Doctor probes elm/spago/purs (68 tools); grammars pinned by sha256
  (elm-tooling, rescript-lang, nwolverson — MIT). 55 grammars, 59 spaces.

## [1.69.0] - 2026-07-13

### The indie stacks — Julia completed, Nim, D, and Racket first-class

The awesome-but-niche languages get the full-vertical treatment, and a
half-shipped one gets finished:

- **Julia, completed.** The grammar/outline/LSP/learning-space half shipped
  long ago; now `Project.toml`/`JuliaProject.toml` is a ProjectKind, and
  every AUTO lane speaks Pkg: CRATE `Pkg.instantiate()` (update →
  `Pkg.update()`, outdated → `Pkg.status(outdated=true)`), FORGE
  `Pkg.precompile()`, VERITAS `Pkg.test()`. Run greys out honestly — a Julia
  package has no standard entry point (IGNITION's julia target runs
  `main.jl` when the script convention is present).
- **Nim.** `*.nimble` glob detection (root or one level down, the .NET
  idiom); pinned grammar + CSL + `#` comment toggle + keywords + spellcheck;
  `nimlangserver` LSP entry; nimble run/build/test/install lanes everywhere
  (IGNITION/FORGE/VERITAS/CRATE, IDE actions, ROSETTA); an outline
  (procs/funcs/types); and a learning space driven by `nim secret` — the
  compiler's built-in interactive VM.
- **D.** `dub.json`/`dub.sdl` ProjectKind; pinned grammar (D rides the
  generic brace outline); `serve-d` LSP entry; dub run/build/test lanes,
  install = `dub upgrade --missing-only` (fetches missing deps without
  moving pins). No learning space: D has no standard REPL — recorded
  honestly rather than faked.
- **Racket.** `info.rkt` ProjectKind; pinned grammar + `;` comments +
  keywords; `racket-langserver` entry; racket/raco lanes (run `main.rkt`,
  build `raco make`, test `raco test .`, deps `raco pkg install --auto`);
  a lisp-family outline (defines/structs/modules); and a learning space
  on `racket -i` (the force-interactive law).
- **Environment Doctor** probes julia/nim/nimble/dub/racket (65 tools).
- Grammar provenance pinned by sha256 in NOTICE-grammars.md
  (nim-lang/vscode-nim, Pure-D/code-d, Eugleo/magic-racket — all MIT).

Detection, lane, and IDE-action coverage for all four stacks ride the
parameterized suites; nim-glob and julia-lane mutations fail named tests.
Odin was considered and skipped honestly: no manifest file exists to
detect. 58 learning spaces; 52 grammars.

## [1.68.0] - 2026-07-13

### Framework learning spaces — Astro, SvelteKit, Nuxt (56 spaces)

The consoles that shipped today get their tutorials: three new FRAMEWORK
learning spaces, each generating a real minimal app (filesystem routes,
the framework's signature idea in working code) with a tutorial that
teaches the core concept and points at the matching rack console —
COSMOS for Astro, KINETIC for SvelteKit, NIMBUS for Nuxt. Same run-driver
shape as the Next.js space (`npm run dev` serves the sample), same honest
install hints. The catalog grows 53 → 56; community drop-ins unchanged.

## [1.67.1] - 2026-07-13

### Modern Web preset

One click to a wired Vite-era rack: VELOCITY serves (URL → SCOPE, READY
pops the browser) while REFLEX fans saves into VERITAS tests and PURITY
lint, both onto MONITOR. The discoverability capstone for the completed
framework-console family — plus the family's first full E2E validation:
COSMOS driven live against a real Astro 4 project (registry version
currency flagging MAJOR!, trust gate, real `astro dev`, URL → ⇄ serving
chip, STOP → honest duration + deregistration).

## [1.67.0] - 2026-07-13

### The meta-framework consoles — KINETIC (SvelteKit) + NIMBUS (Nuxt)

The framework family is complete: every dominant modern web stack now has
a version-aware console. Angular (HALO), Next.js (NEXUS), Vite (VELOCITY),
Astro (COSMOS), Phoenix, Laravel (ARTISAN) — and now:

- **KINETIC — the SvelteKit console (49th device).** SvelteKit rides
  Vite's CLI, so DEV/BUILD/PREVIEW speak `vite` from the kit project while
  DIAG runs `svelte-check` (component type/a11y diagnostics). Version
  currency tracks `@sveltejs/kit` against the registry.
- **NIMBUS — the Nuxt console (50th device).** The Vue meta-framework
  through its `nuxi` CLI: DEV (URL out → SCOPE, READY/SERVING), BUILD,
  PREVIEW, and DIAG (`nuxi typecheck`). Version currency tracks `nuxt`.

Both carry the serving-deregister-on-stop contract from the start
(v1.65.1's lesson), FRAMEWORKS palette, CI-step capable, devices.md
regenerated (50 devices).

## [1.66.0] - 2026-07-13

### COSMOS — the Astro console (48th device)

Astro is the content-first framework for docs sites, blogs, and marketing
pages — its own CLI, its own dev/build/preview loop. It joins VELOCITY
(Vite) and NEXUS (Next.js) as a version-aware framework console:

- **DEV** serves the site; the local URL out feeds SCOPE, with READY + a
  SERVING gate. **BUILD** compiles the static site; **PREVIEW** serves the
  build; **DIAG** runs `astro check` (the type/content diagnostics).
- **Version currency**: installed `astro` tracked against the npm registry,
  nagging (amber → blinking MAJOR!) when the framework moves.
- FRAMEWORKS palette, CI-step capable, `onFinished` drops the serving
  registration on stop (the parity VELOCITY gained in v1.65.1, built in
  from the start here). FrameworkDeviceTest + ServingDevicesTest coverage;
  devices.md regenerated (48 devices).

## [1.65.1] - 2026-07-13

### The file tree survives a tab close/reopen

A review of the v1.64.0 platform tree found a latent bug (present in the
old hand-rolled tree too, now testable through the RootResolver seam):
`dispose()` — called on Project Studio's `componentClosed` — stopped the
panel's `RequestProcessor`. But the studio is `PERSISTENCE_ALWAYS` and
reuses the one panel instance, so reopening the tab posted the root
resolve to a permanently stopped RP: the tree stuck at "No project".
Fixed by keeping the scanner and the self-owned selection relay alive
across the panel's lifetime (the RP idles to zero threads; the relay
listens on the panel's own ExplorerManager — no external leak). Reopen
regression test, mutation-proven against the old `scanner.stop()`.

Also caught in the same review: VELOCITY (v1.65.0) had no `onFinished`,
so stopping the Vite dev server left a phantom serving-registry entry (⇄
chip, ⌘I Live Servers, VITALS/BEACON auto-target) and the SERVING gate
stuck high. Added the deregister/gate-drop/re-announce-reset that NEXUS
already had.

## [1.65.0] - 2026-07-13

### VELOCITY — the Vite console (47th device)

Vite is the dev server and bundler under most modern React/Vue/Svelte/
Solid SPAs — more widely used than any single meta-framework. It gets its
own console now, mirroring NEXUS-for-Next.js and HALO-for-Angular:

- **DEV** serves the app; the local URL out feeds SCOPE, with READY and a
  SERVING gate.
- **BUILD** compiles the production bundle; **PREVIEW** serves that built
  `dist`.
- **Version currency**: the installed `vite` is tracked against the npm
  registry, and the cluster nags (amber, then blinking MAJOR!) when the
  tool moves.

Registered in the FRAMEWORKS palette, CI-step capable (its BUILD exports to
a workflow step), auto-covered by DeviceContractTest and the generated
devices.md. IGNITION still serves Vite generically for the un-opinionated
case; VELOCITY is the version-aware console for a Vite-first project.

## [1.64.0] - 2026-07-13

### The file tree is a platform citizen (ledger 36 closed)

Project Studio's file tree was the last hand-rolled JTree over
`java.io.File` — the v1.39.0 review took the correctness half (CRUD
through DataObject) but left the UI custom because a rewrite carried
visual-regression risk against a careful tree. That risk is now retired
by live verification.

- **Real file-type icons** from the DataObject loaders — the JS badge on
  `server.js`, the Markdown badge on `README.md`, distinct folder/file
  glyphs — where the old renderer drew one generic icon for everything.
- **Git branch annotation** on the folder node (`orders-api [main]`),
  free from the platform.
- **The full node context menu**: Open, Cut, Copy, Delete, Rename,
  Tools, Properties — a *superset* of the old menu (Cut/Copy are new),
  driven by the DataObject's cookies. Live-verified: Open opens the file
  in the editor through the filter node.
- **Lazy, off-EDT child loading** is the platform's own machinery now,
  with a "Please wait…" row — no hand-written background walk.
- **~230 fewer lines.**

Every law the old tree earned is preserved, each with its incident:
the root resolve runs OFF the EDT (the v1.33.1 TCC storm — a re-aim
during a slow resolve never clobbers the newer aim, test-pinned);
heavy directories (`node_modules`/`.git`/`dist`/`build`/`coverage`)
render **childless** — no disclosure triangle, a stronger "you cannot
enter" signal than the old grey text, and the guarantee a 100k-file
generated tree is never enumerated by misclick; external edits arrive
via `FileUtil.refreshFor` off the watcher, keeping expansion state.
`FileTreePanel` is now an `ExplorerManager.Provider` over a
`BeanTreeView`; the selection still feeds the aim publisher (ledger 29).

## [1.63.3] - 2026-07-13

### The night's last polish

- **Monorepo Lanes racks WAYPOINT beside ROSETTA** — the workspaces
  conductor is discoverable where monorepo users already look
  (live-verified: both conductors mount, the package count reads true;
  a no-op on non-workspace repos).
- **The wizard's default location skips ~/.nmox internal homes**
  (journey finding: creating a learning space made ~/.nmox/learn the
  wizard default). Internal recents fall through to the next real
  location or the ~/NMOX workspace. Seam-extracted, mutation-proven.

## [1.63.2] - 2026-07-13

### CI export speaks forward slashes on every OS

The overnight review pass added a WAYPOINT × CI-export composition pin,
and the windows lane promptly failed it — exposing a pre-existing bug:
`relativize().toString()` is backslash-separated on Windows, so an
exported workflow said `working-directory: packages\web`, broken on the
ubuntu runners it targets. Exports now normalize to forward slashes.
Also in this patch: the composition pin itself (a steered lane exports
its working-directory honestly) and the review's written blessings for
the rest of the overnight surface.

## [1.63.1] - 2026-07-13

### Saved selections survive reload

A WAYPOINT review question exposed a latent bug as old as NPM-9000:
RackIO applies a patch's saved state immediately after addDevice, while
dynamic knobs (SCRIPT, WORKSPACE) are still loading their options — so
`selectOption` found nothing and every saved dynamic selection silently
reset to position 0 on reload. The knob now remembers the by-name wish
(`pendingSelection`) and honors it when the options arrive; an explicit
user dial supersedes it. One fix in the widget heals NPM-9000, WAYPOINT,
and every future dynamic knob. Patch-load-order regression test +
mutation proof (forgetting the wish fails it).

## [1.63.0] - 2026-07-13

### Workspaces — ROSETTA one level down

JS monorepos get their conductor: **WAYPOINT** (the 46th device,
UTILITY shelf), the workspace selector.

- **`Workspaces`** pure core: the union of package.json `"workspaces"`
  globs (array or `{packages:[...]}` form) and pnpm-workspace.yaml's
  `packages:` list, resolved to real package dirs — a deliberate glob
  subset (`*` one segment, `**` bounded depth 3, exclusions skipped,
  node_modules never entered, capped at 64) because the rack steers
  lanes, it does not re-implement npm. Names come from each package's
  own manifest; collisions disambiguate with the dir name.
- **WAYPOINT** dials a package and the Node lanes follow: NPM-9000's
  SCRIPT knob lists that package's scripts and runs there; PURITY,
  GLOSS, VERITAS and every base lane re-root through ONE choke point
  (`CommandDevice.commandDir`). `root` restores the old behavior
  exactly. Non-Node lanes are untouched — a ROSETTA-dialed cargo lane
  keeps its Cargo.toml directory (test-pinned).
- The knob persists **by name** (package positions shift as monorepos
  grow), the package set reloads on package.json/pnpm-workspace.yaml
  saves (`pnpm-workspace.yaml` joined ManifestPulse — 19 names),
  removing the device stops the steering (the ROSETTA dispose law),
  and undo re-attach re-applies it (onAttached).

Mutation-proven ×3: deleting the commandDir consult, the pnpm parse,
or the dispose-clear each fails a named test. The base-lane proof
exists because the first mutation run did NOT bite — NPM-9000's own
override was hiding the untested base path.

## [1.62.0] - 2026-07-13

### The journey polish

From tonight's senior-web-dev journey walk (create → install → serve →
browser → stop, live on the real app):

- **One workspace**: the New Project wizard's first-run default now
  lands in `~/NMOX` — the same workspace fresh launches aim at and Open
  Folder starts in — instead of inventing a second `~/NMOXProjects`
  home. Existing users keep their most-recent location.
- **Wizard install is manager-aware** (the v1.60.0 detection) and its
  pre-trust execution carries its written blessing: it runs code the
  product itself just wrote from its own template at the user's
  explicit request — WorkspaceTrust guards other people's code.

Also attempted, live-tested, and honestly pulled: the Tailwind CSS
language server. The platform's LSP client binds multiple servers per
mime (verified in bytecode) and the server started and detected the
project — but it requires `client/registerCapability`, which the
platform client does not implement (UnsupportedOperationException), so
completions never arrive. Recorded as ledger 45 with the evidence and
the restore path; re-test on each platform bump.

## [1.61.0] - 2026-07-13

### Biome speaks for the project

The v1.60.0 rule — run the project's own toolchain — applied to lint and
format. A `biome.json`/`biome.jsonc` opts the project into Biome, and the
rack listens:

- **PURITY** gains a `auto` LINTER position (appended per the knob-index
  law; new devices default to it): auto lints with
  `npx @biomejs/biome lint .` when the project carries a biome config,
  eslint otherwise. FIX honestly spells `--write` for biome, `--fix` for
  the others. Biome's block headers land on the DiagnosticsBus (squiggles
  + Action Items rows labeled `[biome]`) and its `Found N errors/warnings`
  summary drives the E:/W: LCD.
- **GLOSS** formats with `biome format` (WRITE) / `biome format` check
  mode when biome is present; prettier otherwise; PHP/Foundry lanes
  untouched.
- **PREFLIGHT** counts biome.json as a lint config.
- **Environment Doctor** probes biome (60 tools).

Mutation-proven: deleting the detection fails four tests; deleting the
summary parse fails the output test.

## [1.60.0] - 2026-07-13

### The right package manager

A senior web dev's project says which Node package manager it uses — and
now every AUTO lane listens. Running npm in a pnpm or yarn repo writes a
second lockfile and a broken node_modules; the IDE will never do that again.

- **`ProjectInspector.nodePackageManager`** — the one canonical detection:
  the corepack `"packageManager"` pin in package.json wins (it is the
  project's explicit contract), then `pnpm-lock.yaml`, then `yarn.lock`,
  then npm. Monorepo-aware (resolves the Node lane's directory), cached
  behind the existing package.json mtime cache. An unknown pin (e.g. a
  future manager) falls through to the lockfile instead of failing.
- **CRATE (AUTO)** installs/updates/outdated with the detected manager —
  including the `yarn upgrade` verb spelling. Re-syncs when
  `pnpm-lock.yaml`/`yarn.lock` change (both joined the ManifestPulse set,
  now 18 names).
- **NPM-9000** gains an `auto` ENGINE position (appended, not inserted —
  knob indices persist in saved patches, the v1.59.0 law) and new devices
  default to it; a patch that pinned npm/yarn/pnpm keeps its engine.
- **The IDE's native Run/Build/Test/Clean** (F6 and friends) speak the
  detected manager: `yarn run dev`, `pnpm test`, …
- **NPM Explorer / NpmService** delegate to the same detection (its old
  lockfile-only version missed the corepack pin and was consulted only by
  the Explorer's command path).
- **Environment Doctor** probes pnpm and yarn (59 tools).

All mutation-proven: hardcoding npm back into any consumer fails a named
test; deleting the corepack branch fails two.

## [1.59.0] — 2026-07-12

The expansion release: Gleam joins as a full first-class stack, the
Environment Doctor learns every toolchain the IDE already drives, and
the docs finally tell the truth about how much the polyglot layer
covers.

### Added
- **Gleam, first-class end to end**: `gleam.toml` is a project manifest
  (`ProjectKind.GLEAM`, BEAM family), the pinned TextMate grammar
  (gleam-lang/vscode-gleam, sha256 in NOTICE-grammars.md) highlights
  `.gleam` with comment toggling, keyword completion, and spellcheck
  hygiene; `gleam lsp` (built into the compiler) wires as the language
  server; every rack lane speaks it — IGNITION `gleam run`, FORGE
  `gleam build`, VERITAS `gleam test`, CRATE `gleam deps download`/
  `update` — and the IDE-native Run/Build/Test/Clean map to the same
  argv. A 53rd learning space ships (the honest kind: `gleam shell`
  compiles the generated sample and drops into the Erlang shell with
  your modules loaded, and the tutorial says so).
- **Environment Doctor probes 10 more toolchains** it could already
  drive but never checked: gleam, dotnet, dart, zig, sbt, stack, dune,
  crystal, swift, gradle — each with the right version dialect (zig
  rejects `--version`) and an install hint.

### Fixed — documentation truth
The docs claimed "Bun/Deno/Rust/Go/BEAM+/PHP toolchains" while the
product has quietly shipped detection, device lanes, grammars, and LSP
entries for **.NET/C#, F#, Dart, Zig, Scala, Haskell, OCaml, and
Crystal** for many releases. The stack lists now say what ships.

## [1.58.0] — 2026-07-12

Plugin ecosystem groundwork plus two bounded security/UX fixes.

### Added
- **NBM signing pipeline** (the credibility gap in the plugin story). A
  `sign-nbms` profile in the root pom activates on `-Dnbm.keystore=<path>`
  and hands the keystore to the nbm-maven-plugin; the release workflow
  decodes a base64 keystore secret and signs every module NBM. **Off by
  default** — no secret means unsigned NBMs exactly as before — turned on
  by adding three repository secrets. The mechanism is jarsigner-verified
  (a throwaway keystore produces a `jar verified.` NBM). Setup in
  [docs/engineering/nbm-signing.md](docs/engineering/nbm-signing.md).
- **`examples/uptime-device/`** — the worked Device SPI plugin from the
  docs, now a committed, buildable Maven project (the exact device
  installed live to validate the SPI in v1.55.0) instead of an inline-only
  snippet. `docs/device-spi.md` points at it as a starting point.

### Fixed
- **ledger 43 — `gitdir:` pointer confinement.** A crafted `.git` *file*
  could aim `GitFacts` at any directory and turn the branch chip into a
  narrow "does this dir's HEAD look like a ref" oracle. The resolved gitdir
  is now canonicalized (killing `../` and symlink games) and required to
  live inside a `.git` directory — worktrees and submodules still resolve,
  arbitrary paths are refused.
- **ledger 44 — no dead-click resume for a missing device.** If a plugin
  device was live at a crash and its plugin was uninstalled before restart,
  its slot's `MissingDevice` matched by type id and "Resume last session?"
  offered a no-op. A placeholder can never resume anything, so it no longer
  matches.



The threading tail. The last gap in the "never freezes" promise, closed
with its own focused release and live verification — plus the small EDT
touches and CI flake the v1.56.0 review deferred.

### Fixed
- **Every device's RUN button now launches off the EDT (ledger 41).**
  `RackDevice.exec` read `.env` files and called `ProcessBuilder.start()`
  synchronously on the caller — so on a wedged or network-mounted project
  dir the fork froze the UI on the exact gesture the rack exists for.
  Now the dotenv loads and the fork ride a RequestProcessor lane while a
  synchronous `PendingHandle` keeps the entire observable contract
  unchanged: `isProcessRunning()`/`isLive()` answer true the instant
  `exec` returns (so a readiness gate can't double-launch), a second
  `exec` cancels the first, stop-before-spawn means no process is ever
  created, `panic()` stays bounded on an unspawned run, and the exit
  callback fires exactly once in every phase. All 46 devices and the SPI
  host inherit it. Live-verified: a real SOLDER `echo` ran to a green OK
  with the UI responsive and the trust prompt firing correctly.
- **Three small EDT touches from the v1.56 review:** the New Learning
  Space picker scans `~/.nmox/learn-catalog.d` off the EDT; the rack's
  Save Patch write moved to a lane (the last workspace writer the v1.44
  SaveLane sweep left on the EDT); ORACLE's keychain peek moved off the
  EDT so an unlock prompt can't stall the button.
- **The Windows `JsDebugServerTest` `@TempDir` flake** (ledger 37/38
  class): the test now manages its own temp dir with a best-effort
  retry-delete, so a still-releasing Windows file handle after the
  js-debug node process dies is waited out, not fatal.



The third senior review. Five read-only lenses over everything shipped
since the v1.39.0 idiom pass (sixteen releases: the git chip, the a11y
widget layer, browser debugging, async-stop/SaveLane, the OpenProjects
bridge, soft-dependency facades, spec versions, the update center,
ORACLE, the drop-in catalog, and the frozen Device SPI). The house laws
held — concurrency, the keyring idiom, update-center pinning, ORACLE
secret handling, SPI exec/port/serving isolation all traced clean — and
what the lenses proved got fixed, with the SPI corrections landing while
the API is a day old and has no external consumers.

### Fixed — security
- **Cloud API tokens no longer touch plaintext preferences.** The Tools ▸
  Options "Rack & Cloud" panel — the primary token-entry UI — wrote
  DigitalOcean/Hetzner/Cloudflare tokens to `NbPreferences` node
  `nmox/cloud`, the one keyring bypass the v1.36 sweep missed. They now
  go to the OS keychain (off the EDT) under the same `nmox.cloud.*`
  scheme the infra designer reads, and the password buffer is zeroed
  after use. Source-gate-pinned against any plaintext path.

### Fixed — the Device SPI (additive corrections to the frozen contract)
- **Undo now revives a plugin.** `DeviceLogic` gained `onAttached(services)`
  (a `default`, so additive to the frozen API), which the host calls on
  first mount and on undo re-attach of the same instance — so a plugin can
  restart a poll/clock or re-announce a serving URL a removal tore down.
  This closes the v1.50 TAIL/TEMPO re-attach bug class for third parties;
  mutation-proven.
- **A throwing plugin can no longer break the shelf.** `build()` exceptions
  were unguarded on the palette double-click and Quick Search mount paths
  (the drag-drop path was already guarded) — a lawful-descriptor plugin
  whose `build()` threw, or a stale entry whose module was just
  uninstalled, put an uncaught exception on the EDT. All three mount paths
  now degrade to a status message.
- **`onDispose()`'s contract is now true.** The host stops the device's
  process before calling `onDispose()`, matching the javadoc (was: after);
  mutation-proven with a live process.
- **Port count is capped at validation.** A descriptor with more jacks than
  the back panel holds (~8/side) is refused with a named reason instead of
  passing validation and then painting jacks off-plate (contract law #2).

### Fixed — coherence & idioms
- **The two update notifications converge.** The daily release heads-up now
  opens the in-app Plugin Manager — the same destination the platform's
  weekly autoupdate check uses — instead of a web download page, so a user
  never gets two contradictory update procedures for one release. Web page
  survives as the fallback when the action can't be resolved.
- **"Open as project" scans off the EDT.** Project Studio's Projects-tab
  button ran `ProjectManager.findProject` (a manifest disk scan) on the
  EDT, contradicting the codebase's own bridge rule; now on a
  RequestProcessor.

### Fixed — documentation truth
- Three stale device counts corrected to 45 (README ×2, user-guide); the
  `-proc:full` note in device-spi.md corrected to JDK 23+ (matches the
  pom); CLAUDE.md's core row now names the Device SPI; ledger 31 refreshed
  now that the SDK story shipped; the REPL INSTALL "curated data, not user
  input" comment corrected for community drop-ins; the SPI's
  frozen-in-lockstep enum constraint documented in package-info.

### Build/CI robustness
- The test JVM now gets the product's own `java.base` `--add-opens`
  (nmoxstudio.conf ships them): platform code (NbPreferences/IOProvider)
  reflects into `java.base` to install URL stream handlers, and on JDK 21
  a fork that reached that init first threw `InaccessibleObjectException`.
- De-flaked `ProcessSupportTest.shouldKillGrandchildHoldingPipeOnTimeout`:
  the orphan-reap poll now waits 15s (was 3s) — `destroyForcibly` is
  async and the guarantee is eventual, so the window must outlast
  full-reactor CPU contention, not measure it.

### Deferred with reasons (ledger 41–44)
The systemic EDT `exec`/dotenv fork (pre-existing, all 46 devices, its own
release), third-party code at session restore (accepted — restore
instantiates its patch; exec stays trust-gated), the bounded `gitdir:`
disclosure, and a MissingDevice resume dead-click edge.

## [1.55.0] — 2026-07-12

The Device SPI. Third parties can now write rack devices — the single
largest net-new lever in the plan, shipped as the declarative contract
the design dossier chose: plugins describe, the Studio hosts, and the
house laws are enforced by construction.

### Added
- **`org.nmox.studio.core.spi.device`** — frozen public API (additive-only
  evolution): `DeviceExtension` (register with `@ServiceProvider`),
  `DeviceDescriptor` (reverse-DNS id, shelf category, how-to card, typed
  ports), `DeviceFace` (the faceplate builder: knob/button/toggle/led/
  lcd/vu — buttons declare GO/STOP/MUTATE/QUERY roles so the color law
  is unviolatable, labels are mandatory so the accessibility law is too,
  the first GO/STOP take the shared transport columns, and a face that
  overflows its units fails at mount naming the control), `DeviceLogic`
  (signal/project/dispose callbacks), and `DeviceServices` (trust-gated
  `exec` riding the monitor bus + flight recorder + orphan guarantee,
  `emit*` onto declared jacks only, the Serving Registry).
- **The rack hosts extensions** as first-class devices: palette shelf,
  Quick Search, patch persistence, undo, Stop All, panic — identical to
  the built-in 45. Law-breaking descriptors are skipped with a logged
  note naming the offender (the learn-catalog.d idiom); an uninstalled
  plugin's devices survive in patches as MISSING placeholders (v1.54.0)
  until the plugin returns.
- **`docs/device-spi.md`** — the laws, the threading contract, and a
  complete worked plugin (pom + device).

### Verified live, end to end
An out-of-tree Maven project compiled against the SPI, packaged as an
NBM (generated dependency `core > 1.55.0` — refused by the loader on
older installs), installed through Tools ▸ Plugins with no restart,
appeared on the shelf, mounted, and its CHECK press hit the HOST's
Workspace Trust prompt before `uptime` ran onto its LCD. The trust
gate, descriptor validation, and Lookup merge are each mutation-proven;
the contract tests parameterize over the catalog, so a fixture extension
is held to all eight device laws in every build.

## [1.54.0] — 2026-07-12

The SPI pre-work release. The Device SPI decision is made — a small
declarative contract, hosted by the rack, laws enforced by construction
(Option B; the design dossier chose it over freezing the organically
grown RackDevice class). Before that door opens, this release fixes
everything that silently assumed the device catalog was a closed world.
Each fix is a real bug today, plugin or no plugin.

### Fixed
- **Unknown devices no longer corrupt patches.** `.nmoxrack.json` stores
  cables by device index, and a type id nothing answers (a plugin device
  not installed here, or a newer built-in opened in an older Studio) was
  silently dropped on load — shifting every later index so cables
  re-attached to the *wrong devices*. Unknown types now keep their slot
  as an honest `MISSING` placeholder that preserves the stranger's type
  id, saved state, and cables verbatim: the patch round-trips losslessly
  and the harness stays on the right devices. Mutation-proven (the old
  drop-the-stranger behavior fails the new tests).
- **Two same-title devices no longer merge into one phantom.** The
  Output tab, monitor bus, and flight recorder all keyed a run on the
  device's *title*, so two SOLDERs corrupted each other's launch/exit
  pairing, duration statistics, BLACKBOX slow-creep alarm, and ORACLE's
  failure context. Devices now carry a unique bus name — the first
  instance keeps the bare title (existing journals stay continuous),
  later same-title instances get " ·2", " ·3"…, assigned once at first
  attach so undo re-attach never renames a running lane.

### Changed
- **One device registry.** New `DeviceCatalog` — the palette, patch
  persistence, Quick Search, the how-to card, and CI export all consult
  it instead of reaching into the `DeviceType` enum (whose three
  `byId()` call sites each handled a miss differently). Today it mirrors
  the enum exactly (test-pinned, member for member); when the device SPI
  ships, extension devices merge in here and every consumer learns about
  them for free.
- **CI-step capability lives in the catalog.** The workflow exporter
  carried its own hardcoded set of 14 type-id strings deciding which
  devices export as CI steps; that knowledge moved to the catalog
  (`DeviceType.isCiStep()`), pinned by an exact-set test.
- **The device contract tests run over the catalog, not the enum** — a
  registry-contributed device is held to the same eight laws (unique
  labeled ports, state round-trip, faceplate fit, port lexicon,
  accessible names, shelf guidance) as a built-in.

## [1.53.0] — 2026-07-12

Community Learning Spaces. The 52 built-in tutorials have always been
data-driven JSON; now anyone can add their own.

### Added
- **`~/.nmox/learn-catalog.d/` drop-in catalog.** Every `*.json` file in
  that directory contributes learning spaces to the New Learning Space
  picker, merged with the built-ins. A drop-in space with the same `slug`
  as a built-in **overrides** it (improve a shipped tutorial without
  forking); new slugs append. Schema is exactly the built-in catalog's,
  documented with a worked example in `docs/learning-spaces.md`.
- Malformed files are skipped whole with a logged warning and a
  status-line note naming the file — one bad drop-in never blocks the
  picker or its neighbours. The directory is read lazily when the picker
  opens (never at boot), cached against a path+mtime+size fingerprint so
  an edit shows on the next open without a restart while an unchanged dir
  costs one listing.

## [1.52.0] — 2026-07-12

ORACLE — AI assistance through the rack's metaphor. The 45th device
explains the error currently on the MONITOR bus: visible, wired,
unpluggable, not a chat sidebar (plan.md's one remaining AI direction,
now chosen). It is the BLACKBOX shape — a FlightRecorder consumer with a
QUERY-blue button and a modeless popup viewer — not a command device.

### Added
- **ORACLE device** (`rack`, OBSERVE category). **EXPLAIN** (QUERY-blue,
  read-only per the color law) reads the last failed run off
  `FlightRecorder.last()` where `kind == EXIT_FAIL` — the command, exit
  code, up to five sampled error lines, the device and project name — and
  asks the Anthropic Messages API (`POST /v1/messages`, `claude-haiku-4-5`
  default, `claude-sonnet-5` on the **MODEL** knob) what went wrong and
  how to fix it. A short **verdict** lands on a multi-line LCD; **VIEW**
  opens the full answer in a scrollable window; a **THINK** LED blinks
  while it consults. Nothing in the project is mutated.
- **`OracleClient`** — plain HTTPS+JSON over the shared `HttpClientFactory`
  behind a `Transport` seam (the `JsonRpcClient` idiom): prompt assembly is
  a pure `FailureContext → String` function and response parsing is
  socket-free unit-tested. The API key travels only in the `x-api-key`
  header — never the URL (a fixed HTTPS constant) or the body — and is
  never logged, thrown, or echoed.
- **`OracleKeys`** — the API key in the OS keychain only (the
  `RpcSecrets`/`Passwords` idiom, `nmox.oracle.apikey`), with a `keyringUsable`
  test seam, an in-memory fallback, a warn-once balloon, and an environment
  fallback chain: keychain → `ANTHROPIC_API_KEY` → `CLAUDE_API_KEY` (first
  non-blank wins). A faceplate **KEY…** button sets it via a `JPasswordField`
  dialog (never the plaintext-echoing LCD editor).
- **`OracleConsent`** — ORACLE's own one-time consent for the outward data
  flow. WorkspaceTrust is an *inward* execution guard; sending a failed
  run's output to an external API is an *outward* flow it does not cover.
  The first EXPLAIN asks once, spelling out exactly what is sent (command,
  exit code, ≤5 error lines, device, project name) and what is not (no
  source, no environment, no secrets); the grant is a preference
  (`java.util.prefs`, the WorkspaceTrust mechanism), headless auto-allow
  with no persistence.

### Laws held
- **Zero boot cost** — attach registers only a FlightRecorder change-listener;
  no keyring read, no network, no HTTP warm-up until the first EXPLAIN.
- **No network without a button press** — `consult` is the single path to
  the API; the key gate and consent gate are both mutation-proven (removing
  either lets a spy transport catch the attempt).
- **Secrets Keyring-only**, **DialogDisplayer never JOptionPane**, listeners
  symmetric (change-listener added in `onAttached`, removed in `dispose`).
- Honest degradation for every failure state — no key, no consent, nothing
  to explain, offline, refusal — each an honest LCD line, never a throw.

### Tests
- `OracleClientTest` (14): prompt assembly asserted verbatim, request
  envelope, response parse (text/refusal/error/empty/non-JSON), the whole
  call over a canned transport, `FailureContext.fromRecorder` reconstruction.
- `OracleKeysTest` (9): in-memory round-trip, the env fallback chain and its
  order, stored-wins-over-env, blank-env-ignored — all through the `env` seam,
  never the real environment.
- `OracleConsentTest` (4): default-ungranted, grant/revoke, headless
  auto-allow-without-persist.
- `OracleDeviceTest` (6): the key and consent gates (mutation-proven),
  nothing-to-explain, happy path, offline degradation, SONNET model selection.
- `DeviceContractTest`/`DeviceDocsTest` auto-cover ORACLE (ports,
  state round-trip, accessible names, usage recipe); `docs/devices.md`
  regenerated. rack 902 tests; SpotBugs/find-sec-bugs clean on the new
  HTTP/secret code; coverage floor met.

## [1.51.0] — 2026-07-12

The update center: in-app updates fed from GitHub releases (tech-debt
#21). The platform's Plugins infrastructure (autoupdate services/ui/cli)
always shipped in our cluster; now it has something to read.

### Added
- **"NMOX Studio Updates" update center**, registered in `ui/layer.xml`
  (the classic `Services/AutoupdateType` + `AutoupdateCatalogFactory`
  shape, attribute names verified against the shipped
  autoupdate-services jar). Tools ▸ Plugins now lists the product
  modules and offers updates whenever a newer release is published;
  Help ▸ Check for Updates works too. The catalog URL rides GitHub's
  stable `releases/latest/download/updates.xml` redirect, so shipped
  apps follow every future release with no code change.
- **Release assets: `updates.xml`(+`.gz`) and the 11 module NBMs.** The
  linux release lane runs the new `scripts/build-update-site.sh`
  (`nbm:autoupdate`), which pins every NBM inside the catalog to its
  own release tag by ABSOLUTE URL — the platform resolves relative
  distribution URLs against the pre-redirect `/latest/` catalog URL, so
  relative ones would let a cached older catalog download newer NBM
  bytes and fail its own SHA-512 digests. The script gates the module
  set (exactly the 11 product modules; the never-shipped sample
  template is pruned — the aggregator ignores `-pl` and its include
  filter only applies to nbm-application projects, both verified
  against the 14.5 mojo). The six existing asset names are untouched.
- **NBMs now embed the MIT license** (root pom `licenseName`/
  `licenseFile`), so the Plugin Manager's install dialog shows real
  terms instead of "License terms: Unknown".
- `UpdateCenterTest` (ui, 5 tests): registration shape + exact catalog
  URL, Bundle display name, workflow ships the catalog + NBMs
  (line-anchored after a mutation showed `updates.xml.gz` masks a
  substring check), script pins absolute URLs + gates the module set,
  and a catalog-shape check (11 modules, sample absent, spec versions
  match the root pom) that runs whenever a local site exists. URL and
  workflow mutations proven to fail.

### Notes
- No fetch at boot: the registration is inert layer XML. The platform
  checks on user action or its own schedule — default EVERY_WEEK,
  first evaluated ~500 ms after the UI is ready (decompiled
  AutoupdateCheckScheduler; tunable in Plugins ▸ Settings). The
  zero-boot-spawns law is untouched.
- Dev builds carry real spec versions (root `<spec.version>`, bumped to
  1.51.0 for this sprint), so a same-version catalog offers nothing.
- NBMs remain unsigned (the UC is marked trusted — our own HTTPS
  channel); a signing keystore is a separate story. Installers remain
  the path for fresh installs; the update center updates the modules of
  an existing install.

## [1.50.0] — 2026-07-12

The housekeeping release: two long-deferred ledger items closed, each
sub-fix mutation-proven (a test that fails on today's code).

### Fixed
- **Rack undo bleeds across a preset/patch load** (tech-debt #19, the
  load-bearing sub-bug). Loading a preset or patch left the pre-load
  device removals and additions on the undo stack, so ⌘Z after a load
  could peel the just-loaded patch apart device by device and eventually
  resurrect the PREVIOUS patch's structure — undo edits that predate the
  current patch. Fixed at the single choke point every load routes
  through: `RackIO.fromJson` now clears the undo history after replacing
  the rack's contents, covering the Presets menu, the Load Patch button,
  and RackService's project-switch autoload alike (the project-switch
  no-patch case keeps its own clear in RackService). Proven by loading
  patch A, editing, loading preset B, and asserting ⌘Z can't cross B
  (removing the clear fails the test).
- **`lastTriggerAt` entries survive device removal** (tech-debt #19).
  Removing a device severed its cables in bulk but left their
  trigger-cooldown bookkeeping in the map for the life of the rack — a
  leak `disconnect()`/`removeCable()` avoided per cable. `removeDevice`
  now drops each dead cable's entry; undo re-adds the cable objects
  verbatim (no stale cooldown), so it's safe against re-attach. Proven
  by triggering a cable, removing its source device, and asserting the
  map no longer tracks it.
- **TAIL/TEMPO show stale displays after an undo re-attach** (tech-debt
  #19). Undo of a device removal re-attaches the SAME instance, but
  `dispose()` had stopped the follow poll / transport clock while leaving
  the FOLLOW switch, EYE led, CLOCK switch and tick LCD untouched — so
  the faceplate read "armed" while nothing ran. Both devices now re-run
  their display/timer sync from `onAttached()`, which fires on every
  (re-)attach (a no-op on a fresh, switch-off add). Proven by arming,
  removing, and undoing — the timer must be running again.

### Changed
- **org.json's version is one root property** (tech-debt #23). The eight
  module copies STAY — module classloaders make a shared wrapper
  ClassCastException territory (ledger 3) — but their version literal is
  now a single `<orgjson.version>` in the root pom that every module
  references, so Dependabot bumps all eight in one PR. Byte-verified: all
  eight still resolve to `org.json:json:20260522`. A new
  `OrgJsonVersionGateTest` fails if any module re-hardcodes a literal.

## [1.49.0] — 2026-07-12

Ledger 32 closed: rack tool findings reach the standard Action Items
window — and the squiggle half turned out to already be platform plumbing.

### Added
- **Rack tool findings in the Action Items window** (tech-debt #32). What
  PURITY (eslint) and TYPEGUARD (tsc/phpstan) publish on the
  DiagnosticsBus now also lands in the platform Task List: one
  `PushTaskScanner` ("Rack tool findings"), severity mapped onto the
  window's own error/warning groups, the tool name in the task text
  (matching the squiggle hover exactly), click-to-navigate for free —
  and, the piece squiggles can never show, findings in files no editor
  has open. Layer-registered so the framework instantiates it lazily on
  first scan: zero boot cost (the boot law holds), and the bus's
  late-subscriber replay catches a late-born scanner up. Replace-per-run
  semantics carried through exactly — a fresh run clears the files it no
  longer names (all-clear runs clear everything), and one tool going
  clean never erases another tool's rows on the same file
  (mutation-proven: deleting the old-batch union fails 3 tests).

### Changed
- Nothing in the squiggle path — the v1.49.0 recon found `RackSquiggler`
  already rendered via `HintsController` (the ledger item's "draws its
  own squiggles" overstated); it stays byte-identical, and a new
  source-gate pins both halves to the platform APIs so a bespoke
  renderer can't sneak back in. DiagnosticsBus stays: it is the
  transport, not the debt.

## [1.48.0] — 2026-07-11

The remainder sprint: the small honest remainders of ledger 34 (now fully
closed) and ledger 29 (now Kit actions only).

### Added
- **Project Studio's file tree publishes its selection** (ledger 29
  remainder, the highest-payoff piece). Selecting a file makes that
  file's DataObject node the window's activated node — per-file context
  (git Annotate via the chip, the platform's file verbs) finally sees a
  real file selection instead of just the aim directory. Clearing the
  selection falls back to the aim node — the v1.45.0 selection is
  refined, never emptied out. AimNodePublisher generalized from
  directories to any file: same off-EDT resolve, same equality guard,
  and a new pinned law — a distinct-target storm (arrow key held down
  the tree) on the busy lane resolves twice (in-flight + final), not N
  times. Hidden tabs still publish nothing (the v1.38.0 boot law,
  test-pinned).
- **NPM Explorer publishes the found project's node** (ledger 29
  remainder). The last studio window with an empty lookup now sets the
  Node project directory's node as its activated node while a project is
  found, and withdraws to a null opinion — deliberately not an empty
  array — when there is none, so the registry keeps the last real
  selection alive. Its hand-read registry fallback stays, with the why
  written in place: it serves aims with NO Node project (an editor file
  whose parent chain carries a package.json), a case our own publish
  cannot cover — and it now skips the node this window itself published,
  because consuming our own output would echo a stale project after a
  re-aim away from it (guard extracted as projectFromNodes, test-pinned
  both ways).
- **Contract Studio's artifact walk runs under a real ProgressHandle**
  (ledger 34, last sliver — the item is now fully closed). Both rescan
  paths route through one finally-guarded, indeterminate handle
  ("Scanning contract artifacts…"); no Cancellable on purpose — the walk
  is one uninterruptible Files.walk with no seam to abort, so a cancel
  button would be a lie (the v1.44.0 DB-connect/cloud-sync idiom,
  commented at the site). Source-gate-tested: a bare ArtifactScanner.scan
  call in the window fails the build.

### Notes
- Ledger: 34 closed fully; 29 reduced to context-sensitive Kit-action
  registrations only (deferred because focus-keyed enablement would
  disable them while the editor is focused — a UX regression
  masquerading as idiom).

## [1.47.0] — 2026-07-11

The spec-versions release: ledger 20 — module manifests say which release
they came from, and the module loader enforces it.

### Changed
- **Module spec versions track the product version** (ledger 20). Every
  module jar's `OpenIDE-Module-Specification-Version` is now the release
  version (1.47.0) instead of the pom-derived `1.0` frozen since v0.1.
  Mechanism: each module's `src/main/nbm/manifest.mf` declares
  `${spec.version}`, a root-pom resources execution interpolates the
  single `<spec.version>` property into `target/nbm-manifest/manifest.mf`,
  and nbm-maven-plugin's `<sourceManifestFile>` keeps that entry verbatim
  (its documented conditionally-add seam; the jar-plugin `manifestEntries`
  route was tried first and loses to the generated `<manifestFile>` on
  conflicting keys — tested). The reactor packages each module before its
  dependents' manifests generate, so inter-module dependencies now carry
  real ranges (`org.nmox.NMOX.Studio.core > 1.47.0`): a module jar dropped
  into an older install is refused by the module loader instead of
  throwing LinkageError at call time (the ledger-30 hardening).
- **The release workflow stamps `<spec.version>` from the tag** in the
  same three per-OS steps that stamp branding's `currentVersion` — the
  tag stays the single version source, no `versions:set`, asset names
  unchanged. Branding's committed dev value ("NMOX Studio 1.0") is
  untouched: `Versions.extract` reads 1.0 as a dev build and that keeps
  dev launches out of the update check.

### Added
- `SpecVersionGateTest` (application, 3 tests): all 11 shipped module
  manifests on the app's classpath equal the injected `${spec.version}`;
  every source manifest carries the literal placeholder, exactly once,
  never a hardcoded number — a future release cannot half-bump.

## [1.46.0] — 2026-07-11

The soft-dependency release: ledger 30 and 31 — the same surgery, both
halves. Optionality is now a lookup, not a caught classloader failure;
rack's exports are now first-party-only.

### Changed
- **Interface-in-Lookup replaces catch(LinkageError)** (ledger 30). Core
  exports `org.nmox.studio.core.spi` with two small facades — `ProjectAim`
  (projectDir / aim / recentProjects, projectChanged listeners, manifest
  listeners) and `LiveServings` (snapshot + coarse listeners) — and rack
  publishes thin @ServiceProvider adapters (RackProjectAim,
  RackLiveServings; pure delegation, listener wrappers mapped so
  add/remove stay symmetric and a double-add never double-delivers). 31
  catch sites across apiclient/web3/dbstudio/project/tools/infra/ui
  converted to find()-and-branch-on-null; absence = feature quietly off,
  behavior unchanged. ServingBridge, BaseUrlOffer and ChainAutoConnect are
  retyped to the facade — their storm tests ride a fake with the real
  registry's threading contract, still green.
- **apiclient, web3 and infra no longer depend on rack at all** — the
  Maven module dependency is gone; each pins that with a
  RackSoftDependencyTest (facade lookups null in the module's own test
  environment, rack classes not even loadable). dbstudio keeps the dep
  for FileWatcher/DockerClient, project for the Workbench's rack UI
  surface, tools for CommandExecutor/ProjectInspector — each kept catch
  now carries a KEPT/why comment. Catches guarding genuinely-optional
  PLATFORM modules (Keyring, NotificationDisplayer, editor kits,
  ConnectionManager, the terminal-emulator probe) stay: that's what the
  idiom is FOR.
- **Rack's exports are friend-declared** (ledger 31).
  `OpenIDE-Module-Friends` lists exactly the five first-party modules
  that still compile against rack (editor, tools, project, ui, dbstudio)
  — byte-verified in the built jar's manifest; the module system now
  refuses any other dependent, so the world-exported-API risk is closed
  before any plugin story ships. Core stays friend-less on purpose: its
  minimal exports are the intended public surface.

### Added
- `SoftDependencyGateTest` (core): pins per-file catch(LinkageError)
  counts — zero at every converted site, exact counts at the mixed files
  — and walks apiclient/web3/infra asserting no main source names a rack
  package. Mutation-proven: re-adding a catch fails it.
- Facade contract tests: RackProjectAimTest / RackLiveServingsTest (rack;
  lookup finds the providers, delegation, listener lifecycle incl. the
  never-double-deliver law), RackSoftDependencyTest ×3 (the absent
  branch), FakeLiveServings test doubles in apiclient and web3.

### Notes
- TrustGate was deliberately not facaded: editor's rack dependency must
  stay for CommandExecutor/DiagnosticsBus regardless, so a trust facade
  would remove neither the idiom nor a dependency (honest scope).

## [1.45.0] — 2026-07-11

The context release: ledger 29, the one big architectural arc, worked as
its own release. The rack's aimed project is now a first-class platform
citizen.

### Added
- **Aiming publishes to the platform.** A real aim opens the project in
  OpenProjects and makes it the main project (background lane; re-entrancy
  flag so WebProjectOpenedHook's echo terminates on our guard; passive
  aims — the fresh-boot ~/NMOX default, persisted window state, quiet
  experiment opens — provably never resolve platform projects, keeping the
  boot laws; nothing is ever closed on re-aim).
- **The aim-owning windows publish a real selection.** Task Rack, Project
  Studio and Workbench expose the aimed directory's node via
  setActivatedNodes (off-EDT resolve, equality-guarded, showing-gated) —
  so Utilities.actionsGlobalContext() finally sees NMOX's context.
- **The payoff, live-verified**: the Team menu is the full enabled git
  suite with just a project aimed (in v1.40.0 it was one disabled stub —
  three invocation strategies failed); the git chip's Show Changes / Diff
  / Annotate verbs are back, opening the platform's real windows.

### Notes
- Still open by design (ledger 29 remainder): context-sensitive Kit-action
  registrations (focus-keyed enablement would disable them while the
  editor is focused — a UX regression masquerading as idiom) and lookups
  for the seven non-aim windows. Both are incremental now; the pattern is
  established. 19 new tests, guards mutation-proven.

## [1.44.0] — 2026-07-10

The debt sweep. No new features: seven ledger items worked with the
v1.26.0 rule — fresh evidence, never a remembered reason — and each
closed with the test that would have caught it.

### Fixed
- **Stop All and project-switch no longer freeze the IDE.** Stopping a
  SIGTERM-proof tool held the paint thread 1.5 seconds per device
  (measured); stops now run on a bounded worker, the switch proceeds
  only when everything is dead, Stop All can't double-fire, and the
  status line narrates. The shutdown reaper stays synchronous —
  source-gate-pinned — so the orphan guarantee is untouched (proven on
  live process handles).
- **Workspace saves left the paint thread** — all four studios snapshot
  on the EDT and write on a dedicated single-throughput SaveLane; the
  write and its self-write stamp are one task, and foreign-vs-own
  verdicts queue behind pending writes, closing the misclassification
  race by construction. Close and reload drain the lane. Found in
  passing: DbWorkspaceIO was the last non-atomic workspace writer
  (v1.39's sweep missed it) — now AtomicFiles.
- **Window-menu items show their keyboard shortcuts.** Keymaps shadows
  now mirror every Shortcuts registration onto the same action;
  WindowShortcutsTest pins chord AND target so the two registration
  mechanisms — the v1.38.1 failure class — can no longer drift.
- DB Studio connect and infra cloud sync show real progress bars
  (per-provider ticks); the last constructor-wired rack listeners moved
  to symmetric attach/detach with mutation-proven lifecycle tests; the
  `netbeans.default_userdir_root` startup warning is gone (launcher-free
  code setter — both conf attempts had word-split on "Application
  Support"); the SBOM gains the vendored js-debug component (purl, MIT,
  pinned sha256) — its one blind spot, closed.

### Notes
- Ledger: 15, 16, 17, 22, 26, 28 closed; 34 mostly closed (web3's fast
  artifact walk stays status-text-only, lowest value). The big open
  items remain deliberate: 29 (context-system migration — three
  customers waiting), 20/21 (spec versions + update center), 38/40
  (Windows Job Objects).

## [1.43.0] — 2026-07-10

Browser debugging. The v1.37.0 machinery — the vendored js-debug adapter
behind the DapProxy multiplexer — learns the `pwa-chrome` launch, so
breakpoints set in the IDE now stop JavaScript running in a real Chrome.
Recon first, the v1.37.0 method: a scratch harness drove the real adapter
against headless Chrome before any product code, and its transcript pinned
the shape (one page-target `startDebugging` on the parent link, worker
targets on the child link, the browser spawned as the adapter's direct
child at `configurationDone`, a client disconnect alone killing every
browser process — js-debug's `cleanUp: wholeBrowser` default, verified at
zero processes 3s after disconnect).

### Added
- **"Debug in Chrome (breakpoints)"** on .html, .js, and .ts editor
  context menus. URL selection, most-live first: a rack serve device
  already announcing a URL for the project wins (exact project dir, then
  containing dir for monorepo lanes); with no live server an .html file
  opens as `file://` (js-debug maps file URLs against webRoot —
  recon-verified); a bare script with no server gets an honest status
  message instead of a guess. Chrome runs headed — the user watches the
  page — with a fresh throwaway profile under the userdir cache (never
  the real Chrome profile; deleted after the tree is confirmed dead).
- **BrowserLocator** — the ToolLocator idiom for Chromium-family browsers:
  Chrome, then Edge, then Chromium, per-OS install paths (mac
  /Applications, Windows install roots from the env, Linux via the
  augmented-PATH scan). None found → a status message naming what to
  install, and nothing is ever spawned.
- **RealChromeIntegrationTest** — the recon transcript as a permanent
  regression test: real adapter + real `--headless=new` Chrome against an
  in-JVM HTTP fixture; breakpoint hit, stack mapped back through webRoot
  to the real file, and disconnect-kills-the-whole-browser asserted on
  live ProcessHandles. Skips with a message where no browser is
  installed; mutation-proven (removing the code that trips the breakpoint
  fails it).
- **DapProxy pins for the browser shape**: the pwa-chrome child dance
  replayed frame-for-frame from the recon transcript, and a worker
  target's `startDebugging` arriving on the CHILD link answered there —
  never surfacing to the client, never dialing a third connection.

### Security
- Browser debugging gates on **Workspace Trust** before anything spawns —
  the same record the rack and the v1.37.0 debug paths use; "Keep Safe"
  stops the launch before the browser probe even runs (source-gate
  tested).

### Ledger
- **39 (new)**: a page's Web Workers sit paused under browser debugging,
  not undebugged — recon-proven both ways (success and failure answers);
  same single-session root cause as ledger 25, same future fix.

## [1.42.0] — 2026-07-10

The Windows lane. Every release since v1.4.1 shipped a real Windows
installer; every test ran on Linux and macOS. windows-latest now runs the
full verify — tests, SpotBugs, coverage floors, the test-execution audit —
as a blocking gate, and getting it green surfaced exactly what the plan
predicted it would.

### Added
- **windows-latest in the CI matrix**, full `mvn verify` + audit, blocking.
  Green in 7m24s across all 14 modules. The assembled-app probes (boot
  smoke, rendering) stay Linux/macOS — the .exe launcher and runner
  display story are ledger 37.

### Fixed
- **No language server was ever detected as installed on Windows** —
  `LanguageServerCatalog` probed bare names with `canExecute()`, which
  never matches `.exe`/`.cmd`. It now probes the same suffixes ToolLocator
  resolves. Regression test runs on every OS.
- **A DapProxy disconnect race on every OS**, exposed by Windows
  scheduling: stop landing mid child-session handshake told only the
  parent adapter and left the debuggee alive. The fan-out now gates on the
  child socket, not the post-handshake flag; a new test freezes the
  handshake mid-flight and fails on the old gate.
- **`ProcessSupport.killTreeAndWait`**: `destroyForcibly` is async, and a
  dying Windows process still holds file/cwd locks; `JsDebugServer.stop()`
  now returns only after the tree is confirmed dead (bounded).
- Five tests made honest across OSes (canonical-path/CRLF/per-OS-shell
  fixtures; two `@TempDir` cwd-lock cleanups; one genuine connection leak
  in the DB Services test that every OS had been tolerating).

### Notes
- One Windows disable, total, with runner-proven evidence: the
  grandchild-pipe timeout test — Git Bash breaks the Windows parent-PID
  chain at exec, so MSYS-spawned grandchildren are invisible to any pure
  Java `descendants()` walk (documented in `killTree`'s javadoc; native
  Windows trees ARE swept; ledger 38 records the Job-Objects fix if
  Git-Bash-under-timeout ever matters).
- New failure pattern (plan.md): a test that spawns a process into its
  `@TempDir` must confirm the process dead — or point its cwd elsewhere —
  before cleanup; Windows file locking turned that into three separate
  incidents this sprint.

## [1.41.0] — 2026-07-10

The accessibility sweep. The rack's custom-painted widget library (knobs,
buttons, switches, LEDs, LCDs, VU meters) was screen-reader opaque —
`getAccessibleContext()` returned null on every control — and mouse-only.
Every control is now visible to assistive technology and the operable
ones work from the keyboard, with the painted look unchanged except a
visible focus ring.

### Added
- **Screen-reader visibility for all six rack widgets.** Each implements
  `Accessible` with the right role: Knob = SLIDER with AccessibleValue
  (continuous knobs report percent matching the painted readout, stepped
  knobs report the position index with "Position n of m: name" in the
  description), RackButton = PUSH_BUTTON with a working AccessibleAction,
  ToggleSwitch = TOGGLE_BUTTON whose CHECKED state tracks the bat, Led and
  LcdDisplay = read-only LABELs (LED description says on/off/blinking and
  names the palette color; LCD description is the text on the glass),
  VuMeter = PROGRESS_BAR reporting percent. Value/state/text changes fire
  the matching accessible property events — guarded on the context field,
  so nothing fires until assistive tech asks.
- **Keyboard operation.** Knobs: arrows step (an option / 5%), Home/End
  jump to the rails. Buttons: Space/Enter press — routed through the same
  single fire path as the mouse, so a dimmed button ignores both alike.
  Switches: Space flips. Clicking any operable control focuses it; a
  `RackStyle.FOCUS_RING` ring (a hue the color law reserves for nothing
  else) marks the focus owner. Indicators (LED/LCD/VU) are explicitly
  non-focusable. Tab traverses controls in placement order; the rack-flip
  Tab now yields whenever a faceplate control or the REPL field has focus
  (the toolbar toggle still flips at any time).
- **Every placed control has an accessible name**, enforced by a new
  DeviceContractTest invariant across all 44 devices (failed first for
  59 controls on 40 devices, then fixed by wiring names — never by
  weakening the assertion). Labeled widgets reuse their silkscreen label;
  label-less LCDs derive their name from the edit prompt or persistence
  key, with explicit names where neither exists (one shared fix in
  CommandDevice covered the status panel on 26 devices).
- **A11yContractTest** (19 tests): roles, names, focus policy,
  AccessibleValue arithmetic, keyboard actions invoked through the real
  key bindings, and the property-change announcements.

## [1.40.0] — 2026-07-10

The git surface. The plan's next daily-driver gap: the IDE shipped twelve
git modules (jgit, diff, versioning, the full Team menu) and no NMOX
surface told you so much as the branch.

### Added
- **A git chip in the status line.** Aim at a project inside a git repo and
  `⎇ main ±3` appears — branch read straight from `.git/HEAD` (worktree
  `gitdir:` indirection and detached HEAD included), changed-file count
  from a bounded `git status --porcelain`. Zero processes at boot, honest
  to the v1.38.0 law: the count runs only on aim, on click, and on a 30s
  tick that arms only while the chip is visible (source-gate tested). New
  pure `GitFacts` core (9 tests) + `GitChip` model (8 tests incl. the
  boot-guard state machine).
- **History, one click away.** The chip menu opens the platform full
  Show History browser for the repository via the git module exported
  API — live-verified against a real repo: commits, authors, filters,
  per-commit diffs.

### Notes
- The chip menu deliberately offers only what works without a selection
  (History, Refresh). The platform Show Changes/Diff/Annotate are
  NodeActions that read the global selection — which no NMOX window
  publishes (ledger 29) — so those stay where they work: the Team menu,
  with a file selected or open. Three invocation strategies were tried
  live before accepting this; dead buttons do not ship (the v1.38.1
  lesson). Ledger 29 gains its first concrete customer.
- Found in passing: the second argument of the git module
  openSearchHistory(File, String) is a commit-ish, not a path — jgit
  says "COMMIT [path] does not exist" if you guess wrong.

## [1.39.0] — 2026-07-10

The idiom release. Five senior-NetBeans-RCP audit lenses over the whole
codebase — Lookup/services/actions, window-system wiring, FileSystems/
DataObjects, threading/platform utilities, module wiring — then fixes for
the twelve findings that were cheap and clearly right, written deferrals
for the deep ones (ledger 29–36), and blessings-in-writing for what looks
unidiomatic but is deliberate. No features; the release makes the internals
*more platform-native* without moving a single control the user can see.

### Fixed
- **Open editors now follow the file tree.** Project Studio's create/rename/
  delete went through raw `java.nio`, so deleting or renaming a file that
  was open left the editor holding a stale buffer over a dead path (a later
  save could resurrect the deleted file). CRUD now routes through the
  platform — `DataObject.delete()`, locked `FileObject.rename`, masterfs-
  visible creates — so editor buffers close and relocate with the file.
  The trash-preferring delete survives.
- **Workspace saves are atomic.** All five workspace writers (`.nmoxapi`,
  `.nmoxweb3`, `.nmoxinfra`, rack patches, package.json edits) used
  truncate-then-write, so our own change pollers — or an external tool —
  could read a half-written file, and a poll landing inside the window
  would misclassify our own save as a foreign edit and reload garbage.
  New `core` `AtomicFiles` (temp sibling + `ATOMIC_MOVE`): readers now see
  old bytes or new bytes, never a mixture.
- **Every `new Rack()` leaked a JVM shutdown hook** pinning its whole
  device graph until exit. One static reaper hook over a live-set now
  (the JsDebugServer pattern); `shutdown()` deregisters. `RackReaperTest`
  pins it.
- **Docker Panel ignored being closed.** `PERSISTENCE_NEVER` +
  `openAtStartup` meant the window system forgot the close and forced the
  tab back on every launch — the only suite tab that wouldn't stay shut.
  Now `PERSISTENCE_ALWAYS` like its siblings.
- **Three studios loaded their workspace twice** — once in the constructor
  (during window-system deserialization, the wrong lifecycle phase) and
  once on open. The load now happens exactly once, in `componentOpened`;
  moving it exposed that DB Studio's open-time reload would have run the
  Docker probe behind a hidden tab at boot — caught and gated on
  `isShowing()`, so the v1.38.0 zero-boot-spawns law still holds
  (lifecycle-gate tests pin all three).
- **Dead output hyperlinks on relative paths**: `FileUtil.toFileObject`
  requires normalized files; compiler/stack-trace paths with `..` segments
  silently returned null. Both un-normalized call sites fixed.
- **API Studio was the only module doing background work on raw threads** —
  send worker, workspace loader, serving poke now ride a named, bounded
  `RequestProcessor("API Studio")`. NPM operations no longer occupy the
  JVM-shared `ForkJoinPool.commonPool` for 30-second subprocess calls
  (module RP passed as executor). `DockerClient` is now a real
  `@ServiceProvider` resolved through Lookup instead of a bare static;
  `NpmService.getInstance()` renamed `getDefault()` to say what it is.
- Hand-rolled `os.name` sniffing (including a darwin-contains-"win" trap)
  replaced with `Utilities.isMac()/isWindows()` (`BaseUtilities` in core,
  which deliberately lacks the UI util module).

### Notes
- **Blessed in writing** (looks unidiomatic, is deliberate): mtime polling
  over `FileObject` listeners (macOS WatchService lag + watching unmounted
  `node_modules`-scale trees; skip-dirs conflict documented); RackBus as a
  bespoke off-EDT output stream (no platform equivalent); JVM shutdown
  hooks over `@OnStop` for process reaping (hooks fire on SIGTERM/
  `System.exit`, `@OnStop` doesn't); WorkspaceTrust on global
  `java.util.prefs` (trust must survive userdir wipes — now documented in
  the class so nobody "normalizes" it); modeless SONAR/BLACKBOX viewers as
  raw JDialogs.
- **Ledger 29–36** record the deep deferrals with reasons: the rack as the
  IDE's context system vs `OpenProjects`/`actionsGlobalContext` (identity,
  not accident — migrate as its own release or not at all), the
  `catch (LinkageError)` soft-dependency shape, rack's friend-less public
  packages (must narrow before any plugin SDK), DiagnosticsBus vs the
  platform hints/task-list plumbing, studios sharing the `editor` mode,
  ProgressHandle gaps, the missing `@OnStop` seam, and FileTreePanel as a
  raw JTree (the correctness half — editor-synced CRUD — shipped above).
- What the audit found already right, kept: declarative action registration
  throughout, real platform SPIs (`ProjectFactory`, `LanguageProvider`,
  `MIMEResolver`, `StatusLineElementProvider`), all `findTopComponent` IDs
  verified against real `preferredID`s, `invokeWhenUIReady` discipline,
  named+bounded RequestProcessors, zero `JOptionPane`/`printStackTrace`,
  layer hygiene with collision-free QuickSearch positions, acyclic module
  graph with core exporting only leaf utilities.

## [1.38.1] — 2026-07-09

The DX pass. Four senior-developer journeys walked end to end in the real
app — debug a Node service, drive a database, serve a classic site, reach
for the tools. The engine held up beautifully; the *surroundings* were
where the bugs were, and every one of them sat exactly one keypress off
the path a feature test walks.

### Fixed
- **Four of seven advertised keyboard shortcuts opened the wrong window.**
  The Welcome launchpad and the docs promised ⌘0 Workbench, ⇧⌘6 Contract
  Studio, ⇧⌘7 DB Studio, ⇧⌘8 API Studio. Pressing them gave the Editor,
  Tasks, Properties and the Palette — the platform's Keymaps profile owns
  those chords, and a Keymaps registration beats the `Shortcuts/` folder we
  register in. (⇧⌘O "Open Folder" lost the same way, to Open Project, which
  is why it dropped users in `~/NetBeansProjects` — a folder NMOX never
  creates.) The studios now live on ⌥⌘6–9, Workbench on ⌥⌘0 and Open Folder
  on ⌥⌘O — a digit family no shipped module claims. Every chord was pressed
  in the assembled app, before and after. `WindowShortcutsTest` pins both
  halves: no chord in the platform's reserved set, and Welcome advertises
  exactly what is registered.
- **A debug session's Output window never surfaced.** The debuggee's stdout
  landed correctly in an Output tab, but nothing opened it, so a debugged
  server's `listening on 3100` banner was invisible until you knew to press
  ⌘4 and hunt. Debugging a server without its console is debugging blind;
  the session now opens Output for you.
- **Open Folder started in `$HOME`** — a wall of Library/Desktop/Downloads
  (and on macOS, TCC prompts). It starts in the `~/NMOX` workspace, where
  New Project puts things.

### Notes
- The v1.37.0 debugger was exercised against a real HTTP server: the trust
  prompt named the right project root, the breakpoint stopped a live
  request mid-flight, the call stack showed `Server.emit → parserOnIncoming`,
  variables resolved, and Stop left zero orphans — adapter, debuggee and
  port all released.
- Two limits are now written down instead of merely suffered (ledger 27–28):
  the platform's **Breakpoints window never lists DAP breakpoints** (verified
  against Python, which uses none of our code — the gutter is the breakpoint
  manager, and the user guide now says so), and Window-menu entries for our
  studios show no accelerator.

## [1.38.0] — 2026-07-09

The startup-truth release. Measured first (startup-log phases + JFR on
cold, warm, dev-tree, and installed-app boots), then fixed exactly what
the profile named. Headline finding: the 7-second cold start recorded at
v1.26 no longer exists — the v1.33.x storm fixes removed it. The window
paints in **1.4s** (warm OS cache) to **2.7s** (first boot), ~90% of
which is the platform module system reading 519 cluster jars: the
deliberate price of shipping real editors, git, and databases. What was
NOT deliberate was hidden tabs working at boot — fixed below,
JFR-verified: the IDE now spawns **zero** external processes at startup.

### Fixed
- **NPM Explorer ran `npm ls -g` twice on every boot** — once from its
  constructor, once from componentOpened, both of which fire during
  window-system load while the tab is hidden. Per JFR these were the only
  processes the whole IDE spawned at boot. The refresh now waits for
  componentShowing (the DB Studio Docker-offer idiom), and a rack re-aim
  while hidden takes a note instead of spawning behind an invisible tab.
- **Contract Studio walked the artifact tree at boot** — the constructor's
  rescan ran `Files.walk` over `out/` + `artifacts/` for a tree nobody was
  looking at. Both rescan paths (initial and build-pulse) now defer while
  hidden and coalesce to a single walk on first show; a build storm behind
  a hidden tab becomes one deferred scan, not N.
- **Docker panel generated dockerize previews at boot** (a project-dir
  detect walk). Deferred to first show; the docker daemon calls were
  already correctly gated and stay untouched.
- **Project Explorer and Project Studio each did their boot work twice**
  (constructor + componentOpened both fired the toolchain-detect walk /
  the directory list + FileWatcher spin-up). componentOpened owns the work
  now; the constructors are passive.

### Notes
- Every fix is pinned by a source-gate test (the DebugTrustGateTest idiom),
  the NPM one proven to fail against the old code. Verified live: a fresh
  boot with JFR shows 0 process starts, and clicking each tab serves its
  deferred work (npm globals, artifact scan, dockerize previews) on first
  show.
- The audit found the rest of the suite already clean: DB Studio and the
  Workbench launchpad were the model (componentShowing gates since
  v1.35.1), Infra's token check is local-only, API Studio loads one JSON
  file. No default-open tab touches the network at boot.

## [1.37.0] — 2026-07-09

### Added
- **JavaScript and TypeScript breakpoint debugging**, zero setup. Set a
  breakpoint in the gutter, right-click → *Debug File (breakpoints)*, and
  the program stops there with call stack, variables, stepping and watch
  expressions — the platform's own debugger UI, driving Microsoft's
  `js-debug` (MIT, v1.117.0), **vendored in the box** so nothing needs
  installing. `cwd` is the project root, so requires and `node_modules`
  resolve as they do from a terminal.
- `DapProxy`, a DAP session multiplexer. js-debug's first connection is
  only a coordinator: after `launch` it sends a `startDebugging` reverse
  request and expects the client to dial a *second* socket for the real
  target. NetBeans' DAP client is stream-based and cannot dial, so a
  direct wiring leaves the debuggee paused forever. The proxy answers that
  request itself, opens the child session, replays the client's
  breakpoints, and splices it in — one flat session as far as the IDE
  knows. `RealJsDebugIntegrationTest` drives the real adapter end to end
  and pins the whole message flow against future js-debug versions.
- `ProcessSupport.killTree` promoted to public (descendants first): the
  debuggee is a child of the adapter, and neither may outlive the session.

### Fixed
- **Security: debugging bypassed Workspace Trust.** The rack gates every
  device launch behind a trust prompt; the debug action ran the project's
  code — all three languages, not just the new one — without asking. It
  now consults the same trust record at the same project root, and
  *Keep Safe* blocks the launch before any adapter or debuggee spawns.
- **The proxy slammed the client's socket shut on `terminated`.** After
  forwarding the session's last event it called `close()`, which closed the
  socket the DAP client reads from — discarding whatever bytes were still
  sitting unread in that socket's receive buffer, `terminated` among them.
  A finished session could stay marked live in the debugger UI. macOS hid
  it (the reader thread usually drained first); Linux loses that race every
  time, which is how CI caught it. The proxy now half-closes its own end,
  so the FIN queues *behind* the frames it already wrote: the client drains
  them, reads a clean EOF, and ends the session on its own terms. The
  socket pair belongs to whoever took the streams. A dropped link now takes
  the same graceful path instead of the same slam.
- **IGNITION's static lane served silently.** `python3 -m http.server`
  prints its `Serving HTTP on` banner to stdout, which python
  block-buffers when it isn't a TTY, so the banner never reached the
  device: no READY, no URL jack, no `⇄ serving` chip, no ⌘I Live Servers
  entry (the access log is stderr, so output *looked* healthy). Fixed with
  `python3 -u`. The old test injected the banner directly into `onLine()`,
  so it passed while reality failed; the new test asserts the argv.

### Notes
- The vendored `js-debug` is not a Maven dependency: it does not appear in
  the SBOM and Dependabot cannot see it. Version bumps are manual — see
  `editor/src/main/release/jsdebug/NOTICE` (source URL, sha256, license).
- A debug session follows one process. Child processes the program spawns
  run undebugged rather than pausing for an attach that never comes
  (`autoAttachChildProcesses: false`).

## [1.36.0] — 2026-07-05

The senior review release. A very-senior-NetBeans-RCP-developer pass
over the whole codebase: six read-only audit lenses (platform/module
architecture, device/process lifecycle, listener symmetry, timers,
EDT & process hygiene, API quality & house laws), then fixes for
everything the audit proved — and a written blessing for everything
that looked wrong but is deliberate. No new features; net −3,000 lines.
The audit's headline was what **held**: the orphan-process guarantee,
the storm laws, the Keyring boundaries, and the v1.35 listener-symmetry
pass all survived adversarial reading with file:line evidence. What it
caught clustered in the two oldest surfaces and in the *mutation* half
of code whose *read* half was hardened in earlier sprints.

### Fixed
- **Infra Designer's inverted listener lifecycle** (the review's
  sharpest finding). Listeners attached in the constructor and were
  removed on close but never re-added — after one close/reopen,
  auto-save was dead, the never-clobber dirty guard was blind (external
  edits silently overwrote unsaved canvas work), and a stale project
  binding could write project A's design into project B's
  `.nmoxinfra.json`. Listeners now attach per-open (ApiClient's
  attached-flag idiom), reopen re-loads the currently-aimed project,
  and close flushes the pending debounced save. Autosave failures now
  warn once per streak instead of vanishing; the five cloud workers
  (deploy/sync/refresh/destroy×2) serialize on the designer's
  RequestProcessor with their buttons locked while running.
- **Docker survives a wedged daemon.** DockerClient read stdout to EOF
  *before* starting its 15-second timeout, so a docker CLI stuck on a
  frozen daemon (silent, pipe open) was never killed — the first hung
  refresh pinned all four pool threads forever and every Docker feature
  silently froze on stale data until restart. Output now drains on its
  own thread while the timeout runs first; the kill is real (regression
  test proven to fail on the old code). The same read-before-timeout
  idiom died at four more sites via the new `ProcessSupport.runBounded`
  — SONAR's lsof scan could starve the JVM-wide commonPool, and
  CommandProbe/ProjectTemplates/NpmService each had the latent hang.
- **The EDT war's missing half: mutations.** New Project ran the
  template write plus up to four sequential git spawns on the EDT
  (every creation froze the UI; pathologically ~2 minutes); Experiments
  discard/promote ran recursive deletes and git on the EDT; the file
  tree's Delete could beachball for minutes on a `node_modules`; the 5s
  session snapshot wrote to disk on the paint thread. All moved to
  RequestProcessors with dialogs honestly locked and progress shown;
  the snapshot writer is latest-wins so a slow disk never backlogs.
- **Corrupt workspace files can no longer be clobbered by autosave.**
  A studio workspace that failed to parse loaded as empty — and the
  first edit autosaved emptiness over the user's original (sharpest
  case: Contract Studio's deployment address book). All four studios
  now keep the unreadable original as `<file>.bak` and say so in a
  balloon before starting empty.
- **Ghost servings and disposed devices.** Deleting a device mid-serve
  now deregisters its ⇄ serving entry on dispose, and a disposed flag
  (cleared on undo re-attach, so ⌘Z still revives devices) guards the
  signal router and exec — a queued trigger can never launch a process
  into a deleted device. REPL stop gained the TERM→wait→KILL rung, so
  a TERM-trapping interpreter can't outlive the shutdown reaper;
  InteractiveProcess and CommandProbe pumps survive throwing consumers.
- **The JavaScript context menu was silently scrambled.** A hand-written
  layer.xml actions block collided with the annotation-generated set
  (three same-position conflicts; Tools/Properties rendered mid-menu
  among Cut/Copy). The annotations own loader wiring now. Also merged
  the duplicate `Editors` folders, deleted a dead type-mismatched
  editor-kit registration, and fixed four ⌘I Quick Search category
  position collisions (Rack vs Go To Type, Infra vs API Studio, plus
  rack's Projects folder now joins the platform category
  deterministically).
- **Error-path UX.** The last surviving JOptionPane (NPM Explorer) is
  now a platform dialog; all 15 `printStackTrace` sites downgraded —
  the worst popped the red exception dialog for a malformed
  `package.json` the user was editing in this very IDE (now: log line
  plus the reason on the error node); 11 identical completion-item
  document edits collapsed into one tested helper logging at INFO.
  API Studio's Send button re-enables when a request worker fails
  (a hand-edited `"target": null` assertion left it dead until
  restart), and a null assertion target is a failed assertion, not an
  NPE. Docker panel: no more dual instances (window-system singleton),
  auto-refresh honestly resumes on reopen, and the "publishes no host
  ports" message now distinguishes no-ports from unparseable-ports.
  Failed debug launches reap the spawned dlv/debugpy adapter instead
  of orphaning a listener per retry. The Workbench's rack listener got
  the open/close pair (project switches no longer rebuild a closed
  Workbench with per-project disk walks).

### Security
- **Cloud API tokens moved from plaintext Preferences to the OS
  keychain** (DigitalOcean/Hetzner/Cloudflare) — the last secrets
  outside the Keyring. Legacy tokens migrate on first read, and the
  old preference is deleted only after the keychain save succeeds, so
  a degraded session can never destroy the only durable copy. When no
  keychain is reachable, DB Studio, Contract Studio, and the Infra
  designer now say so once per session instead of silently not saving.

### Removed
- The never-consumed v0.x `tools.build` service (shipped a latent pipe
  deadlock and regex-parsed JSON), the dead v0.x `CodeIndexService`
  (two executors, a watch loop that died permanently on first
  exception), the sample template module from the shipped product (it
  remains in the repo as a dev template), a dead `deployment` build
  profile generating an update site no workflow used, the ui module's
  wildcard package export, and a dead ui→tools dependency edge.

### Internal
- New `core` facility: `ProcessSupport.runBounded` — one-shot tool
  runs whose timeout is actually real (both streams drain on helper
  threads while `waitFor` runs first; forcible kill unblocks the
  drains). Five call sites adopted it.
- Immutability hardening: `LearningCatalog`, `SessionState`,
  `ClassicKit`, `CommandDevice` presets, `HeaderGrader.Report`,
  `NodeKind.Prop` all hand out unmodifiable copies (order-preserving
  where generated files depend on iteration order).
- `InfraGraph.setStatus` equality-guarded (the storm law); cloud syncs
  no longer re-fire per node per refresh.
- Tests: 45 new (including regression tests proven to fail on the old
  code for the DockerClient timeout, the disposed-device races, and
  the Infra listener bookkeeping); suites covering the deleted dead
  code went with it. Totals: core 18, rack 734, tools 59, project 18,
  ui 39, editor 336, infra 193, apiclient 97, dbstudio 355, web3 271 —
  2,120 across the ten code modules. Coverage floors hold everywhere
  with headroom (rack 0.699 vs 0.60, infra 0.768 vs 0.72, dbstudio
  0.870 vs 0.73, web3 0.892 vs 0.80).
- The debt ledger gained the audit's deliberate deferrals (items
  15–24, each with its reason) and a closed section for this sprint;
  the v1.33-era REPL-INSTALL entry that v1.35.1 actually fixed was
  finally moved out of the open list.

## [1.35.1] — 2026-07-05

The finishing pass: the security alerts, the flagged-but-unfixed bug,
the connection release's own IOUs, and one honest finding from the live
demo — all closed.

### Security
- **PostgreSQL JDBC driver 42.7.5 → 42.7.11** — clears both open HIGH
  advisories against the bundled driver (unbounded PBKDF2 iterations in
  SCRAM authentication enabling CPU-exhaustion DoS, and a fallback to
  insecure authentication despite `channelBinding=require`).

### Fixed
- **REPL INSTALL now runs compound install commands correctly.** Since
  v1.31.0 the INSTALL button argv-split the catalog's install command,
  so entries with shell operators — Foundry's
  `curl -L … | bash && foundryup`, and a PyTorch entry whose trailing
  `# CPU wheel` comment was being fed to pip3 as arguments — silently
  did the wrong thing. Commands containing operators now run via
  `/bin/sh -lc` with the raw string (never re-split; login shell so
  profile-managed PATHs work); plain commands keep the exact prior
  no-shell path. SOLDER is deliberately unchanged: user-typed commands
  still never touch a shell (its documented security stance) — the
  shell wrapper exists only for curated catalog data.
- **`php -S` registers with the Serving Registry** — IGNITION's PHP
  built-in-server lane now announces its URL and appears in the status
  chip, ⌘I Live Servers, and auto-targeting, like every other serve
  lane (the v1.35.0 ledger's own follow-up).
- **API Studio follows mid-session re-aims** — re-aim the rack and the
  studio force-saves any pending edits to the OLD project's
  `.nmoxapi.json`, loads the new project's workspace, re-points its
  file watcher, and re-evaluates `{{baseUrl}}` offers. Storm-tested:
  a 100-event re-aim storm costs one load; an A→B→A bounce costs none.
- **DB Studio's Docker offers wait until you can see them.** The live
  demo caught the gap: the container probe ran at app startup while
  the tab was hidden, the balloons expired unseen, and the
  once-per-session guard was consumed — so the offer could never
  reappear. The probe now fires when the tab actually becomes visible,
  a probe completing while hidden holds its plan for the next showing,
  and the guard is consumed only when an offer is actually displayed.
- **One SelfWriteTracker** — the deliberately-duplicated
  self-write-vs-foreign-edit discriminator (web3 + apiclient) is now a
  single canonical class in core; dbstudio's ExternalEdits stays
  separate on purpose (different verdict semantics, documented).
- core 13 / rack 719 / dbstudio 353 / apiclient 94 / web3 268 tests;
  SpotBugs + find-sec-bugs at zero findings on every touched module.

## [1.35.0] — 2026-07-05

The connections release: the parts of the studio now talk to each other.
Servers announce themselves, edited manifests re-sync the devices that
read them, studios notice the world changing around them — and nothing
storms, because every new reaction is bounded, equality-guarded, and
regression-tested.

### Added
- **The Serving Registry** — one live answer to "who is serving what."
  Every serve device (SURGE, IGNITION's static and webpack lanes,
  ARTISAN, HALO, NEXUS, PHOENIX, and ANVIL as a chain) announces its
  URL the moment it's ready and withdraws it on stop. What that unlocks:
  - The **status line** grows a `⇄ serving: <url>` chip — click it to
    open any live server in the browser.
  - **⌘I gains "Live Servers"** — running servers are search results;
    selecting one opens it (chains focus Contract Studio).
  - **VITALS and BEACON auto-target**: leave the URL blank and they
    aim at the running server for the aimed project (`auto: <url>` on
    the LCD; an explicit URL always wins).
  - **API Studio offers `{{baseUrl}}`** when a dev server for the
    aimed project appears and the active environment lacks one — one
    quiet balloon per server per session; accepting writes the variable
    (creating a "Local" environment if none exists).
  - **Contract Studio auto-connects**: start ANVIL and the network
    chip goes green by itself; stop it and the chip greys immediately.
    No more re-selecting the combo to make the studio notice.
- **Manifest edits re-sync the rack** — saving package.json,
  Gruntfile/gulpfile, composer.json/lock, bower.json, lockfiles,
  webpack configs, foundry.toml, .gas-snapshot, or .env pulses exactly
  the devices that read them: NPM-9000 re-lists scripts, DYNAMO
  re-parses tasks, CRATE refreshes its deps LCD, HALO and ARTISAN
  re-check version currency — without re-aiming the project. A kit
  writing ten files costs one coalesced re-sync, not ten (test-pinned).
  `.env` saves surface a status-line note ("env changed — restarts pick
  it up") — honest about running processes keeping their old env.
- **Contract Studio watches the build** — new artifacts in Foundry's
  `out/` or Hardhat's `artifacts/` auto-rescan the contracts tree,
  whether the build came from FORGE, a terminal, or CI.
- **DB Studio talks to Docker** — running containers that publish a
  database port (postgres/mysql/mariadb/mongo/couchdb, inferred from
  image name first, port second) get a quiet connection offer with the
  dialog prefilled; at most two balloons per pass, one offer per
  container per session, total silence without a Docker daemon.
- **Studios notice external edits** — hand-edit (or git-pull over)
  `.nmoxdb.json`, `.nmoxapi.json`, `.nmoxweb3.json`, or
  `.nmoxinfra.json` and the studio reloads: silently when it holds no
  unsaved state, with a "changed on disk — Reload?" balloon when it
  does. Self-writes are discriminated from foreign edits by stamp, so a
  studio never reacts to its own saves — and never silently clobbers
  yours. A saved `.env` also re-arms DB Studio's connection offer.
- **Open the manifest behind the device** — NPM-9000, DYNAMO, CRATE,
  HALO, ARTISAN, GOVERNOR, and FORGE grow a context-menu jump straight
  to the file their knobs are built from.
- **Less duplication behind the scenes** — one shared URL-from-output
  scanner replaces four device-private copies (deliberately different
  parsers kept, with reasons), and package.json script parsing in the
  rack rides one cached path.
- rack 714 / dbstudio 349 / web3 271 / infra 181 / apiclient 91 tests
  (+133 this release); every new listener carries a bounded-reaction
  storm test; SpotBugs + find-sec-bugs at zero findings.

## [1.34.0] — 2026-07-05

The classic web release: the stacks that used to be number one — jQuery,
MooTools, Prototype, Backbone, Knockout, Webpack, Grunt, Gulp, Bower,
CoffeeScript, and the script-tag site with no manifest at all — open,
run, and grow in NMOX Studio just like the modern ones.

### Added
- **Script-tag sites are projects now.** A folder containing nothing but
  `index.html` and assets opens as a project (new STATIC kind — strictly
  a last resort, suppressed the moment any real toolchain manifest is
  present, so modern apps never misclassify). IGNITION serves the folder
  (`python3 -m http.server`) with READY/URL jacks, so VITALS, BEACON,
  and the browser devices work on a 2005-vintage site unchanged.
- **Legacy toolchains are recognized citizens** — `bower.json`,
  `Gruntfile.js` (and `.coffee`), `gulpfile.js` (+babel/mjs), and
  `webpack.config.js` (+cjs/mjs) all open as projects (30 manifests
  now). FORGE detects webpack/grunt/gulp from config files, not just
  package.json deps, and its TOOLS knob gains grunt and gulp lanes.
  CRATE grows a Bower lane (`bower install`/`update`) slotted into the
  install-all sequence.
- **DYNAMO (44th device)** — the legacy task runner: statically parses
  Gruntfiles (`registerTask`, loadNpmTasks-implied tasks, CoffeeScript
  form) and gulpfiles (v3 `gulp.task` and v4 `exports.build`), lists
  the tasks on a knob — instantly, no node required to browse — and GO
  runs the dialed task via `npx grunt|gulp <task>`. Classic Web Bench
  preset wires CRATE → DYNAMO → IGNITION with MONITOR and VITALS.
- **Honest version currency for the classics** — the project header
  chips now name detected libraries with their version, and jQuery
  1.x/2.x reads `jquery 1.12.4 — EOL` (detection merges package.json,
  bower.json, and `<script src>` tags including versioned filenames).
- **Classic Kit (File → Classic Kit…)** — extend any codebase with the
  classics: add jQuery 3.7.1, MooTools 1.6.0, Prototype 1.7.3,
  Backbone 1.6.0 (+Underscore 1.13.7, wired first — it's a hard
  dependency), or Knockout 3.5.1 — **vendored** (pinned minified builds
  bundled with the IDE, ~527 KB, sha256-recorded in NOTICE-vendor.md;
  written to `vendor/`, script tags wired idempotently into index.html)
  or as **npm dependencies** (Prototype honestly refuses npm mode — no
  canonical package). Generate a `webpack.config.js` (entry
  auto-detected), `Gruntfile.js`, `gulpfile.js`, or `bower.json` for an
  existing project — never clobbering: an existing file gets a
  `.suggested` sibling instead. Running the kit twice is a no-op.
- **"Classic Web (jQuery)" template** — script-tag era, no build step:
  vendored jQuery 3.7.1, era-honest page skeleton, rack pre-wired with
  the Classic Web Bench.
- **CoffeeScript is a real language** — pinned TextMate grammar
  (48 grammars), Navigator outline (classes and `name: ->` methods, 45
  mimes), `#` comments, keyword completion, comment-only spellcheck.
  Registering `source.coffee` also un-pruned the CoffeeScript fences
  the Pug/Vue/Svelte grammars already referenced.
- **Completion knows the classic APIs** — when a project's deps or
  script tags show jQuery, MooTools, Prototype, Backbone, Underscore,
  or Knockout, their APIs join JS/HTML/CoffeeScript completion (295
  entries with signatures: `$.aj` → `$.ajax(url[, settings])`,
  `myEl.addC` → `.addClass` with the receiver preserved, `_.deb` →
  `_.debounce`). Detection walks up from the edited file and caches on
  manifest timestamps — no per-keystroke disk reads.
- **Doctor probes the era's tools** — webpack, grunt, gulp, bower,
  coffee, each with its install hint.
- rack 671 / editor 341 / tools 92 / ui 39 / project 17 tests;
  SpotBugs + find-sec-bugs at zero findings (two potential-XML-injection
  findings in the new kit fixed by idiom, not exclusion).

## [1.33.4] — 2026-07-05

### Fixed
- **Windows shortcuts, taskbar pins, and the installer now wear the NMOX
  icon.** The Windows sibling of the macOS Dock-icon gap below: the
  Start-menu and desktop shortcuts pointed at `bin\nmoxstudio64.exe`,
  whose embedded PE icon is the stock nbm-maven-plugin launcher stub
  (generic NetBeans), and the setup exe showed Inno Setup's default icon
  — the running app's window icon was branded, but nothing a user clicks
  to get there was. Now: `BrandingArtGenerator` emits a committed
  multi-resolution `packaging/icons/nmox-studio.ico` (16/32/48 as 32-bit
  BMP entries + 256 as a PNG-compressed entry, with a parse-back
  self-check), the Inno Setup script brands the setup exe
  (`SetupIconFile`), ships the `.ico` into `{app}` and points both
  shortcuts at it (`IconFilename`), and the release workflow patches
  every launcher exe's icon group with rcedit before packing — so the
  exe file icon and pinned-taskbar icon are branded too. A new
  `windows-installer-check` workflow (PRs touching the packaging surface
  + on-demand) builds the real installer on windows-latest,
  byte-verifies the branded 256px entry inside every launcher exe and
  the setup exe, then silent-installs and asserts both shortcuts resolve
  to the branded icon.

- **The .app bundle's Dock/⌘Tab icon is the NMOX rack again, not the
  default Java icon.** The cluster's generated launcher passes
  `-J-Xdock:icon=$progdir/../../nmoxstudio.icns` on macOS, which inside
  the bundle resolves to `Contents/Resources/nmoxstudio.icns` — but
  `build-dmg.sh` only ever wrote the hyphenated
  `nmox-studio.icns` (for Info.plist's `CFBundleIconFile`). A dangling
  `-Xdock:icon` path overrides the bundle's own icon attribution and
  macOS falls back to the generic Java icon. The DMG build now copies
  the icns under both names, satisfying the launcher's exact path (the
  same arrangement `application/pom.xml` has staged next to the bare
  cluster for dev launches since v1.30.0). Live-verified with an
  `--app-only` bundle on a throwaway userdir: the running app's Dock
  tile shows the NMOX icon, and the JVM's resolved `-Xdock:icon` path
  exists inside the bundle.

## [1.33.3] — 2026-07-05

### Fixed
- **The last fresh-launch startup storm.** After v1.33.2's coalescer,
  one more self-sustaining UI-thread loop remained (caught by thread
  dumps of the live hang): the Infra designer's `FlowCanvas.fit()` —
  and its sibling `selectNode()` — re-posted themselves via
  `invokeLater` at full speed whenever the canvas had no size yet. On a
  fresh launch the Infra tab is open-but-unselected, so its canvas
  stays 0×0 and the loop spun forever, starving first paint. Both
  sites now arm a single resize listener and run exactly once when a
  real size arrives (regression tests pin the bounded,
  arm-at-most-once behavior — and were proven to fail against the old
  code). A repo-wide audit of all 116 `invokeLater` sites, every
  paint/layout path, and every property/registry listener found no
  further self-sustaining loops. **Live-verified**: a fresh install
  now boots to a painted window with the event thread idle, the
  workspace tree rooted at `~/NMOX`, and zero permission prompts —
  the full first-launch experience the v1.33.x arc set out to fix.
  infra 170 tests; SpotBugs + find-sec-bugs clean.

## [1.33.2] — 2026-07-04

### Fixed
- **Fresh-launch startup no longer hangs before first paint.** v1.33.1
  moved several startup scans off the UI thread — which was correct, but
  it *unmasked* a latent feedback loop that the old blocking scan had
  been hiding. On a fresh userdir the window system opens and activates
  all ten default-open suite tabs in a burst, and the Project Explorer
  was running a full rebuild on **every** open/activate event (~20 of
  them), each fanning out per-row background detection that posted back
  to the UI thread and churned still more events — a self-sustaining
  storm that pegged the event thread at 100% CPU and starved first
  paint. Before v1.33.1 the first rebuild blocked forever on the old
  `$HOME` scan, so the loop never got a second iteration and stayed
  invisible. Fix: a **refresh coalescer** collapses a burst of events
  into a single rebuild (500 → 1, test-pinned), and the async row
  updates are now idempotent (no needless re-layout when a value is
  unchanged).
- **No stray `.nmoxinfra.json` written into a fresh workspace.**
  `InfraGraph.clear()` fired a change event even on an already-empty
  graph, and a flag-reset race let a plain project load schedule a
  spurious save — so aiming at the new empty `~/NMOX` wrote an infra
  file on every launch. `clear()` is now silent when empty and the
  load path captures its guard synchronously. rack 594 / project 17 /
  infra 166 tests; SpotBugs + find-sec-bugs clean.

## [1.33.1] — 2026-07-04

### Fixed
- **No more macOS permission storm on first launch.** A fresh launch
  with no recent project aimed the rack at your **home directory**, and
  Project Studio's file tree plus the Project Explorer's toolchain
  detection both enumerated it **on the UI thread** during startup.
  Walking `~` touches `~/Desktop`, `~/Downloads`, and `~/Pictures/Photos
  Library` — the macOS TCC-protected folders — so each listing fired a
  system permission prompt, and because it ran on the UI thread the
  prompts stacked and the main window couldn't finish drawing. Fresh
  launches now aim at a dedicated **`~/NMOX` workspace** (created on
  first run); only that folder is ever scanned, so the app never touches
  Desktop/Downloads/Photos and macOS never prompts. A recent or
  resurrected project still takes precedence — `~/NMOX` is only the
  no-project default.
- **A slow folder can no longer freeze first paint.** Every startup
  filesystem walk moved off the UI thread — FileTreePanel resolves
  children lazily on expand via a background worker (directories get a
  placeholder instead of a probing `File.list()`), and
  `ProjectInspector.detectKinds` runs off-thread with the header filled
  in asynchronously. A startup audit caught and fixed three more
  reachable-on-the-EDT walks (the Docker panel and the ROSETTA /
  package-manager devices' project-change handlers). rack 593 tests,
  project 14; SpotBugs + find-sec-bugs clean.

## [1.33.0] — 2026-07-04

Web3 becomes a first-class citizen: **Contract Studio** (⇧⌘6), Solidity
in the editor, Foundry in the toolchain, and two new rack devices. The
security boundary is the feature: the IDE never touches private keys.

### Added
- **Contract Studio** — a new `web3` module and suite tab. The tree
  scans Foundry `out/` and Hardhat `artifacts/` (auto-detected,
  `.dbg.json` skipped); the **Interact** pane builds call forms from
  the ABI — CALL view functions and read decoded returns, SEND writes
  and watch receipts land ("Mined in block N · gas used 21,000"),
  reverts decoded to their reasons, named custom errors included.
  Deploys poll their receipt and record into a per-project address
  book (`.nmoxweb3.json`, capped 200). Attach-by-address works for
  already-deployed contracts. ⌘I reaches contracts and deployments.
- **Observation: the Watch pane** — a 2-second poller follows your
  devnet: blocks ("#N · 3 txs · gas 42%") interleaved with ABI-decoded
  event logs (topic-matched via Keccak-256), newest first, ring-capped.
  Poll errors gray the connection chip; they never dialog-spam.
- **Oversight pane** — per-function gas table (`forge test
  --gas-report`, parsed tolerant of both table styles), every
  contract's bytes against the EIP-170 24,576-byte limit with headroom
  bars, and the deployments book with copy-address.
- **The no-private-keys boundary, test-pinned** — no key fields, no
  signing code, no mnemonics. Sends go through `eth_sendTransaction`
  on the node's own unlocked accounts (anvil/hardhat devnets); remote
  networks are read-only with the reason shown ("Read-only network —
  no unlocked accounts…"). RPC URLs that embed API keys live only in
  the OS Keyring; a secret network serializes with **no URL in the
  file** (pinned), and URLs are redacted to scheme+host in every
  message.
- **Solidity in the editor** — pinned TextMate grammar
  (vscode-solidity 0.0.187, MIT), `.sol` MIME, keyword completion,
  comment-aware spellcheck, Navigator outline (contracts, functions,
  modifiers, events, errors, structs, enums — 44 outline mimes), and
  `@nomicfoundation/solidity-language-server` in the LSP catalog.
- **Foundry toolchain** — `foundry.toml` recognized (21st manifest);
  IDE-native Build/Test/Clean → `forge build/test/clean`; rack AUTO
  lanes: VERITAS→`forge test` (with named-failure re-runs via
  `--match-test`), FORGE→`forge build`, GLOSS→`forge fmt`,
  TYPEGUARD→solhint (only when `.solhint.json` exists — honest hint
  otherwise), CRATE→`forge install`/`forge update`.
- **ANVIL device** (42nd) — your local EVM chain in the rack: PORT and
  BLK-TIME knobs, CHAIN-ID and FORK-URL params, READY trigger + URL
  signal on "Listening on…", SERVING gate, resurrection like every
  serve device, honest install hint when anvil is missing.
- **GOVERNOR device** (43rd) — the gas budget gate: `forge snapshot
  --check` with a TOLERANCE knob (0–25%); "WITHIN BUDGET" or the first
  offending diff on the LCD; no snapshot → fails closed. The quality-
  gates family (VITALS/VERITAS/GAUNTLET/PRISM/BEACON) gains its Web3
  member. **Web3 Bench** preset wires MASTER→FORGE→VERITAS→GOVERNOR
  with ANVIL free-running and MONITOR tapped.
- **Solidity learning space** (52nd) — chisel REPL (the ENGINE knob
  gains chisel automatically), Counter.sol sample, walked tutorial.
- **Doctor** probes forge, anvil, cast, chisel, solc, slither, solhint
  with real version commands and install hints. Welcome TOOLING gains
  Contract Studio.

### Internal
- Vector-pinned cores: hand-rolled Keccak-256 (official vectors plus
  OpenSSL-validated multi-block/rate-boundary cases), ABI codec pinned
  to the Solidity spec's own worked examples (baz/sam/f), JSON-RPC 2.0
  client with seam-tested transport, gas-report parser with zero
  regexes (ReDoS-proof by construction).
- web3: 245 tests, 89.5% line coverage (floor 0.80); editor 313,
  tools 85, rack 585 (+27 incl. both new devices under the 247-
  assertion contract test), SpotBugs + find-sec-bugs 0 findings, no
  new exclusions. docs/devices.md regenerated (43 device cards).

## [1.32.0] — 2026-07-04

DB Studio becomes a working DBA's tool: edit rows in the grid and apply
them as reviewable SQL, export any result, keep your queries, connect
from `.env`, and ask the planner what it's thinking. Closes debt ledger
#9 (results are read-only).

### Added
- **In-grid row editing with an honest Apply** — run a simple
  single-table SELECT (or just peek a table) against a SQL engine and
  the grid unlocks: edited cells tint amber, a chip counts pending
  edits, and **Apply…** shows the exact `UPDATE` statements it built —
  primary-key WHERE clauses, dialect-quoted identifiers — for review
  before anything runs. On failure it stops ("stopped after N of M"),
  keeps your edits for retry; on success the query re-runs so the grid
  shows database truth, not local hope. Grids that can't be edited
  safely say why in plain words: "Read-only — no primary key",
  "Read-only — not a single-table SELECT", "Read-only — document
  engine", "Read-only — <t> is a view". Primary-key cells aren't
  editable (the key is what addresses the row), NULL keys are refused,
  and typing `NULL` means SQL NULL.
- **Export CSV / JSON** — any result grid exports via a save dialog
  (UTF-8, default filename from the table), document engines included.
- **Persistent query history + saved queries** — every console run is
  journaled (newest-first, capped 50) into the project's `.nmoxdb.json`
  beside the connections and survives restarts; **Save…** names a query
  and puts it on the shelf for next session. Still no passwords in the
  file (test-pinned).
- **Connections from `.env`** — aim DB Studio at a project whose `.env`
  declares `DB_*` or `DATABASE_URL` (DB_* wins, all-or-nothing) and a
  quiet balloon offers to create the matching connection, dialog
  prefilled — password goes to the Keyring on save, never to disk. One
  offer per project per session, no modal ambush.
- **EXPLAIN button** — lights up for SELECT/WITH on SQL engines and
  runs the engine's native plan syntax (`EXPLAIN QUERY PLAN` on
  SQLite); the plan lands in a normal (read-only) result tab.

### Internal
- `SqlDialect` extracted as the one identifier-quoting seam
  (PeekQueries pinned byte-identical); `quote()` now doubles embedded
  quote chars so a hostile identifier can't break out. find-sec-bugs
  flagged ReDoS in a new numeric-literal regex — fixed by replacing the
  regex with a single-pass scanner, not by exclusion.
- dbstudio: 317 tests (was 163), 87.1% line coverage (floor 0.73),
  SpotBugs + find-sec-bugs at 0 findings.

## [1.31.0] — 2026-07-04

The REPL grows up: pick an engine from the rack, and missing interpreters
install themselves. Born from a live session — creating the Common Lisp
learning space on a machine without clisp meant a trip to the terminal.
Never again.

### Added
- **ENGINE knob on the REPL** — turn it to lisp, python, node, ruby,
  haskell, elixir, sqlite, redis… (26 engines, derived at runtime from
  the learning catalog's own REPL drivers, deduped) and the command
  field fills itself with the *correct* invocation — force-interactive
  flags included (`python3 -i -q`, `node -i`). Picking an engine also
  seeds its HINTS snippets (hints now work for any dialed engine, not
  just generated learning spaces) and its per-OS install command. Type
  your own command and the knob flips to CUSTOM — presets never fight
  manual control. Saved patches win over knob seeding on load.
- **INSTALL button on the REPL** — armed exactly when the dialed
  interpreter has a known install command and isn't running. Click →
  the install (brew/apt/choco, from the curated catalog) streams onto
  the REPL screen → "installed — press START". Dial haskell without
  ghci and the path from curiosity to a live GHCi prompt is two clicks.
  Never auto-starts; STOP-ALL kills an in-flight install.
- **The learning-space picker warns before you commit** — selecting a
  space live-probes its interpreter off-EDT and shows "requires clisp —
  ✓ found" or "✗ not found · brew install clisp" under the blurb,
  before anything is created.
- Catalog integrity test: the well-known interpreter spaces must carry
  mac/linux/windows install commands — no future space ships without
  its escape hatch.

## [1.30.2] — 2026-07-04

### Fixed
- **The ⌘Tab switcher labeled dev launches "java"** — the switcher name
  comes from the app bundle's Info.plist and only attributes when the
  JVM runs from inside a `.app`; no `-Xdock` flag can reach it. The DMG
  script gained `--app-only`: it stops after building the real bundle
  (embedded runtime included), giving dev play-testing full macOS
  fidelity — icon, menu name, and switcher label. Packaged users were
  always correct; this closes the last dev-launch branding gap.

## [1.30.1] — 2026-07-04

### Fixed
- **NPM Explorer is no longer a dead end without a project** — where it
  said only "No package.json found", it now lists the globally installed
  packages (`npm ls -g --depth=0`, sorted, with versions and a count),
  with Install sensibly disabled ("Open a project to install its
  dependencies"). npm's benign non-zero exits (extraneous/invalid
  findings) are tolerated — the JSON is parsed regardless — and a
  missing npm shows an install hint instead of silence. The parse seam
  is pure and test-pinned.

## [1.30.0] — 2026-07-04

DB Studio joins the platform: connections configured in the NetBeans
**Services** window now work in DB Studio directly. Plus the dev-launch
branding trilogy completes.

### Added
- **The Services bridge** — DB Studio's tree gains a "Services" branch
  listing every connection from the NetBeans Database Explorer
  (`ConnectionManager`), live-updated as you add/remove them there.
  Select one and it behaves like a native connection: browse tables,
  double-click-peek, run console SQL, grids/history/cancel. The bridge
  wraps the **live `java.sql.Connection` NetBeans manages**, so any
  database Services can reach — Java DB/Derby, Oracle, anything with a
  registered driver — runs in DB Studio's console, with NetBeans owning
  drivers, credentials, and lifecycle. Passwords never pass through our
  code; close() never closes NB's shared connection; Edit/Remove/Test
  stay honestly disabled ("Managed in the Services window"). Peeks on
  non-bundled engines use SQL-standard `FETCH FIRST n ROWS ONLY` with
  double-quoted identifiers (Derby and Oracle reject `LIMIT`).
- The JDBC statement loop was **extracted into a shared `JdbcCore`**
  (DbClient shed 142 lines, its SQLite e2e suite untouched), so both
  backends run one proven engine. The Services adapter is verified by a
  fully headless test through the real NetBeans API — driver
  registration → connection → our backend → temp SQLite → rows.

### Fixed
- **⌘Tab and the dock showed the Java coffee cup on dev launches** —
  the generated launcher hardcodes `-Xdock:icon=<app-parent>/
  nmoxstudio.icns`, which only the packaged .app satisfied. The icns is
  now a committed asset (built once from the same iconset the DMG uses)
  and the application build copies it to exactly that spot. Completes
  the dev-branding trilogy (window title and menu-bar name shipped in
  v1.29.2).

## [1.29.2] — 2026-07-04

Two more live-observation fixes, straight from the user's screen:

### Fixed
- **The macOS app menu said "nmoxstudio"** on dev/bin-script launches —
  the generated launcher defaults the dock name to the executable
  basename. The conf now sets `APP_DOCK_NAME="NMOX Studio"` (read only by
  the launcher's Darwin block; inert elsewhere). The packaged .app was
  already correct via its own wrapper.
- **The Welcome launchpad's TOOLING column didn't list DB Studio** — the
  one suite tab missing from it. "DB Studio  ⇧⌘7" now sits between
  Project Studio and API Studio.

## [1.29.1] — 2026-07-04

DB Studio's live click-through (the house tradition) said it plainly:
"a little raw, could use some working buttons." Every raw edge found,
fixed:

### Fixed
- **Double-clicking a table now shows its data** — the gesture every
  database tool owes its user. Double-click a table/collection/database
  in the tree and an engine-appropriate peek query fills the console and
  runs (`SELECT * … LIMIT n` with dialect quoting / Mongo `find` /
  Couch Mango). A Couch database the connection isn't aimed at explains
  itself instead of querying the wrong thing.
- **Async feedback you can't miss** — Test and Connect outcomes now
  raise balloon notifications (with the actual error) in addition to
  the status strip. A failed Test no longer looks like a dead button.
- **RUN gates honestly** — disabled until a connection is selected,
  with a tooltip saying why; no more silent no-op clicks. Re-entry
  during a run is blocked.
- **First-run guidance** — an empty DB Studio now says "No connections
  yet — click Add below to create one" in the tree and hints the
  double-click-to-peek gesture in the status strip, instead of a blank
  tree with greyed buttons.
- **SQLite Browse can create a new database** — the file chooser was
  open-style (couldn't select a file that doesn't exist yet); now
  save-style, matching "created on first use" reality.

## [1.29.0] — 2026-07-04

**DB Studio** — a first-class database management suite in its own tab,
alongside the Rack, the Infra Designer, and API Studio. And the whole
suite now greets you on first launch.

### Added
- **DB Studio (⇧⌘7)** — a new `dbstudio` module. Define connections,
  browse structure, run queries, read results in grids — for five
  engines minimum: **SQLite, PostgreSQL, MySQL, MariaDB, MongoDB, and
  CouchDB**.
  - **Batteries included**: drivers are bundled — MariaDB Connector/J
    (LGPL-2.1; one driver speaks both MySQL and MariaDB), PostgreSQL
    (BSD-2), SQLite/xerial (Apache-2), MongoDB `mongodb-driver-sync`
    (Apache-2). CouchDB needs no driver at all — it rides the shared
    HTTP client against Couch's REST API.
  - **Passwords never touch disk**: connection specs persist per project
    in `.nmoxdb.json`; secrets live only in the OS keychain via the
    platform Keyring.
  - **One tree, per-engine semantics**: connection → tables/views (SQL),
    collections (Mongo), or databases (Couch) → expand for columns or an
    honest one-document shape sample.
  - **Kind-aware console**: SQL engines get the SQL editor kit
    (highlighting included); document engines get a JSON editor — Mongo
    speaks Extended-JSON command documents (`{"find": "users", …}`),
    CouchDB speaks Mango selectors (bare selectors auto-wrapped, plus an
    `_all_dbs` convenience). Scripts split per statement — respecting
    strings, identifiers, and comments — and an error in one statement
    never stops the rest.
  - **Results**: one tab per statement (rows / update counts / errors),
    per-statement elapsed times, row-limit spinner with honest "first N
    rows only" truncation flags, and a History tab (last 50 runs,
    double-click to reload). Runs execute off the UI thread; CANCEL
    actually cancels in-flight SQL.
  - **⌘I reaches databases**: Quick Search finds connections and
    already-discovered tables/collections and jumps to them in the tab.
  - The engine is *proven*, not promised: 150 module tests including
    real end-to-end JDBC against SQLite files in CI, canned-JSON parse
    seams for CouchDB, and a 0.73 coverage floor from measured numbers.
- **The whole suite opens by default now.** A fresh install lays out
  **Workbench → Rack → DB Studio → Infra Designer → API Studio →
  Docker** as tabs, with the Welcome launchpad selected — discovery by
  seeing, not by hunting menus. The window system still remembers your
  arrangement: close a tab once and it stays closed. (Boot smoke
  verified: all six tabs construct headless, 6s boot unchanged.)

### Deliberately not done (ledger)
- Editing rows in the results grid — read-only results; `UPDATE`/
  Mongo update commands in the console are the honest v1.
- Mongo in-flight kill and cursor continuation (`getMore`) — CANCEL is
  documented as a no-op there; first batch only.
- Live Mongo/Couch integration tests — the logic is seam-tested against
  canned responses (the same status as the cloud providers' live calls);
  SQLite carries the real end-to-end burden.
- SSH-tunneled connections — HELM runs remote commands today; tunneling
  merits its own design if demand shows.

## [1.28.0] — 2026-07-04

The LAMP/LEMP sprint: the IDE audited from a senior LAMP/LEMP engineer's
chair — PHP, MySQL/MariaDB, nginx/Apache, Linux deploys — and every gap in
that daily loop built. PHP was already half a citizen (composer/phpunit/
Xdebug/intelephense); now it's a first-class one.

### Added
- **ARTISAN — the Laravel console** (the rack's 41st device), in Laravel
  brand red: SERVE / TEST / MIGRATE / FRESH(+seed) / QUEUE / ROUTES on the
  action knob, `composer.lock` version currency with an async
  latest-version probe, and real long-runner wiring — READY fires once on
  Laravel's "Server running on" line, URL emits on change, the SERVING
  gate drops when the process dies. Tinker is deliberately not a knob
  target — the REPL device handles interactive (the How-to-use card says
  so).
- **TYPEGUARD and GLOSS gained PHP lanes.** On a PHP project, TYPEGUARD
  AUTO runs `vendor/bin/phpstan analyse` and feeds phpstan's findings into
  the same diagnostics bus (squiggles included); GLOSS runs Laravel Pint,
  honoring WRITE/CHECK. The tsc and Prettier lanes are byte-identical and
  test-pinned.
- **IGNITION's php target serves for real**: `php -S 127.0.0.1:8000 -t
  public` when a composer-era `public/` docroot exists, root fallback
  otherwise.
- **Apache config editing** — `.htaccess`, `httpd.conf`, `apache2.conf`,
  and `*.vhost` now highlight (pinned MIT grammar, self-contained), with
  `#` comment-toggle and comments-only spellcheck. Generic `.conf` is
  deliberately not claimed. The "A" in LAMP, finally.
- **PHPUnit in Run Focused Test** — the test under the caret runs via
  `vendor/bin/phpunit --filter <name>` (global fallback), matching both
  `public function testX` and `#[Test]` shapes.
- **"PHP Web (LEMP)" project template** — 14 honest files: guarded front
  controller that works pre-`composer install` and doubles as a cli-server
  router (`composer serve`), PSR-4 `src/` + a passing PHPUnit test,
  `phpstan.neon.dist` (so the TYPEGUARD lane works out of the box),
  `.env.example`, a working three-service `docker-compose.yml`
  (nginx + php-fpm + MariaDB), the matching `docker/nginx.conf`
  (front-controller `try_files`, `fastcgi_pass`), and
  `deploy/cloud-init.yml` — a commented LEMP bootstrap you paste into a
  droplet's user_data in the Infra designer. Validated for real:
  `php -l` clean, compose config passes, PHPUnit ran the generated test
  green.
- **LAMP Bench rack preset** — CRATE (composer) fans out to VERITAS
  (phpunit) + TYPEGUARD (phpstan) + GLOSS (pint --test) with IGNITION
  serving and everything on the MONITOR bus; the template's rack aims at
  it automatically.
- **Dockerize learned PHP** — multi-stage `composer:2` →
  `php:8.3-fpm-alpine` Dockerfile with an nginx sidecar in the generated
  compose (port 80), plus `vendor/`+`.env` in `.dockerignore`.
- **Environment Doctor probes the LAMP stack**: composer, mysql client,
  nginx (whose version prints to stderr via `-v` — handled), apachectl.
- **The Database Explorer was already on board — now documented.** The
  app ships NetBeans' full DB tooling (10 modules: connections, schema
  browsing, SQL editor with result grids, MySQL integration). Point it at
  your MySQL/MariaDB from the Services window; bring the Connector/J jar
  and the driver-registration UI does the rest.

### Fixed
- **Dockerize crashed writing nested output paths** — files like
  `docker/nginx.conf` hit a missing-parent `NoSuchFileException`; parents
  are now created.
- **Every project template's README rendered its headings as code
  blocks** — the generator emitted 12-space-indented Markdown (a
  text-block min-indent bug). All templates now emit flush-left READMEs.

### Deliberately not done
- **No MySQL learning space** — learning-space REPLs launch local
  interpreters; `mysql` needs a live server to connect to, which breaks
  the type-in-and-learn model. The SQLite space already teaches SQL
  against a real engine.

## [1.27.0] — 2026-07-03

The coverage sprint: raise test coverage across the whole codebase to the
point where every module's *testable* logic is exercised, then lock it in
with per-module JaCoCo floors so it can't quietly regress. "Full coverage"
here means honest coverage — pure-Swing windows, dialogs, and canvases that
can't be unit-tested without a live windowing system are excluded by name
with a written reason (mirroring the SpotBugs-exclude discipline), not
chased with brittle tests; everything with a branchable path is tested.

### Added
- **~320 new unit tests** across editor, rack, tools, apiclient, infra,
  ui, and project — completion providers and their item classes, editor
  actions (comment-toggle, smart-break, focused-test command building),
  the LSP catalog, every rack device's command-building and `receive()`
  logic, the control-widget models (Knob/Led/VuMeter/LcdDisplay/Toggle),
  API Studio's table models and header grader, the infra DeployPlanner /
  NodeKind cost math / graph / GraphIO, and the ui-resident action logic.
- **Per-module coverage floors on all eight code modules.** Six existing
  floors were raised to just under the achieved level; **project and ui
  gained their first floors.** Now enforced (LINE coverage of the testable
  surface): core 0.78, apiclient 0.78, infra 0.73, rack 0.63, editor 0.55,
  tools 0.54, project 0.50, ui 0.12.
- **An honest coverage-exclusion policy** in the root `pom.xml`: pure-UI
  glue is excluded at the JaCoCo instrumentation level (so the report and
  the floor both reflect the testable surface), each entry a named class
  with a one-line rationale. Control widgets are *not* excluded — their
  model logic is tested; only `paint()` stays uncovered.

### Fixed
- **`WorkspaceTrust` could overflow the preferences store and break trust
  on a long-lived install** — a real latent bug the extra tests surfaced.
  Trusted project paths were persisted as one `File.pathSeparator`-joined
  string under a single preference key; `java.util.prefs` caps a value at
  8 KB, so a user who trusted enough projects over time would eventually
  hit "Value too long" on the next trust and every subsequent one. It's
  now **one entry per path** (hash key, path value — neither the value nor
  the key can overflow), with automatic migration off the legacy key. A
  regression test trusts 200 long paths (≈24 KB joined, three times the old
  ceiling) without throwing. On CI this stayed invisible because the prefs
  node starts empty each run; it only bit once the local suite had
  accumulated enough trusted paths across the day.

### Coverage before → after (module LINE %, testable surface)
`core 68→83 · editor 43→59 · tools 21→58 · rack 51→67 · apiclient 38→83 ·
infra 46→77 · project 8→55 · ui 0.4→15`. ui stays low by design — it is a
thin presentation shell whose logic (the PWA/Standards/Doctor kits, the
learning catalog, update-tag parsing) lives in rack/core and is tested
there; the floor guards the small slice of genuine ui-resident logic.

## [1.26.0] — 2026-07-03

The complete-system sprint: everything the debt ledger recorded as
half-done "with a reason" was taken back up and finished — or its
deferral was re-examined against the code and firmed into a decision.
Six feature-shaped debts (0a–0f) shipped; the seven refactor-shaped
ones were each re-inspected, and two of them (the toolchain switches
and the faceplate boilerplate) turned out to have no duplication worth
removing once read again rather than remembered. A deferral you can
defend after re-reading the code is a decision, not a guess.

### Added
- **Rack undo/redo (⌘Z / ⇧⌘Z).** Every structural rack edit — add,
  remove, move a device; connect or disconnect a cable — is now
  reversible, with a 100-deep bounded history. Removing a device and
  undoing it brings the device back *with its cables re-patched*. Bulk
  operations (default-rack load, patch autoload) are deliberately
  non-undoable so the history starts clean on the patch you loaded.
- **Quick Search reaches into API Studio and the infra designer.** ⌘I
  now finds API requests by method and name (jumps to the request in
  API Studio) and infra nodes by name and kind (selects them on the
  canvas) — cross-tab navigation that previously stopped at projects
  and rack devices. (Closes ledger 0a.)
- **"Sync from cloud" is genuinely multi-provider.** The infra
  designer pulls live resources from DigitalOcean, Hetzner Cloud
  (servers, networks, load balancers, volumes, firewalls, floating
  IPs), and Cloudflare (zones → DNS records) in one sweep. Each
  provider is isolated — one cloud's outage or bad token reports as a
  per-provider failure without aborting the rest — and re-syncing
  refreshes existing nodes in place instead of stacking duplicates.
  (Closes ledger 0b.)
- **TAIL and TEMPO survive a crash.** The two timer devices now report
  as resumable from their arm switch (FOLLOW / CLOCK) and re-arm on
  resume, so a kill -9 mid-follow or mid-clock comes back running —
  without inflating the status line's process "running" count, which
  stays process-only. (Closes ledger 0d.)
- **Eight more Navigator outlines.** Structure view (⌘7) now populates
  for Haskell, OCaml, R, Perl, Julia, F#, Crystal, and Zig — 43 mimes
  with an outline, up from 35. (Closes ledger 0e.)
- **An Options checkbox for the startup update check.** Tools >
  Options > NMOX Studio > "Check for updates on startup" flips the
  `updateCheck` preference that previously had no UI. (Closes ledger
  0f.)
- **SOLDER exports to CI.** The any-command device now emits a GitHub
  Actions `run:` step alongside the other build steps; PREFLIGHT
  stays out on purpose (in CI the workflow *is* the ship gate).
  (Closes the SOLDER half of ledger 0c.)

### Changed
- The debt ledger (`docs/engineering/tech-debt.md`) was rewritten:
  0a–0f moved to "Closed by v1.26.0," and the refactor items 1–7 were
  re-audited with fresh evidence. #7 (rack-palette startup cost) was
  measured — a 7-second cold boot is dominated by JVM warm-up and
  first-run module install, not the ~0.3s palette build — and firmed
  to "won't fix until a profiler names it." #1 and #2 firmed to
  won't-fix once re-reading the code showed the "duplication" was a
  shared *idiom*, not shared *values* (each device places its
  transport cluster at its own coordinates; each toolchain switch maps
  the same enum to a *different* verb).

### Tests
- `RackUndoTest` (add/undo/redo, cable restoration, redo invalidation,
  bulk-load-not-undoable), `TimerResumeTest` (idle-not-resumable,
  armed-resumable-not-live, resume-rearms), `CloudSyncTest` (all
  provider endpoint shapes, failure isolation, dedupe-refresh), plus
  the new search-provider and Options-panel tests. Full suite green;
  DeviceContractTest's 241 assertions unchanged.

## [1.25.1] — 2026-07-03

The cleanup: loose ends tied off, including two real bugs the deferred
live click-throughs caught the moment the screen unlocked.

### Fixed
- **REPLs that buffer piped stdin now force interactive mode.** The
  live smoke proved the engine perfect and the data wrong: python3
  (and node, lua, julia, ts-node) treat piped stdin as a script and
  execute only at EOF — you typed, nothing answered until STOP. Their
  catalog commands gained the force-interactive flags (`python3 -i
  -q`, `node -i`, …): type an expression, read the answer immediately.
- **The Environment Doctor probes go with `go version`** — the one
  toolchain that rejects `--version` no longer shows a garbled status
  line for a perfectly healthy install.

### Verified live (no code change)
- The v1.25.0 Welcome launchpad, the Environment Doctor (24 of 32
  tools found on this machine, with `clisp ✗ brew install clisp`
  rendered exactly as designed), and the full Learning Space flow —
  picker via ⇧⌘L, Common Lisp leading the catalog, Python space
  generated with tutorial + seeded REPL, live type-in round trip.

### Tidied
- README gained the v1.25 features section; CLAUDE.md caught up;
  the debt ledger records the one new deferral (an Options checkbox
  for the update-check preference); the ui module got its first test
  (UpdateCheck's tag parsing); one unused import; five stale local
  branches pruned.

## [1.25.0] — 2026-07-03

The daily-driver polish: after twenty-four releases of capability, the
weakest surfaces were the first ones a user meets. This release makes
the front door, the environment, and the upgrade story honest.

### Added
- **The Welcome screen is a launchpad again.** It was frozen at v1.0 —
  three buttons, unaware of everything since. Now: a Start column
  (New Project / New Experiment / New Learning Space / Open Folder,
  shortcuts shown), a live Recent column (your actual projects, click
  to aim the studio — refreshed every time the tab shows), a Tooling
  column with every window and its keystroke, and an honest footer:
  the stamped version plus "What's new ↗" to the release notes.
- **Environment Doctor (Tools menu).** The studio leans on ~40
  external tools — the core four, a toolchain per language lane, and
  an interpreter per learning space — and each device already speaks
  up alone. The Doctor sweeps them all at once: one table, every tool
  probed live with `--version` through the same hardened launcher the
  devices use — ✓ with its version line, or ✗ with the install
  command that fixes it. The checklist extends itself from the
  learning catalog, so new spaces are covered automatically.
- **Update awareness.** Once a day, fifteen seconds after startup and
  entirely off the EDT, the studio asks GitHub for the latest release
  tag and — only when it outranks the version this build was stamped
  with — shows one quiet notification linking to the releases page.
  Dev builds never check, offline never nags, and the `updateCheck`
  preference turns it off. Version arithmetic is numeric (1.9 < 1.24),
  tested in core.

## [1.24.0] — 2026-07-03

Learning Spaces: projects that exist to be learned from. Pick a
language, framework, or library and the studio generates a real
project — sample code, a tutorial that walks it, and a rack already
wired with a REPL pointed at the right tool. Practice Lisp and you get
a CLISP prompt, starter expressions, and something to type in seconds.

### Added
- **A real REPL in the rack.** The new REPL device launches an
  interactive interpreter (clisp, python3, node, ghci, iex, irb,
  sqlite3, redis-cli — anything with a prompt), keeps its stdin open,
  and lets you type an expression and read the answer in the
  scrollback. HINTS lists the starter snippets a learning space seeds.
  This is a genuinely new capability: the command devices run with a
  closed stdin so a prompt can never hang them — a REPL is nothing but
  a prompt, so it needed its own interactive-process engine
  (`InteractiveProcess`), proven end to end in tests against `cat`.
- **New Learning Space… (File menu, ⇧⌘L).** A searchable picker of
  **51 spaces** across languages, frameworks, and libraries. Choose
  one and the studio writes the sample files, a TUTORIAL.md that walks
  them (with the install command for your OS), and a pre-wired rack —
  then opens all three. REPL spaces get a seeded REPL; run-based spaces
  (dev servers, builds) get SOLDER wired into a MONITOR.
- **The catalog is data, not code** (`learn-catalog.json`) — 25
  languages (Common Lisp, Python, JS/TS, Ruby, Go, Rust, Java via
  jshell, C/C++/C#, Swift, Kotlin, PHP, Lua, Haskell, Elixir, Erlang,
  Clojure, Scala, Julia, R, Perl, Bash, SQLite), 13 frameworks (React,
  Vue, Svelte, Angular, Next.js, Express, Django, Flask, Rails,
  Phoenix, Spring Boot, Laravel, ASP.NET — the console-bearing ones
  wire their real REPL: rails console, manage.py shell, iex -S mix,
  artisan tinker), and 13 libraries (NumPy, Pandas, PyTorch, jQuery,
  D3, Three.js, Redux, RxJS, Tailwind, GraphQL, Prisma, Redis, Docker
  Compose). Each entry is honest about what it needs: if a tool isn't
  installed, the REPL says so and the tutorial carries the install
  command. New spaces are a JSON edit, never a recompile.

### Notes
- Spaces persist under `~/.nmox/learn/<slug>`, pre-trusted, reusing the
  directory for the same slug rather than piling up copies.

## [1.23.0] — 2026-07-03

The completeness release: four audits asked "did every idea ship
whole?" — every dead-end, asymmetry, and dangling promise they found
is now finished or honestly ledgered.

### Added
- **Experiments, managed (File → Experiments…, ⇧⌘X).** The New
  Experiment dialog has promised "Promote it later" since v1.11 — now
  you can. Lists every experiment with template and age; Open aims the
  studio, Promote graduates a keeper (move out of ~/.nmox/experiments,
  drop the marker, git init, opened for real — recents and all),
  Discard stops anything running there and deletes the tree, still
  refusing anything unmarked.
- **Hetzner is a first-class cloud.** Drift Refresh, Destroy stack,
  cloud-init user_data, and server-IP capture (ssh command) now work
  for all six Hetzner kinds exactly as for DigitalOcean; Cloudflare
  DNS records drift-check and destroy through their zone. R2 buckets
  stay honestly unverifiable (the API returns no addressable id).
- **Any-provider live deploys.** The deploy gate demanded a
  DigitalOcean token even for pure-Hetzner stacks — now a stack goes
  live when every provider it actually uses has its token, and dry-run
  names the missing ones. En route this killed a real bug: the old
  gate read tokens from a different preferences node than the Tokens
  dialog wrote, so stored DO tokens were invisible to it.
- **VITALS gates all four Lighthouse categories.** The GATE knob grew
  `best`, `seo`, and `all` (append-only — patched knob indexes stay
  stable); the failure LCD names exactly which category closed the gate.
- **Two presets for the invisible 60%.** Ship Gate chains prod build →
  Lighthouse floor → bundle budget → PREFLIGHT verdict → armed deploy;
  Dev Intelligence surrounds the dev server with clocked health probes,
  SONAR port sweeps, BLACKBOX, and TAIL on the console.
- **Navigator outline for the BEAM + Lisp languages** (32 → 35 mimes):
  Elixir defmodule/def/defp/defmacro nested by indentation, Clojure
  top-level forms as a flat list, Erlang module attributes plus
  column-0 function clause heads.

- **Window shortcuts for the all-day tools**: API Studio ⇧⌘8, Infra
  Designer ⇧⌘9 — parity with the rack (⌘9) and Docker (⌘8).

### Fixed
- **Wizards validate their inputs.** Standards Kit refuses a blank
  name, an unparseable site URL, or a non-address security contact
  before writing anything; PWA Kit checks the artwork file exists and
  is readable (full path in the message) and refuses a blank app name.
- **Racking a preset with BLACKBOX no longer scans your home
  directory** — the preset builder's throwaway rack aimed at
  ~/. by default, and BLACKBOX's changed-since-last-green walk plus
  REFLEX's watcher baseline could hang for minutes. It aims at an
  empty scratch directory now.
- The toolbar button says **"Sync from DigitalOcean"** — which is what
  it does; the deploy tooltip and destroy confirmations name each
  node's actual provider.

### Removed
- **Six phantom NpmService methods** (addPackage, removePackage, update,
  audit, listPackages, init) — zero non-test callers; the rack's CRATE
  and AUDIT devices superseded them.

### Deferred with reasons (docs/engineering/tech-debt.md)
- Quick Search for API Studio/infra nodes; multi-provider sync;
  CI-export scope for gates/observers; TAIL/TEMPO resurrection;
  outlines for the remaining 8 grammar-only languages.

## [1.22.0] — 2026-07-03

The Snow Leopard release: no new features. Six parallel audits swept
the whole codebase; what they found got fixed, consolidated, tested,
or honestly written down as deferred. Everything is faster, cleaner,
and truer — nothing is different.

### Fixed
- **Explicit UTF-8 at 12 file I/O sites** that used the platform
  default charset — including session-state JSON (resume could corrupt
  on non-UTF-8 systems), CI-workflow export, docker-compose writes,
  and package.json parsing.
- **Wizard writes left the EDT.** Standards Kit and PWA Kit did disk
  I/O (and PNG encoding) inside the event dispatch; both now run on a
  request processor and hop back for the report.
- **API Studio autosave no longer fails silently.** A chronically
  failing 800ms autosave meant silent data loss; it now warns once per
  failure streak and logs the cause.
- **Two timers that never stopped** (status-line 2s poll, session
  snapshots at 5s) are lifecycle-bound.
- **Relaunching a running command skewed its elapsed readout** — start
  times are now captured per launch.
- **PING's history capped at record time**: fifty retained multi-MB
  response bodies was a leak, not a console log.

### Changed
- **JOptionPane is gone.** 26 call sites across 12 rack files now use
  the platform's DialogDisplayer — themed, consistent — and raw
  exception dumps became task-oriented messages ("Could not save the
  patch: …").
- **ProcessSupport and ToolLocator moved to core.** The editor no
  longer depends on the rack just to launch processes, and the tools
  module's npm probe finally gets the augmented PATH (npm was
  "missing" when the IDE launched from Finder). The hand-rolled copy
  in the template generator — with its Windows-breaking hardcoded
  /dev/null — is gone.
- **One HTTP connection pool** (core HttpClientFactory) replaces four
  per-module clients; **one JsonUtil** replaces three copies of the
  JSON sniff/pretty-printer (String-only API — org.json types never
  cross NBM module boundaries).
- **FlightRecorder's journal loads off the boot path**, and the boot
  smoke test now prints a boot-to-exit time CI can trend.

### Tests & docs
- Coverage backfill for the untested pure logic in core, tools,
  apiclient, and infra, with JaCoCo floors set from measured coverage.
- Truth pass: CLAUDE.md caught up four releases (v1.17 → v1.21, 39
  devices); ~30 v0.x aspirational docs gained historical banners; three
  test scripts that checked classes that never existed were deleted;
  docs/engineering/tech-debt.md rewritten as the honest current ledger
  — every open item carries the reason it was deferred.

## [1.21.0] — 2026-07-03

The PWA sprint: one wizard and the aimed project becomes installable —
icons forged in-process, a service worker that precaches what the
project actually has, and index.html wired without clobbering a thing.

### Added
- **PWA Kit wizard (File → PWA Kit…).** App name, short name, theme
  and background colors, an icon source, a caching strategy — and the
  project gains the full installability set. Existing files are never
  overwritten; the report says exactly what was written, skipped, or
  wired.
- **Icon generation, zero tools required.** Plain Java2D renders the
  whole set: icon-192/512 (rounded plate, transparent corners), the
  maskable pair (full-bleed, glyph held in the W3C safe zone), and an
  opaque apple-touch-icon. Two sources: a 1–2 letter monogram on your
  theme color for projects without art yet, or your own image scaled
  crisply (progressive halving) to every size. The manifest's icon
  promises finally ship with the pixels to back them.
- **A service worker written to be read.** Vanilla sw.js — versioned
  cache, precache on install, stale-cache cleanup on activate — with a
  strategy you choose: app shell (cache-first, instant loads,
  offline-ready) or network-first (always fresh, cache fallback).
  GET-only, same-origin caching, and an offline.html fallback for
  navigations, generated in your theme. The precache list is built
  from the files the project actually has, not a guess.
- **Installability-complete manifest.** site.webmanifest with both
  icon purposes (any + maskable), scope, display, and your colors —
  beyond the Standards Kit's baseline.
- **index.html wired idempotently.** Manifest link, theme-color meta,
  apple-touch-icon link, and the service worker registration are
  inserted before </head> — each piece only if missing. Running the
  wizard twice changes nothing.

## [1.20.1] — 2026-07-03

Patch: a defect found by driving the released IDE with computer
automation — clicks, keystrokes, and on-disk assertions against the
running app.

### Fixed
- **Follow-up dialogs no longer stack behind the main window.** A
  dialog opened in the same event dispatch that was still disposing its
  predecessor (the Standards Kit outcome report after the wizard, the
  New Experiment error report, API Studio's variables editor after
  naming a new environment) could open *behind* the main window —
  modal, invisible, every menu action greyed: a soft-locked app with no
  visible reason. Confirmed live via window enumeration; each follow-up
  dialog is now deferred one dispatch so its predecessor is fully gone
  before it shows.

### Verified live (no code change)
- The automation run also battle-tested the paths CI could only
  logic-verify: a real HTTPS send through API Studio (200, timed,
  sized, headers graded on the Standards tab), .editorconfig
  save-enforcement in the running editor (trailing whitespace trimmed,
  final newline added, on disk), experiment lifecycle (scaffold →
  no-git marker → external-delete resilience), Standards Kit files
  spec-checked on disk, and the Infra Designer's fit/zoom controls.

## [1.20.0] — 2026-07-03

The standards sprint: the web's specs, supported with gusto — text
standards enforced on save, well-known files generated by wizard,
security headers graded on every request, and WCAG as a shipping gate.

### Added
- **.editorconfig is honored, not just highlighted.** Every save now
  applies the standard for real: `trim_trailing_whitespace` and
  `insert_final_newline` (both directions), with full spec semantics —
  glob sections (`*`, `**`, `?`, `[seq]`, `{alt}`, `{n..m}`), upward
  file discovery stopping at `root = true`, closer-file and
  later-section precedence. Minimal-edit application: the caret keeps
  its place. No .editorconfig, no change.
- **Standards Kit wizard (File → Standards Kit…).** One dialog and the
  project gains the web's well-known files, each correct to its spec:
  robots.txt (RFC 9309) with a Sitemap pointer, sitemap.xml
  (sitemaps.org protocol), site.webmanifest (W3C, with the members
  installability requires), .well-known/security.txt (RFC 9116 —
  required Contact and a genuinely RFC 3339 Expires one year out), and
  humans.txt. Existing files are never overwritten.
- **Security headers graded on every send.** API Studio responses gain
  a Standards tab: HSTS, CSP, X-Content-Type-Options, anti-clickjacking
  (frame-ancestors or X-Frame-Options), Referrer-Policy,
  Permissions-Policy, and COOP — value-aware verdicts (a short max-age
  warns; unsafe-inline warns) with a deterministic letter grade and a
  named fix for every miss.
- **Accessibility is a shipping gate.** VITALS' floor learns which
  standard it holds: the new GATE knob selects perf, **a11y (WCAG)**,
  or both — an inaccessible page can now close the deploy gate exactly
  like a slow one.

### Fixed
- The security.txt generator's first draft emitted an Expires without
  seconds — invalid RFC 3339. Its own spec test caught it before it
  shipped; that is what the spec tests are for.

## [1.19.0] — 2026-07-02

API Studio — a Postman-style suite in its own tab, beside the rack and
the infra designer.

### Added
- **API Studio (Window → API Studio).** A new module and tab for
  managing and testing HTTP APIs:
  - **Collections** of saved requests in a tree, with add/delete.
  - A **request builder**: method, URL, query params and headers
    (toggleable rows), request body, and auth (bearer or basic).
  - **Environments** with `{{variable}}` substitution, so one saved
    request travels from localhost to staging to prod by switching the
    environment instead of editing URLs.
  - **Tests**: per-request assertions — status is, time under N ms,
    body contains, JSON has path (`data.user.id`, `items.0.sku`),
    header present — evaluated on every send, green/red per line. A
    probe becomes a check.
  - A **response viewer**: status, time, size, pretty-printed JSON
    body, and response headers.
  - The workspace **persists as `.nmoxapi.json`** beside the project,
    exactly like the rack patch and the infra design. Secrets belong
    in environment variables kept out of source control (the file is
    committable).

## [1.18.0] — 2026-07-02

The second-cut sprint's final tranche: PING grows into a REST console,
and the infra designer's dialogs join the platform.

### Added
- **PING is a REST console now.** HEADERS carries auth and content
  types (session-only — tokens never persist into the committable
  patch, the ATMOS rule); VIEW opens a console with the last 50
  exchanges, pretty-printed JSON responses, and one-click **Replay**.
  A dialed Content-Type wins over the JSON sniff.

### Changed
- **The infra designer speaks the platform.** All twelve raw
  `JOptionPane` dialogs — deploy confirmation, destroy-stack, token
  entry, sync/refresh results — are now `DialogDisplayer`
  notifications and dialogs: correctly parented, keyboard-correct, and
  consistent with the rest of the IDE. These are the highest-stakes
  confirmations in the app (they spend money); they deserved it.

## [1.17.0] — 2026-07-02

The infrastructure designer, deepened: it opens fitted, provisions
servers that configure themselves, and hands you the way in.

### Added
- **Cloud-init on droplets.** A droplet's new user_data field flows
  into the create body, so a server configures itself on first boot
  (install nginx, pull your app, whatever) — infrastructure as one
  design, not a design plus a runbook.
- **Copy SSH command from a node.** Once a droplet is deployed (or
  imported by Sync), its public IP is remembered and persisted; the
  node's right-click menu offers "Copy SSH command (root@…)". The
  designer stops being a place you look at servers and becomes the way
  in to them.
- **Deploy log.** Every deploy appends a timestamped, per-resource
  record to .nmox/deploy-log beside the project — a half-deployed
  stack is legible after the fact, not a hunt.

### Fixed
- **The Infra Designer opens at a sane zoom.** Fit ran before the
  window had a size, so dividing by a zero-width viewport slammed the
  zoom to its floor on every open — the canvas always started "way
  out". Fit now waits for a real layout pass, contains the design
  without ever magnifying it (small designs stay at natural size), and
  centers the content. The toolbar gains − / Fit / + zoom buttons.

## [1.16.0] — 2026-07-02

Three second-cut devices: the rack reaches your servers, watches your
certificates, and weighs your bundles.

### Added
- **HELM — the rack reaches your servers.** Dial user@host and a
  command; it runs over ssh (BatchMode: key auth only, no hanging
  prompts) with output on the bus and OK/FAIL/DONE jacks. Patch
  LAUNCHPAD OK → RUN and a deploy finishes with a remote migration or
  service restart; the HOST jack takes a cable.
- **BEACON — cert & uptime sentinel.** One CHECK answers what pages
  people at 3am: is it up, and how many days on the TLS cert? Dial
  MIN DAYS and a cert inside the window fires FAIL. Patch TEMPO BAR →
  CHECK to watch production on a clock.
- **PRISM — bundle-size gate.** Weighs the build output; MAX sets the
  budget. Patch FORGE OK → MEASURE and bundles over budget fire FAIL
  before they ship. Bundles grow one innocent import at a time —
  PRISM holds the line.

## [1.15.0] — 2026-07-02

Bun and Deno, first-class. The 2026 runtimes stop being "Node with
extra steps": their manifests are detected with precedence, every AUTO
device speaks the right binary, and CI export sets them up properly.

### Added
- **Bun toolchain.** `bun.lock`/`bunfig.toml` beside a package.json
  means Bun, not Node: CRATE runs `bun install/update/outdated`,
  VERITAS `bun test` (re-run failed via `-t`), FORGE `bun run build`,
  IGNITION `bun run start`, and the CI exporter emits
  `oven-sh/setup-bun`.
- **Deno toolchain.** `deno.json`/`deno.jsonc` detection: CRATE
  `deno install/outdated`, VERITAS `deno test` (re-run failed via
  `--filter`), FORGE `deno task build`, IGNITION `deno task start`,
  CI export via `denoland/setup-deno`. Both register as real platform
  projects (Open Project works on a bare Deno repo).

## [1.14.0] — 2026-07-02

Infra truth and teardown: the designer stops being create-only — the
canvas can now ask the cloud what is really there, and take a whole
stack down in one decision with the bill in view.

### Added
- **Refresh — drift detection.** Every deployed node is re-checked
  against the provider API: a droplet deleted in the cloud console
  stops claiming "live" and reports **drifted: deleted in cloud**
  (its stale id is released so re-deploy works). Kinds without a
  per-resource read endpoint say "unverifiable" honestly instead of
  guessing.
- **Destroy stack — one-click teardown with the bill in view.** The
  confirmation names every deployed resource and what destroying them
  saves ("Destroy 6 cloud resources, saving ~$48.00/month?"), then
  tears down in reverse dependency order — attachments and dependents
  before the resources they lean on. Individual failures don't strand
  the rest; the design stays on the canvas, ready to deploy again.

## [1.13.0] — 2026-07-02

Testing rigor: failures get names and one-click re-runs, and coverage
and throughput join quality as gates that can close the OK jack.

### Added
- **VERITAS names its failures.** Failing tests are captured by name
  across runner families (jest/vitest ✕ lines, pytest FAILED node ids,
  cargo, go); the FAILURES button lists them and **Re-run failed**
  runs exactly those — `jest -t`, `vitest -t`, pytest node ids,
  `cargo test <names>`, `go -run` — no full-suite penance for one red
  test.
- **Coverage is a number and a floor.** With COVER on, the measured
  percentage (istanbul, pytest-cov, go -cover) lands on the tally LCD
  (`P:41 F:0 C:85%`); dial MIN COV and a green-but-thin suite fires
  FAIL instead of OK. Unmeasured coverage never gates — the device
  refuses to punish runners it can't read.
- **GAUNTLET can fail a pipeline.** MIN R/S puts a throughput floor
  under the bench: below it, FAIL fires — a load test that always
  "passes" can't gate anything. Both floors ride the
  `overallSuccess` hook, so LEDs and jacks tell the same truth.

## [1.12.0] — 2026-07-02

Three devices that close the last gaps between "runs your toolchain"
and "runs your whole web workflow" — custom steps, log files, and web
quality as a shipping gate.

### Added
- **SOLDER — any command as a rack unit.** `make seed-db`,
  `./scripts/fixtures.sh --reset` — whatever you type runs with the
  full treatment: OK/FAIL/DONE jacks, output on the bus, CI export.
  Parsed to argv with quotes respected, never handed to a shell.
- **TAIL — tail -f on the patch bay.** Servers that write
  `logs/app.log` were invisible next to their stdout. Dial the path,
  flip FOLLOW, and appended lines ride the OUT jack into PHOSPHOR.
  Log rotation resets cleanly to the new head.
- **VITALS — the web quality gate.** Lighthouse headless against the
  dialed URL; performance, accessibility, best-practices and SEO land
  on four meters. Dial MIN and a slow page fires FAIL instead of OK —
  patch SURGE URL → URL, READY → RUN, OK → LAUNCHPAD and nothing
  slow, inaccessible, or sloppy ships. Built on a new
  `CommandDevice.overallSuccess` hook: a device's verdict can demand
  more than "the tool didn't crash", and the LEDs tell the same truth
  as the jacks.

## [1.11.0] — 2026-07-02

Daily-driver foundations and a crispness pass: the fixes that make
living in the studio all day safe — switching projects can no longer
kill work behind your back, the environment your commands run in is
finally file-based, honest, and leak-free — and the app itself stops
carrying another IDE's furniture.

### Added — the platform, used properly
- **Experiments: throwaway work is first-class.** File → New
  Experiment… (Cmd+Shift+E) generates any template into
  `~/.nmox/experiments` — no git repo, no recents pollution, the
  directory pre-trusted so devices run without prompts — and aims the
  rack at it. Keepers graduate (promote = move + git init + marker
  removed); the rest are discarded, which stops anything running there
  first and refuses to delete unmarked directories by contract.
- **Quick Search knows the product.** The toolbar search (Cmd+I) now
  matches recent projects (Enter re-aims the IDE through the switch
  guard) and the device catalog (Enter racks the device) alongside the
  platform's actions and files.
- **The status line answers "is anything running?"** A green lane
  count with device names on hover appears while servers, tunnels, or
  watch builds are live — visible from any window, gone when quiet.
- **One Options category.** Tools → Options → NMOX Studio, with Rack &
  Cloud and Format on Save as subpanels — no more settings scattered
  across top-level categories.

### Changed — crisp
- **Evicted the VCS museum.** Mercurial, Subversion, the CVS installer
  shim, the Jenkins (hudson) client, and the Selenium server bridge no
  longer ship — 12 modules a web dev in 2026 never asked for. The Team
  menu is Git now. (Verified by the boot smoke test: the module system
  comes up clean without them.)
- **One build story, not three menus.** The custom top-level "Build"
  menu — whose single item bypassed the real per-toolchain commands for
  a legacy manager with a raw popup — is gone, along with the duplicate
  "New Web Project…" item. Run/Build/Test/Clean live in the Run menu
  (F6/F11), wired to the rack like everything else.
- **Cmd+O opens files again.** The platform's "Go to Type" (a Java
  dialog) was silently stealing the binding.
- **Keys for the product's own surfaces**: Cmd+9 Task Rack, Cmd+8
  Docker Panel (now also in the Window menu at last), Cmd+0 Workbench,
  alongside Cmd+Shift+P Switch Project.
- **Startup is calmer.** The Infra Designer no longer squats in the
  editor area from minute one (Window menu / one click away), and the
  Welcome tab steals focus only on the very first launch instead of
  every launch.
- **The macOS menu bar says "NMOX Studio", not "nmoxstudio"** — the
  DMG launcher and run.sh now pass the dock name to the JVM (a
  macOS-only flag, kept out of the shared conf where it would break
  Linux).
- **The About dialog stops describing a different product.** It called
  us a "Professional Media Development Environment, Version 1.0.0";
  the copy now matches reality, and the never-rendered about.html and
  fake version keys are deleted. The Refactor menu (always empty for
  TextMate/LSP languages) is hidden; GIT's commit prompt says "Commit",
  not "TIMELINE Commit"; dead window-system scaffolding removed.

### Added
- **`.env` support, zero-config.** Every command the rack launches now
  reads the project's `.env` and `.env.local` (`.env.local` wins; in a
  monorepo the lane's own directory overlays the root). Rack settings
  (ATMOS) win over the files, per-launch extras over both. No dialect:
  comments, `export` prefixes and quoted values — nothing else.
- **Switch Project… (Cmd+Shift+P).** A filterable popup over recent
  projects that re-aims the whole IDE — and goes through the new
  switch guard like every other path.

### Fixed
- **Switching projects no longer silently kills running work.** Aiming
  the rack at another project used to dispose the patch — dev server,
  tunnel, watch build all died without a word (or lingered half-aimed
  when the new project had no patch). The switch now names what is
  live ("SURGE, WORMHOLE are still running in api-server. Stop and
  switch?") and stops it cleanly only with consent.
- **ATMOS retracts what it applied.** Removing the env mixer (or
  swapping patches on a project switch) no longer leaks
  `NODE_ENV=production` and friends into the next project's commands.
- **Secrets can no longer ride the patch file into git.** The ATMOS
  EXTRA line is session-only now: durable values belong in `.env`,
  which is read automatically and already gitignored by every
  template. Legacy patches with stored extras load fine — the value is
  just not applied or re-saved.
- **PING responses are readable.** The BODY jack used to clip at 400
  characters — no real JSON payload survived. Full bodies now travel
  the cable (up to 64 KB) into PHOSPHOR's scrollback.
- **Deleted a leftover boot scaffold** that tried to replace the main
  window with a "v3.0.0 test panel" at every start (it only failed by
  losing a race), plus a stale services file registering a class that
  does not exist.

## [1.10.1] — 2026-07-02

A user-experience pass walked four developer journeys through the built
app (a Vite/React monorepo first-open, a zero-config static site, a
CI-configs-and-docs repo, a messy legacy project) and fixed what they
hit, smallest risk first.

### Fixed
- **The editor actually looks dark now.** The IDE always runs the
  FlatLaf Dark look and feel, but the editor's color profile is a
  separate switch that only the Options→Appearance panel ever flipped —
  so every editor rendered light-profile colors on a dark IDE: JS/TS in
  pale Phosphor on white, CSS/YAML/JSON/Markdown in plain black on
  white. The dark profile ("FlatLaf Dark") is now the shipped default,
  and the Phosphor JS/TS palette no longer leaks into the light
  profile. **Upgrading users see this immediately: every editor tab
  switches from a white canvas to the dark Phosphor scheme.** An
  explicit profile choice in Tools→Options still wins over the shipped
  default. Verified per-mime from a wiped userdir: TS/TSX, JS, JSON
  and Markdown render full Phosphor on dark, CSS the platform's dark
  colorings.
- **The launcher picks a Java the platform can run on.** Launching
  without a bundled runtime (portable zip, source builds) could select
  sdkman's `current` link even when it pointed at Java 8 — then spawn a
  JVM that printed "Cannot run on older versions of Java" and never
  exited. The launcher conf now chooses, in order: an explicit
  `jdkhome` / the installer's bundled JRE (still wins — it is applied
  later), `--jdkhome` on the command line (steps past the whole block),
  `JAVA_HOME` when it is 21+, the macOS `java_home` 21+ query, the
  newest 21+ JDK in the standard install dirs, a 21+ `java` on PATH —
  and if none qualifies, exits cleanly (code 3) with an actionable
  message instead of handing a doomed choice to the platform.
  `LauncherJavaSelectionTest` sources the real conf against fixture
  JDKs to pin every branch.
- **Markdown spellcheck reads prose, not code.** `const`, `npm`,
  `querySelectorAll` — anything in a fenced block, inline code span,
  URL, or YAML front matter — no longer gets the red typo squiggle;
  paragraphs, headings, emphasis, and link text stay checked.
- **Markdown fences highlight their language again.** TM4E prunes any
  grammar rule whose cross-grammar include can't resolve, which was
  silently stripping every language-tagged fence (191 rules per
  session) and the front-matter rule, and degrading HTML/Vue/Svelte/
  Astro `<script>` embeddings. Five embed-only grammars (source.js,
  source.ts, source.tsx, source.yaml, text.html.derivative — vscode
  1.95.0, like the rest of the pack) now register purely for scope
  resolution under synthetic mimes no file opens with; a ```js fence
  renders real JavaScript tokens.

## [1.10.0] — 2026-07-01

### Added
- **Format on save with Prettier.** Saving a JS/TS, CSS/SCSS/Less, HTML,
  JSON, YAML, Markdown, Vue, GraphQL, Svelte, or Astro file now runs the
  project's own Prettier over the buffer first. **Upgrading users get
  format-on-save automatically in any project containing a Prettier
  config; disable via Options → Editor → Format on Save.** The effective
  gate is the project's own opt-in: any `.prettierrc*` /
  `prettier.config.*` or a package.json that mentions prettier, searched
  from the saved file up to the repository root (`.git`) and no further —
  monorepo roots count, a personal `~/.prettierrc` above the checkout
  does not. The project-pinned `node_modules/.bin/prettier` is preferred
  over a global install so output matches CI, resolved within the same
  repository boundary. Failures never interrupt the save: a syntax error
  mid-edit, a missing binary, or a hung process (5 s kill) all save the
  buffer unchanged with a log line. The applied edit is the minimal
  changed span, so the caret and scroll position survive the save.
  Measured cost per save (prettier 3.9.4, M-series Mac): ~145 ms on a
  300-line file, ~410 ms on a 3,600-line/392 KB one; files over 512 KB
  skip formatting entirely.

### Tests
- `PrettierFormatterTest`: the opt-in walk (config names, package.json
  mention, monorepo walk-past, the repository boundary — configs and
  node_modules above `.git` don't count, a config at the repo root
  does, a `.git` *file* bounds like a directory), local-binary
  preference, every failure mode collapsing to "no change", and a
  real-process round trip through the stdin/stdout runner.
  `FormatOnSaveTest`: minimal-edit correctness on every shape of change
  (Positions survive a middle-of-file edit), and mime coverage read
  from the generated layer — the artifact that actually registers the
  hook.

## [1.9.0] — 2026-06-22

Polyglot pipelines already compose as parallel *lanes* — one device chain per
toolchain, each pinned to its own manifest directory. This release adds the
three primitives that let those lanes coordinate: a join barrier, readiness
gates on every long-runner, and per-lane file-change routing.

### Added
- **QUORUM (Lane Join) — a new device.** The barrier where parallel lanes
  converge. In ALL mode it fires OK once every wired IN has arrived and all of
  them passed (FAIL if any failed) — "web tests AND api tests pass, then deploy."
  In ANY mode it relays the first arrival, a race. Patch each lane's DONE jack
  into an IN, OK into the shared downstream step (LAUNCHPAD, PREFLIGHT).
- **Readiness ENABLE gate on every long-runner** (IGNITION, SURGE, HALO, NEXUS,
  PHOENIX, WORMHOLE, INSPECTOR). Patch an upstream RUNNING/SERVING gate into a
  server's ENABLE input and it serves only while its dependency is up — Postgres
  → API → web ordering by cable. A high edge while already running never
  double-launches. Generalizes TEMPO's enable semantics across the fleet.
- **Per-lane GLOB route on REFLEX.** A new GLOB field pins one watcher to a
  single language: type `rs` to fire only on Rust saves, `ts,tsx` for the web
  lane. Empty falls back to the coarse FILTER knob. A `.rs` save now drives the
  cargo lane and a `.ts` save the vitest lane, instead of every save running both.
- **Polyglot Gauntlet preset.** Per-language REFLEX watchers → their own VERITAS
  lanes → QUORUM → PREFLIGHT: the rack ships only when every lane is green.

### Tests
- `CrossLaneTest`: the QUORUM ALL barrier (waits for all lanes, ANDs their
  results), the FAIL path, the ANY race, the ENABLE gate's start/stop and
  no-double-launch guard, and REFLEX glob parsing. `DeviceContractTest` now
  covers QUORUM for free.

## [1.8.6] — 2026-06-22

A control-surface audit swept all 33 rack devices for knobs, toggles, buttons,
and patch jacks that looked wired but weren't — and made them carry real
signals and real results. Most of the fleet was already honest; the cases below
were not.

### Fixed
- **VERITAS COVER actually collects coverage now.** The toggle keyed on the raw
  RUNNER knob, so in the default AUTO position (and anywhere AUTO resolved a
  framework) it added nothing — the switch was visibly on and did nothing. It
  now resolves AUTO first, so a jest/vitest project gets `--coverage` and a
  Python project gets `pytest --cov`.
- **BLACKBOX's OUT jack was inert.** The flight recorder declared an output port
  but never emitted on it — a cable you could draw that could never carry a
  signal. It now broadcasts the newest tape entry (launch, exit, or error) as it
  happens, so you can wire BLACKBOX → MONITOR for a live event log or into a
  notifier.
- **SONAR's OUT jack spoke prose, not data.** It emitted the sentence
  `"sonar: N ports listening"`, which nothing downstream could act on. It now
  emits the listening port numbers as a sorted, de-duplicated, comma-separated
  list a wired device can parse.
- **FORGE's WATCH toggle was a no-op in the esbuild position.** esbuild now gets
  `--watch` when WATCH is on, like the other bundlers.

### Added
- **PING can send a request body.** POST and PUT previously sent an empty body,
  so you couldn't smoke-test an API that expects a payload. A new body field is
  sent on POST/PUT (with a JSON `Content-Type` when the body looks like JSON);
  reads and blank bodies stay body-less.
- **`DeviceWiringTest`** locks these behaviors in: COVER resolves AUTO before
  adding a coverage flag, PING's request builder carries the body only on
  write methods, SONAR's payload is machine-usable, and BLACKBOX's OUT jack
  delivers a recorder event down a real cable.

### Changed
- **NPM-9000** dropped two dead button fields (inlined as locals), and
  **MAESTRO's** STOP ALL now describes honestly what it does — halt every
  command-backed device — rather than implying it can stop timer- or
  listener-driven ones.

## [1.8.5] — 2026-06-21

### Fixed
- **A clean startup log.** Every NMOX-owned warning the platform logged on
  launch is gone. The 45+ TextMate grammar MIME resolvers and the editor/
  explorer tool-window tabs now declare explicit `position` attributes, so the
  filesystem ordering is fully specified instead of "Not all children marked
  with the position attribute"; the top-level Build menu and the Window-menu
  entry are positioned too. The redundant layer-based JavaScript MIME resolver
  was removed — `JavaScriptDataObject`'s annotation already registers
  js/mjs/jsx, and the duplicate triggered the "ineffective registration"
  warning (NB #191777). (The only startup warnings left come from upstream
  NetBeans modules and a benign platform notice — not our code.)

### Changed
- **The Infra Designer opens at startup**, joining the Welcome and Task Rack
  tabs in the main editor area, so the multi-cloud canvas is one glance away
  instead of a menu trip.

## [1.8.4] — 2026-06-21

### Fixed
- **A duplicate commons-codec bundle no longer fails to start on launch.**
  v1.8.3 installs surfaced a startup warning — an OSGi bundle whose "state
  remains INSTALLED after start()". The cause: the NetBeans `ide` cluster's
  `commons_compress` dragged in the plain `commons-codec` Maven jar
  transitively, alongside the canonical platform wrapper. `nbm-maven-plugin`
  auto-wrapped that plain jar into a second module in `extra/` with the same
  `Bundle-SymbolicName` as the platform copy, and Netigso couldn't start the
  redundant bundle (its packages were already exported), leaving it stuck in
  INSTALLED. The transitive plain jar is now excluded so only the platform
  wrapper ships. Nothing required the duplicate — the real consumers
  (`commons_compress`, `commons_io`) bind to the retained platform module —
  so this clears the warning dialog with no functional loss.

## [1.8.3] — 2026-06-21

### Fixed
- **The bundled-runtime floor is JDK 21, matching the Platform 30 baseline.**
  NetBeans Platform 30 requires JDK 21, but the installers' jlinked runtime
  and the macOS launcher's fallback still named JDK 17. Both now floor at 21,
  so the embedded runtime can't drop below what the platform itself needs.
- **The Homebrew cask CI regenerates an idempotent file.** The release
  workflow's `homebrew` job emitted `depends_on macos: ">= :big_sur"` — the
  deprecated string form — which didn't match the bare-symbol `:big_sur` in
  the committed cask, so every run produced a spurious diff. The heredoc now
  emits the bare symbol, matching the repo.

### Documentation
- README badges and copy caught up with the build: Java 21+ (was 17+),
  NetBeans Platform 30.0 (was 22.0), the editor screenshot caption now reads
  45+ languages (matching the intro and feature list), and the footer tagline
  speaks the rack/synth voice instead of the old generic one.

## [1.8.2] — 2026-06-18

### Changed
- **Coverage can't silently regress now.** JaCoCo gained a fail-closed
  floor on the three substantial-logic modules — rack (≥40% line),
  infra (≥30%), editor (≥28%) — verified to actually fail the build when
  coverage drops below the line. The UI/glue/packaging modules stay
  measured but ungated, since forcing coverage there would only breed
  hollow tests. That completes the self-policing build: a correctness
  gate (SpotBugs), a security gate (find-sec-bugs), and now a coverage
  gate, all on every push.

## [1.8.1] — 2026-06-18

### Security
- **A security static-analysis gate, and a clean review behind it.**
  find-sec-bugs now runs alongside SpotBugs on every build. It caught two
  genuine **ReDoS** (catastrophic-backtracking) regexes — in the eslint
  and `docker ps` output parsers, which chew on external tool output —
  now rewritten to linear time; the source-outline regexes also got a
  line-length bound. Everything else it flags is by-design for a local
  IDE (process launches via argv not a shell, file ops on the user's own
  paths, a localhost debug socket, UI-jitter randomness) and is excluded
  with a written rationale, so the dangerous classes — XXE, SSRF, weak
  crypto, hardcoded secrets, unsafe deserialization — stay watched.

### Fixed
- **A running tool process can't be orphaned by a stale exit.** A
  device's `exec` swapped its process handle without an identity check,
  so a previous run's exit callback arriving late could null out the
  handle of the run that replaced it — leaving a live process that
  `isLive()`/panic could no longer see or kill. The swap is now
  identity-guarded.
- Removed a dead, unescaped HTML-building method left over from the
  language-server panel rework.

## [1.8.0] — 2026-06-18

### Added
- **Supply-chain hygiene.** Dependabot now watches three ecosystems —
  the Maven reactor, the GitHub Actions in the workflows, and the npm
  dependencies inside the project-scaffold templates — so a generated
  project never starts out vulnerable (the Vite advisory that bit us was
  exactly that case) and the build's own dependencies stay patched. Every
  release now also ships a **CycloneDX SBOM** (`*-sbom.json`) listing every
  component that goes into the app.

### Changed
- **NMOX Studio is now a Java 21 LTS application.** The source target
  moved 17 → 21 and the installers bundle a Temurin 21 runtime, so the
  app ships on a current, supported JVM (newer GC, security fixes, the
  language features 21 brings). CI proves the cross-platform claim it
  always made: the full `verify` — tests plus the SpotBugs gate — now
  runs on a **Linux + macOS matrix** on JDK 21, not just one Linux/JDK 17
  box. (The NetBeans Platform itself stays on RELEASE270; a platform bump
  is its own careful step.)

## [1.7.0] — 2026-06-18

### Changed
- **One place to launch a tool, done right.** The process-launch
  preamble — PATH augmentation, empty stdin, the no-color/non-interactive
  environment — was copy-pasted at three sites (`CommandExecutor`,
  `DockerClient`, `PortScanner`), and one copy hardcoded `/dev/null`,
  which breaks on Windows. It now lives once in `ProcessSupport`; the
  Windows null-device bug is fixed and a wedged `lsof` is reaped on
  timeout instead of leaking.
- **The build now policies itself.** SpotBugs runs on every `mvn verify`
  (so on every CI push and PR) with a correctness-focused filter, and
  JaCoCo reports coverage per module (≈40% instruction today, carried by
  the engine and logic layers). Error Prone was considered and skipped
  on purpose — it hooks the compiler and would fight the load-bearing
  `-proc:full` annotation processing; SpotBugs works on bytecode and
  doesn't.

### Fixed
- Static analysis caught and we fixed a batch of real defects: an
  unsynchronised lazy singleton (`BuildTaskManager.getInstance` had a
  classic init race), four ignored `mkdirs()` results now use
  `Files.createDirectories` (which surfaces failures instead of
  swallowing them), two dead copy-paste ternaries in the infra deploy
  planner (both branches were identical), a no-op port lookup, and
  missing `switch` defaults.
- **The structure outline no longer invents symbols.** A `function` or
  `class` that only *looks* like a declaration because it sits inside a
  block comment or a multi-line `` `template literal` `` (an embedded SQL
  or HTML heredoc) is no longer listed in the Navigator.
- The Infrastructure Designer detaches its graph and rack listeners and
  stops its autosave timer when closed, so opening and closing it no
  longer leaks a listener each time.
- `NpmService` bounds the wait for a finished command: once its output
  has drained, a process that never exits is reaped after a grace period
  instead of hanging the action thread (a slow build is unaffected — its
  output has to finish first).

## [1.6.0] — 2026-06-17

### Added
- **One-click language-server install.** Tools ▸ Language Servers… is now
  an install interface, not just a status list: every language NMOX can
  light up, with an **Install** button that runs the ecosystem's own
  command — npm, brew, `go install`, `cargo`/`rustup`, `gem`, `dotnet
  tool`, `coursier`, `opam` — each in its idiomatic way, with the bar
  running across while it downloads and the output streaming to the
  Output window. **Install all missing** walks the batch with a Cancel
  that kills the run; servers that ship with their toolchain (Swift,
  Dart) or need a manual setup are marked, never given a fake button; and
  a server whose package manager isn't present says "install `go` first"
  rather than failing cryptically. Commands run as argv through the
  rack's executor — nothing is shell-interpolated and sudo is never
  added.

## [1.5.0] — 2026-06-17

### Added
- **Language-server health — "why is there no hover here?" finally has an
  answer.** The platform delivers go-to-definition, hover, rename and
  live errors for free once a language's server launches, but every
  server is an external binary the developer installs. Now, when a
  file's server isn't found, you get one notification — once per language
  per session — naming the binary and the exact command to install it,
  click-to-copy. No nagging, no modal. And **Tools ▸ Language Servers…**
  shows the whole polyglot picture at a glance: every language NMOX can
  light up, which servers are installed, and the install command for the
  ones that aren't.

### Changed
- **The New Web Project dialogs are now platform dialogs** (the first
  slice of moving off raw `JOptionPane` to `DialogDisplayer`/
  `NotifyDescriptor`): the name prompt and the success/error messages are
  themed with the rest of the IDE and behave under the test harness.

## [1.4.9] — 2026-06-17

### Fixed
- **The Workspace Trust dialog renders its text instead of showing raw
  HTML.** The 1.4.7 move to the platform dialog passed an HTML string,
  which NetBeans shows verbatim (`<html><b>…`) rather than formatted; the
  message is now a `JLabel`, which renders it.
- **The test suite runs headless, so it never pops a real dialog onto
  your screen mid-build.** On macOS local test runs weren't headless, so a
  command-launching test could surface the trust prompt (for a `junit…`
  temp folder, no less). Surefire now sets `java.awt.headless=true`,
  matching CI — and the previously-skipped headless trust test now
  actually runs.

## [1.4.8] — 2026-06-17

### Fixed
A software-quality pass — a fan-out audit surfaced these, each fixed with
a test where the logic allows:
- **Docker calls can't deadlock or outlive their timeout.** The client
  read stdout to completion before touching stderr, so a command with a
  large stderr burst would fill that pipe and hang forever (the timeout
  was unreachable). stderr is now drained on its own thread.
- **Saved settings survive an abrupt quit.** Four more places wrote
  preferences with `put` but never `flush` — the cloud API token, recent
  files, recent projects, and the terminal-theme marker — so a crash
  between the write and the lazy flush lost them (the same bug class as
  the workspace-trust fix). All flush now.
- **NEPTUNE's MIGRATE runs the right tool.** It shelled out to `sqlite3`
  for Postgres and MySQL targets; now `psql -f` and `mysql -e "source"`.
- **`i++ / 2` is division, not a regex.** The JS lexer treated `/` after
  a `++`/`--` as the start of a regex literal, mis-painting the rest of
  the line.
- **HTML attribute-name completion works past the first attribute.** Once
  one attribute was quoted (`href="x" `), the next attribute name stopped
  completing; the value/name decision now uses quote parity.
- **A wedged `npm --version` can't hang the IDE** — that probe now has a
  bounded wait.

## [1.4.7] — 2026-06-17

### Fixed
- **Workspace Trust stops asking once you've answered.** The grant was
  written with `Preferences.put` but never flushed, so a less-than-clean
  quit dropped it and the next launch asked you to trust the same folder
  all over again — it now persists immediately. The prompt is also a
  proper platform dialog now instead of a bare Swing window, and trusting
  `/a/foo` no longer accidentally trusts the unrelated sibling `/a/foobar`
  (the check now respects path boundaries).

## [1.4.6] — 2026-06-17

### Added
- **A web project is now a first-class platform project, and the
  IDE-native Run / Build / Test / Clean drive the rack.** `WebProject`
  used to expose only a name and a tree; it now carries the full
  project SPI — `Sources` (so Find-in-Projects and Go-to-File know the
  source roots), an `ActionProvider` that maps each command to what the
  toolchain expects (`npm run dev`/`build`/`test` for Node, `cargo`,
  `go`, `mvn`, `mix`, … for the rest), `RecommendedTemplates` to scope
  the New File wizard to the web stack, and a `ProjectOpenedHook` so the
  rack aims itself the moment a project opens instead of every entry
  point remembering to. The actions appear on the project's right-click
  menu (Test on ⌃F6), grey out honestly when the toolchain can't express
  them (a project with no `clean` script has Clean disabled), run
  through the rack's own command bus with PATH augmentation, and show in
  the status-bar progress widget with a Cancel that actually kills the
  process. The platform's gestures and the rack are one mechanism now,
  not two parallel worlds.

### Fixed
- **Workspace Trust no longer refuses to run when there's no one to ask.**
  The trust prompt is a modal dialog; in a headless context (CI,
  automated launches, the test suite) it threw and was caught as a "no",
  so every command launch was silently refused — which had quietly turned
  the build red since 1.4.4. An interactive guard with no interaction now
  allows rather than blocks; the prompt still appears for real users.

## [1.4.5] — 2026-06-17

### Security
- **The Vue project template now scaffolds on Vite 6.** Its pinned
  `vite ^4.4.5` carried six advisories (two high — a `server.fs.deny`
  bypass and a `launch-editor` command injection, both Windows), so a
  freshly generated Vue project started life vulnerable. Bumped to
  `vite ^6.4.3` with `@vitejs/plugin-vue ^5.2.4` and `vue ^3.5.13`;
  verified a clean `npm install` (0 advisories) and production build.

## [1.4.4] — 2026-06-17

### Added
- **NEPTUNE database console** — a rack device for the part of web work
  that lives behind the app. PING checks a database is reachable
  (Postgres, MySQL, SQLite) or that a framework's schema is in order
  (Prisma, Ecto, Django); MIGRATE runs the migration the framework
  expects (`prisma db push`, `mix ecto.migrate`, `manage.py migrate`).
  CONNECTED/ERROR LEDs and a RUNNING gate let it drive the rest of a
  patch, the same as every other device.
- **Workspace Trust.** Running build, database, docker or make commands
  against a repository you just opened is a real risk; NMOX now asks
  before executing tools in an untrusted directory and remembers your
  answer, the way a modern editor should.

### Changed
- **Project templates are data, not code.** The React, Vue and vanilla
  scaffolds moved out of a giant Java string-builder into real resource
  files, so a template is now an editable folder of source — the
  long-standing tech-debt item finally paid down.

### Fixed
- **The rack can't be wired into a feedback loop.** Connecting an output
  back to a cable path that returns to its own input is now rejected, so
  a patch can't drive itself into an infinite signal cycle.
- Hardened `package.json` caching, command parsing and New Project
  wizard validation against malformed input.

## [1.4.3] — 2026-06-13

### Added
- **The Navigator window now outlines every language NMOX lexes.**
  TextMate gives colour but no parse tree, so a file's shape used to be
  invisible in the platform's structure view. A new outline engine reads
  it from the source directly — classes and their methods, functions and
  arrow functions, `describe`/`it` test blocks, CSS selectors and
  at-rules, Markdown headings, JSON/YAML/TOML/INI keys and sections, Rust
  and Go and Java-family declarations, GraphQL types, Makefile targets,
  and TODO/FIXME markers as a fallback. Each symbol wears a colour-coded
  phosphor badge; nesting follows what the language affords (brace depth,
  indentation, or heading level). Click a symbol to jump the editor to
  it; the tree refreshes as you type, parsed off the UI thread. Open it
  with **Window ▸ Navigator** (⌘7).

## [1.4.2] — 2026-06-12

### Added
- **The config layer a web project is actually full of now opens as
  itself.** Thirteen new TextMate grammars cover `.editorconfig` and
  INI, ignore files (`.gitignore`/`.dockerignore`/…), GraphQL, Vue,
  Svelte, Astro, Pug, Handlebars, Liquid, nginx, Makefile, Protocol
  Buffers and Prisma; YAML/TOML/Dockerfile/SQL ride the platform's
  native support. Comment-toggling works in all of them, and LSP
  intelligence attaches where a standard server exists (yaml, taplo,
  dockerfile, graphql, vue, svelte, astro, prisma).
- **Dotfiles and bare names resolve by filename**, the way the
  platform's own Dockerfile support does: `.editorconfig`, `.env`
  (and `.env.*`), `.gitignore`, `Makefile`, `nginx.conf` and friends
  now get their real MIME instead of opening as plain text.

### Fixed
- **Spellcheck no longer treats config files as prose.** A wrong token
  property key meant comment detection silently failed for every
  TextMate language, so the checker fell back to flagging keys and
  values — `charset`, `indent_style` — as misspellings. Now it reads
  the scope stack correctly and checks words only inside comments,
  across all 40+ lexed languages.
- **Config files with no toolchain no longer crash the editor on
  open.** A CSL language binding routed them through a code path whose
  lexer class was unreachable at document-load time; the TextMate
  grammar alone carries the highlighting.

## [1.4.1] — 2026-06-12

### Fixed
- **The installers no longer depend on the user's Java.** The DMG,
  Windows setup, tar.gz and deb now embed a jlinked Java runtime
  (`jre/` inside the app, `jdkhome="jre"` in its conf), so machines
  with no Java — or only a legacy Java 8, which previously booted the
  IDE far enough to die on a "Java 17 or newer required" dialog — just
  work. The macOS launcher probes the bundled runtime, falls back to
  an installed JDK 17+, and otherwise explains itself in a real dialog
  instead of failing cryptically. The deb is now honest about being
  `amd64` (it carries a native runtime) and no longer recommends a
  system JRE. Only the portable zip still expects a host Java 17+.

## [1.4.0] — 2026-06-12

### Added
- **PREFLIGHT Ship Check** — "done" as a machine state, not a feeling.
  CHECK runs the readiness list planned from your project (git clean,
  tests, build, lint, audit), one verdict per item on the phosphor
  checklist, and a final READY TO SHIP / NOT READY verdict. Patch its
  OK jack into LAUNCHPAD and unverified code physically cannot deploy.
- **BLACKBOX answers "what changed since it last worked?"** — when a
  run fails, the health line counts files modified since that device
  last went green, and the timeline lists them (newest first,
  dependency directories skipped).
- **HTML, CSS, SCSS and Less are first-class languages** — TextMate
  grammars (pinned from VS Code 1.95.0), their own MIME identities,
  CSL editor services, typing intelligence, spellcheck, and LSP via
  vscode-langservers-extracted. The HTML completion now finishes
  attribute values (`type=`, `rel=`, `role=`, …) and the CSS value
  dictionary grew from 8 properties to 36.
- **Regex-aware JavaScript lexer** — `/ab+c/gi` is one regex token,
  `total / count` stays division; escapes, character classes and
  unterminated slashes all degrade gracefully. Template-literal state
  now survives incremental relexing too.
- **File > New Project opens the real wizard** (it used to show a
  placeholder dialog) and File > Open File reports failures instead of
  swallowing them.
- **Infra deploys check every provider token up front** — a plan
  touching Hetzner or Cloudflare without a token is a dry run with a
  banner, not a half-deployment that dies on node 7. Sync now imports
  13 resource types (was 6), and a created resource whose id could not
  be parsed says so instead of showing a clean green "created".

### Changed
- Deleted the dead `cloud/`, `deployment/` and `containers/` skeleton
  modules (superseded by the Infra Designer and HARBOR) and the unused
  core `ServiceManager`/`FileCache`/`PerformanceMonitor` machinery.
  The remaining core module does exactly one thing: the phosphor
  Terminal theme. Also removed `NMOXStudioApplication` — an @OnStart
  hook in the nbm-application packaging module, which never produces a
  module jar, so the class had never once executed.
- `.css`, `.scss`, `.sass`, `.less` and `.json` files no longer
  masquerade as `text/html` — each resolves to its real MIME type.
- The rack's "Save Patch" confirmation no longer appends `[saved]`
  onto itself; the NPM Explorer no longer stacks a duplicate rack
  listener per open/close cycle; wizard panels honor the
  change-listener contract so Next/Finish revalidate as you type.
- Language servers that fail to launch now say why in the log.

## [1.3.0] — 2026-06-12

### Added
- **Session Resurrection** — the IDE survives its own death, mosh-style.
  The rack's running state is snapshotted continuously (every 5s, per
  project), so it survives clean quits, crashes, `kill -9`, and power loss.
  Return to a project whose last session died with tools running and a
  notification offers them back: one click restarts your dev server,
  watcher, and friends. Proven by SIGKILL-ing the IDE mid-serve and
  resurrecting vite with one click.
- **Persistent flight tape** — BLACKBOX's recorder journals to disk and
  reloads at startup, so the session timeline spans restarts.

### Changed
- Aim semantics: an explicit project choice (wizard, Workbench, Open…)
  is never clobbered by passive sources (persisted window state, the
  open-projects follower); passive sources may still follow each other.
- CI runs on Node 24 action majors (`checkout@v5`, `setup-java@v5`,
  `upload-artifact@v5`, `download-artifact@v5`) ahead of GitHub's
  2026-06-16 forcing date.

## [1.2.1] — 2026-06-12

### Fixed
- Three workflow bugs found by hand-walking the happy path:
  - Opening the Task Rack no longer re-aims the IDE at last session's
    project, clobbering the project you just created.
  - The flight recorder records from application startup, not from the
    first time a BLACKBOX is racked — the tape no longer misses the flight.
  - New projects start versioned: `git init` plus an initial commit, so
    TIMELINE and the platform's git integration work from minute one.

## [1.2.0] — 2026-06-12

### Added
- **BLACKBOX Flight Recorder** — every launch, exit, duration, and error
  on a scrollable session timeline, with per-device duration statistics
  and a slow-creep alarm that notices when a build quietly doubles.
- **SONAR Port Radar** — every listening port mapped to its owning
  process (docker containers labeled), with browse and confirmed
  one-click kill. EADDRINUSE, solved.
- **Docker Manager** shortcut in the Workbench's TOOLING shelf.

## [1.1.0] — 2026-06-12

### Added
- **HARBOR Docker Engine** rack device — ENGINE LED tracking the daemon,
  LCDs for containers up / images held / total reclaimable disk, and an
  always-safe PRUNE.
- **The Docker Panel** — a six-tab control room: the `system df` disk
  ledger with one-click per-category RECLAIM, live container management
  with browser-jump ports, image pull/tag/history/quick-run/cleanup,
  volumes, networks, and **Dockerize**: production multi-stage
  Dockerfiles, `.dockerignore`, and `compose.yaml` generated from the
  project's detected toolchain.

## [1.0.1] — 2026-06-12

### Fixed
- The branded splash screen, window icons, and About page actually ship.
  (A global `*.jar` gitignore silently excluded the `core.jar/` branding
  directories for the project's entire history; v1.0.0 artifacts showed
  the stock NetBeans splash.)

## [1.0.0] — 2026-06-12

Initial release. (Earlier in its life this project's entire UI displayed
"DON'T PANIC". We kept the spirit.)

### Added
- **The Task Rack** — a Reason-style rack where every web-dev task is a
  hardware device: knobs, LEDs, LCDs, and patch cables. Wire OK jacks
  together and one keypress runs your pipeline. 28 devices at launch,
  presets, patch persistence per project, and CI export of any patch to
  a GitHub Actions workflow.
- **Polyglot editing** — 30+ languages via TextMate grammars activated
  through NetBeans CSL; LSP providers for 31 MIMEs with ordered
  fallbacks; comment-aware spellcheck; typing intelligence; the NMOX
  Phosphor dark theme.
- **The Workbench** — a home base: current project with detected
  toolchain chips, open and recent files, projects, and the tooling shelf.
- **Project Studio** — templates (React, Vue, Svelte, Express, vanilla,
  TypeScript library, Python, Go), file CRUD, package.json editor.
- **Infra Designer** — Node-RED-style multi-cloud infrastructure flows
  for DigitalOcean, Hetzner, and Cloudflare, with cost estimates,
  dry-run planning, and live deploys.
- **Rock-solid guarantees, tested in CI with real tools** — a gauntlet
  that runs actual `npm install`/build/test/serve through the rack on
  every commit; a synchronous process reaper so quitting the IDE never
  orphans a dev server; human-readable failure modes; big-project
  performance rails.
- Installers for macOS (DMG), Windows (setup.exe), and Linux
  (tar.gz/deb), plus a portable zip — built and published by a
  tag-triggered release workflow.

[1.4.3]: https://github.com/NMOX/NMOX-Studio/compare/v1.4.2...v1.4.3
[1.4.2]: https://github.com/NMOX/NMOX-Studio/compare/v1.4.1...v1.4.2
[1.4.1]: https://github.com/NMOX/NMOX-Studio/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/NMOX/NMOX-Studio/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/NMOX/NMOX-Studio/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/NMOX/NMOX-Studio/releases/tag/v1.0.0
