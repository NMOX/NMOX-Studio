# The 3.2 developer-experience plan

*Written 2026-09-24, the night after 3.1.0 shipped. 3.1 asked how long the
first hour takes. 3.2 asks about the second week.*

## The question

The developer who got through the first hour on Tuesday is still here the
following Monday. They commit twenty times a day, review diffs, hand work to
a coding agent and write a README. Where does NMOX Studio make them leave for
another tool, and where does it make them repeat themselves?

## What the first survey found

1. **Git cannot use NMOX Studio as its editor.** `code --wait` is how a VS
   Code user writes commit messages, rebase plans and merge messages;
   `nmox -w` is refused by name ("has no counterpart here"), and so is
   `nmox -d` for `git difftool`. The platform's command-line handshake
   already keeps a caller waiting while a handler works (the server writes a
   keep-alive byte every second, read from the RELEASE310 `CLIHandler`
   bytecode), and every connection gets its own server thread, so a waiting
   commit does not block a second `nmox .`.
2. **The Agent Port forgets its address and token on every restart.** An
   agent configured once is disconnected the next morning; the dialog offers
   a `.mcp.json` snippet but not the one-line `claude mcp add` a Claude Code
   user types.
3. **CONTRIBUTING's everyday rebuild fails.** `mvn -o install -DskipTests`,
   the command it says to type "every time after", fails on the JaCoCo floor
   after any focused test run has left a partial `jacoco.exec` behind: the
   check runs with the tests skipped and measures the leftovers.

## The plan

| # | Unit | Answers | Proof |
|---|------|---------|-------|
| 1 | `nmox --wait` and `nmox --diff`: NMOX Studio as git's editor and difftool, on all three OSes | 1 | a real `git commit` whose message was typed in the IDE; `git difftool` with the diff view |
| 2 | The Agent Port keeps its address and token when asked to, starts with the IDE when asked to, and offers the `claude mcp add` line | 2 | a Claude Code configured once reconnects after a restart |
| 3 | The JaCoCo floor stands down when the tests do | 3 | a focused run, then the everyday rebuild, green |

Every row the walks add is written below as it is found.

## What the walks added, in the order they found it

