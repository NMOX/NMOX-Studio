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
this file is the truth.

The v1.26.0 sprint took a rule to the whole ledger: **build every
feature-shaped item; re-examine every refactor-shaped item with fresh
evidence rather than a remembered reason.** The feature items (0a–0f)
all shipped. The refactor items (1–7) were each re-inspected — most
verdicts held and two sharpened into outright won't-fix once the code
was read again rather than recalled. A deferral you can defend after
re-reading the code is a decision; one you only remember making is a
guess. These are decisions.

## Open — added by 3.2.0 (the second-week release)

### 122. ~~Russian and Ukrainian menu mnemonics do nothing~~ — CLOSED after 3.2.0

**Closed.** The platform's `org.openide.awt.Mnemonics`
gives a key only to `A–Z` and `0–9` and looks any other letter up with a
plain `ResourceBundle.getBundle("org.openide.awt.Mnemonics")`, which finds a
table only in an UNBRANDED locale jar (`org-openide-awt_ru.jar`). The entry
called that jar impossible because the branding goal writes
`org-openide-awt_nmoxstudio_ru.jar`; read from the mojo, it names a jar
`brandingToken + "_" + the file's locale`, so a second `nbm:branding`
execution whose token IS the locale, over a file with no locale suffix,
writes exactly the platform's name. `branding/src/main/nbm-mnemonics` holds
the two tables (each letter on a letter key to the Latin letter on that key
in the ЙЦУКЕН layout; letters on punctuation keys deliberately have no entry,
because an open menu ignores a typed character that is not a letter or digit,
so a mnemonic there would underline a key that does nothing); they ride the
cluster, the branding NBM and its `update_tracking`, so an update-center
install gets them too. Measured on the assembled app under `--locale ru`:
241 "Mapping from a non-Latin character" refusals at boot without the table,
0 with it (and 0 under `uk`). With the letters live, fourteen of them turned
out to press a key another row of the same menu already claimed (`Другая
&VCS` and `От&менить` both V) or kept an appended Latin letter though a
letter of their own was free; the mnemonic laws now compare the KEY a
mnemonic presses (the letter comparison, as a control, passed the collision
silently) and the fourteen were moved. The hostile review of that change
found 26 more values underlining a letter on a punctuation key (`Со&хранить`,
`З&берегти як…`), which the first tables mapped to the key code and which an
open menu cannot select; they moved to letter keys. What stays true of every
Cyrillic mnemonic, and is Swing's (`BasicPopupMenuUI` compares the typed
CHARACTER inside an open menu): an item is selected there only while a Latin
layout is active; Alt+letter on the menu bar and on a dialog's buttons goes by
key code and works under either layout. `TranslatedMnemonicsTest` runs the
platform's own `Mnemonics` over every Russian and Ukrainian value in the
cluster.

**The rest of the entry, closed the same way.** The eight languages written
in Latin letters ship one generated table each (every precomposed Latin
letter to its base letter's key: `&Édition` presses E, `&Đóng` D, `Zwi&ń`
N), so the accented mnemonics work where the translators put them; a
French boot logged one refusal (the É) without it and none with it. Hindi
needed no table and got none: its convention appends a Latin letter, and
25 values (its whole top menu bar among them) underlined Devanagari
instead; they now append the letter the other appending languages use
(`फ़ाइल(&F)`, the Refactor menu `(&G)`). The top menu bar gained its
uniqueness law, measured through the platform's own `Mnemonics`, and it
found clashes in four languages, not just the French one this entry
recorded: French (A three times, N twice), Spanish (E, A, V), German (A
three times, D) and Russian (three, visible only once its letters had
keys). `TranslatedMnemonicsTest` holds all of it for every translated
language.

### 123. The platform status line can drop a message (upstream)

**Open, upstream's.** `NbStatusDisplayer.add` reads the FIRST message of
its list where it means the current one, so once a message nobody holds a
reference to has been garbage-collected at the head, the next plain
`setStatusText` lands behind an older live one and is not shown (probed on
RELEASE310 in 3.2.0: "second", a dropped message, "third" still reads
"second"). Found because two ui tests read each other's status lines;
the tests no longer share it (`EditRequestWatcher.status` is a seam, and
`PointAnAgentActionTest` reads once first). The product shares the
exposure; v2.183.0 already holds its one long-lived message in a field.

### 124. The 3.2.0 review's LOW remainder

**Open, deliberately small.** The hostile reviews of the night's code found
forty-five problems; every proven one was fixed before 3.2.0 shipped (a `/dev/zero`
link read to exhaustion, two backtracking patterns, the macOS newline guard,
a trailing newline naming another file, git's `/dev/null`, a selected file
counting as open, Windows sh). What is left:

- **Ctrl-C at `nmox -w` on Windows** leaves its `%TEMP%\nmox-request.*`
  folder: cmd has no trap. The folder is private and small.
- **A Linux file name that is not UTF-8** is refused ("the request could
  not be read") rather than opened; the request file is read as UTF-8.
- **Team ▸ Use NMOX Studio with Git…** asks whether the IDE can find
  `nmox` (the product's tool lookup, which also searches Homebrew's
  folders), not whether git's shell can; a GUI-launched IDE and a
  terminal can see different PATHs.
- **The diff view's Textual tab** writes its patch header ("# This patch
  file was generated by NetBeans IDE") in code, not a bundle, so it stays
  English.
- **A stray `<<<<<<<` earlier in a file** (a documentation snippet, say)
  leaves the merge-conflict parser inside an unclosed block, so a real
  conflict after it reads as nested and gets no tint and no hint. The parser
  refuses nesting on purpose (it cannot tell a recursive merge's markers from
  prose); restarting at a later marker would guess. The file still opens and
  git still owns the merge.
- **One unreadable negation turns a repository's search back to
  everything.** `core.util.GitIgnore` cannot read a POSIX class
  (`![[:digit:]]*.log`); rather than guess what it re-includes, the file
  becomes doubtful and no path beneath it is answered "ignored" — so
  `node_modules` is searched again in that repository. The under-matching
  side, by design: a wrong "ignored" is remembered by the git module and
  hides the file from Commit.
- **The first read of a `.gitignore` can land on the EDT.** The versioning
  module asks the sharability query for each top-level child while it
  decides whether a Team action is enabled; each answer is a few stats,
  but a changed `.gitignore` (at most 1 MiB) is read there once.
- **Ctrl+Alt+letter chords and AltGr (Windows).** ⌥⌘C is Ctrl+Alt+C off
  macOS, and on a Polish layout AltGr+C types "ć"; the platform's shortcut
  processor has no AltGr case (its bytecode read in 3.2), so if the JDK
  reports AltGr as Ctrl+Alt without the AltGraph flag the chord could fire
  on the letter. The ⌥⌘ family has been in the product since v1.38.1
  (⌥⌘O Open Folder, ⌥⌘E Emmet, ⌥⌘P), so this is one question for the whole
  family, answerable only on Windows with a Polish (Programmers) layout.
- **Find in Projects on a network mount.** Every question re-reads what it
  rests on (about 40 µs a file, seven levels deep, on local APFS); a mount
  where a stat costs a millisecond makes a 20,000-file search pay seconds.
  Measured locally only.
- **A `.gitignore` with a modification time in the future** (a tarball,
  `rsync -t`, a skewed network mount) is never "settled", so it is re-read
  on every question — correct, and about a millisecond a time for a 1 MiB
  file. A mount whose clock runs BEHIND makes fresh files look settled at
  once, and an in-place edit that restores both the time and the size is
  not seen; both are recorded, neither measured on a real mount.
- ~~**Quick Search's *Terminal: Create New Terminal*** fires ⌃`'s action,
  which brings an open terminal forward rather than always starting one.~~
  Closed after 3.2.0: the row fires *New Terminal in Project*
  (`ProjectTerminalNew`), which always starts a shell through
  `ProjectTerminal.openNew`, the Terminal button's own rule.
- **Copy Path from a diff pane or a history revision** may copy the
  platform's temporary file for that side. Plausible, not walked.
- **The grouped-DataObject rules guard nothing today.** `EditedFile` and
  `EditorTabs` name the file a document or tab holds when one DataObject
  owns several; this platform's properties loader forms no such group
  (`nestedView` is false and nothing sets it), so the rules, their gate and
  the group branches of Annotate and the Workbench are exercised only by
  tests. Kept because the rule is right for any DataObject of several
  files; if a platform bump turns grouping on, the table editor and a tab
  not shown yet name the group's primary, a renamed group's open editor
  names nothing until reopened, and a group's modified flag is the group's.
- **Action Items' project scopes see nothing in a folder with no
  manifest.** Check Markdown Links… (and every other finding) reaches the
  platform's Action Items through its scanner, whose "current project" and
  "open projects" scopes ask the platform for a project; a folder aimed
  with `nmox .` that has no manifest is no project to it, so the window
  reads "no current project" and lists nothing. The squiggles, the ✕/⚠
  count and the status line's sentence still speak, and the window's
  current-file scope lists them (walked in 3.2: nothing under the project
  scope, three rows under current file, all three again once a
  `package.json` made the folder a project).
- ~~**A git folder not named `.git`** (`--separate-git-dir`, a bare
  repository) is not recognised by the left-over-message sweep, so a
  restored `COMMIT_EDITMSG` there reopens as before.~~ Closed after 3.2.0:
  `GitRequestFiles` also recognises git's own shape (a `HEAD` beside
  `objects` and `refs`, or a linked worktree's `HEAD` beside `commondir`),
  tested against folders real git made; the sweep reads that on a lane.
- **If git's `nmox -w` starts the IDE and the restore also brings back a
  tab on the same message**, the request waits until both are closed.
  Plausible from the code, not walked.
- **The action census** still cannot read `Actions . forID(` with spaces
  around the dot, and reads the last of two same-named constants in one
  file. No source spells either.
- **The census pins are keyed by a file's simple name** (the action census's
  helpers, the edited-file blessings): two sources of one name in two
  modules would share a pin. None do today.
- **`nmox -d` refuses two binaries over 16 MiB** although their
  comparison runs off the EDT.
- **The diff view still reads both files on the EDT.** After 3.2.0 our part
  moved: the 8,000-byte binary sniff and every DataObject lookup run on a
  lane, then the request is opened and tracked in one EDT turn. But the
  platform's `EditableDiffView` sets its two sources in an `invokeLater`
  and reads each whole file there (its bytecode, read after the review
  said so), and a line-positioned open can load the document on the EDT
  too. Neither is ours to move; a large file compared with `nmox -d`
  pauses the window for as long as that read takes.

## Open — added by 3.1.0 (the developer-experience release)

### 118. macOS: Finder's Open With and the Dock cannot hand the IDE a folder

**Open, deliberately.** A folder reaches NMOX Studio on macOS through
`nmox .` and File ▸ Open Folder…; on Linux and Windows the file manager
offers it too (3.1.0).

AWT answers open-documents events only when the process's main bundle
declares `CFBundleDocumentTypes`. The JVM is a child of the platform's
`nbexec` script, so CoreFoundation takes the runtime's own embedded
Info.plist as the main bundle (`com.azul.zulu.java`), and the event never
reaches Java. Exporting `CFProcessPath` naming the bundle's executable
fixes that **under an ad-hoc signed runtime and not under ours**: measured
on the notarized 3.1.0 dry run, one small AWT program reads
`org.nmox.studio` under Homebrew's ad-hoc `java` and `com.azul.zulu.java`
under the release's hardened one. CoreFoundation ignores the variable in a
restricted process, and the only entitlement that lifts the restriction,
`com.apple.security.cs.allow-dyld-environment-variables`, also re-opens
DYLD injection into a notarized app. That trade is not worth a folder drop.
3.1.0 shipped the change and took it out the same night, before release,
because a declared folder type would list the app under Open With and then
do nothing.

**What would close it:** a native Mach-O launcher that starts the JVM
in-process through JLI (the shape `jpackage` builds), so the bundle's own
executable is the process and its Info.plist the main bundle, with no
environment variable at all. That replaces the shell launcher and its
Gatekeeper-proven `exec /bin/sh` path, so it needs its own notarized dry
run. `OpenFolderFromOsGateTest` holds the absence until then.

### 119. The menu-doors gate checks only paths that start at a real menu

**Closed in 3.2.0.** Tonight's translators found three guides naming the Edit menu wrongly (es `Editar ▸`, vi `Sửa ▸`, id `Sunting ▸` where the menus read Edición, Chỉnh sửa and Edit), and the gate had passed all three. It now also reads a path whose first segment is not a menu: when the next segment is a row some menu has (two words or more), the path names the wrong door — unless the segment before the arrow is itself a row (a dialog's tab after a leaf, `Options ▸ Keyboard Shortcuts`), the macOS `Settings…`, the menu with a joined conjunction (Arabic و, Hebrew ו), or a right-click. The reintroduced `Editar ▸ Copiar como Markdown` fails it by name. What follows is the entry as it stood:

**Was open, deliberately.** `DocsMenuDoorsTest` walks a `▸` path only when its
first segment is a top-level menu of the document's language. A translated
guide that writes a menu that does not exist (`Werkzeuge ▸` where German
reads `Extras ▸`, `Ver ▸` where Portuguese reads `Exibir ▸`) is therefore
never checked. The 3.1.0 translators found and fixed every instance they met
by reading the bundles, which is how the class was found. A census of the
translated docs' path roots shows a small set, but the legitimate non-menu
roots (the Options dialog and its tabs, the Plugin Manager's tabs, the macOS
app menu) are not derivable from the layers, so a strict rule would need a
hand-kept allow-list per language — the shape `PlatformDialogLedgerTest`
keeps for dialogs. Worth building when a translation next changes many
paths; until then, a translation brief names the bundles as the only source
of a door's name.

### 120. The 3.1.0 review's LOW remainder

**Open, deliberately small.** A hostile review of the night's code found 21
problems; the proven ones and every MED were fixed before 3.1.0 shipped (the
`.editorconfig` glob backtracking, the Agent Port's secret files, the
diagnostics tap's framing and its end/record race, recents on the EDT, the
`nmox` refusals, the tasks.json shell). What is left, each suspected rather
than proven:

