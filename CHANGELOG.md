# Changelog

All notable changes to NMOX Studio are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [1.32.0] — 2026-07-04

DB Studio becomes a working DBA's tool: edit rows in the grid and apply
them as reviewable SQL, export any result, keep your queries, connect
from `.env`, and ask the planner what it's thinking. Closes debt ledger
#9 (results are read-only).

### Added
- **In-grid row editing with an honest Apply** — run a simple
  single-table SELECT (or just peek a table) against a SQL engine and
  the grid unlocks: edited cells tint amber, a chip counts pending
  edits, and **Apply…** shows the exact `UPDATE` statements it built —
  primary-key WHERE clauses, dialect-quoted identifiers — for review
  before anything runs. On failure it stops ("stopped after N of M"),
  keeps your edits for retry; on success the query re-runs so the grid
  shows database truth, not local hope. Grids that can't be edited
  safely say why in plain words: "Read-only — no primary key",
  "Read-only — not a single-table SELECT", "Read-only — document
  engine", "Read-only — <t> is a view". Primary-key cells aren't
  editable (the key is what addresses the row), NULL keys are refused,
  and typing `NULL` means SQL NULL.
- **Export CSV / JSON** — any result grid exports via a save dialog
  (UTF-8, default filename from the table), document engines included.
- **Persistent query history + saved queries** — every console run is
  journaled (newest-first, capped 50) into the project's `.nmoxdb.json`
  beside the connections and survives restarts; **Save…** names a query
  and puts it on the shelf for next session. Still no passwords in the
  file (test-pinned).
- **Connections from `.env`** — aim DB Studio at a project whose `.env`
  declares `DB_*` or `DATABASE_URL` (DB_* wins, all-or-nothing) and a
  quiet balloon offers to create the matching connection, dialog
  prefilled — password goes to the Keyring on save, never to disk. One
  offer per project per session, no modal ambush.
- **EXPLAIN button** — lights up for SELECT/WITH on SQL engines and
  runs the engine's native plan syntax (`EXPLAIN QUERY PLAN` on
  SQLite); the plan lands in a normal (read-only) result tab.

### Internal
- `SqlDialect` extracted as the one identifier-quoting seam
  (PeekQueries pinned byte-identical); `quote()` now doubles embedded
  quote chars so a hostile identifier can't break out. find-sec-bugs
  flagged ReDoS in a new numeric-literal regex — fixed by replacing the
  regex with a single-pass scanner, not by exclusion.
- dbstudio: 317 tests (was 163), 87.1% line coverage (floor 0.73),
  SpotBugs + find-sec-bugs at 0 findings.

## [1.31.0] — 2026-07-04

The REPL grows up: pick an engine from the rack, and missing interpreters
install themselves. Born from a live session — creating the Common Lisp
learning space on a machine without clisp meant a trip to the terminal.
Never again.

