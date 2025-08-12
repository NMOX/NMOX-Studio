# Next Iteration Plan: v0.2 "Polish & Performance"

*From working prototype to polished developer tool*

## 🎯 Mission: Make v0.1 Features Shine

Transform our working foundation into a tool developers actively choose over alternatives.

## 📊 Current State Assessment

### What Works Well ✅
- File type recognition and project loading
- NPM command execution and output display
- Project templates create functional structures
- Dark theme provides modern appearance
- Distribution package installs and runs

### What Needs Improvement 🔧
- Basic syntax highlighting needs proper implementation
- Error messages are technical/unfriendly
- UI responsiveness during long operations
- Missing configuration options
- Performance optimization opportunities

## 🚀 v0.2 Feature Plan

### Priority 1: Core Experience (Week 1-2)

#### 1. JavaScript Syntax Highlighting
**Goal:** Proper syntax highlighting for JavaScript/TypeScript files

**Implementation:**
```java
// Create custom NetBeans Lexer
@MimeRegistration(mimeType = "text/javascript", service = LanguageHierarchy.class)
public class JavaScriptLanguageHierarchy extends LanguageHierarchy<JavaScriptTokenId> {
    
    @Override
    protected Lexer<JavaScriptTokenId> createLexer(LexerRestartInfo<JavaScriptTokenId> info) {
        return new JavaScriptLexer(info);
    }
    
    // Token definitions for keywords, operators, literals, etc.
}
```

**Technical Tasks:**
- [ ] Create JavaScriptTokenId enum with token types
- [ ] Implement JavaScriptLexer with proper state machine
- [ ] Define color scheme for syntax highlighting
- [ ] Register lexer with NetBeans MIME system
- [ ] Test with various JavaScript constructs

**Success Criteria:**
- Keywords, strings, comments properly highlighted
- ES6+ syntax recognition (arrow functions, destructuring)
- TypeScript syntax support
- Performance acceptable for large files

#### 2. Enhanced NPM Error Handling
**Goal:** User-friendly error messages and better process management

**Current Problem:**
```java
// Raw error dump
catch (Exception e) {
    io.getErr().println("Command failed: " + e.getMessage());
}
```

**Improved Implementation:**
```java
public class NpmErrorParser {
    public static class NpmError {
        private final String userMessage;
        private final String technicalDetails;
        private final List<String> suggestions;
    }
    
    public NpmError parseError(String errorOutput) {
        // Parse common npm error patterns
        if (errorOutput.contains("ENOENT")) {
            return new NpmError(
                "NPM not found. Please install Node.js and NPM.",
                errorOutput,
                Arrays.asList(
                    "Install Node.js from nodejs.org",
                    "Restart NMOX Studio after installation"
                )
            );
        }
        // ... handle other error patterns
    }
}
```

**Technical Tasks:**
- [ ] Create NPM error pattern recognition
- [ ] Design error dialog with user-friendly messages
- [ ] Add suggestions for common problems
- [ ] Implement command timeout handling
- [ ] Add cancellation support for long-running commands

**Success Criteria:**
- Clear error messages for common npm problems
- Actionable suggestions when possible
- Ability to cancel long-running operations
- Graceful handling of missing npm binary

#### 3. Async Operation Management
**Goal:** Prevent UI blocking during NPM operations

**Implementation:**
```java
public class AsyncNpmRunner {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    public CompletableFuture<CommandResult> runCommand(String command, File workingDir) {
        return CompletableFuture.supplyAsync(() -> {
            // Run npm command in background
            ProcessBuilder pb = new ProcessBuilder("npm", "run", command);
            pb.directory(workingDir);
            
            // Stream output to UI on EDT
            SwingUtilities.invokeLater(() -> updateUI(output));
            
            return new CommandResult(exitCode, output);
        }, executor);
    }
}
```

**Technical Tasks:**
- [ ] Background execution framework
- [ ] Progress indicators for operations
- [ ] Cancellation mechanism
- [ ] UI updates on Event Dispatch Thread
- [ ] Error propagation to UI layer

### Priority 2: User Experience (Week 3)