- ~~**The terminal after a re-aim.**~~ **Closed in 3.2.0**: ⌃\` after a
  re-aim starts a shell in the new project (`ProjectTerminal.decide`), and
  the Terminal button's shell counts as the project's too.
- ~~**A terminal that could not start in the project**~~ **Closed in
  3.2.0**: the fallback says so on the status line.
- **The problem count's click** does nothing if the platform's Action Items
  action is missing, which only a trimmed cluster could cause.
- **`ToolbarAccessibleNames`** adds a container listener per toolbar-pool
  change to any non-`JComponent` container inside a toolbar; none exist
  in the shipped toolbars.
- **The save-time settings lookup** (the second review): each save now
  also walks up for a `.vscode/settings.json` (bounded, stat-only until a
  file is found) on the thread the platform saves on, beside the
  `.editorconfig` walk that already ran there. Accepted as recorded; if a
  profile ever shows it, the answer is the code-style provider's cache.

### 121. Two translation questions the 3.1.0 translators raised

**Open, for a language owner.** Neither is a defect a gate can decide.

- **Ukrainian "debug".** The shipped Ukrainian bundles say *налагодження /
  налагоджувач / Налагодити*; the Ukrainian documents mostly say
  *зневадження / зневаджувач*, so a section titled with one leads into a
  menu label with the other. The menu labels quoted in the docs match the
  bundles; the running prose is the question. `glossary.json` has no entry
  for "debug" either way.
- **Home paths in right-to-left prose.** conventions.md asks for an LRM
  before a dotfile's leading dot after a right-to-left word (done across
  the newcomer documents in 3.1.0). A path starting `~/` begins with a
  neutral too, and about thirty such paths in the Hebrew and Arabic docs
  carry no LRM. Whether the rule should say "a path beginning with a
  neutral character" is the decision; the sweep after it is mechanical.
  **Decided in 3.2.0, by measurement:** `java.text.Bidi` in a
  right-to-left paragraph draws `שלום ~/NMOX/app` as `NMOX/app/~` (and
  `./`, `../`, `/usr`, `.env`, `*.json` the same way, a trailing `/` or
  `.` detaching to the far side too), so conventions.md now asks for an
  LRM before a path beginning with a neutral and after one ending with
  one, 163 marks were added across the Hebrew and Arabic documents, and
  `RtlDocsPathDirectionGateTest` derives the population and pins the
  measurement.

## Closed by v2.186.0 — every "Decided, not done" item, done

Recorded by v2.184.0's senior-developer pass; all seven closed in v2.186.0.

**Seven of these carried the phrase "Decided, not done" — the decision taken,
the work postponed for size.** That is the most expensive shape a ledger entry
can have: the thinking is already paid for and none of the benefit is banked,
so the entry has to be re-read by every future author while the defect it
describes goes on shipping. Closing them found **nine live defects** nobody was
looking for, including a four-month-old `NullPointerException` on the IGNITE
button, two files destroyed by a read failure (measured at 9,437,184 bytes
going to 550 and to 48), a write that followed a symlink out of the project,
and four Options controls that left Apply permanently grey.

**It also found that six of these entries were wrong about their own subject**,
and those corrections are recorded in place below, because a corrected claim is
worth more than a fix applied to nothing.

A read-only survey of the 910 product files across three dimensions — one
fact with two homes, work that was never finished, and shape that makes the
next change expensive. Everything it found is here: fixed in v2.184.0 and
marked so, or open with a decision attached. A finding without a decision
is just a list.

**Two claims in those surveys did not survive checking, and are recorded
because a corrected claim is worth more than a fix applied to nothing.**
The missing `.cmd` suffix in `CommandDevice.toolOnPath` was reported as a
live Windows bug affecting "every console whose tool is an npm shim"; the
three callers probe `slither`, `stellar` and `anchor`, which are real
binaries, so it is a latent trap in a shared base class and nothing more.
And `LearningSpace.resolveInside` was reported as returning an
un-canonicalised path; it does, and that is worth fixing, but no caller
today reaches it with a symlinked segment.

### 110. ~~Nine different answers to "which directories does a walk skip"~~ — CLOSED v2.186.0

Twelve declarations across four modules, nine distinct sets. Only
`node_modules`, `.git`, `dist`, `build` and `coverage` appear in all twelve.
`target` and `out` appear in eleven — **the exception being
`core.util.HeavyDirs`, the class whose javadoc calls itself "one home … so
the file tree and every later walk elide the same names"**, and which only
two callers use. The sharpest instance: `editor/fullstack/Routes.java`
privately re-declares a set byte-identical to the `public`
`BoundedWalk.SKIP_DIRS` in the same package, and never references it.

**Decided, not done.** The right home is `HeavyDirs`, widened, with each
caller adding its own extras (`.nmox` for the two that must not descend into
the IDE's own state, `.idea` for the file watcher) — several differences are
legitimate and must survive the merge. It is not in v2.184.0 because
choosing WHICH of the nine sets is correct changes what a dozen scans see,
and that wants its own release with its own walk. What is not deferred is
the reason it grew: nothing derives or gates this population, so a
thirteenth copy can land tomorrow. The gate comes with the fix.

**Closed v2.186.0, and the entry was wrong about its own census four ways.**
`HeavyDirs` is the one home, widened from 5 names to 13, with `plus(String...)`
so a caller differs by ADDING. The merge was verified MONOTONE — every one of
the declarations now skips a strict superset of what it skipped before, nothing
narrowed. `HeavyDirsLedgerTest` derives its population from **911 files** and
fails on a new declaration.

The corrections: there are **thirteen** declarations and **ten** distinct
answers, not twelve and nine — the thirteenth is `Workspaces.walk`'s inline
`"node_modules".equals(dir.getName())`, which the entry's own set-literal census
could not see. *The entry written to close a blind spot contained that blind
spot*, so the gate's population is now FILES NAMING the directory, not set
literals. Across all thirteen the universal intersection is `node_modules`
ALONE, not five names. And the list of legitimate extras missed `vendor` (six
callers) — the one union name that could not be promoted, because the Classic
Kit WRITES `vendor/jquery-3.7.1.min.js` into the project and wires a script tag
at it, so a file tree refusing to expand it would hide files the IDE itself just
put there. `.cache` was missed beside `.idea` too.

Two live bugs fell out: `Workspaces` offered `dist/my-lib/package.json` (an
Angular library build's own output) to WAYPOINT as a package to dial, and
`ProjectInspector` read Next.js standalone output's `package.json` as a nested
Node project nobody wrote.

### 111. ~~Four canonical-containment guards, three of which call themselves the canonical one~~ — CLOSED v2.186.0

`LearningSpace.resolveInside` canonicalises both sides then returns the
UNRESOLVED path, so a caller that passes the check still reads through the
link. `SiteServer.resolveInside` accepts root-equals-root where
`LearningSpace` refuses it. `PageSourceResolver.insideOnly` canonicalises
only the target, then does a string prefix test against a canonical root.
And `DockerRecipes.resolveInside` — **a write path** — is `normalize()`
only, no canonicalisation at all, under a javadoc presenting it as the
structural resolution of the v1.290.0 guard lesson.

**Decided, not done.** One home in `core.util`, with the symlink policy and
the root-equals-root policy each decided once. Held back deliberately:
tightening a containment guard changes what is refused, and this family
guards writes and a served docroot. It belongs in a release that can walk
each refusal, not folded into twenty other changes. No instance is
exploitable today — checked — so this is a hardening, not an incident.

**Closed v2.186.0.** One home, `core.util.Containment`, with both policies
decided once and written in its javadoc. **Symlink policy: the guard returns the
CANONICAL resolved path** — the check and the read must name the same file, which
is the `LearningSpace` defect exactly. **Root-equals-root: REFUSED** — every
caller asks "which FILE?" (three call `isFile()`, the fourth writes bytes) and
`SiteServer` appends `index.html` before calling, so accepting the root only
defers the refusal into a silent `isFile()` false or a raw "Is a directory" from
the OS, a refusal that no longer names containment.

**The write path was the live one**: `DockerRecipes.resolveInside` was
`normalize()` only, so a symlinked segment leaving the project was FOLLOWED and
written through. It refuses now. Each surface keeps its own refusal sentence, and
each is walked with a real symlink through the real caller path (`SiteServer`'s
over a raw socket, because the JDK's `HttpClient` normalises `..` away before the
request leaves).

Three more instances outside the named four — `DebugEntries` (Path-based,
returning the unresolved file, on a debug-launch path), `DocsStaging` and
`NgSchematic` — were found in the same pass and swept in the same release.

### 112. ~~Four hand-maintained mirrors of one kind-to-token vocabulary~~ — CLOSED v2.186.0

`RunDevice` carries the target tokens three times (an append-only knob
array, `ProjectKind -> String`, and its inverse) plus a third switch in
`buildCommand`; `TestDevice` repeats the shape with its own vocabulary. The
four currently AGREE — that is the finding. They agree by discipline, and
both `default` arms answer `NODE`, so a missed arm compiles, ships, and
mislabels instead of greying honestly. Two producible tokens are already
absent from the knob arrays.

**Decided, not done.** `ProjectKind` already declares its own facts where it
is declared — v2.184.0 added `manifests()` for exactly that reason — so
`runTarget()` / `testRunner()` belong beside them, with the inverse derived
by streaming `values()` and `default` yielding null so the existing honest-
grey path takes over. Adding a language is the most repeated change in this
repository and today it costs six switch edits with no compiler help. Left
out of v2.184.0 only for size; it is the next one worth doing.

**Closed v2.186.0, and the entry's central claim was false.** It said *"The four
currently AGREE — that is the finding."* They did not: `gradle` had no
`commandDir` arm, so it fell to `default -> NODE` and ran `gradle test` **in the
Node lane's directory instead of beside `build.gradle`**. The disagreement the
entry described as hypothetical was already shipping.

`runTarget()`/`testRunner()` now live on `ProjectKind`, the inverse is derived,
and `default -> NODE` is gone, so FOUNDRY, LEARN and NONE grey honestly instead
of inventing a node command. The two missing tokens were `webpack` (IGNITION) and
`gradle` (VERITAS), both appended so the knob's on-disk index contract holds.
`VeritasRunnerMatrixTest`'s own mirror had drifted too — it stopped at `aiken`
and never learned the `node` position v1.252.0 appended.

**And a four-month-old NullPointerException**: `RunDevice.primaryAction()` ran
`cmd.contains("webpack")` where `cmd` is null for Tact and ReScript, so pressing
IGNITE threw before reaching the refusal three lines below. The honest grey those
arms promised since v1.161.0 was **unreachable from the button**, because no test
had ever pressed it on a null.

### 113. ~~Contract Studio's Watch engine lives in a TopComponent and no CI run tests it~~ — CLOSED v2.186.0

About 400 lines of non-UI orchestration inside `Web3StudioTopComponent`:
subscribe-or-poll selection, scheduled polling, head-gap backfill, two-lane
dedup and decode, and a coalescer. Every COLLABORATOR has its own tested
class — `WatchReconciler`, `WatchSocket`, `WatchFeed`, `WatchRows`,
`WatchCursor`, `WatchEndpoint` — and the only test of the orchestration is
`WatchStreamAnvilLiveTest`, whose javadoc says it is skipped when `anvil` is
absent, which is every CI runner. So the socket-vs-poll choice, the gap
fill, and the generation guard that stops a dying tick tearing a new
session's cursors are verified on no CI run, in the most concurrency-dense
code in the module.

**Decided, not done.** Extract `engine/WatchRunner` taking a client
supplier, the feed, and a three-method UI callback; the tab and its
callbacks stay where they are. The existing fake WebSocket server in the
test tree can drive it headlessly. Deferred for size, not for doubt.

**Closed v2.186.0.** `engine/WatchRunner` takes a client supplier, the feed and a
UI callback; the TopComponent lost 410 lines and holds only Swing. The callback
has **four** methods, not the sketched three — the original had two distinct row
signals (`watchAdvanced` for table-plus-chip, and a coalesced table-only refresh
for streamed logs), and folding them would have either dropped the coalescing or
marched the chip backwards on a late log.

All four unverified behaviours are now tested headlessly against the existing
fake WebSocket server: socket-vs-poll selection, a cut socket resuming with no
duplicate and no gap, head-gap backfill, and the generation guard.

**The finding is about testability, and it is this arc's recurring law**:
`WatchFeed.addBlock` dedupes by hash itself, so a duplicate block fetch is
INVISIBLE in the feed. An assertion written against the feed alone — the obvious
thing to write — passes while a real duplicate-fetch regression ships. The test
asserts on the transport's recorded fetches as well.

### 114. ~~Half-built, and each half is visible~~ — CLOSED (three in v2.186.0; the LSP providers had already shipped in v2.184.0)

Four places where the plumbing for a feature was written and the feature
was not. Each is small; together they are a pattern worth naming, because
every one of them reads as complete from the outside.

- **`TEMPLATE_EXPRESSION`** — a JavaScript token id, a lexer state, AND a
  registered colour in `syntax-colors.xml`, wired into the live dark profile.
  Nothing ever emits it: `finishTemplateLiteral` counts `${`/`}` depth and
  swallows the interpolation into one string token. The depth counting is
  already there. **Decision: finish it** — the colour is authored and the
  arithmetic exists; a `${user.name}` inside a template literal should lex as
  JavaScript.
- **Cairo and Move in the LSP catalog** — two rows the Language Servers panel
  renders with status and a runnable install, and no `LanguageServerProvider`
  anywhere launches either binary. 41 of the catalog's 43 have one. The
  changelog claims both (v1.134.0 "scarb serves the LSP", v1.137.0
  "move-analyzer LSP"), which makes this the v1.189.0 law: a claim the code
  does not back. **Decision: add the two providers**, in the shape the other
  65 use, and say plainly that neither has been live-verified.
- **Emmet's `count`** — a repeat total threaded through five call sites and
  never read; the shape of `$@` reverse numbering, declared and not built.
  **Decision: delete it.** The javadoc promises only `$` and `$$`, both of
  which work without it.
- **`GhostText.armed()`** — public, zero readers. Written for a guard that was
  never added; `arm()` already calls `dismiss("")` first, so re-arming is
  correct without it. **Decision: delete it.**

**Closed.** `TEMPLATE_EXPRESSION` is emitted — `` `a${b.c}d` `` now lexes as
TEMPLATE_STRING / TEMPLATE_EXPRESSION / IDENTIFIER / DELIMITER / IDENTIFIER /
TEMPLATE_EXPRESSION / TEMPLATE_STRING, with the brace-depth and backslash-escape
arms each killed by their own named mutant. Emmet's `count` and
`GhostText.armed()` are deleted.

**The entry was stale about its own second sub-item.** `CairoServer` and
`MoveServer` were added in **v2.184.0 itself** — the release that wrote this
entry — as nested classes in `LanguageServers`, each single-mime, each with the
honest limit in its javadoc (*"has NOT been verified against a real scarb
install"*). The decision was recorded as pending for work that had already
shipped. That is a staleness the ledger's own honesty gate cannot catch: it
checks whether a SECTION headed Open holds open work, not whether an item's
sub-decision was quietly delivered.

### 116. ~~The read-failure clobber has two more homes~~ — CLOSED v2.186.0

v2.184.0 fixed the class in the three files it was briefed for — the task
board, the database workspace and the contract workspace — and the agent
that fixed it found two more while reading. Both throw correctly at the IO
layer and both consumers swallow it:

- `apiclient/.../api/WorkspaceIO.java` — `ApiClientTopComponent.readWorkspace`
  catches `Exception` and falls back to `starterWorkspace()`.
- `infra/.../model/GraphIO.java` — `InfraDesignerTopComponent` catches
  `Exception`, calls `graph.clear()`, and stamps `designSync.recordOwn(...)`
  in a **`finally`**, so the file is marked as ours on the catch path too.

**Decided, not done, and the reason is scope rather than doubt.** The fix is
exactly the one that shipped: a typed read failure the caller binds
read-only, no ownership stamp for a file that was never read. It was left
out of v2.184.0 to keep a data-loss change inside the surface that had been
probed and measured — the three that shipped were each proven destroying a
9.4 MB file before a line was changed, and the same proof is owed here. It
is the next thing to do in this family.

**Closed v2.186.0, with the destroy measured on the shipped code first, as owed.**
Driven through the real windows' own load and save, not a replayed sequence:
apiclient's `.nmoxapi.json` went **9,437,184 bytes to 550**, infra's
`.nmoxinfra.json` **9,437,184 to 48**. Both survive intact now.

Infra was the worse of the two exactly as recorded: `DesignSync` answered NONE
for the file's real stamp, because `recordOwn` in the `finally` ran on the catch
path — **the never-clobber guard was disarmed by the very failure it exists
for**. Three cases are kept distinct: ABSENT takes the starter, UNPARSEABLE keeps
the v1.36.0 `.bak` law, UNREADABLE binds read-only, writes nothing, stamps
nothing and says so in fifteen languages.

The mutation that matters most removed the save guard while still withholding the
stamp: the file is still lost, which proves the two halves are independently
load-bearing rather than one masking the other. A find from reading the fix back:
a read-only bind records no stamp, so infra's next check saw an unknown version
and announced *"Reloaded — the file changed outside the designer"* when nothing
had changed and nothing had been read.

### 115. ~~Two gates whose population or path is smaller than their claim~~ — CLOSED v2.186.0

- **`GrammarBundleTest`** filters by filename `*Grammar.java`, so
  `EmbeddedScopeGrammars.java` (49 registrations) and `NgTemplateGrammars.java`
  (6, including every Angular-template grammar) are invisible — **55 of 131
  registrations outside a gate whose stated purpose is that a typo would
  silently kill a language's highlighting**. All 55 resolve today, so this is
  an unguarded surface rather than a live defect. **Decision: derive from the
  generated layer**, as its sibling already does, with a non-empty floor.
- **`SassCompilerTest.argvPinned`** puts its assertions inside
  `if (outcome == COMPILED)`, and `SassCompiler.compile` resolves the binary
  BEFORE the injected fake runner — so on any machine without dart-sass, which
  is all three CI lanes, the test asserts only that the compiler did nothing.
  The argv it is named for is never checked. **Decision: inject the binary
  resolution too**, so the fake runner is always reached.

**Closed v2.186.0 — and 115a's stated failure mode does not exist.** The entry
said *"a typo would silently kill a language's highlighting"*. Planting that exact
typo (`yaml.tmLanguage.json` -> `yaml.tmLanguag.json`) makes **javac fail**: the
platform's `CreateRegistrationProcessor` calls `LayerBuilder.validateResource` and
then READS the grammar to extract its `scopeName`. So widening the gate by
filename would have been decorative — it could never have caught the named defect,
in the old population or the new.

The widening happened anyway, from the generated layer (**76 -> 131**
registrations across 88 files, with a floor), and it now holds three laws the
compiler does NOT prove — including `EmbeddedScopeGrammars`' own prose rule that
an embed-only mime is a scope for TM4E's registry and not an editor, which was
load-bearing and enforced by nothing.

**115b's mutant genuinely lived before**, and that was proven rather than
asserted: modelling the CI condition (no dart-sass, so the resolver answers
`null`) against the shipped test body with `--no-source-map` deleted gave
`Tests run: 1, Failures: 0` and BUILD SUCCESS. The test named for an argv
asserted nothing on all three lanes. The binary resolution is a seam now, so the
fake runner is always reached.

## Closed by v2.184.0 and v2.186.0 — the shaping arc's review items

### 100. ~~A width fit is computed per SIZED font, not per font~~ — CLOSED v2.186.0

**What it is.** `ComplexTextShaping.fits` keys on the `WCFont` instance,
which WebKit creates one of per family *and size and weight and style*.
The fitted parameter is a ratio of widths measured at one size, so it is
dimensionless and identical across those — but a page using one family at
`h1`, `h2`, `body`, `strong` and `em` pays the whole corpus fit five times
instead of once. Each fit is 21–29 laid-out words plus one advance per
distinct letter, roughly 60–90 fresh JavaFX layouts, and it runs on
whichever thread holds WebKit's page lock — the FX application thread
during layout, the render thread during paint. So the cost lands on a
frame, not on a background lane.

**The key is DECIDED: the `FontResource`, not the family.** A bold face is a
different file with different metrics, so "per family" would share a fit
between faces that can legitimately disagree about it — the cache would be
faster and sometimes wrong, which is worse than slow. That question is
answerable from the code and does not need a machine to answer it, so it is
answered here; what still needs a machine is whether the win is worth the
change at all.

**What remains deferred, and why.** The measurement: how much a fit actually costs on a real page
(the repaint budget the v2.171.0 cache was built against was ~12 ms p90),
and whether two `WCFont`s of one family can ever disagree about the fit —
a bold face is a different file, so "per family" may be too coarse and
"per `FontResource`" the right key. Measuring that belongs with the next
Linux run, where the fit matters and the fonts vary.

**Where it bites.** Only where the fit runs at all: the repaired simple
path, which since v2.174.0 means Linux and unknown builds. macOS and
Windows take WebKit's own path and never fit.

**Closed v2.186.0.** Re-keyed to the `FontResource` via the already-open
reflective `PGFont.getFontResource()`, falling back to the `WCFont` when the
resource cannot be obtained (both paths tested). Five sized fonts of one face now
pay the corpus fit ONCE; a second face pays a second. Asserted through a
`fitsComputed()` seam, never a clock — a timing assertion on a shared runner is
the v2.99.1 flake.

Both halves of "what remains deferred" turned out moot: the key was already
decided in v2.184.0, and the measurement question ("is the win worth it") is
answered by the argument — re-keying is a strict coarsening WITHIN one face, so
it cannot increase the fit count and cannot change an answer. Only the key's
identity could be wrong, and that is what the test pins.

### 101. ~~The Browser's first-load waits are per-open and serialized~~ — CLOSED v2.184.0

**What it is.** `WebBrowserTopComponent.loadWhenShaped` posts each first
load to a throughput-1 `RequestProcessor` and blocks up to 2,500 ms for
shaping to install. Closing and reopening the Browser tab posts another,
which cannot begin its own wait until the previous one's has elapsed. On
an update-center install whose conf lacks the attach flags — the case the
`ShapingAttach` helper exists for, with a 30 s leash — a second open can
show a blank pane for about five seconds, a third for seven and a half,
and the `browser == first` guard means the delay lands on exactly the page
the user is waiting for.

**Decided without the walk, because one of the two shapes is right under
any measurement.** Shaping installs ONCE per JVM — `install()` is
synchronized and memoizes its outcome — so the deadline belongs to the JVM
and not to the tab, and a reopen should not start a fresh budget. That is
true whatever a walk would have shown; the walk would only have told us how
long the stall was. `ShapingDeadline` is armed by the first open and every
later one waits the remainder.

**The fix contains a landmine the obvious version steps on.** Read from the
platform's own bytecode, `Task.waitFinished(0)` does NOT return at once: it
logs "infinite wait, again" and loops on an untimed `Object.wait()`. So
"wait whatever is left" with the remainder rounded down to zero would turn a
2.5-second bound into no bound at all, on the lane holding the user's first
page. Zero means DO NOT WAIT, the caller honours that literally, and a live
deadline never answers zero. The clock is an argument, so every rule is a
test rather than a sleep.

## Closed by v2.180.0 and v2.186.0 (the rack ecosystem release's walk debts)

### 103. ~~The v2.176–v2.179 edges never walked in the app~~ — CLOSED v2.186.0

**The three RACK edges are now walked** (v2.180.0, in the shipped 2.179.1 with
a throwaway userdir *and* a throwaway home):

- **Import from Clipboard, end to end** — a prepared rack on the clipboard,
  the manifest read, Mount pressed, TAIL and TEMPO mounted with `~/logs/app.log`
  expanded to the RECEIVER's home and TEMPO's CLOCK reading HALT. The at-rest
  law seen rather than asserted.
- **The final mount of a prepared file.** v2.179.0 recorded the replace
  question's Yes as out of the background tools' reach; that was true only of
  the KEYBOARD. `Yes` is exposed as an `AXButton` and `AXPress` presses it —
  the Web Pipeline preset replaced a non-empty rack. *The walk tools' limit was
  the input method, not the dialog.*
- **Remove from My Racks** — Share → Keep in My Racks wrote
  `~/.nmox/presets.d/shop.nmoxrack.json` with no `/Users/` in it and the entry
  appeared in the Presets menu as `shop · yours`; Remove is enabled only for a
  rack that is yours, confirms with the safe default, and removed the file
  while the project's own `.nmoxrack.json.bak` stayed untouched.

Zero SEVERE across the session, zero orphans.

**What is still owed.** The v2.178.0 re-aim refusal (proven behaviourally, and
its code re-read this night: every import door captures the aim and re-checks
it after the modal), and v2.177.0's ⌃Space translation-key completion and
⌘-click to the catalog line. Those two need a real completion popup and a real
modifier-click: a screen walk with full control, or a person.


**Closed v2.186.0, without the screen walk it was waiting for.** The entry said
these two "need a real completion popup and a real modifier-click: a screen walk
with full control, or a person." That framing was wrong, and this repo had
already answered it twice — v2.145.0 asked the platform's own
`BuildPopupMenuAction` to BUILD a context menu and read every row with no gesture
at all, and v2.58.1 found a real completion defect through a probe cluster rather
than by driving a popup. **A popup is a rendering of an answer the provider
already gave; the answer is the thing under test.**

So both are proven at the provider, through the platform's own query path:
`I18nCompletionQuery` constructs the real `CompletionResultSetImpl` and reads
back the items the popup would render, across all six call shapes with their
`en/common.json · "Home"` provenance; the jump drives the real
`HyperlinkProviderExt` to a resolved **file and line**.

**The open question came back negative, which is the good answer**: the i18n
provider does NOT mask out `COMPLETION_ALL` — it was written after v2.58.1 and
inherited the fix — and a census of all twelve `CompletionProvider` files
confirms `CompletionAllQueryGateTest`'s derived population has no blind spot. The
gap that WAS closed: under a wrong-line mutant the pre-existing
`I18nKeyHyperlinkTest` stayed green, because it tested the pure resolver rather
than what the click does with it.

**The ceiling is written into both test classes**: this proves the provider
ANSWERS — which items, what each says, which file and line — not that the popup
PAINTS or that a held modifier draws a link. No instrument here can deliver
either gesture (v1.291.0 measured three `MOUSE_MOVED` events for an entire
session). A stated ceiling is a decision; an unstated one is a gap.

## Closed by v2.186.0 (the rack debt night's last open measurement)


### 107. ~~A house typography decision the JDK bypasses, under French~~ — CLOSED v2.186.0 (all three platforms)

`docs/i18n/conventions.md` states it plainly: *"French typography uses a narrow
no-break space (U+202F)… We use U+00A0 everywhere because not every Swing font
carries U+202F, and a box in place of a space is worse than a space slightly
too wide."* That rule governs the text this product WRITES.

It does not govern the text the JDK writes. A bare numeric `{1}` in a
MessageFormat is grouped by the platform, and **French's group separator on
JDK 25.0.4.1 is U+202F** — measured, beside `.` for es/de/pt and `,` for
en/hi/tl/zh. So a written, measured house decision is bypassed below the bundle
layer, where no bundle gate can see it, and it reaches **every bare numeric
argument in the product under French**, not one key.

**Found by a translator**, on `RackService_patchTooLarge`, while checking what
its own rendering actually contained rather than reading it.

#### Measured 2026-09-18 — Linux closed, and the macOS clearance withdrawn

**The separator is the JDK's data, not the platform's.** The same probe run on
Linux (Zulu 25.0.4.1, `debian:trixie-slim`) and macOS (25.0.4.1) gives
byte-identical answers over the exact fifteen tags `UiLocale.SUPPORTED` ships:
`fr` is U+202F on both, `ru`/`uk`/`pl` are U+00A0 (the house character already),
`es`/`de`/`pt`/`id`/`vi` `.`, and `en`/`tl`/`zh`/`hi`/`he`/`ar` `,`. Two
controls worth keeping: **`fr-CA` is U+00A0, so this is `fr` specifically**, and
`ar` reports zero digit U+0030 with a comma on this JDK, so
`UiLocale.readableDigits` — written in v2.152.0 against a CLDR that gave `ar`
Arabic-Indic digits — is a correct no-op here rather than a live rewrite.

**`canDisplay` was the wrong question, and it is what cleared macOS above.** A
font can carry U+202F at **zero advance**: `canDisplay` returns true, nothing is
drawn, and no width is reserved, so the separator does not look wrong — it
disappears. The probe therefore asks three things per font: the glyph, the
advance in px, and the count of dark pixels painted at 64pt beside a control
character (U+E000) that is guaranteed absent.

**Linux: clean, on every realistic font set — CLOSED.** Measured in the
v2.173.0 probe-kit shape (`debian:trixie-slim` + `fontconfig` + a Zulu 25
copied in), on **both arm64 and amd64**, under a generated `fr_FR.UTF-8`
(`sun.jnu.encoding=UTF-8`, not the C-locale default that would have faked the
result):

- Every logical family (`Dialog`, `DialogInput`, `SansSerif`, `Serif`,
  `Monospaced`) paints U+202F with **no ink** and a real narrow advance
  (10.62px against a 16.64px space at 64pt, Noto), while the control paints a
  396px box. The rendered PNG shows `1 234 567` above a control line of
  `1□234□567` — two instruments agreeing.
- 188 of the 199 families in `fonts-noto-core` lack the glyph, but they are
  script fonts; `Monospaced`'s own slot 0 (`Noto Mono`) lacks it too and the
  composite answers from slot 1 (`Noto Sans Mono`). Fallback, working.
- The thin set is **not** a risk and the "no fonts at all" case is unreachable:
  `fontconfig-config` *depends* on one of `fonts-dejavu-core | fonts-liberation |
  fonts-croscore | fonts-freefont-otf | fonts-freefont-ttf | fonts-urw-base35 |
  fonts-texgyre`, so a Debian box with fontconfig always has one of those seven.
  DejaVu alone: all three families carry U+202F, no ink, 12.78px.
- **The desktop-font case is the one that could have bitten, and does not.**
  `FlatLaf.LinuxFontPolicy` (decompiled from the shipped `flatlaf-3.7.2.jar`)
  takes the session's own font name — GNOME `gnome.Gtk/FontName`, KDE
  `kdeglobals [General] font=` — and builds it with `FlatLaf.createCompositeFont`,
  which is `StyleContext.getDefaultStyleContext().getFont(…)`. That wrapping is
  load-bearing and was measured, not assumed: through `new Font(name,…)`
  **Open Sans, Cousine and Liberation Mono paint a 312–396px BOX** and Cantarell,
  Nimbus Sans and Nimbus Mono PS paint nothing at all; through `StyleContext`
  every one of them becomes a COMPOSITE, answers `canDisplay` true, paints no
  ink, and gets the 10.62px narrow advance. Cantarell — the GNOME default, which
  does not carry the glyph — renders `1 234 567` correctly, visibly narrower
  than the U+00A0 line.

**macOS: the chrome is right, the logical family is not.** On the shipped
bundled runtime (Azul 25.0.4+7, `/Applications/NMOX Studio.app/…/jre`) with
`FlatDarkLaf` installed as the conf does, every UI key — `defaultFont`,
`Label.font`, `TextField.font`, `Table.font`, `ToolTip.font`, `MenuItem.font`,
`TextArea.font` and a bare `new JLabel().getFont()` — resolves to **Helvetica
Neue**, whose U+202F advances 8.90px against a 17.79px space and paints no ink.
So the status line, dialogs and every platform label are correct. But
**`Dialog`, `SansSerif`, `Lucida Grande`, `Geneva` and `SF Pro` advance U+202F
by 0.00px**: painted, the French number is `1234567`, pixel-identical to the
same digits with no separator at all. `Serif` (12.81px), `Monospaced` and
`Menlo` (38.53px) are fine.

That is **latent, not shipped**: 22 sites in 8 files build their own
`new Font(Font.SANS_SERIF, …)` (`MainWindow`'s Welcome, `RackStyle`'s
faceplates, `IconForge`, `ProjectExplorerTopComponent`, four infra panels), and
none of them formats a number — infra's money strings go through
`Numbers.display` into confirm dialogs and text areas, which are FlatLaf chrome.
The hazard is that the next counted sentence painted on one of those surfaces
loses its separator under French, silently, on the maintainer's own platform.

**Windows: still unmeasured. Exactly what to measure, and where.** The
`windows-installer-check` workflow already runs `windows-latest` with
`setup-java` Zulu 25 and Maven, and its `webkit-text-path` job is the precedent
for a targeted measurement job, so a third job (or a step) could carry this: it
needs a JVM and no linked runtime. Three questions, in order:

1. `DecimalFormatSymbols.getInstance(Locale.FRANCE).getGroupingSeparator()` —
   expected U+202F, since this is CLDR data and both measured platforms agree,
   but it is one line and must not be inferred.
2. Segoe UI's own U+202F **advance and ink**, not `canDisplay` — macOS is the
   proof that the third answer differs from the first two.
3. **Whether the composite wrap happens at all.** On Windows FlatLaf reads
   `win.defaultGUI.font` / `win.messagebox.font` (decompiled) and builds it
   through the same `StyleContext.getFont`, but that method only wraps when
   `FontUtilities.fontSupportsDefaultEncoding` is false. On Linux, under
   `fr_FR.UTF-8`, it wrapped. A Windows `sun.jnu.encoding` follows the OS ANSI
   codepage, so the answer may differ — and if it does not wrap, Segoe UI's own
   metrics govern and question 2 becomes the whole answer.

Honest limit of that job: a `windows-latest` runner is Windows Server under an
en-US codepage. It can answer 1 and 2 faithfully (Segoe UI is a core OS font)
and 3 only for its own codepage; a non-Western Windows install stays unknown
until someone runs it there.

**The decision, stated rather than assumed.** Do **not** substitute U+00A0 for
the JDK's French separator. U+202F is correct French typography, the platform's
own data, and it is measured to render correctly in the font the product
actually paints chrome with on both measured platforms; overriding it would
make the product wrong on purpose everywhere in order to protect a surface that
is not currently exposed. The conventions file's rule stands where it was
written — for text the product AUTHORS — and its stated reason ("a box in place
of a space") turns out not to apply here anyway: on Linux fallback prevents the
box, and on macOS the failure mode is a vanishing space, not a box.

**What remains**: the Windows measurement above, and a decision on the macOS
`Font.SANS_SERIF` zero-width case the day a counted sentence is painted on a
self-painted surface — at which point the fix is that surface asking for the
chrome font rather than a logical family, not a rewrite of the separator.

**The law worth keeping**: *`canDisplay` answers whether a glyph exists, not
whether it has width or ink. A font can carry a space at zero advance, which
passes every existence check and deletes the character from the screen — ask
the pixels.*

**Repeating the probe**: build `debian:trixie-slim` with `fontconfig` plus the
font set under test, copy a Zulu 25 in from `azul/zulu-openjdk-debian:25`,
generate `fr_FR.UTF-8` (a slim image has no locales, and without one
`sun.jnu.encoding` is ASCII and the composite wrap fires for the wrong reason),
then for each font print the glyph code, the advance, and the dark-pixel count
at 64pt beside a U+E000 control — and render the real
`NumberFormat.getIntegerInstance(Locale.FRANCE).format(1234567)` to a PNG and
look at it.

**Closed v2.186.0, and not the way the entry expected.** It assumed the Windows
measurement needed a new job on `windows-installer-check`. It does not:
`build-and-test.yml` has run the full `mvn verify` on a `windows-latest` matrix
leg as a BLOCKING gate since v1.42.0. So the measurement is two plain JUnit tests
in `core`, which turns a one-off reading into a standing law checked on every PR,
on all three platforms.

`GroupSeparatorLedgerTest` derives its population from `UiLocale.SUPPORTED` and
pins every shipped locale's separator with both controls (`fr-CA` is U+00A0;
`ar`'s zero digit is U+0030, so `readableDigits` is a correct no-op on this JDK).
`NarrowNoBreakSpaceInkTest` asks the glyph, the ADVANCE and the INK — never
`canDisplay` — asserts that the chrome font reserves width and paints nothing,
and RECORDS the logical families rather than asserting them, because macOS's
0.00px `Dialog`/`SansSerif` is a known accepted state. It prints its whole report
on pass, fail and stand-down, so a green Windows leg still answers all three
questions.

**A measurement trap worth keeping**: an unresolvable font name silently becomes
`Dialog` — the zero-advance family — so a probe that does not check
`getFamily()` matches what it asked for measures the logical fallback and calls
it chrome.

The latent macOS hazard the entry left standing is now gated:
`PaintedNumberSeparatorGateTest` derives the 22 sites in 8 files that build their
own zero-advance logical family and refuses a grouped number painted on one.
`Numbers.display` is not banned — it formats `"%.Nf"`, which takes no grouping
separator — and **the gate READS that fact from `Numbers.java` instead of
restating it**, so the day `display` grows a grouping flag its callers on those
surfaces fail by name.

## Closed by v2.181.0

**Decided (v2.184.0): the Windows half rides `windows-installer-check`.**
That job already links a real bundled runtime the way the release does, on
a real Windows runner — it is the only lane in this project that can answer
the question at all, and the probe is a font-metric read, not a UI test. The
macOS half was measured and its clearance withdrawn; Linux measured clean.
What is owed is the probe itself in that job, not another decision.


### 108. An RTL value's PLACEHOLDER can strand a leading dot or a trailing slash — CLOSED

A dotfile that arrives as `{0}` after an RTL word lays out as
`[nmoxrack.json][.]` — the leading dot detaches and draws AFTER the name. That
is the defect `docs/i18n/conventions.md` already describes for literals, and
the gate that holds it, `NativeTypographyGateTest`, matches a **literal** dot
followed by a Latin letter. A dotfile arriving as an argument has no dot in the
bundle value, so there is nothing to match. **Ledger 88's shape, one layer
over**: the defect enters through an argument, below where the gate looks.

**Found by a translator**, in its own committed work from the same night, by
laying the strings out through `java.text.Bidi` rather than reading them.

**Measured** (v2.180.0, all eleven modules' `Bundle_ar` / `Bundle_he`, with
`\uXXXX` decoded first because the branding overlays are escaped):

- 2,871 placeholders in ar/he values; **1,841 unguarded with an RTL character
  last before them**, collapsing to **927 distinct (package, key, argument)
  pairs** — ui 401, rack 267, editor 154, web3/infra 127 each, apiclient 119,
  dbstudio 94, branding 58, tools 15, project 10.
- Of those 927: **115 can lead with a neutral** (a dotfile, path, glob, flag,
  `.bak`) — the real risk; 128 carry a number and 181 a bare Latin word, both
  measured correct bare; **503 are undetermined**, because the call site is
  `ex.getMessage()` or `node.label` and **only 1 of the 927 keys carries the
  `# {0} - …` comment** the house convention provides for exactly this.
