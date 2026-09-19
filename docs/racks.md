# The Rack Gallery

Every rack the product hands out, straight from the catalog — this file is
generated from `RackGallery` by `RackGalleryDocsTest` and CI fails if it
drifts. Do not edit by hand; regenerate with:

```
mvn -pl rack test -Dtest=RackGalleryDocsTest -Dnmox.docs.write=true
```

A rack file is a saved rack: the devices in order, every knob and setting, and
the patch cables between their jacks, as JSON (`.nmoxrack.json` — what Save
Patch writes beside a project). A file meant to travel also carries a `shared`
header that says what it is: a `name`, a `description`, the project `kinds` it
fits and the tools it `requires` on the PATH. Mounting a rack runs nothing —
every rack here arrives at rest, and each device still asks Workspace Trust
before its first command.

## Contribute a rack

The community racks live in one directory, and a pull request is how one joins:

1. Build the rack in the IDE and use Share… to write it, or write the JSON by
   hand. Name it `<stem>.nmoxrack.json` (lower-case letters, digits, hyphens).
2. Put it in `rack/src/main/resources/org/nmox/studio/rack/gallery/racks/` and
   add its file name as a line of `index` in the same directory.
3. Give the `shared` header a `name` (60 characters at most), a `description`
   that says what the rack DOES and when you would want it (400 at most),
   `kinds` and `requires`. English only; translations follow as
   `name.<lang>` / `description.<lang>` siblings.
4. Run the gate: `mvn -pl rack -am test -Dtest=CommunityRacksGateTest`. It fails
   by file name with the reason: only built-in devices, every cable really
   mounts, nothing switched on that would start by itself, no absolute or home path, no address but
   localhost, bare tool names, real project kinds.
5. Regenerate this file (the command above) and open the pull request.

## Community racks

### Rust save loop

> Every Rust save checks formatting and runs clippy side by side; cargo test runs only when both pass, and a failed test run goes to KVASIR for an explanation. For a crate you are changing all day.

- **File:** `rust-save-loop.nmoxrack.json`
- **Fits:** RUST
- **Requires:** `cargo`
- **Devices:** REFLEX, GLOSS, PURITY, QUORUM, VERITAS, KVASIR, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ GLOSS RUN, PURITY RUN`
  - `GLOSS DONE ▸ QUORUM IN 1`
  - `GLOSS OUT ▸ MONITOR IN`
  - `PURITY DONE ▸ QUORUM IN 2`
  - `PURITY OUT ▸ MONITOR IN`
  - `QUORUM OK ▸ VERITAS RUN`
  - `VERITAS FAIL ▸ KVASIR EXPLAIN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `KVASIR OUT ▸ MONITOR IN`

### Go: restart on green

> A Go save runs go test; only a green run restarts the service through IGNITION and runs go vet. While the service is up, TEMPO has PING call its health route every ten seconds. The running server is never replaced by code that fails its tests.

