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
for want of the keyboard) and 129 (Linux without a Secret Service; the
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

The mutant for the spoken rows survived at first, in both the new test and
the one 3.4.0 shipped: an `<html>` label names itself in words, so a fixture
painted that way cannot tell whether the code under test ran. *A fixture
that is kinder than the thing it stands for proves the kindness.*

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
