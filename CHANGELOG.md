# Changelog

All notable changes to NMOX Studio are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [1.10.0] — 2026-07-01

### Added
- **Format on save with Prettier.** Saving a JS/TS, CSS/SCSS/Less, HTML,
  JSON, YAML, Markdown, Vue, GraphQL, Svelte, or Astro file now runs the
  project's own Prettier over the buffer first. **Upgrading users get
  format-on-save automatically in any project containing a Prettier
  config; disable via Options → Editor → Format on Save.** The effective
  gate is the project's own opt-in: any `.prettierrc*` /
  `prettier.config.*` or a package.json that mentions prettier, searched
  from the saved file up to the repository root (`.git`) and no further —
  monorepo roots count, a personal `~/.prettierrc` above the checkout
  does not. The project-pinned `node_modules/.bin/prettier` is preferred
  over a global install so output matches CI, resolved within the same
  repository boundary. Failures never interrupt the save: a syntax error
  mid-edit, a missing binary, or a hung process (5 s kill) all save the
  buffer unchanged with a log line. The applied edit is the minimal
  changed span, so the caret and scroll position survive the save.
  Measured cost per save (prettier 3.9.4, M-series Mac): ~145 ms on a
  300-line file, ~410 ms on a 3,600-line/392 KB one; files over 512 KB
  skip formatting entirely.

### Tests
- `PrettierFormatterTest`: the opt-in walk (config names, package.json
  mention, monorepo walk-past, the repository boundary — configs and
  node_modules above `.git` don't count, a config at the repo root
  does, a `.git` *file* bounds like a directory), local-binary
  preference, every failure mode collapsing to "no change", and a
  real-process round trip through the stdin/stdout runner.
  `FormatOnSaveTest`: minimal-edit correctness on every shape of change
  (Positions survive a middle-of-file edit), and mime coverage read
  from the generated layer — the artifact that actually registers the
  hook.

## [1.9.0] — 2026-06-22

Polyglot pipelines already compose as parallel *lanes* — one device chain per
toolchain, each pinned to its own manifest directory. This release adds the
three primitives that let those lanes coordinate: a join barrier, readiness
gates on every long-runner, and per-lane file-change routing.

### Added
- **QUORUM (Lane Join) — a new device.** The barrier where parallel lanes
  converge. In ALL mode it fires OK once every wired IN has arrived and all of
  them passed (FAIL if any failed) — "web tests AND api tests pass, then deploy."
  In ANY mode it relays the first arrival, a race. Patch each lane's DONE jack
  into an IN, OK into the shared downstream step (LAUNCHPAD, PREFLIGHT).
- **Readiness ENABLE gate on every long-runner** (IGNITION, SURGE, HALO, NEXUS,
  PHOENIX, WORMHOLE, INSPECTOR). Patch an upstream RUNNING/SERVING gate into a
  server's ENABLE input and it serves only while its dependency is up — Postgres
  → API → web ordering by cable. A high edge while already running never
  double-launches. Generalizes TEMPO's enable semantics across the fleet.
- **Per-lane GLOB route on REFLEX.** A new GLOB field pins one watcher to a
  single language: type `rs` to fire only on Rust saves, `ts,tsx` for the web
  lane. Empty falls back to the coarse FILTER knob. A `.rs` save now drives the
  cargo lane and a `.ts` save the vitest lane, instead of every save running both.
- **Polyglot Gauntlet preset.** Per-language REFLEX watchers → their own VERITAS
  lanes → QUORUM → PREFLIGHT: the rack ships only when every lane is green.

### Tests
- `CrossLaneTest`: the QUORUM ALL barrier (waits for all lanes, ANDs their
  results), the FAIL path, the ANY race, the ENABLE gate's start/stop and
  no-double-launch guard, and REFLEX glob parsing. `DeviceContractTest` now
  covers QUORUM for free.

## [1.8.6] — 2026-06-22

