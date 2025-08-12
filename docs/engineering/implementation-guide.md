# Implementation Guide

## 🎯 Overview

This guide provides detailed technical specifications for implementing NMOX Studio features. Each specification includes architecture, APIs, data models, and implementation steps.

## 📝 Core Editor Implementation

### Syntax Highlighting Engine

#### Architecture
```java
public interface SyntaxHighlighter {
    TokenStream tokenize(String content, Language language);
    StyleMap getStyles(TokenType type);
    void invalidateCache(DocumentRegion region);
}
```

#### Implementation Steps
1. **Lexer Integration**
   ```java
   @ServiceProvider(service = LanguageTokenizer.class)
   public class JavaScriptTokenizer implements LanguageTokenizer {
       private final Lexer lexer = new JavaScriptLexer();
       
       @Override
       public TokenStream tokenize(CharSequence text) {
           lexer.reset(text, 0, text.length(), INITIAL_STATE);
           return new LexerTokenStream(lexer);
       }
   }
   ```

2. **Incremental Parsing**
   ```java
   public class IncrementalParser {
       private final RangeTree<Token> tokenTree = new RangeTree<>();
       
       public void updateRange(int start, int end, String newText) {
           // Remove affected tokens
           tokenTree.removeRange(start, end);
           
           // Re-tokenize changed region
           TokenStream tokens = tokenizer.tokenize(newText);
           tokenTree.insertAll(tokens, start);
           
           // Adjust subsequent token positions
           tokenTree.shift(start + newText.length(), 
                          newText.length() - (end - start));
       }
   }
   ```

3. **Style Mapping**
   ```java
   public enum TokenStyle {
       KEYWORD("keyword", "#569CD6", Font.BOLD),
       STRING("string", "#CE9178", Font.PLAIN),
       COMMENT("comment", "#6A9955", Font.ITALIC),
       IDENTIFIER("identifier", "#9CDCFE", Font.PLAIN);
       
       private final String cssClass;
       private final Color color;
       private final int fontStyle;
   }
   ```

### Code Completion System

#### Data Model
```java
public class CompletionItem {
    private String label;
    private CompletionItemKind kind;
    private String detail;
    private String documentation;
    private String insertText;
    private TextEdit[] additionalTextEdits;
    private CompletionItemResolveData data;
}

public class CompletionContext {
    private Document document;
    private Position position;
    private String triggerCharacter;
    private CompletionTriggerKind triggerKind;
    private String prefix;
    private ASTNode currentNode;
}
```

#### Completion Provider Chain
```java
public class CompletionProviderChain {
    private final List<CompletionProvider> providers = Arrays.asList(
        new KeywordCompletionProvider(),
        new VariableCompletionProvider(),
        new TypeCompletionProvider(),
        new SnippetCompletionProvider(),
        new AICompletionProvider()
    );
    
    public CompletableFuture<List<CompletionItem>> getCompletions(
            CompletionContext context) {
        return providers.parallelStream()
            .map(p -> p.getCompletions(context))
            .reduce(CompletableFuture.completedFuture(new ArrayList<>()),
                (f1, f2) -> f1.thenCombine(f2, this::merge));
    }
}
```

#### Ranking Algorithm
```java
public class CompletionRanker {
    private static final double WEIGHT_RELEVANCE = 0.4;
    private static final double WEIGHT_FREQUENCY = 0.3;
    private static final double WEIGHT_DISTANCE = 0.2;
    private static final double WEIGHT_TYPE = 0.1;
    
    public List<CompletionItem> rank(List<CompletionItem> items, 
                                    CompletionContext context) {
        return items.stream()
            .map(item -> new ScoredItem(item, score(item, context)))
            .sorted(Comparator.comparing(ScoredItem::score).reversed())
            .map(ScoredItem::item)
            .collect(Collectors.toList());
    }
    
    private double score(CompletionItem item, CompletionContext context) {
        return WEIGHT_RELEVANCE * relevanceScore(item, context) +
               WEIGHT_FREQUENCY * frequencyScore(item) +
               WEIGHT_DISTANCE * distanceScore(item, context) +
               WEIGHT_TYPE * typeScore(item, context);
    }
}
```