- Twelve real shipped values laid out with real arguments: **six read wrongly
  today**, including `ApiClientTopComponent_couldNotRead`,
  `DbStudioTopComponent_reloaded` and `RackTopComponent_noPatchInProject`.

**The defect tracks the runtime VALUE, not the key.** `ApiClientTopComponent_savedFile`
is correct for `response.json` and breaks the moment a user saves a dotfile, so
the population cannot be split into safe and unsafe keys by inspection.

**Two things the measurement settles.** First, the guard is **provably inert
where it is unnecessary**: for a number, RTL text, a bare Latin word and a Latin
phrase, the rendering with a guard is byte-identical to bare — because the guard
is a zero-width strong-L character and only changes behaviour for a neutral
adjacent to the placeholder's content. Second, **LRM alone is not enough**: it
fixes a leading neutral and cannot fix a trailing one, so
`http://localhost:8080/` renders with its slash at the front either way. A sweep
must ISOLATE the placeholder — `LRI…PDI` (U+2066…U+2069) over `LRE…PDF`, because
isolates do not leak into neighbouring text.

**The decision, executed in v2.181.0.** Take the strong form: require every
RTL-context placeholder to be isolated, with the population DERIVED (walk back
from each `{n}` to the first strong directional character; flag it if that
character is RTL). No literal matching and no hand-kept list, and a new
translated value with an unguarded placeholder fails on the commit that adds it.
The ledger shape (classify each of the 927) is the right answer when guarding
has a cost — here it has none, and it would ask 503 questions nobody can
currently answer.

**WALKED in Hebrew (the ceiling the Bidi proof could not close).** The
assembled build with the sweep, booted `--locale he` on a project with a
corrupt patch: the refusal painted with `.nmoxrack.json` reading dot-first,
photographed. The control settles that the guard is what does it — with the
isolates stripped, the leading dot **detaches and lands at the opposite end of
the sentence** (`…הראק ריק: .`) and the name renders bare. *A containment
assertion could not tell these apart* — `contains(".nmoxrack.json")` passes on
the broken control, because `.nmoxrack.json.bak` contains that substring; only
reading the visual order did. Zero SEVERE, zero orphans.

**Shipped in v2.181.0**: 2,198 arguments isolated across 1,583 values in 120
files, with `RtlPlaceholderIsolationGateTest` deriving both populations. The
639 at value start were measured rather than swept blind — **194 are genuinely
safe and deliberately left bare**, because their values contain no RTL letter
at all, so Swing draws them in logical order and an isolate would assert
something untrue about the value; the other 435 are guarded, and 6 are
`{0,choice,…}` at value start, skipped because a left-to-right isolate around a
choice element would lay its RTL branch text out backwards.

**Where the sweep would have been wrong** — it met resistance nine times and
only THREE were caught by a test: it wrote a literal newline into a properties
file, it nested `⁦{1}⁦⁩{2}⁩` for adjacent arguments which **MessageFormat parses
happily**, its own first gate cut was vacuous (it read the context beside an
element rather than before its guard, so `checked` was 0), it would have put an
invisible character inside a CSS declaration, and 108 of 360 files are
escaped-ASCII where a raw U+2066 would have shipped mojibake. *A large
mechanical change that meets no resistance anywhere is one nobody checked.*

## Closed by v2.182.0 and CORRECTED by v2.183.0

### 109. The aim's refusal vanished before it could be read — CLOSED (v2.182.0 was WRONG; v2.183.0 closes it)

Ledger 104 gave the aim-time patch refusal a voice on the status line, and the
Hebrew walk of the RTL sweep found what that voice is worth: **the sentence is
painted and then replaced within roughly two seconds**, while the project is
still opening. It was captured only by firing the aim and photographing without
waiting; every capture taken four seconds later found the strip empty.

**Diagnosed (v2.181.0), and the suspect recorded above was wrong.** Nothing
overwrites it. `org.netbeans.core.NbStatusDisplayer.setStatusText(String)`
reads, in bytecode:

```
add(text, 0);
MessageImpl.clear(SURVIVING_TIME);
```

with `SURVIVING_TIME = Integer.getInteger("org.openide.awt.StatusDisplayer.DISPLAY_TIME", 5000)`.
**A plain status message is designed to clear itself after five seconds.** The
entry above guessed at the platform's project-open progress; that guess is
withdrawn — no other writer is involved, and none of our own `setStatusText`
callers sits on an aim path (checked: palette, CI export, Docker, Agent Port,
the two search providers and KVASIR are all user gestures).

**Why five seconds is still not enough here.** The clock starts when the rack
loads the patch, which is *during* project opening — before the window has
settled and while the reader's attention is anywhere but the status strip. The
sentence is correct and translated fifteen times and can still be missed
entirely.

**What closed it.** The `Message` overload, with both numbers made laws:
importance **100** (above zero, which is the entire point — zero is the branch
that schedules its own deletion — and well under the platform's own 700–1000
family, so an editor annotation or a find still wins the strip) and a linger of
**15 s**, bounded by the returned `Message`'s own `clear`.

**The cost is written where it is paid.** An importance above zero outranks
every plain `setStatusText`, so while the refusal shows it also HIDES ordinary
status text. During a project open that traffic is progress noise and a
sentence explaining an empty rack matters more — but that is exactly why the
linger is bounded rather than "until something replaces it". Three mutants by
name: `theImportanceIsAboveZero`, `theLingerIsLongerThanTheDefaultAndStillBounded`,
`theHelperKeepsTheMessageAliveAndBoundsIt`.

**v2.182.0 CLAIMED fifteen seconds and did not deliver them.** It was walked
AFTER the tag, and in a clean instance with a single refusal the sentence was
present at T+2 s and gone by T+9 s — twice. The three tests asserted
`PATCH_REFUSAL_IMPORTANCE == 100` and `PATCH_REFUSAL_LINGER_MS == 15_000`; not
one of them asserted the sentence was still on screen. *A check that answers an
easier question passes* — the lesson this arc had already written down, shipped
as an instance of itself.

