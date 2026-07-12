# The Plan

*Rewritten 2026-07-12, at v1.50.0, as a fresh senior-eyes capstone by the
assistant that built v1.8→v1.50 with David. The prior capstone was written
at v1.36.0; seven releases (v1.44.0→v1.50.0) ran overnight and drained the
high-value debt queue, so this is a from-scratch pass, not a patch. It is
the current-reality companion to [tech-debt.md](tech-debt.md) (the itemized
ledger): where the project stands, what's genuinely not done, what's worth
doing next, and the working method that got it here. Unlike most of
docs/engineering/, this file is NOT historical — keep it true or delete it.*

## Where the project stands

NMOX Studio is a shipping NetBeans RCP IDE (v1.50.0, ~2,370+ tests, six
release assets per tag, Homebrew cask, a windows-latest CI lane that runs
the full verify) whose identity is the **Reason-style task rack**: 44
hardware-styled devices wired with patch cables, backed by real process
execution, session resurrection, and CI export. Around it: a 48-grammar
polyglot editor with LSP, four studios (API, DB, Contract/Web3, Infra), the
classic-web-first-class layer, Learning Spaces, and the v1.35 "connections"
spine (ServingRegistry + ManifestPulse) that keeps every surface live-synced.

Since the v1.36.0 senior-review capstone, five things graduated from
"opportunity" or "deferred" to "shipped and tested":

- **It debugs.** JS/TS breakpoints out of the box (v1.37.0) via the vendored
  js-debug adapter and the `DapProxy` session multiplexer; **browser/Chrome
  debugging** (v1.43.0) on the same one-child splice, gated on Workspace
  Trust, real-adapter integration-tested. The honest ceiling is recorded,
  not hidden: one child session per run (ledger 25), and a page's Web Workers
  sit *paused* rather than undebugged (ledger 39) — both wait on a platform
  N-session DAP client.
- **It knows its branch, and its project is a platform citizen.** The git
  chip (v1.40.0) reads HEAD from disk and opens the platform History browser.
  Then the big one: **ledger 29, the context migration, landed** (v1.45.0 +
  v1.48.0). A real aim now publishes to `OpenProjects` and `setMainProject`
  on a background lane; the aim-owning windows (Task Rack, Project Studio,
  Workbench) publish the aimed node via `setActivatedNodes`; Project Studio's
  file tree publishes the selected FILE's DataObject node and NPM Explorer
  publishes the found Node project's node. The payoff is live: the **Team
  menu is the full enabled git suite** with just a project aimed (it was one
  disabled stub in v1.40.0), and the chip's Show Changes / Diff / Annotate
  verbs open the platform's real windows. The rack IS the context system now,
  bridged to the platform's — not read past it.
- **It's tested where it ships.** windows-latest joined the CI matrix as a
  blocking full-verify gate (v1.42.0); its first green found two real product
  bugs (language servers never detected on Windows; a cross-OS DapProxy
  disconnect race).
- **It's usable without a mouse or a screen.** The widget library speaks
  Swing accessibility (v1.41.0): SLIDER knobs with keyboard arrows and focus
  rings, Space/Enter buttons, state-announcing LEDs/LCDs/VU meters; every
  control on all 44 devices exposes an accessible name, CI-gated by
  DeviceContractTest's name law (59 controls fixed to get there).
- **Its module system tells the truth.** Spec versions track the product
  version with real inter-module dependency ranges (v1.47.0, ledger 20), so a
  module jar dropped into an older install is refused by the loader instead of
  throwing LinkageError at call time. Soft-dependency is now a Lookup, not a
  caught classloader failure (v1.46.0, ledger 30/31): core exports
  `org.nmox.studio.core.spi` facades, rack publishes @ServiceProvider adapters,
  and **apiclient/web3/infra dropped their rack Maven dependency entirely**.
  Rack tool findings reach the platform Action Items window (v1.49.0, ledger 32).

The v1.36.0 audit remains the trust anchor: its finding was mostly *negative
space* — the house laws held under adversarial reading. The seven overnight
releases were disciplined debt work in that spirit, each sub-fix
mutation-proven against the pre-fix code. The codebase's verified state is
still the most valuable asset this project has; every section below protects it.

## Not done (the honest gaps)

The old v1.36.0 gap list is nearly all closed: Windows is now test-executed
(v1.42.0), the EDT-freeze on Stop All is async (v1.44.0), studio workspace
writes ride named SaveLanes (v1.44.0), and spec versions are real (v1.47.0).
What remains, ranked by how much it would matter to a daily driver — and the
honest headline is that **the high-value queue is drained**. Most of what's
left is either a settled won't-fix or a call that needs a product decision.

