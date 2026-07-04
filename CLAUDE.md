# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NMOX Studio is a NetBeans Platform-based IDE for modern web development, with first-class polyglot support (JS/TS, Java, C/C++, Python, Ruby, Rust, Go, PHP, shell + configs), NPM integration, project templates, and build tools. It's built as a multi-module Maven project with the NetBeans Rich Client Platform (RCP).

**Status**: shipping (v1.28.0). Polyglot editing (46+ TextMate grammars incl. the config layer with Apache configs (.htaccess/httpd.conf/.vhost), LSP providers, Bun/Deno/Rust/Go/BEAM+/PHP toolchains with per-lane monorepo dirs; Navigator outline for 43 mimes), the Reason-style task rack (41 devices incl. the REPL and the ARTISAN Laravel console — see docs/devices.md, generated + CI-gated; undo/redo ⌘Z on every structural edit; cross-lane QUORUM join, readiness ENABLE gates, per-lane REFLEX GLOB routing; quality gates: VITALS Lighthouse floor with perf/a11y/both/best/seo/all GATE, VERITAS coverage floor + named-failure re-runs, GAUNTLET throughput floor, PRISM bundle budget, BEACON cert/uptime sentinel; SOLDER any-command (exports to CI), TAIL log follow + TEMPO clock (both resurrect), HELM ssh runner; presets, CI export, session resurrection, PREFLIGHT ship gate), safe project switching + .env everywhere + Experiments (~/.nmox/experiments lifecycle), Quick Search (⌘I — reaches projects, rack devices, API Studio requests, and infra nodes)/status line/keymap surfaces, the Workbench home base, the Docker control panel, the multi-cloud infra designer (DigitalOcean/Hetzner/Cloudflare — sync live resources from all three, drift Refresh, Destroy-stack with cost framing, cloud-init user_data, copy-ssh-from-node, deploy log), **API Studio** (Postman-style tab with per-response security-header grades), **standards enforced for real** (.editorconfig on save; Standards Kit wizard for robots/sitemap/manifest/security.txt/humans), the **PWA Kit wizard** (Java2D icon forge incl. maskable set, readable service worker, offline page, idempotent index.html wiring), **Learning Spaces** (New Learning Space… — 51 built-in tutorials across languages/frameworks/libraries, each generating sample code + a walked tutorial + a rack pre-wired with a real in-rack REPL you type into), one Options category (General/Rack/Format sub-panels, incl. the startup update-check toggle), installers for all three OSes with bundled runtimes, Homebrew cask, and a tag-triggered release workflow with SBOM.

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
├── infra/                   # Node-RED-style DigitalOcean infrastructure designer
├── apiclient/               # API Studio — Postman-style request/collection/test suite
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
| **editor** | File editing and language support | `JavaScriptLexer` (regex-aware), `JavaScriptDataObject`, `TypeScriptDataObject`, `WebFileSupport` (HTML), 45+ TextMate grammars incl. HTML/CSS/SCSS/Less + the config layer, JS/HTML/CSS completion providers, LSP providers, `OutlineModel`/`StructureNavigatorPanel` (Navigator outline for 43 mimes), `ConfigFileResolver` (dotfile MIME) |
| **tools** | Development tools and integrations | `NpmService`, `WebProjectFactory`, `BuildToolService` |
| **rack** | Reason-style virtual task rack + project lifecycle | `RackTopComponent`, `Rack`/`RackDevice` model, 41 task devices (incl. ROSETTA mixed-repo selector, QUORUM lane-join barrier, IGNITION polyglot runtime, INSPECTOR debug launcher, ARTISAN Laravel console, HARBOR docker, BLACKBOX flight recorder, SONAR port scanner, PREFLIGHT ship check, PHOSPHOR terminal), cross-lane coordination (QUORUM join, `RackDevice.enableGate` readiness gates on long-runners, REFLEX per-lane GLOB routing), patch-cable wiring, `FileWatcher`, `RackIO` persistence, `RackService`, `ProjectStudioTopComponent` (templates, file CRUD, package.json editor, presets) |
| **apiclient** | API Studio (Postman-style) | `ApiClientTopComponent` tab, collections/requests tree, request builder (params/headers/body/auth/tests), response viewer, `{{var}}` environments, `.nmoxapi.json` per-project persistence; `ApiClient`/`TestRunner`/`WorkspaceIO` engine |
| **infra** | DigitalOcean infra designer | `InfraDesignerTopComponent`, `NodeKind` catalog (22 DO offerings), `FlowCanvas`, `DeployPlanner`, `DigitalOceanClient`, cost estimation, `.nmoxinfra.json` persistence |
| **project** | Project management | `ProjectExplorerTopComponent`, `WebProject`, wizards |
| **ui** | Core UI components | `MainWindow`, `WelcomeScreen`, `StartupInitializer`, actions |
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
- **v1.23.0**: the completeness release — every half-shipped idea finished: Experiments manager (promote/discard UI), Hetzner drift/destroy/cloud-init + any-provider live deploys, VITALS gates all four Lighthouse categories, Ship Gate + Dev Intelligence presets, BEAM outline families, wizard validation, window shortcuts (API Studio ⇧⌘8, Infra ⇧⌘9)
- See CHANGELOG.md for the full record; tagging vX.Y.Z triggers the release workflow (6 assets incl. SBOM)

## Documentation

- `docs/devices.md` - The 39-device reference (GENERATED from DeviceType by DeviceDocsTest; CI fails on drift)
- `docs/engineering/tech-debt.md` - The CURRENT debt ledger: open items with deferral reasons, closed items by version
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
