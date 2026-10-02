# The 3.5 plan: the product on Windows and Linux

*Started 2026-10-01, shipped as 3.5.0. 3.1 to 3.4 each asked a question
about the person using the product. Every one of them was answered on a
Mac. 3.5 asks the question underneath: what does someone who installs the
Windows or the Linux build see?*

## The question

The product ships five installers for three operating systems. Windows has
been a blocking CI lane since v1.42.0, and what that lane ran was the test
suite: it never started the product (ledger 37). Linux boots it under xvfb
for the smoke test and a colour probe, and nobody looks. Every walk, every
picture in the documentation and every review up to 3.4.1 was taken on one
developer's Mac.

So: does it start, does it look like itself, do its instructions name keys
the keyboard has, and does anything in the log say otherwise?

## How it was measured

`scripts/platform-walk.sh` boots the assembled app with the documentation's
own screenshot forge, on whatever OS it runs on, and writes thirteen
pictures, the boot log, the launcher's output and a one-screen summary. The
`Platform walk` workflow runs it on `windows-latest` and `ubuntu-latest`
with a JDK that carries JavaFX and uploads the result. It runs on demand
and on a push to any `walk/**` branch, so a walk can be taken before its
branch merges. It gates only that the app booted, painted and exited
cleanly. The pictures are for reading.

Three walks were taken (runs 36952901996, 36954798099, 36957881331), each
after the fixes the previous one asked for.

Two things about the walk itself had to be fixed before its pictures could
be trusted, and both would have been reported as product defects:

- **The forge paints at 2x over a layout made at 1x.** On a Mac's HiDPI
  screen that is the screen's own scale. On a 1x screen Swing lays text out
  with hinted 1x metrics and the 2x paint draws it wider, so labels lost
  their last letters in the picture and nowhere else. The walk now paints
  at the screen's scale (`nmox.shots.scale`).
- **The Windows runner's desktop is 1024×768.** The workflow sets it to
  1920×1080 first.

## What the walks found

| Seen | Where | Cause | Shipped |
| --- | --- | --- | --- |
| The Welcome tells the reader to press `⌥⌘7` | Windows, Linux | The product's strings name chords in Mac notation, 24 keys in fifteen languages, and showed them as written | `Chords.forThisOs` at every place such a string is shown; the Welcome reads `Ctrl+Alt+7` |
| The NetBeans cube as the window icon | Windows | Under a dark look and feel the platform asks for `frame_dark.gif` first; only the light variants were branded | Every frame icon and splash the platform ships has an NMOX twin |
| One SEVERE in the boot log | Windows | JavaFX: a scene hidden while a scale update for it is queued (`EmbeddedScene.setPixelScaleFactors` after `dispose()`) | That one exception, identified by its frames, is absorbed and logged at INFO; any other is passed on |
| DB Studio's console one line tall, its tree 129 px wide | Windows, Linux, and a Mac with an empty workspace | The bundled runtime's `JSplitPane.setComponentOrientation` re-adds a pane's children whenever the orientation object changes, and the first orientation sweep over any window is such a change | `SplitShapes`: every split pane keeps its children and its divider through the sweep |
| Rescan cut in half, the artifact count missing | Linux | Contract Studio's one-row toolbar is wider than the window in the system's font | The toolbar wraps |
| 109 grammar warnings at boot | all three | TM4E resolves `#includes` only against a grammar's top-level repository; four grammars keep repositories inside rules | Those repositories are hoisted; Ruby's percent literals and string interpolation tokenize |

The fourth row is the one that mattered most, and it is not about Windows
or Linux. The first walks photographed it; a walk on a Mac in a throwaway
home matched them. It had not been seen on the one machine the product was
ever looked at because a workspace with real connections in it has names
long enough to make the tree's preferred width look like the designed one.
*The developer's own data was hiding the defect.* The cause was found with
a property-change listener and a stack trace, not by reasoning: the theories
about first layouts and clamped dividers that came before it were wrong.

Right-to-left makes it worse in that runtime: a vertical split's top and
bottom are exchanged, `getLeftComponent()` answers null afterwards, and
switching back to a left-to-right language undoes none of it. `SplitShapes`
restores children by identity for that reason.

## Found on the way

