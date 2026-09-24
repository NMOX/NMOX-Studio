# Quickstart: five minutes to your project running

This page gets one of your own projects running inside NMOX Studio. It
covers only what you need for that. [The user guide](user-guide.md) is the
full manual. If you use VS Code, read
[coming from VS Code](coming-from-vscode.md) next.

## 1. Install (one minute)

**macOS, with Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Homebrew asks you to run `brew trust` once for any third-party tap. It
won't ask again when you update.

**macOS, Windows, Linux, without Homebrew:** download the latest release
for your OS from
[the releases page](https://github.com/NMOX/NMOX-Studio/releases/latest):

| OS | File | Then |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | Drag the app to Applications. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | Run the installer. |
| Debian, Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Other Linux | `NMOX-Studio-<version>-linux.tar.gz` | Unpack it and run `bin/nmoxstudio`. |

Every one of these files includes its own Java runtime, so you have
nothing else to install. Only the portable zip needs Java 21 or newer
already on the machine.

On macOS the app is notarized by Apple. The first time you open it, macOS
asks whether to open an app downloaded from the internet: click **Open**.

## 2. Open your project (one minute)

Launch **NMOX Studio**. It opens three tabs: **Welcome**, **Task Rack** and
**Browser**.

To open your project, choose **File ▸ Open Folder…** (⌥⌘O on macOS,
Ctrl+Alt+O on Windows and Linux) and pick its folder. You can also do this
from a terminal:

```bash
cd ~/code/my-app
nmoxstudio --open .
```

If NMOX Studio is already running, that command hands it the folder and
exits.

A folder counts as a project if it has a `package.json`, `Cargo.toml`,
`go.mod`, `pom.xml`, `composer.json`, `pyproject.toml` or one of 57 other
project files. A folder of plain HTML files counts too.

Three things happen when you open a project:

- **Project Studio**, on the left, shows your files.
- The status line, at the bottom, shows your git branch and the number of
  changed files.
- The **Task Rack** is set up for the kind of project it is. A Vite
  project gets a Vite console, a Cargo project gets run, debug and test
  lanes, and so on.

## 3. Run it (one minute)

Press **▶** on the toolbar, or F6. It runs your project the way its tools
run it: the `dev`, `start` or `serve` script from `package.json`, `cargo
run`, `go run`. It uses your project's own package manager: npm, pnpm or yarn,
or bun for a Bun project.

The first time you run anything in a project, NMOX Studio asks whether you
trust the folder. A project you haven't trusted runs none of its own code:
no scripts, builds or tests. Click **Trust Workspace** for your own code.

If your project is a dev server, its address appears on the status line
beside a **⇄** symbol, and the page opens in the **Browser** tab. Edit a
file and save, and the page reloads.

To stop everything that's running, press **■** beside ▶, or ⌥⌘. (Option,
Command and period).

If nothing happens, check the **Output** tab at the bottom. It explains why
the run couldn't start, for example that a tool isn't installed or that
dependencies aren't installed yet, and offers to fix it. **Tools ▸
Environment Doctor…** lists every tool NMOX Studio can use and shows which
are installed.

## 4. Find anything (thirty seconds)

Press **⌘I** (Ctrl+I on Windows and Linux) and type. Quick Search finds
files, menu actions, symbols, rack devices, running servers and commands,
and your `package.json` scripts. Press Enter to open or run the result.

Press **⌘P** to open a file by name.

## 5. Test it (thirty seconds)

Press **⌃F6** (Ctrl+F6) to run your project's tests. To see every test in
the project before you run any, open the **Tests** window with ⌥⌘2.

## If you have no project handy

- **File ▸ New Project…** creates a real project from a template (Angular,
  Vue, Svelte, React with Vite, plain JavaScript, PHP, Phoenix and more).
  It creates the files, sets up git and installs dependencies.
- **File ▸ New Learning Space…** opens a guided tutorial. *Your First Web
  Page* comes first in the list.

## Where to go next

- **[The Task Rack](user-guide.md#4-the-task-rack)**. Every tool you run is
  a device on the rack, and cables between devices chain them: for
  example, run the tests whenever the build passes.
- **[The editor](user-guide.md#5-the-editor)**. Includes Emmet, colour
  swatches, breakpoint debugging for Node and Chrome, and Angular
  templates.
- **[The studios](user-guide.md#6-the-studios)**. The API, DB, Contract
  and Block studios, and the Task Board.
- **[The glossary](glossary.md)** explains the product's own words: rack,
  patch, jack, lane, aim, KVASIR.
