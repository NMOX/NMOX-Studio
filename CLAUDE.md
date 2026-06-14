# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NMOX Studio is a NetBeans Platform-based IDE for modern web development, with first-class polyglot support (JS/TS, Java, C/C++, Python, Ruby, Rust, Go, PHP, shell + configs), NPM integration, project templates, and build tools. It's built as a multi-module Maven project with the NetBeans Rich Client Platform (RCP).

**Status**: shipping (v1.3.0 released, v1.4.0 in flight). Polyglot editing (32 TextMate grammars incl. HTML/CSS/SCSS/Less activated through CSL, LSP providers for 35 MIMEs), the Reason-style task rack (31 devices, stderr monitor bus, presets, CI export, session resurrection, PREFLIGHT ship gate), the Workbench home base (open/recent files, projects, tooling shelf), the Docker control panel, the multi-cloud infra designer (DigitalOcean/Hetzner/Cloudflare), settings UI, installers for all three OSes, and a tag-triggered release workflow.

## Build and Run Commands

### Prerequisites
- **Java 17+** (required; project uses Java 17, tested with Java 23)
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
- `./test-everything.sh` - Comprehensive test suite
- `./quick-test.sh` - Quick validation
- `./gui-test.sh` - GUI-specific tests

## Module Structure

The project is organized as a multi-module Maven build using NetBeans Platform:

```
NMOX-Studio/
├── core/                    # Cross-cutting platform touches (Terminal phosphor theme)
├── editor/                  # File type support, JavaScript lexer, syntax highlighting, completion
├── tools/                   # NPM integration, build tools
├── rack/                    # Reason-style task rack: drag-drop device wiring, control surfaces
├── infra/                   # Node-RED-style DigitalOcean infrastructure designer
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
| **editor** | File editing and language support | `JavaScriptLexer` (regex-aware), `JavaScriptDataObject`, `TypeScriptDataObject`, `WebFileSupport` (HTML), 45+ TextMate grammars incl. HTML/CSS/SCSS/Less + the config layer, JS/HTML/CSS completion providers, LSP providers, `OutlineModel`/`StructureNavigatorPanel` (Navigator outline for 32 mimes), `ConfigFileResolver` (dotfile MIME) |
| **tools** | Development tools and integrations | `NpmService`, `WebProjectFactory`, `BuildToolService` |
| **rack** | Reason-style virtual task rack + project lifecycle | `RackTopComponent`, `Rack`/`RackDevice` model, 31 task devices (incl. ROSETTA mixed-repo selector, IGNITION polyglot runtime, INSPECTOR debug launcher, HARBOR docker, BLACKBOX flight recorder, SONAR port scanner, PREFLIGHT ship check, PHOSPHOR terminal), patch-cable wiring, `FileWatcher`, `RackIO` persistence, `RackService`, `ProjectStudioTopComponent` (templates, file CRUD, package.json editor, presets) |
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
- **WebProject**: Represents NPM projects
- **WebProjectFactory**: Creates projects from templates
- **NpmExplorerTopComponent**: UI for browsing and running NPM scripts

Projects are recognized by presence of `package.json`.

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
- See CHANGELOG.md for the full record; tagging vX.Y.Z triggers the release workflow (5 artifacts)

## Documentation

- `docs/hack/implementation-complete.md` - What actually shipped in v0.1
- `docs/engineering/actual-implementation.md` - Real architecture
- `docs/engineering/next-iteration.md` - v0.2 plans
- `docs/hack/technical-debt.md` - Known issues and shortcuts
- `CONTRIBUTING.md` - Contribution guidelines

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
