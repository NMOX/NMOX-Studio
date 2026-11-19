# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NMOX Studio is a NetBeans Platform-based IDE for modern web development, with first-class polyglot support (JS/TS, Java, C/C++, Python, Ruby, Rust, Go, PHP, shell + configs), NPM integration, project templates, and build tools. It's built as a multi-module Maven project with the NetBeans Rich Client Platform (RCP).

**Status**: shipping (v3.13.1; v3.13.0, tagged 2026-11-13, was published on 2026-11-19 once the Apple notary agreement was accepted and its failed jobs were re-run, no new tag) — **What a command line can prove.** The first session worked from a terminal instead of the desktop app (2026-11-19), so the open ledger was read for what a driver, a daemon or a toolchain could settle. `CLAUDE.md` itself was 626,700 characters against a reader that stops at 150,000 (every ship stacked its status paragraph on the last): the history moved to `docs/engineering/status-history.md` and `ClaudeMdSizeGateTest` holds a budget and ONE status paragraph, which a ship REPLACES. MEASURED AND FIXED: a MongoDB or CouchDB host could carry a proxy, an auth mechanism or another server (`HostileDocumentHostTest`; one host rule for every server engine, `DbClient.hostCarriesUrlSyntax`); a Yarn Plug'n'Play project got an image that could not start, Yarn 4 now leaves devDependencies out (`workspaces focus`, measured on 4/3/2), a Deno project is started by its task or its real entry, a library-only Rust crate is told so (four recipes built and answered on a real daemon, three new proof variants); a refused login (SQLSTATE 28000, measured on mysql:8.4 and mariadb:11) is no longer called a certificate problem, and a Services connection's refusal no longer names the Use TLS box; the Gleam space has a test (`1 passed`) and the Playwright space installs one browser and says what it costs. Twenty mutants by name. *A ledger entry that says "not measured" is an instruction, and the instrument may be one `docker run` away.* NOT verified: anything on a screen; a zero-install Yarn project; a hostname-mismatch certificate. Earlier status paragraphs and the per-version history live in `docs/engineering/status-history.md`; `CHANGELOG.md` is the full record.

## Working Method (read before coding — human or AI)

The long-form culture lives in CONTRIBUTING.md ("How this house
works"), docs/engineering/plan.md (the living plan + failure
patterns), and docs/engineering/codebase-guide.md (the five RCP ideas
+ house laws with their origin incidents). The operational
distillation:

**The unit shape.** Recon first (decompile/probe the platform, never
folklore) → build with tests in the same change → **walk it in the
ASSEMBLED app** (`application/target/nmoxstudio/bin/nmoxstudio`,
throwaway `--userdir`, `-J-Dplugin.manager.check.updates=false`) →
review the fresh code with hostile lenses within a day or two → docs
truth (CHANGELOG + README + user guide in the same ship). Most real
finds come from the walk and the review, not the first write.

**The proof protocol.** Interesting tests are mutation-proven: break
the code the way the test exists to catch, watch the NAMED test fail,
restore, watch it pass. Only full verdict lines count
(`Tests run: N, Failures: N`) — a missing verdict is a failed run.
Commit work BEFORE any mutation; a bare `git checkout --` restore has
eaten uncommitted fixes repeatedly. Test each OR-branch alone;
equivalent mutants get documented and structurally pinned.

**The standing laws** (each paid for by a shipped bug): refusals
speak — nothing fails silently; secrets are keychain-only; no spawn
of project-controlled code without Workspace Trust BEFORE the spawn
(SpawnSiteTrustLedgerTest classifies every site, ServerTrustLedgerTest
every language server: most servers RUN what they analyse); every read bounded;
writes atomic + never-clobber (corrupt files → `.bak`); no disk/process
work on the EDT (named RP, newest-wins, EDT apply); a result belongs
to the workspace that produced it (re-aim clears everything armed);
every runtime invariant over a checked-in file gets a parse-time
heal; every control carries an accessible name (contract-tested).

