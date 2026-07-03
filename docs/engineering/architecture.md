> **Historical document** (v0.x era). Kept for archaeology; see CLAUDE.md,
> README.md and CHANGELOG.md for current reality.

# System Architecture

## 🏗️ Architecture Overview

NMOX Studio is built on a modular, plugin-based architecture leveraging the NetBeans Rich Client Platform. This provides enterprise-grade stability while enabling rapid feature development.

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │  Editor  │ │ Explorer │ │  Debug   │ │ Terminal │      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
├─────────────────────────────────────────────────────────────┤
│                    Service Layer                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │   LSP    │ │   NPM    │ │  Build   │ │   Git    │      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
├─────────────────────────────────────────────────────────────┤
│                    Core Platform                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │ FileSystem│ │  Lookup  │ │  Nodes   │ │ Windows  │      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
├─────────────────────────────────────────────────────────────┤
│                 NetBeans Platform 22.0                       │
└─────────────────────────────────────────────────────────────┘
```

## 🧩 Module Architecture

### Core Modules

#### 1. Editor Module (`org.nmox.studio.editor`)
**Responsibility:** Code editing, syntax highlighting, completion

**Key Components:**
- `WebFileSupport` - Unified web file handling
- `CompletionProviders` - IntelliSense implementation
- `SyntaxHighlighters` - Language-specific highlighting
- `CodeFormatters` - Formatting engines

**Dependencies:**
- NetBeans Editor API
- NetBeans Lexer API
- Language Server Protocol client

#### 2. Tools Module (`org.nmox.studio.tools`)
**Responsibility:** Build tools, package managers

**Key Components:**
- `NpmService` - NPM/Yarn/PNPM integration
- `BuildToolService` - Webpack/Vite/Parcel

Build, test, and debug *execution surfaces* live in the rack module
(FORGE, VERITAS, INSPECTOR devices); tools keeps the services the
Build menu uses.

**Dependencies:**
- Process management
- JSON parsing

#### 3. Platform Module (`org.nmox.studio.platform`)
**Responsibility:** Platform services and utilities

**Key Components:**
- `WorkspaceManager` - Project management
- `ConfigurationService` - Settings management
- `TelemetryService` - Analytics
- `UpdateService` - Auto-updates

### Module Communication

```java
// Service registration via NetBeans Lookup
@ServiceProvider(service = BuildToolService.class)
public class DefaultBuildToolService implements BuildToolService {
    // Implementation
}

// Service consumption
BuildToolService buildService = Lookup.getDefault().lookup(BuildToolService.class);
```

## 🔌 Integration Architecture

### Language Server Protocol (LSP)

```
IDE <---> LSP Client <---> Language Servers
                            ├── TypeScript LS
                            ├── CSS LS
                            ├── HTML LS
                            └── JSON LS
```

**Implementation:**
```java
public class TypeScriptLanguageClient {
    private final ProcessHandle serverProcess;
    private final JsonRpcClient rpcClient;
    
    public CompletableFuture<CompletionList> getCompletions(Position position) {
        return rpcClient.request("textDocument/completion", 
            new CompletionParams(document, position));
    }
}
```

## 🏛️ Design Patterns

### 1. Service Locator Pattern
Used for loose coupling between modules via NetBeans Lookup.

```java
// Service interface
public interface CodeFormatter {
    String format(String code, String language);
}

// Service provider
@ServiceProvider(service = CodeFormatter.class)
public class PrettierFormatter implements CodeFormatter {
    // Implementation
}
```

### 2. Observer Pattern
For reactive updates across the IDE.

```java
public class ProjectWatcher {
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    public void fileChanged(File file) {
        pcs.firePropertyChange("file.changed", null, file);
    }
}
```

### 3. Command Pattern
For user actions and undo/redo support.

```java
@ActionID(category = "Build", id = "org.nmox.studio.build.action")
@ActionRegistration(displayName = "Build Project")
public class BuildAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
        BuildToolService.getInstance().build(project);
    }
}
```

### 4. Strategy Pattern
For pluggable implementations.

```java
public interface BuildStrategy {
    CompletableFuture<BuildResult> execute(BuildConfiguration config);
}

public class WebpackStrategy implements BuildStrategy { }
public class ViteStrategy implements BuildStrategy { }
```

## 🔐 Security Architecture

### Threat Model

| Threat | Mitigation |
|--------|------------|
| Code injection | Sandboxed execution, input validation |
| Supply chain attacks | Dependency scanning, signed plugins |
| Data exfiltration | Local-first, encrypted storage |
| Privilege escalation | Minimal permissions, sandbox |

### Security Layers

1. **Process Isolation**
   - Build tools run in separate processes
   - Limited IPC communication
   - Resource quotas enforced

2. **Plugin Sandboxing**
   - ClassLoader isolation
   - Permission boundaries
   - API access control

3. **Data Protection**
   - Credentials in system keychain
   - Encrypted workspace settings
   - No telemetry without consent

## 🚀 Performance Architecture

### Memory Management

```java
public class EditorBufferPool {
    private final SoftReference<ByteBuffer>[] pool;
    private final Semaphore available;
    