- **A container's name in two published pictures.** Comparing the walk's DB
  Studio with the one in the README showed the README's picture carrying a
  notification that names a database container on the developer's machine.
  The forge's filtered Docker view (v2.164.0) had been wired into the
  staged run only; the plain run, which paints `docs/images/tabs`, talked to
  the daemon directly. Both pictures are repainted from a throwaway home,
  the forge now hands every run either the filtered view or an address
  nothing listens on, and `DocsForgeDockerViewGateTest` runs the script's
  dry run to hold that. Every committed picture was then read by OCR for
  the same class: two had it, both are replaced. The earlier commits still
  hold the old pictures; rewriting published history is not something a
  release does on its own.
- **A count typed into a walkthrough.** Converting the chords in the
  experiment walkthrough showed it promising "92 guided tutorials" beside a
  catalogue of 93. The count is derived now, and the gate that should have
  caught it reads past an adjective.
- **Show Keystrokes and AltGr.** On most European layouts AltGr types
  brackets and braces. An AltGr combination is typing and is no longer
  shown on the projector.

## What is still open

Ledger 127 (should a horizontal split mirror for a right-to-left reader:
3.5.0 keeps every pane as authored, which is what the documentation's
pictures show), 128 (AltGr against the Ctrl+Alt window chords, unverified
when 3.5.0 shipped and measured the same night, below) and 129 (Linux without a Secret Service; the
unresolved includes that upstream grammars carry; the Workbench's
38-character subtitles; what a CI runner cannot show). Ledger 125 and 126
from 3.4 are unchanged.

A walk on a runner is a picture of stock fonts on a virtual screen. It
found six defects and it is not a substitute for someone using the Windows
build for a week.

## After 3.5.0 shipped (3.5.1)

Reading the third walk's remaining pictures, and asking what else had never
been run, found four more things.

- **The installers' results had never been started.** The walk boots the
  assembled build. The release then wraps that build in a Windows setup
  program, a Debian package and a disk image, each with a runtime of its
  own, and only the disk image was ever opened, by hand. The `Installed
  boot` workflow downloads a release's own assets, installs each the way a
  user does and boots the result with the runner's JDK out of reach,
  failing unless the app reports the bundled runtime as its Java home. Then
  it photographs the installed app, Browser included. Its first run, against
  v3.4.1, passed on all three systems.
- **The forge accepted every dialog it photographed.** The Agent Port
  picture on all three systems showed the Standards Kit's "that is the
  example site" warning. The forge closed its dialogs with `dispose()`; a
  `NotifyDescriptor` holds its initial value, OK, until a button or the
  close box says otherwise; so each run accepted the learning-space picker
  (a space was created) and the Standards Kit (its warning was raised and
  photographed as the next dialog). The forge sends the close box now, and
  the walk, which runs in a home of its own, fails when a photographed
  dialog leaves anything behind.
- **Block Studio's pieces overlapped their own text on Linux**: fixed
  geometry, drawn in the system's font. Fixed type.
- **Ledger 126**, the platform's tree rows spoken as markup, closed for
  every tree with a global wrapper and a walk with a control.
- **Ledger 128, measured.** A probe on a Windows runner with a German
  layout loaded: AltGr+7 carries its own modifier, does not fire the
  Ctrl+Alt+7 chord, and types `{`.

The mutant for the spoken rows survived at first, in both the new test and
the one 3.4.0 shipped: an `<html>` label names itself in words, so a fixture
painted that way cannot tell whether the code under test ran. *A fixture
that is kinder than the thing it stands for proves the kindness.*

## After 3.5.1 shipped (3.5.2)

The walks so far photographed windows as a first launch leaves them. The
documentation's staged scenes (a racked project, a debugger stopped on a
breakpoint, DevTools on a served page) had only ever run on the Mac that
paints the guide. `NMOX_WALK_STAGED=1` runs them in the walk, and the
workflow runs that on Windows and Linux.

- **The paused line.** The first Windows picture of the debugger showed its
  current line as light text on pale green. It is the same on every system
  and had been since the dark look shipped: annotation colours are pushed
  onto the annotation types by the Options dialog, and this product selects
  its profile without the dialog. `editor.theme.ProfileAnnotationColors`
  reads the profile in use and gives the types its colours.
