# The 3.4 developer-experience plan

*Started 2026-09-26. 3.1 asked how long the first hour takes; 3.2 asked
what a developer who stayed does all day; 3.3 asked what happens when the
project is big. Every one of them imagined one person, at a keyboard and a
screen, on a day where nothing broke. 3.4 asks three questions that drop
one of those assumptions each.*

## The questions

1. **What happens when a second developer joins?** The studios commit six
   files beside the code (`.nmoxrack.json`, `.nmoxapi.json`,
   `.nmoxtasks.json`, `.nmoxdb.json`, `.nmoxweb3.json`, `.nmoxblocks.json`),
   and teammates merge them. Secrets live in each person's own keychain, so
   a colleague's clone has none of them. Does the second developer's first
   hour work, does the IDE say plainly what they are missing, and when two
   people edit the same studio file and git merges it, does each studio
   open it, refuse it by name, or silently lose someone's work?
2. **What happens when something goes wrong?** The IDE killed mid-save, a
   full disk, the network dropped mid-request, a language server that
   crashes, a dev server that ignores SIGTERM, a repository mid-rebase, a
   corrupt workspace file. "Refusals speak" is a house law tested case by
   case; this is the sweep. Does every failure say what happened, and does
   the IDE recover without the user repairing its files by hand?
3. **Can someone use it without a mouse, or without seeing it?** The name
   laws guarantee every control has an accessible name. Nobody has done
   the daily loop keyboard-only, or read it the way a screen reader does.
   Can every window be reached, every control operated and every result
   read without a pointer, and does what a screen reader hears match what
   the screen shows?

## How each is measured

- **A second developer:** two clones of one repository on this machine,
  two throwaway userdirs and two throwaway HOMEs (so neither shares a
  keychain entry, a trust grant or a preference). Alice sets the project up
  in every studio and pushes; Bob clones and opens it; both edit the same
  studio files and merge, producing git's own conflicts and keep-both
  merges. Each studio's parser is fed the merged bytes headlessly first,
  then the walk opens them in the assembled app.
- **When things go wrong:** fault injection, one fault per run, each
  against the assembled app or the code path it names: `kill -9` during a
  save, a full disk image mounted as the project, a proxy that drops the
  connection mid-response, a language server killed under the editor, a
  dev server that traps SIGTERM, a repository stopped mid-rebase and
  mid-merge, each workspace file truncated and garbled.
- **Without a mouse or a screen:** the accessibility tree the walk tools
  read is the one a screen reader reads. Every window reached by keyboard
  alone (its chord, then Tab), every control's role, name and state read
  and compared with what the screen paints, and a static census of the
  painted surfaces that take no keyboard at all.

## What the surveys found

### 1. A second developer

Every probe ran against the real parsers and writers, with real `git merge`
runs in scratch repositories.

1. **A merge conflict in a studio file was treated as corruption, and git
   then committed the loss — in all seven studios.** Nothing looked for
   git's markers. The studio kept a `.bak`, fell back to an empty or starter
   workspace and said it could not read the file; the next ordinary action
   (a Send in API Studio, a query Run in DB Studio, any edit elsewhere) saved
   that over the conflicted file, and `git add -A && git commit` made the
   starter the merge result. The rack *moved* the file to `.bak`, so
   `git commit -am` recorded the merge as a deletion.
2. **A teammate on a newer version lost data on the next save.** Values a
   build does not know were dropped at parse: a DB connection with an unknown
   engine, an infra node of an unknown kind (with the `doId` of a live,
   billed resource and its wires), an API request's unknown auth type turned
   into none; Block Studio refused the whole file, fell back and overwrote.
3. **Rack cables named devices by position**, so a clean merge rewired them
   silently: Alice deleted a device, Bob wired to the one after it, and the
   patch loaded wired to a different device.
4. **Secrets are per machine, and Bob was not told.** An API request whose
   token lives in Alice's keychain went out with no `Authorization` header
   and no warning; a DB connection with no stored password showed the
   driver's raw error; a SQLite file chosen with the chooser was committed as
   an absolute path, which on Bob's machine either failed with
   `SQLITE_CANTOPEN` or silently created an empty database.
5. **Per-person state lived in the committed files** — DB query history with
   its SQL text, API send history, the active environment, the open Block
   Studio component — rewritten on every Run, Send and pick: the likeliest
   source of the conflicts in 1.