- **File:** `go-green-restart.nmoxrack.json`
- **Fits:** GO
- **Requires:** `go`
- **Devices:** REFLEX, VERITAS, IGNITION, TEMPO, PING, PURITY, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `VERITAS OK ▸ IGNITION RUN, PURITY RUN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `IGNITION RUNNING ▸ TEMPO ENABLE`
  - `IGNITION OUT ▸ MONITOR IN`
  - `TEMPO TICK ▸ PING SEND`
  - `PING BODY ▸ MONITOR IN`
  - `PURITY OUT ▸ MONITOR IN`

### Python quality loop

> A Python save runs ruff through SOLDER, then pytest with coverage held at an 80% floor, so a green suite with thin coverage still fails; a failed run goes to KVASIR for an explanation. The floor needs the pytest-cov plugin installed.

- **File:** `python-quality-loop.nmoxrack.json`
- **Fits:** PYTHON
- **Requires:** `python3`, `pytest`, `ruff`
- **Devices:** REFLEX, SOLDER, VERITAS, KVASIR, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ SOLDER RUN`
  - `SOLDER OK ▸ VERITAS RUN`
  - `SOLDER OUT ▸ MONITOR IN`
  - `VERITAS FAIL ▸ KVASIR EXPLAIN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `KVASIR OUT ▸ MONITOR IN`

### API smoke, then bench

> SURGE starts the service; the moment it is READY, PING calls the health route, and only a passing smoke test lets GAUNTLET load-test the live address against a 100 requests-per-second floor. For checking that a change did not quietly cost throughput.

- **File:** `api-smoke-then-bench.nmoxrack.json`
- **Fits:** NODE
- **Requires:** `node`, `npx`
- **Devices:** SURGE, PING, GAUNTLET, MONITOR
- **Wiring:**
  - `SURGE READY ▸ PING SEND`
  - `SURGE URL ▸ GAUNTLET URL`
  - `SURGE OUT ▸ MONITOR IN`
  - `PING OK ▸ GAUNTLET RUN`
  - `PING BODY ▸ MONITOR IN`
  - `GAUNTLET OUT ▸ MONITOR IN`

### Next.js E2E with triage

> NEXUS serves the app and SPECTER runs the Playwright or Cypress suite the moment it is READY. A failing suite goes straight to KVASIR for an explanation, the long output scrolls in PHOSPHOR, and SCOPE opens the HTML report when SPECTER serves it.

- **File:** `nextjs-e2e-triage.nmoxrack.json`
- **Fits:** NODE
- **Requires:** `node`, `npx`
- **Devices:** NEXUS, SPECTER, KVASIR, SCOPE, PHOSPHOR, MONITOR
- **Wiring:**
  - `NEXUS READY ▸ SPECTER RUN`
  - `NEXUS OUT ▸ MONITOR IN`
  - `SPECTER FAIL ▸ KVASIR EXPLAIN`
  - `SPECTER URL ▸ SCOPE URL`
  - `SPECTER READY ▸ SCOPE OPEN`
  - `SPECTER OUT ▸ PHOSPHOR IN`
  - `KVASIR OUT ▸ MONITOR IN`

### Workspace package gate

> For a JavaScript monorepo: dial one package on WAYPOINT and every lane runs there. A save type-checks, lints and tests that package side by side, and QUORUM builds it only when all three pass, so one package is proven without running the whole repository.

- **File:** `workspace-package-gate.nmoxrack.json`
- **Fits:** NODE
- **Requires:** `node`, `npx`
- **Devices:** WAYPOINT, REFLEX, TYPEGUARD, PURITY, VERITAS, QUORUM, FORGE, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ TYPEGUARD RUN, PURITY RUN, VERITAS RUN`
  - `TYPEGUARD DONE ▸ QUORUM IN 1`
  - `TYPEGUARD OUT ▸ MONITOR IN`
  - `PURITY DONE ▸ QUORUM IN 2`
  - `PURITY OUT ▸ MONITOR IN`
  - `VERITAS DONE ▸ QUORUM IN 3`
  - `VERITAS OUT ▸ MONITOR IN`
  - `QUORUM OK ▸ FORGE RUN`
  - `FORGE OUT ▸ MONITOR IN`

### Static site quality

> IGNITION serves the folder and SCOPE opens it; the same READY runs a Lighthouse audit that must hold 90 in all four categories. While the site is served, every fourth TEMPO tick has BEACON confirm it still answers. Re-aim BEACON at the live URL once the site is deployed.

- **File:** `static-site-quality.nmoxrack.json`
- **Fits:** STATIC
- **Requires:** `python3`, `npx`
- **Devices:** IGNITION, SCOPE, VITALS, TEMPO, BEACON, MONITOR
- **Wiring:**
  - `IGNITION URL ▸ SCOPE URL, VITALS URL`
  - `IGNITION READY ▸ SCOPE OPEN, VITALS RUN`
  - `IGNITION RUNNING ▸ TEMPO ENABLE`
  - `IGNITION OUT ▸ MONITOR IN`
  - `VITALS OUT ▸ MONITOR IN`
  - `TEMPO BAR ▸ BEACON CHECK`

### Contract gas loop