- **Given, not set.** The first cut called the types' setters. Each one
  saves the type's file in the user directory, in place, and the platform
  re-reads that folder on another thread: the next walk logged a SEVERE from
  a half-written file. `putProp` stores the value without the announcement.
  *That the platform's own dialog calls a setter is not evidence the setter
  is safe to call for every type at once, at startup.*
- **Every recent project listed twice**, on Windows only, because the
  runner's temp directory is an 8.3 short path and the platform echoes an
  aim under the long one. `RackService.platformSpelling`.
- **Linux skipped the breakpoint scene** until the walk ran under a window
  manager: no focus, no focused editor, no breakpoint.
- **Seven WARNINGs a session** from the platform's folder ordering, about
  the attributes it keeps a project's bookmarks under. `WebProject` answers
  `AuxiliaryConfiguration` itself; 27 warnings in a staged walk became 17.
- **Ledger 129's Workbench subtitles**, closed: `core.util.FitLabel`. The
  proof found the rest of it. In a narrow dock the page kept its own width
  and scrolled sideways, so nothing was ever asked to shorten; and a cut
  subtitle, having a tooltip, took the pointer from its row.
- **A squeezed label stayed squeezed.** Found by reading the new label, not
  by a test: its test passed. A plain label's maximum is the width of the
  text it shows now, a column gives a child no more than its maximum, and so
  a label that had cut its text could never be given the room to undo it.
  The header's path had behaved that way since 3.1.0. The test passed
  because a panel that was never shown keeps the sizes it first computed
  (an invalid parent is not invalidated again), and a shown window does
  not. The headless layout tests now clear that memory before each layout.
  *A test that lays out a window which was never shown is testing a window
  that never forgets.*

**What the pictures could not say.** The spelling fix was proven by a
picture: one row where there had been two. The pull request's Windows lane
then failed ten tests that compare an aimed folder with the temp path they
gave it, because the runner's temp path is an 8.3 short name and the aim is
now its long one. The walk workflow runs no tests. The class is reproducible
on a Mac, whose disk forgives letter case and whose platform repairs it:
`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/PRIVATE/TMP/… mvn clean test` gives
every test a temp directory the platform spells differently. Exactly those
ten failed. *A change walked on a system is not a change tested on it.*

**The lane that failed twice was telling the truth.** The pull request's
macOS lane failed one debugger test, `argsAndEnvReachTheProgram`, on a
branch that had not touched the debugger, and failed it again. Its
transcript ended `thread exited`, `terminated`, with the program's output
nowhere. Printing each frame's connection showed why: the output comes from
the launcher's connection and the end from the target's, and
`DapProxy.endSession` ran on the first `terminated` from either. A fake
adapter that sends the target's end before the launcher's output reproduces
it every time. *A test that fails twice on a branch that did not touch it
is describing the product, not the branch.*

What remains in a staged session's log is the platform's (`Invalid
shortcut: Actions/Help/master-help.xml`, two deprecation notices) and the
upstream grammars' (ledger 129).

## After 3.5.2 shipped (3.5.3)

The Workbench's picture was repainted in fifteen languages for 3.5.2, and
the Arabic one showed its rows as English lays them out: title on the left,
subtitle after it. The walk that followed is the one this plan had not
taken: the staged scenes with the direction forced right-to-left on the
English build (`NMOX_WALK_ARGS=-J-Dnmox.rtl=true`), before and after, and
then the Hebrew build itself.

- **Screen sides.** `BoxLayout.X_AXIS` does not reverse for a right-to-left
  container; `LINE_AXIS` does. The same holds for `FlowLayout.LEFT` against
  `LEADING` and `BorderLayout.WEST` against `LINE_START`. A census counted
  107 of the first kind in 36 files and ten lopsided margins; a script
  replaced them and `ReaderSidesGateTest` keeps them out. Left-to-right
  builds are unchanged by construction: each pair means the same thing
  there.
- **A sweep sees what exists.** Rows built after the sweep had no direction.
  The fix is in the one seam: a container listener, installed only while
  the direction is right-to-left.
- **Machine text.** 118 text inputs were classified by hand, prose or
  machine text, with the decision written at each constructor so that a
  gate can require one of every new input.