**What was actually wrong (v2.183.0).** `NbStatusDisplayer` keeps only a
`WeakReference` to each message **and** gives `MessageImpl` a `finalize()` that
calls `run()`, which removes it from the strip. A message nobody holds dies at
the first garbage collection — and a project opening allocates heavily, which
is precisely when this one is set. v2.182.0 assigned the returned `Message` to
a **local** and reasoned that `clear(ms)`'s pending RequestProcessor task would
keep it alive. It did not check. The same commit's javadoc named the hazard
("the list holds only a WeakReference, so a message set and dropped can vanish
at a GC") and the code walked past it.

The `Message` is held in a field now. Re-walked on the same fixture: **still on
screen at T+9 s**, where it had died, and gone after its bound. The new test
pins the FIELD, and reverting to v2.182.0's local kills it by name
(`theMessageIsHeldSoAGarbageCollectionCannotTakeIt`).

**Not done, and honest about it:** the rack's own placard is still the better
home for this sentence — it is where the reader is already looking when they
wonder why the rack is empty. This is the fix that could ship today, not the
one that ends the question.

**Why it matters.** This is the refusal the user did not ask for: it explains
an empty rack they are about to wonder about. A sentence that is correct,
translated into fifteen languages and gone before it can be read is the
v2.85.0 class (*a copy notice ERASED the response verdict*), one surface over.

**What would close it**: identify what writes the status line after the aim,
then give this sentence a surface that outlives it — the rack's own placard is
the obvious candidate, since an empty rack is exactly what the sentence is
about, and the reader is looking at it.

**What is already proven and is NOT in question:** the sentence's content
(`PatchNotLoadedSpeaksTest`), that the catch reaches the status line at all,
and that it renders correctly in Hebrew — photographed live.

## Closed by v2.180.0 (the rack debt night)

### 106. A refusal's REASON reaches a translated build in English — CLOSED

`RackService.patchNotLoadedText` renders ledger 104's new sentence in the
reader's language and then splices `failure.getMessage()` into it as `{1}` —
and that message comes from `RackIO` in English ("Corrupt rack patch X (kept
as .bak): …", "…is 9216 KiB, over the 8 MiB cap — not read"), or, when the
failure carries no message, from `getClass().getSimpleName()`, which is a Java
class name. So a Vietnamese reader gets a Vietnamese sentence ending in
English or in `JSONException`.

**And the file name is printed twice.** `{0}` is `patch.getName()`, while the
engine's message already opens with `"Corrupt rack patch " + file.getName()`,
so the sentence renders `…so the rack is empty: .nmoxrack.json — Corrupt rack
patch .nmoxrack.json (kept as .bak): …`. That half is wrong in ENGLISH too,
and it was invisible to `PatchNotLoadedSpeaksTest` because the test asserts
`contains(".nmoxrack.json")` on a hand-written message — which passes whether
the name appears once or twice. *An assertion that a string is present cannot
see that it is present twice.*

**Found by the translators**, three times, independently: the European group,
the no-plural group and the Slavic/RTL group each reported the English splice
without seeing the others' work, and the Slavic group found the duplication
on top of it by rendering the sentence with a real exception rather than
reading it. No gate can
see it — every bundle is complete, the parity gate is green, and the English
enters BELOW the bundles as an argument. That is exactly ledger 88's shape
(`LiveRuns.since()` splicing the word "since"), one layer down.

**What closed it.** The house answer, taken the same night the translators
found it: **an argument is data** (v2.100.0). `RackIO`'s two refusals a reader
can act on became typed outcomes carrying their facts — `PatchTooLargeException`
the measured size, a new `CorruptPatchException` the name the bytes were kept
under — and `RackService` renders each in the reader's own key. Both halves of
the defect close at once: the file is named once because the sentence names it,
and no English crosses because there is none left to cross.

**The parser's own complaint stays English, in the log.** org.json's
"Expected a ',' or ']' at 97" is a parser's technical text, not prose this
product can translate, so it goes where it already went — the WARNING — and
never reaches the status line. An unexpected failure still falls back to the
engine's sentence, which is English and says so here.

**Scope note, still open:** the same question should be asked of every other
place a caught exception's message is SHOWN to a user rather than logged. That
population was not measured, and this entry is the reason to measure it.

### 102. A plugin device's KNOB also runs plugin code when a rack mounts — CLOSED

**What it was.** Restoring a saved value fired the plugin's `onChange`
Runnable, which holds `DeviceServices` and can reach `exec`. v2.179.0 answered
the SWITCH half by switching every plugin toggle off on import; a knob has no
"off", so the mount ran plugin code for a value the user never touched.

**What closed it.** The answer the deferral asked for, taken from the other
end: the callbacks are not suppressed by the caller, the device knows it is
restoring. `RackDevice.applyState` raises a flag for the length of the restore
and `ExtensionDevice` wraps every plugin `onChange` so it does not run while it
is set — for knobs and toggles alike. **A restore is not a gesture.** The
plugin is then told once, after the whole state is in, through a new
`DeviceLogic.onStateRestored(services)` default method: additive to the frozen
SPI, and the hook `onAttached` could never be (a device is racked BEFORE its
state is applied, so `onAttached` fires with the defaults still in place).
`ExtensionSelfStartTest` proves all three legs — the callback does not run
during a restore, the plugin is told after it, and an ordinary press still
runs it. Its predecessor had been written to PROVE the hole existed, as the
justification for switching every plugin toggle off; it is inverted now, and
the switch-off stays as the second defence for a plugin that reaches `exec`
from something other than a knob callback.

### 104. An oversize or corrupt rack patch on AIM only logs — CLOSED

**What it was.** `RackService.autoLoadPatch` caught the load failure and wrote
a WARNING; the reader got an empty rack and no sentence. Every other refusal
in the product speaks — this was the one the user did not ask for.

**What closed it.** It says so on the status line, where the rest of the aim
already speaks, naming the file and carrying the engine's own reason (which
already distinguishes "kept as .bak" from "over the 8 MiB cap — not read").
The surface the deferral wanted a running app to choose turned out to be the
one already in use for everything else about aiming. `PatchNotLoadedSpeaksTest`
holds the sentence and reads `autoLoadPatch`'s catch to prove the call site
exists — a message with green tests and no call site is a payload without a
gate.

### 105. "1 devices, 1 cables": three rack sentences count without a plural — CLOSED

**What it was.** `importSummary` (v2.176.0), `leavingSummary` and
`leavingNothing` (v2.179.0) rendered `{0} devices, {1} cables` at every count.
Found by the translators, who had quietly dodged it in Polish, Russian and
Ukrainian by writing the count after a label.

**What closed it.** All three take `{n,choice,…}` branches, and the at-rest
clause — which used to be fused into `importSummary` as "{2} were saved armed
or running", a sentence about nothing on every rack that had none — is its own
key, said only when there is one. The ChoiceFormat trap bit during the fix and
is worth keeping: a value BELOW the first limit takes the FIRST branch, so
`{0,choice,1#…|1<…}` renders 0 as the singular; every count carries an explicit
`0#`. `RackCountsReadRightTest` reads each sentence at 0, 1 and many.

**What it uncovered.** `PluralCopyGateTest` has held the plural law since
v2.85.0 and could not see this: its population is a hand-kept list of five
nouns, matched only in Java string concatenation, so a count living inside a
message value was invisible to it — the v2.147.0 shape, a gate whose population
is a SHAPE rather than the thing it is about. A census of every English message
value across the ten modules found 33 further candidates behind 41 verbs
wrongly matched (`{0} installs into the project`) and 14 keys correctly using
the house's own `…One`/`…Many` idiom.


## Closed by v2.165.0 (the Browser shapes complex scripts)

### 99. The in-app Browser paints Arabic letters unjoined — CLOSED

**What it was.** Recorded by v2.164.0 as seen but unexplained. Measured
since: OpenJFX's WebKit never shapes complex scripts, on any release —
the same page paints unjoined Arabic and unreordered Devanagari on 17,
21, 24 and 26. The port paints every run through WebKit's simple text
path, whose shaping hook (`Font::applyTransforms`) is a no-op outside
CoreText and HarfBuzz builds, and 25 onward also hard-code
`s_codePath = Simple` for the Java platform. A glyph-logging agent on
`WCGraphicsPrismContext.drawString` proved it: the painted glyphs were
each character's plain cmap glyph, reversed into visual order, while
the same font shaped correctly in every JavaFX control. Page CSS
(`text-rendering`, `font-feature-settings`, a system Arabic font)
changes nothing.

**How it closed** (David chose the in-memory agent over shipping a
patched `javafx.web` or reporting upstream only). The Browser attaches
its own agent to its own process on first open (`ComplexTextShaping`,
enabled by `-Djdk.attach.allowAttachSelf=true` and
`-XX:+EnableDynamicAgentLoading` in the launcher conf), opens the JavaFX
packages it reads, defines a one-method hook inside `javafx.web`, and
rewrites that one draw method with ASM so it hands its glyphs to
`ComplexScripts`: runs of Arabic or Indic glyphs are mapped back to text,
shaped by JavaFX's own text layout, and written back in place. No
OpenJFX file changes. Every refusal leaves the page painting as before.

**The trade-off v2.165.0 left, and how v2.166.0 narrowed it.** WebKit
measured the unshaped glyphs, so a shaped word was narrower than its box and
an Arabic phrase inside an English line sat apart from its neighbours.
v2.166.0 rewrites `WCFontImpl.getGlyphWidth` as well, so WebKit measures
marks at zero, Arabic letters a quarter of the way from medial to final form and Indic
letters at 0.72 of their width (both measured on running text), and each
shaped run keeps its reading edge. What remains is a few pixels per word at
the run's far end; exact widths need context WebKit does not pass, which
only a native change could give.

**What checking Persian and Urdu found (v2.167.0).** Three defects the
Arabic and Hindi pictures had not exercised. Digits were reversed with the
letters around them (`١٢٣` painted `٣٢١`), so only letters and marks are shaped
now. Vowelled text lost its marks' vertical offsets and its advances were read
in logical order; the installer also rewrites the advances-only glyph-run build
in `TextUtilities.createGlyphList`, so a shaped run is drawn from x/y positions
and may take more glyphs than it had characters, and JavaFX's upside-down y on
macOS is detected from where a kasra lands. Nastaliq, which that made
renderable, estimates its widths at half way from medial to final and misses by
9-10px a word; that looseness is the recorded remainder.

**Kurdish, Pashto, Sindhi and Uyghur (v2.168.0).** Letter forms, marks and
digits were already right; long right-to-left lines were not. The estimate's
per-word error added up across a paint call and went to its far edge, eating
the space before the next run. Now, in right-to-left runs, spaces between words absorb up to
half their width of it and spaces at the kept edge stay as measured (Indic
runs keep the old rule, their per-word miss being about a whole space); what remains still
reaches the far edge, where a comma or phrase can sit close to its neighbour on
a line of many words measured short.

**The other Indic scripts (v2.169.0).** Bengali, Gurmukhi, Gujarati, Oriya,
Tamil, Telugu, Kannada and Malayalam shape correctly. Their widths had all been
measured at Hindi's 0.72, itself calibrated against a measure the bridge does
not take; each script now has a share measured in the Browser from WebKit's own
fonts, and an Indic run's spaces take on up to half, give up at most a quarter,
of what its words miss. Remainder: per-word misses of 5-11px, so conjunct-heavy
phrases sit a little apart and Kannada/Malayalam phrases a little close.
Vietnamese written with combining accents (NFD) placed its accents beside the
letters in the unpatched WebView too; v2.170.0 shapes a letter and its combining
marks into the precomposed glyph and measures the marks at zero, so
decomposed Vietnamese, French, German, Greek and Cyrillic read as precomposed.

**More scripts, the update-center gap, the cost (v2.171.0).** Sinhala, Thai,
Tibetan, Myanmar and Khmer painted unshaped too and are shaped now, each at a
share measured in the Browser; Syriac, Thaana and N'Ko shape right to left, and
Syriac and N'Ko join only since a paint holding a new composite-font fallback
slot rebuilds the glyph table once for that slot. Armenian, Georgian and
Ethiopic were already right. CLOSED: installs updated in-app, whose conf lacks
the attach flags, attach from a helper process (the IDE's runtime and its own
`ShapingAttach`), proven on a runtime started without them. The repaint p90 of
21-30ms (11-12 unshaped) is back to about 12 with a per-font laid-out-word
cache. Remainder: Lao is excluded because JavaFX's own text drifts it; Khmer
words miss by 12px on average.

**WebKit's own path (v2.172.0).** The estimates were near their ceiling (an
offline fit of per-font parameters moved nothing; a virama term took Hindi from
4.1 to 3.5px). OpenJFX's WebKit has a working complex path that the Java port
starts switched off (`FontCascade.cpp`: `#if PLATFORM(JAVA) s_codePath =
CodePath::Simple`). For the bundled OpenJFX 26.0.2 libraries on macOS arm64 and
Windows x64, identified by SHA-256 and tied to the release lanes' jmods pins,
`WebKitTextPath` calls WebKit's own setter with `Auto` (guarded by reading
`Simple` first), and `getTextRuns` strips the length the native glue appends
(`makeString(characters, characters.size())`). Remainder: Linux keeps the
repaired simple path (its library reads no such byte near its font code); macOS
x64 is not in the table (not a shipped installer); every JavaFX bump must
re-measure the offsets, which the gate enforces. Both OpenJFX defects belong
upstream.

**Windows (v2.173.0).** v2.172.0's Windows entry named `bin/jfxwebkit.dll`; a
linked image carries `bin/javafx/jfxwebkit.dll` (jlink drops the jmod entry's
first segment, then files a `.dll` under `bin`), so Windows silently kept the
repaired path. Fixed, with a missing library now logged, the setter's target byte
re-derived from the shipped DLL, and `WebKitTextPathLiveTest` run on a linked
runtime by a `windows-installer-check` job (macOS runs it on the installed app's
runtime).

**Linux (v2.173.0).** Checked in a container on the release's Linux WebKit:
no data switch exists (each candidate byte flipped, no change), so the repaired
path stays. Its constants, measured on macOS fonts, were far off on Linux's
(Tamil 20px a word); `FontFit` fits the share or Arabic blend to each font from
a word corpus, used only when clearly better (held-out mean 5.99 → 4.46px).
Remainder: the per-word spread a context-free estimate cannot remove (4–8px);
JavaFX's own Linux layout draws Tamil `பொ` with a dotted circle and ZWNJ as a
box (font/JavaFX, not the Browser).

## Closed by v2.187.0 (the containment sweep finished)

### 117. ~~Three more containment guards, named by the derivation that unified the other four~~ — CLOSED v2.187.0

Closing ledger 111 put the four named guards in one home and then **derived** a
census across all ten modules — comment-stripped source carrying the resolve
shape, plus every caller of the guard. The derivation immediately found three
copies nobody had named, which is the point of deriving rather than listing:

- **`McpSubscriptions`** — the Agent Port's subscribe path.
- **`UserTemplates.generate`** — **lexical only**: the v1.293.0 path law refuses
  absolute, `..`, backslash and drive-letter spellings by disqualifying the whole
  template, but a **symlinked segment passes it**, and this is a WRITE path.
- **`SymbolIndexProvider`** — editor.

`ContainmentLedgerTest` classifies all three by name with the walk each still
needs, so none can hide and a new one fails the build (census 13: 9 routed, 4
classified, floored at 8 so an empty derivation cannot pass; the classified half
is an exact-set match, so a stale entry fails too). `GitFacts` is classified as a
genuine distinction rather than a miss — ledger 43 asks "inside *a* `.git` dir",
which for a worktree is deliberately outside the repo root.

**Deferred deliberately, and for ledger 111's own stated reason**: tightening a
containment guard changes what is refused, each of these refusals is a spoken
message on a different surface, and each needs its own refusal walk. Folding three
more into a release that already lands eighteen units would be the scope mistake
111 was held back to avoid. `UserTemplates` is the one to do first — it writes.

