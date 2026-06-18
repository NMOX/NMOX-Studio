# Changelog

All notable changes to NMOX Studio are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

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