- **The empty Overview.** The Hebrew Overview picture was blank and so was
  the committed one, and the German one. The forge had never built it. *A
  defect found while looking for another is first described as an instance
  of the one being looked for.*

Not done: a split pane's sides (ledger 127), which now stand out against
everything else in the window.

## After 3.5.3 shipped (3.5.4)

3.5.2 left "what a staged walk leaves in the log" in the ledger as
seventeen warnings, none ours to fix. That was wrong about nine of them,
and following the last one found the largest defect of this plan.

- **Seven were includes of rules nobody defines.** Upstream grammars rename
  rules and leave includes behind. Thirty-two such names in sixteen
  grammars now have a rule that matches nothing.
- **One was a grammar the engine never loaded.** Markdown's fenced blocks
  include Groovy, Groovy includes a javadoc grammar this product registers
  a stub for, and the engine reported it missing. A probe outside the IDE
  loaded it without complaint with three grammars and lost it with all of
  them: TM4E keeps the rules its dependency walk has visited in a hash set,
  and a rule is a hash map, so Groovy's `{"include": "#comments"}` was
  taken for the same-reading rule of a grammar walked before it. The walk
  also never looks inside captures. Measured over every registered grammar
  as the top one, eight lost a dependency; four one-line rules close all
  eight.
- **The gate for that tokenizes one line with each grammar, and two threw.**
  Elixir and Haxe each carry, among their first rules, a look-behind joni
  cannot compile. Compiling every pattern of every grammar found eighteen in
  nine files. The published 3.5.3 was then opened on a seven-line Elixir
  file: an empty tab.

The Mac walks never opened an Elixir, a Haxe or a Svelte file, and neither
did the forge. The Phoenix console, the Svelte template and the Haxe
learning space were each walked through their run buttons. *A walk is true
of the files it opened.*

The installed-boot check hung on the macOS runner after 3.5.2 and again
after 3.5.3, with no leash (a stock Mac has no `timeout`) and nothing to
read afterwards. The same app walked clean on a Mac and each job passed
when run again, so what hangs is not known (ledger 132). The walk script
now stops itself and takes a thread dump first, so the next one will say.

## After 3.5.4 shipped (3.5.5)

**The hang, explained.** The staged walk of this branch stopped after nine
pictures on the Windows runner, and on the next push on the Linux one, and
the leash 3.5.4 added ended each at 900 seconds with a thread dump. The two
dumps agree. Two threads, both RUNNABLE, each "waiting on the
Class initialization monitor": the event thread inside
`JFXPanel.addNotify` wanting `javafx.scene.Node`, the JavaFX thread inside
`FxBrowserPanel.initFx` wanting `NodeHelper`. The two classes initialize
each other, and the Browser began them from two threads. This is the hang
the installed-boot check met on a macOS runner after 3.5.2 and 3.5.3
(ledger 132), so all three systems have shown it, and it is what a person
would meet as a frozen window on the first click of the Browser tab. Two
reruns had "passed" it. *A hang that
passes when run again is a race.*


"A walk is true of the files it opened" was written under 3.5.4, and the
walk it asks for had still not been taken. The learning catalogue is a
corpus the product already ships: 187 sample files in seventy kinds. One of
each was written to a scratch folder and opened in one launch
(`nmoxstudio --open` takes a list), and the log and three windows were read.

| Seen | Cause | Shipped |
| --- | --- | --- |
| 1,088 warning lines | joni's remarks on pattern style, one per compile, logged by TM4E; a C++ file compiles its grammar's patterns hundreds of times | That logger starts at SEVERE; an explicit level stands |
| `hello.R` in one colour | Extensions match by case off Windows; only `r` was registered | `R`, and the capital spellings of Fortran and COBOL |
| "Language server racket exited with 1", and Perl's five times | The interpreter is on PATH and the server package is not | The four interpreter-hosted servers ask first; only a definite no stops a launch |
| Five SEVERE records from language servers | A server answers a folding request for a file in no project with an error; the platform's client logs it | Not changed (ledger 133) |

| `Found same position 1,950` on opening an `.http` file | A language's editor menu is merged with the inherited one; one NMOX row in each sat at 1950 | A position of its own; the cluster census reads the merged view |

