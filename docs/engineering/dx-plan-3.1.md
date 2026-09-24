# The 3.1 developer-experience plan

*Written 2026-09-23, the night 3.1.0 was planned. The PM's plan for one
release, kept after it ships as the record of what was decided and why.*

## The question

A developer who has never heard of NMOX Studio finds it on a Tuesday. How
long until something of theirs runs inside it, and how many times does the
product make them feel stupid on the way?

Three surveys answered that before a line of this plan was written: a
**first-hour walk** (download → install → first launch → open a project →
run it), a **switcher's walk** (someone arriving from VS Code with twenty
years of chords in their hands), and a **contributor's walk** (clone →
build → change → test → PR). Each finding below cites where it was
measured.

## What the walks found

### The first hour

1. **The installed app did not open.** Every 3.0.x install from Homebrew or
   a browser answered *"NMOX Studio.app Not Opened — Apple could not verify
   it is free of malware"*, on a bundle `spctl` calls notarized. The bundle's
   launcher `exec`'d `bin/nmoxstudio`, a shell script, and Gatekeeper judges
   every quarantined file a process executes — a script carries no embedded
   signature. `gh release download` sets no quarantine, which is how every
   verification since 3.0.0 missed it. **This is the first unit and it is
   not negotiable.**
2. **There is no `nmox` command.** The Linux `.deb` ships `nmox-studio`;
   the macOS cask and the Windows installer put nothing on `PATH`. A web
   developer's reflex is `cd project && code .` — the equivalent here does
   not exist. The launcher already forwards `nmoxstudio .` to a running
   instance and aims the project; only the door is missing.
3. **There is no quickstart.** The shortest path from download to a running
   project is seven steps inside chapter 3 of a twelve-chapter manual.
4. **The README opens with a metaphor and puts Download at line 113.**
5. **The docs still describe the past.** "Self-signed until v3.0" (the
   README and the guide), a `NetBeans Platform 30.0` badge on a RELEASE310
   product, `mvn package -Pdeployment` (a profile that no longer exists),
   studio and engine counts that disagree between README and website, a dead
   wiki link, a project tree listing folders that do not exist, no Windows or
   Linux download link on the website. The user guide carries 45 version
   mentions and a *"Crossing 2.35.0"* box a new reader cannot use.
6. **No glossary.** Rack, device, patch, jack, lane, aim, the ⇄ chip,
   KVASIR, studio, PREFLIGHT, NBM — the product's vocabulary is taught
   nowhere in one place.

### The switcher

7. **Three chords fight VS Code muscle memory.** ⇧⌘P is Switch Project
   (VS Code: the command palette), ⇧⌘E is New Experiment (Explorer), ⇧⌘X is
   Manage Experiments (Extensions). The first thing a switcher presses opens
   the wrong thing.
