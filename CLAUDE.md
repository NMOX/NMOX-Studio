# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NMOX Studio is a NetBeans Platform-based IDE for modern web development, with first-class polyglot support (JS/TS, Java, C/C++, Python, Ruby, Rust, Go, PHP, shell + configs), NPM integration, project templates, and build tools. It's built as a multi-module Maven project with the NetBeans Rich Client Platform (RCP).

**Status**: shipping (v1.47.0). **Modules say which release they came from** (every module manifest carries the product version as its OpenIDE spec version, driven by one root <spec.version> property the release workflow stamps from the tag alongside branding; inter-module dependencies are real ranges — core > 1.47.0 — so a mismatched module refuses to load instead of LinkageError at call time; SpecVersionGateTest byte-gates all 11 jars + the placeholder discipline, mutation-proven). **Optional means a lookup, not a caught classloader failure** (core.spi ProjectAim/LiveServings facades + rack @ServiceProvider adapters replace 31 catch(LinkageError) sites; apiclient/web3/infra dropped their rack dependency entirely; rack's exports are OpenIDE-Module-Friends-locked to the five first-party consumers — byte-verified; SoftDependencyGateTest pins the idiom out, mutation-proven). **A platform citizen** (aiming opens the project in OpenProjects + publishes a real selection from the aim-owning windows — the Team menu is the full enabled git suite with just a project aimed, and the git chip's Show Changes/Diff/Annotate are back; ledger 29 worked as its own release, guards mutation-proven, boot laws intact). **Never freezes, never lies** (Stop All/switch off the EDT with the orphan guarantee intact — 1.5s/device freeze measured then killed; studio saves on single-lane writers with stamp-atomic self-write discrimination; Window menu shows real accelerators, drift-gated; SBOM covers the vendored adapter). **Debugs the browser too** (Debug in Chrome on HTML/JS/TS: the vendored js-debug launches a throwaway-profile Chrome at the project's live ServingRegistry URL — or file:// for serve-less HTML — and page breakpoints hit in the IDE; recon-first, real-Chrome integration test, mutation-proven). **Tested where it ships** (windows-latest runs the full verify as a blocking CI gate — the lane's first green found two real product bugs: language servers never detected as installed on Windows, and a cross-OS DapProxy disconnect race; one evidenced POSIX-only disable). **Usable without a mouse or a screen** (the widget library speaks Swing accessibility: knobs are SLIDERs with keyboard arrows + focus rings, buttons/toggles answer Space/Enter, LEDs/LCDs/VU meters report state; every control on all 44 devices exposes an accessible name — CI-gated by DeviceContractTest, 59 fixed to get there). **Knows its branch** (a git chip on the status line — HEAD read from disk, dirty count bounded + boot-law-gated; one click to the platform History browser; Team menu carries the rest). **Platform-native inside** (a five-lens senior-RCP idiom review: file CRUD rides DataObject so open editors follow deletes/renames, all five workspace writers are atomic — temp sibling + ATOMIC_MOVE via core AtomicFiles — the Rack shutdown-hook leak is a live-set reaper, Docker Panel respects being closed, studio workspace loads moved ctor→componentOpened without breaking the zero-boot-spawns law, API Studio/NpmService on named RequestProcessors, DockerClient a real @ServiceProvider; deep deferrals written as ledger 29–36, deliberate divergences blessed in writing). **Says what it does** (every advertised keyboard shortcut opens the window it names — studios on ⌥⌘6–9, Workbench ⌥⌘0, Open Folder ⌥⌘O; the four that collided with the platform Keymaps profile were found by pressing them). **Starts honest** (window in 1.4–2.7s; zero processes spawned at boot — hidden default-open tabs defer their work to first show, JFR-verified). **Breakpoints everywhere** (JS/TS debug out of the box via vendored js-debug + a DAP session multiplexer; Python/Go unchanged; all three now gate on Workspace Trust). **Senior-review hardened** (a six-lens RCP architecture audit fixed the two oldest surfaces' latent bugs: real process timeouts everywhere via ProcessSupport.runBounded, the EDT mutation half — New Project/Experiments/file-tree delete/session snapshots all off the paint thread, per-open studio listener lifecycles, corrupt workspace files kept as .bak instead of clobbered, cloud tokens in the OS keychain, dead v0.x code deleted — net −3,000 lines), **everything wired together** (the Serving Registry — serve devices announce URLs live; status-line `⇄ serving` chip, ⌘I "Live Servers", VITALS/BEACON auto-target, API Studio {{baseUrl}} offers, Contract Studio auto-connects to ANVIL; manifest edits pulse exactly the devices that read them — NPM-9000/DYNAMO/CRATE/HALO/ARTISAN re-sync without re-aiming; Contract Studio auto-rescans on new build artifacts; DB Studio offers connections for running Docker database containers; all four studio workspace files reload on external edits with never-clobber dirty guards; every listener bounded + equality-guarded + storm-tested), polyglot editing (48 TextMate grammars incl. CoffeeScript and the config layer with Apache configs (.htaccess/httpd.conf/.vhost), LSP providers, Bun/Deno/Rust/Go/BEAM+/PHP toolchains with per-lane monorepo dirs; Navigator outline for 45 mimes; classic-library-aware completion — jQuery/MooTools/Prototype/Backbone/Underscore/Knockout APIs appear when the project's deps or script tags carry them), **the classic web as first-class** (script-tag sites with no manifest open as STATIC projects and serve via IGNITION; bower.json/Gruntfile/gulpfile/webpack.config are project manifests — 30 total; jQuery 1.x/2.x get honest EOL chips; Classic Kit wizard extends any codebase with vendored-or-npm jQuery/MooTools/Prototype/Backbone/Knockout + webpack/grunt/gulp/bower scaffolds, never clobbering), the Reason-style task rack (44 devices incl. the REPL, the ARTISAN Laravel console, the ANVIL local EVM chain, the GOVERNOR gas gate, and the DYNAMO Grunt/Gulp task runner — see docs/devices.md, generated + CI-gated; undo/redo ⌘Z on every structural edit; cross-lane QUORUM join, readiness ENABLE gates, per-lane REFLEX GLOB routing; quality gates: VITALS Lighthouse floor with perf/a11y/both/best/seo/all GATE, VERITAS coverage floor + named-failure re-runs, GAUNTLET throughput floor, PRISM bundle budget, BEACON cert/uptime sentinel; SOLDER any-command (exports to CI), TAIL log follow + TEMPO clock (both resurrect), HELM ssh runner; presets, CI export, session resurrection, PREFLIGHT ship gate), safe project switching + .env everywhere + Experiments (~/.nmox/experiments lifecycle), Quick Search (⌘I — reaches projects, rack devices, live servers, API Studio requests, and infra nodes)/status line/keymap surfaces, the Workbench home base, the Docker control panel, the multi-cloud infra designer (DigitalOcean/Hetzner/Cloudflare — sync live resources from all three, drift Refresh, Destroy-stack with cost framing, cloud-init user_data, copy-ssh-from-node, deploy log), **API Studio** (Postman-style tab with per-response security-header grades), **DB Studio** (⌥⌘7 database suite: SQLite/PostgreSQL/MySQL/MariaDB/MongoDB/CouchDB with bundled drivers, Keyring-only passwords, kind-aware SQL/JSON console, per-statement result grids with in-grid row editing — PK-gated, Apply previews the exact UPDATEs, honest read-only reasons — CSV/JSON export, persistent history + saved queries, .env connection offers, EXPLAIN button, ⌘I-reachable; suite tabs Workbench→Rack→DB→Web3→Infra→API→Docker all open by default on first launch), **Contract Studio** (⌥⌘6 Web3 suite: Foundry/Hardhat artifact tree, ABI-driven Interact with decoded returns/reverts, Watch pane streaming blocks + decoded event logs, Oversight pane with gas table + EIP-170 size verdicts + deployment address book in .nmoxweb3.json; NO private keys ever — sends via devnet unlocked accounts, secret RPC URLs Keyring-only; Solidity grammar/outline/LSP; foundry.toml toolchain), **standards enforced for real** (.editorconfig on save; Standards Kit wizard for robots/sitemap/manifest/security.txt/humans), the **PWA Kit wizard** (Java2D icon forge incl. maskable set, readable service worker, offline page, idempotent index.html wiring), **Learning Spaces** (New Learning Space… — 52 built-in tutorials across languages/frameworks/libraries, each generating sample code + a walked tutorial + a rack pre-wired with a real in-rack REPL you type into), one Options category (General/Rack/Format sub-panels, incl. the startup update-check toggle), installers for all three OSes with bundled runtimes, Homebrew cask, and a tag-triggered release workflow with SBOM.