A control-surface audit swept all 33 rack devices for knobs, toggles, buttons,
and patch jacks that looked wired but weren't — and made them carry real
signals and real results. Most of the fleet was already honest; the cases below
were not.

### Fixed
- **VERITAS COVER actually collects coverage now.** The toggle keyed on the raw
  RUNNER knob, so in the default AUTO position (and anywhere AUTO resolved a
  framework) it added nothing — the switch was visibly on and did nothing. It
  now resolves AUTO first, so a jest/vitest project gets `--coverage` and a
  Python project gets `pytest --cov`.
- **BLACKBOX's OUT jack was inert.** The flight recorder declared an output port
  but never emitted on it — a cable you could draw that could never carry a
  signal. It now broadcasts the newest tape entry (launch, exit, or error) as it
  happens, so you can wire BLACKBOX → MONITOR for a live event log or into a
  notifier.
- **SONAR's OUT jack spoke prose, not data.** It emitted the sentence
  `"sonar: N ports listening"`, which nothing downstream could act on. It now
  emits the listening port numbers as a sorted, de-duplicated, comma-separated
  list a wired device can parse.
- **FORGE's WATCH toggle was a no-op in the esbuild position.** esbuild now gets
  `--watch` when WATCH is on, like the other bundlers.

### Added
- **PING can send a request body.** POST and PUT previously sent an empty body,
  so you couldn't smoke-test an API that expects a payload. A new body field is
  sent on POST/PUT (with a JSON `Content-Type` when the body looks like JSON);
  reads and blank bodies stay body-less.
- **`DeviceWiringTest`** locks these behaviors in: COVER resolves AUTO before
  adding a coverage flag, PING's request builder carries the body only on
  write methods, SONAR's payload is machine-usable, and BLACKBOX's OUT jack
  delivers a recorder event down a real cable.

### Changed
- **NPM-9000** dropped two dead button fields (inlined as locals), and
  **MAESTRO's** STOP ALL now describes honestly what it does — halt every
  command-backed device — rather than implying it can stop timer- or
  listener-driven ones.

## [1.8.5] — 2026-06-21

### Fixed
- **A clean startup log.** Every NMOX-owned warning the platform logged on
  launch is gone. The 45+ TextMate grammar MIME resolvers and the editor/
  explorer tool-window tabs now declare explicit `position` attributes, so the
  filesystem ordering is fully specified instead of "Not all children marked
  with the position attribute"; the top-level Build menu and the Window-menu
  entry are positioned too. The redundant layer-based JavaScript MIME resolver
  was removed — `JavaScriptDataObject`'s annotation already registers
  js/mjs/jsx, and the duplicate triggered the "ineffective registration"
  warning (NB #191777). (The only startup warnings left come from upstream
  NetBeans modules and a benign platform notice — not our code.)

### Changed
- **The Infra Designer opens at startup**, joining the Welcome and Task Rack
  tabs in the main editor area, so the multi-cloud canvas is one glance away
  instead of a menu trip.

## [1.8.4] — 2026-06-21

### Fixed
- **A duplicate commons-codec bundle no longer fails to start on launch.**
  v1.8.3 installs surfaced a startup warning — an OSGi bundle whose "state
  remains INSTALLED after start()". The cause: the NetBeans `ide` cluster's
  `commons_compress` dragged in the plain `commons-codec` Maven jar
  transitively, alongside the canonical platform wrapper. `nbm-maven-plugin`
  auto-wrapped that plain jar into a second module in `extra/` with the same
  `Bundle-SymbolicName` as the platform copy, and Netigso couldn't start the
  redundant bundle (its packages were already exported), leaving it stuck in
  INSTALLED. The transitive plain jar is now excluded so only the platform
  wrapper ships. Nothing required the duplicate — the real consumers
  (`commons_compress`, `commons_io`) bind to the retained platform module —
  so this clears the warning dialog with no functional loss.

