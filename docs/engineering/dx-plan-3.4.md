# The 3.4 developer-experience plan (draft)

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

*(the survey's report, when it lands)*

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

## The plan

| # | Question | Unit | Proof |
|---|----------|------|-------|

## Already on the branch

- The 3.3 big-project fixture is a script (`scripts/big-fixture.sh`).