## Build and Run Commands

### Prerequisites
- **Java 21+** (required; project targets Java 21 LTS, tested with Java 23)
- **Maven 3.6+**

**IMPORTANT**: Ensure JAVA_HOME points to JDK 17+ or use `--jdkhome` when running:
```bash
# Set Java for build
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home
# or use: export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Alternative: use --jdkhome when launching IDE
./application/target/nmoxstudio/bin/nmoxstudio --jdkhome /Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home
```

**Java Module Access**: The IDE is configured to open necessary Java modules for NetBeans Platform compatibility with Java 9+. These flags are automatically included in the default configuration.

### Building

```bash
# Clean build without tests (fastest)
mvn clean package -DskipTests

# Full build with tests
mvn clean test package

# Build specific module
mvn clean package -pl editor

# Build specific module with dependencies
mvn clean package -pl editor -am
```

### Running the IDE

```bash
# Using convenience script
./run.sh

# Or manually after build
cd application/target/nmoxstudio/bin
./nmoxstudio

# Run directly with Maven (development mode)
mvn nbm:run-platform
```

### Testing

```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl tools

# Run specific test class
mvn test -pl tools -Dtest=NpmServiceTest

# Skip tests during build
mvn package -DskipTests

# Generate test coverage report
mvn test jacoco:report
```

### Convenience Scripts

The project includes several shell scripts:
- `./build.sh` - Clean build
- `./run.sh` - Build and run the IDE
- `./gui-test.sh` - GUI-specific tests (macOS)
- `./setup-and-run.sh` - Interactive setup menu
- `scripts/boot-smoke-test.sh` - Headless boot of the assembled app (the CI gate)

The comprehensive suite is `mvn verify` — tests plus the SpotBugs,
find-sec-bugs, and JaCoCo gates. (The v0.x-era test-everything.sh /
quick-test.sh scripts checked classes that never shipped and were
removed in v1.22.0.)

## Module Structure

The project is organized as a multi-module Maven build using NetBeans Platform:

```
NMOX-Studio/
├── core/                    # Cross-cutting platform touches (Terminal phosphor theme)
├── editor/                  # File type support, JavaScript lexer, syntax highlighting, completion
├── tools/                   # NPM integration, build tools
├── rack/                    # Reason-style task rack: drag-drop device wiring, control surfaces
├── infra/                   # Node-RED-style multi-cloud infra designer (DO/Hetzner/Cloudflare)
├── apiclient/               # API Studio — Postman-style request/collection/test suite
├── dbstudio/                # DB Studio — SQL + document database suite (5 engines, bundled drivers)
├── web3/                    # Contract Studio — Web3/EVM smart contracts (Keccak/ABI/JSON-RPC cores)
├── project/                 # Project templates, scaffolding, project explorer UI
├── ui/                      # Main windows, actions, welcome screen, startup logic
├── branding/               # Application theming, splash screen, icons
├── application/            # Final packaging and distribution assembly
└── NMOX-Studio-sample/    # Sample module template
```

### Active Modules (in build)

| Module | Purpose | Key Components |
|--------|---------|----------------|
| **core** | Cross-cutting platform touches | `TerminalPhosphor` (phosphor Terminal theme on first run) |
| **editor** | File editing and language support | `JavaScriptLexer` (regex-aware), `JavaScriptDataObject`, `TypeScriptDataObject`, `WebFileSupport` (HTML), 48 TextMate grammars incl. HTML/CSS/SCSS/Less + the config layer, JS/HTML/CSS completion providers, LSP providers, `OutlineModel`/`StructureNavigatorPanel` (Navigator outline for 45 mimes), `ConfigFileResolver` (dotfile MIME), `debug/DapDebugAction` + `debug/dap/` (DapFrames/DapProxy/JsDebugServer — flattens js-debug's child-session protocol for the platform's single-session DAP client; adapter vendored at `src/main/release/jsdebug/`) |
| **tools** | Development tools and integrations | `NpmService`, `WebProjectFactory`, `NpmExplorerTopComponent` |
| **rack** | Reason-style virtual task rack + project lifecycle | `RackTopComponent`, `Rack`/`RackDevice` model, 44 task devices (incl. ROSETTA mixed-repo selector, QUORUM lane-join barrier, IGNITION polyglot runtime, INSPECTOR debug launcher, ARTISAN Laravel console, the REPL, ANVIL local EVM chain, GOVERNOR gas gate, DYNAMO Grunt/Gulp runner, HARBOR docker, BLACKBOX flight recorder, SONAR port scanner, PREFLIGHT ship check, PHOSPHOR terminal), cross-lane coordination (QUORUM join, `RackDevice.enableGate` readiness gates on long-runners, REFLEX per-lane GLOB routing), patch-cable wiring, `FileWatcher`, `RackIO` persistence, `RackService`, `ProjectStudioTopComponent` (templates, file CRUD, package.json editor, presets) |
| **apiclient** | API Studio (Postman-style) | `ApiClientTopComponent` tab, collections/requests tree, request builder (params/headers/body/auth/tests), response viewer, `{{var}}` environments, `.nmoxapi.json` per-project persistence; `ApiClient`/`TestRunner`/`WorkspaceIO` engine |
| **dbstudio** | DB Studio (database suite, ⌥⌘7) | `DbStudioTopComponent` (connections+schema tree, kind-aware SQL/JSON console, per-statement result grids + history), `DbBackend.create()` dispatch — `DbClient` JDBC (SQLite/PG/MySQL/MariaDB, bundled drivers), `MongoBackend` (Extended-JSON runCommand), `CouchBackend` (pure HTTP Mango); `SqlSplitter`, `DocumentGrid`, `Passwords` (Keyring-only), `.nmoxdb.json` specs, `DbSearchProvider` (⌘I) |
| **web3** | Contract Studio (⌥⌘6) | `Web3StudioTopComponent` (Interact/Watch/Oversight, network manager, DA-6), `Keccak256`/`AbiCodec` (spec-vector-pinned), `JsonRpcClient` (Transport seam, URL redaction), `ArtifactScanner` (Foundry out/ + Hardhat artifacts/), `GasReportParser`, `ContractSizeCheck` (EIP-170), `Web3WorkspaceIO` (.nmoxweb3.json, secret networks never serialize URLs), `RpcSecrets` (Keyring), `Web3SearchProvider` (⌘I) |
| **infra** | Multi-cloud infra designer (DO/Hetzner/Cloudflare) | `InfraDesignerTopComponent`, `NodeKind` catalog, `FlowCanvas`, `DeployPlanner`, per-provider clients, `CloudTokens` (Keyring), cost estimation, `.nmoxinfra.json` persistence |
| **project** | Project management | `ProjectExplorerTopComponent`, `WebProject`, wizards |
| **ui** | Core UI components | `MainWindow` (the Welcome launchpad), `UpdateCheck`, actions, Options panels |
| **branding** | Application identity | Splash screen, icons, custom branding |
| **application** | Final assembly | Distribution package creation |

