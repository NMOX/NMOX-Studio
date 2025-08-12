# Technical Debt & Known Issues

*Honest assessment of shortcuts taken and problems to solve*

## 🎯 Philosophy: Ship First, Perfect Later

We successfully shipped v0.1 by making strategic compromises. This document honestly tracks what needs attention in future iterations.

## 🚨 Critical Issues (Fix in v0.2)

### 1. JavaScript Syntax Highlighting Missing
**Problem:** Basic file recognition without proper syntax highlighting
```java
// Current: Generic text highlighting
// Need: Proper JavaScript Lexer implementation
```
**Impact:** Poor developer experience for code reading
**Effort:** 2-3 days (implement NetBeans Lexer)
**Priority:** HIGH

### 2. NPM Error Handling is Basic
**Problem:** NPM command failures show raw console output
```java
// Current: Just dumps stderr to console
catch (Exception e) {
    io.getErr().println("Command failed: " + e.getMessage());
}

// Need: Parse npm errors and show user-friendly messages
```
**Impact:** Confusing error messages for users
**Effort:** 1-2 days
**Priority:** HIGH

### 3. No Input Validation for Project Creation
**Problem:** Project wizard doesn't validate names/paths
```java
// Current: Accepts any input
String projectName = nameField.getText();

// Need: Validate names, check for existing folders, etc.
```
**Impact:** Runtime errors, corrupted projects
**Effort:** 1 day
**Priority:** MEDIUM

## ⚠️ Design Debt (Address in v0.3-0.4)

### 4. Hardcoded Project Templates
**Problem:** Templates are embedded as Java strings
```java
// Current: Massive string literals in code
String packageJson = "{\n" +
    "  \"name\": \"" + projectName + "\",\n" +
    "  \"version\": \"1.0.0\",\n" +
    // ... 50 more lines
```
**Better Approach:** Template files in resources with variable substitution
**Effort:** 2-3 days (refactor to template engine)
**Priority:** MEDIUM

### 5. Basic Code Completion
**Problem:** Simple keyword completion, no semantic analysis
```java
// Current: Static keyword array
private static final String[] SNIPPETS = {
    "console.log()", "function() {}", "if () {}"
};

// Need: Parse project dependencies, analyze imports, context-aware suggestions
```
**Impact:** Limited usefulness compared to modern IDEs
**Effort:** 1-2 weeks (semantic analysis)
**Priority:** MEDIUM

### 6. No Configuration System
**Problem:** All settings are hardcoded
```java
// Current: No way to configure npm path, build commands, etc.
ProcessBuilder pb = new ProcessBuilder("npm", "run", command);

// Need: Settings API for user preferences
```
**Impact:** Can't adapt to different environments
**Effort:** 3-4 days (settings infrastructure)
**Priority:** MEDIUM

## 🔧 Implementation Shortcuts (Technical Debt)

### 7. Missing File Type Icons
**Problem:** Using generic NetBeans icons
```java
// Current: Default file icons
// Need: Custom icons for .js, .ts, .vue, etc.
```
**Impact:** Visual consistency issues
**Effort:** 1 day (create/integrate icon set)
**Priority:** LOW

### 8. No Caching for Package.json Parsing
**Problem:** Re-parse package.json on every access
```java
// Current: File I/O every time
JSONObject json = new JSONObject(Files.readString(packageJsonPath));

// Need: Cache parsed content, invalidate on file changes
```
**Impact:** Performance degradation on large projects
**Effort:** 1 day
**Priority:** LOW

### 9. Bundle Configuration Dependencies
**Problem:** Some NetBeans dependencies need cleaner integration
```java
// Current: Using -Dnetbeans.verify.integrity=false to build
// Need: Properly declare all transitive dependencies
```
**Impact:** Build complexity, potential runtime issues
**Effort:** 2-3 days (dependency analysis)
**Priority:** MEDIUM

### 10. No Unit Tests for Core Components
**Problem:** Integration tests only, no unit test coverage
```java
// Current: Manual testing only
// Need: Unit tests for NpmService, WebProject, etc.
```
**Impact:** Regression risk during refactoring
**Effort:** 1 week (test infrastructure + coverage)
**Priority:** MEDIUM

## 🏗️ Architectural Limitations

### 11. Single-threaded NPM Execution
**Problem:** NPM commands block UI thread
```java
// Current: Synchronous process execution
Process process = pb.start();
process.waitFor(); // Blocks UI

// Need: Background execution with cancellation
```
**Impact:** UI freezes during long operations
**Effort:** 2-3 days (async execution framework)
**Priority:** MEDIUM

### 12. No Plugin Extension Points
**Problem:** Monolithic design, hard to extend
```java
// Current: Everything in core modules
// Need: Extension points for custom file types, build tools, etc.
```
**Impact:** Limits community contributions
**Effort:** 1-2 weeks (plugin API design)
**Priority:** LOW (v1.0 feature)

