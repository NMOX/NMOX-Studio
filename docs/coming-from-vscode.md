# Coming from VS Code

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
| Go to definition | F12 | **⌘B** | F12 | **Ctrl+B** |
| Rename a symbol | F2 | **⌃R** | F2 | **Ctrl+R** |
| Go to line | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Toggle line comment | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Show suggestions | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Add the next occurrence to the selection | ⌘D | **⌘J** | Ctrl+D | **Ctrl+J** |
| Select every occurrence | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Add a cursor above / below | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Move the line up / down | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Copy the line down | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Delete the line | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Format the document | ⇧⌥F | **⌃⇧F** | Shift+Alt+F | **Alt+Shift+F** |
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


## From the terminal

`code .` is `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox            # just start the IDE
```

It returns at once, and a second `nmox` hands its folder to the IDE that
is already running. Homebrew, the Windows installer (*Add "nmox" to
PATH*) and the Linux packages put it on your PATH; for a DMG install, the
[user guide](user-guide.md#2-first-launch) shows the one-line link.

## Where each VS Code idea lives

| In VS Code | In NMOX Studio |
|---|---|
| **Explorer** | **Project Studio** (⇧⌘E) — the file tree, templates, and the project's `package.json` editor. The **Workbench** (⌥⌘0) is the home base: open files, recent files, recent projects, and everything running. |
| **Command Palette** | **Quick Search** (⇧⌘P or ⌘I) — actions, files, recent projects, rack devices, live servers, API Studio requests, symbols. |
| **Extensions** | **Tools ▸ Plugins** installs and updates modules, NMOX's own updates included. Much of what an extension adds in VS Code is a **rack device** here — and you can write one as a JSON file in `~/.nmox/devices.d` ([device files](device-files.md)). |
| **`tasks.json`** | Your project's own scripts, run the way they are written: the toolbar's Run / Build / Test (F6, F11, ⌃F6), **Run Script** on a `package.json` scripts line, the **NPM Explorer**, and the **Task Rack** (⌘9), where tasks are devices you wire together. |
| **`launch.json`** | **Debug File** (⇧⌘F5) and the toolbar's debug button work out what to launch from the project itself — the `start` script's entry, `main`, `index.js` — and the **INSPECTOR** rack device launches a debugger as a step in a pipeline. |
| **Integrated terminal** | The **Terminal** window (⌃\`): the first press starts a shell in the project folder, later presses bring it back. |
| **`settings.json`** | Tools ▸ Options (on macOS, NMOX Studio ▸ Settings…). Your project's `.editorconfig` is honoured on save. |
| **Problems panel** | **Action Items** (⌘6): lint and type findings from the rack's PURITY and TYPEGUARD devices, next to the squiggles in the editor. |
| **Outline** | The **Navigator** (⌘7). |
| **Source Control** | The git chip on the status line (branch and changes, one click to history) and the **Team** menu. |
| **Workspace Trust** | The same idea, enforced before anything a repository chose is run: opening a cloned project runs nothing until you trust it. |
| **Keyboard Shortcuts editor** | Tools ▸ Options ▸ Keymap (on macOS, Settings… ▸ Keymap) — edit any chord, or switch the whole profile to Eclipse, Emacs or IntelliJ. |

## What is honestly different

- **⌘D is not multi-cursor here.** The same gesture is **⌘J** (Ctrl+J);
  ⌘D itself is unbound. Rebind it under Keymap if your fingers insist.
- **⌃\` opens and focuses the Terminal; it does not hide it.** And while
  the Terminal has focus, the keys belong to your shell, so the second
  press reaches the shell rather than taking you back to the editor.
- **`.vscode/tasks.json` and `launch.json` are not read.** A task is a
  command a repository chose, and reading one deserves its own design
  around Workspace Trust; until then, the project's own scripts and the
  debug entry rules above do that job.
- **There is no "VS Code" keymap profile.** The chords above ride the
  default profile and the other four. One deliberate exception: in the
  **Eclipse** profile ⇧⌘E stays Eclipse's own *Switch to Editor*, and
  inside the editor ⇧⌘P and ⇧⌘X keep Eclipse's meanings (matching
  brace, upper case) — someone who picked Eclipse expects Eclipse.
- **On Linux, Ctrl+\` used to open the window switcher** — the platform's
  fallback for desktops (KDE) that grab Ctrl+Tab. It opens the Terminal
  now; the switcher is still on Ctrl+Tab.
- **The Ctrl+Alt chords can collide with AltGr.** On Windows, keyboard
  layouts that type characters with AltGr (Polish, for one) send
  Ctrl+Alt for it. If Ctrl+Alt+P or Ctrl+Alt+K types a character for you,
  move *Switch Project* or the experiment chords under Keymap.
- **VS Code extensions do not install here.** Language intelligence comes
  from the language servers NMOX knows (the Environment Doctor lists
  what is missing and how to install it), from the editor's own grammars,
  and from plugins built for the NetBeans Platform.