## 🛠️ Build System Integration

### Build Tool Detection

```java
public class BuildToolDetector {
    private final List<BuildToolMatcher> matchers = Arrays.asList(
        new FileMatcher("webpack.config.js", BuildToolType.WEBPACK),
        new FileMatcher("vite.config.[jt]s", BuildToolType.VITE),
        new FileMatcher(".parcelrc", BuildToolType.PARCEL),
        new PackageJsonMatcher("webpack", BuildToolType.WEBPACK),
        new PackageJsonMatcher("vite", BuildToolType.VITE)
    );
    
    public BuildToolType detect(File projectDir) {
        return matchers.stream()
            .map(m -> m.match(projectDir))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst()
            .orElse(BuildToolType.UNKNOWN);
    }
}
```

### Build Execution Pipeline

```java
public class BuildPipeline {
    private final ExecutorService executor = ForkJoinPool.commonPool();
    private final Map<BuildToolType, BuildExecutor> executors;
    
    public CompletableFuture<BuildResult> build(BuildRequest request) {
        return CompletableFuture
            .supplyAsync(() -> validate(request), executor)
            .thenCompose(this::prepare)
            .thenCompose(this::execute)
            .thenCompose(this::postProcess)
            .exceptionally(this::handleError);
    }
    
    private CompletableFuture<BuildContext> prepare(BuildRequest request) {
        return CompletableFuture.allOf(
            installDependencies(request),
            cleanBuildDir(request),
            generateConfig(request)
        ).thenApply(v -> new BuildContext(request));
    }
}
```

### Build Configuration Management

```java
@Entity
public class BuildConfiguration {
    @Id
    private String id;
    
    @Enumerated(EnumType.STRING)
    private BuildMode mode;
    
    private boolean minify;
    private boolean sourceMaps;
    private String outputDir;
    private Map<String, String> environmentVariables;
    private List<String> includePaths;
    private List<String> excludePatterns;
    
    @PrePersist
    public void applyDefaults() {
        if (mode == BuildMode.PRODUCTION) {
            minify = true;
            sourceMaps = false;
        } else {
            minify = false;
            sourceMaps = true;
        }
    }
}
```

## 🐛 Debugging Implementation

### Chrome DevTools Protocol Client

```java
public class CDPClient {
    private final WebSocketClient wsClient;
    private final Map<Integer, CompletableFuture<JsonNode>> pending;
    private final AtomicInteger messageId = new AtomicInteger();
    
    public CompletableFuture<Void> setBreakpoint(String url, int line) {
        int id = messageId.incrementAndGet();
        JsonNode params = Json.object()
            .put("url", url)
            .put("lineNumber", line);
            
        JsonNode message = Json.object()
            .put("id", id)
            .put("method", "Debugger.setBreakpointByUrl")
            .put("params", params);
            
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        
        wsClient.send(message.toString());
        return future.thenApply(response -> null);
    }
    
    @OnWebSocketMessage
    public void onMessage(String message) {
        JsonNode json = Json.parse(message);
        
        if (json.has("id")) {
            // Response to our request
            int id = json.get("id").asInt();
            CompletableFuture<JsonNode> future = pending.remove(id);
            if (future != null) {
                if (json.has("error")) {
                    future.completeExceptionally(
                        new CDPException(json.get("error")));
                } else {
                    future.complete(json.get("result"));
                }
            }
        } else if (json.has("method")) {
            // Event from debugger
            handleEvent(json.get("method").asText(), 
                       json.get("params"));
        }
    }
}
```

### Breakpoint Management