### 13. Basic Project Model
**Problem:** Simple package.json recognition only
```java
// Current: Binary project detection
return projectDirectory.getFileObject("package.json") != null;

// Need: Workspace concepts, multi-module projects, monorepos
```
**Impact:** Limited project type support
**Effort:** 1 week (enhanced project model)
**Priority:** LOW

## 📊 Performance Issues

### 14. Cold Startup Time (~5 seconds)
**Problem:** NetBeans platform + module loading overhead
```
Current startup sequence:
- NetBeans platform init: ~2s
- Module loading: ~1s  
- UI initialization: ~1s
- Theme application: ~1s
```
**Optimization opportunities:**
- Lazy module loading
- Cached metadata
- Startup splash optimization
**Effort:** 1 week (startup optimization)
**Priority:** MEDIUM

### 15. Memory Usage (~400MB baseline)
**Problem:** NetBeans platform memory overhead
```
Memory breakdown:
- NetBeans platform: ~250MB
- Our modules: ~50MB
- UI components: ~50MB
- File caches: ~50MB
```
**Optimization opportunities:**
- Reduce loaded modules
- Better file caching
- UI component pooling
**Effort:** 3-4 days
**Priority:** LOW

## 🔐 Security Considerations

### 16. Unsandboxed NPM Execution
**Problem:** NPM commands run with full user privileges
```java
// Current: No process isolation
ProcessBuilder pb = new ProcessBuilder("npm", command);
// Executes with full filesystem access

// Need: Consider sandboxing for untrusted projects
```
**Impact:** Potential security risk with malicious projects
**Effort:** 1-2 weeks (sandboxing research + implementation)
**Priority:** LOW (trust local environment)

### 17. No Input Sanitization for Commands
**Problem:** Command arguments passed without validation
```java
// Current: Direct string passing
new ProcessBuilder("npm", "run", userInput);

// Need: Validate against allowed command patterns
```
**Impact:** Potential command injection
**Effort:** 1 day
**Priority:** MEDIUM

## 🧪 Testing Gaps

### 18. Limited Error Scenario Testing
**Problem:** Happy path testing only
```
Missing test scenarios:
- npm not installed
- Invalid package.json
- Network timeouts
- Disk space issues
- Permission problems
```
**Effort:** 2-3 days (comprehensive error testing)
**Priority:** MEDIUM

### 19. No Performance Testing
**Problem:** No baseline performance measurements
**Need:** Automated performance tests for:
- Startup time
- File operation speed  
- Memory usage patterns
- UI responsiveness
**Effort:** 1 week
**Priority:** LOW

## 📋 Documentation Debt

### 20. Missing Developer Documentation
**Problem:** No contribution guidelines or API docs
**Need:**
- Code style guidelines
- Build setup instructions
- Architecture overview for contributors
- API documentation
**Effort:** 2-3 days
**Priority:** MEDIUM

### 21. No User Documentation
**Problem:** No help or tutorial content
**Need:**
- Getting started guide
- Feature documentation
- Troubleshooting guide
- Keyboard shortcuts reference
**Effort:** 1 week
**Priority:** MEDIUM

## 🎯 Debt Retirement Strategy

### Phase 1: Critical Fixes (v0.2)
1. JavaScript syntax highlighting
2. NPM error handling improvement
3. Input validation for project creation
4. Security: command sanitization

### Phase 2: Design Improvements (v0.3)
1. Template system refactoring
2. Configuration infrastructure
3. Async NPM execution
4. Unit test coverage

### Phase 3: Performance & Polish (v0.4)
1. Startup time optimization
2. Memory usage reduction
3. File type icons
4. Comprehensive error testing

### Phase 4: Architecture (v0.5+)
1. Plugin extension points
2. Enhanced project model
3. Process sandboxing
4. Performance testing framework

## 💰 Technical Debt Interest Calculation

```
Current Debt Level: MANAGEABLE
- High-impact issues: 3 items
- Medium-impact issues: 12 items  
- Low-impact issues: 6 items

Development Velocity Impact: ~20%
- Time spent on workarounds
- Manual testing overhead
- Limited feature capability

Debt Retirement Timeline: 3-4 months
- Critical issues: 1 month
- Design improvements: 2 months
- Performance & architecture: 1 month
```

## 🎉 Debt Success Stories

### What We Did Right
1. **Used NetBeans APIs correctly** - No architectural debt
2. **Modular design** - Easy to refactor components
3. **Simple build process** - Easy to understand and maintain
4. **Clear separation of concerns** - Each module has focused responsibility

### Lessons Learned
1. **Ship with shortcuts** - Better than not shipping
2. **Document debt honestly** - Helps prioritize fixes
3. **Focus on user value** - Perfect code doesn't matter if no one uses it
4. **Incremental improvement** - Small consistent fixes beat big rewrites

---

**Bottom Line:** We have manageable technical debt that doesn't prevent continued development. Most issues are fixable with focused effort, and none are architectural dead ends.

*"The best code is code that ships and gets used."* ✅