## [1.8.3] — 2026-06-21

### Fixed
- **The bundled-runtime floor is JDK 21, matching the Platform 30 baseline.**
  NetBeans Platform 30 requires JDK 21, but the installers' jlinked runtime
  and the macOS launcher's fallback still named JDK 17. Both now floor at 21,
  so the embedded runtime can't drop below what the platform itself needs.
- **The Homebrew cask CI regenerates an idempotent file.** The release
  workflow's `homebrew` job emitted `depends_on macos: ">= :big_sur"` — the
  deprecated string form — which didn't match the bare-symbol `:big_sur` in
  the committed cask, so every run produced a spurious diff. The heredoc now
  emits the bare symbol, matching the repo.

### Documentation
- README badges and copy caught up with the build: Java 21+ (was 17+),
  NetBeans Platform 30.0 (was 22.0), the editor screenshot caption now reads
  45+ languages (matching the intro and feature list), and the footer tagline
  speaks the rack/synth voice instead of the old generic one.

## [1.8.2] — 2026-06-18

### Changed
- **Coverage can't silently regress now.** JaCoCo gained a fail-closed
  floor on the three substantial-logic modules — rack (≥40% line),
  infra (≥30%), editor (≥28%) — verified to actually fail the build when
  coverage drops below the line. The UI/glue/packaging modules stay
  measured but ungated, since forcing coverage there would only breed
  hollow tests. That completes the self-policing build: a correctness
  gate (SpotBugs), a security gate (find-sec-bugs), and now a coverage
  gate, all on every push.

## [1.8.1] — 2026-06-18

### Security
- **A security static-analysis gate, and a clean review behind it.**
  find-sec-bugs now runs alongside SpotBugs on every build. It caught two
  genuine **ReDoS** (catastrophic-backtracking) regexes — in the eslint
  and `docker ps` output parsers, which chew on external tool output —
  now rewritten to linear time; the source-outline regexes also got a
  line-length bound. Everything else it flags is by-design for a local
  IDE (process launches via argv not a shell, file ops on the user's own
  paths, a localhost debug socket, UI-jitter randomness) and is excluded
  with a written rationale, so the dangerous classes — XXE, SSRF, weak
  crypto, hardcoded secrets, unsafe deserialization — stay watched.

### Fixed
- **A running tool process can't be orphaned by a stale exit.** A
  device's `exec` swapped its process handle without an identity check,
  so a previous run's exit callback arriving late could null out the
  handle of the run that replaced it — leaving a live process that
  `isLive()`/panic could no longer see or kill. The swap is now
  identity-guarded.
- Removed a dead, unescaped HTML-building method left over from the
  language-server panel rework.

## [1.8.0] — 2026-06-18

### Added
- **Supply-chain hygiene.** Dependabot now watches three ecosystems —
  the Maven reactor, the GitHub Actions in the workflows, and the npm
  dependencies inside the project-scaffold templates — so a generated
  project never starts out vulnerable (the Vite advisory that bit us was
  exactly that case) and the build's own dependencies stay patched. Every
  release now also ships a **CycloneDX SBOM** (`*-sbom.json`) listing every
  component that goes into the app.

### Changed
- **NMOX Studio is now a Java 21 LTS application.** The source target
  moved 17 → 21 and the installers bundle a Temurin 21 runtime, so the
  app ships on a current, supported JVM (newer GC, security fixes, the
  language features 21 brings). CI proves the cross-platform claim it
  always made: the full `verify` — tests plus the SpotBugs gate — now
  runs on a **Linux + macOS matrix** on JDK 21, not just one Linux/JDK 17
  box. (The NetBeans Platform itself stays on RELEASE270; a platform bump
  is its own careful step.)

## [1.7.0] — 2026-06-18