> A Solidity save runs forge build, then forge test; a green suite is checked by GOVERNOR against the committed gas snapshot and analysed by PURITY with slither when it is installed, and a red one goes to KVASIR for an explanation. For catching a gas regression on the save that caused it.

- **File:** `contract-gas-loop.nmoxrack.json`
- **Fits:** FOUNDRY
- **Requires:** `forge`
- **Devices:** REFLEX, FORGE, VERITAS, GOVERNOR, PURITY, KVASIR, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ FORGE RUN`
  - `FORGE OK ▸ VERITAS RUN`
  - `FORGE OUT ▸ MONITOR IN`
  - `VERITAS OK ▸ GOVERNOR RUN, PURITY RUN`
  - `VERITAS FAIL ▸ KVASIR EXPLAIN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `GOVERNOR OUT ▸ MONITOR IN`
  - `PURITY OUT ▸ MONITOR IN`
  - `KVASIR OUT ▸ MONITOR IN`

### Compose bench

> SOLDER brings the stack up with docker compose and streams it to PHOSPHOR, TAIL follows the app log onto MONITOR when you flip FOLLOW, and while HARBOR sees the Docker engine running TEMPO has PING check the health route every thirty seconds.

- **File:** `compose-bench.nmoxrack.json`
- **Fits:** any project
- **Requires:** `docker`
- **Devices:** HARBOR, SOLDER, TAIL, TEMPO, PING, PHOSPHOR, MONITOR
- **Wiring:**
  - `HARBOR RUNNING ▸ TEMPO ENABLE`
  - `HARBOR OUT ▸ MONITOR IN`
  - `SOLDER OUT ▸ PHOSPHOR IN`
  - `TAIL OUT ▸ MONITOR IN`
  - `TEMPO TICK ▸ PING SEND`
  - `PING BODY ▸ MONITOR IN`

### Parallel release gate

> One press of MAESTRO runs three lanes at once: the tests, a production build weighed by PRISM against a 500 KB budget, and the security audit. QUORUM lets PREFLIGHT run only when every lane passed, and only a PREFLIGHT pass reaches the LAUNCHPAD, which stays unarmed until you arm it.

- **File:** `parallel-release-gate.nmoxrack.json`
- **Fits:** NODE
- **Requires:** `node`, `npm`, `git`
- **Devices:** MAESTRO, VERITAS, FORGE, PRISM, SENTRY, QUORUM, PREFLIGHT, LAUNCHPAD, MONITOR
- **Wiring:**
  - `MAESTRO TRIG 1 ▸ VERITAS RUN`
  - `MAESTRO TRIG 2 ▸ FORGE RUN`
  - `MAESTRO TRIG 3 ▸ SENTRY RUN`
  - `VERITAS DONE ▸ QUORUM IN 1`
  - `VERITAS OUT ▸ MONITOR IN`
  - `FORGE OK ▸ PRISM MEASURE`
  - `FORGE FAIL ▸ QUORUM IN 2`
  - `FORGE OUT ▸ MONITOR IN`
  - `PRISM OK ▸ QUORUM IN 2`
  - `PRISM FAIL ▸ QUORUM IN 2`
  - `SENTRY DONE ▸ QUORUM IN 3`
  - `SENTRY OUT ▸ MONITOR IN`
  - `QUORUM OK ▸ PREFLIGHT RUN`
  - `PREFLIGHT OK ▸ LAUNCHPAD RUN`
  - `PREFLIGHT OUT ▸ MONITOR IN`
  - `LAUNCHPAD OUT ▸ MONITOR IN`

## Presets

### Web Pipeline

> MAESTRO fires install → build → test; SURGE pops the browser when serving

