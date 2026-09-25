# Coming from VS Code

<!-- languages -->
**English** · [Español](coming-from-vscode.es.md) · [Français](coming-from-vscode.fr.md) · [Deutsch](coming-from-vscode.de.md) · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · [Polski](coming-from-vscode.pl.md) · [Português (Brasil)](coming-from-vscode.pt.md) · [Bahasa Indonesia](coming-from-vscode.id.md) · [Filipino](coming-from-vscode.tl.md) · [Tiếng Việt](coming-from-vscode.vi.md) · [简体中文](coming-from-vscode.zh.md) · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

Your hands already know where things are. This page is the map from
those habits to NMOX Studio: the chords first, then where each VS Code
idea lives here, then what is honestly different.

The first four chords a VS Code user presses do what they expect:
**⇧⌘P** opens the command palette, **⇧⌘E** the file tree, **⇧⌘X** the
plugins, and **⌃\`** the terminal. They are registered in all five keymap
profiles the platform ships, and a build gate resolves each one through
the assembled keymap on macOS, Windows and Linux so nothing else fires
in its place.

## The chords

macOS columns use the menu-bar glyphs (⌃ Control, ⌥ Option, ⇧ Shift,
⌘ Command); the Windows and Linux columns are the same chord on a PC
keyboard.

| You want | VS Code, macOS | NMOX, macOS | VS Code, Win/Linux | NMOX, Win/Linux |
|---|---|---|---|---|
| Command palette | ⇧⌘P | **⇧⌘P** (or ⌘I) — Quick Search | Ctrl+Shift+P | **Ctrl+Shift+P** (or Ctrl+I) |
| Open a file by name | ⌘P | **⌘P** — Go to File | Ctrl+P | **Ctrl+P** |
| The file tree | ⇧⌘E | **⇧⌘E** — Project Studio | Ctrl+Shift+E | **Ctrl+Shift+E** |
| Extensions | ⇧⌘X | **⇧⌘X** — Tools ▸ Plugins | Ctrl+Shift+X | **Ctrl+Shift+X** |
| The terminal, in the project folder | ⌃\` | **⌃\`** | Ctrl+\` | **Ctrl+\`** |
| Open a recent project | ⌃R | **⌥⌘P** — Switch Project… | Ctrl+R | **Ctrl+Alt+P** |
| Go to a symbol in the project | ⌘T | **⌥⇧⌘O** | Ctrl+T | **Ctrl+Alt+Shift+O** |
| Go to definition | F12 | **F12** or ⌘B | F12 | **F12** or Ctrl+B |
| Find references | ⇧F12 | **⇧F12** — Find Usages | Shift+F12 | **Shift+F12** |
| Rename a symbol | F2 | **F2** or ⌃R | F2 | **F2** or Ctrl+R |
| Quick fix | ⌘. | **⌘.** or ⌃↩ | Ctrl+. | **Alt+Enter** |
| Go to line | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Go back / forward | ⌃- / ⌃⇧- | **⌃- / ⌃⇧-** | Alt+← / Alt+→ | **Alt+← / Alt+→** |
| Toggle line comment | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Show suggestions | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Add the next occurrence to the selection | ⌘D | **⌘D** or ⌘J | Ctrl+D | **Ctrl+D** or Ctrl+J |
| Select every occurrence | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Add a cursor above / below | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Move the line up / down | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Copy the line down | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Delete the line | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Indent the line | ⌘] | **⌘]** | Ctrl+] | **Alt+Shift+→** |
| Replace | ⌥⌘F | **⌥⌘F** or ⌘R | Ctrl+H | **Ctrl+H** |
| Format the document | ⇧⌥F | **⇧⌥F** or ⌃⇧F | Shift+Alt+F | **Alt+Shift+F** |
| Close the editor tab | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| The Problems panel | ⇧⌘M | **⌘6** — Action Items (⇧⌘M toggles a bookmark here) | Ctrl+Shift+M | **Ctrl+6** |
| Toggle a breakpoint | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| Start debugging | F5 | **⇧⌘F5** — Debug File | F5 | **Ctrl+Shift+F5** |
| Run without debugging | ⌃F5 | **F6** — Run Project | Ctrl+F5 | **F6** |
| Settings | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | Tools ▸ Options (no chord) |

Every NMOX chord in the table was read out of the shipped keymap, not
remembered (⌘, is the macOS app menu's own). A few things the table
cannot say in a cell:

- **F5 is taken while debugging.** Here it means *Continue*, the way it
  does in every NetBeans-family IDE, so a debug run starts from
  **⇧⌘F5** (Ctrl+Shift+F5) and resumes from F5.
- **⌃R is Rename here**, which is why *Switch Project* lives on ⌥⌘P
  instead of VS Code's Open Recent chord. Rename works where the
  language behind the file supports it.
- **Ctrl+, on Windows and Linux** moves back through your edit history,
  as it always has in NetBeans; the settings live under
  Tools ▸ Options (on macOS the app menu's **Settings…**, ⌘,).

**Help ▸ Keyboard Shortcuts…** lists every NMOX chord in your active
keymap, the four VS Code chords included, read from the running keymap so it cannot
drift from what the keys do.

### Every editing chord, measured

The chords a VS Code user's hands reach for while editing, each looked
up in the shipped keymap of the default profile on macOS. Where VS Code's
chord was free here it now does what VS Code does (the rows that say
**The same:**); where it already meant something NetBeans users rely on,
it keeps that meaning and the row says where VS Code's action lives.

| VS Code, macOS | VS Code does | In NMOX Studio |
|---|---|---|
| F12 | Go to Definition | **The same:** Go to Declaration, as ⌘B does |
| ⇧F12 | Go to References | **The same:** Find Usages, as ⌃F7 does |
| F2 | Rename Symbol | **The same:** Rename, as ⌃R does |
| ⌘. | Quick Fix | **The same:** the fixes for the line, as ⌃↩ shows them |
| ⌥↑ / ⌥↓ | Move Line Up / Down | Previous / next marked occurrence; moving the line is ⌃⇧↑ / ⌃⇧↓ |
| ⇧⌥↑ / ⇧⌥↓ | Copy Line Up / Down | The same, as it always was |
| ⇧⌘K | Delete Line | Next Matching Word (completes the word from the file); deleting the line is ⌘E |
| ⌘L | Expand Line Selection | Select Identifier; selecting the line has no chord |
| ⇧⌘L | Select All Occurrences | Paste as Lines in the editor; selecting every occurrence is ⌃⇧⌘J |
| ⌘/ | Toggle Line Comment | The same, as it always was |
| ⇧⌥A | Toggle Block Comment | Nothing: there is no separate block-comment action, and ⌘/ toggles the comment |
| ⌘] | Indent Line | **The same:** Shift Line Right |
| ⌘[ | Outdent Line | Match Brace, as it always was; outdenting is ⇧Tab or ⌃⇧← |
| ⌘B | Toggle Sidebar | Go to Declaration; ⇧⌘↩ shows only the editor, ⇧Esc maximizes the window you are in |
| ⌘J | Toggle Panel | Adds the next occurrence in the editor (as ⌘D does); the Output window is ⌘4 |
| ⌘\ | Split Editor | Show Code Completion Popup in the editor; splitting the editor is ⌃⇧⌘V |
| ⇧⌘T | Reopen Closed Editor | The same, as it always was: Open Recent File's chord reopens the last closed file |
| ⌃- / ⌃⇧- | Go Back / Go Forward | **The same:** Back and Forward through where you have been editing, as ⌃← / ⌃→ (chords macOS usually keeps for switching desktops) |
| ⌘G / ⇧⌘G | Find Next / Previous | The same, as it always was |
| ⌥⌘F | Replace | **The same:** Replace, as ⌘R does |
| ⇧⌘F | Find in Files | The same, as it always was: Find in Projects |
| ⇧⌘O | Go to Symbol in Editor | Open Project; the file's symbols are in the Navigator (⌘7) |
| ⌘T | Go to Symbol in Workspace | Transpose Letters in the editor; the project's symbols are ⌥⇧⌘O |
| ⌃G | Go to Line | The same, as it always was |
| ⌘K ⌘S | Keyboard Shortcuts | ⌘K is Previous Matching Word; the sheet is **Help ▸ Keyboard Shortcuts…** |
| ⌘, | Settings | The same, as it always was: NMOX Studio ▸ Settings… |
| ⇧⌥F | Format Document | **The same:** Format, as ⌃⇧F does |

The **The same:** chords ride every keymap profile that leaves them
free, and a profile that gives one of them its own meaning keeps it:
F12 in the Eclipse, Emacs and NetBeans 5.5 profiles, F2 in every profile
but the default, ⇧F12 in Emacs and NetBeans 5.5, ⌃- and ⌃⇧- in Emacs
and IntelliJ, ⇧⌥F in IntelliJ. On Windows and Linux F12, ⇧F12 and F2
work the same way; VS Code's other chords are different there, and the
table above gives both.


## From the terminal

`code .` is `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

