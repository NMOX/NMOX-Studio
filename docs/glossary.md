# Glossary

The words NMOX Studio uses that another IDE doesn't, plus the NetBeans
terms that show through. Each entry says what the word means here and where
to read more.

## The rack

**Task Rack** (⌘9) — The window where your tools run. Each task (install, build, test, serve,
lint, deploy) is a *device* mounted in a rack, like a studio's hardware.
[User guide §4](user-guide.md#4-the-task-rack).

**Device** — One tool on the rack, for example VELOCITY (Vite), VERITAS (tests) or
PURITY (lint). A device has a front panel (the *faceplate*) with knobs,
buttons, lights and a small display, and a back panel with *jacks*. There
are 53 built-in devices, listed in [the device reference](devices.md).
You can add your own as a JSON file in `~/.nmox/devices.d/`
([device files](device-files.md)).

**Faceplate** — A device's front panel. Press **Tab** in the rack to flip it and see the
back panel.

**Jack** — A socket on a device's back panel. Output jacks send signals and input
jacks receive them. There are three kinds of signal:
- A **trigger** is one pulse: "the build finished", "OK", "FAIL".
- A **gate** stays on or off: "the server is up".
- **Data** carries text, such as a URL or a line of output.

**Cable** — A connection from an output jack to an input jack. Connect a build's OK
jack to the test runner's RUN jack, and the tests run whenever a build
passes. To connect two jacks, drag from one to the other, or click one
and then the other.

**Patch** — A whole rack: its devices, their settings and their cables. Saved beside
the project as `.nmoxrack.json`, so it's worth committing.

**Preset** — A ready-made patch you can load from the rack's Presets menu, for example
*Ship Gate* or *E2E Loop*. Save any patch into `~/.nmox/presets.d/` and
it appears in the menu too.

**Starter rack** — The patch a project gets the first time you open it, chosen from the
kind of project: a Vite console for a Vite app, run, debug and test
lanes for a Cargo crate, and so on.

**Lane** — Two meanings, both about running things:
- A **pipeline**: a chain of devices joined by cables, such as install →
  build → test. Several lanes can run side by side, and QUORUM waits for
  all of them to finish.
- A device's **AUTO lane**: the command it chooses for this project. On
  AUTO, the test device runs `npm test` in a Node project and
  `cargo test` in a Rust one.

**Share… / Import…** — Save a rack to a file for someone else, or load one from them. Before
anything is mounted, Import shows everything the file contains, and
every device arrives switched off.

**Rack Gallery** — **Tools ▸ Rack Gallery…** lists community racks, presets, starter racks
and your own saved racks. Each entry shows what it's for and which tools
it needs. [Community racks](racks.md).

## Projects and running

**Aim** / **aimed project** — The project the IDE is currently working on. Opening a project aims
it: the rack, the studios, the status line and Run all follow the aimed
project. Aiming a different one switches them all, and anything still
running is stopped first, after asking.

**Workspace Trust** — The question NMOX Studio asks before it first runs a project's own code
(scripts, builds, tests). If you answer
**Keep Safe**, nothing from the project runs. Your answer is remembered
per folder.

**▶ and ■** — Run and Stop on the toolbar. ▶ (F6) runs the aimed project. ■ (⌥⌘.)
stops every command NMOX Studio started for you.

**⇄ chip** / **serving** — When something you run prints a local address, such as
`http://localhost:5173/`, the address appears on the status line after
a ⇄ symbol. That running server is a *serving*. Click the address to open
it in the Browser. Quick Search lists servings under *Live Servers*.

**Experiment** — A throwaway project made from a template in `~/.nmox/experiments`. Its
dependencies are already installed and it's already trusted. **Promote**
it to keep it, or **Discard** it. **File ▸ New Experiment…**.

**Learning space** — A guided tutorial for a language or framework. It creates a real
project, a walkthrough and a rack set up with a live REPL, and **File ▸
Check My Work** checks your exercises. There are 93. **File ▸ New
Learning Space…**.

**PREFLIGHT** — The ship-check device. It runs the checks your project defines (lint,
types, tests, build) as one pass or fail.

**First Steps** — The checklist on the Welcome tab. Steps tick themselves off as you do
them and never untick.

## The windows

**Studio** — A window with its own tool for one kind of work. There are
five: **API Studio** (⌥⌘8), **DB Studio** (⌥⌘7), **Contract Studio** (⌥⌘6,
smart contracts), **Block Studio** (⌥⌘5, web components built from blocks)
and the **Infra Designer** (⌥⌘9, cloud infrastructure). Each saves its work
beside the project in a `.nmox*.json` file. **Project Studio** shares the
name but is the file tree and project templates.

**Workbench** (⌥⌘0) — The home base: what's running, what's open, and your recent projects and
files.

**Task Board** (⌥⌘1) — A kanban board for each project, with sprints and a time clock, saved as
`.nmoxtasks.json`.

**Welcome** — The start tab: actions to begin with, recent projects, the *TOOLING*
column that lists every window, and First Steps.

## AI

**KVASIR** — The name for NMOX Studio's AI features: Ask, Edit, Complete, Explain,
Draft Commit Message. It works with Claude, ChatGPT or Gemini using your
own API key, which is stored in the OS keychain. Every feature asks for
your consent once and names exactly what it will send. Nothing is sent
until you use a feature. Earlier releases called it ORACLE.

**Agent Port** — **Tools ▸ Agent Port (MCP)…** gives an AI agent running on your
machine, such as a coding assistant, read-only access to the IDE's state
over MCP: open files, diagnostics, runs, symbols. It is read-only by
design and listens only on your own machine.
[Tutorial](tutorials/agent-port.md).

## NetBeans terms you may see

NMOX Studio is built on the NetBeans Platform, and a few of its words show
through.

**Module** / **NBM** — A part of the application. An *NBM* is the file a module is delivered
in. **Tools ▸ Plugins** installs updates module by module.

**Update center** — Where **Tools ▸ Plugins ▸ Updates** gets new versions of NMOX Studio's
modules. It reads a catalog published with each GitHub release.

**userdir** — The folder where NMOX Studio keeps its settings, window layout, logs and
installed updates. To find it, open **Help ▸ About**. Its log is at
`var/log/messages.log`. To start with fresh settings, launch with
`--userdir <an empty folder>`.

**Options** / **Settings…** — The preferences dialog. It's **Tools ▸ Options** on Windows and Linux,
and **NMOX Studio ▸ Settings…** on macOS.

**Action Items** — The window listing problems found in the project, including the rack's
lint and type-check results. Click a problem to go to that line.