### Changed
- **One place to launch a tool, done right.** The process-launch
  preamble — PATH augmentation, empty stdin, the no-color/non-interactive
  environment — was copy-pasted at three sites (`CommandExecutor`,
  `DockerClient`, `PortScanner`), and one copy hardcoded `/dev/null`,
  which breaks on Windows. It now lives once in `ProcessSupport`; the
  Windows null-device bug is fixed and a wedged `lsof` is reaped on
  timeout instead of leaking.
- **The build now policies itself.** SpotBugs runs on every `mvn verify`
  (so on every CI push and PR) with a correctness-focused filter, and
  JaCoCo reports coverage per module (≈40% instruction today, carried by
  the engine and logic layers). Error Prone was considered and skipped
  on purpose — it hooks the compiler and would fight the load-bearing
  `-proc:full` annotation processing; SpotBugs works on bytecode and
  doesn't.

### Fixed
- Static analysis caught and we fixed a batch of real defects: an
  unsynchronised lazy singleton (`BuildTaskManager.getInstance` had a
  classic init race), four ignored `mkdirs()` results now use
  `Files.createDirectories` (which surfaces failures instead of
  swallowing them), two dead copy-paste ternaries in the infra deploy
  planner (both branches were identical), a no-op port lookup, and
  missing `switch` defaults.
- **The structure outline no longer invents symbols.** A `function` or
  `class` that only *looks* like a declaration because it sits inside a
  block comment or a multi-line `` `template literal` `` (an embedded SQL
  or HTML heredoc) is no longer listed in the Navigator.
- The Infrastructure Designer detaches its graph and rack listeners and
  stops its autosave timer when closed, so opening and closing it no
  longer leaks a listener each time.
- `NpmService` bounds the wait for a finished command: once its output
  has drained, a process that never exits is reaped after a grace period
  instead of hanging the action thread (a slow build is unaffected — its
  output has to finish first).

## [1.6.0] — 2026-06-17

### Added
- **One-click language-server install.** Tools ▸ Language Servers… is now
  an install interface, not just a status list: every language NMOX can
  light up, with an **Install** button that runs the ecosystem's own
  command — npm, brew, `go install`, `cargo`/`rustup`, `gem`, `dotnet
  tool`, `coursier`, `opam` — each in its idiomatic way, with the bar
  running across while it downloads and the output streaming to the
  Output window. **Install all missing** walks the batch with a Cancel
  that kills the run; servers that ship with their toolchain (Swift,
  Dart) or need a manual setup are marked, never given a fake button; and
  a server whose package manager isn't present says "install `go` first"
  rather than failing cryptically. Commands run as argv through the
  rack's executor — nothing is shell-interpolated and sudo is never
  added.

## [1.5.0] — 2026-06-17

### Added
- **Language-server health — "why is there no hover here?" finally has an
  answer.** The platform delivers go-to-definition, hover, rename and
  live errors for free once a language's server launches, but every
  server is an external binary the developer installs. Now, when a
  file's server isn't found, you get one notification — once per language
  per session — naming the binary and the exact command to install it,
  click-to-copy. No nagging, no modal. And **Tools ▸ Language Servers…**
  shows the whole polyglot picture at a glance: every language NMOX can
  light up, which servers are installed, and the install command for the
  ones that aren't.

### Changed
- **The New Web Project dialogs are now platform dialogs** (the first
  slice of moving off raw `JOptionPane` to `DialogDisplayer`/
  `NotifyDescriptor`): the name prompt and the success/error messages are
  themed with the rest of the IDE and behave under the test harness.

## [1.4.9] — 2026-06-17

### Fixed
- **The Workspace Trust dialog renders its text instead of showing raw
  HTML.** The 1.4.7 move to the platform dialog passed an HTML string,
  which NetBeans shows verbatim (`<html><b>…`) rather than formatted; the
  message is now a `JLabel`, which renders it.
- **The test suite runs headless, so it never pops a real dialog onto
  your screen mid-build.** On macOS local test runs weren't headless, so a
  command-launching test could surface the trust prompt (for a `junit…`
  temp folder, no less). Surefire now sets `java.awt.headless=true`,
  matching CI — and the previously-skipped headless trust test now
  actually runs.

