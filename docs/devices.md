# The Device Reference

Every unit in the rack, straight from the catalog — this file is
generated from `DeviceCatalog` by `DeviceDocsTest` and CI fails if it
drifts. Do not edit by hand; regenerate with:

```
mvn -pl rack test -Dtest=DeviceDocsTest -Dnmox.docs.write=true
```

## AUTOMATE

### MAESTRO — Master Control — fire whole pipelines

> Press RUN SEQUENCE to fire all four TRIG outs at once.
> Patch TRIG 1 → CRATE RUN, then chain OK jacks: install → build → test.

- **Out:** `TRIG 1` (trigger), `TRIG 2` (trigger), `TRIG 3` (trigger), `TRIG 4` (trigger)

### REFLEX — File Watcher — fire pipelines on save

> Flip WATCH on and every file save fires CHANGED.
> Patch CHANGED → VERITAS RUN for test-on-save; FILTER narrows to code/styles/docs, or GLOB to one lane (*.rs).

- **Out:** `CHANGED` (trigger), `PATH` (data)

### QUORUM — Lane Join — barrier: fire OK when all lanes pass

> The barrier where lanes converge: ALL fires OK once every wired IN has arrived and all passed.
> Patch each lane's DONE → IN, OK → LAUNCHPAD. MODE=ANY relays the first instead.

- **In:** `IN 1` (trigger), `IN 2` (trigger), `IN 3` (trigger), `IN 4` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger)

### IGNITION — Polyglot Runtime — run anything: node/go/rust/py/rb/php

> Runs your project's main: cargo run, go run, mix run, python…
> TARGET=auto follows the detected toolchain; ARGS feed the command line.

- **In:** `RUN` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `RUNNING` (gate), `READY` (trigger), `URL` (data)

### NPM-9000 — Script Sequencer — run package.json scripts

> One package.json script per press. SCRIPT knob lists your scripts.
> Patch OK into the next device to chain scripts into pipelines.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### TEMPO — Step Sequencer — fire pipelines on a clock

> A clock: TICK fires at the dialed rate, BAR every 4th tick.
> Gate it with ENABLE (patch SURGE RUNNING in) for health checks only while serving.

- **In:** `START` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `TICK` (trigger), `BAR` (trigger), `RUNNING` (gate)

### SOLDER — Custom Command — run anything as a pipeline step

> Runs exactly what you type - make seed-db, ./scripts/fixtures.sh - argv only, no shell.
> Patch VERITAS OK → RUN to chain custom steps; SOLDER exports to CI like any device.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### DYNAMO — Grunt/Gulp task runner — dial a task, GO

> The Grunt/Gulp era as a device: TASK lists what the Gruntfile or gulpfile declares (static parse, instant).
> Dial a task, GO runs npx grunt/gulp <task>; RUNNER settles grunt-vs-gulp when a repo carries both. Patch OK onward to chain.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

## VERIFY

### CRATE — Package Manager — install & update deps

> INSTALL readies dependencies - in mixed repos it sequences every toolchain.
> UPDATE upgrades, CHECK reports outdated. OK fires when done.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### FORGE — Build Engine — vite/webpack/rollup & co

> BUILD compiles with the detected tool (vite/cargo/mix/swift…).
> WATCH mode fires OK on every rebuild - patch OK → VERITAS for build-then-test.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### VERITAS — Test Harness — jest/vitest/mocha...

> Runs the suite; the tally LCD shows live pass/fail.
> RUNNER=auto picks jest/pytest/cargo/mix… Patch OUT → MONITOR to read output.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### PURITY — Lint Filter — biome/eslint/stylelint

> Static analysis; E/W counts land on the LCD, CLEAN lights when spotless.
> FIX rewrites violations in place (amber = it mutates your files).

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### GLOSS — Code Formatter — biome/prettier; pint on PHP lanes

> Prettier over the project (Laravel Pint on PHP lanes). WRITE rewrites; CHECK only verifies.
> Patch REFLEX CHANGED → RUN for format-on-save.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### TYPEGUARD — Type Checker — tsc, watch-aware; phpstan on PHP lanes

> tsc --noEmit (phpstan on PHP lanes). WATCH keeps the compiler resident and fires OK/FAIL per check.
> STRICT adds --strict. E: count on the LCD.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### VITALS — Web Quality Gate — Lighthouse scores with a shipping floor

> Lighthouse headless against the dialed URL - PERF/A11Y/BEST/SEO on the meters.
> Dial MIN + GATE (perf/a11y/both/best/seo/all): any held score below the floor fires FAIL, not OK.

- **In:** `RUN` (trigger), `URL` (data)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### PRISM — Bundle-Size Gate — weigh the build, hold the line

> Weighs the build output dir; MAX sets the budget.
> Patch FORGE OK → MEASURE and OK → LAUNCHPAD: bundles over budget don't ship.