## Architecture

### NetBeans Platform Concepts

NMOX Studio leverages the NetBeans Rich Client Platform (RCP):

1. **Module System**: Each module is an OSGi-like bundle (NBM) with explicit dependencies
2. **Lookup System**: Service discovery and dependency injection via `Lookup.getDefault().lookup(Service.class)`
3. **TopComponents**: Dockable window panels for UI
4. **DataObjects**: File type handlers registered via MIME types
5. **Nodes**: Tree/explorer view representations
6. **Actions**: Commands registered via annotations

### Key Architecture Patterns

**Service Registration**:
```java
@ServiceProvider(service = SomeService.class)
public class SomeServiceImpl implements SomeService {
    // Implementation
}
```

**Service Lookup**:
```java
SomeService service = Lookup.getDefault().lookup(SomeService.class);
```

**TopComponent (Window)**:
```java
@TopComponent.Registration(mode = "editor", openAtStartup = false)
public class MyPanel extends TopComponent {
    // UI implementation
}
```

**DataObject (File Type)**:
```java
@MIMEResolver.Registration(displayName = "JavaScript", resource = "mime-resolver.xml")
@DataObject.Registration(mimeType = "text/javascript", ...)
public class JavaScriptDataObject extends MultiDataObject {
    // File handling
}
```

### Module Dependencies

- **core** has minimal dependencies (base NetBeans APIs only)
- **editor** depends on core + editor/lexer APIs
- **tools** depends on core + project APIs + external libs (JSON)
- **ui** depends on core + windowing APIs
- **application** depends on all active modules

## Development Workflow

### Adding a New Feature

1. Identify the appropriate module (or create new one)
2. Add dependencies to module's `pom.xml`
3. Implement using NetBeans Platform APIs
4. Register services/components via annotations
5. Add unit tests
6. Update layer.xml if adding UI components or actions
7. Test in IDE with `mvn nbm:run-platform`

### Common Tasks

**Add a new file type**:
- Create DataObject class in `editor/`
- Add MIME resolver XML
- Implement EditorKit or use existing
- Register with `@DataObject.Registration`

**Add a new tool window**:
- Create TopComponent in appropriate module
- Use `@TopComponent.Registration` annotation
- Implement UI with Swing components

**Add a new service**:
- Define interface in module API
- Implement with `@ServiceProvider`
- Consume via Lookup

**Add NPM functionality**:
- Extend `NpmService` in `tools/`
- Add UI in `NpmExplorerTopComponent`

### Testing

The project uses JUnit 5 with AssertJ assertions and Mockito for mocking:

```java
@Test
@DisplayName("Should parse npm error when command not found")
void shouldParseNpmErrorWhenCommandNotFound() {
    // Given
    String errorOutput = "npm: command not found";

    // When
    NPMError result = parser.parseError(errorOutput);

    // Then
    assertThat(result.getUserMessage()).contains("NPM not found");
}
```

Integration tests can use NetBeans Platform test harness in the `test` scope.

## Key Implementation Details

### JavaScript Syntax Highlighting

Location: `editor/src/main/java/org/nmox/studio/editor/javascript/`

The JavaScript lexer (`JavaScriptLexer.java`) provides token-based syntax highlighting using NetBeans Lexer API. Tokens are defined in `JavaScriptTokenId.java` and language hierarchy in `JavaScriptLanguageHierarchy.java`.

**Token categories**: keywords, identifiers, operators, literals, comments, whitespace

**Integration**: Registered via `@LanguageRegistration` and `layer.xml`

### NPM Integration

Location: `tools/src/main/java/org/nmox/studio/tools/npm/`