```java
public class BreakpointManager {
    private final Map<String, Set<Breakpoint>> fileBreakpoints;
    private final CDPClient cdpClient;
    
    public class Breakpoint {
        private final String id;
        private final String file;
        private final int line;
        private String condition;
        private boolean enabled;
        private int hitCount;
        
        public CompletableFuture<Void> toggle() {
            enabled = !enabled;
            if (enabled) {
                return cdpClient.setBreakpoint(file, line)
                    .thenAccept(id -> this.id = id);
            } else {
                return cdpClient.removeBreakpoint(id);
            }
        }
    }
    
    public void addBreakpoint(String file, int line) {
        Breakpoint bp = new Breakpoint(file, line);
        fileBreakpoints.computeIfAbsent(file, k -> new HashSet<>())
                      .add(bp);
        bp.toggle();
    }
}
```

## 🧪 Testing Framework Integration

### Test Runner Abstraction

```java
public interface TestRunner {
    TestConfiguration detectConfiguration(File projectDir);
    CompletableFuture<TestResult> runTests(TestRequest request);
    CompletableFuture<Coverage> getCoverage(File projectDir);
    Stream<TestFile> discoverTests(File projectDir);
}

public class JestRunner implements TestRunner {
    @Override
    public CompletableFuture<TestResult> runTests(TestRequest request) {
        ProcessBuilder pb = new ProcessBuilder(
            "npx", "jest",
            "--json",
            "--outputFile=" + tempFile,
            request.getTestPattern()
        );
        
        return ProcessExecutor.execute(pb)
            .thenApply(this::parseResults);
    }
    
    private TestResult parseResults(String jsonOutput) {
        JsonNode json = Json.parse(jsonOutput);
        return TestResult.builder()
            .passed(json.get("numPassedTests").asInt())
            .failed(json.get("numFailedTests").asInt())
            .skipped(json.get("numPendingTests").asInt())
            .duration(json.get("testResults").get(0)
                         .get("perfStats").get("runtime").asLong())
            .testCases(parseTestCases(json))
            .build();
    }
}
```

### Test Discovery

```java
public class TestDiscovery {
    private static final List<Pattern> TEST_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\.test\\.[jt]sx?$"),
        Pattern.compile(".*\\.spec\\.[jt]sx?$"),
        Pattern.compile(".*/test/.*\\.[jt]sx?$"),
        Pattern.compile(".*/__tests__/.*\\.[jt]sx?$")
    );
    
    public Stream<TestFile> discover(Path root) {
        try {
            return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(this::isTestFile)
                .map(this::parseTestFile);
        } catch (IOException e) {
            return Stream.empty();
        }
    }
    
    private TestFile parseTestFile(Path path) {
        String content = Files.readString(path);
        List<TestCase> tests = extractTests(content);
        return new TestFile(path, tests);
    }
    
    private List<TestCase> extractTests(String content) {
        // Parse describe() and it() blocks
        Pattern pattern = Pattern.compile(
            "(describe|it|test)\\s*\\(['\"`]([^'\"]+)['\"`]"
        );
        
        return pattern.matcher(content).results()
            .map(mr -> new TestCase(mr.group(2), mr.group(1)))
            .collect(Collectors.toList());
    }
}
```

## 🔌 Plugin System Implementation

### Plugin Lifecycle

```java
public abstract class Plugin {
    private PluginState state = PluginState.INSTALLED;
    
    protected final void transitionTo(PluginState newState) {
        if (!state.canTransitionTo(newState)) {
            throw new IllegalStateException(
                "Cannot transition from " + state + " to " + newState);
        }
        
        PluginState oldState = state;
        state = newState;
        
        notifyListeners(oldState, newState);
    }
    
    public final void activate() {
        transitionTo(PluginState.ACTIVATING);
        try {
            doActivate();
            transitionTo(PluginState.ACTIVE);
        } catch (Exception e) {
            transitionTo(PluginState.FAILED);
            throw new PluginException("Activation failed", e);
        }
    }
    
    protected abstract void doActivate();
    protected abstract void doDeactivate();
}
```

### Plugin Sandboxing

```java
public class PluginSandbox {
    private final ClassLoader sandboxLoader;
    private final SecurityManager securityManager;
    private final ResourceQuota quota;
    