- **Devices:** MAESTRO, CRATE, FORGE, VERITAS, PURITY, SURGE, SCOPE, MONITOR
- **Wiring:**
  - `MAESTRO TRIG 1 ▸ CRATE RUN`
  - `CRATE OK ▸ FORGE RUN`
  - `FORGE OK ▸ VERITAS RUN, PURITY RUN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `PURITY OUT ▸ MONITOR IN`
  - `SURGE URL ▸ SCOPE URL`
  - `SURGE READY ▸ SCOPE OPEN`

### Dev Loop

> Dev server auto-opens the browser; saves run the tests

- **Devices:** REFLEX, SURGE, SCOPE, VERITAS, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `SURGE URL ▸ SCOPE URL`
  - `SURGE READY ▸ SCOPE OPEN`
  - `VERITAS OUT ▸ MONITOR IN`

### TDD Loop

> REFLEX armed: every save runs the tests, tally on the console

- **Devices:** REFLEX, VERITAS, PURITY, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `VERITAS OK ▸ PURITY RUN`
  - `PURITY OUT ▸ MONITOR IN`

### CI Lane

> MAESTRO fires install → build → test, output on the console

- **Devices:** MAESTRO, CRATE, FORGE, VERITAS, MONITOR
- **Wiring:**
  - `MAESTRO TRIG 1 ▸ CRATE RUN`
  - `CRATE OK ▸ FORGE RUN`
  - `FORGE OK ▸ VERITAS RUN`
  - `VERITAS OUT ▸ MONITOR IN`

### Uptime Watch

> While the dev server runs, TEMPO clocks PING health checks

- **Devices:** SURGE, SCOPE, TEMPO, PING, MONITOR
- **Wiring:**
  - `SURGE URL ▸ SCOPE URL, PING URL`
  - `SURGE READY ▸ SCOPE OPEN`
  - `SURGE RUNNING ▸ TEMPO ENABLE`
  - `TEMPO TICK ▸ PING SEND`
  - `PING BODY ▸ MONITOR IN`

### Modern Web

> VELOCITY serves (SCOPE pops the browser on READY); saves fan out to VERITAS tests and PURITY lint

- **Devices:** VELOCITY, SCOPE, REFLEX, VERITAS, PURITY, MONITOR
- **Wiring:**
  - `VELOCITY URL ▸ SCOPE URL`
  - `VELOCITY READY ▸ SCOPE OPEN`
  - `REFLEX CHANGED ▸ VERITAS RUN, PURITY RUN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `PURITY OUT ▸ MONITOR IN`

### Monorepo Lanes

> Mixed repo: ROSETTA shows the mix, WAYPOINT dials the package, saves fan out to node + cargo test lanes

- **Devices:** ROSETTA, WAYPOINT, REFLEX, CRATE, VERITAS, VERITAS, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ VERITAS RUN, VERITAS·2 RUN`
  - `CRATE OUT ▸ MONITOR IN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `VERITAS·2 OUT ▸ MONITOR IN`

### Ship Lane

> Prod build → security scan → armed deploy, with console trail

- **Devices:** MAESTRO, FORGE, SENTRY, LAUNCHPAD, MONITOR
- **Wiring:**
  - `MAESTRO TRIG 1 ▸ FORGE RUN`
  - `FORGE OK ▸ SENTRY RUN`
  - `SENTRY OK ▸ LAUNCHPAD RUN`
  - `LAUNCHPAD OUT ▸ MONITOR IN`

### Polyglot Gauntlet

> Per-language saves drive their own lane; QUORUM clears PREFLIGHT only when every lane is green

- **Devices:** ROSETTA, REFLEX, REFLEX, VERITAS, VERITAS, QUORUM, PREFLIGHT, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `REFLEX·2 CHANGED ▸ VERITAS·2 RUN`
  - `VERITAS DONE ▸ QUORUM IN 1`
  - `VERITAS OUT ▸ MONITOR IN`
  - `VERITAS·2 DONE ▸ QUORUM IN 2`
  - `VERITAS·2 OUT ▸ MONITOR IN`
  - `QUORUM OK ▸ PREFLIGHT RUN`

### Ship Gate

> Prod build → Lighthouse floor → bundle budget → PREFLIGHT verdict → armed deploy