It returns at once, and a second `nmox` hands its folder to the IDE that
is already running. A column (`src/app.ts:42:7`) is accepted and the
editor opens at the start of the line; a name that is not there is
refused on the terminal instead of starting anything. `-r` is accepted,
`-n` opens in NMOX Studio's single window (there is no second one), and
`-a` and `-v` are refused by name.

`-w` (`--wait`) opens a file and waits until you close its tab, and `-d`
(`--diff`) compares two files side by side, so NMOX Studio can be git's
editor, difftool and mergetool, the way `code --wait` is:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

`git commit` then opens the message in the IDE; save it and close the tab,
and git carries on. Quitting the IDE while a file is still open also hands
it back, with whatever was saved.
`git mergetool` opens each conflicted file the same way. Where VS Code puts
*Accept Current Change | Accept Incoming Change | Accept Both Changes* above
a conflict, NMOX Studio tints the two sides and puts a warning on the
`<<<<<<<` line; the bulb in the gutter, or Quick Fix with the caret on that
line (⌘. on a Mac, Alt+Enter elsewhere), offers the same three, each one
undoable edit. Save,
close the tab, and git moves to the next file. The tints and the three
choices are there in any file with conflict markers, with or without
`git mergetool`.
**Team ▸ Use NMOX Studio with Git…** sets the same lines for you,
after showing what each one is set to now.
Homebrew, the Windows installer (*Add "nmox" to PATH*) and the Linux
packages put it on your PATH; for a DMG install, the [user guide](user-guide.md#2-first-launch)
shows the one-line link.

## Where each VS Code idea lives

| In VS Code | In NMOX Studio |
|---|---|
| **Explorer** | **Project Studio** (⇧⌘E) — the file tree (right-click a file for Copy Path, Copy Relative Path and Reveal in Finder), templates, and the project's `package.json` editor. The **Workbench** (⌥⌘0) is the home base: open files, recent files, recent projects, and everything running. |
| **Command Palette** | **Quick Search** (⇧⌘P or ⌘I) — actions, files, recent projects, rack devices, live servers, API Studio requests, symbols. VS Code's own command names work too: *Format Document*, *Toggle Terminal*, *Git: Commit* or *Open Settings* lists the action that does the same thing here, under **VS Code commands**, with its own name and chord. |
| **Extensions** | **Tools ▸ Plugins** installs and updates modules, NMOX's own updates included. Much of what an extension adds in VS Code is a **rack device** here — and you can write one as a JSON file in `~/.nmox/devices.d` ([device files](device-files.md)). |
| **`tasks.json`** | Your repository's `.vscode/tasks.json` is read: type a task's name into Quick Search (⇧⌘P or ⌘I) and Enter on *Run task: build — make all* runs it, with Workspace Trust asking first on a project you have not trusted, its output in the Output window and the toolbar ■ to stop it. Beside it, the project's own scripts run the way they are written: the toolbar's Run / Build / Test (F6, F11, ⌃F6), **Run Script** on a `package.json` scripts line, the **NPM Explorer**, and the **Task Rack** (⌘9), where tasks are devices you wire together. |
| **`launch.json`** | Your repository's `.vscode/launch.json` is read: type a configuration's name into Quick Search (⇧⌘P or ⌘I) and Enter on *Debug: Launch Program — ${workspaceFolder}/server.js* starts the breakpoint debugger on that program, with Workspace Trust asking first. Node (`node`, `pwa-node`) and Python (`python`, `debugpy`) configurations debug their `program` in their `cwd`, with their `args` and `env`; Chrome (`chrome`, `pwa-chrome`) configurations open their `url` (or `file`) with their `webRoot`. Without a `launch.json`, **Debug File** (⇧⌘F5) and the toolbar's debug button work out what to launch from the project itself — the `start` script's entry, `main`, `index.js` — and the **INSPECTOR** rack device launches a debugger as a step in a pipeline. |
| **Integrated terminal** | The **Terminal** window (⌃\`): the first press starts a shell in the project folder, later presses bring it back. |
| **`settings.json`** | Tools ▸ Options (on macOS, NMOX Studio ▸ Settings…). A repository's `.vscode/settings.json` is read too: `editor.tabSize`, `editor.insertSpaces` and `editor.indentSize` set its indentation as you type, `files.trimTrailingWhitespace` and `files.insertFinalNewline` (when `true`) apply when you save, and a language block such as `"[typescript]"` overrides them for its language. Where the repository also has an `.editorconfig`, the `.editorconfig` wins wherever both speak. |
| **Problems panel** | **Action Items** (⌘6), or click the **✕ ⚠** count on the status line: the language servers' errors and warnings, and the lint and type findings from the rack's PURITY and TYPEGUARD devices. As in VS Code, some servers report only on the files you have open; gopls reports on the whole package. |
| **Search view** (`search.useIgnoreFiles`) | **Find in Projects** (⇧⌘F). As in VS Code, it skips what the repository's `.gitignore` files and `.git/info/exclude` ignore, so `node_modules` and `dist/` stay out of the results when the `.gitignore` lists them; outside a repository it skips `node_modules`, `dist`, `build` and the other build folders by name. Tick **Search in Generated Sources** in its dialog to search them too. Your global git excludes file is not read. |
| **Outline** | The **Navigator** (⌘7). |
| **Source Control** | The git chip on the status line (branch and changes, one click to history) and the **Team** menu. |
| **Workspace Trust** | The same idea, enforced before anything a repository chose is run: opening a cloned project runs nothing until you trust it. |
| **Keyboard Shortcuts editor** | Tools ▸ Options ▸ Keymap (on macOS, Settings… ▸ Keymap) — edit any chord, or switch the whole profile to Eclipse, Emacs or IntelliJ. |

The first time you open a repository that carries `.vscode/tasks.json`,
`launch.json` or `settings.json`, a notice says what was found and where
it lives; click it for Quick Search. It says so once per project.

## What is honestly different

- **⌘D adds the next occurrence in the default keymap, not in every
  profile.** The Eclipse profile keeps ⌘D as Eclipse's *Delete Line*, the
  NetBeans 5.5 profile as *Shift Line Left* and the Emacs profile as *kill
  word* (and Ctrl+D as *delete character* on Windows and Linux); the
  IntelliJ profile has ⌘D on macOS and keeps Ctrl+D as *Duplicate Line*
  on Windows and Linux. The gesture's other chord differs by profile too:
  ⌘J (Ctrl+J) in the default, ⌃J (Alt+J) in Eclipse and IntelliJ, and none
  in Emacs and NetBeans 5.5, where Keymap can give it one.
- **⌃\` opens and focuses the Terminal; it does not hide it.** And while
  the Terminal has focus, the keys belong to your shell, so the second
  press reaches the shell rather than taking you back to the editor.
- **`launch.json` is read, and what the debugger cannot honour is
  refused.** The debugger here passes a program, its working folder, its
  `args` (a list of strings) and its `env` (strings added to the
  inherited environment), so a configuration that sets `envFile`,
  `runtimeExecutable`, `runtimeArgs`, `preLaunchTask` or any other field
  it has not been taught is listed but not started: Enter names the
  fields on the status line. Starting the program without them would
  debug something other than what the file says. `args` written as one
  string (VS Code hands that to a shell) and an `env` value of `null`
  (which unsets a variable) are refused the same way, and so are
  `"request": "attach"`, a `compounds` entry, a type with no adapter
  here (`go`, `msedge`, `cppdbg` and the rest), a value only VS Code can
  supply (`${file}`, `${input:…}`), and a path outside the project.
  Fields that only shape what the debugger shows — `skipFiles`,
  `outFiles`, `sourceMaps`, `console`, `justMyCode`, `presentation` — are
  accepted and not applied; the program's output goes to the Output
  window.
- **`tasks.json` is read, and what cannot run as written is refused.**
  A task that uses a value only VS Code can supply (`${input:…}`,
  `${file}`, `${config:…}`, `${command:…}`) or that `dependsOn` another
  task is listed but not run:
  Enter says which variable or which task on the status line. Running it
  with the value left blank, or without the task it depends on, would run
  something other than what the file says. So would a task type an
  extension provides (`gulp`, `typescript`), and a working folder outside
  the project.
- **A `"type": "shell"` task runs in the shell VS Code would use.** On
  macOS and Linux that is your `$SHELL` with `-c` (a macOS zsh, bash or
  fish starts as a login shell, `-l`, as VS Code's default profiles do);
  on Windows it is PowerShell, `pwsh` when installed. `options.shell`
  is honoured the VS Code way: name an `executable` and it runs with
  exactly the `args` you give, so a bash needs `"args": ["-c"]`. On
  Windows only PowerShell (args ending in `-Command`) and `cmd.exe`
  (args ending in `/c`) are run; any other shell there is refused by
  name rather than handed a command line quoted by guesswork.
- **There is no "VS Code" keymap profile.** The chords above ride the
  default profile and the other four. One deliberate exception: in the
  **Eclipse** profile ⇧⌘E stays Eclipse's own *Switch to Editor*, and
  inside the editor ⇧⌘P and ⇧⌘X keep Eclipse's meanings (matching
  brace, upper case) — someone who picked Eclipse expects Eclipse.
- **On Linux, Ctrl+\` opens the Terminal, not a window switcher.** The
  switcher is on Ctrl+Tab. On a desktop that takes Ctrl+Tab for itself
  (KDE, for one), **Window ▸ Documents…** lists the open files instead.
- **The Ctrl+Alt chords can collide with AltGr.** On Windows, keyboard
  layouts that type characters with AltGr (Polish, for one) send
  Ctrl+Alt for it. If Ctrl+Alt+P or Ctrl+Alt+K types a character for you,
  move *Switch Project* or the experiment chords under Keymap.
- **VS Code extensions do not install here.** Language intelligence comes
  from the language servers NMOX knows (the Environment Doctor lists
  what is missing and how to install it), from the editor's own grammars,
  and from plugins built for the NetBeans Platform.
