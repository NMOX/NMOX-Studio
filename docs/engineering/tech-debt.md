# Technical Debt Ledger

The **current** debt record. Rewritten during the v1.22.0 Snow Leopard
sprint, extended by the v1.23.0 completeness sprint, worked through
end-to-end by the v1.26.0 complete-system sprint (2026-07-03), and
re-audited whole by the v1.36.0 senior-review sprint (2026-07-05: a
six-lens read-only architecture audit, then fixes for everything it
proved), and again by the v1.56.0 third senior review (2026-07-12:
five lenses over the v1.40–v1.55 surface; fixes for the proven, items
41–44 added with reasons). Every entry is either open with a reason it was deferred, or
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

## Open — deferred deliberately, with reasons (added v1.76.0, the fourth review)

### 46. CiExporter emits no setup step for the post-v1.59 toolchains — CLOSED (v1.79.0)
Closed in the v1.79.0 debt sprint: every kind an exported lane can speak
now gets its ecosystem's setup action (setup-beam/gleam, setup-julia,
setup-v, setup-fpm, setup-alire, setup-nim, setup-dlang, setup-racket,
setup-zig, setup-dart, setup-dotnet, haskell setup, setup-ocaml,
install-crystal; the npm-riding functional web dedupes to one
setup-node) or an honest `# NOTE:` comment in the workflow (scala/swift).
Test-pinned incl. the dedup. The paragraph below is the original record.

`CiExporter.setupSteps()` provisions node/bun/deno/rust/go/python/
maven/gradle/beam/ruby/php on the runner; gleam, julia, nim, dlang,
racket, elm, purescript, v, fpm, and alr get no setup-action, so an
exported workflow with one of their lanes fails on command-not-found.
Deferred: each needs its own setup-action research (several have none —
a `run: |` install block per tool), and CI export is an advanced
feature with a visible failure mode. Fix when a user hits it or when
the next CI-export sprint runs.

### 47. INSPECTOR's AUTO falls to the node lane for undebuggable kinds — CLOSED (v1.77.1)