No tokenizer exception in seventy files, nor in the twenty-three written
for the grammars the catalogue does not cover. The gate that came out of
it runs all of them through their grammars on every build, and asks for a
sample of any grammar added later.

## After 3.5.5 shipped (3.5.6)

The review of 3.5.5 asked what registering `.R` had switched on. It had
switched on R's language server for those files, and R, started in a
project's directory, sources that project's `.Rprofile`. The trust law was
in the launch path for a server binary inside the project and for two
servers whose configuration is a program. It was not there for a server
that runs the project in order to analyse it, which is most of them.

A scratch Cargo project with a `build.rs` that writes a file settled
whether this was a reading of documentation or a fact: opened in an
untrusted folder in the 3.5.5 build, the file was written. The fix is one
question in the one method that launches a server, and a table that says,
for each of sixty-three binaries, whether it runs the project's code and
how. The table is the part that needed care. One mechanism in it was
measured; the others are what each server's own documentation says it
does; three could not be established either way and are held. A wrong
READS leaves the hole open for that server, and a wrong RUNS costs one
click, so doubt goes to RUNS.

Two decisions were made that cost something, and both are in the ledger
(134): a file in no project gets only the servers that read, and
TypeScript's server is decided by what it would load, not by its name.

## After 3.5.6 shipped (3.5.7)

3.5.6 asked which language servers run what they are pointed at. The same
question about the other tool the product starts without being asked, git,
had been answered in the spawn ledger in 3.2.0: fixed argv, the user's own
config. The second half was wrong. Git reads the repository's `.git/config`
as well, and that file can name a program for `status`, for a diff, for a
signed log. A scratch repository with `core.fsmonitor` set to a command
settled it in one run of the chip's own command.

The chip had one guard in front of all nine of its spawns, written for the
boot law, so the fix is one more condition in it and a chip that says what
it is waiting for. Line blame and the Standup's log take the same check.

## The proofs

Every unit below was committed, broken the way its test exists to catch,
and seen to fail by name.