6. **Keeping both sides of a conflict lost entries silently** (DB saved
   queries and web3 imported contracts with the same name; duplicate Block
   Studio piece ids unhealed), **the Task Board had one clock for the whole
   team** (Bob clocking in clocked Alice out), and **Infra node ids came
   from a counter**, so two people adding a droplet both made `droplet-3`;
   Refresh with a teammate's token for another account would sever the
   design's links to live resources.

Clean: load → save → save produced identical bytes for all seven writers;
the duplicate-id heals held; no secret was ever written to a file; Workspace
Trust is per path, so Bob is asked for himself.

### 2. When things go wrong

Each fault ran against the real class or the assembled app; where a finding
was only traced in code it says so. Writes that go through `AtomicFiles`
never lost data — a killed save, a full disk and a corrupt file left every
target intact.

1. **Stop could leave a server running while every surface said stopped.**
   A child that ignores SIGTERM is reparented once its shell dies, and the
   escalation re-read the process tree after the grace, found only the dead
   root, and sent nothing: the child kept its port, the ■ was grey, the
   flight recorder read STOPPED. `LiveRuns` also removed the row before the
   kill ran, and a child the script put in the background escaped Stop, quit
   and the JVM reaper altogether.
2. **A server that stalls mid-body hung every HTTP client forever.** The
   request timeout ends at the headers and the capped body read had no
   deadline: KVASIR's consult blocked the rack's shared background lane (and
   seven devices' work behind it), CouchDB's Cancel did nothing, Contract
   Studio's Watch stopped advancing while looking live.
3. **An unreadable rack patch kept the previous project's rack** (a
   permission error, binary bytes or a directory threw before the corrupt-file
   branch), and Save Patch then overwrote the unreadable file with the old
   project's devices. **Block Studio overwrote an unreadable workspace**, and
   **the Task Board failed silently** — a malformed board or a failed save
   only logged.
4. **The git chip hid what git was doing.** A conflicted merge read
   `⎇ main ±2`, a rebase a bare sha; the status poll took `index.lock`
   without `--no-optional-locks`, and its timeout's SIGKILL left the lock on
   disk, so the user's next `git add` failed; Checkout… ran mid-rebase.
5. **A crashing language server left no trace**: its stderr was discarded,
   and a server that always crashes went dark for a minute at a time with no
   word to the user.
6. **Smaller silences**: the session snapshot was not atomic and its failure
   was swallowed (a full disk made it empty, and the crash-resume offer
   vanished with it); API Studio called a body cut off mid-way "No route —
   closed"; a killed save left its temp file forever, where `git add .`
   would commit it and REFLEX would fire on it; each file kept one `.bak`,
   overwritten by the next rescue.

Clean: truncated, zero-byte, binary, 50 MB, directory and unreadable studio
files each kept a copy and bound read-only (Task Board and Block Studio
apart, above); drop-in readers never read a temp file; a silent server hits
each client's timeout; bare, shallow, worktree and unborn repositories read
correctly; a TERM-trapping root is SIGKILLed after three seconds.

### 3. Without a mouse or a screen

1. **A screen reader could navigate into no window at all.** Measured live
   on the accessibility tree VoiceOver walks: the window system's tab
   containers exposed zero children, so the main window read as its toolbar
   and status line — 33 elements — and every studio's content was reachable
   only by pointing. The platform's container reports a tab list with no
   tabs, and macOS builds a tab group's children from its tabs.
2. **No toolbar button in any studio could be reached with Tab** (FlatLaf's
   `ToolBar.focusableButtons = false`): 58 buttons across 9 windows, among
   them DB Studio's RUN and EXPLAIN and the Infra Designer's DEPLOY, most
   with no other key.
3. **The rack was anonymous and half mouse-only**: devices had no accessible
   name (a listener heard "STOP" without knowing whose), editable LCD fields
   opened only on a double-click, the device shelf mounted only by drag or
   double-click and read its cards as raw markup, Tab in the shelf flipped
   the rack instead of moving focus, and cables could be neither patched,
   unplugged nor heard without a mouse.
4. **Popups a keyboard could not open**: the Infra node menu, the IRC
   network menu (the only way to add a network), the Task Board column menu,
   the git chip's Pull Requests and Draft Commit Message — all shown from
   mouse listeners on components that take no focus.
