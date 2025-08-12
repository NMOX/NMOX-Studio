# API Design Documentation

## 🎯 API Design Principles

1. **Consistency** - Uniform patterns across all APIs
2. **Simplicity** - Intuitive and easy to use
3. **Extensibility** - Forward-compatible design
4. **Performance** - Optimized for common use cases
5. **Documentation** - Self-documenting with examples

## 🔌 Plugin API

### Core API Structure

```java
/**
 * Base interface for all NMOX Studio plugins.
 * @since 1.0.0
 */
public interface Plugin {
    
    /**
     * Plugin metadata
     */
    interface Metadata {
        String getId();
        String getName();
        String getVersion();
        String getDescription();
        String getAuthor();
        String getLicense();
        String[] getDependencies();
        String getMinimumVersion();
    }
    
    /**
     * Lifecycle methods
     */
    void initialize(PluginContext context) throws PluginException;
    void activate() throws PluginException;
    void deactivate() throws PluginException;
    void dispose();
    
    /**
     * Plugin metadata
     */
    Metadata getMetadata();
}
```

### Plugin Context API

```java
/**
 * Context provided to plugins for accessing IDE services.
 * @since 1.0.0
 */
public interface PluginContext {
    
    // Service Access
    <T> T getService(Class<T> serviceClass);
    <T> void registerService(Class<T> serviceClass, T implementation);
    
    // UI Access
    WorkbenchWindow getWorkbench();
    StatusBar getStatusBar();
    MenuManager getMenuManager();
    ToolBarManager getToolBarManager();
    
    // Editor Access
    EditorManager getEditorManager();
    Editor getActiveEditor();
    List<Editor> getOpenEditors();
    
    // Project Access
    ProjectManager getProjectManager();
    Project getActiveProject();
    List<Project> getOpenProjects();
    
    // Event System
    void addEventListener(String eventType, EventListener listener);
    void removeEventListener(String eventType, EventListener listener);
    void fireEvent(Event event);
    
    // Configuration
    Configuration getConfiguration();
    void saveConfiguration(Configuration config);
    
    // Resources
    ResourceBundle getResourceBundle(String baseName);
    URL getResource(String path);
    File getPluginDirectory();
}
```

### Extension Points

```java
/**
 * Language support extension point
 * @since 1.0.0
 */
@ExtensionPoint("language")
public interface LanguageSupport {
    
    String getLanguageId();
    String[] getFileExtensions();
    String[] getMimeTypes();
    
    // Syntax
    Tokenizer getTokenizer();
    Parser getParser();
    StyleProvider getStyleProvider();
    
    // Features
    CompletionProvider getCompletionProvider();
    HoverProvider getHoverProvider();
    SignatureHelpProvider getSignatureHelpProvider();
    DefinitionProvider getDefinitionProvider();
    ReferenceProvider getReferenceProvider();
    
    // Refactoring
    RenameProvider getRenameProvider();
    CodeActionProvider getCodeActionProvider();
    
    // Formatting
    Formatter getFormatter();
    OnTypeFormattingProvider getOnTypeFormattingProvider();
}
```

```java
/**
 * Build tool extension point
 * @since 1.0.0
 */
@ExtensionPoint("build.tool")
public interface BuildToolProvider {
    
    String getId();
    String getName();
    String getDescription();
    
    // Detection
    boolean isApplicable(Project project);
    BuildConfiguration detectConfiguration(Project project);
    
    // Build
    CompletableFuture<BuildResult> build(BuildRequest request);
    CompletableFuture<Void> clean(Project project);
    
    // Development
    CompletableFuture<Process> startDevServer(DevServerConfig config);
    CompletableFuture<Void> watch(Project project, WatchListener listener);
    
    // Dependencies
    List<Dependency> getDependencies(Project project);
    CompletableFuture<Void> installDependency(Project project, String dependency);
    CompletableFuture<Void> updateDependencies(Project project);
}
```