    public ByteBuffer acquire() {
        // Efficient buffer reuse
    }
}
```

### Lazy Loading

```java
@ServiceProvider(service = LanguageSupport.class)
public class TypeScriptSupport implements LanguageSupport {
    private volatile TypeScriptService service;
    
    private TypeScriptService getService() {
        if (service == null) {
            synchronized (this) {
                if (service == null) {
                    service = new TypeScriptService();
                }
            }
        }
        return service;
    }
}
```

### Caching Strategy

| Cache | Type | TTL | Max Size |
|-------|------|-----|----------|
| File metadata | LRU | 5 min | 10,000 |
| Parse trees | Soft ref | Until GC | Unlimited |
| Completion cache | LRU | 1 min | 1,000 |
| Build artifacts | Disk | 7 days | 1 GB |

## 🔄 Data Flow Architecture

### Project Open Flow

```
User Action → Project Open
    ↓
Workspace Manager → Load .nmox/workspace.json
    ↓
File System → Index project files
    ↓
Language Servers → Start appropriate servers
    ↓
Build Tools → Detect and configure
    ↓
UI Update → Show project tree
```

### Code Completion Flow

```
Keystroke → Editor
    ↓
Completion Provider → Context Analysis
    ↓
Language Server → Request completions
    ↓
Cache Check → Return cached if fresh
    ↓
Filter & Rank → Apply user preferences
    ↓
UI Update → Show completion popup
```

## 🌐 Plugin Architecture

### Plugin Structure

```
plugin/
├── META-INF/
│   └── MANIFEST.MF
├── plugin.xml
├── lib/
│   └── plugin.jar
└── resources/
    └── icons/
```

### Plugin API

```java
public abstract class NMOXPlugin {
    protected final PluginContext context;
    
    public abstract void initialize();
    public abstract void activate();
    public abstract void deactivate();
    
    protected final <T> T getService(Class<T> serviceClass) {
        return context.getService(serviceClass);
    }
}
```

### Extension Points

| Extension Point | Purpose | Example |
|----------------|---------|---------|
| `editor.language` | Add language support | Rust support |
| `build.tool` | Add build tool | Gradle integration |
| `debugger.protocol` | Add debug protocol | DAP support |
| `ui.theme` | Add UI theme | Material theme |

## 📊 Scalability Architecture

### Horizontal Scaling (Cloud Workspaces)

```
Load Balancer
    ├── Workspace Server 1 (1-100 users)
    ├── Workspace Server 2 (101-200 users)
    └── Workspace Server N
         ├── Container per user
         └── Shared resources
```

### Vertical Scaling (Large Projects)

- **Incremental parsing** - Parse only changed files
- **Virtual file system** - Load files on demand
- **Worker threads** - Parallel processing
- **Off-heap storage** - Memory-mapped files

## 🔧 Technology Decisions

### Why NetBeans Platform?

| Aspect | Benefit |
|--------|---------|
| Maturity | 20+ years of development |
| Modularity | Clean plugin architecture |
| Performance | Optimized for large codebases |
| Features | Rich API ecosystem |
| Stability | Enterprise-proven |

### Why Java 17?

- LTS release with long-term support
- Modern language features (records, patterns)
- Performance improvements (GC, JIT)
- Strong ecosystem
- Cross-platform native packaging

### Why Chrome DevTools Protocol?

- Industry standard for debugging
- Rich feature set
- Active development
- Wide browser support
- Extensible protocol

## 🏗️ Build Architecture

### Multi-Module Maven Structure

```
nmox-studio/
├── pom.xml (parent)
├── platform/
│   └── pom.xml
├── editor/
│   └── pom.xml
├── tools/
│   └── pom.xml
└── application/
    └── pom.xml
```

### Build Pipeline

```
Source → Compile → Test → Package → Sign → Distribute
         ↓         ↓      ↓        ↓      ↓
       javac    JUnit   NBM    Codesign  GitHub
                        ↓                  ↓
                     Installer          Releases
```

## 📈 Monitoring Architecture

### Metrics Collection

```java
@Component
public class MetricsCollector {
    private final MeterRegistry registry;
    
    @EventListener
    public void onEditorAction(EditorEvent event) {
        registry.counter("editor.actions", 
            "type", event.getType()).increment();
    }
}
```

### Telemetry Pipeline

```
IDE → Local Buffer → Batch Upload → Analytics Server
         ↓                               ↓
    Privacy Filter                  Aggregation
         ↓                               ↓
    User Consent                    Dashboard
```

---

**Last Updated:** January 2025  
**Architecture Review:** Monthly  
**RFC Process:** architecture@nmox.studio