5. **Double-click-only actions** (DB Studio's table peek and history reload,
   joining an IRC channel from the list, the Motion timeline's stops), **an
   Infra canvas operable only by mouse** apart from Delete, **no visible
   focus** on the Welcome's links, and **status chips named by their
   glyphs**.
6. **Nothing is announced when a result arrives.** JDK 25 has no
   announcement API (checked in the runtime image), and refusals speak on a
   status line VoiceOver does not read aloud.

Clean: the rack widgets expose their state and fire their events; the Task
Board's WIP and blocked states are in words; the security grade is text, not
colour; no validation is a red border alone.

## The plan, as shipped in 3.4.0

| # | Question | Unit | Proof |
|---|----------|------|-------|
| 1 | Second developer | `core.util.MergeConflicts`; every studio opens a conflicted file read-only and writes nothing over it | a real `git merge` conflict walked in the assembled Task Board; per-studio conflict tests, mutants by name |
| 2 | Second developer | `SelfWriteTracker.beforeWrite` on every studio's save lane | the review's pull-under-the-IDE tests, turned into `WriteRecheckTest`, `PatchPulledUnderTheIdeTest` and siblings |
| 3 | Second developer | newer versions and unknown kinds open read-only | `NewerVersionReadOnlyTest` and per-studio siblings |
| 4 | Second developer | rack cables resolve by a stable device id | `CableIdentityTest`, `CableSameTypeTest` |
| 5 | Second developer | per-person state in `core.util.PersonalState`; Task Board clock owners | `PersonalStateTest`, `PersonalHistoryCapTest`, `ClockOwnersTest`, `SessionOwnersMismatchTest` |
| 6 | Second developer | keep-both heals; random Infra ids; Refresh forgets only behind a No-default question | `TeamDbWorkspaceTest`, `TeamDesignTest`, `DriftNeverSeversTest` |
| 7 | Second developer | missing credentials refused before sending; relative SQLite paths | `ResolvedCredentialTest`, `MissingPasswordTest`, `SqlitePathsTest` |
| 8 | Second developer | `core.util.Backups`: a rescue never overwrites a rescue | `BackupsTest` |
| 9 | Things going wrong | Stop, Stop All and the panic snapshot the tree and share one grace | `CommandExecutorTest` (three process tests) |
| 10 | Things going wrong | `HttpBodies` idle deadline with a ceiling, the close off the watchdog, cancel as cancel | `HttpBodiesTest`, `CouchCancelTest`, `ApiBodyBrokeTest`, `KvasirConsultLaneTest` |
| 11 | Things going wrong | the git chip names every stopped operation; `--no-optional-locks`; the checkout guard | `GitFactsTest`, `GitChipTest`, `GitStatusNoLocksGateTest` |
| 12 | Things going wrong | language-server stderr tail; *stopping…* rows; atomic snapshot; hidden, swept temps | `ServerStderrTest`, `LiveRunsTest`, `SessionSnapshotWriteTest`, `AtomicFilesTest` |
| 13 | No mouse, no screen | `WindowTabsAccessibility`: every window reachable, switches announced, no leak | `WindowTabsAccessibilityTest`; the AX tree read live (33 elements before) |
| 14 | No mouse, no screen | toolbar Tab stops, keyboard menus, Enter for double-clicks, named chips, Team-menu doors | `ToolbarKeyboardAccessTest`, `KeyboardMenusTest`, `StatusChipsKeyboardTest`, `DbStudioEnterTest`, `ChannelListEnterTest` |
| 15 | No mouse, no screen | the rack without a mouse; the Infra canvas from the keyboard | `DeviceAccessibilityTest`, `ShelfKeyboardTest`, `RackKeyboardCablesTest`, `RackTabFocusTest`, `FlowCanvasKeyboardTest` |
| 16 | No mouse, no screen | cell renderers name themselves | `RenderersNamedGateTest`, found by reading a Task Board card through the AX tree |

Not shipped: tracking a background child that outlives its script (the
sampler was built, measured by review at 0 of 8 on the common shape, and
taken out; ledger 125 names the process-group design). No announcement
API exists in JDK 25, so a result arriving is still not spoken aloud.

## Already on the branch before the plan

- The 3.3 big-project fixture is a script (`scripts/big-fixture.sh`).
