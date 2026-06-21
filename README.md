# NMOX Studio

**The web studio with a rack — wire your tools like a synth.**

[![Build and Test](https://github.com/NMOX/NMOX-Studio/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/NMOX/NMOX-Studio/actions/workflows/build-and-test.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![NetBeans Platform](https://img.shields.io/badge/NetBeans%20Platform-30.0-green.svg)](https://netbeans.apache.org/)

![NMOX Studio — the Task Rack](docs/images/task-rack.png)

NMOX Studio is an IDE for web development with a twist: your tooling lives in a Reason-style **Task Rack**. Every task — install, build, test, serve, lint, deploy — is a hardware-styled device with knobs, LEDs, and patch cables; wire OK jacks together and one keypress runs your whole pipeline, with errors landing on a phosphor monitor bus. Around the rack: a polyglot editor (45+ languages via TextMate grammars, NetBeans CSL, and LSP — code and the whole config layer, down to `.editorconfig` and `.env`), a Workbench home base, a Node-RED-style multi-cloud infra designer (DigitalOcean, Hetzner, Cloudflare), and project templates. Built on the NetBeans Rich Client Platform; the core developer loop is proven against real `node`/`npm` in CI on every commit.

## Download

Grab **[the latest release](https://github.com/NMOX/NMOX-Studio/releases/latest)** — DMG (macOS), installer (Windows), tar.gz/deb (Linux), or portable zip. The DMG, installer, tar.gz and deb ship with their own Java runtime — nothing to install. (The portable zip alone expects a Java 21+ on the machine.)

> **macOS note:** the app is not yet notarized. If Gatekeeper objects, right-click the app and choose *Open*, or run `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`.

### Homebrew (macOS)

```bash
brew tap NMOX/NMOX-Studio https://github.com/NMOX/NMOX-Studio
brew trust --cask nmox/nmox-studio/nmox-studio
brew install --cask nmox-studio
```

The `brew trust` step is a one-time acknowledgment Homebrew requires for any third-party tap; you won't be asked again for future updates. The cask wraps the release DMG (bundled Java runtime, no separate install) and Homebrew clears the quarantine attribute on install, so you skip the Gatekeeper prompt without any code signing.

Update later with `brew update && brew upgrade --cask nmox-studio`; remove cleanly with `brew uninstall --cask --zap nmox-studio`.

## Screenshots

*The web studio with a rack — wire your tools like a synth.*

| | |
|---|---|
| ![Welcome screen](docs/images/welcome.png) | ![Rack rear — patch cables](docs/images/rack-rear.png) |
| *Welcome screen* | *Flip the rack (Tab) and patch task pipelines by cable* |
| ![Editor](docs/images/editor.png) | ![Workbench and Infra Designer](docs/images/workbench-infra.png) |
| *Phosphor-on-dark editing, 45+ languages* | *Workbench home base beside the multi-cloud Infra Designer* |

## Features

### 🎛️ The Task Rack
Every web-dev task is a hardware device — knobs, LEDs, LCDs, patch cables.
Wire OK jacks together (Tab flips the rack) and one keypress runs install →
build → test, with output scrolling on a phosphor monitor. 32 devices:
package managers, bundlers, test runners, dev servers, databases, linters,
formatters, git, deploy, HTTP, tunnels, load bench, file watcher, and more.
Patches persist per project, ship as presets, and export to GitHub Actions.

### 🧠 The rack remembers, sees, and survives
- **BLACKBOX** records every launch, exit, duration, and error on a session
  timeline that persists across restarts — with a slow-creep alarm that
  notices when your build quietly doubles.
- **SONAR** maps every listening port to its owning process (docker
  containers labeled) with one-click kill. EADDRINUSE, solved.
- **Session Resurrection**: crash, `kill -9`, or power loss — relaunch and
  the IDE offers your running dev servers back. One click and they're alive.

### 🐳 First-class Docker
The HARBOR device tracks the daemon (containers up, images held, disk
reclaimable) and opens the Docker Panel: a disk-reclaim ledger, live
container management with browser-jump ports, image tooling, volumes,
networks — and **Dockerize**, which generates production multi-stage
Dockerfiles from your project's detected toolchain.

### ⌨️ Polyglot editing
45+ languages with syntax highlighting (TextMate grammars through NetBeans
CSL) — code plus the whole config layer: `.editorconfig`, dotenv, ignore
files, GraphQL, Vue, Svelte, Astro, Pug, Handlebars, Liquid, nginx,
Makefile, Protocol Buffers, Prisma, YAML, TOML, Dockerfile. First-class
HTML, CSS, SCSS and Less with tag, attribute, value and property
completion; LSP with ordered server fallbacks; a regex-aware JavaScript
lexer; typing intelligence; comment-only spellcheck (your keys and values
are never flagged as typos); a **Structure navigator** (⌘7) that outlines
any file — classes, functions, tests, selectors, headings, config keys —
and jumps to a symbol on click; and the NMOX Phosphor dark theme.

### 🏗️ Projects and infrastructure
The Workbench home base (toolchain chips, open/recent files, tooling
shelf), Project Studio templates that scaffold versioned, rack-wired
projects, and a Node-RED-style Infra Designer for DigitalOcean, Hetzner,
and Cloudflare with cost estimates and dry-run planning.

### ✅ Proven, not promised
CI runs real `npm install`/build/test/serve through the actual rack devices
on every commit. Quitting the IDE reaps every child process — no orphaned
dev servers, guaranteed and tested.

See the [CHANGELOG](CHANGELOG.md) for the full release history.

## Quick Start

### Prerequisites
- **Java 21+** (JDK required for development)
- **Maven 3.6+**
- **Git** (for source code management)

### Building from Source

```bash
# Clone the repository
git clone https://github.com/NMOX/NMOX-Studio.git
cd NMOX-Studio

# Build the application
./build.sh

# Run the application
./run.sh
```

### Development Build

```bash
# Clean build with tests
mvn clean test package

# Create distribution packages
mvn package -Pdeployment

# Run in development mode
mvn nbm:run-platform
```

## Project Structure

```
NMOX-Studio/
├── core/                    # Core services and infrastructure
├── cloud/                   # Cloud provider abstraction layer
│   ├── api/                # Provider interfaces
│   ├── providers/          # AWS, Azure, GCP implementations
│   └── services/           # Cloud service management
├── deployment/             # Deployment management
│   ├── ui/                # Deployment UI components
│   ├── services/          # Deployment orchestration
│   └── model/             # Deployment models
├── containers/             # Container and orchestration
│   ├── docker/            # Docker integration
│   ├── kubernetes/        # Kubernetes client
│   └── ui/               # Container management UI
├── ui/                     # Core UI components
├── project/               # Project management
├── tools/                 # Development tools
├── branding/              # Application branding
├── application/           # Main application assembly
├── build.sh              # Build script
├── run.sh               # Development run script
└── README.md           # This file
```

### Module Overview

| Module | Description | Key Components |
|--------|-------------|----------------|
| **core** | Core services and infrastructure | `ServiceManager`, `NMOXStudioCore` |
| **cloud** | Cloud provider abstraction and management | `CloudProvider`, `CloudInstance`, `CloudMetrics` |
| **deployment** | Application deployment management | `DeploymentManager`, `DeploymentService` |
| **containers** | Docker and Kubernetes integration | `DockerService`, `KubernetesService` |
| **ui** | Main windows and UI components | `MainWindow`, Actions |
| **project** | Project and resource management | `ProjectExplorerTopComponent` |
| **tools** | Development and debugging tools | Tool windows, utilities |
| **branding** | Application theming | Splash screen, icons |

## Architecture

NMOX Studio follows a clean, modular architecture based on the NetBeans Platform:

### Service Management
The application uses a centralized service management system that provides:
- **Automatic Discovery**: Services are automatically discovered through the Lookup system
- **Lifecycle Management**: Proper initialization and cleanup of services
- **Event Notification**: Service registration/unregistration events
- **Type Safety**: Strongly-typed service retrieval

### Module System
Each module is a self-contained NetBeans module (NBM) with:
- **Clear Dependencies**: Explicit module dependencies
- **API Separation**: Clean separation between API and implementation
- **Resource Management**: Proper resource bundling and internationalization
- **Testing Support**: Comprehensive unit and integration tests

## Development

### Building and Testing

```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl core

# Build without tests
mvn package -DskipTests

# Generate test reports
mvn surefire-report:report
```

### Adding New Modules

1. Create module directory structure
2. Add module POM with proper dependencies
3. Register module in parent POM
4. Implement module functionality
5. Add comprehensive tests
6. Update documentation

### Code Quality

The project maintains high code quality through:
- **Static Analysis**: Compiler warnings and linting
- **Unit Testing**: Comprehensive test coverage with JUnit 5
- **Integration Testing**: NetBeans platform integration tests
- **Code Review**: Pull request review process
- **Documentation**: Comprehensive inline and external documentation

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on:
- Code of conduct
- Development workflow
- Pull request process
- Coding standards

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/NMOX/NMOX-Studio/issues)
- **Discussions**: [GitHub Discussions](https://github.com/NMOX/NMOX-Studio/discussions)
- **Documentation**: [Wiki](https://github.com/NMOX/NMOX-Studio/wiki)

---

**NMOX Studio** - wire your web tooling like a synth.