- **Devices:** MAESTRO, FORGE, VITALS, PRISM, PREFLIGHT, LAUNCHPAD, MONITOR
- **Wiring:**
  - `MAESTRO TRIG 1 ▸ FORGE RUN`
  - `FORGE OK ▸ VITALS RUN`
  - `VITALS OK ▸ PRISM MEASURE`
  - `PRISM OK ▸ PREFLIGHT RUN`
  - `PREFLIGHT OK ▸ LAUNCHPAD RUN`
  - `PREFLIGHT OUT ▸ MONITOR IN`
  - `LAUNCHPAD OUT ▸ MONITOR IN`

### Dev Intelligence

> Serve with full awareness: clocked health probes, port radar, flight recorder, log tail

- **Devices:** SURGE, TEMPO, PING, SONAR, BLACKBOX, TAIL, MONITOR
- **Wiring:**
  - `SURGE RUNNING ▸ TEMPO ENABLE`
  - `SURGE URL ▸ PING URL`
  - `TEMPO TICK ▸ PING SEND`
  - `TEMPO BAR ▸ SONAR RUN`
  - `PING BODY ▸ MONITOR IN`
  - `SONAR OUT ▸ MONITOR IN`
  - `BLACKBOX OUT ▸ MONITOR IN`
  - `TAIL OUT ▸ MONITOR IN`

### LAMP Bench

> Composer install fans out to phpunit + phpstan + Pint; IGNITION serves public/