| # | Found by | What was wrong | What 3.2.0 does | Proof |
|---|----------|----------------|-----------------|-------|
| 4 | walking row 1 with a real `git commit` | the `#` lines of a commit message read as plain text, where VS Code greys them as comments | git's message files and the rebase todo are their own languages (VS Code's MIT grammars, pinned), spellcheck reads only what the author writes, and a summary past 72 characters is a warning | `GitGrammarsTokenizeTest` through the real TextMate engine, `GitSummaryLineTest`, five mutants by name |
| 5 | walking row 1 with the accessibility tree | every editor pane announced itself as "Editor for null": the platform names it after the pane's component name, which nothing sets | each pane is named after its file, and the sentence is overlaid in fourteen languages | `EditorAccessibleNamesTest`; walked in German: "Editor für quit.txt" |
| 6 | walking row 1 as `git difftool` | the diff view it opens paints its chrome (Graphical, Textual…) in English in every translated build; and the view has no way through a long diff but the mouse | the view's sixteen paintable keys overlaid in fourteen languages; the window gains Previous/Next Difference and "Difference 2 of 5", shows a binary file as binary, and takes git's `/dev/null` as an empty side | `DiffViewOverlayLedgerTest` derives the paintable keys from the shipped jar; `EditRequestTest`, `TerminalCommandGateTest` |
| 7 | walking row 5 in German | the Window menu read `Editor(J)`, `Dokumente(G)…`: the Chinese appended-mnemonic form in languages whose labels are letters | 91 appended letters moved into their labels across eight languages, 49 dead accented mnemonics re-lettered (the platform maps only A–Z and 0–9, measured) | `MenuRowsSpeakTest`'s three new laws; Russian and Ukrainian open as ledger 122 |
| 8 | the same German walk | the main window itself still carries English: the editor's Source/History tabs, the Favorites tab, the Quick Search hint | 924 values: every editor tab derived from the layers, Favorites, Quick Search's field, popup and categories, the progress area and INS/OVR | `MainWindowChromeSpeaksTest` |
| 9 | the right-to-left sweep of row 10 | a path beginning `~/` after a Hebrew or Arabic word is drawn `NMOX/app/~` (ledger 121) | measured with `java.text.Bidi`, the rule written, 163 marks in the documents and two in the bundles | `RtlDocsPathDirectionGateTest`, `NativeTypographyGateTest` |
| 10 | ledger 120 | ⌃\` after a re-aim brought forward a terminal sitting in the project the user had left | a shell starts in the new project; a shell that could not start there says so | `ProjectTerminalTest` |
| 11 | writing the README a second-week developer writes | nothing in the IDE says a relative link or a `#heading` in the project's Markdown goes nowhere; GitHub shows the 404 after the push | Tools ▸ Check Markdown Links… checks every relative link and image the way GitHub renders it, to Action Items and squiggles | `MarkdownLinksTest`; on this repository, 436 files and 7,420 links in 616 ms, one real finding |
| 12 | re-reading row 1 as someone who never types `nmox --help` | making NMOX Studio git's editor took three `git config` lines copied from a terminal | Team ▸ Use NMOX Studio with Git… shows the three settings beside their current values, copies them, or applies them | `GitSetupTest` pins them against `nmox --help` |
| 13 | a hostile review of the night's own code | ten findings: a `/dev/zero` link read to exhaustion, two backtracking patterns, the macOS newline guard that never fired, a trailing newline naming another file, git's `/dev/null`, a selected file counting as open, `nmox` unreachable from Git for Windows' sh, and three smaller | every proven one fixed; the remainder is ledger 124 | the tests named in each fix, with mutants |
| 14 | reading every VS Code editing chord against every keymap profile | F12, ⇧F12, F2, ⌘., ⌘], ⌥⌘F, ⌃-/⌃⇧- and ⇧⌥F did nothing; and 3.1.0's ⌘D overrode Emacs' own and IDEA's Ctrl+D | the ten free chords bound, each only where free; ⌘D scoped to the profiles that do not bind it | `VsCodeKeymapResolutionTest` replays the platform's keymap rules per profile and OS |
| 15 | walking the night's build: a `git commit` typed in the IDE, `nmox -d` on text, binary and `/dev/null`, Team ▸ Use NMOX Studio with Git… against a throwaway `GIT_CONFIG_GLOBAL`, Check Markdown Links, Copy GitHub Link | the diff bar's first Next landed on the difference already shown (the controller reports -1 while its divider reads 1/2), and two binary files one byte apart read "The files are the same" | -1 counts as the first; the window compares the bytes itself and says "Binary files that differ" | `EditRequestTest.diffSteps`; the rest walked clean, including the link `blob/main/README.md#L3` and a config written to the throwaway file only |
| 16 | the second week's commonest git pain: a conflicted file | `<<<<<<<` blocks read as plain text, resolved by hand-deleting markers; `git mergetool` had nowhere to go | the blocks tinted in any editor, VS Code's three choices as a Quick Fix, each one undoable edit (a stale offer refused); `nmox -w` as the mergetool | `MergeConflictsTest`, `ConflictFixTest`, `ConflictWatcherTest`, five mutants by name |
| 17 | the step after a push | opening a pull request meant leaving to find the repository and the branch | Team ▸ New Pull Request on GitHub, and on the git chip: GitHub's compare page for the checked-out branch; a detached HEAD refused | `GitHubLinksTest.newPullRequest*`, a mutant by name |
| 18 | "who wrote this line?" — GitLens' most-used answer | the platform answers only with a whole-file gutter (Team ▸ Show Annotations) | the caret line's author, age and summary on the status line; one cached blame per file version; nothing before the user's first gesture | `BlamePorcelainTest`, `LineBlameTest`, `BlameStatusLineTest`, six mutants by name |
| 19 | a failing test's stack trace in the Terminal | `src/app.ts:42:7` was text; the platform links only its own escape, which no tool prints | ⌘/Ctrl-click opens the location, through the terminal's accessibility text; OSC 8 and the platform's friend-only hooks measured and left alone | `TerminalLinksTest` (43), `TerminalLinkClicksTest`, a probe against the real `StreamTerm`, six mutants by name |
| 20 | walking rows 16–18 on the verified build | nothing: `git mergetool` opened the conflicted file with both sides tinted and the hint on the `<<<<<<<` line, Source ▸ Fix Code… offered the three choices, closing the tab handed the file back; line blame read "Walk, 52 minutes ago · init", "Not committed yet" on the markers and "unsaved changes" once edited; both new menu rows present | — | the background tools cannot press an item in the platform's hint popup (Return reaches the document through accessibility), so applying a choice stays pinned by `ConflictFixTest` |
| 21 | "do I need to push?" | the chip said which branch and how many changed files, not where the branch stood against its upstream | `↑2 ↓1` from the same one spawn, as `git status --porcelain=v2 --branch` | `GitChipTest.aheadBehind`, a mutant by name |
| 22 | the main-window agent's recorded remainder | the editor toolbar's tooltips were English in every translated build; the editor's own Back/Forward paint keys the existing `jump-list` overlay never reached | 21 keys × 14 languages, each found by what a German build painted | `MainWindowChromeSpeaksTest`'s four toolbar laws, five mutants by name |
| 23 | a second hostile review, of the agents' code | nine: a kept Agent Port token that followed a taken port (a squatter's harvest), must-act sentences that lived five seconds, `file://` frames not decoded, an unbounded diff side, a stale blame answer and a cached timeout, a FIFO in `.git`, a whole `commit -v` diff copied per pause, `..cache` refused, a remote's token printed in a refusal | all nine fixed; the stray-marker case is ledger 124 | named tests for each, two mutants by name |
| 24 | row 22's agent, reading a German main window | the main toolbar's New File, Open Project and Pause I/O Checks tooltips were English: a toolbar button paints the short description, not the menu row's name v2.102.0 translated | the toolbar gate derives its buttons from the cluster and found 19 keys | `ToolbarOverlayGateTest`, three mutants by name |
| 25 | the translators of row 11's tutorial | three guides named the Edit menu by a word the menu does not use, and the doors gate passed them (ledger 119) | the three corrected; the gate reads a path from a non-menu root when its next segment is a menu's row | `DocsMenuDoorsTest`, the reintroduced error failing by name |
| 26 | a German build's Team menu, in the git release | every git row English: its names live in git's action classes, where no layer-derived gate looks | 61 keys × 14 languages, found by what the menu painted, one name per action (the chip's words) | `CodeNamedMenuRowsTest` (177 rows), `MenuRowsSpeakTest`, two mutants by name |
| 27 | the boot law, measured on the release build: a relaunch under JFR with a tracked file's editor restored | nothing of ours: the four processes at boot are the platform git module's JGit finding the system git config (`which git`, `xcode-select -p`, `git --version`, `git config --system`) because a repository is open — not NMOX Studio code, and not new; no `git blame`, no `git status` | — | `jfr print --events jdk.ProcessStart`, stacks read to the git module's `FilesystemInterceptor` |
| 28 | a second-week search for a function name | Find in Projects returned the copies in `node_modules` and `dist/` beside the code (4 hits for 1) | the project answers `SharabilityQuery` with exactly what git ignores (a new bounded `.gitignore` reader), never more — the git module reads the same answer | `FindInProjectsIgnoresTest` on the real search API, `GitIgnoreTest`, six mutants by name |