**Closed one at a time (v2.187.0), which is what the deferral above asked for.**
`McpSubscriptions` is routed: it was the closest of the three to the policy and
loses nothing, because its refusals are one protocol sentence (the Agent Port's
`-32002`, *not found*) that the guard's single null cannot blur. The existence
test it needs stays at the CALL SITE rather than being inherited — `Containment`
deliberately answers for a leaf that does not exist yet, since the write paths
need that. `UserTemplates.generate` is routed too, and **the escape the bullet
above names did not reproduce through it**: the lexical guard really does accept
a symlinked segment, but never-clobber proves the target empty two lines earlier
and a planted link IS an entry, so the write is refused before the guard is
reached — defence in depth, not a demonstrated escape. The same lexical spelling
had caused two REAL defects nobody had named, both reproduced: it left the BASE
un-normalized while the target was normalized, so a location carrying a `..`
segment (the wizard's location field is free text) refused EVERY file of a
perfectly ordinary template as an escape; and a path resolving to the project
root reached `writeString` and came back as the operating system's raw *"Is a
directory"*, a refusal naming no template. `SymbolIndexProvider` is **not** swept
and now says so permanently, measured: the Agent Port hands agents ABSOLUTE paths
of its own — `EditorState` reports every open tab as `getAbsolutePath()`, so
`ide_context.activeFile` is absolute and *"outline what I am editing"* is the
next call an agent makes — and `Containment` JOINS an absolute-looking name under
the root by its own deliberate, test-pinned policy, so routing would answer *"no
such file"* about a path this same server had just emitted. `DebugEntries` went
the joining way in v2.186.0 because npm's spec makes `main` relative; that reason
does not reach a string the product itself wrote. So the census sentence above —
*"the walk each still needs"* — is spent: two are counted by their CALL, and the
third carries that reason inside the gate, where a person can disagree with it.

**CORRECTION (v2.187.0): what v2.186.0 recorded here as a "ceiling" was a LIVE
WRITE-ESCAPE, and the entry was wrong in both directions.** It is left in place
below, struck through, because a wrong claim is worth more corrected than
deleted.

The hazard as recorded — a link followed out *"if that target were later
created"* — is the case the guard **already handled**: once the target exists
the link resolves, canonicalization walks it, and the verdict flips from
contained to null. The real hazard was the reverse and **immediate**. A dangling
link as the **final** component was answered *contained*, and `Files.writeString`
opens with `CREATE`, **which follows a dangling link and creates its target**.
Measured on the shipped guard: with `proj/Dockerfile -> OUTSIDE/pwned.txt`
(absent), the guard said contained and the write created `OUTSIDE/pwned.txt`
holding the caller's bytes. No waiting, and the link never resolved.

So `DockerRecipes` — the one caller that writes into a directory it did not
create, which a `git clone` fills — could escape, and v2.186.0 filed it as *"not
believed exploitable today"*. That judgement was mine and it was wrong; the
measurement is what corrected it.

**The fix is not the rule v2.186.0 proposed, either.** "Refuse when the
canonicalized ancestor is itself still a symlink" also refuses a dangling link
pointing back **inside** the root — which is contained, and which `Containment`'s
own symlink policy promises to accept. What shipped reads the link's RECORDED
target (`readSymbolicLink`), resolves it against the link's own directory, and
re-asks containment of that: chains are followed under a bound, and a cycle
exhausts the bound and refuses.

**One outcome class changed, and only one.** Every READ caller
(`SiteServer`, `PageSourceResolver`, `DebugEntries`, `NgSchematic`,
`Checkpoints`, `CheckDisclosure`) asks `isFile()`/`isDirectory()` within a line
or two, and that FOLLOWS links — so an escaping dangling link was already
refused there, one step later. `UserTemplates` is unchanged because
never-clobber proves the target empty first and a link is an entry. The rule
changes exactly one thing: **a write that would have escaped now refuses.**

~~**A second containment ceiling, recorded rather than taken (v2.186.0).** A path
through a **broken** symlink is judged on its spelling, because no platform can
resolve one. So a link inside the root pointing at a target that does not exist
YET would be followed out if that target were later created. This is
pre-existing and byte-identical before and after this release's guard rewrite —
verified by a 30-input differential probe. Closing it means refusing when the
canonicalized ancestor is ITSELF still a symlink, which is arguably just the
stated symlink policy made true; it is left undone because it is a NEW refusal
rule, it cannot be tested on Windows from this bench, and adding one to a
release that already lands twenty units is the scope mistake ledger 111 was held
back to avoid.~~

**The Windows half of this family was a live hole, found by CI (v2.186.0).**
`Containment.resolve` canonicalized the WHOLE target path, and on Windows
canonicalization cannot resolve a symlinked ancestor when the FINAL component
does not exist — so a not-yet-existing file behind a link inside the root came
back spelled as-is and passed containment. On POSIX the same call resolves it,
which is why every local run was green: *the platform was doing the work, not
the code.* That mattered most on the WRITE paths, where an absent leaf is the
normal case rather than an edge — `DockerRecipes`, `LearningSpace` and
`DocsStaging` all name files that do not exist yet. The guard now canonicalizes
the deepest EXISTING ancestor and re-appends the tail, so the answer no longer
depends on the leaf existing, and the test states exactly that as a
platform-independent property. The helper that created the symlinks was also
returning early instead of skipping, so the whole test body vanished under a
green tick on any platform that refuses them — the same defect as 115b's
`argvPinned`, in the test written to catch this one.

**CORRECTION (v2.187.0): the verdict below acquitted a FAMILY on one MEMBER's
measurement, and two escapes have since been found in the family it acquitted.**
It is struck through rather than deleted, for the same reason as the ceiling
above.

The measurement inside it is sound and still stands, exactly as far as it goes:
restoring the old `DebugEntries` code left the escape assertion PASSING, because
that guard canonicalised before deciding. That is a fact about `DebugEntries`.
What does not stand is the scope — *"what these leave … not an escape"* is a
verdict over every site in this entry, resting on one site answering one
question. It was already contradicted by the paragraph directly above it, in the
same release: the Windows half is called **a live hole** there, and what passed
containment through it was a not-yet-existing file behind a symlinked ancestor —
which is precisely the shape `DockerRecipes` writes. A file created outside the
project is an escape, not a TOCTOU-shaped residue. The dangling-link write
corrected at the top of this entry is the second one, on POSIX as well, reachable
with no waiting and no link ever resolving.

The honest verdict for this family is therefore **two escapes, both measured,
both now refused** — and the sentence below is its own best evidence for the
lesson: *"not believed" is a belief, and a belief written where a measurement
belongs will be read as a measurement.* A verdict must not outrun the scope of
what was measured; where it cannot cover the family, name the member that was
measured and stop there.

~~**Not believed exploitable today**, and that is measured for the one that
mattered most rather than assumed: restoring the old `DebugEntries` code, the
escape assertion still PASSED, because that guard canonicalised before deciding.
What these leave is the same TOCTOU-shaped residue — the check and the use naming
different paths — not an escape.~~

## Upstream — filed, not ours (added v2.156.0, the multi-session walk)

Carried here so a walk that meets it knows what it is looking at, not because
it is work this project owes. **Decided v2.186.0**: a patched copy of a
platform module would be a second home for platform code, which this repo
refuses on principle, so the only lever is the issue — and it is filed. The
entry stays until the fix lands upstream and the platform bump picks it up.

### 98. The platform's Breakpoints window throws on every repaint while a DAP session is stopped — UPSTREAM, filed
Found by the v2.156.0 walk, read from bytecode, not ours: in the RELEASE310
lsp-client, `breakpoints/BreakpointModel.getIconBase` (line 90) calls
`DAPStackTraceAnnotationHolder.contains(debugger.getCurrentLine(), bp.getLine())`
— a `Line` as the first argument — and `contains(Object, Line)` (line 102)
opens with `checkcast [Lorg/openide/text/Annotatable;`. Two platform classes
disagree about one parameter's type, so whenever the Breakpoints window paints
a breakpoint row while ANY DAP session is stopped (`getCurrentLine()` non-null),
the renderer throws `ClassCastException: EditorSupportLineSet$SupportLine
cannot be cast to [Lorg.openide.text.Annotatable;`, logged SEVERE once per
repaint (eight in a two-minute walk). Visible cost: the "breakpoint hit" icon
never paints (the plain one does — the rows list fine) and the status bar's
error badge lights. One session or ten, the same; unrelated to the proxy.
**Deferred**: the fix is a one-line change in NetBeans (`contains` should
accept the `Line`, or the model should pass the holder's annotations), which
belongs upstream; a patched copy of a platform module would be a second home
for platform code. Worth an Apache NetBeans issue with the two line numbers
above. Until then the walk law: an error badge that appears the moment the
Breakpoints window opens on a stopped session is this, not the debugger.
**Filed upstream 2026-09-15** as
[apache/netbeans#9621](https://github.com/apache/netbeans/issues/9621), in
their bug-report form with the two line numbers and the stack head.

## Open — deferred deliberately, with reasons (added v2.19.4, the deps split)

### 97. ~~The learning-space catalog speaks English in every translated build~~ — CLOSED v2.133.0

Photographed during the German walk that closed the dialog-button class: the
New Learning Space picker's chrome is German (`Lernen durch Tun — wählen Sie
eine Sprache, ein Framework oder eine Bibliothek:`, `benötigt npx — ✓
gefunden`, `Abbrechen`), and every one of the 93 entries inside it is
English — `Your First Web Page`, "Never written HTML before? Start here —
build a real page, see it in the browser, and change it live."

This is not the same class as the chrome. Those were keys in bundles; these
are **authored prose in a data file** (`learn-catalog.json`, plus whatever a
user drops in `~/.nmox/learn-catalog.d`), and each blurb is a sentence
someone wrote to persuade a learner. 93 titles + 93 blurbs × twelve
languages is 2,232 pieces of persuasion — a translator pass, not an author
with a dictionary (the ledger-89 rule, which was right about 72 values and
is more right about 2,232).

Two further reasons to decide rather than default:

- **The drop-in half has no home for a translation.** A user's own catalog
  file carries one `title` and one `blurb`. Giving the built-ins twelve
  languages while a drop-in has one means the picker mixes languages on the
  same list, which reads worse than a consistent English list — the same
  argument that held two thirds of a toolbar back in v2.101.1 until v2.102.0
  could do all of it.
- **The tutorials behind them are longer than the catalog.** Each space
  generates a `TUTORIAL.md`. Translating the picker and not the tutorial
  sends a German speaker from German prose into an English document at the
  exact moment they start learning.

The honest shape, when it is taken: a `blurb.<lang>` sibling in the catalog
schema so built-ins and drop-ins are described by the same mechanism, a
parity gate deriving its population from the catalog itself, and the
tutorials in the same pass or not at all.

**v2.133.0 took that shape, and re-decided the last clause.** The schema
gained `name.<lang>`, `blurb.<lang>` and `tutorial.<lang>` siblings — one
mechanism for built-ins and drop-ins alike, a language added by adding a
line, a catalogue written before this release parsing unchanged. 93 blurbs
× twelve languages are written (1,116 values), plus the three names that
were prose rather than technology names, plus the picker's grouping words
(four categories and thirty prose families × twelve, in a bundle beside the
picker that renders them — the v2.132.0 placement law; the fourteen
families that are NAMES carry no key and survive translation intact).
`LearningCatalogSpeaksTest` derives its population from the catalogue the
assembled cluster ships, so a space added tomorrow — or a fourteenth
language — fails the build until it speaks.

The tutorials did not ride along, and that is now a written decision rather
than a reason to hold everything. Measured: 130,863 characters of authored
English across the 93 spaces, about 1.57 million characters in twelve
languages. The argument that stopped v2.101.1 shipping two thirds of a
toolbar does not transfer, because the two halves are read at different
moments: the picker is a catalogue you scan to choose, the tutorial is a
document you open after choosing. A German reader now chooses in German and
then opens an English document — the same boundary every other IDE's docs
have — instead of never seeing German at all. **A surface with nowhere to
put a translation is unfinished; a surface with the slot open and the size
written down is a decision.** The slot is `tutorial.<lang>`, it is read by
the same parser, and a drop-in author can fill it today.

### 96. ~~The first-launch surface~~ — CLOSED: (a) v2.118.0, (b)+(c) v2.184.0

Three things the first-time walk measured but did not change on its own,
because each reverses a decision the owner made by name. Numbers first, so
the call could be made on evidence rather than taste. David read them and
took (a) the same day; (b) and (c) stand as recorded.

**a. Thirteen windows open before the user has done anything — CLOSED
(David's call, same day).** Ten in the editor area (Welcome, Task Rack, DB
Studio, Contract Studio, Infra Designer, API Studio, Browser, IRC, Task
Board, Docker Panel) and three docked (Project Studio, Workbench, NPM
Explorer). Opening an ordinary Node/Express project, **four of the ten were
for technologies the project cannot use** — DB Studio with no database,
Contract Studio announcing "0 contract artifacts" for a bakery API, the
Infra Designer, the Docker Panel with no Dockerfile — a fifth was a chat
client, and a sixth an empty kanban board whose first touch writes
`.nmoxtasks.json` into someone else's repository. They consumed 56% of the
tab strip at the forge's 1600 px window before a single file was opened,
and the editor tab for the file the user came to read arrived eleventh.

The counter-argument was on the record: v1.29.0 chose "every major surface
one click away from minute one", and `DiscoveryTabsGateTest` pinned the
Browser and IRC as *"David's call"*. What settled it was that **discovery
already has three surfaces that cost nothing** — the Welcome's TOOLING
column lists every window with its chord, on screen at first launch; the
Window menu lists them again; the ⌥⌘ chords open them directly. The tab
strip was the fourth copy, and the only one with a price.

Seven closed: DB Studio, Contract Studio, Infra Designer, API Studio, IRC,
Task Board, Docker Panel. A first launch is Welcome → Task Rack → Browser,
plus the three docked panes. The Browser stayed because a Run arms
OpenOnServe and the served page lands there. `openAtStartup` seeds only a
userdir with no saved layout, so no existing install's arrangement moved.

`FirstLaunchWindowsLedgerTest` now derives the population from every
`@TopComponent.Registration` in the product's sources: a new window fails
the build until it states, with a reason, whether a newcomer meets it, and
a second assertion pins the editor strip at exactly three. The decision is
no longer whatever each window chose on the day it was written.

**b. ~~The in-app Browser's bare home is a news site~~ — DECIDED and CLOSED
v2.184.0.** Replaced with a local start page. The principle settles it
without needing a preference: an IDE should not make an outbound request the
user did not ask for, and opening a pane to look at your OWN running app is
about as clear a case as there is. The request is also a small disclosure —
it tells someone else's server that this machine started an IDE, and when.
`StartPage` is built in-process, rendered through `loadContent` so there is
no temp file and no misleading address bar, translated like any chrome, and
`StartPageTest` holds what "fetches nothing" means: no absolute or
protocol-relative URL anywhere in the document, and no remote address left
in the component to fall back to. Nothing else moved — a live serving still
wins, and SCOPE- or Run-routed opens still land on their own URL. The
original text follows.

**b (the original entry). The in-app Browser's bare home is a news site.** With nothing serving,
`⌥⌘4` loads `https://news.ycombinator.com/` and the tab renames itself
"Hacker News" — an outbound request and a third-party page inside a work
IDE, sitting between API Studio and IRC in the tab strip. The logic is
already half right (a live serving wins, v1.204.0/v1.212.0); only the
no-serving case is a stranger's website. A local start page naming the
project's servings would be the honest empty state. Deferred: HOME_URL is
recorded as "David's pick".

**c. ~~The Tools menu's two NMOX groups are split by seven platform rows~~ —
DECIDED and CLOSED v2.184.0, and the entry was wrong about the count.**

Read out of the assembled cluster's own layers rather than from memory,
there is exactly ONE platform row between the two groups — `ToolsAction` at
100 — not seven. The seven (Variables, Libraries, Server Manager, Cloud
Manager, Templates, XML Catalog) come AFTER our second group, past a
separator. The entry also never mentioned a THIRD NMOX row: Language Servers
at 1450.

The deferral's reason has expired. It was "repositioning into the platform's
own range is how v2.104.0 found a real collision" — and what v2.104.0 built
in response was `LayerPositionCensusTest`, which now reads the platform, ide,
java and extra clusters for every folder we write into. The risk that
justified waiting is the thing that is now gated: a collision fails the
build. The capture family moved to 96–99 (free across the whole assembled
cluster, checked), leaving one contiguous block at 90–99 ahead of the
platform's row.

**Language Servers stays at 1450, deliberately.** Grouping by author would
put a diagnostics row among screenshot actions; grouping by FUNCTION puts it
beside Plugin Manager, where both answer "what is installed". The complaint
was never really that two groups had one author — it was that a family had
been split by drift. That is fixed; this one is not drift.


### 87. Two stop registries, two populations — BLESSED (2026-09-05, v2.72.0; amended v2.74.0: the ■ is TOTAL)

*Amendment (v2.74.0):* the rack's device runs joined `LiveRuns` too —
every `RackDevice.exec` registers under the device's title and an
outside stop routes through `CommandDevice.stopByUser` so the verdict
reads STOPPED. "Stop every running command" now means every one; the
faceplate STOP and the rack's Stop All stay. The platform's Run menu
population is unchanged (project actions only). The ledger test's
RackDevice exemption is withdrawn.

The toolbar ■ stops everything registered with `core.spi.LiveRuns`
(the ▶'s runs, NPM Explorer scripts, Focused Test / Tests-window runs,
`ng generate`, the setup installs, the language-server installer;
LiveRunsLedgerTest keeps the population complete). The platform's own
**Run ▸ Stop Build/Run** lists only `BuildExecutionSupport` items —
the ▶'s runs and the NPM lane — because that menu's contract is
PROJECT ACTIONS with a Repeat, and an install or a schematic is neither.
The asymmetry is deliberate: the ■ is the product's stop, the menu is
the platform's, and a run that belongs on both is registered on both
(before the spawn, v2.71.0). Likewise the Focused Test lane keeps ONE
"Focused Test" output tab while the ▶ and the NPM lane name tabs per
run: a test runner's tab is a place you return to, a dev server's tab
is the run's own.

### 86a. ~~Apple Developer ID + notarization~~ — CLOSED v2.188.4, shipped in v3.0.0

**Bought and proven 2026-09-20.** David enrolled (Individual, Team
`GVEU23Q6RB`) and set five repository secrets; **no code change turned the lane
on**. Verified on the published artifact rather than inferred from the lane's
design: `spctl --assess` answers **accepted, source=Notarized Developer ID** for
both the DMG and the app, `stapler validate` passes on both, the app carries
`flags=0x10000(runtime)`, the JVM binary carries all three entitlements, and the
app boots `RC=0` with zero SEVERE.

**It took five tags to get there, and every failure was a real defect in a lane
that had never executed** — recorded because the pattern matters more than the
fixes: AMFI rejects the XML comments the entitlements plist carries by house law
(and `plutil -lint` calls the file fine); `notarytool submit --wait` **exits 0 on
a rejection**, so the guard never fired and the run died two steps later in
`stapler`; making the clusters read-only before signing blocked `codesign`, which
writes *into* the binaries; and Apple's own log finally named ten Mach-O
libraries inside five jars that `codesign` can never reach. A scan of mine had
reported ZERO of those, because `for j in $(find …)` word-splits on the space in
`NMOX Studio.app`.

**The in-place updater would have unsigned it.** v1.298.0 guessed in a
parenthetical that `/Applications` would not be writable; measured, it is (owned
by whoever dragged the app there), so the updater wrote inside the bundle. A/B on
a real 2.187.0 → 2.187.1 update: writable cluster → 1,068 files inside the bundle
and `codesign --verify` **exit 1**; read-only → 0 inside, 955 jars in the
USERDIR, boot RC=0 at 2.187.1 while the bundle stayed 2.187.0, `codesign`
**exit 0**. The signed path now drops write permission after sealing.

### 86b. Windows Authenticode — still a purchase, still open (David's decision)

Microsoft **renamed Trusted Signing to Artifact Signing**
(<https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart>). Before
paying, note what the current docs require for the individual path: Public Trust
for **individuals is US/Canada only**, the Azure **billing account must be
Account Type = Individual** with legal name and address matching the
government-issued ID, the *Artifact Signing Identity Verifier* role is needed,
and `Microsoft.CodeSigning` must be registered on the subscription. Our earlier
worry about a three-year-history requirement does **not** appear there for
individuals. The lane is written and gated; `packaging/windows/authenticode-sign.ps1`
pins the NuGet client at 1.0.60 against a current 1.0.95 and should be bumped
with the first real run. Authenticode buys less than notarization did —
SmartScreen softens as reputation accrues rather than flipping a refusal.

### 86. ~~Official release signing — a v3.0 milestone~~ — macOS CLOSED; see 86a/86b (David's decision, 2026-08-27)

**Re-read v2.184.0 during the senior pass and deliberately NOT decided.**
This is not engineering debt with a deferred answer; it is a purchase and an
identity. Apple Developer ID costs about $99 a year and requires David to
enrol personally, and Windows Authenticode is a monthly subscription — no
principle about code quality settles whether to spend someone else's money
or use their legal identity. The engineering half is already done and
waiting: NBM signing rides a secrets-gated release profile (v2.42.0), the
certificate ships in-product at TRUST level (v2.43.0), and the release lane
publishes a GPG-signed SHA256SUMS pair. If the accounts appear, the lane
takes them; until then the self-signed chain is a deliberate position, not a
gap. The entry stays open because the decision is outstanding, not because
the work is.

Until v3.0, the trust chain is deliberately SELF-SIGNED (v2.42.0):
every module NBM jarsigner-signed with a self-signed 4096-bit RSA
keystore, and every release carrying a GPG-signed SHA256SUMS manifest
(key + certificate published in the repo-root KEYS file; private
material keychain-only + repo secrets). What v3.0 buys, in order of
user-visible weight:

- **Apple Developer ID + notarization** (~$99/yr, requires David to
  enroll): deletes the cask's consented quarantine-clear entirely.
  Upstream NetBeans does this manually per release with the ASF's
  Developer ID (altool-era steps in their wiki); ours should ride the
  release lane via secrets instead. JVM apps need hardened-runtime
  entitlements (JIT, unsigned executable memory) — the one fiddly part.
- **Windows Authenticode** (Azure Trusted Signing ~$10/mo): signs the
  .exe launchers + installer.
- ~~NBM TRUSTED bless~~ — DELIVERED v2.43.0: recon found the
  platform SPI (KeyStoreProvider, Lookup-collected, TrustLevel.TRUST
  for exact-cert trust; Utilities.verifyCertificates consumes it) and
  NmoxTrustedCerts now ships the certificate in-product,
  KEYS-parity-gated. ~~Remainder: the GUI TRUSTED label observed on the
  first post-2.43.0 update walk.~~ CLOSED v2.154.0: there is no label to
  photograph — the Plugin Installer's `InstallStep` opens its *Verify
  Certificate* panel only for plugins that are NOT `isTrusted`. The platform's
  own decision (`Utilities.verifyCertificates`, which `verifyNbm` feeds) run on
  the published 2.153.1 core NBM with a portable's cluster on the classpath:
  1 TRUST certificate, 1 signer, **TRUSTED**; the no-certificate control says
  SIGNATURE_UNVERIFIED. `scripts/nbm-trust-probe.sh <tag>` repeats it for any
  release.

### 94. macOS cannot set the app's language, and saying it could would be a lie — DECIDED: won't do (v2.186.0)

Measured 2026-09-10 while looking for the last monolingual surfaces. The
bundle's `Info.plist` carries no `CFBundleLocalizations`, so macOS treats
NMOX Studio as an English-only application and it does not appear under
**System Settings ▸ General ▸ Language & Region ▸ Applications**.

The tempting fix is to declare the key. **Do not** — it would advertise a
control that does nothing.

The probe, kept because the conclusion is not obvious: a minimal `.app`
bundle declaring `CFBundleLocalizations` for `en`/`uk`/`de`, whose executable
is a shell script that execs `java` exactly the way our launcher does, was
given a per-app language the way System Settings sets one:

```
defaults write org.nmox.locprobe AppleLanguages -array uk
open -W Probe.app
```

The JVM reported `Locale.getDefault() = en_US`, `user.language = en` — with
and without the preference set, byte-identical. macOS passes the choice as
an `-AppleLanguages` argument to the bundle's own executable and exposes it
through that application's `CFPreferences`; a shell wrapper drops the
argument, and the `java` child process is a different application as far as
`CFPreferences` is concerned. Declaring the key would put a language menu in
System Settings that the IDE ignores.

**What closing it would take**, if anyone wants it: the launcher reads the
per-app preference itself and translates it into the `--locale` the platform
already understands — `defaults read org.nmox.studio AppleLanguages`, first
entry, accepted only when it names one of `UiLocale.SUPPORTED`, and only when
the user has NOT pinned a language in Options (the conf block must keep
winning; an explicit choice outranks an ambient one). It is macOS-only, it is
shell-quoting-sensitive in a launcher that already sources a conf, and the
product already offers the same control in Options with a live switch — so
the value is convenience, not capability.

**Decided v2.186.0: won't do, and the reason is a house law rather than
effort.** Closing it means the launcher reading an AMBIENT preference for a
fact that already has one explicit home with a live switch — Options ▸
General ▸ Language, which since v2.103.0 applies without a restart. A second
home for one fact is the exact defect v2.131.0 and v2.136.0 each spent a
release removing, and this one would be the worse kind: the two homes could
disagree, so the rule "an explicit choice outranks an ambient one" would have
to be written, tested and kept true forever. The gain on the other side of
that is a language menu in System Settings for a control the product already
offers, on one operating system.

The cost is also badly placed. The change lands in the launcher's conf
sourcing, which is the highest blast radius in the product: the Windows `.exe`
GREPS that file instead of sourcing it, so two assignments have no defined
winner (v1.256.0), and the same release watched a `sed` rewrite the
explanatory comment beside the line it was editing and ship prose that
contradicted its own code. A convenience feature does not buy a change there.

The measurement is kept, because it is the part with lasting value: it stops
the next author adding the plist key on the reasonable-sounding assumption
that it works. A comment sits at the `Info.plist` heredoc in
`packaging/macos/build-dmg.sh` pointing here. Revisit only if macOS gains a
way to pass the choice to a JVM child process, which is the thing that is
actually missing.

### 90. ~~The platform toolbar is English in every translated build~~ — CLOSED v2.102.0

Found by walking shipped 2.101.0 in Ukrainian and read out of the main
window's accessibility tree: our own actions were translated, the
platform's toolbar buttons were not — `&New File...`, `Save &All`,
`&Run Main Project`, `Profile the Application`.

**This entry's first version was wrong, and the way it was wrong is the
point.** It claimed the four Main Project verbs were "composed at runtime"
and existed in no bundle, so only eight of twelve could be overlaid — and
concluded that two thirds of a toolbar reads worse than a consistent one.
Every part of that was reasoning built on a search that had not been
finished. The keys exist:

```
LBL_RunMainProjectAction_Name = &Run {0,choice,-1#Main Project|0#Project|1#Project ({1})|1<{0} Projects}
```

The composition lives INSIDE the value, which an overlay replaces
wholesale. The earlier search missed them because it filtered values to
under sixty characters and these are longer. **A search with a filter in
it has not proved an absence** — and a conclusion resting on one is an
argument, not a finding. The pattern to watch for: when the reasoning
sounds principled and the measurement was cheap to redo, redo the
measurement.

**Closed:** 144 overlay values across five platform jars in twelve
languages, plus Save All in three more. Eleven of the twelve toolbar
controls now speak the user's language, verified by relaunching and
reading the accessibility tree, not by inspecting the files:
`&Створити файл...`, `Зберегти &все`, `&Скасувати`, `&Повернути`,
`&Зібрати головний проєкт`, `&Очистити та зібрати головний проєкт`,
`&Запустити головний проєкт`, `&Налагодити головний проєкт`,
`Профілювати застосунок`. The verbs are taken from the menu overlays we
already ship, so the toolbar and the menu agree. Slavic carries the extra
2/5 branches; the platform's own `-1` branch — what a fresh start shows —
is the one that used to read English.

**The walk found the second mistake too.** `Save &All` did not change on
the first pass, because the overlay went to `versioning-util`'s
`LBL_SaveAll`, which belongs to a diff dialog. Four jars hold that string;
all three plausible ones are overlaid now.

**The honest remainder, this time actually measured:** the toolbar's
project-configuration combo still reads `Set Project Configuration`. That
string is absent from *every entry of every one of the 520 jars* — not
just the properties files, and not filtered by length — so it is built
programmatically and no overlay can reach it. One control.

### 89. ~~The ■ tooltip and its status line are English in every language~~ — CLOSED v2.101.0

Closed the day it was opened, on David's call: *don't half-do things.*

The rendering left `core.spi` entirely. A pure core has no bundle and can
have none — it is the seam every module depends on, below the UI — so a
sentence written there is a sentence no translation can reach. `LiveRuns`
returns the data now and `tools.npm.StopRunText` renders it, which is the
house pattern already written down: **the law is the string that reaches
the label.**

Six keys, twelve languages, 72 values. What made that defensible without a
translator pass, where v2.99.0 and v2.100.0 had drawn the line, is that
almost none of it was new prose: `CTL_StopRun` already carried the singular
sentence in every language, `LiveRunSearchProvider_stopped` already carried
"Stopped: {0}", the NPM marker carried the "running" verb, and v2.100.0 had
just landed each language's word for "since". Only "nothing is running" and
the plural noun were authored, and both are built from vocabulary sitting
in the same bundles.

The plural is real, not imitated. Polish, Russian and Ukrainian inflect
across 1 / 2–4 / 5+ and carry the four-branch `choice` this codebase uses
everywhere (`Zatrzymaj 3 działające polecenia` → `Zatrzymaj 5 działających
poleceń`); Indonesian, Filipino, Vietnamese and Chinese say the same words
in every branch, on purpose.

**The gate is upstream of the sink, because that is where this class
lives.** `ChromeLiteralRatchetTest` watches literals AT a Swing sink and
could never have seen these — they were two modules away inside a helper
and arrived at the sink as a variable. `SpiHoldsNoProseTest` bans sentences
from `core.spi` at all, wherever they were headed, with exactly one blessed
exception written down: KVASIR's neutral default question is the payload of
a request, not chrome.

**Two lessons, both from mutants that survived.** The plural assertion
first asked whether `2 działające polecenia` equals `5 działające
polecenia` — never true, because the digit differs — so Polish could have
stopped inflecting and the test would have passed; it normalises the digit
and compares the words now. And the incremental-build trap: `tools.npm`
keeps a hand-written `Bundle.properties`, the annotation processor MERGES
into it at compile time, and a bare `mvn test` re-copies the hand file
without recompiling — so a mutation proof in this package must run `clean`
or it measures the copy, not the code.

*The original entry, kept as the diagnosis it was:*

Found while closing 88 (v2.100.0), by reading the same call site one layer
out. `core.spi.LiveRuns` does not just leak a word — `tooltip(List<Run>)`
and `stoppedMessage(List<Run>)` assemble whole English sentences in a pure
core and hand them straight to a Swing sink: "Stop the running command: ",
"Stop 3 running commands: ", "Stop Running Command — nothing is running",
"Nothing is running", "Stopped: ". `StopRunAction` sets the first as the
toolbar ■'s `SHORT_DESCRIPTION` and the second on the status line, so a
Ukrainian user hovering the ■ reads English, in a build where every bundle
is complete.

Not taken inside v2.100.0, and the reason is the boundary that release was
about. Ledger 88 was a preposition per language inserted into a phrase a
translator had already written — grounded in reviewed vocabulary. This is
roughly six new keys carrying real sentences, ~72 values, and plural forms
(«Stop {0} running commands» inflects in Polish, Russian and Ukrainian and
does not exist in Indonesian, Filipino, Vietnamese or Chinese — the
v2.99.0 plural rules apply). That wants a translator pass, not an author
with a dictionary. *(Closed the same day: the vocabulary turned out to be
already in the bundles — see the close above.)*

The shape of the fix is known: the pure core returns the DATA (the runs and
their times) and the consumer renders with its own bundle, which is the
house pattern everywhere else — the law is the string that reaches the
label. `ChromeLiteralRatchetTest` now sees the `putValue` sink, so a
regression here is caught the moment the prose moves to a literal; what it
still cannot see is prose assembled in a helper, which is what this is.

### 88. ~~An English word reaches every translated build through an ARGUMENT~~ — CLOSED v2.100.0

`LiveRuns.since()` is gone; `sinceTime()` returns the bare `HH:mm` and the
word that introduces it lives in each language's own key. Twelve languages
read correctly, and the fix is gated three ways: the time is data
(`LiveRunsTest.sinceTimeIsDataNotProse`, over four zones and every hour),
no production source can call the prose form again, and each language's
value must carry its own reviewed since-word
(`BundleArgumentIsDataTest`). All four mutants die by name.

**What the closing found that the opening did not.** Hindi was not just
also-broken — it was broken the OTHER way. Its translator had already read
`{0}` as a bare time and supplied the postposition (`{0} से चल रहा है`), so
Hindi rendered the preposition twice («since 14:32 से चल रहा है») while the
other eleven rendered an English word. The two halves of one ambiguity, in
one key, shipped together for seven releases. **When translators disagree
about what an argument is, the code never told them** — and the answer is
always that an argument is data.

The class remains worth watching: prose assembled in a helper and handed to
a sink is invisible to a gate that reads literals at the sink. Ledger 89 is
the standing instance.

*The original entry (v2.99.0), kept as the diagnosis it was:*

Found by a Chinese translator agent reading the call site rather than the
string (v2.99.0). `core.spi.LiveRuns.since(id)` returns the literal
`"since " + HH:mm`, and three user-visible surfaces splice that whole
phrase in as `{0}`: the Workbench's RUNNING row
(`WorkbenchRunning_runningSince=running {0}`), the ⌘I live-run result
(`LiveRunSearchProvider_stop`), and the NPM Explorer's marker — plus,
the closing found, the served row's address suffix and the agent-facing
`live_runs` JSON field. So a
Ukrainian, Chinese or Polish user reads «виконується since 14:32»,
「正在运行 since 14:32」, "działa since 14:32". Shipped since v2.73.0 and
invisible to every gate here, because the bundles are all correct — the
English enters BELOW them, as data.

The fix is small but crosses twelve languages: add `LiveRuns.sinceTime`
returning the bare `HH:mm`, move the word "since" into each of the three
keys' English text, and have the translators supply the three phrases per
language. Deliberately NOT done inside v2.99.0: authoring 36 strings in
languages without a translator pass would be exactly the shortcut this
arc has avoided. It is its own unit, and the fourth member of the
argument-carries-untranslated-text class worth a gate — any string handed
to a bundle as `{0}` should be data (a name, a path, a number), never
prose.

### 85. ~~IDE string localization — English by construction~~ — OPENED as the l10n arc, v2.97.0

v2.98.0 (2026-09-08) added UKRAINIAN as the seventh language on the road
v2.97.0 built: one entry in `UiLocale.SUPPORTED`, one in the parity gate's
locale list, 2,830 translated keys across the same 71 packages, and a
seventh set of branding+locale overlays for the platform menu bar. The
cost of a language is now the translation and nothing else — which was
the point of the mechanism. Ukrainian also closed the MessageFormat
apostrophe hazard by construction: its values use `’` (U+2019), correct
typography and inert to the format parser.

v2.97.0 (2026-09-07, David's ask: EN/FR/RU/HI/ES/DE) shipped the
mechanism and the first tranche: Options ▸ General ▸ Language writes a
shell-safe `--locale` block into the per-user launcher conf (restart to
apply), the product's own chrome strings moved into NetBeans bundles
with five translations each, the platform's top-menu names localized
through branding+locale overlay jars, and `LocaleBundleParityTest`
holding the assembled cluster to five-locale parity. STILL OPEN, by
decision: the rack faceplate vocabulary (GO/STOP/EXPLAIN, knob names,
LCD lines) stays English — the hardware panel, sized by the fit law;
and every platform string beyond the top menus stays English until the
community bundles for these languages exist. Windows launcher reading of
the userdir conf is unverified — still true at v2.98.0, and the same
remainder now applies to Ukrainian. The original measurement follows.


Measured v2.37.5 (the i18n pass): 51 of 697 main-source files touch
Bundle/@Messages — mostly action display names the platform
registration requires — and every dialog, status line, and report
string is an English literal. The shipped platform cluster carries no
non-English locale bundles either, so module-side externalization
alone would produce a mixed-language UI. Full l10n = externalize
~650 files' strings + platform locale bundles + a translation
pipeline: a dossier-first project on the RELEASE310 model, not a
sweep. What v2.37.5 DID ship is the half with defects in it: the
Turkish-I sweep (55 bare case-folds → Locale.ROOT,
CaseFoldLocaleGateTest the law) — matching now works for every user
regardless of their locale, even though the words stay English.

### 84. ~~The NetBeans Platform upgrade (RELEASE300 → RELEASE310+)~~ — CLOSED v2.35.0 (shipped, boundary observed v2.35.1)

Dependabot's 2026-08-20 grouped PR carried netbeans.version
RELEASE300→RELEASE310 beside routine junit/org.json/mongodb bumps —
refused structurally (the v1.243.0 OpenJFX class, platform-sized):
the release assembles the ENTIRE platform cluster at this version,
and the codebase carries decompiled-behavior assumptions pinned
against RELEASE300 jars: LSPBindings' instance-keyed server reuse +
MultiMime registration semantics (ledger 83, v2.19.0/v2.19.1), the
rename refactoring collecting every binding's edits (ledger 81), the
autoupdate catalog's pre-redirect URL resolution (v1.51.0),
FileElement$Type.accept mime-resolver composition (v1.217.0), the
LanguageIdResolver fallback (v1.218.0). The upgrade is a dossier-first
project on the JDK-25 model (v1.250.0): re-read each decompiled
assumption against the new jars, recompile + full verify, boot laws,
browser + update-center + toolchain gauntlets, then David's call.
dependabot.yml now ignores org.netbeans.{api,modules,cluster} with
the reason written in place, so the group PR stays mergeable without
smuggling the platform. The measured half is DONE same-day
(docs/engineering/release310-dossier.md, v2.19.6): all four decompiled
assumptions byte-identical 300→310, artifacts on Central, full reactor
test-compile AND full verify GREEN under -Dnetbeans.version=RELEASE310
with every floor holding. The runtime probe (same day)
came back RED: the assembled RELEASE310 app fails to start
org.eclipse.jgit under Netigso (identical bundle bytes, identical
cluster census — the host regressed) and wedges before module
turn-on. Root-caused same day (v2.21.6): the host was innocent —
jgit's org.slf4j [1.7.0,3.0.0) OSGi import lost its provider to a
cluster-placement change (slf4j-api stopped being auto-wrapped as a
bundle); one explicit application-pom dependency pins the placement
and the 310 assembly boots clean. GO-READY; remaining gauntlets ride
the bump PR. CLOSED by v2.35.0 (David's GO, 2026-08-23): property
flipped, full verify green, and every dossier GO item measured on the
assembled RELEASE310 app - boot laws (6s, 0 spawns, 0 SEVERE), the
browser gauntlet (https + plain-http + DevTools DOM on FX 26), all
nine chords, the update-site dry run (11 NBMs), the example SPI
plugin against the new core. The update boundary measured: 2.35.0
modules carry RELEASE310 dependency floors one spec above what a
2.34.5 cluster provides, so older installs are held back honestly
(refuse-by-range, v1.47.0) and cross via installers per the v1.256.0
timing law. The boundary OBSERVED post-tag (v2.35.1): a stock 2.34.5
against the real 2.35.0 catalog — the Plugin Manager OFFERS all 11
pre-checked, and the Plugin Installer wizard then names the exact
missing platform specs ("Utilities API >= 9.42 but only 9.41 was
found") and refuses to advance (three Next activations + Return, no
state change, nothing downloaded, jars byte-identical, 0 SEVERE) —
the graceful hold, measured not assumed. CAVEAT: the headless
`--modules --update-all` CLI does NOT run that check — it installed
all 11 in place and every product module then failed enable (the
refuse-by-range cascade), originals in update/backup. The gauntlet
law: never script --update-all across a platform boundary.

## Closed — every item here was delivered (added v1.356.0, the toolchain walks)

### 83. ~~One LSP server process per mime per project~~ — CLOSED v2.19.0 (the senior-RCP pass)

The v1.356.0 measurement (two `deno lsp` for one .ts+.js workspace;
two typescript-language-server in any mixed JS/TS project; two
ngserver in an Angular project with a component and its template open)
is fixed with the platform's OWN seam, found by the recon this entry
demanded: `MultiMimeLanguageServerProvider` (in our RELEASE300
lsp-client) makes `LSPBindings.buildBindings` file a started server
under EVERY declared mime, so the second mime reuses the live
bindings. The recon's two decisive facts, from the platform source:

- **Teardown fear unfounded**: shutdown is GC-driven (`LSPReference`
  on the active reference queue) behind a 10-minute keep-alive — NOT
  editor-close based. Sharing across mimes is what the mechanism is
  for.
- **The trap is instance identity**: the reuse map is keyed by
  provider INSTANCE, and a class-level `@MimeRegistration` puts one
  `.instance` file in each mime folder — each instantiated separately,
  so the second mime presents a different instance and a second server
  starts anyway. The cure: registrations moved to static SINGLETON
  FACTORY methods (`methodvalue` in the generated layer), so every
  mime folder resolves the same object. `MultiMimeSingletonGateTest`
  pins both laws against the generated layer (parity: registrations ==
  `getMimeTypes()`; no class-instance backdoor), mutation-proven.

Converted: DenoServer, EslintServer, StylelintServer (5 mimes),
AngularServer, ClangdServer. The tsserver pair is the interesting one:
its ts/js split is workspace-CONDITIONAL (ledger 81 — ngserver alone
owns .ts in Angular workspaces while .js keeps tsserver) and
`getMimeTypes()` is static, so a naive merge would rebind tsserver
beside ngserver — the proven double-rename. The pair now partitions by
WORKSPACE KIND instead of mime: `TypeScriptServer` (multi-mime ts+js,
starts only in plain workspaces — one shared process) and
`AngularJavaScriptTsServer` (single-mime js, Angular workspaces only);
exactly one returns non-null for any (workspace, mime), pinned by
`TsServerAngularSuppressionTest`. The same-night review (v2.19.1)
found the conversion had missed CssServer (three-mime class
registration) because the gate checked the MECHANISM (classes
implementing MultiMime) instead of the OUTCOME (any multi-mime
registration in the generated layer) — the gate now derives its
population from the layer itself, failing-first proven.

## Closed — every item here was delivered (added v1.283.0, the Task Rack walk)

### 76. ~~Tooltips never reach an LCD on a rack faceplate~~ — CLOSED 2026-08-11 (the bisect with David)
The bug was in the VERDICT, not the code. A property-gated probe
(`-Dnmox.tooltip.probe`) in the running app, with David at a real
mouse, traced every link live: `addNotify` registration ✓, mouse
events delivered to the LCD ✓, `ToolTipManager` querying
`getToolTipText(MouseEvent)` and receiving the full non-null string on
thirteen consecutive moves ✓ — and the tip window SHOWED ✓ (David's
eyes). The tooltip has worked at least since v1.283.0's registration
fix. Three live checks said "never fires" because of two compounding
observation failures:

- **Synthesized hovers can never see a tooltip.** `ToolTipManager`
  restarts its 750 ms timer on every `mouseMoved`; a tip appears only
  after the pointer holds completely still. Automation that glides and
  clicks never dwells. (The recipe that CAN verify tooltips from
  automation: one move onto the target, then wait > 750 ms without
  moving.)
- **The tip was camouflaged.** The LAF's default tooltip is near-black
  with plain text — over the rack's near-black faceplate it is
  functionally invisible, so even human spot-checks looked through it.

The second failure was the real product defect, and it is fixed:
`RackStyle.phosphorTip` styles rack tips like the LCDs they sit beside
(phosphor green on glass black, visible bezel line, LCD font), wired
via `createToolTip()` on `LcdDisplay`, `RackButton`, and `RackDevice`
only — never through `UIManager`, which would restyle the whole IDE.
Seam + wiring pinned by `PhosphorTipTest` (the v1.321.0 two-proof
law).

### 79. CLOSED same day — ngserver never attempted because the file's OWNER project is src/ (the v1.223.0 class)
The ALS gauntlet on ~/NMOX/ngdemo found the whole chain silently inert:
across three sessions (incl. fresh JVMs), on both text/typescript and
text/x-ng-template panes, with EVERY externally-checkable precondition
met — angular.json present, workspace trusted (Run spawns with no
prompt), typescript/lib/tsserverlibrary.js present, and (after install)
node_modules/.bin/ngserver executable — no ngserver process ever
started, no "intelligence unavailable" notification fired, and the log
carries no LSP attempt. typescript-language-server started fine in the
same sessions, so the provider chain itself runs. Consequences: the
one-click install NOTIFICATION entry point can never fire for ngserver
(the Tools ▸ Language Servers panel path works — live-proven installing
@angular/language-server@18.1.2 into the project, trust-gated, exit 0),
and Refactor ▸ Rename over ngserver is untestable. Template
intelligence was last live-proven in v1.218.0. Next step is a
property-gated probe bisect of AngularServer.startServer's five decline
points in a dev build — external observation is exhausted.
RESOLUTION (same day, one probe round): projectDir(lookup) returns the
file's OWNER project — and Angular's src/index.html is a STATIC-kind
manifest, so that owner is ngdemo/src, where src/angular.json does not
exist → silent decline (the v1.223.0 class, second consumer). Fix:
AngularServer resolves the workspace by walking UP for angular.json
itself (angularRootAbove, the v1.223.0 cure). Live-proven post-fix:
ngserver started with the project's own probe locations, and the
platform rename dialog reached the TEMPLATE ({{ title }} → {{ heading }}
rewritten across files). The property-gated -Dnmox.ng.probe probes stay.

### 81. CLOSED v1.349.0 — LSP rename double-apply: ngserver owns text/typescript in Angular workspaces
With tsserver AND ngserver both bound to text/typescript, Refactor ▸
Rename (⌃R) collected BOTH servers' edit sets and applied both: the
class declaration became `headingheading` while the template usage
(ngserver's edit alone) renamed correctly. CLOSED the structural way:
the "filter rename capability" option was BUILT and REFUTED live — a
stream filter that verifiably stripped tsserver's renameProvider from
its initialize response changed nothing, because the platform's
RenameRefactoringPlugin queries EVERY binding on the mime with an
always-true capability predicate (decompiled: lambda$prepare$6 is
iconst_1; ireturn — renameProvider never consulted). The only lever
the platform leaves is WHICH servers are bound, so tsserver now yields
text/typescript to ngserver in Angular workspaces (ngserver wraps the
TypeScript language service; .ts intelligence stays) while plain .js
keeps its own unsuppressed registration. Live-proven: title→heading
applied ONCE at the declaration, {{ title }} rewritten in the
template; TsServerAngularSuppressionTest gates it, mutation-proven.

### 80. ~~Popup position 95 collision on ng-template panes~~ — CLOSED v2.36.4 (structurally)
The recorded pair had already been moved (goto-component sits at 96
with the ledger comment), but the class was alive: the new
PopupPositionUniquenessTest — derived from the GENERATED layer, the
v2.19.1 outcome-gate idiom — named FIVE collisions on its first run,
all Emmet's popupPosition 95 against the v1.313 switcher's 95 across
x-ng-template/typescript/css/scss/less. Emmet moved to 94 everywhere;
any future collision fails the build, not the boot log.

### 77. CLOSED v1.349.0 — the bisect: content-resolved panes never existed
A file claimed by the PROGRAMMATIC NgTemplateContentResolver (suffixless
`usage.html`) gets text/x-ng-template for LEXING — the Angular grammar
paints — but its editor pane does not dispatch the mime's Keybindings:
⌥⌘E and ⌥⌘B are dead there, while the SAME chords in the SAME session
work on a declaratively-resolved `.component.html` pane (differential
proven live, both directions). Suffixless templates therefore have
colors and completion but not chord gestures. Suspected split-brain
between the document's lexer mime and the editor-kit/keybinding mime
for .instance-resolved files; needs a dedicated bisect of the platform's
kit-selection path. The popup-menu entries still work everywhere.
The bisect landed at the bytecode: MIMESupport$CachedFileObject.
getResolvers() builds its chain as declarativeResolvers() FIRST — a
walk of Services/MIMEResolver taking only .xml children — then appends
Lookup-provided instances, so the platform's declarative ext=html
claim answers before ANY .instance resolver regardless of its position
attribute (position orders only within the declarative group). The
programmatic resolver never won in any session; the "session
dependence" was misattributed observation. Reproduced deterministically
(usage.html = plain html in a fresh dev userdir, unchanged after
touch+reopen). The keybinding half is therefore moot — no
content-resolved pane can exist. The inert resolver is deleted with a
tombstone gate (SuffixlessAngularGateTest); the GOAL reopens as 82.

### 82. ~~Suffixless template MIME recognition~~ — DELIVERED v2.37.8 (editor surface; ALS remainder recorded)
David's call stands — the Angular bet means ALL Angular repos, and
Angular 21's CLI generates suffixless widget.html files — but the
v1.346.0 content resolver could never run (see 77: declarative XML
resolvers always precede Lookup-provided ones in decompiled
MIMESupport, so the platform's ext=html claim wins first). Candidate
mechanisms, none free: (a) a DECLARATIVE resolver before html's
position using <pattern> content rules — only catches templates
carrying Angular-only syntax (*ngIf=, @if () — a partial, mushy match
that risks claiming other frameworks' files; refused for now under the
wrong-guess-mutates law), (b) an upstream MIMESupport change letting
positioned instances interleave, (c) rerouting at the editor layer
(DataObject/kit) instead of MIME. The four-file switcher, Run Focused
Test, and ng generate all already handle suffixless sets by their own
file logic — this ledger is ONLY the mime/coloring/chords surface.

**Mechanism (c) measured VIABLE (2026-08-25, decompiled):**
`CloneableEditorSupport.setMIMEType(String)` is PUBLIC — the editor
support carries its own `mimeType` field consulted by `cesKit()`, so
a DataObject can force the editor content type independently of the
FileObject's resolver-assigned mime. The candidate shape: a
DataLoader positioned before the platform html loader claiming an
.html file on TWO structural signals (a same-basename `.ts` sibling
carrying `@Component`, inside an `angular.json` ancestry — the
v1.314.0 two-signals rule; no content sniffing of the template
itself, so the wrong-guess-mutates law holds), whose
DataEditorSupport calls `setMIMEType("text/x-ng-template")`. Open
questions for the probe build: the LSP client's binding follows the
DOCUMENT mime or the FileObject mime (decides whether ALS rides
along), and loader-order parity with the platform html loader.
The probe is its own unit; this entry is the mechanism dossier.

**DELIVERED v2.37.8, both halves measured live:** `registerEditor`
alone reroutes only the multiview registry (the first walk opened an
EMPTY editor — the ng mime registers no MultiViewElement — and after
the plain-editor fix the popup/breadcrumb STAYED html); adding the
public `CloneableEditorSupport.setMIMEType` pinned the document mime
and the full template surface followed — Angular coloring (@if as a
block), the ng popup (Go to Declaration ⌘B, Open Component Class, Go
to Component), and the component jump, all live-proven on a
suffixless widget.html/widget.ts pair, with plain.html walked as the
control (html multiview + html popup, untouched). The honest
remainder: the platform LSP client binds by the FILE mime, so the
Angular Language Service does not attach to suffixless templates —
the html LSP toast fired instead.

**The ALS half CONCLUDED structurally blocked (v2.38.6, the night
research):** decompiled `LSPBindings` keys server bindings per
PROJECT × MIME — never per file — so every route to attaching
ngserver here fails the same way: a provider registered under
`text/html` (even a workspace-guarded one) binds ngserver beside the
html LS for EVERY html file in the project, and the platform rename
collects edits from every bound server — the ledger-81 double-rename
class reborn, the exact hazard v2.38.0's research refused. Per-file
discrimination inside a provider is impossible at this seam
(startServer has no file), and rerouting the FILE mime was mechanism
(a)/(b), refused under the wrong-guess-mutates law. The remainder
therefore joins ledger 83's recorded direction: safe per-file LSP
binding needs a PLATFORM change (bindings consulting the document
mime, or per-file provider consultation) or an LSP multiplexer that
owns the fan-out — either is its own project, not a patch. Suffixless
templates keep the full editor surface (coloring, popup, ⌘B, the
component jump); template type-checking arrives when one of those
lands.

**The ALS half researched further (v2.38.0):** the tempting route —
registering ngserver for text/html in Angular workspaces via the
ledger-83 MultiMime singleton — is REFUSED for now: the platform
binds every provider on a mime (v1.213.0's lookupAll design), so
ngserver would sit beside vscode-html-language-server on EVERY html
file in the workspace, and the platform rename collects edits from
every binding (ledger 81, decompiled) — the double-rename class
v2.19.0 killed, reborn. A safe attach needs either a per-FILE veto in
the provider SPI (upstream) or the mime-suppression surgery ledger 81
used for typescript, scoped to html — its own unit with A/B proofs,
not a batch rider.


### 78. CLOSED v1.349.0 — ⌘B on templates: tags AND identifiers
The bisect landed: the chord was never shadowed — on CSL panes ⌘B is
CSL's OWN Go to Declaration, which consults the language's
DeclarationFinder and silently no-ops when there is none (so the
v1.219.0 mime action never saw the key; its era of "working" was
likely always the popup). The ng-template language now registers a
snapshot-only Parser + NgSelectorDeclarationFinder, and the native ⌘B
jumps <app-hero> → its component (live-proven, caret on the selector
line). The identifier half closed v1.349.0: NgSelectorDeclarationFinder
claims identifier spans (only when an LSP hyperlink provider exists)
and routes them to the platform LSP client's performClickAction —
CSL stays quiet on the returned NONE while ngserver answers the
definition. Live-proven: ⌘B on `heading` inside {{ heading }} in
app.component.html landed the caret on the class property (12:3).

## Closed — every item here was delivered (added v1.243.0, the deps housekeeping)

### 74. ~~The OpenJFX major upgrade is chained to a bundled-JDK decision~~ — CLOSED v1.253.0
David's call, 2026-08-03: advance. The product baseline moved to
**JDK 25 LTS + OpenJFX 26**, all pins in lockstep, with the full
gauntlet green: `mvn verify` (tests + SpotBugs + find-sec-bugs +
JaCoCo floors, all ten modules) passes on JDK 25 with zero errors —
the dossier's biggest unknown; a workflow-identical ALL-MODULE-PATH
jlink over the complete FX 26 jmods dir (incubator modules included)
builds clean; the app boots on that runtime with zero SEVERE; and the
Browser renders https, loads plain http through the v1.226.0
h2c-flagged loader, and answers DevTools DOM reads on FX 26's WebKit.
The one thing that does NOT move: `maven.compiler.target` stays 21 —
see the law at the property in the root pom (the update center ships
modules, not runtimes).

## Closed by v1.273.0 (the ledger-75 layout pass)

### 75. ~~The narrow-width clip class — both instances~~ — CLOSED v1.273.0
The deferral's trigger was "a layout pass"; the second instance
(v1.271.0's Infra walk) supplied it. DB Studio's console toolbar now
installs `core.util.WrapLayout` — a FlowLayout whose
preferredLayoutSize reports the WRAPPED height for the container's
current width, so at narrow widths the bar flows onto a second row
and Save…/saved-queries stay reachable while RUN/EXPLAIN/Cancel keep
their one-click place (plain FlowLayout wraps at layout time but lies
one-row in preferred size, which is exactly what clipped the second
row invisible). The Infra property form became a Scrollable that
tracks the viewport width, so GridBag squeezes the weightx=1 editor
column instead of growing a horizontal scrollbar over clipped
Name/Size values. WrapLayoutTest (3 behavior tests incl. the one-row
lie), PropertyPanelLayoutTest, and ConsoleBarLayoutGateTest pin all
three; mutation-proven ×3.

## Closed — every item here was delivered (added v1.241.0, the Angular truth release)

### 73. ~~Suffixless Angular templates are invisible to the template intelligence~~ — CLOSED 2026-08-11 (David's call: invest)
The programmatic resolver the deferral asked for exists:
`NgTemplateContentResolver` claims `text/x-ng-template` for an
`.html` whose same-basename `.ts` sibling carries `@Component`
(`NgTemplates`: capped 8 KB sniff, verdicts cached by mtime+size,
ONE sibling stat for ordinary html — the per-file-open cost the
deferral weighed). Registered as an `.instance` IN the ordered
`Services/MIMEResolver` layer folder at position 260 — the one
channel that beats the platform's declarative html claim (the
v1.217.0 ServiceProvider lesson, this time proven live POSITIVELY:
a suffixless `hero.html` opened wearing the Angular grammar). The
four-file switcher was already suffix-agnostic and is now pinned by
`SuffixlessAngularGateTest`; index.html has no `.ts` twin and stays
plain. Our own generators keep pinning the suffix regardless.

## Closed by v2.186.0 (the ambient-selection release)

### 72. ~~API Studio, Contract Studio and the Infra Designer stay selection-less~~ — CLOSED v2.186.0
v1.235.0 gave six suite windows (Welcome, Browser, IRC, Docker Panel,
Block Studio, DB Studio) the aimed project as their ambient selection
via `rack.service.AimFollower`, so Test Project (^F6), the Team menu
and every project-sensitive action work while those windows are
focused. The remaining three CANNOT ride the same helper: apiclient,
web3 and infra dropped their rack Maven dependency on purpose in the
v1.46.0 soft-dependency surgery, and reaching AimFollower would
re-add it. The honest route is a small aim-node facade in core.spi
(the ProjectAim idiom: rack publishes an @ServiceProvider adapter
exposing the resolved node or its Lookup, the three modules consume
it null-safely). Deferred until the facade earns a second consumer
beyond selection — the three studios have their own re-aim machinery
already, and their users' project-sensitive gestures (Team menu from
INSIDE API Studio) are rare enough that no journey has hit the gap
yet. Wire it when one does, or when core.spi grows the facade for
another reason.

**Closed v2.186.0, and the deferral's premise was wrong.** It waited for "a small
aim-node facade in core.spi" to earn a second consumer, assuming the facade was
the cost. There was no facade to build: `core.spi.ProjectAim` has carried
`projectDir()` and aim-change listeners since v1.46.0, and `core/pom.xml` already
declared `org-openide-nodes` AND `org-openide-loaders`. **The entire fix was an
address change.**

`AimFollower` and `AimNodePublisher` MOVED to `core.util` — not copied, no
delegate left in rack, one implementation for all nine windows — rebuilt on
`ProjectAim` instead of `Rack.Listener`. The three studios consume it with the
lookup-and-null-branch living INSIDE the follower, so the idiom has one home
instead of three. None regained a rack dependency: `RackSoftDependencyTest` is
green in all three, `dependency:tree` shows no rack artifact, and the generated
NBM manifests read `rack: False | nodes: True`.

**A concurrency fix came out of a surviving mutant.** The moved class carried
three gates for one law — a volatile `showing` flag, a plain `attachedTo`
reference, and a null check — so the publish-while-hidden mutant hid behind the
other two. The plain reference was the wrong kind of gate anyway, since
`ProjectAim` fires on the AIMER's thread where a non-volatile field has no
visibility guarantee. Collapsed to one volatile reference; `hidden()` clears it
BEFORE unsubscribing, so an event already in flight finds null and is refused —
the case an unsubscribe alone cannot cover. *A surviving mutant is a finding
about the code, not about the test.*

## Open — deferred deliberately, with reasons (added v1.216.0, the v1.209–v1.215 arc review)

### 63. CLOSED v1.262.0 — arrow-target URLs are mapping destinations, not servings
The deferral's own condition was met: a live repro pinned the exact
line shapes. **http-proxy-middleware 2.0.9** (the CRA-era stack)
prints `[HPM] Proxy created: /  -> http://localhost:3001` BEFORE the
server's own banner — the hazard was real; **HPM 4.2.0 and
webpack-dev-server 5** print no proxy line at all — modern stacks
were never exposed. The general rule the deferral said didn't exist
fell out of the corpus: no banner (vite `Local:`, wds `Loopback:`,
CRA, `started server on`, artisan, `php -S`) puts an arrow before its
own URL — **arrows point at destinations**. `ServeUrls.firstLocalUrl`
now skips a URL immediately preceded by `->` or `→` and keeps
scanning; a pure proxy line yields null and the real banner registers
on a later line, which is the correct order. Both consumers (the
serve devices and the ide Run lane) share the one scan.
Mutation-proven; the captured lines are pinned verbatim in
`ServingDevicesTest.arrowTargetsAreNotServings`.

### 64. ~~OpenOnServe's listener attaches after the arm's registry snapshot~~ — CLOSED v2.68.0 (one synthetic rescan after the attach; seam-tested)
A serving registered in the microseconds between `urlsBefore` and
`addListener` is neither suppressed as pre-existing nor delivered as an
event. Unreachable by the armed project's own server (its process spawns
after `arm()` returns) — it needs an unrelated device announcing the
same project's URL in that instant, and the next registry event re-scans
anyway. Deferred as measured-harmless; noting it so the window is a
recorded decision, not an unknown.

### 65. A present-but-broken LSP server binary can be relaunched per file open (re-blessed v2.68.0: the platform's retry policy; a per-open spawn probe taxes every healthy install)
The platform LSP client retries a server that dies at startup. The
Angular provider declines deterministically detectable breakage
(TypeScript 7's missing tsserverlibrary); a binary broken for
environmental reasons (wrong node, half-installed package) is not
cheaply detectable before spawn, for eslint or any other server.
Deferred: a spawn-probe per file-open would cost every healthy install
to guard a broken one; the platform's retry policy is the platform's.

### 66. ~~IRC messages during a closed tab are neither rendered nor logged~~ — CLOSED v1.322.0 (the logging half)
Logging moved from the window's Bridge to the engine: `IrcLogTap` is a
second `IrcClient.Listener` attached at CLIENT creation, so it lives as
long as the connection does — an enabled log keeps recording while the
tab is closed. It keeps its own minimal channel-membership map
(353-seeded, JOIN/PART/KICK/NICK-maintained) because QUIT names no
channels; the Bridge renders only and no longer logs inbound traffic
(one writer per line), and the send path's existing
`!capEnabled("echo-message")` guard composes so echo-capable servers
log the echoed copy via the tap instead. The RENDERING half (transcript
backfill into a reopened window) stays out by design — the transcripts
are window furniture; the durable record is the log. Mutation-proven ×2
(353 seeding deleted → the seeded-quit test errors; a Bridge event log
restored → the wiring gate fails by name).
By design the connections outlive the window (v1.204.0), but ALL
transcript and log writes ride the window's Bridge — so with logging
enabled and the tab closed, traffic in that window-closed period is
lost to the log files the user turned on. Pre-dates the arc (recorded
by its review). Fixing it means moving logging from the UI bridge to
the engine layer — a real design change, its own release.

### 67. ~~"compose" ranks Laravel/CRATE above HARBOR in device search~~ — CLOSED v2.68.0 (SearchTerms.score; exact hits first, membership unchanged)
`composer` (the PHP tool, correctly in two vocabularies) prefix-matches
the query "compose", and shelf order puts those devices first. All hits
are true matches; the strongest-intent device just isn't first. Ranking
is a different feature from matching — deferred until search results
carry scores.

### 68. "Workspace" carries four meanings in UI strings — BLESSED (David, 2026-08-01)
The word names (a) Workspace Trust, (b) npm/pnpm monorepo workspaces
(WAYPOINT), (c) the per-studio `.nmox*.json` workspace files, and (d)
Angular workspaces (`angular.json`). The v1.215.0 IA pass flagged that
two of these are industry terms we must keep and two are our own
coinage that could rename. David's call: keep all four as-is — each is
the natural word in its context, and a rename would trade familiar
local vocabulary for global consistency nobody asked for. Revisit only
if a real user confuses two of them in the same surface.

### 69. `mvn verify` wipes the developer's real Workspace Trust prefs — CLOSED v1.225.0
Fixed: the first `clearForTest()` call flips `WorkspaceTrust` into
test mode for the rest of the JVM — the in-memory set clears and every
subsequent write lands in a scratch child node, leaving the real
grants untouched. Production never calls it. Regression test seeds a
sentinel in the real node and proves it survives clearForTest AND
test-mode writes. (Original entry kept below for the record.)

### 69-original. ~~`mvn verify` wipes the developer's real Workspace Trust prefs~~ — superseded by 69, CLOSED v1.225.0 (kept for the original wording)
`WorkspaceTrust` stores grants in `java.util.prefs` userRoot
(`org/nmox/studio/rack/service/trusted`, one entry per path — the
v1.27.0 8KB-cap fix), and its tests call `clearForTest` against the
REAL userRoot, so a local `mvn verify` deletes the developer's own
trust grants. Bit this session twice: after each verify the dev app's
LSP servers (which are trust-gated since v1.216.0) silently stopped
launching until trust was re-granted. Not a shipping concern (users
don't run our test suite), but a real dev-loop footgun. Fix shape:
point the tests at a scratch prefs node (system-property override in
`WorkspaceTrust`, the OracleKeys `env`-seam idiom). Deferred: touches
a security-sensitive class for a dev-only annoyance; do it as part of
the next trust-surface release, not as a drive-by.

### 70. The Angular CLI's esbuild dev server hangs the JavaFX WebView — CLOSED v1.226.0
ROOT CAUSE FOUND, fixed in the product. JavaFX WebKit sends the RFC
7540 §3.2 cleartext-upgrade probe (`Upgrade: h2c` +
`Connection: Upgrade, HTTP2-Settings`) on every plain-HTTP request;
Angular's esbuild dev server accepts such a connection and never
responds. Proven headlessly: capture the WebView's exact request with
a socket logger, replay it with curl — hangs with the header, 200 in
5 ms without it, and a plain static server answers 200 either way (so
the fault is the dev server's, not the header's). Fix:
`FxAvailability` sets `com.sun.webkit.useHTTP2Loader=false` before any
WebKit class loads (respecting an explicit -D). Costs nothing — h2c
is essentially never accepted in practice and https:// still gets
HTTP/2 via ALPN — and `ng serve` now loads in the Browser, with the
DevTools Angular pane reading its live component tree. Original
entry below.

### 71. The platform CSS parser flags modern color syntax as warnings — LOW, blocked externally; MITIGATED v1.232.0 (stylelint-lsp ships — the "modern linter" half of the future-fixes list is done; the grammar-refresh half remains upstream work)
Measured live (2026-08-02, v1.231.0 gauntlet): `color-mix(in oklch,
tomato 40%, white)` and space-separated `hsl(210 60% 40%)` draw
"Unexpected character(s) … found" warnings from the ide cluster's
css.lib ANTLR grammar, which predates CSS Color 4 and native nesting
era value syntax. Three removal routes were tried and are all blocked
from outside the platform module:
- `CssPreferences.disabledErrorChecks` (the Alt-Enter machinery) was
  pre-seeded with every key the producer can emit (`PARSING`, `LEXING`,
  `AST`) in a live userdir — the warnings persisted; the mechanism is
  inert for these errors in this build.
- `csl.spi.ErrorFilter` unions the outputs of ALL registered factories
  (decompiled `ErrorFilterQuery`): an error kept by the css module's
  own filter survives no matter what ours returns.
- `css.lib.api.ErrorsProvider` is additive-only — it can contribute
  diagnostics, never remove another provider's.
What shipped instead (v1.231.0): the swatch layer moved to the TOP
rack so the modern color literals paint over the warning background —
the color always shows, the warning stays as squiggle + gutter badge.
Honest future fixes: upstream a css.lib grammar refresh to Apache
NetBeans, or ship stylelint as an LSP so a modern linter carries CSS
correctness and users can Alt-Enter the legacy check off per file.

### 70-original. ~~The Angular CLI's esbuild dev server hangs the JavaFX WebView~~ — superseded by 70, CLOSED v1.226.0 (kept for the original wording)
Measured live (2026-08-01, v1.222.0 gauntlet): navigating the in-app
Browser to a running `ng serve` (Angular 18, the esbuild-based
`@angular/build` dev server) starts a load that never commits — the
tab title clears, no error fires, and the previous page stays
rendered. The same dev bundle built with
`ng build --configuration development` and served by a plain static
server (python `http.server`) loads instantly, as do example.com,
Hacker News, and every other server tried; the dev server's GET
response is properly framed (`Content-Length` present), so the
malformed-response theory is out. Something in the esbuild dev
server ↔ JFX WebKit interplay (keep-alive handling is the leading
suspect) needs its own instrumented investigation. Workaround for
Angular DevTools work: build dev, serve static. Affects any workflow
that points the in-app Browser at `ng serve`; SCOPE auto-follow of
HALO's serving URL will hit this too.

## Decided — the v1.192.0 decisions pass (2026-07-27)

The changelog's three longest-standing "deferred with a reason" items
were each converted into a written decision, so nothing in this ledger
is waiting on an answer nobody scheduled:

### Ledger 29 remainder (Kit-action context registration) — CLOSED AS DECIDED
Always-enabled IS the correct behavior, not debt. Every kit acts on the
AIMED project (the rack's `getProjectDir()`), never the window
selection — so selection/focus-keyed enablement would grey a valid
action whenever focus sat in an editor. The runtime "Aim the studio at
a project first" guard is the honest gate at the only moment the answer
is knowable. Decision written at the code site (PwaKitAction, the
family exemplar). Ledger 29 is fully closed.

### Ledger 33 (studios in the `editor` wsmode) — DECIDED: won't-move
The interleaving is the v1.29.0 discovery design working as designed;
a custom wsmode would churn every user's persisted layout for an
aesthetic separation with zero user-visible defects on record across
160+ releases. Re-open only with BOTH a reported user pain and a
layout-migration story.

### Ledger 38/40 (Windows Job Objects) — DECIDED: conditional won't-fix
The guarantees that matter hold on every OS today (Stop leaves zero
orphans; runBounded returns bounded; no shipping path spawns through an
MSYS shell). The JNA/FFM sprint is built only when a trigger fires:
(a) a Windows user reports an orphaned grandchild in the wild, or
(b) the product gains a feature that launches via Git-Bash. Until then
this is a solved-enough boundary, not open work.

Also closed in the same pass: the v1.182.0 "Dele" toolbar-clip
observation — fixed structurally (a 2×2 grid cannot clip; Delete also
gained the tree context menu and the Delete/Backspace keys, which made
the new safe-default confirm on non-empty collections load-bearing).

## Closed — the v1.141.0 debt sprint (2026-07-24)

Three real duplications/reach-ins, each fixed with a test; plus a new
cross-home drift gate. No feature work — debt only.

### The kit write-law lived in three copies — CLOSED (v1.141.0)
`ClassicKit` and `ContractKit` each inlined the never-clobber
`.suggested` logic (the HttpBodies-class debt from v1.124.0). Extracted
to `KitFiles.writeNeverClobber` — the one home every kit generator now
calls; `KitFilesTest` pins the four outcomes, mutation-proven.

### `AnchorDevice` reached into `StellarDevice.toolOnPath` — CLOSED (v1.141.0)
A console borrowing a sibling device's static PATH probe was a reach-in.
The helper moved home to `CommandDevice` (the shared base every console
extends); the cross-device call is gone.

### `ContractKit.Chain.tool` was a dead field — CLOSED (v1.141.0)
Added but never read. Now drives an honest "`<tool>` isn't on your PATH
yet" hint in the wizard's report — the field earns its place.

### Kit and catalog pins drifted freely — GATED (v1.141.0)
The Contract Kit's templates and the learning-catalog spaces carry the
SAME live-proven starters, so the same dependency pins (soroban-sdk,
cosmwasm-std, ink, solana-program, miniscript) lived in two places with
nothing tying them. `KitCatalogParityTest` now fails the build the
moment they disagree — the soroban-sdk "23" pin-rot lesson made
structural. Mutation-proven (kit→26 vs catalog→27 fails loud).

## Closed — every item here was delivered (added v1.76.0, the fourth review)

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

## Closed — every item here was delivered (added v1.102.0, the first editor review)

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

## Closed — every item here was delivered (added v1.106.0, the first core review)

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

## Closed — every item here was delivered (added v1.107.0, the first rack-engine review)

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

**Re-checked 2026-09-15 (v2.158.0), deferral stands.** The only
third-party-shaped devices that exist — the v2.0.0 JSON device format and
the six bundled gallery devices — cannot commit this lie:
`JsonDeviceExtension` raises no gate before `exec` (an LED the −1 exit
contract corrects, and a TRIGGER only after exit), and the host now says
"UNTRUSTED WORKSPACE — EXECUTION REFUSED" on the refused line. A Java SPI
plugin that calls `emitGate` before `exec` would still be exposed, and none
has been written. Same condition, same answer.

## Closed — every item here was delivered (added v1.89.0, the fifth review)

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

### 49. Preview server: no deregister on app exit — BLESSED (residue; cannot outlive the JVM)
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

### 42. Third-party `descriptor()`/`build()` can run at session restore — DECIDED: accepted, with a revisit condition
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

### 4. ~~Hardcoded project templates (ProjectTemplates.java)~~ — CLOSED v1.293.0 (currency pass v2.153.1)
The feature this entry waited for shipped: user templates are data, read from
`~/.nmox/templates.d` (`UserTemplates`), and join the wizard beside the built-ins.
The built-ins stay in code on purpose, because their pins carry gate-enforced
version ceilings. The original entry:

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

### 6. ~~.sass (indented dialect) shares the SCSS grammar~~ — CLOSED v2.20.0 (currency pass v2.153.1)
Indented Sass has its own mime, `text/x-sass`, with the canonical indented
grammar; see the v2.20.0 CHANGELOG entry for what follows the dialect and what
stays out. The original entry:

Approximate highlighting for the indented dialect. The correct fix is a
dedicated indented-sass TextMate grammar (a curated upstream fetch +
scope-mapping pass), not a code change here. Demand has not justified
the grammar-sourcing work.

### 7. Startup: rack UI construction (~200–400ms EDT during restore)
*Numbers superseded (currency pass v2.153.1): the shelf holds 53 built-in devices
now, and v1.38.0 measured the window at 1.4–2.7 s with zero processes spawned
at boot, about 90% of it the module system scanning the cluster. The won't-fix
verdict below still holds; its figures are from v1.26.0.*

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

### 29. The rack IS the context system — not OpenProjects/actionsGlobalContext — CLOSED v1.45.0, remainder DECIDED v1.192.0
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

### 33. All seven studios live in the `editor` mode — DECIDED v1.192.0: won't move (see plan.md)
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
*(v2.154.0: WorkspaceFilePulse became `core.util.FilePulse` in v2.7.0, and
`ArtifactPulse` now drives a FilePulse for `.nmoxweb3.json` instead of copying
its stamp diff, keeping only the artifact-tree diff a single-file pulse cannot
express. `FileWatcher` stays separate on purpose: it is a debounced recursive
tree watcher with skip-dirs, not a file stamp.)*

## Closed — every item here was delivered (added v1.38.1)

### 27. ~~The Breakpoints window never lists DAP breakpoints~~ — CLOSED v2.154.0: it lists them; a filter hid them
**Re-walked 2026-09-14 on the 2.153.0 portable (RELEASE310).** One DAP
breakpoint was seeded through the platform's own persistence
(`debugger.breakpoints.dap` in `config/Services/org-netbeans-modules-debugger-Settings.properties`,
read by lsp-client's `BreakpointsReader`), because a gutter click cannot be
delivered from the background. With the folder AIMED, the window listed
`app.js:3` with its enabled checkbox. With the same breakpoint and the file
opened on its own, the window was empty while the gutter still marked line 3.
The cause is spi-debugger-ui's `BreakpointGroup.createGroups`, which reads
`Breakpoints.fromOpenProjects` (default **true**) and skips a breakpoint whose
`GroupProperties.getProjects()` are not open. The v1.38.1 walk predates the
v1.45.0 OpenProjects bridge, so its project was never "open". Not a platform
defect: the user guide now says what the window shows and names the
**From Opened Projects Only** toggle under *Breakpoint Groups*. The original entry:

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

### 39. ~~Browser debugging: a page's Web Workers sit paused, not undebugged~~ — CLOSED v2.156.0: each worker is a session of its own
**Closed 2026-09-15 with item 25, by the same mechanism.** The platform's
DAP client has a door for exactly this: `DAPDebugger.attachedChildSession`
is a `@JsonRequest` the adapter side sends with a `config` holding
`__jsDebugChildServer` (a port) and `name`; the client dials the port and
starts a NEW debug session with `initialize` + a bare `attach`. `DapProxy`
now answers every `startDebugging` after the first with that request, on
the link the target was raised on, each backed by a one-shot loopback relay
that dials the adapter, translates the bare `attach` into the target's
`launch` (its `__pendingTargetId` configuration) and maps the answer back.
`RealChromeIntegrationTest.shouldDebugWebWorkerAsItsOwnSession` proves it
against real headless Chrome: `app.js` starts `new Worker('worker.js')`,
the offer arrives on the root client, and worker.js:2 hits inside the
worker's session. The original entry follows for the record.

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
(evidence in each carve-out's comment: ProcessSupportTest's disable
since v1.42.0, and the POSIX-only exit halves of NpmRunLaneTest,
DeviceRunsJoinTheStopTest and StopAllReadsStoppedTest).
runBounded still returns bounded (worst case the drain tail waits
2×5s). The real fix is Windows Job Objects via JNA/FFM. **Deferred**:
matters only if rack/SOLDER-style Git-Bash commands run under
runBounded timeouts on Windows; no such path ships today. Documented
in killTree's javadoc so nobody trusts the sweep there.

A second cost, closed rather than deferred: those three fixtures each
spawned a `sleep` and then aborted on the assumption, so on Windows an
unreachable grandchild outlived the method holding its `@TempDir` as a
working directory — Windows will not remove a directory that is a live
process's cwd, and JUnit failed the method with "Failed to close
extension context". An intermittent red on a green sha, one full CI
cycle each time. The fixtures now hold themselves alive on a shell
builtin and spawn nothing, so the abort has nothing to strand; the
kill's blindness is unchanged.

## Closed — every item here was delivered (added v1.37.0)

### 25. ~~One debug session per run: child processes run undebugged~~ — CLOSED v2.156.0: every child process and worker is a session of its own
**Closed 2026-09-15.** The "platform change" this entry waited on already
existed one module over: the RELEASE310 lsp-client's `DAPDebugger` answers
an `attachedChildSession` request by opening a further session on a port.
`DapProxy` keeps the first target spliced flat (unchanged) and offers every
further `startDebugging` — a `child_process.fork`, a `worker_threads`
Worker, a grandchild raised on a child's link — through that door via a
one-shot `ChildRelay`; `DapDebugAction` no longer sets
`autoAttachChildProcesses: false`. `RealJsDebugIntegrationTest.shouldDebugChildProcessesAndWorkersAsSessions`
runs the real adapter over `parent.js` → fork `child.js` → `new Worker(worker.js)`
and sees breakpoints hit at parent.js:3, child.js:2 and worker.js:2, each
in its own session; `DapProxyTest` pins the relay, the grandchild link and
teardown with three mutants by name. Item 39 (browser workers) closed with
it. One honest ceiling (v2.160.0 review): the GRANDCHILD shape — a worker of
a forked child, offered on the child's own relay link — is pinned by
`DapProxyTest` against a fake platform only; both E2Es saw the worker raised
on the root link (the program starts it), so no real `DAPDebugger` has yet
answered an offer arriving on a child session. The original entry follows
for the record.

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

### 18. CommandExecutor exit detection — still unreproduced (the stale-run half was fixed in v2.186.0)
Two hardening ideas from the lifecycle audit: drive exit from
`process.onExit()` with a bounded drain (today a forcibly-killed
process whose pipes linger can delay `onFinished`), and a per-launch
generation counter so a stale run's `onFinished` can't drop the gate of
the run that replaced it. Both are engine-core changes under the
device-contract tests; neither has a reproduced failure in the wild.
Queued behind a reproduction or the next engine sprint.

**The second half was real, and it was not narrow.** The entry queued both ideas
behind "a reproduction or the next engine sprint", and the reproduction came back
positive on the first try: **press a serve device's DEV twice.**

`RackDevice.exec` guarded only the half it owned (`if (running == pending)
running = null;`) and then called `onExit.accept(code)` UNCONDITIONALLY. Because
`exec` ends the previous run with `Handle.kill()` — which TERMs a tree and returns
ASYNCHRONOUSLY — the replaced run's exit lands after `exec` has returned and after
the replacement has raised its gates. The `PendingHandle`'s second-exec-cancels-
first contract (v1.57.0) is honoured, but "cancelled" only stops the SPAWN: the
cancelled run still reports, and the device acts on the report.

**On a serve device the damage is permanent.** `ViteDevice.dev()` raises `serving`
once, right after `launch()` returns, and `onFinished` is the only thing that
lowers it — so a stale exit leaves the gate LOW for the entire life of a dev
server that is up, and with it the `ServingRegistry` entry: the ⇄ chip, ⌘I Live
Servers, VITALS and BEACON targeting. That is v1.93.0's law ("the serving gate
never lies") failing from the other direction, which is the shape the entry
itself predicted.

**The fix is not at the choke point, deliberately.** Swallowing a replaced run's
exit inside `exec` would reverse a shipped decision — `AsyncExecTest.secondExec
CancelsFirstPending` pins that a replaced run still reports, and the frozen Device
SPI relies on it — and would strand chains. So `exec` gained a `RunExit` form that
tells a completing run whether a later launch replaced it; the plain `IntConsumer`
form is untouched. The generation (`RackDevice.launchSeq`) is opened BEFORE the
kill, not after, and that ordering is pinned by a device that fires the replaced
run's exit from INSIDE the kill that replaces it — deterministic, rather than
documented as an equivalent mutant.

**The first half stays open and stays unreproduced**, and the reproduction never
led into it: driving exit from `process.onExit()` with a bounded drain would
SHORTEN this window but could not close it, because the window is created by
`kill()` returning before the process dies.

### 19. Rack polish cluster: undo across presets, trigger bookkeeping — CLOSED (v1.50.0)
See "Closed by v1.50.0" below.

### 21. Platform autoupdate modules ship with no update center — CLOSED (v1.51.0)
See "Closed by v1.51.0" below.

### 23. org.json rides in 8 module copies (~710 KB total) — CLOSED (v1.50.0)
See "Closed by v1.50.0" below.

### 24. ~~i18n: ~450 user-visible strings are hardcoded~~ — CLOSED by the l10n arc, v2.97.0–v2.153.0 (currency pass v2.153.1)
The product speaks fifteen languages; `docs/engineering/l10n-completion.md` records
every covered surface with its gate and every ceiling with its measurement.
Ledger 85 opened the arc. The original entry:

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
  module-internal until an external consumer exists. *(v2.153.1: the first
  external consumer arrived as an agent, not a plugin. The Agent Port (MCP)
  reads servings and runs since v2.54.0 and pushes their changes since v2.84.0,
  read-only. A Java event API for Device SPI plugins still waits for a plugin
  author who needs one.)*
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
- ~~**Tuple/struct ABI parameters**~~ — CLOSED v2.154.0: encode, decode and
  events through a type tree (`AbiType`), a strict square-bracket literal
  (`AbiLiteral`) whose refusals name the component and position, the money
  law inside structs, bounded decode. Walked on Anvil: `place` with a struct
  mined, `orderAt(0)` decoded with field names, `cast call` agreeing. The
  original entry: parsed (functions list fine) but
  refused at encode time with a pointer to `cast`. Build when a real
  project needs it; the encoding is mechanical but the form UX isn't.
- ~~**eth_subscribe websockets**~~ — CLOSED v2.155.0: the JDK's HttpClient
  does ship a WebSocket builder. Watch subscribes to `newHeads` and `logs`
  where the network has a WS endpoint (an explicit `wsUrl` in
  `.nmoxweb3.json`, or a loopback `http(s)` RPC read as `ws(s)` on the same
  port, which covers anvil); remote gateways and secret networks keep polling
  because their WS paths are not guessed. One `WatchReconciler` session owns
  the cursors both lanes share, generation-guarded; a dropped socket falls back
  to the 2 s poller after the last streamed block, logs de-duplicated by
  transaction hash and log index; each message capped at 1 MB. Live-proven
  against anvil 1.8.1 through a proxy cut mid-stream: blocks 2–4 streamed,
  5–6 polled, every block and log exactly once. Remainder: the network dialog
  has no `wsUrl` field (set it in the workspace file). The original entry: the
  Watch pane polls at 2s, honest and simple; the shared HttpClient has no WS.
  Revisit if devnet watching ever feels laggy.
- ~~**Vyper / non-EVM chains (Solana, Move, ink!)**~~ — CLOSED v2.155.0 for
  Vyper: the toolchain now exists (Foundry compiles `.vy` when `vyper` is
  installed, and the artifact tree reads `out/`), so `.vy`/`.vyi` are editor
  citizens — the tintinweb grammar (MIT, sha256-pinned), `#` comments,
  decorator completion, a Navigator outline, a Doctor probe. No Vyper language
  server or project kind: Foundry carries the build. The original entry:
  grammar-only support would mislead without a toolchain behind it. *(v2.153.1:
  the non-EVM half shipped WITH toolchains — STELLAR and ANCHOR on the rack,
  Cairo and Move verticals, and eleven chains in the Contract Kit, v1.130.0–
  v1.153.0.)*
- ~~**slither as a rack lane**~~ — CLOSED v2.155.0: PURITY's LINTER knob gains
  `slither` (appended, position 8; AUTO picks it on a Foundry project). The
  Python-env story is refusal, not installation: without slither on PATH the
  lane greys with the Doctor's own hint and spawns nothing, and slither's
  missing-compiler traceback becomes one sentence. Findings come from slither's
  `--json` report (a file, because one stdout line would pass the pump's line
  cap), read capped at 8 MB, into DiagnosticsBus. The original entry: Doctor
  probes it and hints the install; running it well needs a Python-env story.
  TYPEGUARD's solhint lane covers day-to-day linting.
- **Foundry project template** — `forge init` does it better (pulls
  forge-std, sets remappings); a wizard shelling out to it is a later
  nicety. *(v2.153.1: covered since v1.139.0 — File ▸ Add to Project ▸
  Contract Kit scaffolds a Foundry project with its test and notes. A
  wizard that runs `forge init` itself still does not exist.)*

## Open — deferred deliberately, with reasons (added v1.29.0)

### 10. ~~DB Studio: Mongo cancel is a no-op; cursors read firstBatch only~~ — CLOSED v2.155.0
Cursors follow `getMore` up to the row cap (`MongoCursorPager`), mark the
result truncated when more remained, and release abandoned cursors with
`killCursors`. Cancel is a server-side kill: the 5.11 driver ignores a thread
interrupt mid-read (measured: the first live cancel waited out a 20 s scan),
so each backend carries a unique application name and Cancel ends its own
operations found by `currentOp {$ownOps, appName}` with `killOp`. Proven on a
real `mongo:7` (250 documents paged; a cap of 150 left zero open cursors; a
slow query stopped 38 ms after Cancel with the connection still answering).
An uncapped read is bounded at 1,000,000 documents. The original entry:

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

**Re-checked for RELEASE310 (v2.153.1, 2026-09-14), the bump v2.35.0 made:**
`javap -p` on `org.netbeans.modules.lsp.client.bindings.LanguageClientImpl` in
`org-netbeans-modules-lsp-client-RELEASE310.jar` declares no `registerCapability`,
so lsp4j's default method still throws. The deferral holds; check again on
the next platform bump.

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
