# Changelog

All notable changes to NMOX Studio are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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

[Unreleased]: https://github.com/NMOX/NMOX-Studio/compare/v1.3.0...HEAD
[1.3.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/NMOX/NMOX-Studio/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/NMOX/NMOX-Studio/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/NMOX/NMOX-Studio/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/NMOX/NMOX-Studio/releases/tag/v1.0.0