## [1.4.8] — 2026-06-17

### Fixed
A software-quality pass — a fan-out audit surfaced these, each fixed with
a test where the logic allows:
- **Docker calls can't deadlock or outlive their timeout.** The client
  read stdout to completion before touching stderr, so a command with a
  large stderr burst would fill that pipe and hang forever (the timeout
  was unreachable). stderr is now drained on its own thread.
- **Saved settings survive an abrupt quit.** Four more places wrote
  preferences with `put` but never `flush` — the cloud API token, recent
  files, recent projects, and the terminal-theme marker — so a crash
  between the write and the lazy flush lost them (the same bug class as
  the workspace-trust fix). All flush now.
- **NEPTUNE's MIGRATE runs the right tool.** It shelled out to `sqlite3`
  for Postgres and MySQL targets; now `psql -f` and `mysql -e "source"`.
- **`i++ / 2` is division, not a regex.** The JS lexer treated `/` after
  a `++`/`--` as the start of a regex literal, mis-painting the rest of
  the line.
- **HTML attribute-name completion works past the first attribute.** Once
  one attribute was quoted (`href="x" `), the next attribute name stopped
  completing; the value/name decision now uses quote parity.
- **A wedged `npm --version` can't hang the IDE** — that probe now has a
  bounded wait.

## [1.4.7] — 2026-06-17

### Fixed
- **Workspace Trust stops asking once you've answered.** The grant was
  written with `Preferences.put` but never flushed, so a less-than-clean
  quit dropped it and the next launch asked you to trust the same folder
  all over again — it now persists immediately. The prompt is also a
  proper platform dialog now instead of a bare Swing window, and trusting
  `/a/foo` no longer accidentally trusts the unrelated sibling `/a/foobar`
  (the check now respects path boundaries).

## [1.4.6] — 2026-06-17

### Added
- **A web project is now a first-class platform project, and the
  IDE-native Run / Build / Test / Clean drive the rack.** `WebProject`
  used to expose only a name and a tree; it now carries the full
  project SPI — `Sources` (so Find-in-Projects and Go-to-File know the
  source roots), an `ActionProvider` that maps each command to what the
  toolchain expects (`npm run dev`/`build`/`test` for Node, `cargo`,
  `go`, `mvn`, `mix`, … for the rest), `RecommendedTemplates` to scope
  the New File wizard to the web stack, and a `ProjectOpenedHook` so the
  rack aims itself the moment a project opens instead of every entry
  point remembering to. The actions appear on the project's right-click
  menu (Test on ⌃F6), grey out honestly when the toolchain can't express
  them (a project with no `clean` script has Clean disabled), run
  through the rack's own command bus with PATH augmentation, and show in
  the status-bar progress widget with a Cancel that actually kills the
  process. The platform's gestures and the rack are one mechanism now,
  not two parallel worlds.

### Fixed
- **Workspace Trust no longer refuses to run when there's no one to ask.**
  The trust prompt is a modal dialog; in a headless context (CI,
  automated launches, the test suite) it threw and was caught as a "no",
  so every command launch was silently refused — which had quietly turned
  the build red since 1.4.4. An interactive guard with no interaction now
  allows rather than blocks; the prompt still appears for real users.

## [1.4.5] — 2026-06-17

### Security
- **The Vue project template now scaffolds on Vite 6.** Its pinned
  `vite ^4.4.5` carried six advisories (two high — a `server.fs.deny`
  bypass and a `launch-editor` command injection, both Windows), so a
  freshly generated Vue project started life vulnerable. Bumped to
  `vite ^6.4.3` with `@vitejs/plugin-vue ^5.2.4` and `vue ^3.5.13`;
  verified a clean `npm install` (0 advisories) and production build.