- **Devices:** CRATE, VERITAS, TYPEGUARD, GLOSS, IGNITION, MONITOR
- **Wiring:**
  - `CRATE OK ▸ VERITAS RUN, TYPEGUARD RUN, GLOSS RUN`
  - `CRATE OUT ▸ MONITOR IN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `TYPEGUARD OUT ▸ MONITOR IN`
  - `GLOSS OUT ▸ MONITOR IN`
  - `IGNITION OUT ▸ MONITOR IN`

### Multi-Chain Bench

> STELLAR + ANCHOR + ANVIL side by side — Soroban, Solana, and EVM lanes on one MONITOR

- **Devices:** MAESTRO, STELLAR, ANCHOR, ANVIL, MONITOR
- **Wiring:**
  - `MAESTRO TRIG 1 ▸ STELLAR ACTION`
  - `MAESTRO TRIG 2 ▸ ANCHOR RUN`
  - `STELLAR OUT ▸ MONITOR IN`
  - `ANCHOR OUT ▸ MONITOR IN`
  - `ANVIL OUT ▸ MONITOR IN`

### Web3 Bench

> ANVIL chain + forge build/test + GOVERNOR gas gate, MONITOR watching

- **Devices:** MAESTRO, FORGE, VERITAS, GOVERNOR, ANVIL, MONITOR
- **Wiring:**
  - `MAESTRO TRIG 1 ▸ FORGE RUN`
  - `FORGE OK ▸ VERITAS RUN`
  - `FORGE OUT ▸ MONITOR IN`
  - `VERITAS OK ▸ GOVERNOR RUN`
  - `VERITAS OUT ▸ MONITOR IN`
  - `GOVERNOR OUT ▸ MONITOR IN`
  - `ANVIL OUT ▸ MONITOR IN`

### Classic Web Bench

> CRATE installs (npm + bower), DYNAMO runs the taskfile, IGNITION serves the site, VITALS scores it

- **Devices:** MAESTRO, CRATE, DYNAMO, IGNITION, VITALS, MONITOR
- **Wiring:**
  - `MAESTRO TRIG 1 ▸ CRATE RUN`
  - `CRATE OK ▸ DYNAMO RUN`
  - `CRATE OUT ▸ MONITOR IN`
  - `DYNAMO OK ▸ IGNITION RUN`
  - `DYNAMO OUT ▸ MONITOR IN`
  - `IGNITION URL ▸ VITALS URL`
  - `IGNITION READY ▸ VITALS RUN`
  - `IGNITION OUT ▸ MONITOR IN`

### E2E Loop

> VELOCITY serves and SPECTER runs the E2E suite the moment READY fires; REPORT re-aims SCOPE at the HTML report

- **Devices:** VELOCITY, SPECTER, SCOPE, MONITOR
- **Wiring:**
  - `VELOCITY READY ▸ SPECTER RUN, SCOPE OPEN`
  - `VELOCITY URL ▸ SCOPE URL`
  - `SPECTER URL ▸ SCOPE URL`
  - `SPECTER READY ▸ SCOPE OPEN`
  - `SPECTER OUT ▸ MONITOR IN`

## Starter racks

What a project with no saved rack starts with, decided by what the project is.

### Run · Debug · Test

> Run, debug and test any toolchain on a save loop: REFLEX re-runs VERITAS, with IGNITION and INSPECTOR beside it on one MONITOR.

- **Devices:** REFLEX, IGNITION, INSPECTOR, VERITAS, MONITOR
- **Wiring:**
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `IGNITION OUT ▸ MONITOR IN`
  - `INSPECTOR OUT ▸ MONITOR IN`
  - `VERITAS OUT ▸ MONITOR IN`

### Node Package

> CRATE installs, NPM-9000 runs the script you dial from package.json, and REFLEX re-runs the tests on every save.

- **Devices:** CRATE, NPM-9000, REFLEX, VERITAS, MONITOR
- **Wiring:**
  - `CRATE OUT ▸ MONITOR IN`
  - `NPM-9000 OUT ▸ MONITOR IN`
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `VERITAS OUT ▸ MONITOR IN`

### Vite Site

> SURGE serves into SCOPE, MAESTRO fires install → build → test, and REFLEX re-tests on every save.

- **Devices:** MAESTRO, REFLEX, CRATE, SURGE, SCOPE, FORGE, VERITAS, MONITOR
- **Wiring:**
  - `MAESTRO TRIG 1 ▸ CRATE RUN`
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `CRATE OK ▸ FORGE RUN`
  - `SURGE URL ▸ SCOPE URL`
  - `SURGE READY ▸ SCOPE OPEN`
  - `FORGE OK ▸ VERITAS RUN`
  - `VERITAS OUT ▸ MONITOR IN`

### Angular App

> HALO serves and opens SCOPE when ready, CRATE installs, and REFLEX re-runs VERITAS on every save.

- **Devices:** HALO, CRATE, REFLEX, VERITAS, SCOPE, MONITOR
- **Wiring:**
  - `HALO URL ▸ SCOPE URL`
  - `HALO READY ▸ SCOPE OPEN`
  - `HALO OUT ▸ MONITOR IN`
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `VERITAS OUT ▸ MONITOR IN`

### Node HTTP Service

> SURGE runs the service and, the moment it is READY, PING calls its health route; every lane lands on MONITOR.

- **Devices:** CRATE, SURGE, PING, MONITOR
- **Wiring:**
  - `CRATE OUT ▸ MONITOR IN`
  - `SURGE READY ▸ PING SEND`
  - `SURGE OUT ▸ MONITOR IN`
  - `PING BODY ▸ MONITOR IN`

### Static Site

> SURGE serves the folder and SCOPE opens it when ready — nothing to build.

- **Devices:** SURGE, SCOPE, MONITOR
- **Wiring:**
  - `SURGE URL ▸ SCOPE URL`
  - `SURGE READY ▸ SCOPE OPEN`
  - `SURGE OUT ▸ MONITOR IN`

### BEAM Project

> CRATE fetches dependencies, then VERITAS runs the suite; REFLEX re-tests on every save and IGNITION runs the app.

- **Devices:** CRATE, IGNITION, REFLEX, VERITAS, MONITOR
- **Wiring:**
  - `CRATE OK ▸ VERITAS RUN`
  - `IGNITION OUT ▸ MONITOR IN`
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `VERITAS OUT ▸ MONITOR IN`

### Foundry Contracts

> ANVIL is the local chain, and REFLEX runs forge test through VERITAS on every save.

- **Devices:** ANVIL, REFLEX, VERITAS, MONITOR
- **Wiring:**
  - `ANVIL URL ▸ MONITOR IN`
  - `REFLEX CHANGED ▸ VERITAS RUN`
  - `VERITAS OUT ▸ MONITOR IN`