#### 4. Configuration System
**Goal:** Allow users to customize npm paths, commands, and preferences

**Implementation:**
```java
// Settings API integration
public class NmoxSettings {
    private static final String NPM_PATH_KEY = "nmox.npm.path";
    private static final String DEFAULT_COMMANDS_KEY = "nmox.npm.defaultCommands";
    
    public static String getNpmPath() {
        return NbPreferences.forModule(NmoxSettings.class)
            .get(NPM_PATH_KEY, "npm");
    }
    
    public static void setNpmPath(String path) {
        NbPreferences.forModule(NmoxSettings.class)
            .put(NPM_PATH_KEY, path);
    }
}

// Settings UI panel
@OptionsPanelController.SubRegistration(
    displayName = "NMOX Studio",
    keywords = "nmox npm javascript",
    keywordsCategory = "Advanced/NMOX"
)
public class NmoxOptionsPanel extends OptionsPanelController {
    // UI for configuring settings
}
```

**Technical Tasks:**
- [ ] Settings panel in NetBeans Options
- [ ] NPM path configuration
- [ ] Default npm commands customization
- [ ] Theme preferences
- [ ] Project template locations

#### 5. Enhanced Project Templates
**Goal:** More robust template system with better project types

**Current Limitation:**
```java
// Hardcoded templates
String packageJson = "{\n" +
    "  \"name\": \"" + projectName + "\",\n" +
    // ... embedded in Java code
```

**Improved System:**
```java
public class TemplateEngine {
    public void createProject(TemplateDescriptor template, ProjectConfig config) {
        // Load template from resources
        InputStream templateStream = getClass()
            .getResourceAsStream("/templates/" + template.getName());
        
        // Process with variable substitution
        Map<String, String> variables = Map.of(
            "PROJECT_NAME", config.getName(),
            "AUTHOR", config.getAuthor(),
            "DESCRIPTION", config.getDescription()
        );
        
        processTemplate(templateStream, config.getOutputDir(), variables);
    }
}
```

**Technical Tasks:**
- [ ] Template files in resources directory
- [ ] Variable substitution engine
- [ ] Next.js template
- [ ] Svelte template  
- [ ] Template validation system
- [ ] Custom template import/export

**Success Criteria:**
- Templates load from files, not hardcoded strings
- Easy to add new templates without code changes
- Variable substitution for project customization
- Template validation prevents broken projects

### Priority 3: Performance & Polish (Week 4)

#### 6. Startup Optimization
**Goal:** Reduce cold startup time from ~5s to ~3s

**Optimization Areas:**
```java
// Lazy module loading
@OnStart
public class LazyModuleLoader implements Runnable {
    @Override
    public void run() {
        // Only load essential modules immediately
        // Defer heavy modules until first use
        SwingUtilities.invokeLater(() -> {
            loadNonEssentialModules();
        });
    }
}

// Cached metadata
public class ProjectMetadataCache {
    // Cache package.json parsing results
    // Cache file system scanning
    // Invalidate on file changes
}
```

**Technical Tasks:**
- [ ] Profile current startup sequence
- [ ] Implement lazy loading for non-critical modules
- [ ] Cache frequently accessed metadata
- [ ] Optimize theme application timing
- [ ] Reduce initial UI component creation

#### 7. Custom File Type Icons
**Goal:** Professional appearance with custom iconset

**Implementation:**
```java
// Custom icon registration
@MIMEResolver.ExtensionRegistration(
    extension = {"js"},
    mimeType = "text/javascript",
    iconBase = "org/nmox/studio/icons/javascript.png"
)

// Icon theme system
public class IconTheme {
    public static final String JAVASCRIPT = "javascript.png";
    public static final String TYPESCRIPT = "typescript.png";
    public static final String VUE = "vue.png";
    public static final String REACT = "react.png";
}
```

**Technical Tasks:**
- [ ] Design consistent icon set (16x16, 32x32)
- [ ] Implement for .js, .ts, .vue, .jsx, .json
- [ ] Dark/light theme variants
- [ ] High-DPI support
- [ ] Integration with NetBeans icon system

#### 8. Input Validation & Error Prevention
**Goal:** Prevent common user errors in project creation