## 🌐 REST API

### API Versioning

```
https://api.nmox.studio/v1/workspaces
https://api.nmox.studio/v2/workspaces  # Future version
```

### Authentication

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "secure_password"
}

Response:
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_in": 900,
  "token_type": "Bearer"
}
```

### Workspace API

```yaml
openapi: 3.0.0
info:
  title: NMOX Studio API
  version: 1.0.0
  
paths:
  /api/v1/workspaces:
    get:
      summary: List workspaces
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 1
        - name: limit
          in: query
          schema:
            type: integer
            default: 20
            maximum: 100
      responses:
        200:
          description: Success
          content:
            application/json:
              schema:
                type: object
                properties:
                  data:
                    type: array
                    items:
                      $ref: '#/components/schemas/Workspace'
                  pagination:
                    $ref: '#/components/schemas/Pagination'
    
    post:
      summary: Create workspace
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - name
                - template
              properties:
                name:
                  type: string
                  maxLength: 255
                template:
                  type: string
                  enum: [react, vue, angular, vanilla]
                description:
                  type: string
                  maxLength: 1000
      responses:
        201:
          description: Created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Workspace'
                
  /api/v1/workspaces/{id}:
    get:
      summary: Get workspace
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        200:
          description: Success
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Workspace'
        404:
          description: Not found
          
components:
  schemas:
    Workspace:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        description:
          type: string
        template:
          type: string
        created_at:
          type: string
          format: date-time
        updated_at:
          type: string
          format: date-time
        owner:
          $ref: '#/components/schemas/User'
```

### Error Response Format

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [
      {
        "field": "name",
        "message": "Name is required",
        "code": "REQUIRED"
      }
    ],
    "timestamp": "2025-01-15T10:30:00Z",
    "request_id": "req_abc123"
  }
}
```

## 🔄 WebSocket API

### Connection Protocol

```javascript
// Client connection
const ws = new WebSocket('wss://ws.nmox.studio/v1/workspace');

ws.onopen = () => {
    // Authenticate
    ws.send(JSON.stringify({
        type: 'auth',
        token: 'Bearer eyJhbGciOiJIUzI1NiIs...'
    }));
};

ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    
    switch(message.type) {
        case 'auth_success':
            console.log('Authenticated');
            break;
        case 'file_changed':
            handleFileChange(message.data);
            break;
        case 'cursor_position':
            updateCursorPosition(message.data);
            break;
    }
};
```

### Message Protocol

```typescript
interface WebSocketMessage {
    id: string;           // Message ID for request/response correlation
    type: MessageType;    // Message type
    timestamp: number;    // Unix timestamp
    data: any;           // Message payload
}

enum MessageType {
    // Authentication
    AUTH = 'auth',
    AUTH_SUCCESS = 'auth_success',
    AUTH_ERROR = 'auth_error',
    
    // File Operations
    FILE_OPEN = 'file_open',
    FILE_CLOSE = 'file_close',
    FILE_CHANGE = 'file_change',
    FILE_SAVE = 'file_save',
    
    // Collaboration
    CURSOR_POSITION = 'cursor_position',
    SELECTION_CHANGE = 'selection_change',
    USER_JOIN = 'user_join',
    USER_LEAVE = 'user_leave',
    
    // Real-time Features
    COMPLETION_REQUEST = 'completion_request',
    COMPLETION_RESPONSE = 'completion_response',
    DIAGNOSTIC_UPDATE = 'diagnostic_update',
}
```

## 🤖 Language Server Protocol

### LSP Implementation

