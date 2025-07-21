# NMOX Studio

**Professional Media Development Environment**

[![Build and Test](https://github.com/NMOX/NMOX-Studio/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/NMOX/NMOX-Studio/actions/workflows/build-and-test.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![NetBeans Platform](https://img.shields.io/badge/NetBeans%20Platform-22.0-green.svg)](https://netbeans.apache.org/)

NMOX Studio is a professional, modular media development environment built on the NetBeans Rich Client Platform (RCP). It provides a comprehensive workspace for media professionals with advanced project management, editing capabilities, and extensible tooling.

## Features

### 🏗️ **Professional Architecture**
- **Modular Design**: Clean separation of concerns with dedicated modules for core, UI, editor, project management, and tools
- **Service-Oriented**: Robust service management system with automatic discovery and lifecycle management
- **Extensible**: Plugin-ready architecture supporting custom extensions and integrations

### 🖥️ **Rich User Interface**
- **Modern UI**: Professional workspace with dockable windows and customizable layouts
- **Project Explorer**: Comprehensive project navigation and file management
- **Multi-Window Support**: Advanced window management with persistence
- **Custom Branding**: Professional application branding and theming

### 🛠️ **Development Tools**
- **Integrated Editor**: Advanced text and media editing capabilities
- **Build System**: Professional Maven-based build system with automated testing
- **Deployment**: One-click distribution packages and installers
- **Testing Framework**: Comprehensive test suite with JUnit 5 and AssertJ

## Quick Start

### Prerequisites
- **Java 17+** (JDK required for development)
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
├── ui/                      # User interface components and windows
├── editor/                  # Editor components and file support
├── project/                 # Project management and navigation
├── tools/                   # Development tools and utilities
├── branding/                # Application branding and theming
├── application/             # Main application assembly
├── NMOX-Studio-sample/      # Sample module and resources
├── build.sh                 # Build script
├── run.sh                   # Development run script
└── README.md               # This file
```

### Module Overview

| Module | Description | Key Components |
|--------|-------------|----------------|
| **core** | Core services, service management, and infrastructure | `ServiceManager`, `NMOXStudioCore` |
| **ui** | Main windows, actions, and UI components | `MainWindow`, `NewProjectAction` |
| **editor** | Text and media editing capabilities | Editor support, file types |
| **project** | Project management and file navigation | `ProjectExplorerTopComponent` |
| **tools** | Development tools and utilities | Tool windows, utilities |
| **branding** | Application theming and customization | Splash screen, about dialog, icons |

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

**NMOX Studio** - Empowering media professionals with professional-grade development tools.