- **NpmService**: Executes NPM commands, parses package.json
- **WebProject**: A full platform `Project` — its Lookup carries `Sources`, an `ActionProvider` (Run/Build/Test/Clean → `WebProjectCommands` per toolchain, executed via the rack's `CommandExecutor` with a `ProgressHandle`), `RecommendedTemplates`, and a `ProjectOpenedHook` that aims the rack
- **WebProjectFactory**: `@ServiceProvider(ProjectFactory.class)` — recognizes 16 manifests (package.json, Cargo.toml, go.mod, pom.xml, …) as projects
- **NpmExplorerTopComponent**: UI for browsing and running NPM scripts

Projects are recognized by any supported manifest (package.json and 15 others); the IDE-native Run/Build/Test/Clean and the rack are one mechanism.

### Project Templates

Location: `tools/src/main/resources/org/nmox/studio/tools/npm/`

Templates for React, Vue, and Vanilla JS projects. Uses wizard pattern with `WebProjectWizardIterator`.

## Known Issues and Technical Debt

See `docs/hack/technical-debt.md` for comprehensive list. Key items:

1. **Hardcoded project templates** - templates live in code (ProjectTemplates.java); should be extensible/data-driven
2. **Performance** - startup time ~5 seconds, memory ~400MB
3. **JS/TS use a custom lexer pipeline** while the other languages ride TextMate+CSL; two code paths to maintain
4. **.sass (indented) shares the SCSS grammar** - approximate highlighting for the indented dialect

## Version History

- **v0.1**: Basic JavaScript/TypeScript support, NPM integration, project templates
- **v1.0.x**: task rack, polyglot editor (TextMate+CSL), LSP everywhere, infra designer, Workbench, packaging + release pipeline (and the real branded splash)
- **v1.1.0-v1.3.0**: Docker control panel + HARBOR, BLACKBOX/SONAR awareness devices, session resurrection, PREFLIGHT ship gate
- **v1.4.x-v1.10.x**: bundled JRE installers, config-layer grammars, Navigator outline, WebProject SPI, LSP health + installer, self-policing build (SpotBugs/find-sec-bugs/JaCoCo floors), Java 21 + CI matrix + SBOM + Dependabot, CI release gates (boot smoke, test audit, rendering probe)
- **v1.11.0**: daily driver — safe switching guard, .env everywhere, Experiments, Quick Search/status line/keymap, crisp chrome (VCS-museum eviction, honest About, menu-bar name)
- **v1.12.0-v1.13.0**: SOLDER/TAIL/VITALS + overallSuccess verdict hook; testing rigor (named failures, re-run failed, coverage + throughput floors)
- **v1.14.0**: infra truth — drift Refresh + Destroy stack with cost framing
- **v1.15.0-v1.17.0**: Bun + Deno first-class; HELM/BEACON/PRISM; DO deep (cloud-init, ssh-from-node, deploy log) + designer fit/zoom
- **v1.18.0-v1.19.0**: PING REST console + dialog-idiom sweep; API Studio module (collections, {{var}} environments, assertions, .nmoxapi.json)
- **v1.20.x**: standards with gusto — .editorconfig honored on save, Standards Kit wizard (robots/sitemap/manifest/RFC 9116 security.txt/humans), security-header grades on every API Studio response, WCAG as a VITALS gate; dialog-stacking fix
- **v1.21.0**: PWA Kit wizard — Java2D icon forge (maskable set), readable service worker (app-shell/network-first), offline page, idempotent index.html wiring
- **v1.22.0**: the Snow Leopard release — no new features; charset/EDT/leak/race fixes, ProcessSupport+ToolLocator promoted to core, shared HttpClient + JsonUtil, JOptionPane eviction, test backfill + floors, docs truth pass
- **v1.23.0**: the completeness release — Experiments manager (promote/discard UI), Hetzner drift/destroy/cloud-init + any-provider live deploys, VITALS gates all four Lighthouse categories, Ship Gate + Dev Intelligence presets, BEAM outlines, wizard validation, window shortcuts
- **v1.24.0**: Learning Spaces — a real in-rack REPL (InteractiveProcess engine + REPL device), New Learning Space… launcher, data-driven 51-space catalog (learn-catalog.json) across languages/frameworks/libraries; spaces live under ~/.nmox/learn
- **v1.25.0**: the daily-driver polish — Welcome rebuilt as a launchpad (start/recent/tooling columns, stamped-version footer), Environment Doctor (Tools menu; live-probes ~32 external tools with versions/install hints), daily update check (quiet, opt-out, dev builds skip)
- **v1.25.1**: cleanup — REPL interpreters that buffer piped stdin gained their force-interactive flags (python3 -i -q, node -i, lua/julia -i, ts-node -i), Doctor probes go with `go version`, first ui test, docs truth pass
- **v1.26.0**: the complete-system release — the whole deferred debt ledger worked end-to-end: rack undo/redo (⌘Z), Quick Search into API Studio requests + infra nodes, multi-provider cloud sync (DO/Hetzner/Cloudflare with per-provider failure isolation), TAIL/TEMPO resurrection, 8 more Navigator outlines (43 mimes), Options update-check toggle, SOLDER→CI export; the refactor items re-audited with fresh evidence (#7 startup measured at 7s cold, palette not the bottleneck; #1/#2 firmed won't-fix — repeated idiom, not repeated values)
- **v1.27.0**: the coverage release — ~320 new unit tests across all eight code modules; JaCoCo coverage now measured on the *testable surface* (pure-Swing windows/dialogs/canvases excluded at the root pom, each named with a reason); floors raised on six modules and added to project + ui (all eight now gated); the extra tests surfaced and fixed a real latent bug — WorkspaceTrust stored trusted paths as one joined preference value that would overflow java.util.prefs' 8 KB cap and break trust on a long-lived install, now one entry per path with legacy migration + a 200-path regression test
- **v1.28.0**: the LAMP/LEMP release — PHP made a first-class citizen: ARTISAN Laravel console device (41st device; serve/test/migrate/fresh/queue/routes, composer.lock version currency, READY/URL/SERVING wiring), TYPEGUARD→phpstan + GLOSS→Pint PHP AUTO lanes, IGNITION docroot-aware `php -S`, Apache config grammar (.htaccess/httpd.conf/apache2.conf/.vhost; generic .conf deliberately unclaimed), PHPUnit in Run Focused Test (both test-method shapes), "PHP Web (LEMP)" template (guarded front controller/cli-server router, phpunit+phpstan+pint wired, nginx+php-fpm+MariaDB compose, LEMP cloud-init for droplets) + LAMP Bench preset, Dockerize PHP (composer:2 → php:8.3-fpm-alpine + nginx sidecar), Doctor probes composer/mysql/nginx/apachectl, Database Explorer (already shipping, 10 db modules) surfaced in docs; two template bugs fixed in passing (nested dockerize paths, README heading indentation); MySQL learning space skipped with reason (REPL model needs a live server)
- **v1.29.0**: DB Studio — a new dbstudio module: database management tab (⇧⌘7) for SQLite/PostgreSQL/MySQL/MariaDB/MongoDB/CouchDB with bundled drivers (MariaDB Connector/J LGPL serves MySQL too; PG BSD-2; SQLite+Mongo Apache-2; Couch driverless over the shared HttpClient), DbBackend abstraction (JDBC + document backends), Keyring-only passwords + .nmoxdb.json specs, SqlSplitter/DocumentGrid pure cores, kind-aware console (SQL kit / JSON), per-statement result grids + history + real cancel, ⌘I search into connections/tables, 150 module tests (real SQLite e2e in CI) + 0.73 measured floor; plus the discovery layout — all six suite tabs (Workbench→Rack→DB→Infra→API→Docker) open by default on first launch, boot smoke 6s unchanged
- **v1.23.0**: the completeness release — every half-shipped idea finished: Experiments manager (promote/discard UI), Hetzner drift/destroy/cloud-init + any-provider live deploys, VITALS gates all four Lighthouse categories, Ship Gate + Dev Intelligence presets, BEAM outline families, wizard validation, window shortcuts (API Studio ⇧⌘8, Infra ⇧⌘9)
- **v1.29.x**: DB Studio arc — v1.29.0 the suite (dbstudio module: 5+ engines with bundled drivers, Keyring passwords, kind-aware console, ⇧⌘7, all suite tabs open by default), v1.29.1 the live click-through polish (peek-on-double-click, balloon feedback, honest RUN gating, first-run guidance, save-style SQLite chooser), v1.29.2 dev-launch branding (APP_DOCK_NAME conf, Welcome TOOLING gains DB Studio)
- **v1.30.0**: the Services bridge — NetBeans Database Explorer connections (ConnectionManager) appear in DB Studio's tree and run in its console by wrapping NB's live java.sql.Connection (any registered driver incl. Derby/Oracle; FETCH FIRST peeks; NB owns credentials/lifecycle); JdbcCore extracted from DbClient (one proven statement loop, headless test through the real NB API); dev ⌘Tab/dock icon fixed (committed nmoxstudio.icns copied to target by the application build)
- **v1.30.1-v1.30.2**: NPM Explorer lists global packages (`npm ls -g`) when no project is aimed; `--app-only` bundle build closes the last dev-launch branding gap (⌘Tab switcher label)
- **v1.31.0**: REPL grows up — ENGINE knob (26 engines derived from the learning catalog, force-interactive commands + HINTS + per-OS install seeded; CUSTOM on manual edit), INSTALL button (missing interpreters install from the rack, streamed onto the REPL screen), learning-space picker probes interpreter availability before creating anything
- **v1.32.0**: DB Studio 2, the working-DBA sprint — in-grid row editing (EditGate single-table/PK gating with honest read-only reasons, EditSession dirty tracking, UpdateBuilder dialect-quoted UPDATEs with status-bar-ready refusals, Apply-with-SQL-preview then re-run-for-truth), CSV/JSON export on every grid, persistent history (cap 50) + saved queries in .nmoxdb.json, .env connection offers (DB_*/DATABASE_URL; password → Keyring only), EXPLAIN button (engine-native plan syntax); SqlDialect extracted as the one quoting seam (doubles embedded quotes); ReDoS finding fixed by idiom not exclusion; debt #9 closed; dbstudio 317 tests, 87.1% line coverage
- **v1.33.0**: the Web3 release — Contract Studio (new web3 module, ⇧⌘6, suite-tab default-open): Foundry/Hardhat artifact tree, ABI-driven Interact (CALL decoded returns, SEND receipt polling, revert/custom-error decode), Watch (2s block poller + Keccak-topic-matched event decode), Oversight (gas report, EIP-170 verdicts, address book); no-private-keys boundary test-pinned (devnet unlocked accounts only, secret RPC URLs Keyring-only, URL redaction); vector-pinned cores (hand-rolled Keccak-256 w/ OpenSSL-validated multi-block vectors, ABI codec on the Solidity spec examples, regex-free gas parser); Solidity editor citizenship (grammar 0.0.187 pin, outline, LSP entry, keywords, spellcheck); foundry.toml toolchain + forge lanes; ANVIL (42nd) + GOVERNOR (43rd) devices + Web3 Bench preset; solidity/chisel learning space (52nd); 7 Doctor probes; web3 245 tests @ 89.5% (floor 0.80), rack 585
- **v1.33.1**: fresh-launch fix — the rack no longer aims at `$HOME` on first run (Project Studio's tree + Project Explorer's detection walked it on the EDT, hitting macOS TCC-protected `~/Desktop`/`~/Downloads`/`~/Pictures` and firing a permission-prompt storm that also froze first paint); fresh launches aim at a created-on-first-run `~/NMOX` workspace (recent/resurrected project still wins), and every startup filesystem walk moved off the EDT (lazy FileTreePanel children, detectKinds on a RequestProcessor, + Docker panel/ROSETTA/package-manager handlers found by audit); rack 593 tests, project 14
- **v1.33.2**: fixed a fresh-launch startup hang that v1.33.1 unmasked — moving startup scans off the EDT exposed a self-sustaining event storm (ProjectExplorer ran a full refresh on every PROP_OPENED/PROP_ACTIVATED across all 10 default-open suite tabs, each fanning out per-row async detection; previously masked because the old synchronous scan blocked on $HOME File.list before a 2nd iteration); fix = RefreshCoalescer (burst → one refresh, 500→1 test-pinned) + idempotent row updates; also InfraGraph.clear() no longer fires on an empty graph (was writing a stray .nmoxinfra.json into fresh ~/NMOX every launch); rack 594 / project 17 / infra 166 tests
- **v1.33.3**: the last startup storm — FlowCanvas.fit()/selectNode() busy-re-posted invokeLater(self) while the canvas was 0×0 (Infra tab open-but-unselected on fresh launch → infinite loop, EDT pinned, first paint starved; pre-existing, unmasked like its v1.33.2 siblings); fix = deferUntilSized (arm ONE resize listener, run body once; regression tests proven to fail on the old code); repo-wide audit of all 116 invokeLater sites + paint/layout + listener paths found no more loops; live-verified fresh boot (window paints, EDT idle ~4%, ~/NMOX aim, zero prompts); infra 170 tests. Lesson pinned in memory: locked-screen sessions map no windows — check lock state before calling a clean boot hung
- **v1.33.4**: click-surface branding on both desktop OSes — macOS: build-dmg.sh ships the icns under BOTH names (nmox-studio.icns for Info.plist + nmoxstudio.icns for the launcher's -Xdock:icon, whose dangling path was overriding bundle attribution → default Java icon); Windows: BrandingArtGenerator emits a committed multi-res nmox-studio.ico, Inno Setup brands the setup exe + both shortcuts, release workflow rcedit-patches every launcher exe, new windows-installer-check workflow byte-verifies it all on windows-latest
- **v1.34.0**: the classic web release — script-tag sites open as STATIC projects (last-resort kind, suppressed by any real manifest; IGNITION serves them with READY/URL); bower.json/Gruntfile/gulpfile/webpack.config are manifests (30 total); FORGE config-file lanes + grunt/gulp knob positions; CRATE bower lane; DYNAMO device (44th — static-parses Gruntfile/gulpfile tasks onto a knob, GO runs via npx); LegacyWeb detection with jQuery 1.x/2.x EOL chips; Classic Kit wizard (vendored pinned jQuery 3.7.1/MooTools/Prototype/Backbone+Underscore/Knockout with sha256 NOTICE, or npm deps; webpack/grunt/gulp/bower generators, never-clobber .suggested siblings; HtmlWiring seam shared with PWA Kit); Classic Web (jQuery) template; CoffeeScript citizenship (48 grammars, 45 outline mimes); classic-library-aware completion (295 API entries, manifest-mtime-cached detector, dotted matcher); Doctor probes webpack/grunt/gulp/bower/coffee; rack 671/editor 341/tools 92/ui 39
- **v1.35.0**: the connections release — ServingRegistry (7 serve devices announce URLs; status-line ⇄ chip, ⌘I Live Servers, VITALS/BEACON auto-target, API Studio {{baseUrl}} offer, Contract Studio ANVIL auto-connect/grey) + ManifestPulse (16 manifest names; NPM-9000/DYNAMO/CRATE/HALO/ARTISAN re-sync on save, coalesced 10-writes→1, .env → status note + studio hooks); Contract Studio ArtifactPulse (out/ is in FileWatcher's skip-dirs — purpose-built poller), DB Studio Docker connection offers (DockerDbOffers.plan: image-name then port inference, cap 2/pass, once per container/session), all four .nmox*.json files external-edit reload with self-write-stamp discrimination + never-clobber dirty guards (infra holds its debounced save on conflict), "Open manifest" device context jumps, ServeUrls/scripts() dedup; every listener bounded+equality-guarded+storm-tested; rack 714/dbstudio 349/web3 271/infra 181/apiclient 91 (+133)
- **v1.35.1**: the finishing pass — PostgreSQL JDBC 42.7.5→42.7.11 (clears both HIGH Dependabot advisories); REPL INSTALL runs compound catalog commands via /bin/sh -lc when shell operators present (foundryup pipe+&&, PyTorch trailing-# entries; SOLDER deliberately keeps its no-shell stance for user-typed commands); php -S lane registers with the ServingRegistry; API Studio follows mid-session re-aims (force-save old → load new → re-point pulse; 100-event storm → 1 load, A→B→A bounce → 0); DB Studio Docker offers wait for tab visibility (probe on componentShowing, guard consumed only on displayed offers — the v1.35.0 live-demo finding); SelfWriteTracker promoted to core (web3+apiclient dedup); core 13/rack 719/dbstudio 353/apiclient 94/web3 268
- **v1.36.0**: the senior review release — six read-only audit lenses (platform architecture, device/process lifecycle, listener symmetry, timers, EDT+process hygiene, API quality) then fixes for everything proved, blessings-in-writing for the deliberate-but-odd; the house laws HELD (orphan guarantee, storm laws, Keyring boundaries, v1.35 listener symmetry). Fixed: Infra Designer's inverted listener lifecycle (close/reopen killed auto-save + cross-project write vector), DockerClient's fictional timeout (wedged daemon bricked Docker silently; regression test fails on old code), the EDT mutation half (New Project's 4 git spawns, Experiments discard/promote, file-tree recursive delete, 5s session-snapshot writes), corrupt workspace .bak-before-fallback ×4 studios, ghost-serving/disposed-device races (flag cleared on undo re-attach), JS context-menu layer collision + dup Editors merge + 4 ⌘I position collisions, last JOptionPane + all 15 printStackTrace downgrades, cloud tokens prefs→Keyring w/ delete-only-after-save migration. New core ProcessSupport.runBounded (waitFor-first, drains on threads) adopted at 5 sites. Deleted: tools.build service, CodeIndexService, sample module from product, dead deployment profile, ui wildcard export (net −3,000 lines). Ledger items 15–24 recorded with reasons. core 18/rack 734/tools 59/project 18/ui 39/editor 336/infra 193/apiclient 97/dbstudio 355/web3 271 = 2,120
- **v1.37.0**: the debugging release — JavaScript/TypeScript breakpoint debugging out of the box: vendored `js-debug` v1.117.0 (MIT, 2.5MB, nbmResources → InstalledFileLocator) + `DapProxy`, a DAP session multiplexer that answers js-debug's `startDebugging` reverse request, dials the child session, replays breakpoints and splices it in — the platform's stream-based DAP client can't dial a second socket, so without it the debuggee pauses forever (verified live before a line was written). `RealJsDebugIntegrationTest` drives the real adapter end-to-end and pins the transcript. `ProcessSupport.killTree` made public (descendants first: the debuggee is the adapter's child). Three bugs fixed, each with a test proven to fail on the old code — two found by the E2E click-through, one by ubuntu CI: **the proxy slammed the client's socket on `terminated`** (close() discarded bytes still unread in the peer's receive buffer, so a finished session could stay "live"; macOS's reader usually won the race, Linux never does — now a half-close, FIN queued behind the frames; reproduced in a Linux container, mutation-proven); **debugging bypassed Workspace Trust** (all three languages ran project code with no prompt — now gated, "Keep Safe" blocks before any spawn) and **IGNITION's static lane served silently** (python block-buffers its "Serving HTTP on" banner to stdout, so READY/URL/serving-chip/⌘I never fired; `python3 -u`; the old test injected the banner into onLine() and passed while reality failed). Ledger items 25–26: one child session per debug run (children run undebugged); the vendored adapter is invisible to Dependabot/SBOM (manual bumps, sha256 in NOTICE). core 19/rack 735/tools 59/project 18/ui 39/editor 355/infra 193/apiclient 97/dbstudio 355/web3 271 = 2,141
- **v1.38.0**: the startup-truth release — measured first (startup-log phases + JFR across cold/warm/dev-tree/installed boots), then fixed what the profile named. The v1.26-era "7s cold start" no longer reproduces (the v1.33.x storm fixes removed it): window paints in 1.4s (warm OS cache) to 2.7s (first boot), ~90% of it the platform module system scanning 519 cluster jars — the deliberate feature-set price. The real waste was hidden default-open tabs working at boot: **NPM Explorer spawned `npm ls -g` twice per boot** (the only boot-time processes in the whole IDE, per JFR), Contract Studio walked the artifact tree, the Docker panel ran its dockerize detect walk, and Project Explorer/Project Studio double-fired their scans (ctor + componentOpened). All gated on componentShowing (the DB Studio v1.35.1 idiom) or deduped; rack re-aims and build pulses behind hidden tabs coalesce to one deferred pass on first show. Pinned by source-gate tests (NPM one proven to fail on old code); live-verified — fresh boot = 0 ProcessStart events, each tab serves its deferred work on first click. Audit blessed the rest: no default-open tab hits the network at boot. plan.md's perf item struck with fresh numbers. core 19/rack 737/tools 61/project 19/ui 39/editor 355/infra 193/apiclient 97/dbstudio 355/web3 272 = 2,147
- **v1.38.1**: the DX pass — four senior-developer journeys walked E2E in the real app. The engines held (v1.37.0 debugging stopped a live HTTP request mid-flight in a real Node service; trust prompt named the right project root; Stop left zero orphans), and the bugs were all one keypress off the feature path: **four of seven advertised shortcuts opened the wrong window** (⌘0→Editor, ⇧⌘6→Tasks, ⇧⌘7→Properties, ⇧⌘8→Palette, and ⇧⌘O→Open Project, because Keymaps-profile registrations beat `Shortcuts/`-folder ones — studios moved to the unclaimed ⌥⌘6–9 / ⌥⌘0 / ⌥⌘O family, WindowShortcutsTest pins chords + Welcome labels, mutation-proven), **a debug session's Output never surfaced** (the debuggee's console existed but nothing opened it), **Open Folder started in $HOME** (now the ~/NMOX workspace). Ledger 27–28 record what we won't fix yet: the platform's Breakpoints window never lists DAP breakpoints (reproduced with Python, none of our code — the gutter is the manager, user guide says so) and Window-menu items show no accelerator. plan.md gains the failure pattern: *a UI affordance documented but never exercised is untested*. ui 42/dbstudio 355/web3 272/apiclient 97/infra 193/project 19/editor 355
- **v1.39.0**: the idiom release — five senior-RCP lenses (Lookup/services/actions, window system, FileSystems/DataObjects, threading/utilities, module wiring) audited the whole codebase; twelve cheap-and-right fixes shipped: **file CRUD through DataObject** (open editors followed deletes/renames for the first time; rename via locked FileObject.rename because DataObject.rename's extension handling is loader-dependent — proven by a failing test), **atomic workspace writes ×5** (core AtomicFiles: temp sibling + ATOMIC_MOVE; kills the torn-read/false-foreign race SelfWriteTracker existed to prevent), **Rack's per-instance shutdown-hook leak** → one static reaper over a live-set (RackReaperTest), **Docker Panel PERSISTENCE_NEVER+openAtStartup contradiction** (the one tab that ignored being closed), **ctor→componentOpened workspace loads ×3** (double-load fixed; DB Studio's open-time Docker probe caught trying to spawn behind a hidden tab and gated on isShowing — zero-boot-spawns held), **apiclient raw threads → RequestProcessor("API Studio")**, **NpmService off ForkJoinPool.commonPool + getInstance→getDefault**, **DockerClient @ServiceProvider**, **normalizeFile ×2** (dead hyperlinks on relative paths), **Utilities.isMac/isWindows ×3** (BaseUtilities in core — no util-ui dep). Ledger 29–36: rack-as-context-system (identity — migrate as its own release or never), LinkageError soft-deps, friend-less rack exports (tripwire: before any plugin SDK), DiagnosticsBus vs HintsController, studios in editor mode, ProgressHandle gaps, no @OnStop seam, FileTreePanel UI half. Blessed in writing: polling over FileObject listeners, RackBus, hooks-over-@OnStop for reaping, WorkspaceTrust global prefs (now documented in-class), modeless JDialog viewers. core 22/rack 743/tools 62/project 19/ui 42/editor 355/infra 194/apiclient 100/dbstudio 356/web3 274
- **v1.40.0**: the git surface — recon first (the ide cluster already ships 12 git modules; the gap was OUR chrome): status-line branch chip (GitFacts pure core: HEAD parse incl. gitdir indirection + detached heads, porcelain counting; GitChip model w/ boot-guard state machine, source-gate tested — branch is a file read, the git spawn fires only on aim/click/visible-30s-tick), one-click History via the git module exported API (reflection over the friend-restricted package, degrades to a status message). The chip menu ships ONLY what works selection-free: the platform Show Changes/Diff/Annotate are NodeActions reading the global selection no NMOX window publishes — three invocation strategies tried live (context lookup, node delegate, setActivatedNodes assist), all refused, so ledger 29 (the context-system migration) gains its first concrete customer and the Team menu keeps those verbs. Live E2E: real repo, chip main +-1, History shows real commits; API quirk pinned in a comment (openSearchHistory String arg is a commit-ish, not a path). core 31/rack 751 (+GitFacts 9, GitChip 8)
- **v1.41.0**: the accessibility release — the widget library (Knob/RackButton/ToggleSwitch/Led/LcdDisplay/VuMeter) implements Swing accessibility: roles (SLIDER/PUSH_BUTTON/TOGGLE_BUTTON/LABEL/PROGRESS_BAR), AccessibleValue on knobs+meters, state/text announcements (EDT-marshaled, guarded so zero cost without assistive tech), keyboard operation (arrows/Home/End on knobs through the same listeners as mouse drags; Space/Enter on buttons incl. dimmed-refusal parity), FOCUS_RING in RackStyle (color law reserves GO/STOP/MUTATE/QUERY for meaning). DeviceContractTest gained the name law — every control on every faceplate exposes a non-blank accessible name — which failed-first on 59 controls across 40 devices (1 CommandDevice fix covered ~26; 30 device-side names; LCD label-less class backstopped via param keys). Found+fixed: RackTopComponent's global Tab-swallow (rear flip) ate focus traversal — now yields when a control has focus. A11yContractTest 19 tests; rack 785 (+63)
- **v1.42.0**: the Windows lane — windows-latest joins the CI matrix as a blocking full-verify gate (tests+SpotBugs+floors+audit; green in 7m24s; probes stay POSIX, ledger 37). Getting green = 5 fixture fixes (canonical paths vs Git Bash /tmp mounts, CRLF folding in DeviceDocsTest, per-OS INSTALL expectation, two @TempDir cwd-lock cleanups incl. the js-debug E2E — which passes on Windows — and a real DB Services connection leak) + 3 product fixes with regression tests: **LanguageServerCatalog never detected any server on Windows** (canExecute on bare names; now probes .exe/.cmd like ToolLocator), **DapProxy disconnect race** (stop mid child-handshake left the debuggee alive on every OS — gate on childSocket, test freezes the dance), **ProcessSupport.killTreeAndWait** (destroyForcibly is async; Windows holds file locks until death — JsDebugServer.stop waits bounded). ONE disable with runner-proven evidence: Git Bash breaks the Windows PID chain, MSYS grandchildren invisible to descendants() (ledger 38: Job Objects). plan.md gap #1 closed + new @TempDir-process failure pattern
- **v1.43.0**: browser debugging — the v1.37.0 adapter's second face: "Debug in Chrome (breakpoints)" on text/html + JS/TS (popup 8100 behind Debug File), same WorkspaceTrust gate before any spawn. Recon-first (four preserved transcripts of the real adapter + headless Chrome): simple pages fire startDebugging ONCE on the parent link (DapProxy unchanged — worker targets fire on the CHILD link, already answered there), breakpoints verify late but hit with exact webRoot source mapping, and a client disconnect alone kills the whole browser in <3s (js-debug cleanUp=wholeBrowser; killTreeAndWait backstops). URL pick: exact-project ServingRegistry WEB serving → containing-dir serving (monorepo) → file:// for .html → honest status for bare scripts. BrowserLocator (Chrome→Edge→Chromium, per-OS candidates, ToolLocator idiom); headed Chrome on a throwaway profile under the userdir cache, deleted only after the tree is confirmed dead. Ledger 39 (the recon surprise): an answered-but-unattached WORKER target pauses forever — different from Node's run-undebugged; no browser autoAttachChildProcesses:false exists. RealChromeIntegrationTest (real adapter + real headless Chrome + in-JVM fixture server; disconnect-kills-Chrome asserted on live ProcessHandles; mutation-proven after a stale-compile false negative). editor 373 (+18)
- **v1.44.0**: the debt sweep — ledger 15/16/17/22/26/28 closed + 34 mostly, each with proving tests: async Stop All/switch (Rack.stopAllAsync on RequestProcessor("Rack Stop",4); EDT freeze measured at 1,513ms/device on old code by the regression test; switch swap callback-driven, dialog unchanged; shutdown reaper synchronous + source-gated; orphan guarantee proven on ProcessHandles), SaveLane ×4 (EDT snapshots + single-throughput named writers; write+stamp one task; classify() rides the lane so foreign-vs-own can't misfire; close/reload drain; DbWorkspaceIO was the last non-atomic writer — caught in passing), listener symmetry on RackPanel/RackTopComponent (mutation-proven lifecycle tests; two headless-safety touches), ProgressHandles on DB connect + cloud sync (honest no-cancel comments — no interrupt seams), Keymaps shadows give Window-menu items their accelerators (WindowShortcutsTest pins chord AND target instance — the v1.38.1 drift class structurally dead), userdir_root warning killed by an @OnStart setter (conf word-splits on "Application Support"), SBOM gains the vendored js-debug component. rack 827/apiclient 108/infra 202/dbstudio 364/web3 282/ui 43/project 19
- **v1.46.0**: the soft-dependency release — ledger 30+31, the same surgery: core exports org.nmox.studio.core.spi (ProjectAim: projectDir/aim/recentProjects + projectChanged/manifest listeners; LiveServings: snapshot + coarse listeners) with rack publishing thin @ServiceProvider adapters (pure delegation, wrapper-mapped listeners — double-add never double-delivers); 31 catch(LinkageError) sites converted to find()-and-null-branch across apiclient(6)/web3(6)/dbstudio(5)/project(6)/tools(4)/infra(2)/ui(1); apiclient/web3/infra dropped the rack Maven dep (RackSoftDependencyTest ×3 pins lookups-null + rack-unloadable); kept catches carry KEPT/why comments (dbstudio's FileWatcher/DockerClient, project's rack UI surface, platform-optional Keyring/notifications/kits); OpenIDE-Module-Friends on rack = editor/tools/project/ui/dbstudio exactly (manifestEntries — the nbm plugin has no friends param; byte-verified in the built jar); TrustGate deliberately not facaded (editor's hard dep stays for CommandExecutor). SoftDependencyGateTest mutation-proven; storm tests ride FakeLiveServings with the registry's real threading contract. core +4/rack +9/apiclient/web3/infra +2 each
- **v1.45.0**: the context release — ledger 29 as its own release: the OpenProjects bridge (findProject→open+setMainProject on RequestProcessor("nmox-project-bridge"); re-entrancy flag beats the WebProjectOpenedHook echo — proven by a hook-re-enter test asserting proceed runs EXACTLY once; passive aims source-gated + behaviorally proven processless; never-close source-gated), AimNodePublisher (off-EDT DataFolder-node resolve → EDT setActivatedNodes on Rack/ProjectStudio/Workbench, equality-guarded, componentShowing-gated), git chip verbs restored via createContextAwareInstance on the published node. LIVE: Team menu = full enabled git suite with only a project aimed (v1.40.0's disabled stub), chip Show Changes opens the real Git window on the real dirty file, fresh boot = 0 children. Ledger 29 remainder honest: Kit actions stay always-enabled (focus-keyed enablement would disable them in the editor — a regression masquerading as idiom), seven non-aim windows still lookup-less. rack 844/project 21 = 2,342 total
- **v1.47.0**: the spec-versions release — ledger 20 closed: module OpenIDE spec versions track the product version off one root `<spec.version>` property instead of the pom-derived 1.0 frozen since v0.1. Mechanism chosen by evidence: jar-plugin manifestEntries tried first and rejected (maven-archiver lets the nbm-generated manifestFile win on conflicting keys — tested; rack's Friends entry survives because nbm never emits that key), reactor-version schemes rejected to keep the tag the only version source; shipped = nbm's own sourceManifestFile seam (each module's src/main/nbm/manifest.mf declares ${spec.version}, a root filter-nbm-source-manifest execution interpolates it, conditionallyAddAttribute keeps it verbatim). Reactor ordering makes downstream Module-Dependencies read the real version off dependency jars (core > 1.47.0), so a module dropped into an older install is refused by the loader — the ledger-30 hardening. Release workflow stamps the property in the same three per-OS steps as branding's currentVersion (committed dev "NMOX Studio 1.0" untouched: Versions.extract reads 1.0 as dev and keeps dev builds out of the update check). SpecVersionGateTest (application, 3 tests, mutation-proven): all 11 shipped jar manifests equal the injected property, every source manifest carries the placeholder exactly once. Byte-verified across the assembled cluster; headless boot smoke clean in 4s
- See CHANGELOG.md for the full record; tagging vX.Y.Z triggers the release workflow (6 assets incl. SBOM)

## Documentation

- `docs/user-guide.md` - The user manual: install → first launch → projects → rack → studios → wizards → safety nets. Part of the docs truth pass — keep it accurate every ship
- `docs/making-a-smart-contract.md` - Worked tutorial: a real escrow contract built the Contract Studio way (code, tests, gas gate, live ANVIL loop) — all output real
- `docs/devices.md` - The 44-device reference (GENERATED from DeviceType by DeviceDocsTest; CI fails on drift)
- `docs/engineering/tech-debt.md` - The CURRENT debt ledger: open items with deferral reasons, closed items by version
- `docs/engineering/plan.md` - The CURRENT plan: where the project stands, honest gaps, ranked opportunities, the working method + house laws + failure patterns — read this first when deciding what to do next
- `CONTRIBUTING.md` - Contribution guidelines
- `docs/hack/` and `docs/product/` and most of `docs/engineering/` - v0.x-era documents kept for archaeology; every one carries a "Historical document" banner and none describes the shipping product

## Troubleshooting

**Build fails with "invalid target release: 17"**:
- Ensure Java 17+ is active: `java -version`
- Set JAVA_HOME: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`
- Or specify Java explicitly: `JAVA_HOME=/path/to/jdk17 mvn clean package`

**"Cannot run on older versions of Java than Java 17" error**:
- This error has been fixed in recent builds
- Use `--jdkhome` flag when launching: `./bin/nmoxstudio --jdkhome /path/to/jdk17`
- Or set in config: edit `application/target/nmoxstudio/etc/nmoxstudio.conf` and set `jdkhome` property
- The launcher now requires Java 17+ (updated from 1.8+ in nbexec script)

**IDE won't start**:
- Check `application/target/nmoxstudio/` exists
- Try rebuilding: `mvn clean package -DskipTests`
- Check logs in `~/Library/Application Support/nmoxstudio/dev/var/log/`
- Verify Java 17+ with: `java -version`

**Java module access errors (InaccessibleObjectException)**:
- Fixed: Java module `--add-opens` flags are now automatically configured
- These are required for NetBeans Platform to work with Java 9+
- Configuration is in `application/src/main/resources/nmoxstudio.conf`

**Tests fail**:
- Ensure Java 17+ is used for testing
- Some tests may require GUI environment (use `./gui-test.sh`)

**Module not found errors**:
- Rebuild from root: `mvn clean install`
- Check module is listed in root `pom.xml` `<modules>` section

**"CodeCache is full" warning**:
- Increase code cache size: add `-J-XX:ReservedCodeCacheSize=256m` to `default_options` in conf file
- This is normal for large NetBeans Platform applications

## External Resources

- [NetBeans Platform Developer Guide](https://netbeans.apache.org/kb/docs/platform/)
- [NetBeans API Documentation](https://bits.netbeans.org/22/javadoc/)
- [Maven NetBeans Plugin](https://netbeans.apache.org/wiki/DevFaqActionAddProjectCustomizer)
- [FlatLaf Look and Feel](https://www.formdev.com/flatlaf/)