1. **The update-center policy decision** (ledger 21) — *needs a product
   call, not code.* The platform autoupdate modules ship in the cluster with
   no update center: dead weight in the download, harmless at runtime. The
   two options are "remove them from the cluster (Plugins-menu implications)"
   or "build a real UC fed by the release workflow." The version scheme a UC
   would need now EXISTS (spec versions closed in v1.47.0), so the technical
   blocker is gone — this is purely "do we want an in-app plugin story?"
   Highest-ranked because it's the one open item that a user decision could
   move today.

2. **The ledger-29 remainder: Kit-action context registration** (deferred
   with a real UX reason). The context migration is done except that the PWA
   Kit / Standards Kit / Classic Kit actions are still always-enabled and
   scold at runtime instead of disabling when out of context. The reason is
   honest and unresolved: focus-keyed enablement would disable them *while the
   editor is focused* — a UX regression masquerading as idiom. Incremental now
   that the pattern is established, but it needs a small UX answer first.

3. **The architectural won't-fixes (ledger 1–7).** Re-audited with fresh
   evidence and each is a decision, not laziness: faceplate "boilerplate" is a
   repeated *idiom* not repeated *values* (#1); the Build/Test/Run switches map
   one enum to three unrelated verbs with nothing shared (#2); the
   RackIO/GraphIO/WorkspaceIO shape can't share a core helper across module
   classloaders without ClassCastException (#3); templates hardcoded in
   ProjectTemplates.java wait for the user-templates feature that would rewrite
   them anyway (#4); JS/TS on a custom lexer vs. TextMate is a real editor
   sprint with a regex-awareness cost (#5); `.sass` approximated by the SCSS
   grammar needs upstream grammar-sourcing (#6); the rack shelf's ~0.3s of
   boot is <5% of a boot the profiler says is dominated by module scanning
   (#7). These are "won't fix unless the premise changes," and the premises
   were re-read, not recalled.

4. **The Windows Job-Objects pair (ledger 38/40).** MSYS/Git-Bash renames the
   launched process and breaks the parent-PID chain, so `ProcessHandle.
   descendants()` can't see grandchildren — which means killTree can't reap a
   Git-Bash grandchild (#38) and, for browser debugging, only the product's
   explicit Stop reaps Chrome on Windows, not a bare DAP disconnect (#40). Both
   are honestly bounded (Stop leaves zero orphans on every OS; runBounded still
   returns bounded) and neither ships a triggering path today. The real fix is
   Windows Job Objects via JNA/FFM — outside pure Java, a dedicated sprint.

5. **FileTreePanel is still a raw JTree over java.io.File (ledger 36).** The
   v1.39.0 review took the *correctness* half (CRUD routes through DataObject,
   so open editors follow deletes/renames; hyperlink paths normalized); the UI
   half — Nodes/BeanTreeView/ExplorerManager for free file-type icons and
   platform file actions — stays custom because the rewrite carries
   visual-regression risk against a tree that is already careful (off-EDT,
   lazy, TCC-safe).

6. **The seven studios live in the `editor` wsmode (ledger 33).** Documents
   opened later interleave with the permanently-open tool tabs. A custom
   `studios` wsmode is the idiomatic direction, but the suite-tabs-first layout
   IS the discovery design (v1.29.0) and moving modes churns every user's
   persisted layout — do it with migration or not at all.

7. **i18n: ~450 hardcoded UI strings (ledger 24).** A reality note, not a
   plan. The house style is deliberate English-only UI; making it localizable
   is a dedicated @Messages-migration sprint. Do it only if a non-English
   audience actually materializes.

8. **The small, deliberately-bounded residue.** Contract Studio never signs
   (by design — no private keys, ever; tuple/struct ABI encode, eth_subscribe
   WS, Vyper/non-EVM, a slither lane, and a `forge init` wizard are the noted
   deferrals, ledger 12); the classic-web "second shelf" (YUI/Dojo/ExtJS
   completion, AngularJS-1.x tooling, a jQuery-migration assistant, ledger 13);
   DB Studio's Mongo cancel is a no-op and cursors read `firstBatch` only, with
   no live Mongo/Couch integration tests (ledger 10/11); CommandExecutor's
   exit-detection and stale-run guards are queued behind a reproduction (ledger
   18); no @OnStop seam yet (ledger 35 — noted so the first need adds one
   rather than misusing a hook); no MySQL learning space (the REPL model needs
   a live server, ledger 8); the platform Breakpoints window never lists DAP
   breakpoints (ledger 27 — reproduced with stock Python, an upstream defect,
   the gutter is the documented manager).

## Could be done (opportunities)

The v1.36.0 list is mostly *done*: browser debugging shipped (v1.43.0), the
git surface shipped (v1.40.0/1.45.0), accessibility shipped (v1.41.0),
startup was measured and closed (v1.38.0). What's genuinely left as
**net-new** — not debt, and not padding — is short, because the product is
mature. Each earns its place only as a full vertical slice (device + tests +
docs + live verify), never as a checkbox:

- **A public device SPI — now unblocked.** Third parties writing rack devices
  was deferred (v1.35 ledger) on one hard prerequisite: without real spec
  versions, every plugin breaks on every release. That prerequisite **shipped
  in v1.47.0** (spec versions with enforced ranges), and v1.46.0's friend
  declarations already draw the line between first-party and outside consumers.
  So the blocker is gone; what remains is the deliberate act of *freezing* the
  `RackDevice` contract (it is stable and storm-law-tested) and documenting it.
  This is the single largest net-new lever, and it is now a decision rather
  than a dependency.
- **Learning Spaces as a community catalog.** 52 built-in spaces; the catalog
  is already data-driven JSON. A `~/.nmox/learn-catalog.d/` drop-in dir plus a
  documented schema is a small change with outsized reach.
- **AI assistance, if ever, through the rack's metaphor.** An "ORACLE" device
  that explains the error currently on the MONITOR bus would fit the identity;
  a chat sidebar would not. The product's differentiation is tactile honesty —
  anything added must be visible, wired, and unpluggable. This is a direction
  to be *chosen*, not a gap to be filled.

That's the whole honest list. New studios (six tabs is the discovery
ceiling), new languages (48 grammars is past diminishing returns), and any
feature that can't be drawn as a device with an honest control surface are
all explicit non-goals.

## Planned (the method — keep doing this)

The cadence that produced 40-plus releases without a broken *shipped* main
(one merge-on-red near-miss overnight, caught by the tag gate — see below):

1. **Sprint → gated PR → tag → live-verify.** One background fail-closed
   pipeline script per release (see the scratchpad templates and the
   `gated-ship-pipeline` memory): local `mvn verify` before push, CI green
   before merge, main green before tag, 6 assets before done. Never touch the
   tree while it runs.
2. **Review-then-fix with evidence.** The v1.36.0 shape held through the
   overnight run: read-only audit lenses first, findings with file:line proof,
   then triage into FIX-NOW / FIX-LATER-with-reason / FINE-AS-IS-blessed.
   Fixes carry regression tests proven against the old code — every v1.44–1.50
   sub-fix was mutation-proven (deleting the fix fails a named test).
3. **The ledger is decisions, not wishes.** Every deferred item in
   tech-debt.md has a reason; every blessed oddity has a written rationale. The
   overnight run re-read premises rather than recalling them — that is why
   ledger 1–3 hardened into won't-fix and ledger 23 closed as "centralize the
   version string only, keep the eight copies" (the copies are an
   architectural constraint, not laziness).
4. **Docs truth pass every ship.** CHANGELOG + CLAUDE.md status/history +
   README claims + ledger. devices.md is generated and CI-gated — the model
   for any future generated doc.
5. **Live-verify before claiming fixed.** Hard rule since v1.33.2. Boot the
   real assembled app; user-visible fixes get a click-through.
6. **Walk a persona's whole journey, not the feature you just built.**
   v1.38.1's lesson stands: the bugs cluster one keypress *off* the feature
   path (a shortcut that opens the wrong window, a console that never surfaces,
   a chooser rooted in a folder we never create). Press the keys the docs
   promise; open the windows the feature implies.

**The house laws** (each earned by a real incident — enforcement lives in CI
gates and regression tests, this list is the index): no EDT I/O and no EDT
process spawns, including the *mutation* half; interactive Stop/switch runs on
a bounded worker while the shutdown reaper stays synchronous, so the orphan
guarantee holds (v1.44.0); listeners bounded + equality-guarded + attach/detach
symmetric per open; process timeouts are waitFor-first with both streams
drained on threads and the **whole tree** killed — dash spawns grandchildren
where bash execs; UTF-8 explicit at every byte↔char boundary; secrets in the
OS Keyring only, never prefs or files, RPC URLs never serialized;
DialogDisplayer/NotificationDisplayer, never JOptionPane; prefs values under
8KB, lists as one-entry-per-item; coverage floors on the testable surface with
pure-Swing excluded by name with reasons; rack tests drain the EDT *and* the
router (`awaitRouterIdle`) before asserting; workspace files written atomically
(temp sibling + `ATOMIC_MOVE` via core `AtomicFiles`) and on a single-throughput
SaveLane whose write+stamp is one task (v1.44.0); file create/rename/delete
through DataObject/FileObject so open editors follow; **optionality is a Lookup
of a core.spi facade, not `catch(LinkageError)`** (v1.46.0); **a real aim
publishes to OpenProjects/setMainProject and the aim node to
actionsGlobalContext, passive aims never resolving a platform project** (v1.45.0).

**Failure patterns to grep for in new code** — every bug class that actually
shipped, once: constructor-attached listeners on TopComponents (remove-half
without the re-add half); read-to-EOF before a timed waitFor; a corrupt file
loading as empty then autosaving emptiness over the original;
`invokeLater(this::self)` while a component is 0×0; full-refresh listeners
fanning out per event across default-open tabs; filesystem walks of `$HOME` on
any thread the user waits on (macOS TCC); unverified `pkill`; a keyboard
shortcut registered but never pressed (`Shortcuts/`-folder chords lose silently
to the Keymaps profile); a UI affordance documented but never exercised (the
empty Breakpoints window); a test that spawns a process into its `@TempDir`
must confirm the process dead — or point its cwd elsewhere — before cleanup
(Windows file locking); **undo capture left ON across a bulk load** — a
preset/patch replace kept the pre-load removals on the stack, so ⌘Z peeled the
just-loaded patch apart (v1.50.0, ledger 19; clear the history at the single
`fromJson` choke point every load routes through).

**Failure patterns learned overnight (v1.44–v1.50) — new, and load-bearing:**

- **Gate the merge on literal "pass" lines, not on the poll loop ending.** A
  `for … done; gh pr merge` chain merges on RED when the loop merely *stops
  polling*. This bit for real: **v1.45.0 was merged with the ubuntu lane
  failed**, because the pipeline treated "the poll loop ended" as "the checks
  passed." Between poll and merge, assert each check line contains "pass",
  `|| exit` otherwise. The release itself was saved by the *second* gate — the
  tag stays behind its own main-green check — but the merge should never have
  landed. Now in the `gated-ship-pipeline` memory.
- **Per-OS surefire filesystem order can poison class-init.** A test-scope API
  without its impl (`projectapi` without `projectapi-nb`) fails `<clinit>` once,
  and then every later `DataObject.getNodeDelegate` in that JVM dies of
  NoClassDefFoundError — whichever OS's directory-listing order hits the poison
  test first. Reproduce with `-Dsurefire.runOrder=reversealphabetical` before
  believing a "passes on my machine" green.
- **Mutation testing can `git checkout` and wipe uncommitted work.** Hit
  **twice** overnight: a mutation harness that resets the tree between runs
  silently discards anything not committed. Commit (or `git stash`) before any
  mutation run, and never launch one over an open unrelated edit.

## What I'd do next, in order

The honest headline first: **the high-value debt queue is drained.** The
seven overnight releases worked through the entire actionable backlog — the
context migration, spec versions, soft-dependencies, the Windows lane,
accessibility, browser debugging, the diagnostics bridge, and the rack-undo
cluster all shipped. What remains is not "more debt to grind"; it splits
cleanly into three buckets, and only one of them is a coding task I can pick
up without direction:

1. **A product decision, not code: the update-center policy (ledger 21).**
   The technical blocker (a version scheme) is gone as of v1.47.0. The
   remaining question — remove the autoupdate modules from the cluster, or
   build a real update center fed by the release workflow — is a direction
   call. This is the top item precisely because it's the one thing a user
   answer would unblock immediately.

2. **A net-new feature that needs a direction: the public device SPI.** Now
   genuinely unblocked (spec versions exist, friends are declared). But
   opening the `RackDevice` contract means *freezing* it, which is a
   one-way door — worth doing only if third-party devices are actually a goal.
   Ask before building.

3. **Settled won't-fixes and bounded residue** (ledger 1–7, 33, 36, 24, and
   the Windows Job-Objects pair 38/40): revisit only when a premise changes or
   a real triggering path appears. Do NOT reopen them on a remembered reason —
   each has its written verdict in tech-debt.md.

So: **autonomous shipping should pause here pending direction.** The next move
is either a product decision (the update center) or a net-new feature that
needs the user to choose a direction (the device SPI, a community learning
catalog, or the ORACLE idea). Grinding further into the ledger would mean
churning settled won't-fixes or spending a sprint on i18n/wsmode-migration
work with no user pulling for it — lower value than a short conversation about
what to build next. Resist, as always: new studios, new languages, and any
feature that can't be drawn as a device with an honest control surface.