    public <T> T executeInSandbox(Callable<T> task) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        SecurityManager originalSM = System.getSecurityManager();
        
        try {
            thread.setContextClassLoader(sandboxLoader);
            System.setSecurityManager(securityManager);
            
            return quota.execute(task);
        } finally {
            thread.setContextClassLoader(originalLoader);
            System.setSecurityManager(originalSM);
        }
    }
}

public class ResourceQuota {
    private final long maxMemory;
    private final long maxCpu;
    private final int maxThreads;
    
    public <T> T execute(Callable<T> task) throws Exception {
        try (ResourceMonitor monitor = new ResourceMonitor(this)) {
            return task.call();
        }
    }
}
```

### Plugin API

```java
public interface PluginAPI {
    // Editor API
    Editor getActiveEditor();
    void openFile(File file);
    void saveFile(File file);
    
    // UI API
    void showNotification(String message, NotificationType type);
    ToolWindow createToolWindow(String id, Component content);
    MenuItem addMenuItem(String path, Action action);
    
    // Service API
    <T> T getService(Class<T> serviceClass);
    void registerService(Class<?> serviceClass, Object implementation);
    
    // Event API
    void addEventListener(String event, EventListener listener);
    void removeEventListener(String event, EventListener listener);
    void fireEvent(String event, Object data);
}
```

## 🌐 Language Server Protocol Implementation

### LSP Client

```java
public class LSPClient {
    private final Process serverProcess;
    private final JsonRpcClient rpc;
    private final MessageHandler handler;
    
    public CompletableFuture<InitializeResult> initialize(
            InitializeParams params) {
        return rpc.request("initialize", params, InitializeResult.class);
    }
    
    public CompletableFuture<List<CompletionItem>> completion(
            TextDocumentPositionParams params) {
        return rpc.request("textDocument/completion", params)
            .thenApply(result -> result.get("items"))
            .thenApply(this::parseCompletionItems);
    }
    
    public void didOpen(DidOpenTextDocumentParams params) {
        rpc.notification("textDocument/didOpen", params);
    }
}
```

### Document Synchronization

```java
public class DocumentSynchronizer {
    private final Map<String, DocumentState> documents;
    private final LSPClient client;
    
    public void syncDocument(Document doc) {
        String uri = doc.getUri();
        DocumentState state = documents.get(uri);
        
        if (state == null) {
            // First time opening
            client.didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem(uri, "javascript", 0, 
                                    doc.getText())
            ));
            documents.put(uri, new DocumentState(doc));
        } else {
            // Incremental sync
            List<TextEdit> edits = state.computeEdits(doc);
            client.didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, 
                                                   state.version + 1),
                edits
            ));
            state.update(doc);
        }
    }
}
```

## 📊 Performance Optimization Techniques

### Memory Pool Management

```java
public class MemoryPool<T> {
    private final Queue<T> pool = new ConcurrentLinkedQueue<>();
    private final Supplier<T> factory;
    private final Consumer<T> reset;
    private final int maxSize;
    
    public T acquire() {
        T instance = pool.poll();
        if (instance == null) {
            instance = factory.get();
        }
        return instance;
    }
    
    public void release(T instance) {
        if (pool.size() < maxSize) {
            reset.accept(instance);
            pool.offer(instance);
        }
    }
}
```

### Virtual File System

```java
public class VirtualFileSystem {
    private final Cache<Path, VirtualFile> cache;
    private final FileWatcher watcher;
    
    public class VirtualFile {
        private final Path path;
        private SoftReference<String> content;
        private long lastModified;
        private long size;
        
        public String getContent() {
            String text = content != null ? content.get() : null;
            if (text == null) {
                text = loadFromDisk();
                content = new SoftReference<>(text);
            }
            return text;
        }
        
        private String loadFromDisk() {
            // Load only when needed
            return Files.readString(path);
        }
    }
}
```

---

**Last Updated:** January 2025  
**Implementation Team:** dev@nmox.studio  
**Code Reviews:** Required for all implementations