8. **No terminal toggle chord** (VS Code: ⌃\`).
9. **`.editorconfig` indentation is ignored.** Save honours
   `trim_trailing_whitespace` and `insert_final_newline`; `indent_style` and
   `indent_size` — the settings people actually write the file for — do
   nothing (`EditorConfig.java`).
10. **npm scripts are not in Quick Search.** ⌘I reaches devices, servers,
    requests, symbols — not `npm run dev`.
11. **No "Coming from VS Code" page and no cross-platform cheat sheet.**

### The contributor

12. **Nothing enforces JDK 25**, and building on 21 fails as hundreds of
    `cannot find symbol: Bundle` lines pointing at the wrong file.
13. **The root is littered with v0.x fossils** — `build.sh` checks for Java
    17, `setup-and-run.sh` looks for 23 or 17, `gui-test.sh` and
    `test-ide-features.sh` test classes that never shipped, and
    `TestLexer.java`, `test-javascript-syntax.js`,
    `demo-lexer-functionality.md` and `validate-implementation.md` describe
    a lexer demo from 2024. `run.sh` creates `userdir/`, which is not
    ignored.
14. **The inner loop is folklore.** `-Dtest=A,B` (commas, never `+`),
    `-Dsurefire.failIfNoSpecifiedTests=false`, never `-q`, rebuild from the
    root before a boot — all written in session memories, none in
    CONTRIBUTING.
15. **111 gate and ledger tests, no index.** A contributor whose build fails
    on `PlainLabelGateTest` has to read the test to learn what law it is.
16. **No PR template, no issue templates.** The CI step named "Set up JDK
    21" installs 25.

## The plan

Everything lands on one branch, `claude/dx-3.1`, and ships once, as 3.1.0.

| # | Unit | Answers | Proof |
|---|------|---------|-------|
| 1 | The bundle launcher hands `bin/nmoxstudio` to `/bin/sh`; a dispatched release run notarizes a DMG without publishing | 1 | quarantined copy launched through LaunchServices, the dialog READ |
| 2 | `nmox` on PATH on all three OSes; the macOS launcher resolves through a symlink | 2 | `nmox .` from a fresh shell aims the project |
| 3 | `docs/quickstart.md` — five minutes, download to a running project | 3 | every step walked on the assembled app |
| 4 | README front page rewritten around what it is and how to get it | 4, 5 | the doc gates, read top to bottom |
| 5 | The stale-claim sweep across README, guide, website, INSTALL | 5 | each claim checked against source |
| 6 | `docs/glossary.md` | 6 | every term cross-checked against the product's own strings |
| 7 | VS Code chords: ⇧⌘P the command palette, ⇧⌘E the files, ⇧⌘X the plugins, ⌃\` the terminal; Switch Project and the experiment chords moved to free chords | 7, 8 | keymap census, all five profiles, pressed in the app |
| 8 | `.editorconfig` indentation honoured | 9 | a four-space project and a tab project, typed into |
| 9 | npm scripts in Quick Search | 10 | ⌘I "dev" runs the script, trust-gated |
| 10 | `docs/coming-from-vscode.md` + the cheat sheet | 11 | every chord in it pressed |
| 11 | Contributor floor: JDK enforcer, fossils removed, `userdir/` ignored, CONTRIBUTING's inner loop, a gates index held by a test, PR and issue templates | 12–16 | a clean clone builds on 25 and refuses 21 by name |

Units 1, 3–6 and 10 are mine; 2, 7, 8 and 11 run as worktree agents on a
pinned commit, and every diff is read before it is folded. When the table is
done the walks run again, and whatever they find becomes the next row.

## What the walks added, in the order they found it

The table above was the plan at the start of the night. Walking each row in
the assembled app turned up the rows below; each shipped in 3.1.0 with its
test, and the ones that could not be made true were taken out and written
down.

| # | Found by | What was wrong | What 3.1.0 does | Proof |
|---|----------|----------------|-----------------|-------|
| 12 | the first-hour walk | every 3.0.x install from Homebrew or a browser was refused by Gatekeeper: the launcher exec'd an unsigned shell script | the launcher hands it to `/bin/sh`; a dispatched release run notarizes a DMG without publishing | a quarantined copy of the notarized dry run, launched through LaunchServices, the dialog read |
| 13 | the switcher walk | Project Studio's Terminal button looked the platform's action up by an id that does not exist (since 1.212.0) | the real ids, and ⌃\` opens a terminal in the project | `ActionIdsResolveTest` checks every id the product looks up |
| 14 | the first-hour walk | opening a project opened four duplicate file trees (the platform's OpenedProjects group) | the group opens the Navigator only | `OpenedProjectsGroupTest` |
| 15 | the first-hour walk | Team ▸ Find Tasks / Report Task, Window ▸ Show Dashboard and the configuration combo could never do anything | hidden, each with the connector or project type that would bring it back | `DeadDoorsTest` |
| 16 | the first-hour walk | the Welcome did not follow an aim made while it showed; no way in for a repository URL | RECENT and First Steps follow the aim; Clone Git Repository… in START, starting in `~/NMOX` | `WelcomeFollowsAimTest`, `CloneRepositoryReflectionTest` |
| 17 | the first-hour walk | a deep path widened the Workbench header and Project Studio's footer across half the window | `PathLabel` keeps both ends, the whole path on the tooltip | `PathLabelTest`, `WorkbenchHeaderPathTest` |
| 18 | the first-hour walk | ▶ Run greyed with no word on a Node project without dev/start/serve | the press says so and shows the NPM Explorer | `RunWithoutScriptDoorTest` |
| 19 | the switcher walk | a language server's problems never reached Action Items, while the docs called ⌘6 the Problems panel | `DiagnosticsTap` on every server's stdout; a ✕/⚠ count on the status line | `DiagnosticsTapTest`, `ProblemsStatusLineTest`; walked with gopls, including a file never opened |
| 20 | the switcher walk | a folder could not be handed over from the file manager | Linux and Windows offer it; macOS measured and withdrawn (the hardened runtime ignores `CFProcessPath`) | `OpenFolderFromOsGateTest` holds both; ledger 118 |
| 21 | the translators | Help ▸ About and the Action Items tab read English in fourteen languages; the Language note promised a restart in twelve | overlaid and corrected | `CodeNamedMenuRowsTest`, `MenuRowsSpeakTest` |
| 22 | the translators | the newcomer pages existed in English only | 42 translated pages, each held to the English shape | `TranslatedNewcomerDocsTest` |
| 23 | the switcher page's honest gaps | `.vscode/tasks.json` and `launch.json` were not read | tasks run from Quick Search behind Workspace Trust; launch configurations reach the breakpoint debugger through two additive `DebugLauncher` doors; every field neither can honour is refused by name | `VsCodeTasksTest`, `VsCodeLaunchTest` and their providers' tests, six mutants by name |
| 24 | the switcher page's honest gaps | ⌘D was unbound while its gesture lived on ⌘J | ⌘D adds the next occurrence in the default, Emacs and IDEA profiles; Eclipse and NetBeans 5.5 keep their own ⌘D | `KeymapProfileParityTest` gains the editor-side scope |
| 25 | a hostile review of the night's code | a cloned repository's `.editorconfig` glob could hang the editor (it predates 3.1, but 3.1 re-resolves it while you type); language-server diagnostics named secret files to the Agent Port; the tap lost framing on a bad body; recents read the disk on the EDT; `nmox` swallowed its own refusals; tasks ran under `/bin/sh` instead of the user's shell | each fixed with its test; the LOW remainder is ledger 120 | the review report's proofs, re-run against the fixes |
| 26 | the switcher walk, again | `nmox src/app.js:42`, `code -g`'s habit, was refused by the launchers' new missing-path check although the platform's `--open` takes FILE:LINE; and a first launch put the Welcome over the file it was asked to open | NAME:LINE and NAME:LINE:COL open at the line on all three OSes, `-g`/`--goto` accepted; the Welcome steps aside when a file is already open | `TerminalCommandGateTest`, `WelcomeStepsAsideTest`, the Windows check run for real |
| 27 | the same walk | `nmox --help` printed nothing and exited 0: the platform's usage went to the detached launch's `/dev/null` | each launcher prints a usage itself | `TerminalCommandGateTest` |

**The lesson of row 20.** The probe that motivated the Finder door ran on an
unsigned bundle. A release runs under the hardened runtime, where the same
environment variable is ignored; the notarized dry run is the only place the
difference shows. *A probe proves the build it ran on.*

## What is deliberately not in 3.1

- **A sixth keymap profile named "VS Code".** Five profiles already carry
  every chord by gate; a sixth doubles that surface for a handful of chords
  the default profile can simply answer. Revisit if the targeted chords are
  not enough.
- ~~**Reading `.vscode/tasks.json` and `launch.json`.**~~ Deferred at the
  start of the night as needing its own design around Workspace Trust; the
  design turned out to be the one the product already had — trust before
  the spawn, refuse by name what cannot be honoured — and both shipped
  (row 23).
- ~~**LSP diagnostics in Action Items.**~~ Deferred as recon-first; the
  recon found the one launch seam every server passes through, and it
  shipped (row 19).