- **In:** `MEASURE` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger)

### GOVERNOR — Gas budget gate — forge snapshot --check

> CHECK runs forge snapshot --check against the committed .gas-snapshot; TOLERANCE allows dialed drift.
> Patch VERITAS OK → CHECK and OK → LAUNCHPAD: gas regressions physically cannot ship.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

## SERVE

### SURGE — Dev Server — start/stop local serving

> START serves your project; URL and READY outs feed SCOPE so the
> browser opens itself at the real address. RUNNING gates TEMPO nicely.

- **In:** `RUN` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `RUNNING` (gate), `READY` (trigger), `URL` (data)

### PING — Request Probe — HTTP smoke tests

> Fires HTTP requests; status + latency on the LCDs, OK/FAIL triggers out.
> VIEW opens the console (pretty-printed responses, last 50 exchanges, replay); HEADERS is session-only.

- **In:** `SEND` (trigger), `URL` (data)
- **Out:** `OK` (trigger), `FAIL` (trigger), `BODY` (data)

### SCOPE — Browser Link — open URLs on trigger

> Opens the system browser at the dialed URL on OPEN or any trigger in.
> Patch a URL data jack in and SCOPE follows wherever the server actually is.

- **In:** `OPEN` (trigger), `URL` (data)
- **Out:** `OPENED` (trigger)

### WORMHOLE — Public Tunnel — cloudflared/ngrok/localtunnel

> OPEN exposes a local port to the internet via cloudflared/ngrok.
> The public URL lands on the LCD and the URL jack - patch into SCOPE to pop it.

- **In:** `RUN` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `URL` (data), `RUNNING` (gate)

### NEPTUNE — Database Console — ping databases & trigger schemas

> Pings SQL databases (PostgreSQL/MySQL/SQLite/MariaDB).
> Dial DB TYPE to select database URL or profile; ping fires OK on success.

- **In:** `RUN` (trigger), `PING` (trigger), `MIGRATE` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `RUNNING` (gate)

### ANVIL — Local EVM chain — anvil devnet with unlocked accounts

> START boots anvil on the dialed PORT: a local EVM chain, ten unlocked funded accounts, instant mining.
> Patch URL → Contract Studio's network and SERVING → a gate; FORK-URL forks any live network's state.

- **In:** `RUN` (trigger), `START` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `URL` (data), `READY` (trigger), `SERVING` (gate)

## FRAMEWORKS

### HALO — Angular Console — serve/generate/update, stays current

> SERVE/BUILD/TEST drive ng; GEN scaffolds with the SCHEMATIC knob.
> The version cluster nags when Angular moves - UPDATE runs ng update.

- **In:** `RUN` (trigger), `SERVE` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `URL` (data), `READY` (trigger), `SERVING` (gate)

### PHOENIX — Phoenix Console — phx.server/gen/ecto, Hex currency

> SERVER runs mix phx.server; GEN row drives phx.gen.*; MIGRATE runs ecto.
> Version cluster tracks :phoenix against Hex.

- **In:** `RUN` (trigger), `SERVE` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `URL` (data), `READY` (trigger), `SERVING` (gate)

### NEXUS — Next.js Console — dev/build/start, registry currency

> DEV serves with the URL out feeding SCOPE; BUILD then START runs production.
> Version cluster tracks next against the registry.

- **In:** `RUN` (trigger), `DEV` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `URL` (data), `READY` (trigger), `SERVING` (gate)

### VELOCITY — Vite Console — dev/build/preview, registry currency

> DEV serves with the URL out feeding SCOPE; BUILD compiles the bundle, PREVIEW serves it.
> Version cluster tracks vite against the registry.

- **In:** `RUN` (trigger), `DEV` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `URL` (data), `READY` (trigger), `SERVING` (gate)

### ARTISAN — Laravel Console — artisan serve/test/migrate, Packagist currency

> SERVE runs php artisan serve (URL out feeds SCOPE); the ACTION knob dials
> test/migrate/fresh/queue/routes for RUN. Tinker is interactive — use the REPL device (php artisan tinker).

- **In:** `RUN` (trigger), `SERVE` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `URL` (data), `READY` (trigger), `SERVING` (gate)

## OBSERVE

### INSPECTOR — Debug Launcher — debug servers with attach endpoints

> LAUNCH starts your runtime in debug-server mode; the attach
> endpoint (chrome://inspect, debugpy, dlv…) lands on the LCD.

- **In:** `RUN` (trigger), `STOP` (trigger), `ENABLE` (gate)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data), `ENDPOINT` (data), `RUNNING` (gate)

### MONITOR — Output Console — watch any OUT jack

> A glanceable 8-line screen. Patch any OUT (data) jack into IN,
> or dial TAP to stderr/all to hear every device unpatched — errors glow red.

- **In:** `IN` (data)

### PHOSPHOR — Scrollback Terminal — 5k lines, selectable