```java
/**
 * Language Server implementation for NMOX Studio
 * @since 1.0.0
 */
public class NMOXLanguageServer implements LanguageServer {
    
    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        
        // Text Document Sync
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        
        // Completion
        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setResolveProvider(true);
        completionOptions.setTriggerCharacters(Arrays.asList(".", ":", "<", "\"", "/"));
        capabilities.setCompletionProvider(completionOptions);
        
        // Hover
        capabilities.setHoverProvider(true);
        
        // Signature Help
        SignatureHelpOptions signatureHelpOptions = new SignatureHelpOptions();
        signatureHelpOptions.setTriggerCharacters(Arrays.asList("(", ","));
        capabilities.setSignatureHelpProvider(signatureHelpOptions);
        
        // Go to Definition
        capabilities.setDefinitionProvider(true);
        
        // Find References
        capabilities.setReferencesProvider(true);
        
        // Document Symbols
        capabilities.setDocumentSymbolProvider(true);
        
        // Code Actions
        capabilities.setCodeActionProvider(true);
        
        // Formatting
        capabilities.setDocumentFormattingProvider(true);
        capabilities.setDocumentRangeFormattingProvider(true);
        
        // Rename
        capabilities.setRenameProvider(true);
        
        InitializeResult result = new InitializeResult(capabilities);
        return CompletableFuture.completedFuture(result);
    }
    
    @Override
    public TextDocumentService getTextDocumentService() {
        return new NMOXTextDocumentService();
    }
    
    @Override
    public WorkspaceService getWorkspaceService() {
        return new NMOXWorkspaceService();
    }
}
```

## 📦 Package Manager API

### NPM Integration API

```java
/**
 * NPM package manager integration
 * @since 1.0.0
 */
public interface NpmAPI {
    
    /**
     * Search for packages
     */
    CompletableFuture<List<Package>> search(String query, SearchOptions options);
    
    /**
     * Get package information
     */
    CompletableFuture<PackageInfo> getPackageInfo(String name, String version);
    
    /**
     * Install packages
     */
    CompletableFuture<InstallResult> install(Project project, String... packages);
    
    /**
     * Update packages
     */
    CompletableFuture<UpdateResult> update(Project project, UpdateOptions options);
    
    /**
     * Run scripts
     */
    CompletableFuture<Process> runScript(Project project, String script, String... args);
    
    /**
     * Audit dependencies
     */
    CompletableFuture<AuditResult> audit(Project project, AuditLevel level);
}
```

## 🎨 UI Extension API

### View Extension

```typescript
/**
 * Register a custom view
 * @since 1.0.0
 */
export interface ViewContribution {
    id: string;
    name: string;
    icon?: string;
    location: ViewLocation;
    component: ComponentType;
    priority?: number;
    when?: ContextExpression;
}

export enum ViewLocation {
    LEFT_PANEL = 'left-panel',
    RIGHT_PANEL = 'right-panel',
    BOTTOM_PANEL = 'bottom-panel',
    EDITOR_TOOLBAR = 'editor-toolbar',
    STATUS_BAR = 'status-bar',
}

// Registration
nmoxStudio.views.register({
    id: 'my-custom-view',
    name: 'My Custom View',
    icon: 'icons/custom.svg',
    location: ViewLocation.LEFT_PANEL,
    component: MyCustomViewComponent,
    priority: 100,
    when: 'workspaceOpen'
});
```

### Command API

```typescript
/**
 * Register commands
 * @since 1.0.0
 */
export interface Command {
    id: string;
    title: string;
    category?: string;
    icon?: string;
    shortcut?: string;
    when?: ContextExpression;
    handler: CommandHandler;
}

// Registration
nmoxStudio.commands.register({
    id: 'myext.doSomething',
    title: 'Do Something',
    category: 'My Extension',
    shortcut: 'Ctrl+Shift+P',
    when: 'editorFocus',
    handler: async (args) => {
        const editor = nmoxStudio.window.activeEditor;
        await editor.insertText('Hello World');
    }
});
```

## 📊 Telemetry API

### Event Tracking