## [1.4.4] — 2026-06-17

### Added
- **NEPTUNE database console** — a rack device for the part of web work
  that lives behind the app. PING checks a database is reachable
  (Postgres, MySQL, SQLite) or that a framework's schema is in order
  (Prisma, Ecto, Django); MIGRATE runs the migration the framework
  expects (`prisma db push`, `mix ecto.migrate`, `manage.py migrate`).
  CONNECTED/ERROR LEDs and a RUNNING gate let it drive the rest of a
  patch, the same as every other device.
- **Workspace Trust.** Running build, database, docker or make commands
  against a repository you just opened is a real risk; NMOX now asks
  before executing tools in an untrusted directory and remembers your
  answer, the way a modern editor should.

### Changed
- **Project templates are data, not code.** The React, Vue and vanilla
  scaffolds moved out of a giant Java string-builder into real resource
  files, so a template is now an editable folder of source — the
  long-standing tech-debt item finally paid down.

### Fixed
- **The rack can't be wired into a feedback loop.** Connecting an output
  back to a cable path that returns to its own input is now rejected, so
  a patch can't drive itself into an infinite signal cycle.
- Hardened `package.json` caching, command parsing and New Project
  wizard validation against malformed input.

## [1.4.3] — 2026-06-13

### Added
- **The Navigator window now outlines every language NMOX lexes.**
  TextMate gives colour but no parse tree, so a file's shape used to be
  invisible in the platform's structure view. A new outline engine reads
  it from the source directly — classes and their methods, functions and
  arrow functions, `describe`/`it` test blocks, CSS selectors and
  at-rules, Markdown headings, JSON/YAML/TOML/INI keys and sections, Rust
  and Go and Java-family declarations, GraphQL types, Makefile targets,
  and TODO/FIXME markers as a fallback. Each symbol wears a colour-coded
  phosphor badge; nesting follows what the language affords (brace depth,
  indentation, or heading level). Click a symbol to jump the editor to
  it; the tree refreshes as you type, parsed off the UI thread. Open it
  with **Window ▸ Navigator** (⌘7).

## [1.4.2] — 2026-06-12

### Added
- **The config layer a web project is actually full of now opens as
  itself.** Thirteen new TextMate grammars cover `.editorconfig` and
  INI, ignore files (`.gitignore`/`.dockerignore`/…), GraphQL, Vue,
  Svelte, Astro, Pug, Handlebars, Liquid, nginx, Makefile, Protocol
  Buffers and Prisma; YAML/TOML/Dockerfile/SQL ride the platform's
  native support. Comment-toggling works in all of them, and LSP
  intelligence attaches where a standard server exists (yaml, taplo,
  dockerfile, graphql, vue, svelte, astro, prisma).
- **Dotfiles and bare names resolve by filename**, the way the
  platform's own Dockerfile support does: `.editorconfig`, `.env`
  (and `.env.*`), `.gitignore`, `Makefile`, `nginx.conf` and friends
  now get their real MIME instead of opening as plain text.

### Fixed
- **Spellcheck no longer treats config files as prose.** A wrong token
  property key meant comment detection silently failed for every
  TextMate language, so the checker fell back to flagging keys and
  values — `charset`, `indent_style` — as misspellings. Now it reads
  the scope stack correctly and checks words only inside comments,
  across all 40+ lexed languages.
- **Config files with no toolchain no longer crash the editor on
  open.** A CSL language binding routed them through a code path whose
  lexer class was unreachable at document-load time; the TextMate
  grammar alone carries the highlighting.

## [1.4.1] — 2026-06-12

### Fixed
- **The installers no longer depend on the user's Java.** The DMG,
  Windows setup, tar.gz and deb now embed a jlinked Java runtime
  (`jre/` inside the app, `jdkhome="jre"` in its conf), so machines
  with no Java — or only a legacy Java 8, which previously booted the
  IDE far enough to die on a "Java 17 or newer required" dialog — just
  work. The macOS launcher probes the bundled runtime, falls back to
  an installed JDK 17+, and otherwise explains itself in a real dialog
  instead of failing cryptically. The deb is now honest about being
  `amd64` (it carries a native runtime) and no longer recommends a
  system JRE. Only the portable zip still expects a host Java 17+.