> 5,000 lines of selectable scrollback. FOLLOW tails the output.
> Patch the OUT of anything chatty in here.

- **In:** `IN` (data)

### REPL — Read-Eval-Print Loop — type into clisp/python/node/ghci live

> Dial ENGINE to a known interpreter — it seeds COMMAND (force-interactive flags included), HINTS snippets,
> and the install command — or stay on CUSTOM and set COMMAND yourself; press START. Type an expression and Enter
> to evaluate; INSTALL runs the catalog's install command when the interpreter is missing. A Learning Space seeds everything for you.

- **In:** `EVAL` (data)
- **Out:** `OUT` (data)

### GAUNTLET — Load Bench — autocannon throughput

> FIRE hammers the URL with autocannon; req/s on the meter.
> Patch SURGE URL → URL and READY → RUN to bench the second it serves.

- **In:** `RUN` (trigger), `URL` (data)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### BLACKBOX — Flight Recorder — session timeline, slow-creep alarm

> The rack's session memory: every launch, exit, duration, and error, timestamped.
> VIEW scrolls the timeline; the health line warns when a build quietly slows past its average.

- **Out:** `OUT` (data)

### SONAR — Port Radar — who owns every port, one-click kill

> SWEEP maps every listening port to its owning process — docker containers labeled.
> VIEW opens the field: BROWSE any port, KILL any squatter. EADDRINUSE, solved.

- **In:** `RUN` (trigger)
- **Out:** `OUT` (data)

### TAIL — Log Follower — tail -f any file onto the patch bay

> tail -f as a device: dial a file (relative to the project), flip FOLLOW.
> Patch OUT → PHOSPHOR and your server's logs/app.log scrolls beside its stdout.

- **Out:** `OUT` (data)

### BEACON — Cert & Uptime Sentinel — TLS runway and reachability, gated

> CHECK answers: is it up, and how many days on the TLS cert?
> Patch TEMPO BAR → CHECK to watch production on a clock; MIN DAYS fires FAIL inside the window.

- **In:** `CHECK` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger)

### ORACLE — Error Explainer — AI explains the last failed run, on a button press

> EXPLAIN reads the last failed run off the flight recorder and asks the Anthropic API what went wrong and how to fix it — QUERY-blue, no project mutation.
> Set the key with KEY… (OS keychain, or ANTHROPIC_API_KEY/CLAUDE_API_KEY); first press asks a one-time consent naming exactly what's sent; MODEL dials HAIKU or SONNET; VIEW opens the full answer.


## SHIP

### TIMELINE — Git Sequencer — status/pull/commit/push

> STATUS/PULL/COMMIT/PUSH with the branch on the LCD and a DIRTY light.
> Amber buttons mutate - the law of the rack.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### SENTRY — Security Analyzer — npm audit meters

> SCAN runs the security audit; severity ladders fill per class.
> SECURE lights green when the tree is clean.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### LAUNCHPAD — Deploy Output — armed deploys only

> Flip ARM, then LAUNCH deploys to the dialed target.
> Unarmed pads ignore even patched triggers - by design.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### HARBOR — Docker Engine — panel, prune, status

> The ENGINE LED tracks the daemon; LCDs show containers up, images held, disk reclaimable.
> PANEL opens the full control room — containers, images, volumes, networks, and one-click Dockerize.

- **In:** `RUN` (trigger)
- **Out:** `RUNNING` (gate), `OUT` (data)

### PREFLIGHT — Ship Check — git/tests/build/lint/audit, one verdict

> CHECK runs the readiness list — git clean, tests, build, lint, audit — one LED per item.
> Patch OK → LAUNCHPAD RUN and unverified code physically cannot deploy.

- **In:** `RUN` (trigger)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

### HELM — Remote Runner — run commands on your servers over ssh

> Runs the dialed command on user@host over ssh (BatchMode: key auth only).
> Patch LAUNCHPAD OK → RUN to finish deploys with a remote migrate or restart; HOST accepts a cable.

- **In:** `RUN` (trigger), `HOST` (data)
- **Out:** `OK` (trigger), `FAIL` (trigger), `DONE` (trigger), `OUT` (data)

## UTILITY

### ROSETTA — Language Selector — steer AUTO knobs in mixed repos

> Mixed repo? Pin every AUTO knob to one toolchain with the dial.
> AUTO follows detection; KIND out reports the choice.

- **Out:** `KIND` (data)

### WAYPOINT — Workspace Selector — steer Node lanes at one package

> JS monorepo? Dial a workspace package and NPM-9000, PURITY, GLOSS and VERITAS run there.
> root = the repository root; DIR out reports the choice.

- **Out:** `DIR` (data)

### ATMOS — Env Mixer — NODE_ENV/CI/custom vars

> Sets NODE_ENV/CI/custom vars for every command the rack runs.
> What the knob says is what every device gets.

- **Out:** `ENV` (data)
