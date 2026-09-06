# The Kitchen Sink

*Every surface of NMOX Studio, exercised in one sitting.*

This is the long tour. One session, twenty-five stations, touching all
of it: the polyglot editor, the task rack, the seven studios, the
in-app browser with its source-aware DevTools, the kits, the learning
spaces, the AI surfaces, and the ship gates. Each station says what to
**do** and what you should **see** — every claim here was proven live
in the shipped app before it was written down. Where the product
refuses to do something, that refusal is deliberate, and this tutorial
points at it proudly.

<!-- contents -->
**Contents**

- [1. First launch and the Doctor](#1-first-launch-and-the-doctor)
- [2. A project in one minute](#2-a-project-in-one-minute)
- [3. The Workbench (⌥⌘0)](#3-the-workbench-0)
- [4. The editor, quickly](#4-the-editor-quickly)
- [5. The Task Rack](#5-the-task-rack)
- [6. The programmable rack](#6-the-programmable-rack)
- [7. Quick Search (⌘I)](#7-quick-search-i)
- [8. The Browser (⌥⌘4), source-aware](#8-the-browser-4-source-aware)
- [9. API Studio (⌥⌘8)](#9-api-studio-8)
- [10. DB Studio (⌥⌘7)](#10-db-studio-7)
- [11. Contract Studio (⌥⌘6) and the Contract Kit](#11-contract-studio-6-and-the-contract-kit)
- [12. Block Studio (⌥⌘5)](#12-block-studio-5)
- [13. Infra Designer (⌥⌘9)](#13-infra-designer-9)
- [14. The Task Board (⌥⌘1)](#14-the-task-board-1)
- [15. IRC (⌥⌘3)](#15-irc-3)
- [16. Debugging](#16-debugging)
- [17. The kits](#17-the-kits)
- [18. Learning Spaces](#18-learning-spaces)
- [19. Break it, check it, export it — the learning loop](#19-break-it-check-it-export-it--the-learning-loop)
- [20. KVASIR — the AI surfaces](#20-kvasir--the-ai-surfaces)
- [21. Angular, first-class](#21-angular-first-class)
- [22. The product's own website](#22-the-products-own-website)
- [23. Ship it](#23-ship-it)
- [24. Point an agent at it](#24-point-an-agent-at-it)
- [25. Show it to a room](#25-show-it-to-a-room)
- [The refusals are features](#the-refusals-are-features)
- [Where everything lives](#where-everything-lives)
<!-- /contents -->

You need: NMOX Studio installed (`brew install --cask
nmox/nmox-studio/nmox-studio`, or an installer from the releases
page — every installer bundles its own Java runtime), and Node.js on
your PATH for the web stations. Everything else is optional; the
product degrades honestly when a tool is missing.

A useful map before you start — the window chords:

| Chord | Window |
|---|---|
| ⌥⌘1 | Task Board |
| ⌥⌘3 | IRC |
| ⌥⌘4 | Browser |
| ⌥⌘5 | Block Studio |
| ⌥⌘6 | Contract Studio |
| ⌥⌘7 | DB Studio |
| ⌥⌘8 | API Studio |
| ⌥⌘9 | Infra Designer |
| ⌥⌘0 | Workbench |
| ⌘I | Quick Search — reaches everything |

---

## 1. First launch and the Doctor

**Do:** Launch the app. Then Tools ▸ **Environment Doctor**.

**See:** The suite tabs are already open — Workbench, Task Rack, DB
Studio, Contract Studio, Infra Designer, API Studio, Browser, IRC,
Tasks, Docker Panel — and the window paints in a couple of seconds
with **zero processes spawned at boot** (hidden tabs defer their work
to first show; that is a tested law, not a hope). A fresh install aims
at a created-for-you `~/NMOX` workspace, never at your home
directory. The Doctor live-probes your toolchains — node, npm, git,
docker, sass, cargo, go, deno, and a few dozen more — with real
versions and per-OS install hints. Your own probes can join it: drop a
JSON file in `~/.nmox/doctor.d`.

## 2. A project in one minute

**Do:** File ▸ New Project… ▸ **Vanilla Web**. Accept the defaults.
Then press the project's **Run**.

**See:** The wizard scaffolds into `~/NMOX`, the project opens, and
Run serves it — without pinning a port: if 8080 is busy the server
scans upward and the announce reads the port the server's own banner
names. A **⇄ serving** chip appears on the status line, and the served
page opens in the in-app Browser. Stop it with the **■** to the right of
Debug on the toolbar — ⌥⌘. from anywhere — (or Run ▸ Stop Build/Run, which offers Repeat
afterwards); a device's STOP reads STOPPED whatever the process's exit
code. The same ■ stops a script you double-clicked in the NPM Explorer
(its row reads **● running** meanwhile, and right-click offers **Stop
Script** for just that one; the Workbench's **RUNNING** section lists
every one of them with its own Open and Stop), a Focused Test that never returns, an
`ng generate` that stalls, and any install the product started for you;
its tooltip names what it would stop. Press Run while that install is
still going and the status line refuses out loud instead of running
against a half-written `node_modules`. Delete `node_modules` and press
Run: the status line refuses again, this time pointing at NPM Explorer
▸ Install. Open the **Workbench** while something runs: the RUNNING
section leads the page with Open and Stop on every row, and ⌘I "stop"
finds the same runs. Press GO on a rack device and it appears there
as well — the ■ stops every running command, the rack's included, and
the device's faceplate reads STOPPED; the rack's Stop All reads the
same way. The scaffold is pre-trusted because
you just created it; a *cloned* repository would have shown the
Workspace Trust prompt first — the product never runs a stranger's
code without asking. That law covers Run/Build/Test, npm scripts, LSP
servers, formatters, and debuggers alike.

**Do:** Now the faster door: **File ▸ New Experiment…** (⇧⌘E — the
first entry on the Welcome tab). Pick **Express API**, leave the
install box checked.

**See:** A throwaway lands in `~/.nmox/experiments` — no git, no
recents, pre-trusted — and opens on its own `EXPERIMENT.md`
walkthrough: what to press, which file to change, where this stack's
IDE intelligence lives. The status line says the dependencies are
already installed, so the FIRST Run works. **File ▸ Experiments…**
shows the shelf with each experiment's age and measured disk cost;
Promote graduates a keeper, Discard deletes without ceremony — and
discarding the one you're aimed at re-aims the studio at `~/NMOX`
instead of a deleted path.

## 3. The Workbench (⌥⌘0)

**Do:** ⌥⌘0.

**See:** The home base: current project, open files, recent files,
recent projects, tooling shortcuts. Right-click any recent row for
**Forget** — the file or project stays on disk, only the list lets go.

## 4. The editor, quickly

Open `index.html` and the stylesheet, then run this gauntlet:

- **Emmet** — type `ul>li*3` in the HTML and press **⌥⌘E**: it
  expands. In the stylesheet, `df` ⌥⌘E becomes `display: flex;`. A
  token Emmet does not recognize is left untouched — a wrong guess
  would mutate your file, so the grammar is exact-match by design.
- **Colors** — every color literal in CSS/SCSS/Less paints as an
  inline swatch; **⌘-click** one and a picker replaces it *in its
  authored form* (hex stays hex, `hsl()` stays `hsl()`). The modern
  functions — `oklch()`, `lab()`, `color-mix()` — swatch too, with
  real conversions.
- **Design tokens** — declare `--brand: #7f5af0;` in `:root`, then
  type `var(` in a rule: completion lists your tokens with color
  swatches, and ⌘-click on a usage jumps to the declaration.
- **The class attribute** — declare `.btn-primary` in the stylesheet,
  then type `class="btn-` in the HTML and press ⌃Space: your project's
  real classes appear, each naming its declaring file. **⌘-click** a
  class in markup to land on its rule, or a `.selector` in a
  stylesheet to land on its first usage. Right-click ▸ **Rename
  Class…** rewrites every whole-token span across the project — and
  refuses out loud when the new name already exists, when the file
  census is too large to guarantee totality, or when unsaved changes
  would clobber it.
- **Env keys** — with a `.env` beside the project, type
  `process.env.` (or Vite's `import.meta.env.`) in any script:
  completion offers the real keys, values truncated so a secret is
  reminded, never disclosed; ⌘-click jumps to the declaring line. And
  ⌘-click a `fetch('/api/…')` path string to land on the Express
  route that serves it — `:param` routes match concrete paths.
- **Navigator** — open the Navigator window on a JavaScript file. An
  Express-style `app.get('/health', …)` outlines as `GET /health`,
  click-to-line; an Angular `app.routes.ts` outlines its route table.
- **Format** — right-click ▸ Format with Prettier (project-local
  binary only when the workspace is trusted), and `.editorconfig` is
  honored on every save.
- **Keymaps** — Tools ▸ Options ▸ Keymap: five profiles (NetBeans,
  NetBeans 5.5, Eclipse, Emacs, IntelliJ) and every NMOX chord rides
  all of them.

Polyglot claims are cheap to test: open any of the 87 grammars' file
types — Rust, Go, Gleam, Solidity, Fortran, COBOL if you like — and
you get highlighting, comment toggling, completion, and (where a
server exists) LSP intelligence, gated by the same trust law.

## 5. The Task Rack

**Do:** Open the Task Rack tab. Your project is already aimed (the
rack follows the aim everywhere). Hover a GO button before pressing
it.

**See:** A Reason-style rack of hardware: 53 devices on the palette,
each with knobs, LEDs, LCDs, and rear-panel jacks. The tooltip on
every GO button shows the **exact command** it will run — command
transparency is a law. Press a device's GO and watch MONITOR stream
its output (stderr in its own color). Now the fun parts:

- **Cables** — flip a device (rear view), then *click* an out-jack and
  *click* a compatible in-jack: connected. Wire VERITAS's FAIL out to
  KVASIR's EXPLAIN in and a failing test explains itself, hands-free.
- **Presets** — the Presets menu ships wired patches (Ship Gate, E2E
  Loop, Monorepo Lanes…). Loading one asks first if your rack has
  unsaved changes — ⌘Z cannot cross a patch load, so the confirm is
  honest.
- **PREFLIGHT** — the ship gate device: per-toolchain checks with
  verdict LEDs, and an OK gate other devices can consume.
- **Resurrection** — start a dev server from the rack, then `kill -9`
  the whole IDE from a terminal. Relaunch: a resume toast offers to
  restart the session exactly as it was.
- **Export CI** — the rack's current patch exports as a GitHub Actions
  workflow with real, current action pins.

## 6. The programmable rack

**Do:** Save this as `~/.nmox/devices.d/hello.json`, then reopen the
palette:

```json
{
  "id": "com.example.hello",
  "title": "HELLO",
  "tagline": "greets the project",
  "category": "AUTOMATE",
  "usage": "GO prints a greeting using the project's own node.\nPatch DONE onward to chain another device after it.",
  "buttons": [
    { "label": "GO", "role": "GO", "command": ["node", "-e", "console.log('hello')"] }
  ]
}
```

**See:** HELLO on the shelf under **Automate** — a real device with the whole security
model inherited on day one: its command is argv-only (no shell
metacharacters), the tool name must be bare, execution rides the same
Workspace Trust gate as every built-in, and any rule breach skips the
file whole with the reason logged. Six gallery devices ship installed
and active so you can crib their JSON, and
[device-files.md](device-files.md) is the full format reference —
every example in it is parsed by the product's own build. No Java, no
plugin build, no restart — this is why v2.0.0 was the major version.

## 7. Quick Search (⌘I)

**Do:** ⌘I, then try: your project's name, a device name (`kvasir`),
`live servers`, an epic label from your task board, `blocked`.

**See:** One search reaching projects, rack devices, live servers, API
Studio requests, DB connections, infra nodes, and Task Board
dimensions. Ordinary words work — `logs`, `bundle size`, `ai` — because
findability was measured (24 of 49 everyday terms used to return
nothing; now zero do).

## 8. The Browser (⌥⌘4), source-aware

**Do:** ⌥⌘4 — it opens on Hacker News. Then point it at your served
project and open DevTools.

**See:** A real WebKit browser in a tab, with viewport presets (the
iPhone preset genuinely reflows — media queries fire for real) and
save-to-reload: ⌘S in the editor repaints the served page. DevTools
is ours — Console, DOM, Network, Storage, plus framework panes that
read live component trees for Vue, Svelte, and Angular. And the loop
that makes it an IDE browser:

1. **Pick element** — a crosshair in the live page selects into the
   DOM tree.
2. **Open Source** — jumps to the HTML file *at the producing line*
   (only through vouched channels; a remote page refuses honestly).
3. **Edit Style…** — instant inline preview, then the declaration
   lands in the *source* stylesheet, in the rule the page's own
   cascade says wins.
4. **Motion** — the DHTML timeline: load the `marquee` preset, press
   Play and watch the element fly; drag keyframe diamonds, scrub the
   ruler to hold the page mid-animation, then **Apply to Source** and
   find the `@keyframes` block sitting in your stylesheet.

## 9. API Studio (⌥⌘8)

**Do:** ⌥⌘8. New request: `GET
https://hacker-news.firebaseio.com/v0/topstories.json`. Send. Then try
Import… — paste a `curl` command, a `.http` file, an OpenAPI spec, a
Postman collection, or a HAR capture.

**See:** Status, timing, size, a pretty-printed body, and a
**security-header grade** on every response. `{{vars}}` resolve from
environments; auth tokens live in the **OS keychain only** — the
committable `.nmoxapi.json` never carries a secret, and every importer
lifts captured credentials keychain-side or drops-and-counts them.
**Explain…** on a response opens an KVASIR conversation about exactly
what is on screen, redacted where the data lives. Copy curl emits the
exact command Send would run.

- **Three exports, one request bar** — *Copy curl* is the exact
  command Send would run; *Copy fetch* is the call your client code
  would make; *Copy TS types* turns the JSON response on screen into
  TypeScript interfaces (nested objects dependency-first, a null
  beside a non-null sibling honestly `| null`). The same codec rides
  every `.json` file's right-click as **Copy TS Types**.
- **From the server, a request** — right-click an
  `app.get('/api/users', …)` line in the editor and **Test in API
  Studio** drafts `GET {{base_url}}/api/users`, ready to Send.

## 10. DB Studio (⌥⌘7)

**Do:** ⌥⌘7 ▸ new SQLite connection (pick any path — it creates the
file). In the console:

```sql
CREATE TABLE guests (id INTEGER PRIMARY KEY, name TEXT);
INSERT INTO guests (name) VALUES ('Ada'), ('Linus');
SELECT * FROM guests;
```

**See:** Per-statement result grids. Double-click a cell and edit —
Apply previews the exact `UPDATE`s before running them, and a grid
without a primary key says *why* it is read-only instead of failing
silently. Save the query, export CSV (formula injection is
neutralized), press EXPLAIN. Passwords for server engines are
keychain-only; if Docker is running a database container, the studio
offers a ready-made connection with the published port pre-filled.

## 11. Contract Studio (⌥⌘6) and the Contract Kit

**Do:** File ▸ Contract Kit (Web3)… — pick a chain (eleven, from
Foundry/EVM to Clarity, Aiken, and Tact). With Foundry installed:
scaffold, `forge test` from the rack, then ⌥⌘6.

**See:** The artifact tree, ABI-driven Interact with decoded returns
and reverts, a Watch pane streaming blocks and decoded events from the
local ANVIL devnet, and gas/EIP-170 verdicts in Oversight. The law of
this studio: **no private keys, ever** — sends go through devnet
unlocked accounts, secret RPC URLs are keychain-only, and the kit
scaffolds carry their refusal paths as tests because on-chain, the
refusals *are* the contract.

## 12. Block Studio (⌥⌘5)

**Do:** ⌥⌘5. Drag pieces to compose a web component; open the live
preview.

**See:** A Scratch-like composer that generates a real, self-contained
custom element — click a piece and its lines highlight in the
generated code. The round trip is total: `generate(parse(code))` is
byte-identical, components can nest other components, and renaming a
tag follows every reference.

## 13. Infra Designer (⌥⌘9)

**Do:** ⌥⌘9. Drag a droplet and a database onto the canvas, wire them,
press Deploy and read the plan — then Cancel.

**See:** A Node-RED-style canvas over DigitalOcean, Hetzner, and
Cloudflare with cost estimation up front. Deploy shows a dry-run plan
before anything bills; destroys name their cost consequences and
default to the safe button (a reflexive Enter cannot delete cloud
resources — that default is a tested law across the whole product).
Tokens are keychain-only. Without an API token, the designer is still
a fully working planner.

## 14. The Task Board (⌥⌘1)

**Do:** ⌥⌘1. Make three cards; drag one to Doing. Right-click it:
**Clock In**. Set a label, mark another card blocked (owner + the
action that unblocks it). Toggle **Overview**. Then press
**Standup…**.

**See:** A per-project kanban living in `.nmoxtasks.json` beside your
code — check it in and the team shares it; every merge hazard the file
can carry (duplicate ids, stray open clock sessions) is healed at
load. The Overview face turns the same records into WIP, flow bins,
aging cards, the blocker register, and a TIME report that clips
sessions per calendar day. The Standup button writes your daily report
— Yesterday/Today from done stamps and day-clipped sessions, Blockers
from the register, Commits from `git log` — one click to copy. Edit
the file in your editor, or pull a teammate's changes: the visible
board updates within about a second and a half.

**Do:** Press **Sprint… ▸ Start Sprint…**, name it, accept the
two-week window. Move a card to Done, toggle **Overview**, then
**Sprint… ▸ Sprint Report…**.

**See:** The Overview grows a sprint header and a burndown
reconstructed from the cards' own done stamps — the dim line is the
ideal, the phosphor line is what happened, and the future stays
unplotted. The report is the Standup's review sibling: done,
open-at-close, still-blocked, clocked time, retro notes, and a
velocity line once **Close Sprint…** has archived a sprint or two.
Closing archives the window, count, and retro — cards stay exactly
where they are — and then OFFERS the next sprint: the dialog opens
pre-filled (name incremented, same-length day-after window), fully
editable, Cancel starting nothing. Once history exists, the Sprint
dialog itself shows the planning number ("Velocity — last 3 sprints:
7, 14, 9 done"), and the Standup's header opens with the sprint and
its day count ("Sprint 8 · day 3 of 14").

## 15. IRC (⌥⌘3)

**Do:** ⌥⌘3, connect to Libera.Chat, join a busy channel. Then:

```
/filter add work * standup|deploy
/lastlog deploy 10
/help
```

**See:** A full client — SASL, IRCv3, tab completion, highlights,
WHOIS cards, the channel browser. The smart filter hides join/part
noise from nicks that haven't spoken in five minutes (WeeChat's
signature, on by default); your own `/filter add` regexes hide
matching lines with *no* signal at all while the plain-text logs under
`~/.nmox/irc-logs` keep every line; `/lastlog` prints matching
scrollback in place; **Ctrl+J** jumps to the most urgent unread
buffer. Closing the tab does not disconnect — a chat client outlives
its window.

## 16. Debugging

**Do:** Open a JS file in a Node project, set a breakpoint in the
gutter, Debug File. For a page: Debug in Chrome.

**See:** Breakpoints hit in the IDE out of the box — the vendored
js-debug adapter handles Node (child processes run undebugged, by
recorded decision) and launches a throwaway-profile Chrome for pages.
Both are gated by Workspace Trust before any spawn. Python and Go
debug through the same DAP client with their standard adapters.

## 17. The kits

Run each against your project from the File menu:

- **PWA Kit** — icon forge (maskable set included), a readable service
  worker that precaches every icon it writes, offline page, idempotent
  index.html wiring.
- **Standards Kit** — robots.txt, sitemap, web manifest, RFC 9116
  security.txt, humans.txt.
- **A11y Kit (Web)** — a11y.css (focus ring, visually-hidden, skip
  link, reduced motion), A11Y-NOTES.md, idempotent index.html wiring
  (lang + skip link + stylesheet); run it twice and the second report
  speaks the refusals.
- **I18n Kit (Web)** — locales/en+es catalogs (same keys), the
  data-i18n applier keeping <html lang> truthful, I18N-NOTES.md, the
  idempotent script-tag wiring.
- **Image Kit (Web)** — presses a project's images (pure-Java JPEG
  re-encode + WebP siblings via your cwebp); originals untouched,
  savings under 10% discarded as already-tight.
- **Classic Kit** — jQuery-era stacks, vendored and sha256-pinned or
  via npm, with webpack/grunt/gulp scaffolds.

Every kit obeys the same write law: **never clobber** — an existing
file gets a `.suggested` sibling instead.

## 18. Learning Spaces

**Do:** File ▸ New Learning Space… Pick **Your First Web Page** (the
catalog opens with it) or go exotic — 93 spaces cover everything from
Angular and Playwright to Clarity contracts and COBOL. Later, File ▸
Learning Spaces… to open or discard them.

**See:** Each space generates sample code, a walked tutorial, and a
rack pre-wired with a real in-rack REPL — the interpreter installs
from an INSTALL button, no terminal needed. Your own tutorials join
the picker from `~/.nmox/learn-catalog.d`.

## 19. Break it, check it, export it — the learning loop

**Do:** Serve a page (any template's Run), open it in the Browser,
then break its JS — a typo'd function name will do — and reload.
Then, in a learning space, File ▸ **Check My Work**.

**See:** The runtime error lands in your EDITOR: a squiggle at the
failing line and an Action Items row, exactly like a lint finding —
the mistake appears where you made it, and a reload clears the old
page's errors. Check My Work grades your file against the space's
real checkpoints — on an untouched space every "You …" line is ✗,
because a task checkpoint exists to tell your work from the sample —
and, on a failure, offers **Explain with KVASIR…**
(its consent names exactly what leaves: the failed checks and your
checked file, nothing more). When your experiment grows up, File ▸
**Export as Learning Space…** turns the aimed project into a drop-in
tutorial others can pick from the catalog — refused whole unless it
round-trips the student picker's own parser. And Manage
Experiments… can **Duplicate** one so you can try the other way
without losing the first.

## 20. KVASIR — the AI surfaces

**Do:** Select some code ▸ right-click ▸ **Ask KVASIR About
Selection…**. Or mount KVASIR in the rack and press EXPLAIN after a
failed run.

**See:** Claude explains your selection or your failure — as a
conversation, with follow-ups riding the full history. Both flows earn
**their own consent** (a dialog that names exactly what is sent and
what never leaves the machine) and need an API key (keychain-only, or
`ANTHROPIC_API_KEY`). No key, no consent, offline? The LCD says so
honestly; nothing phones home without the button press. Fast/Deep
picks the model, fixed per conversation so the transcript never lies
about who answered.

## 21. Angular, first-class

The framework bet. **Do:** New Project ▸ Angular, open a
`.component.html`, type a property typo like `{{ user.logedIn }}`.

**See:** The template squiggles with Angular's own "Did you mean
'loggedIn'?" — the Angular Language Service installs project-local
from a one-click prompt. ⌘B works in templates; the component ↔
template ↔ styles ↔ spec switcher covers the four-file set; the
Navigator outlines your routes; File ▸ New Angular Schematic… runs
`ng generate` as a gesture; HALO is the rack's Angular console; and
the DevTools Angular pane reads the live component tree of a dev
build.

## 22. The product's own website

**Do:** Help ▸ **NMOX Studio Website (local)** (or the Welcome
footer's **Website ⇄**).

**See:** The app serves its own site to you on localhost — the ⇄
serving chip lights on the product's own story, and the hero note
says so because it's true. The EN/ES buttons ride the I18n Kit's own
helper; the a11y stylesheet IS the A11y Kit's output, byte-for-byte,
build-gated. The same bytes are deployed publicly at
<https://nmox.github.io/NMOX-Studio/>. And every release download is
verifiable: the GPG-signed `SHA256SUMS` on the release page, key in
the repo-root `KEYS` file.

## 23. Ship it

**Do:** Rack ▸ PREFLIGHT ▸ GO. Docker Panel ▸ Dockerize. Rack ▸
Export CI. Then Tools ▸ Plugins ▸ Check for Updates.

**See:** The ship checks for your toolchain; a production Dockerfile
that was *built against a real daemon* before it was ever shipped as a
recipe; a CI workflow from your actual rack; and the product updating
itself through its own update center — a stock v2.2.1 install has
walked that road to the current release in one in-app update,
digest-verified, in our release gauntlets. Since v2.42.0 every
module NBM is signed and the certificate ships inside the product,
so the whole update flow runs with no certificate prompts at all. The git chip on the status
line knows your branch; the Team menu is the full git suite.

## 24. Point an agent at it

**Do:** Tools ▸ **Agent Port (MCP)…** ▸ Copy Config, paste it into
your agent's `.mcp.json`, and ask the agent what is running, what you
have open, or where `checkout` is declared.

**See:** A loopback-only MCP server with a per-start token, answering
from the IDE's own records — `ide_context` in one call (the aimed
project with its toolchain and package manager, everything serving, everything running with when it started,
the file you're editing, the last failure, a diagnostics count),
`find_symbol` from the same index as Go to Symbol, `outline` for one
file's structure, `search_text` for a literal across the project (heavy
directories and binaries skipped, at most 50 hits, said when capped),
`editor_state` with unsaved tabs flagged, `run_history` for what ran
lately with exit codes and durations. Every tool is typed and annotated
read-only; the same answers are browsable as `nmox://` resources —
attach `nmox://outline/src/app.js` and the agent has the file's
structure as context — and the *Where is a symbol declared* prompt
folds the hits for a name you give it — and `completion/complete`
suggests that name from the index, or a file for the outline template
from the project's own files. Subscribe to `nmox://runs` and the port's
event stream says when a run starts (subscribe to an outline and it
follows the file); set the log level and every run's lines arrive as
log messages — lifecycle only until you ask for `debug`; the status
line shows `⌁ agent port :N` the whole time (its tooltip: how many
agents stream, and when the last request came); stop a run yourself and
`run_history` reads `stopped`, never `failed`; search for a value that
lives in `.env` and get nothing — secret-bearing files are never
searched. It
is read-only by construction: an agent can ask, never run — the build
fails if any Agent Port class so much as names a spawn, a write, or
the run registry's stop.

---

## 25. Show it to a room

**Do:** With a file open and a project running, View ▸ **Presentation
Mode**; then View ▸ **Show Keystrokes** and press ⌘S; then right-click
in the editor ▸ **Copy as Markdown with Link** and paste somewhere;
then Tools ▸ **Copy Editor Screenshot** and paste into a chat; then
Tools ▸ **Copy Project Tree as Markdown**.

**See:** Every open editor +10 pt, the in-app Browser's page at 150%,
the Output window's text and every open Terminal grown with them — and
all four back exactly
where they were when you toggle off (nothing is written to your
settings). A dark pill reading `⌘S` at the bottom of the window for a
moment; type a word and nothing appears — only chords with ⌘, ⌃ or ⌥
and the function keys ever show, so a password can't end up on the
projector. The paste is a fenced block tagged ` ```jsx ` followed by
`[src/App.jsx#L3-L14](https://github.com/you/repo/blob/main/src/App.jsx#L3-L14)`
— the branch you have checked out, because a local commit never pushed
would be a 404 dressed as a permalink; with unsaved changes the status
line refuses instead ("save first, so the block matches what the link
shows") and copies nothing. The chat gets a crisp 2x image of the
editor tab alone — toolbar, gutter, code, minimap, no IDE chrome — and
the tree paste is the box-drawing README tree with `node_modules/ …`
named but never entered and the IDE's own `.nmox*.json` left out.

---

## The refusals are features

A closing tour of things the Kitchen Sink deliberately will not do —
each refusal is tested:

- Run a cloned repo's code (scripts, LSP servers, formatters,
  debuggers) without the Workspace Trust prompt.
- Put a secret in a committable file — auth tokens, DB passwords, RPC
  URLs, and cloud tokens are keychain-only, everywhere.
- Hold a private key, sign, or send from your accounts — Contract
  Studio works through devnet unlocked accounts only.
- Render external text as markup — a `<html><img src>` in a card
  title, API response, or nick paints as characters, never fetches.
- Default a destructive dialog to Yes — Enter is always the safe
  button.
- Clobber your files — kits write `.suggested` siblings; corrupt
  workspace files are `.bak`'d, never overwritten.
- Guess — Emmet, importers, and the style write-back refuse with a
  reason rather than mutate on a maybe.

## Where everything lives

| File / dir | Owner |
|---|---|
| `.nmoxrack.json` | the rack patch, beside the project |
| `.nmoxapi.json` | API Studio workspace (no secrets) |
| `.nmoxdb.json` | DB connections (no passwords) |
| `.nmoxweb3.json` | Contract Studio (no keys, no secret URLs) |
| `.nmoxinfra.json` | Infra design (no tokens) |
| `.nmoxblocks.json` | Block Studio components |
| `.nmoxtasks.json` | the Task Board |
| `~/.nmox/devices.d` | your JSON rack devices |
| `~/.nmox/learn-catalog.d` | your learning spaces |
| `~/.nmox/templates.d` | your project templates |
| `~/.nmox/presets.d` | your rack presets |
| `~/.nmox/api-library.d` | your shared `.http` requests |
| `~/.nmox/dockerize.d` | your Dockerfile recipes |
| `~/.nmox/doctor.d` | your Doctor probes |
| `~/.nmox/irc-logs` | IRC logs, plain text |
| `~/.nmox/learn` | learning spaces' homes |

All six per-project files reload live on external edits, save
atomically, and never clobber a foreign change.

That's the sink. If you found a surface this tutorial missed, that's a
bug in the tutorial — the product's rule is that everything advertised
works, and everything that works is advertised.