## [1.4.0] — 2026-06-12

### Added
- **PREFLIGHT Ship Check** — "done" as a machine state, not a feeling.
  CHECK runs the readiness list planned from your project (git clean,
  tests, build, lint, audit), one verdict per item on the phosphor
  checklist, and a final READY TO SHIP / NOT READY verdict. Patch its
  OK jack into LAUNCHPAD and unverified code physically cannot deploy.
- **BLACKBOX answers "what changed since it last worked?"** — when a
  run fails, the health line counts files modified since that device
  last went green, and the timeline lists them (newest first,
  dependency directories skipped).
- **HTML, CSS, SCSS and Less are first-class languages** — TextMate
  grammars (pinned from VS Code 1.95.0), their own MIME identities,
  CSL editor services, typing intelligence, spellcheck, and LSP via
  vscode-langservers-extracted. The HTML completion now finishes
  attribute values (`type=`, `rel=`, `role=`, …) and the CSS value
  dictionary grew from 8 properties to 36.
- **Regex-aware JavaScript lexer** — `/ab+c/gi` is one regex token,
  `total / count` stays division; escapes, character classes and
  unterminated slashes all degrade gracefully. Template-literal state
  now survives incremental relexing too.
- **File > New Project opens the real wizard** (it used to show a
  placeholder dialog) and File > Open File reports failures instead of
  swallowing them.
- **Infra deploys check every provider token up front** — a plan
  touching Hetzner or Cloudflare without a token is a dry run with a
  banner, not a half-deployment that dies on node 7. Sync now imports
  13 resource types (was 6), and a created resource whose id could not
  be parsed says so instead of showing a clean green "created".

### Changed
- Deleted the dead `cloud/`, `deployment/` and `containers/` skeleton
  modules (superseded by the Infra Designer and HARBOR) and the unused
  core `ServiceManager`/`FileCache`/`PerformanceMonitor` machinery.
  The remaining core module does exactly one thing: the phosphor
  Terminal theme. Also removed `NMOXStudioApplication` — an @OnStart
  hook in the nbm-application packaging module, which never produces a
  module jar, so the class had never once executed.
- `.css`, `.scss`, `.sass`, `.less` and `.json` files no longer
  masquerade as `text/html` — each resolves to its real MIME type.
- The rack's "Save Patch" confirmation no longer appends `[saved]`
  onto itself; the NPM Explorer no longer stacks a duplicate rack
  listener per open/close cycle; wizard panels honor the
  change-listener contract so Next/Finish revalidate as you type.
- Language servers that fail to launch now say why in the log.

## [1.3.0] — 2026-06-12

### Added
- **Session Resurrection** — the IDE survives its own death, mosh-style.
  The rack's running state is snapshotted continuously (every 5s, per
  project), so it survives clean quits, crashes, `kill -9`, and power loss.
  Return to a project whose last session died with tools running and a
  notification offers them back: one click restarts your dev server,
  watcher, and friends. Proven by SIGKILL-ing the IDE mid-serve and
  resurrecting vite with one click.
- **Persistent flight tape** — BLACKBOX's recorder journals to disk and
  reloads at startup, so the session timeline spans restarts.

### Changed
- Aim semantics: an explicit project choice (wizard, Workbench, Open…)
  is never clobbered by passive sources (persisted window state, the
  open-projects follower); passive sources may still follow each other.
- CI runs on Node 24 action majors (`checkout@v5`, `setup-java@v5`,
  `upload-artifact@v5`, `download-artifact@v5`) ahead of GitHub's
  2026-06-16 forcing date.