**Gates gate outcomes, not mechanisms.** Derive gate populations from
generated artifacts (the layer, the jar, the census) so unanticipated
cases fail the build. Docs-landed checks read COMMITTED content
(`git show HEAD:…`), grep -F for literals (regex metachars have faked
green five times), and the next version number comes from the
CHANGELOG head — never from memory.

**Shipping.** Every change rides `mvn clean verify` (tests + SpotBugs
+ find-sec-bugs + JaCoCo floors — a new finding is usually a real
bug, fix it, don't exclude it) → PR → three-OS blocking CI →
squash-merge → tag → the release workflow publishes 21 assets incl.
the update-center catalog and the signed checksums. `scripts/ship-branch.sh` (in-repo since v2.68.1:
rebase from the FORK SHA CAPTURED AT FORK TIME, the docs-collision
resolver, line-anchored marker checks, the title as a quoted argument,
every exit code checked) opens the PR; `scripts/ship-gate.sh` (in-repo,
parameterized; it names the squash commit itself through
`scripts/squash-subject.sh` since v3.5.9) automates PR-to-tag with the stale-rollup grace,
fetch-never-checkout, and the tree-identity fast path; `scripts/post-ship.sh`
(in-repo since v2.68.2) waits for the assets, fast-forwards main, hops the
cask, deletes the merged branch. `scripts/update-gauntlet.sh <from-tag>`
(in-repo since v2.69.2) is the close proof: a stock portable of an older
release updates itself in-app to the newest and boots clean, first-boot
half included. In chained
scripts, guard mvn's exit code EXPLICITLY — `set -e` has let a red
verify through. Never tag unverified; a published tag is never moved. One working
tree, one chain: never run two ship chains (or a chain plus
interactive git) in one checkout — a chain commits onto whatever
branch HEAD points at by then (measured: a docs chain committed onto
a sibling feature branch and pushed an empty one). Chains assert
`git rev-parse --abbrev-ref HEAD` before every commit and push;
parallel releases get separate worktrees. And a multi-line `grep -F`
pattern is an OR of its lines — every enforced literal is ONE line.

## Build and Run Commands

### Prerequisites
- **Java 25+** (the bundled runtime and CI are JDK 25 LTS since v1.253.0; the compiler still TARGETS 21 bytecode on purpose — see the law at maven.compiler.target)
- **Maven 3.6.3+** (both floors are enforced at `validate`)

**IMPORTANT**: BUILD with JDK 25. JavaFX 26's jars are class-file 68 (JDK 24+),
so the `ui` module cannot be compiled by an older javac — and the failure does not
say so twice: javac reports `bad class file … wrong version 68.0` once, abandons
annotation processing, and every `@Messages`-generated `Bundle` reference in the
module then fails, so a tailed log is hundreds of `cannot find symbol: variable
Bundle` lines pointing at the wrong file. (The compiler still TARGETS 21 — that
law is about the bytecode emitted, not the JDK you build with.)
```bash
# Set Java for build — a JDK 25, which `/usr/libexec/java_home` may not list
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
# the installed app ships one too: "/Applications/NMOX Studio.app/Contents/Resources/nmoxstudio/jre"

# Alternative: use --jdkhome when launching IDE
./application/target/nmoxstudio/bin/nmoxstudio --jdkhome "$JAVA_HOME"
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

- `./build.sh` - `mvn clean install -DskipTests` after checking the JDK Maven will use (25+); `--verify` runs the full gate
- `./run.sh` - Boots the assembled app with `./userdir`, its own cachedir, update check off
- `scripts/boot-smoke-test.sh` - Headless boot of the assembled app (the CI gate)
- `scripts/platform-walk.sh <out>` - Boots and photographs the assembled app on THIS OS; the **Platform walk** workflow runs it on Windows and Linux, on demand or on a push to a `walk/**` branch (push workflow files over SSH). Read the pictures; walk in a throwaway home, because your own data hides first-launch defects. The app is started with a Docker address nothing listens on (`NMOX_WALK_DOCKER_HOST` names a view for a walk about Docker)

CONTRIBUTING.md's **The inner loop** is the day-to-day command list
(`-o install`, one-module rebuilds, `-Dtest='A,B'` with commas, never `-q`,
rebuild from the root before booting). The v0.x `gui-test.sh`,
`setup-and-run.sh` and lexer-demo fossils were removed in 3.1.0.

The comprehensive suite is `mvn verify` — tests plus the SpotBugs,
find-sec-bugs, and JaCoCo gates. (The v0.x-era test-everything.sh /
quick-test.sh scripts checked classes that never shipped and were
removed in v1.22.0.)

## Module Structure

The project is organized as a multi-module Maven build using NetBeans Platform:

```
NMOX-Studio/
├── core/                    # Shared plumbing: process/tool/file/HTTP helpers, the SPI seams, the Device SPI
├── editor/                  # File type support, JavaScript lexer, syntax highlighting, completion
├── tools/                   # NPM integration, build tools
├── rack/                    # Reason-style task rack: drag-drop device wiring, control surfaces
├── infra/                   # Node-RED-style multi-cloud infra designer (DO/Hetzner/Cloudflare)
├── apiclient/               # API Studio — Postman-style request/collection/test suite
├── dbstudio/                # DB Studio — SQL + document database suite (6 engines, bundled drivers)
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
| **core** | Cross-cutting platform touches + shared SPI | `TerminalPhosphor` (phosphor Terminal theme on first run); `spi.device` (the frozen public **Device SPI** third-party rack devices implement); `spi` facades (ProjectAim/LiveServings soft-dependency seams); `ProcessSupport`/`ToolLocator`/`AtomicFiles`/`HttpClientFactory` |
| **editor** | File editing and language support | `JavaScriptLexer` (regex-aware), `JavaScriptDataObject`, `TypeScriptDataObject`, `WebFileSupport` (HTML), 90 TextMate grammars incl. HTML/CSS/SCSS/Less + the config layer, JS/HTML/CSS completion providers, LSP providers, `OutlineModel`/`StructureNavigatorPanel` (Navigator outline for 64 mimes), `ConfigFileResolver` (dotfile MIME), `debug/DapDebugAction` + `debug/dap/` (DapFrames/DapProxy/JsDebugServer — flattens js-debug's child-session protocol for the platform's single-session DAP client; adapter vendored at `src/main/release/jsdebug/`) |
| **tools** | Development tools and integrations | `NpmService`, `WebProjectFactory`, `NpmExplorerTopComponent` |
| **rack** | Reason-style virtual task rack + project lifecycle | `RackTopComponent`, `Rack`/`RackDevice` model, 53 devices (incl. WAYPOINT workspace selector, KVASIR (formerly ORACLE) AI error explainer — Claude, ChatGPT or Gemini via `KvasirProvider`, ROSETTA mixed-repo selector, QUORUM lane-join barrier, IGNITION polyglot runtime, INSPECTOR debug launcher, ARTISAN Laravel console, the REPL, ANVIL local EVM chain, GOVERNOR gas gate, DYNAMO Grunt/Gulp runner, HARBOR docker, BLACKBOX flight recorder, SONAR port scanner, PREFLIGHT ship check, PHOSPHOR terminal), cross-lane coordination (QUORUM join, `RackDevice.enableGate` readiness gates on long-runners, REFLEX per-lane GLOB routing), patch-cable wiring, `FileWatcher`, `RackIO` persistence, `RackService`, `ProjectStudioTopComponent` (templates, file CRUD, package.json editor, presets) |
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

- **core** depends only on base NetBeans APIs (plus its own org.json copy) and exports the `core.spi` seams
- **rack** depends on core; its exports are friend-locked to editor, tools, project, ui and dbstudio
- **editor**, **tools**, **project**, **ui** and **dbstudio** depend on core + rack (ui also on JavaFX, provided scope)
- **apiclient**, **web3** and **infra** depend on core only and reach the rack through `core.spi` lookups (null when absent)
- **application** depends on all active modules

## Development Workflow

### Adding a New Feature

1. Identify the appropriate module (or create new one)
2. Add dependencies to module's `pom.xml`
3. Implement using NetBeans Platform APIs
4. Register services/components via annotations
5. Add unit tests
6. Update layer.xml if adding UI components or actions
7. Walk it in the assembled app (`./run.sh`, or the command in Working Method with a throwaway `--userdir`)

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

The project uses JUnit Jupiter (the 6.x line; the version is `junit.version` in the root pom) with AssertJ assertions, and Mockito where a seam cannot be driven directly. A real one, from `core`'s `VersionsTest`:

```java
@Test
@DisplayName("extract finds the version inside branded and tagged strings")
void extraction() {
    assertThat(Versions.extract("NMOX Studio 1.24.0")).isEqualTo("1.24.0");
    assertThat(Versions.extract("v1.25.0")).isEqualTo("1.25.0");
    assertThat(Versions.extract("no version here")).isNull();
}
```

Integration tests can use NetBeans Platform test harness in the `test` scope.

## Key Implementation Details

### JavaScript Syntax Highlighting

Location: `editor/src/main/java/org/nmox/studio/editor/javascript/`

The JavaScript lexer (`JavaScriptLexer.java`) provides token-based syntax highlighting using NetBeans Lexer API. Tokens are defined in `JavaScriptTokenId.java` and language hierarchy in `JavaScriptLanguageHierarchy.java`.

**Token categories**: keywords, identifiers, operators, literals, comments, whitespace

**Integration**: the editor kit is registered with `@MimeRegistration` (`JavaScriptEditorKit`); `@LanguageRegistration` is the CSL path the TextMate languages take

### NPM Integration

Location: `tools/src/main/java/org/nmox/studio/tools/npm/`

- **NpmService**: Executes NPM commands, parses package.json
- **WebProject**: A full platform `Project` — its Lookup carries `Sources`, an `ActionProvider` (Run/Build/Test/Clean → `WebProjectCommands` per toolchain, executed via the rack's `CommandExecutor` with a `ProgressHandle`), `RecommendedTemplates`, and a `ProjectOpenedHook` that aims the rack
- **WebProjectFactory**: `@ServiceProvider(ProjectFactory.class)` — recognizes 63 manifest names (package.json, Cargo.toml, go.mod, pom.xml, …) as projects
- **NpmExplorerTopComponent**: UI for browsing and running NPM scripts

Projects are recognized by any supported manifest (package.json and 62 others); the IDE-native Run/Build/Test/Clean and the rack are one mechanism.

### Project Templates

Location: `rack/src/main/java/org/nmox/studio/rack/projectstudio/ProjectTemplates.java`

All 15 project templates (React/Vue/Svelte/Solid via Vite, Angular, vanilla, PHP, Classic Web, …) live in Project Studio's wizard — the one New Project surface (File ▸ New Project… and the Welcome tab both open it). The old platform `@TemplateRegistration` wizard (`WebProjectWizardIterator` + resource templates) was unreachable since the v1.11 chrome pass evicted the platform action, and was deleted in v1.246.0.

## Known Issues and Technical Debt

See `docs/engineering/tech-debt.md` for the CURRENT ledger. Key items:

1. **Built-in templates live in code** (ProjectTemplates.java) - deliberate since v1.293.0: their pins carry gate-enforced version ceilings. User templates ARE data-driven now: ~/.nmox/templates.d drop-ins join the wizard (see docs/project-templates.md)
2. **Startup** - window in 1.4-2.7s with ZERO processes spawned at boot (measured v1.38.0, JFR-verified; the module system scanning ~519 cluster jars is ~90% of it - the deliberate feature-set price)
3. **JS/TS use a custom lexer pipeline** while the other languages ride TextMate+CSL; two code paths to maintain
4. ~~.sass shares the SCSS grammar~~ - CLOSED v2.20.0: indented Sass has its own mime + the canonical indented grammar (86 grammars)

## Version History

The per-version history moved to `docs/engineering/status-history.md` (it had grown this file past the size at which it loads). `CHANGELOG.md` is the full record. Tagging vX.Y.Z triggers the release workflow (21 assets: 6 installer/SBOM + updates.xml/.gz + 11 module NBMs for the in-app update center + the GPG-signed SHA256SUMS pair).

**Keep this file small**: each ship REPLACES the status paragraph above with the new release's; the outgoing one goes to the top of `docs/engineering/status-history.md`, never beneath the new one here.

## Documentation

- `docs/user-guide.md` - The user manual: install → first launch → projects → rack → studios → wizards → safety nets. Part of the docs truth pass — keep it accurate every ship
- `docs/making-a-smart-contract.md` - Worked tutorial: a real escrow contract built the Contract Studio way (code, tests, gas gate, live ANVIL loop) — all output real
- `docs/devices.md` - The 53-device reference (GENERATED from DeviceType by DeviceDocsTest; CI fails on drift)
- `docs/engineering/tech-debt.md` - The CURRENT debt ledger: open items with deferral reasons, closed items by version
- `docs/engineering/codebase-guide.md` - A beginner's guide to the codebase: the five RCP ideas everything rides on, four traced flows (boot / open-a-file / press-GO / send-a-request) with real file links, and the house laws with their origins — start here before reading code
- `docs/engineering/plan.md` - The CURRENT plan: where the project stands, honest gaps, ranked opportunities, the working method + house laws + failure patterns — read this first when deciding what to do next
- `CONTRIBUTING.md` - Contribution guidelines, and **The inner loop**: the build/test/boot commands and their traps
- `docs/quickstart.md`, `docs/glossary.md`, `docs/coming-from-vscode.md` - The newcomer's three pages (3.1.0), in all fifteen languages
- `docs/engineering/gates.md` - Every build-failing law test with its law and origin (held complete by GatesIndexGateTest)
- `docs/engineering/dx-plan-3.1.md` - The 3.1 developer-experience plan: what the first-hour, switcher and contributor walks found
- `docs/engineering/dx-plan-3.5.md` - The 3.5 plan: the first Windows and Linux walks, what they found, and what a runner cannot show
- **Every document under `docs/` is current.** The v0.x-era `hack/`
  and `product/` directories and the early `engineering/` papers were
  removed in 3.0.1 — git is the archaeology, and a banner saying "do
  not believe this" is still a document a reader must open to dismiss.

## Troubleshooting

**Build fails at `validate` naming the JDK, or with "invalid target release"**:
- The build needs JDK 25 (the enforcer says so at `validate`): `java -version`
- Set JAVA_HOME to a JDK 25: `export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

**"Cannot run on older versions of Java" error**:
- Use `--jdkhome` flag when launching: `./bin/nmoxstudio --jdkhome /path/to/jdk21`
- Or set in config: edit `application/target/nmoxstudio/etc/nmoxstudio.conf` and set `jdkhome` property
- The launcher requires Java 21+ (the installers bundle their own runtime; only source builds and the portable zip need a host JDK)

**IDE won't start**:
- Check `application/target/nmoxstudio/` exists
- Try rebuilding: `mvn clean package -DskipTests`
- Check logs in `~/Library/Application Support/nmoxstudio/dev/var/log/`
- Verify Java 21+ with: `java -version`

**Java module access errors (InaccessibleObjectException)**:
- Fixed: Java module `--add-opens` flags are now automatically configured
- These are required for NetBeans Platform to work with Java 9+
- Configuration is in `application/src/main/resources/nmoxstudio.conf`

**Tests fail**:
- Build and test with JDK 25 (the root pom's enforcer refuses anything older at `validate`)
- Read the full verdict line; a gate's name says which law it holds - see `docs/engineering/gates.md`

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