**Implementation:**
```java
public class ProjectValidator {
    public ValidationResult validateProjectName(String name) {
        if (name.trim().isEmpty()) {
            return ValidationResult.error("Project name cannot be empty");
        }
        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            return ValidationResult.error("Project name contains invalid characters");
        }
        if (RESERVED_NAMES.contains(name.toLowerCase())) {
            return ValidationResult.error("'" + name + "' is a reserved name");
        }
        return ValidationResult.ok();
    }
    
    public ValidationResult validateProjectPath(File path) {
        if (!path.getParentFile().exists()) {
            return ValidationResult.error("Parent directory does not exist");
        }
        if (!path.getParentFile().canWrite()) {
            return ValidationResult.error("Cannot write to directory");
        }
        if (path.exists() && path.list().length > 0) {
            return ValidationResult.warning("Directory is not empty");
        }
        return ValidationResult.ok();
    }
}
```

**Technical Tasks:**
- [ ] Real-time validation in project wizard
- [ ] Path existence and permission checking
- [ ] Reserved name detection
- [ ] Warning for non-empty directories
- [ ] Suggestion system for invalid names

## 🧪 Testing Strategy

### Automated Testing
```java
// Unit tests for core components
public class NpmServiceTest {
    @Test
    public void testNpmNotFound() {
        // Mock environment without npm
        // Verify proper error handling
    }
    
    @Test
    public void testCommandExecution() {
        // Mock successful npm command
        // Verify output capture
    }
}

// Integration tests
public class ProjectCreationTest {
    @Test
    public void testReactProjectCreation() {
        // Create project from template
        // Verify all files exist
        // Verify npm install works
    }
}
```

### Manual Testing Scenarios
- [ ] Project creation with various templates
- [ ] NPM command execution (success/failure cases)
- [ ] File editing with syntax highlighting
- [ ] Settings configuration and persistence
- [ ] Startup/shutdown cycles
- [ ] Memory usage during extended use

## 📈 Success Metrics

### Performance Targets
- **Startup time**: < 3 seconds (from ~5s)
- **Command execution**: < 1s response time
- **Memory usage**: < 350MB baseline (from ~400MB)
- **UI responsiveness**: No blocking operations > 500ms

### User Experience Goals
- **Error resolution**: Users can understand and fix 80% of errors
- **Project creation**: New projects work without manual fixes
- **Daily usage**: Tool feels responsive during normal development

### Code Quality Metrics
- **Test coverage**: > 60% for core components
- **Documentation**: All public APIs documented
- **Code review**: All changes reviewed before merge

## 🔄 Development Process

### Week-by-Week Breakdown

**Week 1: Syntax Highlighting + Error Handling**
- Days 1-3: JavaScript Lexer implementation
- Days 4-5: NPM error parsing and UI

**Week 2: Async Operations + Configuration**
- Days 1-3: Background execution framework
- Days 4-5: Settings system and UI

**Week 3: Templates + Validation**
- Days 1-3: Template engine refactoring
- Days 4-5: Input validation system

**Week 4: Performance + Polish**
- Days 1-2: Startup optimization
- Days 3-4: Custom icons and UI polish
- Day 5: Testing and bug fixes

### Quality Gates
- **End of Week 1**: Syntax highlighting working, improved error messages
- **End of Week 2**: Non-blocking operations, configurable settings
- **End of Week 3**: New template system, validated inputs
- **End of Week 4**: Performance targets met, ready for release

## 🚀 Release Plan

### v0.2 Release Criteria
- [ ] All Priority 1 features complete and tested
- [ ] Performance targets achieved
- [ ] No critical bugs or regressions
- [ ] Documentation updated
- [ ] Distribution package tested on multiple platforms

### Post-Release
- **User feedback collection**: Survey existing users about improvements
- **Performance monitoring**: Track actual startup times and usage patterns
- **Bug triage**: Prioritize issues based on user impact
- **v0.3 planning**: Based on v0.2 feedback and usage data

---

**The goal is to evolve from "it works" to "it's a pleasure to use" while maintaining the solid foundation we've built.**

*Next review: Weekly progress check-ins*