## [1.2.1] — 2026-06-12

### Fixed
- Three workflow bugs found by hand-walking the happy path:
  - Opening the Task Rack no longer re-aims the IDE at last session's
    project, clobbering the project you just created.
  - The flight recorder records from application startup, not from the
    first time a BLACKBOX is racked — the tape no longer misses the flight.
  - New projects start versioned: `git init` plus an initial commit, so
    TIMELINE and the platform's git integration work from minute one.

## [1.2.0] — 2026-06-12

### Added
- **BLACKBOX Flight Recorder** — every launch, exit, duration, and error
  on a scrollable session timeline, with per-device duration statistics
  and a slow-creep alarm that notices when a build quietly doubles.
- **SONAR Port Radar** — every listening port mapped to its owning
  process (docker containers labeled), with browse and confirmed
  one-click kill. EADDRINUSE, solved.
- **Docker Manager** shortcut in the Workbench's TOOLING shelf.

## [1.1.0] — 2026-06-12

### Added
- **HARBOR Docker Engine** rack device — ENGINE LED tracking the daemon,
  LCDs for containers up / images held / total reclaimable disk, and an
  always-safe PRUNE.
- **The Docker Panel** — a six-tab control room: the `system df` disk
  ledger with one-click per-category RECLAIM, live container management
  with browser-jump ports, image pull/tag/history/quick-run/cleanup,
  volumes, networks, and **Dockerize**: production multi-stage
  Dockerfiles, `.dockerignore`, and `compose.yaml` generated from the
  project's detected toolchain.

## [1.0.1] — 2026-06-12

### Fixed
- The branded splash screen, window icons, and About page actually ship.
  (A global `*.jar` gitignore silently excluded the `core.jar/` branding
  directories for the project's entire history; v1.0.0 artifacts showed
  the stock NetBeans splash.)

## [1.0.0] — 2026-06-12

Initial release. (Earlier in its life this project's entire UI displayed
"DON'T PANIC". We kept the spirit.)

### Added
- **The Task Rack** — a Reason-style rack where every web-dev task is a
  hardware device: knobs, LEDs, LCDs, and patch cables. Wire OK jacks
  together and one keypress runs your pipeline. 28 devices at launch,
  presets, patch persistence per project, and CI export of any patch to
  a GitHub Actions workflow.
- **Polyglot editing** — 30+ languages via TextMate grammars activated
  through NetBeans CSL; LSP providers for 31 MIMEs with ordered
  fallbacks; comment-aware spellcheck; typing intelligence; the NMOX
  Phosphor dark theme.
- **The Workbench** — a home base: current project with detected
  toolchain chips, open and recent files, projects, and the tooling shelf.
- **Project Studio** — templates (React, Vue, Svelte, Express, vanilla,
  TypeScript library, Python, Go), file CRUD, package.json editor.
- **Infra Designer** — Node-RED-style multi-cloud infrastructure flows
  for DigitalOcean, Hetzner, and Cloudflare, with cost estimates,
  dry-run planning, and live deploys.
- **Rock-solid guarantees, tested in CI with real tools** — a gauntlet
  that runs actual `npm install`/build/test/serve through the rack on
  every commit; a synchronous process reaper so quitting the IDE never
  orphans a dev server; human-readable failure modes; big-project
  performance rails.
- Installers for macOS (DMG), Windows (setup.exe), and Linux
  (tar.gz/deb), plus a portable zip — built and published by a
  tag-triggered release workflow.

[1.4.3]: https://github.com/NMOX/NMOX-Studio/compare/v1.4.2...v1.4.3
[1.4.2]: https://github.com/NMOX/NMOX-Studio/compare/v1.4.1...v1.4.2
[1.4.1]: https://github.com/NMOX/NMOX-Studio/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/NMOX/NMOX-Studio/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/NMOX/NMOX-Studio/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/NMOX/NMOX-Studio/releases/tag/v1.0.0