```java
/**
 * Telemetry service for anonymous usage tracking
 * @since 1.0.0
 */
public interface TelemetryAPI {
    
    /**
     * Track an event
     */
    void trackEvent(String category, String action, Map<String, Object> properties);
    
    /**
     * Track a metric
     */
    void trackMetric(String name, double value, Map<String, String> dimensions);
    
    /**
     * Track an exception
     */
    void trackException(Exception exception, Map<String, Object> properties);
    
    /**
     * Track a page view
     */
    void trackPageView(String page, Map<String, Object> properties);
    
    /**
     * Set user properties
     */
    void setUserProperties(Map<String, Object> properties);
    
    /**
     * Opt-out of telemetry
     */
    void optOut();
}
```

## 🔐 Authentication API

### OAuth2 Integration

```java
/**
 * OAuth2 authentication provider
 * @since 1.0.0
 */
public interface OAuth2Provider {
    
    String getProviderId();
    String getProviderName();
    
    /**
     * Get authorization URL
     */
    String getAuthorizationUrl(String state, String redirectUri);
    
    /**
     * Exchange code for tokens
     */
    CompletableFuture<TokenResponse> exchangeCode(String code, String redirectUri);
    
    /**
     * Refresh access token
     */
    CompletableFuture<TokenResponse> refreshToken(String refreshToken);
    
    /**
     * Get user info
     */
    CompletableFuture<UserInfo> getUserInfo(String accessToken);
    
    /**
     * Revoke tokens
     */
    CompletableFuture<Void> revokeToken(String token);
}
```

## 📝 API Documentation

### Automatic Documentation Generation

```java
/**
 * API documentation generator
 * @since 1.0.0
 */
@ApiDoc(
    title = "NMOX Studio Plugin API",
    version = "1.0.0",
    description = "Comprehensive API for extending NMOX Studio",
    contact = @Contact(
        name = "API Support",
        email = "api@nmox.studio",
        url = "https://api.nmox.studio"
    )
)
public class APIDocumentation {
    
    @ApiMethod(
        summary = "Get completion items",
        description = "Returns completion items for the given position",
        parameters = {
            @ApiParam(name = "document", description = "The document URI"),
            @ApiParam(name = "position", description = "The cursor position")
        },
        returns = "List of completion items",
        example = @Example(
            request = "{ \"document\": \"file:///test.js\", \"position\": { \"line\": 10, \"character\": 5 } }",
            response = "[ { \"label\": \"console\", \"kind\": \"Field\" } ]"
        )
    )
    public List<CompletionItem> getCompletions(String document, Position position) {
        // Implementation
    }
}
```

## 🔄 API Versioning Strategy

### Semantic Versioning

```
MAJOR.MINOR.PATCH

MAJOR: Breaking changes
MINOR: New features (backward compatible)
PATCH: Bug fixes (backward compatible)
```

### Deprecation Policy

```java
/**
 * @deprecated Since 1.2.0, use {@link #newMethod()} instead.
 * Will be removed in 2.0.0
 */
@Deprecated(since = "1.2.0", forRemoval = true)
public void oldMethod() {
    // Log deprecation warning
    logger.warn("oldMethod() is deprecated and will be removed in 2.0.0");
    
    // Delegate to new method
    newMethod();
}
```

### Migration Guides

```markdown
# Migration Guide: v1.x to v2.0

## Breaking Changes

### Plugin API
- `initialize()` now returns `CompletableFuture<Void>`
- `PluginContext.getService()` requires explicit type parameter

### Before (v1.x):
```java
public void initialize(PluginContext context) {
    Service service = context.getService("myService");
}
```

### After (v2.0):
```java
public CompletableFuture<Void> initialize(PluginContext context) {
    MyService service = context.getService(MyService.class);
    return CompletableFuture.completedFuture(null);
}
```
```

---

**Last Updated:** January 2025  
**API Documentation:** [api.nmox.studio/docs](https://api.nmox.studio/docs)  
**API Support:** api@nmox.studio