Closed exactly per the fix shape: AUTO keeps the node default only for
the web family (NODE/BUN/DENO/WEBPACK/GRUNT/GULP/BOWER/STATIC/NONE),
maps the six wired debuggers as before, and returns null for everything
else — ATTACH shows "NO DEBUGGER FOR <KIND> — DIAL TARGET" on the LCD,
spawns nothing, raises no gate. An explicit knob position still always
resolves (dialing node on a Rust project is the user's call).
DebugDeviceGreyTest pins all three behaviors, mutation-proven (reverting
the default to node fails the grey assertion).

## Open — deferred deliberately, with reasons (added v1.102.0, the first editor review)

### 56. Unify the seven capped HTTP-read sites into one core helper — CLOSED (v1.124.0)

The unbounded-`ofString` bug was fixed across seven sites in four
releases (apiclient v1.99.0, web3 v1.100.0, dbstudio v1.101.0, and
rack×2 + infra + ui v1.104.0), each inlining its own
`ofInputStream` + `readNBytes(cap)`. The pattern is now stable enough
to extract: a `core.http` helper (e.g. `HttpBodies.readCapped`) would
DRY all seven and give one place to hold the cap constant. Deferred
because it touches core's spec version + every consumer's dep, and the
inlined versions are correct and tested. A source-gate ("no
`BodyHandlers.ofString()` in main sources") is the standing regression
guard until then.

Closed in v1.124.0: `core.http.HttpBodies` (`read`/`readUtf8` →
`Capped(text, byteLength, truncated)`) owns the mechanics — read at most
the cap, probe ONE byte for the truncation bit, decode; a gigabyte body
costs the cap, not the gigabyte (counting-stream proven, cap mutation
fatal). Truncation POLICY deliberately stays at the call sites, where
the seven genuinely differ: API Studio flags it, JSON-RPC and CouchDB
refuse it, the display-only consoles shrug. All seven sites migrated;
the three per-module v1.104.0 source gates now pin routing through
HttpBodies, and a cross-module gate in core fails the build if any site
re-inlines `readNBytes` or reverts to `ofString`. With this — and 55
closed in v1.123.0 — the ledger holds NO actionable open items; 51 and
45 remain deferred with standing reasons (additive-when-a-plugin-needs-it
/ waits-on-platform).

## Open — deferred deliberately, with reasons (added v1.106.0, the first core review)

The v1.106.0 core-module review's HIGH finding — `ProcessSupport.runBounded`'s
uncapped output accumulator (an OOM vector on a runaway child, and the
primitive every module's spawns route through) — was fixed in that
release (4 M-char ceiling, keep-draining-to-EOF, `truncated` flag,
20 MB-flood mutation proof). Its three LOW findings are deferred:

### 57. `AtomicFiles.writeString` narrows file perms to 0600 on rewrite — CLOSED (v1.113.0)

`Files.createTempFile` makes the temp owner-only, and the `ATOMIC_MOVE`
carries those perms onto the target — so atomically rewriting a `0644`
workspace file (`.nmoxapi.json`, `.nmoxweb3.json`, …) leaves it
`rw-------`. Not a security problem (tighter is safer, and consistent
with the keyring-only secrets posture), just a silent behavior change
vs. `Files.writeString`, which honors umask. LOW: no functional impact —
the owner always reads their own workspace files. Fix by re-applying the
target's/umask perms after the move if a shared-perms need ever appears.

### 58. `Versions.compare` throws on non-normalized public input — CLOSED (v1.113.0)

`Integer.parseInt` on a version segment throws `NumberFormatException`
on any non-numeric part (`compare("1.24.0-rc1", "1.24.0")`) or an
overflowing segment. All internal callers feed it `extract()`-normalized
strings, so no live path throws; the risk is a future caller passing a
raw/suffixed version. LOW/latent. Fix by parsing defensively or
documenting the `extract()`-normalized precondition on the public method.

### 59. `GitFacts.readFirstLine` reads a whole `.git` file to get one line — CLOSED (v1.113.0)

`Files.readString` slurps the entire file before taking the first line.
For real `.git/HEAD` and `gitdir:` pointers this is a few bytes, but the
class already treats a crafted `.git` FILE as adversarial input (the
gitdir confinement) — a deliberately huge one would be read fully into
memory first. LOW: the threat model is a file inside the user's own
aimed project, and the chip's git spawn is already bounded elsewhere.
Fix by reading a bounded prefix (`BufferedReader.readLine()` or a capped
`readNBytes`) instead of the whole file.

## Open — deferred deliberately, with reasons (added v1.107.0, the first rack-engine review)

The v1.107.0 rack-engine review's two MED findings were fixed in that
release (FlightRecorder journal I/O off the singleton monitor onto
JOURNAL_RP; RackIO.load `.bak`s a corrupt patch + resets to known-empty).
Its one LOW finding is deferred:

### 60. `CommandExecutor.pumpStream` reads lines with no per-line cap — CLOSED (v1.112.0)

Closed: `readLineBounded` replaces `readLine()` in the pump — same
terminator handling (`\n`, `\r`, `\r\n`), but a line past 200k chars is
returned truncated with an honest ` …[line truncated]` marker and the
remainder of that physical line is drained and discarded, so the child
keeps writing into a moving pipe (no deadlock) while the IDE's memory
stays capped (~400 KB worst case per pump). Terminator parity,
flood truncation, post-flood continuation, and the \r\n boundary all
test-pinned in `BoundedLineReadTest`.

### 61. A hung mount can wedge the Workbench's single-thread detection lane — CLOSED (v1.118.0)

`WorkbenchDetect.detectAsync` walks project directories on the explorer's
single-thread `detector` RP with no reachable timeout. One project dir on
a hung network mount blocks that task indefinitely, and every queued
detection (one per project row) starves behind it — toolchain chips stay
"detecting…" for the session. LOW: off-EDT, so no UI hang — a degraded
feature, not a freeze (the same hung-mount input can no longer touch the
EDT at all since v1.111.0 moved the recent-files stats off it). Fix by
bounding the walk (interrupt/timeout) or isolating rows so one hung dir
can't wedge the lane. From the first dedicated project-module review.

### 62. tools-module LOWs: wizard EDT scaffolding, display-name read, install-path probes — CLOSED (v1.115.0)

From the first full tools review (v1.114.0 fixed its HIGH+2 MED): (a)
WebProjectWizardIterator.instantiate runs its ~8 small file writes on the
EDT at wizard Finish — implement AsynchronousInstantiatingIterator; (b)
WebProject.Info.getDisplayName reads package.json per call during Projects-
window painting — cache or delegate to the mtime-cached ProjectInspector;
(c) NpmExplorer's install/run path does lockfile isFile probes + a
package.json read on the EDT before handing off. All LOW: small local
reads, no storms. Fix together as a tools EDT-polish pass.

### The RCE spawn-gate class — CLOSED across editor (v1.102.0) + tools (v1.103.0)

The systemic finding of the module-review arc: the IDE spawned a
cloned repo's project-controlled code with NO Workspace Trust gate.
`CommandExecutor.run` and `ProcessSupport.builder` are deliberately
un-gated primitives — trust is the CALLER's responsibility, honored by
the rack devices (CommandDevice/ExtensionDevice) and the debug actions
but skipped by four call sites. All four now gated: LSP `launchNpm` +
Prettier `resolveBinary` (v1.102.0, silent isTrusted — auto-firing),
WebProjectActionProvider Run/Build/Test/Clean + NpmService.runCommand
(v1.103.0, prompt-once requestTrust — user-initiated). **The rule for
any new spawn site: gate at the call site; never assume the primitive
is safe.**

### 55. Editor: proxy socket leak + Prettier kill-tree + probe-port binding — CLOSED (v1.123.0)

The 2026-07-20 dedicated editor review shipped its three HIGH findings
in v1.102.0 (LSP + Prettier trust gates closing RCE-on-open/save, DAP
frame cap closing the OOM) and deferred the lower-severity remainder.
All six closed in v1.123.0: **M1** the loopback pair is reaped when the
client pump hits clean EOF (the client has closed — nothing unread can
be discarded, so the half-close law holds; `clientPairClosed` probe,
mutation-proven); **M2** the Prettier timeout runs `killTreeAndWait`,
the drain is a daemon reading a capped prefix then discarding to EOF,
and output past 8 MB is REFUSED outright — a truncated format result
written into the document would destroy the file's tail (cap refusal
mutation-proven); **L1** `freePort` binds loopback; **L3** the child
configuration is parsed BEFORE the success ack, so a malformed
`startDebugging` gets an honest failure response (mutation-proven);
**L4** the completion identifier harvest lexes a 200k-char window
around the caret instead of the whole file; **L5** live Chrome profile
dirs ride a shutdown-hook live-set (the JsDebugServer reaper idiom) so
a force-quit no longer leaks them. The original findings, for the
record:

- **M1 (MED):** `DapProxy.close()` is never called in production
  (`DapDebugAction.debugNode`/`BrowserDebugAction.debugChrome` create
  the proxy as a local and only `endSession` runs on teardown, which
  half-closes `proxySideClient` but never fully closes it). One
  `proxySideClient` FD leaks per debug session. Fix: hold the proxy
  and call `close()` in the onClosed cleanup, or fully close
  `proxySideClient` in `endSession` after the reader drains.
- **M2 (MED):** `PrettierFormatter`'s timeout path uses
  `destroyForcibly()` (not `ProcessSupport.killTree`) — a node wrapper's
  grandchild can survive; the stdout drain thread is non-daemon and
  unbounded (`readAllBytes`). Fix: `killTreeAndWait`, daemon drain,
  cap the drained bytes.
- **L1 (LOW):** `DapDebugAction.freePort` binds `new ServerSocket(0)`
  to all interfaces (vs the loopback-bound `JsDebugServer.freePort`).
  Fix: bind `InetAddress.getLoopbackAddress()`.
- **L3 (LOW):** the `startDebugging` reverse request is ACKed before
  its config is parsed; a malformed config leaves the parent believing
  a child launched. Fix: parse before responding success.
- **L4 (LOW):** `JavaScriptCompletionProvider.addDocumentIdentifiers`
  re-lexes the whole document per completion (O(file size) per query,
  off-EDT). The 1MB-file cost path. Fix: cache/limit the scan window.
- **L5 (LOW):** the shutdown-hook reaper kills adapters but does not
  run `BrowserDebugAction`'s Chrome-profile cleanup, so a throwaway
  profile dir leaks on IDE force-quit (disk-only, best-effort).

## Open — deferred deliberately, with reasons (added v1.95.2, the seventh review)

### 54. DB Studio remainder — FULLY CLOSED (M4/L4 v1.116.0, L3/L5 v1.117.0, M5 v1.119.0, L2 v1.122.0)

The 2026-07-20 dedicated dbstudio review shipped seven fixes in
v1.101.0 (backslash quoting, CouchBackend cap, both dialog defaults,
EXPLAIN single-statement gate, Apply 0-row guard, CSV formula
injection) and deferred the lower-severity remainder:

- **M5 (MED): CLOSED (v1.119.0).** `reloadWorkspace` posts the save-lane
  drain + file read + own-write stamp to RP and marshals the parsed
  workspace back with a newest-wins `reloadSeq` (the web3 v1.100.0
  idiom; teardown waits for the read so the tab never shows an empty
  in-between). `offerEnvConnection` keeps its once-per-project guard on
  the EDT and reads `.env` on RP. `ReloadOffEdtGateTest` pins both
  structurally (mutation-proven).
- **M4 (MED): CLOSED (v1.116.0).** `JdbcCore.cell` caps each cell at
  64k chars; CLOB/NCLOB read only a capped prefix via `getSubString`,
  BLOB/binary render as `[N bytes]` (never stringified), oversize text
  gets an honest `…[N chars, truncated]` marker — a giant LOB can no
  longer OOM the IDE. Live SQLite test-pinned.
- **L2 (LOW): CLOSED (v1.122.0).** `ConnectionSpec` gains a `secure`
  flag (delegating 8-arg constructor keeps every old call site; absent
  key in a pre-v1.122.0 `.nmoxdb.json` loads as false — the cleartext
  behavior those files always had, no migration). The connection dialog
  shows "Use TLS (https)" for CouchDB only; `CouchBackend.baseUrl`
  picks the scheme from it. Round-trip + old-file + scheme test-pinned.
- **L3 (LOW): CLOSED (v1.117.0).** MySQL/MariaDB connects set
  `allowLoadLocalInfile=false` + `allowLocalInfile=false`, so a
  malicious/compromised server can't answer a query with a
  `LOAD DATA LOCAL INFILE` request to read a client file. Test-pinned.
- **L4 (LOW): CLOSED (v1.116.0).** `PeekQueries.consoleTextFor` builds
  the Mongo `find` name via `JSONObject.quote`, so a collection name
  with `"`/`\` yields valid JSON (parse-round-trip test-pinned).
- **L5 (LOW): CLOSED (v1.117.0).** `DbClient.close()` zeroes its
  password clone (close is disposal — the backend is discarded from
  the caller's map, so no reopen re-reads it). Test-pinned.


### 53. Infra Designer: mid-op canvas + re-aim + CME + drift-404 — FULLY CLOSED (v1.98.0/v1.120.0/v1.121.0)

The 2026-07-20 dedicated infra review (its first) found five MED
sharp edges around real paid cloud resources. **(a) CLOSED (v1.98.0):**
Destroy Stack / Destroy Resource / Deploy dialogs defaulted their
Enter/Space button to the destructive option — `NotifyDescriptor.
Confirmation` sets `initialValue = OK_OPTION` and `setValue` never
writes `defaultValue`; both confirms now use the full constructor with
NO_OPTION, and Deploy the DialogDescriptor constructor with Cancel as
initialValue; DialogSafetyTest source-gates it, mutation-proven.
**(c)/(d)/(e) CLOSED (v1.120.0):** (c) the designer's debounced save
binds to `boundDesignFile` at load (the apiclient v1.35.1 idiom) and a
re-aim force-saves the pending window to the OLD project's file before
loading the new — the last-second edit loss AND the old-graph-into-new-
project clobber are both dead, source-gated; (d) every cloud-worker
model mutation (deploy id/ip, drift ip/doId-clear, import placement,
destroy's doId-clear) crosses to the EDT via `onModel` (invokeAndWait —
sequencing preserved), so `GraphIO.toJson`'s autosave iteration can no
longer race a worker `putAll` into a CME; (e) `deletedInCloud` matches
the `HTTP 404:` status PREFIX — impostor 404s (proxy pages, resource
names, retry-afters) no longer sever the deploy linkage; all
test-pinned. **(b) CLOSED (v1.121.0):** the full structural lock. `runExclusive` —
the one choke point every cloud op (deploy / sync / refresh / destroy)
already rode — now arms `opInFlight` + `FlowCanvas.setLocked(true)` and
disables every op button before posting; the canvas refuses delete,
wire, and palette-drop while locked (painted as an unmistakable red
banner; property edits stay live — not structural, cannot orphan), and
a rack re-aim DEFERS (`pendingReaim`) instead of loading another
project's graph mid-operation, honored the moment the op finishes.
Source-gated. Ledger 53 is fully closed. Tokens,
persistence atomicity, FlowCanvas loops, and listener lifecycle all
CLEAN. The dialog-default fix (a) is the highest-value and cheapest —
next infra release. Full report in the 2026-07-20 review.

### 52. API Studio: response robustness + close-save + grader multi-value — CLOSED (v1.99.0)

All four deferred findings from the 2026-07-20 dedicated apiclient
review shipped in v1.99.0: (a) capped streaming body read (8 MB, abort
past the cap, charset honored, truncation flagged) + `prettyForDisplay`
(size gate + StackOverflowError degrade) computed on the send worker,
never the EDT; (b) sends on a dedicated INTERRUPTIBLE
`RequestProcessor("API Studio Send", 4, true)` with a real Cancel
(Send button toggles; interrupt → grey "cancelled" verdict) — the
shared two-slot housekeeping RP can no longer be wedged by hung sends;
(c) `componentClosed` saves only when the debounce says dirty (the
`onProjectReaimed` idiom; the v1.97.0 token migration keeps its own
direct save); (d) CSP graded over the union of all header values,
HSTS deliberately first-field-wins (RFC 6797 §8.1). Fixture-server +
mutation proofs throughout. One honest sliver remains: a body read
that stalls mid-stream has no automatic timer — the user's Cancel
(thread interrupt) is the unblock, and it no longer starves anything
else.

### 51. Device SPI exec has no launched-for-real signal

The frozen `core.spi.device` `DeviceServices.exec` refuses an untrusted
workspace by firing `onExit.accept(-1)` — there is no boolean return
like `CommandDevice.launch()` gained in v1.93.0, so a third-party
device that raises a gate via `emitGate` before calling `exec` can
still lie through the trust prompt (the exact bug class v1.93.0 killed
for the built-ins). The exit(-1) contract lets a well-behaved plugin
self-correct, and the SPI is frozen — the fix is an ADDITIVE overload
(e.g. `boolean tryExec(...)` or an exec returning a handle), added the
day a real plugin author needs it, not speculatively. Found by the
v1.95.2 review's gate lens.

## Open — deferred deliberately, with reasons (added v1.89.0, the fifth review)

### 50. Console in-jacks STOP/ENABLE are inert across the family — CLOSED (v1.90.0)

VELOCITY/COSMOS/NIMBUS/KINETIC/SPECTER now override receive() with the
NEXUS shape (serve → dev, stop → stopProcess, enable → enableGate);
SPECTER's gate runs the suite while high, so VELOCITY SERVING →
SPECTER ENABLE kills the E2E run with the dev server.
ConsoleJackContractTest pins the law catalog-wide (any declared
stop/enable IN jack with a base-class receive fails the build by name;
proven failing-first on VELOCITY and NIMBUS). The v1.89.0 review's
blessing stands: SPECTER's serving=false on non-serving verbs is a
deduping-consumer no-op; symmetric-gate consumers wire REPORT only.

## Open — deferred deliberately, with reasons (added v1.82.0, the Block Studio review)

### 48. The block canvas is not keyboard-operable — CLOSED (v1.83.0)
Shipped as its own release, as sized: Up/Down walk the pieces in layout
order, Left/Right walk the tree, Alt+Up/Down reorder within the parent
(riding the v1.82.0-corrected move semantics), Enter opens a legal-kinds
menu inserting a child, Shift+Enter a sibling after, F2 edits params,
Delete removes, Escape clears — all through the same doc paths as the
mouse gestures, so undo/persist/regenerate see no difference. Pieces are
accessible children now: LIST role on the canvas, one LIST_ITEM per row
with kind + face summary, level, position, and live SELECTED state; the
accessible description names every key. BlockCanvasKeyboardTest drives
the handler with synthesized events (4 tests).

### 49. Preview server: no deregister on app exit (blessed residue)
On app exit with the tab open, componentClosed never runs (window-system
persistence keeps it "open"), so the serving-registry entry and the server
die with the JVM instead of deregistering. The server's threads are daemon
(v1.82.0) and loopback-only, the platform exits via System.exit, and the
registry is in-process — so the entry cannot outlive anything. Revisit
only if an @OnStop seam ever lands (ledger 35). Two behavior notes blessed
with it: the workspace pulse reloads behind a hidden tab (post-show only,
cheap, keeps the canvas honest for the next show), and a foreign
same-project edit stops a running preview (conservative; the live
suppliers would have served the reload, but a stop is never a lie).

## Open — deferred deliberately, with reasons (added v1.56.0, the third senior review)

### 41. `RackDevice.exec` forks + reads dotenv on the EDT — CLOSED (v1.57.0)
The systemic threading item the v1.56 review's concurrency lens flagged as
"the only one with real teeth" — and pre-existing, older than the review
window. Every built-in command device wires its RUN button straight to
`launch()` on the EDT; the path reaches `RackDevice.exec` (`rack/.../model/RackDevice.java`),
which calls `EnvFiles.load` (file reads) then `CommandExecutor.run` →
`ProcessBuilder.start()` — the fork itself — synchronously on the caller.
On a wedged or network-mounted project dir that stalls the EDT, the same
class the boot law guards against, just on the button path. The v1.55 SPI
host (`ExtensionDevice.Services.exec`) faithfully inherits the shape and
adds nothing worse; the trust dialog on the EDT is fine (it pumps a nested
loop). **Deferred, not dismissed:** the fix (hop `EnvFiles.load` +
`CommandExecutor.run` off the EDT inside `RackDevice.exec`, keeping only
the modal trust dialog on the EDT) clears it for all 46 devices at once,
but it changes the threading contract of the hottest path in the rack —
callers that read `isProcessRunning()` right after `exec` would need
auditing — and that is exactly the kind of change the v1.33.x storms
taught us to give its own focused release with live verification, not a
rider on a review sprint. **Closed v1.57.0** its own way: dotenv loads
and the fork ride a RequestProcessor lane, while a synchronous
`PendingHandle` keeps the whole observable contract unchanged —
isProcessRunning()/isLive() answer true the instant exec returns (so
enableGate can't double-launch), a second exec cancels the first,
stop-before-spawn means no process is ever created, panic() stays
bounded on an unspawned run, and the exit callback fires exactly once in
every phase. AsyncExecTest (7, lane-seam stepped) pins each phase;
live-verified a real SOLDER echo ran to OK with the UI responsive and
the trust gate firing on the EDT before the deferred spawn. The three
small sibling EDT touches went with it: the learning-space picker's
drop-in scan, the rack's Save Patch write (the last workspace writer off
the SaveLane), and ORACLE's keychain peek.

### 42. Third-party `descriptor()`/`build()` can run at session restore
The security lens noted the zero-boot-cost law is not enforced *by
construction* for the SPI: if the rack window was open last session and the
aimed project's patch references an installed extension, that plugin's
`descriptor()` (via the palette's `DeviceCatalog.all()`) and `build()` (via
`RackIO` autoload) run during startup, on the EDT, with no user gesture.
**Accepted:** restoring a saved patch legitimately instantiates its
devices — that is what restore *is* — and the security boundary holds
because `exec` stays trust-gated, so a boot-time plugin `exec` prompts
rather than silently spawning. The cost is startup latency proportional to
what the user themselves put in the patch, not an attacker. Revisit only if
a plugin-heavy patch measurably hurts boot.

### 43. `GitFacts` follows an attacker-controlled `gitdir:` pointer — CLOSED (v1.58.0)
Closed by canonicalizing the `gitdir:` pointer and confining it to a
`.git` directory (worktrees/submodules still resolve, arbitrary paths
refused), mutation-proven. The paragraph below is the original record.
A crafted `.git` *file* in an opened project can carry `gitdir: /abs/path`,
and `GitFacts.branch()` reads `<that>/HEAD`'s first line into the chip. The
disclosure is a narrow oracle (surfaces text only when the first line is
`ref: refs/heads/…` or a hex SHA), no process is spawned on that path, and
opening a hostile repo already runs its hooks under the platform's own git.
Low; a canonicalize/confinement pass is the fix if worktree support ever
needs the indirection widened.

### 44. `MissingDevice` can produce a dead-click "Resume last session?" balloon — CLOSED (v1.58.0)
Closed: a `MissingDevice` never matches session-resume, killing the
dead-click balloon; mutation-proven. The paragraph below is the
original record.
If a plugin device was live at a crash and its plugin is uninstalled before
restart, `SessionState.matchAgainst` matches the `MissingDevice` now at
that index by typeId and offers to resume it; the click calls the
placeholder's no-op `resume()`. Capture is correctly gated (a placeholder
is never itself captured), so this is only the reverse edge sequence —
cosmetic, low priority.

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
same node, with an honest Team-menu fallback. **v1.48.0 took two more
remainders**: Project Studio's file tree publishes the selected FILE's
DataObject node (AimNodePublisher generalized to files; selection refines
the aim node, cleared selection falls back to it, distinct-target storms
coalesce on the single lane — never an emptied-out selection), and
NpmExplorer publishes the found Node project's node (null opinion when no
project, so the registry keeps the last real selection; its hand-read
registry fallback stays — it serves aims with no Node project, a case the
publish can't cover — now skipping our own published node so a re-aim
away from a project can't echo it back). **Still open, deliberately —
Kit actions only**: context-sensitive action registrations (PWA Kit,
Standards Kit, Classic Kit still always-enabled and scolding at runtime),
because focus-keyed enablement would disable them while the editor is
focused — a UX regression masquerading as idiom.

### 32. DiagnosticsBus duplicates the platform's editor-hints/task-list plumbing — CLOSED (v1.49.0)
The v1.49.0 recon corrected the item's premise before a line was written:
`RackSquiggler` never drew its own squiggles — it has rendered via
`HintsController.setErrors` (Document overload, ERROR/WARNING severities,
"[tool] " hover prefix) since it shipped, so the editor-hints half was
already platform plumbing and stays byte-identical (its subscription
lifecycle was audited clean in v1.36.0). The REAL gap was the Task List
half: findings for files no editor had open were invisible, and nothing
was listable. Closed by `RackFindingsTaskScanner` (editor/diagnostics), a
`PushTaskScanner` layer-registered under `TaskList/Scanners` so the Task
List framework instantiates it lazily on first scan — zero boot cost, and
the bus's late-subscriber replay catches it up. Severity rides the
platform's own `nb-tasklist-error`/`nb-tasklist-warning` groups (a custom
group would erase the window's severity axis); the tool name rides the
task text, matching the squiggle hover exactly; `File`→`FileObject` via
the house `FileUtil.toFileObject(FileUtil.normalizeFile(f))`. The
replace-per-run semantics live in a pure core (`RackFindings`: a fresh
batch returns every file the OLD batch touched, empty list = clear, and
one tool going clean never erases another tool's rows on the same file) —
extracted because the SPI's `Callback` is final with a package-private
constructor, so the clear logic had to live where a plain test can reach
it. DiagnosticsBus itself STAYS: it is the transport, storm-law-tested,
and both renderers are subscribers. Evidence: `RackFindingsTest` (8,
clear-on-rerun mutation-proven — deleting the old-batch union fails 3
tests) + `RackDiagnosticsWiringTest` (4 source-gates: HintsController
pinned with no Annotation path, layer registration pinned, no-EDT pinned,
headless factory + null-scope deactivation contract).

### 33. All seven studios live in the `editor` mode
Documents opened later interleave with seven permanently-open tool tabs in one
tab well; idiomatic RCP reserves `editor` for documents and docks tool windows
in their own modes. A custom `studios` wsmode (plus a TopComponentGroup for
the Docker/DB/Contract runtime cluster, and a look at whether three default-
open explorer-side trees are two too many) is the direction. **Deferred**: the
suite-tabs-first layout IS the discovery design (v1.29.0), and moving modes
churns every user's persisted layout — do it deliberately, with migration,
or not at all.

### 34. ProgressHandle gaps — CLOSED (v1.44.0, last sliver v1.48.0)
DB Studio connect and infra cloud sync run under finally-guarded
ProgressHandles (per-provider ticks on sync) since v1.44.0; the last
sliver — the web3 artifact walk — closed in v1.48.0 (scanWithProgress:
one indeterminate finally-guarded handle both rescan paths route
through, source-gate-tested so a bare scan call can't sneak back). No
cancel wiring anywhere, deliberately — none of the three ops has an
interrupt seam (DB cancel aborts statements, not connects; the artifact
walk is one uninterruptible Files.walk; commented at each site). The
debounce half closed with #16.

### 35. No @OnStop seam — all shutdown work rides JVM hooks
Blessed for what we use it for: process reaping must survive System.exit and
SIGTERM, which skip @OnStop, so hooks strictly dominate there. But any FUTURE
teardown that needs platform APIs still alive (flushing through NetBeans IO,
keyring handles) has no home today. **Noted so the first such need adds a
ModuleInstall/@OnStop rather than misusing a hook.**

### 36. FileTreePanel remains a raw JTree over java.io.File — CLOSED v1.64.0
The tree half is done: `FileTreePanel` is now an `ExplorerManager.Provider`
over a `BeanTreeView` on the root's `DataFolder` node delegate. It gained
what the ledger asked for — real DataObject file-type icons, the full
platform node menu (Open/Cut/Copy/Delete/Rename/Tools/Properties, a superset
of the old custom menu), git branch annotation, and lazy off-EDT children —
at ~230 fewer lines, live-verified end to end. The visual-regression risk the
deferral cited was retired by the click-through, not assumed away. Laws kept
with their incidents: root resolve OFF the EDT with newer-aim-wins (the
v1.33.1 TCC storm, `RootResolver` seam test-pinned); heavy dirs childless (no
100k-file misclick storm); external edits via `FileUtil.refreshFor`.
**Still open here** (smaller, unrelated): kit wizards still raw-write
possibly-open `index.html` (bounded — wire-in flows on files rarely open at
that moment), and three mtime pollers (FileWatcher/ArtifactPulse/
WorkspaceFilePulse) share a shape a `StampPoller` seam could unify.

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

### 19. Rack polish cluster: undo across presets, trigger bookkeeping — CLOSED (v1.50.0)
See "Closed by v1.50.0" below.

### 21. Platform autoupdate modules ship with no update center — CLOSED (v1.51.0)
See "Closed by v1.51.0" below.

### 23. org.json rides in 8 module copies (~710 KB total) — CLOSED (v1.50.0)
See "Closed by v1.50.0" below.

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

### 45. Tailwind CSS language server — blocked on the platform LSP client (v1.62.0 recon)

Built, live-tested, and PULLED before ship. The platform binds multiple
LanguageServerProviders per mime (verified in LSPBindings bytecode:
project2MimeType2Server keys ServerDescriptions per provider), so the
registration side works — the server started, detected the fixture's
Tailwind v4 project ("Using bundled version of tailwindcss: v4.1.15"),
and built its selectors. But tailwindcss-language-server then calls
`client/registerCapability`, and the platform's LanguageClientImpl
throws `java.lang.UnsupportedOperationException` at
`org.eclipse.lsp4j.services.LanguageClient.registerCapability` — the
server logs "Unhandled rejection ... Internal error" and never answers
a completion request ("No suggestions" in a class attribute, two SEVERE
stacks per session). A server that spawns but cannot answer is worse
than none, so the feature waits on the platform client growing dynamic
capability registration (same waits-on-platform family as ledger 25/39).
Re-test on each NetBeans platform bump: the working detection gate +
tests lived at editor/lsp (v1.62.0 sprint branch history) and can be
restored verbatim.

## Closed by v1.70.0 (the functional web)

- **Elm/ReScript/PureScript verticals**; detection honesty (NODE outranks
  them beside a package.json) test-pinned.
- Two live-drive bugs fixed with tests: InteractiveProcess never stripped
  ANSI (masked until a color-emitting REPL arrived); the Elm learning
  space pinned the user's compiler version (a space must never).
- js-debug readiness deadline 10s→30s — a real cold-machine fix a windows
  docs-gate surfaced, not a CI-only widening.

## Closed by v1.69.0 (the indie stacks)

- **Julia's half-support finished**: the grammar/outline/LSP/space half had
  shipped long ago with no ProjectKind and no lanes; now complete.
- **Nim/D/Racket verticals** on the Gleam recipe; `globDir` extracted so the
  next extension-detected manifest is one predicate, not a copied walker.
- Odin evaluated and skipped with a reason (no manifest to detect); D's
  learning space skipped with a reason (no standard REPL).

## Closed by the v1.63.0–v1.67.0 runs (workspaces + the platform tree + the console family)

- **Ledger 36 tree half (v1.64.0)**: FileTreePanel rewritten as an
  ExplorerManager.Provider over BeanTreeView on the real DataFolder node;
  the deferral's visual-regression premise retired by live click-through.
  Review-hardened in v1.65.1 (reopen survives; VELOCITY serving parity).
- **WAYPOINT workspaces (v1.63.0)** + the knob pending-selection restore
  (v1.63.1) + export portability (v1.63.2) + preset discoverability
  (v1.63.3).
- **The framework-console family completed (v1.65.0–v1.67.0)**: VELOCITY
  (Vite), COSMOS (Astro), KINETIC (SvelteKit), NIMBUS (Nuxt) — 50 devices.

## Closed by the v1.59.0–v1.62.0 overnight run (the web-toolchain sweep)

- **Node package-manager truth (v1.60.0)**: every AUTO lane (CRATE,
  NPM-9000, IDE Run/Build/Test/Clean, NPM Explorer, the New Project
  wizard's install) resolves npm/yarn/pnpm from the corepack
  `packageManager` pin, then the lockfile — never npm-in-a-pnpm-repo.
  pnpm-lock.yaml/yarn.lock joined ManifestPulse (18 names).
- **Biome lanes (v1.61.0)**: biome.json flips PURITY (appended `auto`
  LINTER default) and GLOSS to biome with honest `--write` fix spelling,
  `[biome]` diagnostics, and LCD counts; PREFLIGHT counts it as a lint
  config.
- **Gleam vertical + Doctor backfill + docs truth (v1.59.0)**; **journey
  polish (v1.62.0)**: one ~/NMOX workspace, manager-aware wizard install
  with the pre-trust blessing written in place.
- Item 45 (Tailwind LSP, waits-on-platform) was OPENED by this run with
  live evidence and a restore path — see above.
- **Workspaces vertical (v1.63.0–v1.63.2, same night)**: WAYPOINT (46th
  device) + Workspaces core + Rack.workspaceOverride; the Knob
  pendingSelection fix (saved dynamic selections survive reload — latent
  since v1.0); CI export forward-slash portability (found by the review
  pass's composition pin on the windows lane).

## Closed by v1.51.0 (the update center)

### 21. Platform autoupdate modules ship with no update center — CLOSED
The autoupdate stack (services/ui/cli) always shipped in our platform
cluster; what was missing was a provider for it to read. v1.51.0 wires
both ends:

- **App side** (`ui/layer.xml`): a classic
  `Services/AutoupdateType/*.instance` registration
  (`AutoupdateCatalogFactory.createUpdateProvider` — attribute names
  `url`/`enabled`/`category`/`trusted` verified against the shipped
  autoupdate-services jar, not folklore). "NMOX Studio Updates" is
  enabled, STANDARD, and points at
  `https://github.com/NMOX/NMOX-Studio/releases/latest/download/updates.xml`.
- **The /latest/ redirect trick**: GitHub 302s
  `releases/latest/download/<asset>` to the newest release's asset
  (curl-verified, survives the factory's appended query params), so a
  shipped app follows every future release with no code change. Inside
  the catalog, though, each NBM URL is ABSOLUTE and pinned to its own
  tag (`releases/download/v<version>/<module>.nbm`): the platform
  resolves relative distribution URLs against the *pre-redirect*
  catalog URI (AutoupdateCatalogParser bytecode — it never sees the 302
  target), so relative URLs would let a cached older catalog download
  newer "latest" NBM bytes and fail its own SHA-512 digests.
- **Release side**: the linux lane runs `scripts/build-update-site.sh`
  (`nbm:autoupdate` + gates: exactly the 11 product modules, the
  never-shipped sample template pruned — the aggregator walks
  `session.getAllProjects()`, which ignores `-pl`, and its
  `updateSiteIncludes` filter only applies to nbm-application projects,
  both verified against the 14.5 mojo) and uploads `updates.xml`(+.gz)
  and the 11 NBMs as ADDITIONAL release assets; the six existing asset
  names are untouched. NBM spec versions ride the ledger-20 scheme
  (root `<spec.version>`, stamped from the tag), which is what makes an
  update *offer* meaningful at all.
- **No boot fetch**: the registration is inert layer XML; the platform
  checks on user action (Tools ▸ Plugins, Help ▸ Check for Updates) or
  its own schedule — default `EVERY_WEEK`, evaluated ~500 ms after the
  UI is ready and re-evaluated daily (decompiled
  AutoupdateCheckScheduler/AutoupdateSettings; user-tunable in Plugins ▸
  Settings). Zero-boot-spawns law untouched. Dev builds carry real spec
  versions, so a same-version catalog offers nothing — no special-casing.
- Gated by `UpdateCenterTest` (ui, 5 tests: registration shape + exact
  URL, Bundle display name, workflow ships catalog + NBMs
  (line-anchored — `updates.xml.gz` masks a substring check, found by
  mutation), script pins absolute URLs, catalog-shape when a local site
  exists) — URL and workflow mutations proven to fail it — plus the
  script's own runtime gates on every release.

**NBM signing** (v1.58.0): a `sign-nbms` root-pom profile activates on
`-Dnbm.keystore`, and the release workflow decodes a base64 keystore secret
to sign every module NBM — OFF by default (no secret → unsigned, the
historical behavior), turned on by adding three repo secrets
(docs/engineering/nbm-signing.md); the signing mechanism is
jarsigner-verified with a throwaway keystore. Still manual: the vendored
js-debug adapter still rides full-app releases only (ledger 26). The
Plugins UI can now also install third-party NBMs, which is new surface
we deliberately do not gate.

## Closed by v1.50.0 (the housekeeping release)

### 19. Rack polish cluster: undo across presets, trigger bookkeeping — CLOSED
The three sub-bugs in the one undo/presets neighborhood, each with a test
that fails on the pre-fix code:

- **Undo bled across a preset/patch load** (the load-bearing one).
  Loading a preset or patch replaced the rack's contents while undo
  capture was ON, so the pre-load removals and additions stayed on the
  stack — ⌘Z after a load peeled the just-loaded patch apart device by
  device and, past that, resurrected the PREVIOUS patch's structure (undo
  edits that predate the current patch). Fixed at THE single choke point
  every load routes through: `RackIO.fromJson` clears the undo history
  after replacing the contents, so the Presets menu
  (`RackTopComponent` → `fromJson`), the Load Patch button and
  RackService's project-switch autoload (both via `RackIO.load` →
  `fromJson`) are all covered. RackService keeps its own
  `clearUndoHistory()` after a project switch with no patch file — that
  path never reaches `fromJson`. Test: load patch A, edit, load preset B,
  assert ⌘Z cannot cross B's load (`RackUndoTest.presetLoadClearsUndoHistory`;
  mutation-proven — removing the clear fails it).
- **`lastTriggerAt` entries survived device removal.** `disconnect()` and
  `removeCable()` dropped a severed cable's trigger-cooldown entry, but
  `removeDevice` severed cables in bulk and left their entries in the map
  for the life of the rack. `removeDevice` now drops each dead cable's
  entry; undo re-adds the same cable objects verbatim (no stale
  cooldown). Test: trigger a cable, remove its source device, assert the
  map no longer tracks it (`RackUndoTest.removeDeviceDropsTriggerBookkeeping`;
  mutation-proven via a `tracksTrigger` test seam).
- **TAIL/TEMPO showed stale displays on undo re-attach.** Undo of a
  removal re-attaches the SAME instance, but `dispose()` had stopped the
  follow poll / transport clock while leaving the FOLLOW switch, EYE led,
  CLOCK switch and tick LCD untouched — the faceplate read "armed" while
  nothing ran. Both devices now re-run their existing display/timer sync
  (`sync()` / `syncTimer()`) from an `onAttached()` override, which fires
  on every (re-)attach and is a no-op on a fresh switch-off add. Test:
  arm, remove, undo — the timer must be running again
  (`RackReattachSyncTest`; mutation-proven). All fixes stayed rack-local
  and did not regress the v1.44.0 listener-symmetry / async-panic tests.

### 23. org.json rides in 8 module copies (~710 KB total) — CLOSED
The eight module copies STAY — module classloaders make a shared org.json
wrapper ClassCastException territory (ledger item 3, re-confirmed). What
centralized is only the VERSION STRING: a single `<orgjson.version>` in
the root pom, referenced by all eight module poms (`core`, `rack`,
`tools`, `editor`, `apiclient`, `dbstudio`, `web3`, `infra`), so
Dependabot bumps every copy in one PR. Byte-verified after the build: all
eight resolve to `org.json:json:20260522`. `OrgJsonVersionGateTest`
(application) fails if any module declares a hardcoded org.json version
instead of the property, or if the root property goes missing. What did
NOT change: the number of copies, the actual version, and the per-module
`RackIO`/`GraphIO`/`WorkspaceIO` glue (ledger 3 — per-module by
necessity).

## Closed by v1.47.0 (spec versions)

### 20. Module spec versions are frozen at 1.0 — CLOSED
Every module manifest now carries the product version (1.47.0, not the
pom-derived 1.0) as its `OpenIDE-Module-Specification-Version`, and it
tracks a single root property. Design chosen — and the two rejected
candidates, with evidence: **(a) reactor version bump** (all 13 poms or
`${revision}` CI-friendly versions) was rejected because the release
flow is deliberately `versions:set`-free — the tag is the only version
source, stamped at build time, and the gated pipeline's local `mvn
install` steps would hit `${revision}`-in-parent resolution edge cases
for zero extra benefit; **(b) jar-plugin `manifestEntries`** was tried
first and *does not work*: maven-archiver lets the `<manifestFile>`
(the nbm-generated manifest, which always contains the pom-derived
spec version) win over configured entries on conflicting keys — tested
on a real build, the jar stayed 1.0. (Rack's ledger-31 Friends entry
still rides `manifestEntries` fine because nbm:manifest never emits
that key — merge vs. override.) What shipped is **(c) the plugin's own
seam**: `nbm:manifest` keeps source-manifest entries verbatim
(`conditionallyAddAttribute`), so every module's
`src/main/nbm/manifest.mf` declares
`OpenIDE-Module-Specification-Version: ${spec.version}`, a root
`filter-nbm-source-manifest` resources execution interpolates it into
`target/nbm-manifest/manifest.mf`, and the root pluginManagement points
`<sourceManifestFile>` there. One property (`<spec.version>` in the
root pom) moves all 12 manifests; the release workflow stamps the
tag's version over it in the same three per-OS steps that stamp
branding's `currentVersion` (branding's committed "NMOX Studio 1.0"
stays — `Versions.extract` treats 1.0 as a dev build and that gate
keeps dev launches out of the update check). The payoff beyond
cosmetics: because the reactor packages each module before its
dependents' `nbm:manifest` runs, the generated
`OpenIDE-Module-Module-Dependencies` now read the real version off the
dependency jar — `org.nmox.NMOX.Studio.core > 1.47.0` — so a module
jar dropped into an older install is **refused by the module loader**
instead of surfacing as LinkageError at call time (the ledger-30
hardening this item was always waiting on). Byte-verified in all 11
shipped module jars; `SpecVersionGateTest` (application) pins the
mechanism both ways — every module manifest on the app's classpath
equals the injected `${spec.version}`, and every source manifest
carries the literal placeholder (never a hardcoded number) — so a
future release cannot half-bump.

## Closed by v1.46.0 (the soft-dependency release)

### 30. `catch (LinkageError)` as the soft-dependency mechanism — CLOSED
The idiomatic shape shipped: core exports `org.nmox.studio.core.spi`
with two small facades — `ProjectAim` (projectDir/aim/recentProjects,
projectChanged listeners, manifest listeners) and `LiveServings`
(snapshot + coarse listeners, the Serving record) — and rack publishes
thin `@ServiceProvider` adapters (`RackProjectAim`, `RackLiveServings`;
no logic moved, listener wrappers mapped so add/remove stay symmetric
and a double-add never double-delivers). 31 of the ~55 catch sites
converted to `find()`-and-branch-on-null (apiclient 6, web3 6,
dbstudio 5, project 6, tools 4, infra 2, ui 1 — plus ServingBridge/
BaseUrlOffer/ChainAutoConnect retyped to the facade); **apiclient,
web3 and infra dropped their rack Maven dependency entirely** (pinned
by RackSoftDependencyTest in each: lookups null, rack classes not even
loadable). What stayed, with reasons at each site: dbstudio keeps the
rack dep for `FileWatcher` and `DockerClient` (no core facade — their
two guards are marked KEPT), project keeps it for the Workbench's rack
UI surface (AimNodePublisher/NewProjectDialog/DockerPanel/FileLink/
LegacyWeb; 4 KEPT guards on window-system-touching classes), tools for
CommandExecutor/ProjectInspector, editor/ui hard-depend as before; and
every catch guarding genuinely-optional PLATFORM modules (Keyring in
CloudTokens/Passwords/RpcSecrets, NotificationDisplayer, editor kits,
ConnectionManager, core's terminal-emulator probe, rack's own platform
guards) is legitimate and untouched. `SoftDependencyGateTest` (core)
pins per-file catch counts — zero at converted sites, exact counts at
mixed files — and walks apiclient/web3/infra asserting no main source
names a rack package; mutation-proven (re-adding a catch fails it).
TrustGate was deliberately NOT facaded: editor's rack dependency must
stay for CommandExecutor/DiagnosticsBus regardless, so a trust facade
would remove neither the idiom nor a dependency. The spec-vs-
implementation-version hardening rides with ledger #20 as before.

### 31. rack's public packages have no OpenIDE-Module-Friends — CLOSED
`OpenIDE-Module-Friends: org.nmox.NMOX.Studio.editor, …tools,
…project, …ui, …dbstudio` — exactly the five first-party modules that
still compile against rack after #30 (apiclient/web3/infra no longer
do, so they're not friends). nbm-maven-plugin has no friends
parameter; the entry rides maven-jar-plugin `manifestEntries`, the
same mechanism as the layer entry, and was byte-verified in the built
jar's manifest alongside the unchanged Public-Packages. The module
system now refuses any non-listed dependent, so no external plugin can
grow a claim on rack internals. **The SDK story shipped in v1.55.0**
(the Device SPI, `core.spi.device`): it validated exactly this design —
plugins extend through a small frozen contract in *core*, never through
rack internals, and rack stays friend-locked. `rack.model` stays
exported: the friends list makes narrowing it non-urgent, and
Rack/RackDevice types appear in `rack.service` signatures anyway. Core
deliberately stays friend-less — its exports (process/util/http, the
`spi` soft-dependency facades, and now the `spi.device` Device SPI) ARE
the intended public surface, blessed by the v1.36 and v1.56 audits.

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