### Added
- **ENGINE knob on the REPL** — turn it to lisp, python, node, ruby,
  haskell, elixir, sqlite, redis… (26 engines, derived at runtime from
  the learning catalog's own REPL drivers, deduped) and the command
  field fills itself with the *correct* invocation — force-interactive
  flags included (`python3 -i -q`, `node -i`). Picking an engine also
  seeds its HINTS snippets (hints now work for any dialed engine, not
  just generated learning spaces) and its per-OS install command. Type
  your own command and the knob flips to CUSTOM — presets never fight
  manual control. Saved patches win over knob seeding on load.
- **INSTALL button on the REPL** — armed exactly when the dialed
  interpreter has a known install command and isn't running. Click →
  the install (brew/apt/choco, from the curated catalog) streams onto
  the REPL screen → "installed — press START". Dial haskell without
  ghci and the path from curiosity to a live GHCi prompt is two clicks.
  Never auto-starts; STOP-ALL kills an in-flight install.
- **The learning-space picker warns before you commit** — selecting a
  space live-probes its interpreter off-EDT and shows "requires clisp —
  ✓ found" or "✗ not found · brew install clisp" under the blurb,
  before anything is created.
- Catalog integrity test: the well-known interpreter spaces must carry
  mac/linux/windows install commands — no future space ships without
  its escape hatch.

## [1.30.2] — 2026-07-04

### Fixed
- **The ⌘Tab switcher labeled dev launches "java"** — the switcher name
  comes from the app bundle's Info.plist and only attributes when the
  JVM runs from inside a `.app`; no `-Xdock` flag can reach it. The DMG
  script gained `--app-only`: it stops after building the real bundle
  (embedded runtime included), giving dev play-testing full macOS
  fidelity — icon, menu name, and switcher label. Packaged users were
  always correct; this closes the last dev-launch branding gap.

## [1.30.1] — 2026-07-04

### Fixed
- **NPM Explorer is no longer a dead end without a project** — where it
  said only "No package.json found", it now lists the globally installed
  packages (`npm ls -g --depth=0`, sorted, with versions and a count),
  with Install sensibly disabled ("Open a project to install its
  dependencies"). npm's benign non-zero exits (extraneous/invalid
  findings) are tolerated — the JSON is parsed regardless — and a
  missing npm shows an install hint instead of silence. The parse seam
  is pure and test-pinned.

## [1.30.0] — 2026-07-04

DB Studio joins the platform: connections configured in the NetBeans
**Services** window now work in DB Studio directly. Plus the dev-launch
branding trilogy completes.

### Added
- **The Services bridge** — DB Studio's tree gains a "Services" branch
  listing every connection from the NetBeans Database Explorer
  (`ConnectionManager`), live-updated as you add/remove them there.
  Select one and it behaves like a native connection: browse tables,
  double-click-peek, run console SQL, grids/history/cancel. The bridge
  wraps the **live `java.sql.Connection` NetBeans manages**, so any
  database Services can reach — Java DB/Derby, Oracle, anything with a
  registered driver — runs in DB Studio's console, with NetBeans owning
  drivers, credentials, and lifecycle. Passwords never pass through our
  code; close() never closes NB's shared connection; Edit/Remove/Test
  stay honestly disabled ("Managed in the Services window"). Peeks on
  non-bundled engines use SQL-standard `FETCH FIRST n ROWS ONLY` with
  double-quoted identifiers (Derby and Oracle reject `LIMIT`).
- The JDBC statement loop was **extracted into a shared `JdbcCore`**
  (DbClient shed 142 lines, its SQLite e2e suite untouched), so both
  backends run one proven engine. The Services adapter is verified by a
  fully headless test through the real NetBeans API — driver
  registration → connection → our backend → temp SQLite → rows.

### Fixed
- **⌘Tab and the dock showed the Java coffee cup on dev launches** —
  the generated launcher hardcodes `-Xdock:icon=<app-parent>/
  nmoxstudio.icns`, which only the packaged .app satisfied. The icns is
  now a committed asset (built once from the same iconset the DMG uses)
  and the application build copies it to exactly that spot. Completes
  the dev-branding trilogy (window title and menu-bar name shipped in
  v1.29.2).

## [1.29.2] — 2026-07-04

Two more live-observation fixes, straight from the user's screen:

### Fixed
- **The macOS app menu said "nmoxstudio"** on dev/bin-script launches —
  the generated launcher defaults the dock name to the executable
  basename. The conf now sets `APP_DOCK_NAME="NMOX Studio"` (read only by
  the launcher's Darwin block; inert elsewhere). The packaged .app was
  already correct via its own wrapper.
- **The Welcome launchpad's TOOLING column didn't list DB Studio** — the
  one suite tab missing from it. "DB Studio  ⇧⌘7" now sits between
  Project Studio and API Studio.

## [1.29.1] — 2026-07-04

DB Studio's live click-through (the house tradition) said it plainly:
"a little raw, could use some working buttons." Every raw edge found,
fixed:

### Fixed
- **Double-clicking a table now shows its data** — the gesture every
  database tool owes its user. Double-click a table/collection/database
  in the tree and an engine-appropriate peek query fills the console and
  runs (`SELECT * … LIMIT n` with dialect quoting / Mongo `find` /
  Couch Mango). A Couch database the connection isn't aimed at explains
  itself instead of querying the wrong thing.
- **Async feedback you can't miss** — Test and Connect outcomes now
  raise balloon notifications (with the actual error) in addition to
  the status strip. A failed Test no longer looks like a dead button.
- **RUN gates honestly** — disabled until a connection is selected,
  with a tooltip saying why; no more silent no-op clicks. Re-entry
  during a run is blocked.
- **First-run guidance** — an empty DB Studio now says "No connections
  yet — click Add below to create one" in the tree and hints the
  double-click-to-peek gesture in the status strip, instead of a blank
  tree with greyed buttons.
- **SQLite Browse can create a new database** — the file chooser was
  open-style (couldn't select a file that doesn't exist yet); now
  save-style, matching "created on first use" reality.

## [1.29.0] — 2026-07-04

**DB Studio** — a first-class database management suite in its own tab,
alongside the Rack, the Infra Designer, and API Studio. And the whole
suite now greets you on first launch.

### Added
- **DB Studio (⇧⌘7)** — a new `dbstudio` module. Define connections,
  browse structure, run queries, read results in grids — for five
  engines minimum: **SQLite, PostgreSQL, MySQL, MariaDB, MongoDB, and
  CouchDB**.
  - **Batteries included**: drivers are bundled — MariaDB Connector/J
    (LGPL-2.1; one driver speaks both MySQL and MariaDB), PostgreSQL
    (BSD-2), SQLite/xerial (Apache-2), MongoDB `mongodb-driver-sync`
    (Apache-2). CouchDB needs no driver at all — it rides the shared
    HTTP client against Couch's REST API.
  - **Passwords never touch disk**: connection specs persist per project
    in `.nmoxdb.json`; secrets live only in the OS keychain via the
    platform Keyring.
  - **One tree, per-engine semantics**: connection → tables/views (SQL),
    collections (Mongo), or databases (Couch) → expand for columns or an
    honest one-document shape sample.
  - **Kind-aware console**: SQL engines get the SQL editor kit
    (highlighting included); document engines get a JSON editor — Mongo
    speaks Extended-JSON command documents (`{"find": "users", …}`),
    CouchDB speaks Mango selectors (bare selectors auto-wrapped, plus an
    `_all_dbs` convenience). Scripts split per statement — respecting
    strings, identifiers, and comments — and an error in one statement
    never stops the rest.
  - **Results**: one tab per statement (rows / update counts / errors),
    per-statement elapsed times, row-limit spinner with honest "first N
    rows only" truncation flags, and a History tab (last 50 runs,
    double-click to reload). Runs execute off the UI thread; CANCEL
    actually cancels in-flight SQL.
  - **⌘I reaches databases**: Quick Search finds connections and
    already-discovered tables/collections and jumps to them in the tab.
  - The engine is *proven*, not promised: 150 module tests including
    real end-to-end JDBC against SQLite files in CI, canned-JSON parse
    seams for CouchDB, and a 0.73 coverage floor from measured numbers.
- **The whole suite opens by default now.** A fresh install lays out
  **Workbench → Rack → DB Studio → Infra Designer → API Studio →
  Docker** as tabs, with the Welcome launchpad selected — discovery by
  seeing, not by hunting menus. The window system still remembers your
  arrangement: close a tab once and it stays closed. (Boot smoke
  verified: all six tabs construct headless, 6s boot unchanged.)

### Deliberately not done (ledger)
- Editing rows in the results grid — read-only results; `UPDATE`/
  Mongo update commands in the console are the honest v1.
- Mongo in-flight kill and cursor continuation (`getMore`) — CANCEL is
  documented as a no-op there; first batch only.
- Live Mongo/Couch integration tests — the logic is seam-tested against
  canned responses (the same status as the cloud providers' live calls);
  SQLite carries the real end-to-end burden.
- SSH-tunneled connections — HELM runs remote commands today; tunneling
  merits its own design if demand shows.

## [1.28.0] — 2026-07-04

The LAMP/LEMP sprint: the IDE audited from a senior LAMP/LEMP engineer's
chair — PHP, MySQL/MariaDB, nginx/Apache, Linux deploys — and every gap in
that daily loop built. PHP was already half a citizen (composer/phpunit/
Xdebug/intelephense); now it's a first-class one.

### Added
- **ARTISAN — the Laravel console** (the rack's 41st device), in Laravel
  brand red: SERVE / TEST / MIGRATE / FRESH(+seed) / QUEUE / ROUTES on the
  action knob, `composer.lock` version currency with an async
  latest-version probe, and real long-runner wiring — READY fires once on
  Laravel's "Server running on" line, URL emits on change, the SERVING
  gate drops when the process dies. Tinker is deliberately not a knob
  target — the REPL device handles interactive (the How-to-use card says
  so).
- **TYPEGUARD and GLOSS gained PHP lanes.** On a PHP project, TYPEGUARD
  AUTO runs `vendor/bin/phpstan analyse` and feeds phpstan's findings into
  the same diagnostics bus (squiggles included); GLOSS runs Laravel Pint,
  honoring WRITE/CHECK. The tsc and Prettier lanes are byte-identical and
  test-pinned.
- **IGNITION's php target serves for real**: `php -S 127.0.0.1:8000 -t
  public` when a composer-era `public/` docroot exists, root fallback
  otherwise.
- **Apache config editing** — `.htaccess`, `httpd.conf`, `apache2.conf`,
  and `*.vhost` now highlight (pinned MIT grammar, self-contained), with
  `#` comment-toggle and comments-only spellcheck. Generic `.conf` is
  deliberately not claimed. The "A" in LAMP, finally.
- **PHPUnit in Run Focused Test** — the test under the caret runs via
  `vendor/bin/phpunit --filter <name>` (global fallback), matching both
  `public function testX` and `#[Test]` shapes.
- **"PHP Web (LEMP)" project template** — 14 honest files: guarded front
  controller that works pre-`composer install` and doubles as a cli-server
  router (`composer serve`), PSR-4 `src/` + a passing PHPUnit test,
  `phpstan.neon.dist` (so the TYPEGUARD lane works out of the box),
  `.env.example`, a working three-service `docker-compose.yml`
  (nginx + php-fpm + MariaDB), the matching `docker/nginx.conf`
  (front-controller `try_files`, `fastcgi_pass`), and
  `deploy/cloud-init.yml` — a commented LEMP bootstrap you paste into a
  droplet's user_data in the Infra designer. Validated for real:
  `php -l` clean, compose config passes, PHPUnit ran the generated test
  green.
- **LAMP Bench rack preset** — CRATE (composer) fans out to VERITAS
  (phpunit) + TYPEGUARD (phpstan) + GLOSS (pint --test) with IGNITION
  serving and everything on the MONITOR bus; the template's rack aims at
  it automatically.
- **Dockerize learned PHP** — multi-stage `composer:2` →
  `php:8.3-fpm-alpine` Dockerfile with an nginx sidecar in the generated
  compose (port 80), plus `vendor/`+`.env` in `.dockerignore`.
- **Environment Doctor probes the LAMP stack**: composer, mysql client,
  nginx (whose version prints to stderr via `-v` — handled), apachectl.
- **The Database Explorer was already on board — now documented.** The
  app ships NetBeans' full DB tooling (10 modules: connections, schema
  browsing, SQL editor with result grids, MySQL integration). Point it at
  your MySQL/MariaDB from the Services window; bring the Connector/J jar
  and the driver-registration UI does the rest.

### Fixed
- **Dockerize crashed writing nested output paths** — files like
  `docker/nginx.conf` hit a missing-parent `NoSuchFileException`; parents
  are now created.
- **Every project template's README rendered its headings as code
  blocks** — the generator emitted 12-space-indented Markdown (a
  text-block min-indent bug). All templates now emit flush-left READMEs.

### Deliberately not done
- **No MySQL learning space** — learning-space REPLs launch local
  interpreters; `mysql` needs a live server to connect to, which breaks
  the type-in-and-learn model. The SQLite space already teaches SQL
  against a real engine.

## [1.27.0] — 2026-07-03

The coverage sprint: raise test coverage across the whole codebase to the
point where every module's *testable* logic is exercised, then lock it in
with per-module JaCoCo floors so it can't quietly regress. "Full coverage"
here means honest coverage — pure-Swing windows, dialogs, and canvases that
can't be unit-tested without a live windowing system are excluded by name
with a written reason (mirroring the SpotBugs-exclude discipline), not
chased with brittle tests; everything with a branchable path is tested.

### Added
- **~320 new unit tests** across editor, rack, tools, apiclient, infra,
  ui, and project — completion providers and their item classes, editor
  actions (comment-toggle, smart-break, focused-test command building),
  the LSP catalog, every rack device's command-building and `receive()`
  logic, the control-widget models (Knob/Led/VuMeter/LcdDisplay/Toggle),
  API Studio's table models and header grader, the infra DeployPlanner /
  NodeKind cost math / graph / GraphIO, and the ui-resident action logic.
- **Per-module coverage floors on all eight code modules.** Six existing
  floors were raised to just under the achieved level; **project and ui
  gained their first floors.** Now enforced (LINE coverage of the testable
  surface): core 0.78, apiclient 0.78, infra 0.73, rack 0.63, editor 0.55,
  tools 0.54, project 0.50, ui 0.12.
- **An honest coverage-exclusion policy** in the root `pom.xml`: pure-UI
  glue is excluded at the JaCoCo instrumentation level (so the report and
  the floor both reflect the testable surface), each entry a named class
  with a one-line rationale. Control widgets are *not* excluded — their
  model logic is tested; only `paint()` stays uncovered.

### Fixed
- **`WorkspaceTrust` could overflow the preferences store and break trust
  on a long-lived install** — a real latent bug the extra tests surfaced.
  Trusted project paths were persisted as one `File.pathSeparator`-joined
  string under a single preference key; `java.util.prefs` caps a value at
  8 KB, so a user who trusted enough projects over time would eventually
  hit "Value too long" on the next trust and every subsequent one. It's
  now **one entry per path** (hash key, path value — neither the value nor
  the key can overflow), with automatic migration off the legacy key. A
  regression test trusts 200 long paths (≈24 KB joined, three times the old
  ceiling) without throwing. On CI this stayed invisible because the prefs
  node starts empty each run; it only bit once the local suite had
  accumulated enough trusted paths across the day.

### Coverage before → after (module LINE %, testable surface)
`core 68→83 · editor 43→59 · tools 21→58 · rack 51→67 · apiclient 38→83 ·
infra 46→77 · project 8→55 · ui 0.4→15`. ui stays low by design — it is a
thin presentation shell whose logic (the PWA/Standards/Doctor kits, the
learning catalog, update-tag parsing) lives in rack/core and is tested
there; the floor guards the small slice of genuine ui-resident logic.

## [1.26.0] — 2026-07-03

The complete-system sprint: everything the debt ledger recorded as
half-done "with a reason" was taken back up and finished — or its
deferral was re-examined against the code and firmed into a decision.
Six feature-shaped debts (0a–0f) shipped; the seven refactor-shaped
ones were each re-inspected, and two of them (the toolchain switches
and the faceplate boilerplate) turned out to have no duplication worth
removing once read again rather than remembered. A deferral you can
defend after re-reading the code is a decision, not a guess.

### Added
- **Rack undo/redo (⌘Z / ⇧⌘Z).** Every structural rack edit — add,
  remove, move a device; connect or disconnect a cable — is now
  reversible, with a 100-deep bounded history. Removing a device and
  undoing it brings the device back *with its cables re-patched*. Bulk
  operations (default-rack load, patch autoload) are deliberately
  non-undoable so the history starts clean on the patch you loaded.
- **Quick Search reaches into API Studio and the infra designer.** ⌘I
  now finds API requests by method and name (jumps to the request in
  API Studio) and infra nodes by name and kind (selects them on the
  canvas) — cross-tab navigation that previously stopped at projects
  and rack devices. (Closes ledger 0a.)
- **"Sync from cloud" is genuinely multi-provider.** The infra
  designer pulls live resources from DigitalOcean, Hetzner Cloud
  (servers, networks, load balancers, volumes, firewalls, floating
  IPs), and Cloudflare (zones → DNS records) in one sweep. Each
  provider is isolated — one cloud's outage or bad token reports as a
  per-provider failure without aborting the rest — and re-syncing
  refreshes existing nodes in place instead of stacking duplicates.
  (Closes ledger 0b.)
- **TAIL and TEMPO survive a crash.** The two timer devices now report
  as resumable from their arm switch (FOLLOW / CLOCK) and re-arm on
  resume, so a kill -9 mid-follow or mid-clock comes back running —
  without inflating the status line's process "running" count, which
  stays process-only. (Closes ledger 0d.)
- **Eight more Navigator outlines.** Structure view (⌘7) now populates
  for Haskell, OCaml, R, Perl, Julia, F#, Crystal, and Zig — 43 mimes
  with an outline, up from 35. (Closes ledger 0e.)
- **An Options checkbox for the startup update check.** Tools >
  Options > NMOX Studio > "Check for updates on startup" flips the
  `updateCheck` preference that previously had no UI. (Closes ledger
  0f.)
- **SOLDER exports to CI.** The any-command device now emits a GitHub
  Actions `run:` step alongside the other build steps; PREFLIGHT
  stays out on purpose (in CI the workflow *is* the ship gate).
  (Closes the SOLDER half of ledger 0c.)

### Changed
- The debt ledger (`docs/engineering/tech-debt.md`) was rewritten:
  0a–0f moved to "Closed by v1.26.0," and the refactor items 1–7 were
  re-audited with fresh evidence. #7 (rack-palette startup cost) was
  measured — a 7-second cold boot is dominated by JVM warm-up and
  first-run module install, not the ~0.3s palette build — and firmed
  to "won't fix until a profiler names it." #1 and #2 firmed to
  won't-fix once re-reading the code showed the "duplication" was a
  shared *idiom*, not shared *values* (each device places its
  transport cluster at its own coordinates; each toolchain switch maps
  the same enum to a *different* verb).

### Tests
- `RackUndoTest` (add/undo/redo, cable restoration, redo invalidation,
  bulk-load-not-undoable), `TimerResumeTest` (idle-not-resumable,
  armed-resumable-not-live, resume-rearms), `CloudSyncTest` (all
  provider endpoint shapes, failure isolation, dedupe-refresh), plus
  the new search-provider and Options-panel tests. Full suite green;
  DeviceContractTest's 241 assertions unchanged.

## [1.25.1] — 2026-07-03

The cleanup: loose ends tied off, including two real bugs the deferred
live click-throughs caught the moment the screen unlocked.

### Fixed
- **REPLs that buffer piped stdin now force interactive mode.** The
  live smoke proved the engine perfect and the data wrong: python3
  (and node, lua, julia, ts-node) treat piped stdin as a script and
  execute only at EOF — you typed, nothing answered until STOP. Their
  catalog commands gained the force-interactive flags (`python3 -i
  -q`, `node -i`, …): type an expression, read the answer immediately.
- **The Environment Doctor probes go with `go version`** — the one
  toolchain that rejects `--version` no longer shows a garbled status
  line for a perfectly healthy install.

### Verified live (no code change)
- The v1.25.0 Welcome launchpad, the Environment Doctor (24 of 32
  tools found on this machine, with `clisp ✗ brew install clisp`
  rendered exactly as designed), and the full Learning Space flow —
  picker via ⇧⌘L, Common Lisp leading the catalog, Python space
  generated with tutorial + seeded REPL, live type-in round trip.

### Tidied
- README gained the v1.25 features section; CLAUDE.md caught up;
  the debt ledger records the one new deferral (an Options checkbox
  for the update-check preference); the ui module got its first test
  (UpdateCheck's tag parsing); one unused import; five stale local
  branches pruned.

## [1.25.0] — 2026-07-03

The daily-driver polish: after twenty-four releases of capability, the
weakest surfaces were the first ones a user meets. This release makes
the front door, the environment, and the upgrade story honest.

### Added
- **The Welcome screen is a launchpad again.** It was frozen at v1.0 —
  three buttons, unaware of everything since. Now: a Start column
  (New Project / New Experiment / New Learning Space / Open Folder,
  shortcuts shown), a live Recent column (your actual projects, click
  to aim the studio — refreshed every time the tab shows), a Tooling
  column with every window and its keystroke, and an honest footer:
  the stamped version plus "What's new ↗" to the release notes.
- **Environment Doctor (Tools menu).** The studio leans on ~40
  external tools — the core four, a toolchain per language lane, and
  an interpreter per learning space — and each device already speaks
  up alone. The Doctor sweeps them all at once: one table, every tool
  probed live with `--version` through the same hardened launcher the
  devices use — ✓ with its version line, or ✗ with the install
  command that fixes it. The checklist extends itself from the
  learning catalog, so new spaces are covered automatically.
- **Update awareness.** Once a day, fifteen seconds after startup and
  entirely off the EDT, the studio asks GitHub for the latest release
  tag and — only when it outranks the version this build was stamped
  with — shows one quiet notification linking to the releases page.
  Dev builds never check, offline never nags, and the `updateCheck`
  preference turns it off. Version arithmetic is numeric (1.9 < 1.24),
  tested in core.

## [1.24.0] — 2026-07-03

Learning Spaces: projects that exist to be learned from. Pick a
language, framework, or library and the studio generates a real
project — sample code, a tutorial that walks it, and a rack already
wired with a REPL pointed at the right tool. Practice Lisp and you get
a CLISP prompt, starter expressions, and something to type in seconds.

### Added
- **A real REPL in the rack.** The new REPL device launches an
  interactive interpreter (clisp, python3, node, ghci, iex, irb,
  sqlite3, redis-cli — anything with a prompt), keeps its stdin open,
  and lets you type an expression and read the answer in the
  scrollback. HINTS lists the starter snippets a learning space seeds.
  This is a genuinely new capability: the command devices run with a
  closed stdin so a prompt can never hang them — a REPL is nothing but
  a prompt, so it needed its own interactive-process engine
  (`InteractiveProcess`), proven end to end in tests against `cat`.
- **New Learning Space… (File menu, ⇧⌘L).** A searchable picker of
  **51 spaces** across languages, frameworks, and libraries. Choose
  one and the studio writes the sample files, a TUTORIAL.md that walks
  them (with the install command for your OS), and a pre-wired rack —
  then opens all three. REPL spaces get a seeded REPL; run-based spaces
  (dev servers, builds) get SOLDER wired into a MONITOR.
- **The catalog is data, not code** (`learn-catalog.json`) — 25
  languages (Common Lisp, Python, JS/TS, Ruby, Go, Rust, Java via
  jshell, C/C++/C#, Swift, Kotlin, PHP, Lua, Haskell, Elixir, Erlang,
  Clojure, Scala, Julia, R, Perl, Bash, SQLite), 13 frameworks (React,
  Vue, Svelte, Angular, Next.js, Express, Django, Flask, Rails,
  Phoenix, Spring Boot, Laravel, ASP.NET — the console-bearing ones
  wire their real REPL: rails console, manage.py shell, iex -S mix,
  artisan tinker), and 13 libraries (NumPy, Pandas, PyTorch, jQuery,
  D3, Three.js, Redux, RxJS, Tailwind, GraphQL, Prisma, Redis, Docker
  Compose). Each entry is honest about what it needs: if a tool isn't
  installed, the REPL says so and the tutorial carries the install
  command. New spaces are a JSON edit, never a recompile.

### Notes
- Spaces persist under `~/.nmox/learn/<slug>`, pre-trusted, reusing the
  directory for the same slug rather than piling up copies.

## [1.23.0] — 2026-07-03

The completeness release: four audits asked "did every idea ship
whole?" — every dead-end, asymmetry, and dangling promise they found
is now finished or honestly ledgered.

### Added
- **Experiments, managed (File → Experiments…, ⇧⌘X).** The New
  Experiment dialog has promised "Promote it later" since v1.11 — now
  you can. Lists every experiment with template and age; Open aims the
  studio, Promote graduates a keeper (move out of ~/.nmox/experiments,
  drop the marker, git init, opened for real — recents and all),
  Discard stops anything running there and deletes the tree, still
  refusing anything unmarked.
- **Hetzner is a first-class cloud.** Drift Refresh, Destroy stack,
  cloud-init user_data, and server-IP capture (ssh command) now work
  for all six Hetzner kinds exactly as for DigitalOcean; Cloudflare
  DNS records drift-check and destroy through their zone. R2 buckets
  stay honestly unverifiable (the API returns no addressable id).
- **Any-provider live deploys.** The deploy gate demanded a
  DigitalOcean token even for pure-Hetzner stacks — now a stack goes
  live when every provider it actually uses has its token, and dry-run
  names the missing ones. En route this killed a real bug: the old
  gate read tokens from a different preferences node than the Tokens
  dialog wrote, so stored DO tokens were invisible to it.
- **VITALS gates all four Lighthouse categories.** The GATE knob grew
  `best`, `seo`, and `all` (append-only — patched knob indexes stay
  stable); the failure LCD names exactly which category closed the gate.
- **Two presets for the invisible 60%.** Ship Gate chains prod build →
  Lighthouse floor → bundle budget → PREFLIGHT verdict → armed deploy;
  Dev Intelligence surrounds the dev server with clocked health probes,
  SONAR port sweeps, BLACKBOX, and TAIL on the console.
- **Navigator outline for the BEAM + Lisp languages** (32 → 35 mimes):
  Elixir defmodule/def/defp/defmacro nested by indentation, Clojure
  top-level forms as a flat list, Erlang module attributes plus
  column-0 function clause heads.

- **Window shortcuts for the all-day tools**: API Studio ⇧⌘8, Infra
  Designer ⇧⌘9 — parity with the rack (⌘9) and Docker (⌘8).

### Fixed
- **Wizards validate their inputs.** Standards Kit refuses a blank
  name, an unparseable site URL, or a non-address security contact
  before writing anything; PWA Kit checks the artwork file exists and
  is readable (full path in the message) and refuses a blank app name.
- **Racking a preset with BLACKBOX no longer scans your home
  directory** — the preset builder's throwaway rack aimed at
  ~/. by default, and BLACKBOX's changed-since-last-green walk plus
  REFLEX's watcher baseline could hang for minutes. It aims at an
  empty scratch directory now.
- The toolbar button says **"Sync from DigitalOcean"** — which is what
  it does; the deploy tooltip and destroy confirmations name each
  node's actual provider.

### Removed
- **Six phantom NpmService methods** (addPackage, removePackage, update,
  audit, listPackages, init) — zero non-test callers; the rack's CRATE
  and AUDIT devices superseded them.

### Deferred with reasons (docs/engineering/tech-debt.md)
- Quick Search for API Studio/infra nodes; multi-provider sync;
  CI-export scope for gates/observers; TAIL/TEMPO resurrection;
  outlines for the remaining 8 grammar-only languages.

## [1.22.0] — 2026-07-03

The Snow Leopard release: no new features. Six parallel audits swept
the whole codebase; what they found got fixed, consolidated, tested,
or honestly written down as deferred. Everything is faster, cleaner,
and truer — nothing is different.

### Fixed
- **Explicit UTF-8 at 12 file I/O sites** that used the platform
  default charset — including session-state JSON (resume could corrupt
  on non-UTF-8 systems), CI-workflow export, docker-compose writes,
  and package.json parsing.
- **Wizard writes left the EDT.** Standards Kit and PWA Kit did disk
  I/O (and PNG encoding) inside the event dispatch; both now run on a
  request processor and hop back for the report.
- **API Studio autosave no longer fails silently.** A chronically
  failing 800ms autosave meant silent data loss; it now warns once per
  failure streak and logs the cause.
- **Two timers that never stopped** (status-line 2s poll, session
  snapshots at 5s) are lifecycle-bound.
- **Relaunching a running command skewed its elapsed readout** — start
  times are now captured per launch.
- **PING's history capped at record time**: fifty retained multi-MB
  response bodies was a leak, not a console log.

### Changed
- **JOptionPane is gone.** 26 call sites across 12 rack files now use
  the platform's DialogDisplayer — themed, consistent — and raw
  exception dumps became task-oriented messages ("Could not save the
  patch: …").
- **ProcessSupport and ToolLocator moved to core.** The editor no
  longer depends on the rack just to launch processes, and the tools
  module's npm probe finally gets the augmented PATH (npm was
  "missing" when the IDE launched from Finder). The hand-rolled copy
  in the template generator — with its Windows-breaking hardcoded
  /dev/null — is gone.
- **One HTTP connection pool** (core HttpClientFactory) replaces four
  per-module clients; **one JsonUtil** replaces three copies of the
  JSON sniff/pretty-printer (String-only API — org.json types never
  cross NBM module boundaries).
- **FlightRecorder's journal loads off the boot path**, and the boot
  smoke test now prints a boot-to-exit time CI can trend.

### Tests & docs
- Coverage backfill for the untested pure logic in core, tools,
  apiclient, and infra, with JaCoCo floors set from measured coverage.
- Truth pass: CLAUDE.md caught up four releases (v1.17 → v1.21, 39
  devices); ~30 v0.x aspirational docs gained historical banners; three
  test scripts that checked classes that never existed were deleted;
  docs/engineering/tech-debt.md rewritten as the honest current ledger
  — every open item carries the reason it was deferred.

## [1.21.0] — 2026-07-03

The PWA sprint: one wizard and the aimed project becomes installable —
icons forged in-process, a service worker that precaches what the
project actually has, and index.html wired without clobbering a thing.

### Added
- **PWA Kit wizard (File → PWA Kit…).** App name, short name, theme
  and background colors, an icon source, a caching strategy — and the
  project gains the full installability set. Existing files are never
  overwritten; the report says exactly what was written, skipped, or
  wired.
- **Icon generation, zero tools required.** Plain Java2D renders the
  whole set: icon-192/512 (rounded plate, transparent corners), the
  maskable pair (full-bleed, glyph held in the W3C safe zone), and an
  opaque apple-touch-icon. Two sources: a 1–2 letter monogram on your
  theme color for projects without art yet, or your own image scaled
  crisply (progressive halving) to every size. The manifest's icon
  promises finally ship with the pixels to back them.
- **A service worker written to be read.** Vanilla sw.js — versioned
  cache, precache on install, stale-cache cleanup on activate — with a
  strategy you choose: app shell (cache-first, instant loads,
  offline-ready) or network-first (always fresh, cache fallback).
  GET-only, same-origin caching, and an offline.html fallback for
  navigations, generated in your theme. The precache list is built
  from the files the project actually has, not a guess.
- **Installability-complete manifest.** site.webmanifest with both
  icon purposes (any + maskable), scope, display, and your colors —
  beyond the Standards Kit's baseline.
- **index.html wired idempotently.** Manifest link, theme-color meta,
  apple-touch-icon link, and the service worker registration are
  inserted before </head> — each piece only if missing. Running the
  wizard twice changes nothing.

## [1.20.1] — 2026-07-03

Patch: a defect found by driving the released IDE with computer
automation — clicks, keystrokes, and on-disk assertions against the
running app.

### Fixed
- **Follow-up dialogs no longer stack behind the main window.** A
  dialog opened in the same event dispatch that was still disposing its
  predecessor (the Standards Kit outcome report after the wizard, the
  New Experiment error report, API Studio's variables editor after
  naming a new environment) could open *behind* the main window —
  modal, invisible, every menu action greyed: a soft-locked app with no
  visible reason. Confirmed live via window enumeration; each follow-up
  dialog is now deferred one dispatch so its predecessor is fully gone
  before it shows.

### Verified live (no code change)
- The automation run also battle-tested the paths CI could only
  logic-verify: a real HTTPS send through API Studio (200, timed,
  sized, headers graded on the Standards tab), .editorconfig
  save-enforcement in the running editor (trailing whitespace trimmed,
  final newline added, on disk), experiment lifecycle (scaffold →
  no-git marker → external-delete resilience), Standards Kit files
  spec-checked on disk, and the Infra Designer's fit/zoom controls.

## [1.20.0] — 2026-07-03

The standards sprint: the web's specs, supported with gusto — text
standards enforced on save, well-known files generated by wizard,
security headers graded on every request, and WCAG as a shipping gate.

### Added
- **.editorconfig is honored, not just highlighted.** Every save now
  applies the standard for real: `trim_trailing_whitespace` and
  `insert_final_newline` (both directions), with full spec semantics —
  glob sections (`*`, `**`, `?`, `[seq]`, `{alt}`, `{n..m}`), upward
  file discovery stopping at `root = true`, closer-file and
  later-section precedence. Minimal-edit application: the caret keeps
  its place. No .editorconfig, no change.
- **Standards Kit wizard (File → Standards Kit…).** One dialog and the
  project gains the web's well-known files, each correct to its spec:
  robots.txt (RFC 9309) with a Sitemap pointer, sitemap.xml
  (sitemaps.org protocol), site.webmanifest (W3C, with the members
  installability requires), .well-known/security.txt (RFC 9116 —
  required Contact and a genuinely RFC 3339 Expires one year out), and
  humans.txt. Existing files are never overwritten.
- **Security headers graded on every send.** API Studio responses gain
  a Standards tab: HSTS, CSP, X-Content-Type-Options, anti-clickjacking
  (frame-ancestors or X-Frame-Options), Referrer-Policy,
  Permissions-Policy, and COOP — value-aware verdicts (a short max-age
  warns; unsafe-inline warns) with a deterministic letter grade and a
  named fix for every miss.
- **Accessibility is a shipping gate.** VITALS' floor learns which
  standard it holds: the new GATE knob selects perf, **a11y (WCAG)**,
  or both — an inaccessible page can now close the deploy gate exactly
  like a slow one.

### Fixed
- The security.txt generator's first draft emitted an Expires without
  seconds — invalid RFC 3339. Its own spec test caught it before it
  shipped; that is what the spec tests are for.

## [1.19.0] — 2026-07-02

API Studio — a Postman-style suite in its own tab, beside the rack and
the infra designer.

### Added
- **API Studio (Window → API Studio).** A new module and tab for
  managing and testing HTTP APIs:
  - **Collections** of saved requests in a tree, with add/delete.
  - A **request builder**: method, URL, query params and headers
    (toggleable rows), request body, and auth (bearer or basic).
  - **Environments** with `{{variable}}` substitution, so one saved
    request travels from localhost to staging to prod by switching the
    environment instead of editing URLs.
  - **Tests**: per-request assertions — status is, time under N ms,
    body contains, JSON has path (`data.user.id`, `items.0.sku`),
    header present — evaluated on every send, green/red per line. A
    probe becomes a check.
  - A **response viewer**: status, time, size, pretty-printed JSON
    body, and response headers.
  - The workspace **persists as `.nmoxapi.json`** beside the project,
    exactly like the rack patch and the infra design. Secrets belong
    in environment variables kept out of source control (the file is
    committable).

## [1.18.0] — 2026-07-02

The second-cut sprint's final tranche: PING grows into a REST console,
and the infra designer's dialogs join the platform.

### Added
- **PING is a REST console now.** HEADERS carries auth and content
  types (session-only — tokens never persist into the committable
  patch, the ATMOS rule); VIEW opens a console with the last 50
  exchanges, pretty-printed JSON responses, and one-click **Replay**.
  A dialed Content-Type wins over the JSON sniff.

### Changed
- **The infra designer speaks the platform.** All twelve raw
  `JOptionPane` dialogs — deploy confirmation, destroy-stack, token
  entry, sync/refresh results — are now `DialogDisplayer`
  notifications and dialogs: correctly parented, keyboard-correct, and
  consistent with the rest of the IDE. These are the highest-stakes
  confirmations in the app (they spend money); they deserved it.

## [1.17.0] — 2026-07-02

The infrastructure designer, deepened: it opens fitted, provisions
servers that configure themselves, and hands you the way in.

### Added
- **Cloud-init on droplets.** A droplet's new user_data field flows
  into the create body, so a server configures itself on first boot
  (install nginx, pull your app, whatever) — infrastructure as one
  design, not a design plus a runbook.
- **Copy SSH command from a node.** Once a droplet is deployed (or
  imported by Sync), its public IP is remembered and persisted; the
  node's right-click menu offers "Copy SSH command (root@…)". The
  designer stops being a place you look at servers and becomes the way
  in to them.
- **Deploy log.** Every deploy appends a timestamped, per-resource
  record to .nmox/deploy-log beside the project — a half-deployed
  stack is legible after the fact, not a hunt.

### Fixed
- **The Infra Designer opens at a sane zoom.** Fit ran before the
  window had a size, so dividing by a zero-width viewport slammed the
  zoom to its floor on every open — the canvas always started "way
  out". Fit now waits for a real layout pass, contains the design
  without ever magnifying it (small designs stay at natural size), and
  centers the content. The toolbar gains − / Fit / + zoom buttons.

## [1.16.0] — 2026-07-02

Three second-cut devices: the rack reaches your servers, watches your
certificates, and weighs your bundles.

### Added
- **HELM — the rack reaches your servers.** Dial user@host and a
  command; it runs over ssh (BatchMode: key auth only, no hanging
  prompts) with output on the bus and OK/FAIL/DONE jacks. Patch
  LAUNCHPAD OK → RUN and a deploy finishes with a remote migration or
  service restart; the HOST jack takes a cable.
- **BEACON — cert & uptime sentinel.** One CHECK answers what pages
  people at 3am: is it up, and how many days on the TLS cert? Dial
  MIN DAYS and a cert inside the window fires FAIL. Patch TEMPO BAR →
  CHECK to watch production on a clock.
- **PRISM — bundle-size gate.** Weighs the build output; MAX sets the
  budget. Patch FORGE OK → MEASURE and bundles over budget fire FAIL
  before they ship. Bundles grow one innocent import at a time —
  PRISM holds the line.

## [1.15.0] — 2026-07-02

Bun and Deno, first-class. The 2026 runtimes stop being "Node with
extra steps": their manifests are detected with precedence, every AUTO
device speaks the right binary, and CI export sets them up properly.

### Added
- **Bun toolchain.** `bun.lock`/`bunfig.toml` beside a package.json
  means Bun, not Node: CRATE runs `bun install/update/outdated`,
  VERITAS `bun test` (re-run failed via `-t`), FORGE `bun run build`,
  IGNITION `bun run start`, and the CI exporter emits
  `oven-sh/setup-bun`.
- **Deno toolchain.** `deno.json`/`deno.jsonc` detection: CRATE
  `deno install/outdated`, VERITAS `deno test` (re-run failed via
  `--filter`), FORGE `deno task build`, IGNITION `deno task start`,
  CI export via `denoland/setup-deno`. Both register as real platform
  projects (Open Project works on a bare Deno repo).

## [1.14.0] — 2026-07-02

Infra truth and teardown: the designer stops being create-only — the
canvas can now ask the cloud what is really there, and take a whole
stack down in one decision with the bill in view.

### Added
- **Refresh — drift detection.** Every deployed node is re-checked
  against the provider API: a droplet deleted in the cloud console
  stops claiming "live" and reports **drifted: deleted in cloud**
  (its stale id is released so re-deploy works). Kinds without a
  per-resource read endpoint say "unverifiable" honestly instead of
  guessing.
- **Destroy stack — one-click teardown with the bill in view.** The
  confirmation names every deployed resource and what destroying them
  saves ("Destroy 6 cloud resources, saving ~$48.00/month?"), then
  tears down in reverse dependency order — attachments and dependents
  before the resources they lean on. Individual failures don't strand
  the rest; the design stays on the canvas, ready to deploy again.

## [1.13.0] — 2026-07-02

Testing rigor: failures get names and one-click re-runs, and coverage
and throughput join quality as gates that can close the OK jack.

### Added
- **VERITAS names its failures.** Failing tests are captured by name
  across runner families (jest/vitest ✕ lines, pytest FAILED node ids,
  cargo, go); the FAILURES button lists them and **Re-run failed**
  runs exactly those — `jest -t`, `vitest -t`, pytest node ids,
  `cargo test <names>`, `go -run` — no full-suite penance for one red
  test.
- **Coverage is a number and a floor.** With COVER on, the measured
  percentage (istanbul, pytest-cov, go -cover) lands on the tally LCD
  (`P:41 F:0 C:85%`); dial MIN COV and a green-but-thin suite fires
  FAIL instead of OK. Unmeasured coverage never gates — the device
  refuses to punish runners it can't read.
- **GAUNTLET can fail a pipeline.** MIN R/S puts a throughput floor
  under the bench: below it, FAIL fires — a load test that always
  "passes" can't gate anything. Both floors ride the
  `overallSuccess` hook, so LEDs and jacks tell the same truth.

## [1.12.0] — 2026-07-02

Three devices that close the last gaps between "runs your toolchain"
and "runs your whole web workflow" — custom steps, log files, and web
quality as a shipping gate.

### Added
- **SOLDER — any command as a rack unit.** `make seed-db`,
  `./scripts/fixtures.sh --reset` — whatever you type runs with the
  full treatment: OK/FAIL/DONE jacks, output on the bus, CI export.
  Parsed to argv with quotes respected, never handed to a shell.
- **TAIL — tail -f on the patch bay.** Servers that write
  `logs/app.log` were invisible next to their stdout. Dial the path,
  flip FOLLOW, and appended lines ride the OUT jack into PHOSPHOR.
  Log rotation resets cleanly to the new head.
- **VITALS — the web quality gate.** Lighthouse headless against the
  dialed URL; performance, accessibility, best-practices and SEO land
  on four meters. Dial MIN and a slow page fires FAIL instead of OK —
  patch SURGE URL → URL, READY → RUN, OK → LAUNCHPAD and nothing
  slow, inaccessible, or sloppy ships. Built on a new
  `CommandDevice.overallSuccess` hook: a device's verdict can demand
  more than "the tool didn't crash", and the LEDs tell the same truth
  as the jacks.

## [1.11.0] — 2026-07-02

Daily-driver foundations and a crispness pass: the fixes that make
living in the studio all day safe — switching projects can no longer
kill work behind your back, the environment your commands run in is
finally file-based, honest, and leak-free — and the app itself stops
carrying another IDE's furniture.

### Added — the platform, used properly
- **Experiments: throwaway work is first-class.** File → New
  Experiment… (Cmd+Shift+E) generates any template into
  `~/.nmox/experiments` — no git repo, no recents pollution, the
  directory pre-trusted so devices run without prompts — and aims the
  rack at it. Keepers graduate (promote = move + git init + marker
  removed); the rest are discarded, which stops anything running there
  first and refuses to delete unmarked directories by contract.
- **Quick Search knows the product.** The toolbar search (Cmd+I) now
  matches recent projects (Enter re-aims the IDE through the switch
  guard) and the device catalog (Enter racks the device) alongside the
  platform's actions and files.
- **The status line answers "is anything running?"** A green lane
  count with device names on hover appears while servers, tunnels, or
  watch builds are live — visible from any window, gone when quiet.
- **One Options category.** Tools → Options → NMOX Studio, with Rack &
  Cloud and Format on Save as subpanels — no more settings scattered
  across top-level categories.

### Changed — crisp
- **Evicted the VCS museum.** Mercurial, Subversion, the CVS installer
  shim, the Jenkins (hudson) client, and the Selenium server bridge no
  longer ship — 12 modules a web dev in 2026 never asked for. The Team
  menu is Git now. (Verified by the boot smoke test: the module system
  comes up clean without them.)
- **One build story, not three menus.** The custom top-level "Build"
  menu — whose single item bypassed the real per-toolchain commands for
  a legacy manager with a raw popup — is gone, along with the duplicate
  "New Web Project…" item. Run/Build/Test/Clean live in the Run menu
  (F6/F11), wired to the rack like everything else.
- **Cmd+O opens files again.** The platform's "Go to Type" (a Java
  dialog) was silently stealing the binding.
- **Keys for the product's own surfaces**: Cmd+9 Task Rack, Cmd+8
  Docker Panel (now also in the Window menu at last), Cmd+0 Workbench,
  alongside Cmd+Shift+P Switch Project.
- **Startup is calmer.** The Infra Designer no longer squats in the
  editor area from minute one (Window menu / one click away), and the
  Welcome tab steals focus only on the very first launch instead of
  every launch.
- **The macOS menu bar says "NMOX Studio", not "nmoxstudio"** — the
  DMG launcher and run.sh now pass the dock name to the JVM (a
  macOS-only flag, kept out of the shared conf where it would break
  Linux).
- **The About dialog stops describing a different product.** It called
  us a "Professional Media Development Environment, Version 1.0.0";
  the copy now matches reality, and the never-rendered about.html and
  fake version keys are deleted. The Refactor menu (always empty for
  TextMate/LSP languages) is hidden; GIT's commit prompt says "Commit",
  not "TIMELINE Commit"; dead window-system scaffolding removed.

### Added
- **`.env` support, zero-config.** Every command the rack launches now
  reads the project's `.env` and `.env.local` (`.env.local` wins; in a
  monorepo the lane's own directory overlays the root). Rack settings
  (ATMOS) win over the files, per-launch extras over both. No dialect:
  comments, `export` prefixes and quoted values — nothing else.
- **Switch Project… (Cmd+Shift+P).** A filterable popup over recent
  projects that re-aims the whole IDE — and goes through the new
  switch guard like every other path.

### Fixed
- **Switching projects no longer silently kills running work.** Aiming
  the rack at another project used to dispose the patch — dev server,
  tunnel, watch build all died without a word (or lingered half-aimed
  when the new project had no patch). The switch now names what is
  live ("SURGE, WORMHOLE are still running in api-server. Stop and
  switch?") and stops it cleanly only with consent.
- **ATMOS retracts what it applied.** Removing the env mixer (or
  swapping patches on a project switch) no longer leaks
  `NODE_ENV=production` and friends into the next project's commands.
- **Secrets can no longer ride the patch file into git.** The ATMOS
  EXTRA line is session-only now: durable values belong in `.env`,
  which is read automatically and already gitignored by every
  template. Legacy patches with stored extras load fine — the value is
  just not applied or re-saved.
- **PING responses are readable.** The BODY jack used to clip at 400
  characters — no real JSON payload survived. Full bodies now travel
  the cable (up to 64 KB) into PHOSPHOR's scrollback.
- **Deleted a leftover boot scaffold** that tried to replace the main
  window with a "v3.0.0 test panel" at every start (it only failed by
  losing a race), plus a stale services file registering a class that
  does not exist.

## [1.10.1] — 2026-07-02

A user-experience pass walked four developer journeys through the built
app (a Vite/React monorepo first-open, a zero-config static site, a
CI-configs-and-docs repo, a messy legacy project) and fixed what they
hit, smallest risk first.

### Fixed
- **The editor actually looks dark now.** The IDE always runs the
  FlatLaf Dark look and feel, but the editor's color profile is a
  separate switch that only the Options→Appearance panel ever flipped —
  so every editor rendered light-profile colors on a dark IDE: JS/TS in
  pale Phosphor on white, CSS/YAML/JSON/Markdown in plain black on
  white. The dark profile ("FlatLaf Dark") is now the shipped default,
  and the Phosphor JS/TS palette no longer leaks into the light
  profile. **Upgrading users see this immediately: every editor tab
  switches from a white canvas to the dark Phosphor scheme.** An
  explicit profile choice in Tools→Options still wins over the shipped
  default. Verified per-mime from a wiped userdir: TS/TSX, JS, JSON
  and Markdown render full Phosphor on dark, CSS the platform's dark
  colorings.
- **The launcher picks a Java the platform can run on.** Launching
  without a bundled runtime (portable zip, source builds) could select
  sdkman's `current` link even when it pointed at Java 8 — then spawn a
  JVM that printed "Cannot run on older versions of Java" and never
  exited. The launcher conf now chooses, in order: an explicit
  `jdkhome` / the installer's bundled JRE (still wins — it is applied
  later), `--jdkhome` on the command line (steps past the whole block),
  `JAVA_HOME` when it is 21+, the macOS `java_home` 21+ query, the
  newest 21+ JDK in the standard install dirs, a 21+ `java` on PATH —
  and if none qualifies, exits cleanly (code 3) with an actionable
  message instead of handing a doomed choice to the platform.
  `LauncherJavaSelectionTest` sources the real conf against fixture
  JDKs to pin every branch.
- **Markdown spellcheck reads prose, not code.** `const`, `npm`,
  `querySelectorAll` — anything in a fenced block, inline code span,
  URL, or YAML front matter — no longer gets the red typo squiggle;
  paragraphs, headings, emphasis, and link text stay checked.
- **Markdown fences highlight their language again.** TM4E prunes any
  grammar rule whose cross-grammar include can't resolve, which was
  silently stripping every language-tagged fence (191 rules per
  session) and the front-matter rule, and degrading HTML/Vue/Svelte/
  Astro `<script>` embeddings. Five embed-only grammars (source.js,
  source.ts, source.tsx, source.yaml, text.html.derivative — vscode
  1.95.0, like the rest of the pack) now register purely for scope
  resolution under synthetic mimes no file opens with; a ```js fence
  renders real JavaScript tokens.

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