| Unit | Mutants | Killed by |
| --- | --- | --- |
| `Chords.forOs` | ⌃ beside ⌘ is not Alt; a space is a key; a hyphen is a key; a Mac is converted too | `ChordsTest` (one of the four lived until a test for "a modifier followed by a space" was written) |
| Chord consumers | a Welcome row shown raw; a literal shown raw; tutorials written raw | `MacChordsReachOnlyMacsGateTest` |
| Window icons | the dark icon removed from the branding jar | `WindowIconBrandingGateTest` |
| The JavaFX race | any NPE in that method absorbed; everything absorbed; never installed | `DisposedSceneRaceTest` |
| Split panes | nothing restored; divider not restored; children not restored; children restored through the pane's own fields; every pane recorded on every sweep | `SplitShapesTest` |
| The forge's Docker view | only the staged run gets it; the app inherits the caller's environment; no daemon leaves the default | `DocsForgeDockerViewGateTest` |
| Contract Studio's toolbar | the one-row layout | `ToolbarFitsItsWidthTest`, failing before the change |
| Grammars | a rule-local repository | `RuleLocalRepositoriesGateTest`, failing before the hoist |
| Counts and scale | the typed count; an adjective before the noun; the walk's scale ignored; AltGr shown | `ExperimentGuideParityTest`, `UiCountLiteralGateTest`, `DocsShotsTest`, `KeystrokeHudTest` |
| Annotation colours (3.5.2) | the setter again; a colour the profile does not give stays on; the user's file unread; found by folder name only, or display name only; alpha hex refused; text never inherited again; an underline stays | `ProfileAnnotationColorsTest` (two lived until the fixture's filesystem displayed a name and a type started with its own text colour) |
| A project's records (3.5.2) | the namespace raw in the name; shared and private under one name; any stored element is the answer; namespace not compared; no ceiling; a DOCTYPE accepted; no namespace accepted; the project does not answer | `WebProjectAuxiliaryTest` |
| `FitLabel` (3.5.2) | cut at the letter; separators kept; no word end means an ellipsis alone; a mark parted from its letter; no tooltip when cut; a tooltip when whole; never grows; asks for its whole text; a new kind keeps the old cut; not said to be cut; a maximum of what is shown now | `FitLabelTest`, `PathLabelTest` |
| Workbench rows (3.5.2) | no spare width taken; four times the budget; the click and the hover not on the subtitle; a list cut as a path; the character cut again; a plain page; a page that follows however narrow; a plain hint; every subtitle one kind | `WorkbenchA11yContractTest` (four lived until a subtitle longer than the budget was tested) |
| One spelling (3.5.2) | the aim compared as given | `OpenProjectsBridgeTest` |
| The session's end (3.5.2) | the target's end ends it at once; a held end waits for ever; an ending session drops the held end; the launcher's end leaves the target's held | `DapProxyTest` |
| New children (3.5.3) | a left-to-right container hands on; never installed; never removed; marked text mirrored; a late split left to the runtime; a pass from inside a pass; installed whatever the direction; not re-asked on a language switch | `RightToLeftApplyTest`, `SplitShapesTest`, `RightToLeftWiringTest` |
| The reader's sides (3.5.3) | a margin on the screen's left; a row along the screen; a gap as a left margin; a hint indented on the screen's left; one window naming WEST again; the margin rule dropped; a blessing ignored | `LeadingBorderTest`, `WorkbenchA11yContractTest`, `ReaderSidesGateTest` |
| Machine text (3.5.3) | a DevTools field as prose; a field undecided; a wrapped constructor waved through | `DevToolsReadsLeftToRightTest`, `TextInputsChooseADirectionTest` |
| Back and Forward (3.5.3) | Back points left whoever reads; either arrow not turning with its window | `NavArrowsTest` (one lived until each arrow was asked alone) |
| The forge's Overview (3.5.3) | the card flipped and nothing built | `DocsTaskBoardTest` |
| The walk's leash (3.5.4) | three mutants of the script's own leash, against a stand-in app that never exits and ignores the first signal | `WalkKeepsItsOwnTimeGateTest` |
| Included rules (3.5.4) | three mutants: an include of the grammar's own missing rule, of another grammar's, and a stub that matches something | `DanglingIncludesGateTest` |
| Loaded grammars (3.5.4) | CoffeeScript's naming rule removed; Groovy's removed; the comparison blind to captures; the comparison taking look-alike rules for visited | `GrammarDependenciesLoadGateTest` (the last two lived until each hole had a fixture grammar of its own) |
| Compiled patterns (3.5.4) | Elixir's look-behind as upstream wrote it; its bound one character shorter; one Svelte mode restored; `begin` patterns not compiled; back-references compiled raw | `GrammarRegexesCompileGateTest` (the fourth lived until the gate was given a grammar with a bad pattern under each key) |
| Engine remarks (3.5.5) | the level never set; an explicit level overwritten; the logger a local; not run at start | `EngineNoticesTest` |
| Shipped samples (3.5.5) | a throwing grammar not reported; Elixir's pattern as 3.5.3 shipped it; Fortran's capitals unregistered; the Zig sample removed (one Svelte pattern restored does NOT fail, and the test says why) | `ShippedSamplesTokenizeGateTest` |
| The inherited popup (3.5.5) | API Studio's row back at 1950 | `LayerPositionCensusTest`, failing before the move |
| Hosted servers (3.5.5) | a probe out of time is a no; no interpreter is a no; a package seen is asked for again; Perl does not ask; a no is not believed | `HostedServerProbeTest` |
| The Browser's class order (3.5.5) | not ordered; ordered after the engine is queued; loaded and not initialized; a runtime without JavaFX throws | `FxClassOrderTest` (its control leaves two threads deadlocked on fixture classes, on purpose) |
| Server trust (3.5.6) | the launch does not ask; an unlisted server only reads; trust not asked; a file in no project starts anything; TypeScript always waits; TypeScript never waits; a waiting server reported missing; rust-analyzer unlisted; a refusal not remembered for the caller; a resolved path not known by its name | `ServerTrustLedgerTest` (the last lived until a reading server was asked for by a path) |
| Git and trust (3.5.7) | the chip may spawn in any repository; the chip never says it is waiting; the menu row does nothing; a yes does not count; blame runs in any repository; blame asks about the file's folder; the Standup's log runs anywhere | `GitChipTest`, `LineBlameTest`, `StandupWaitsForTrustTest` |
