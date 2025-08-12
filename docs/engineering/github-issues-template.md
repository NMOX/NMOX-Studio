# GitHub Issues Template for v0.2 Development

*Ready-to-create issues for systematic v0.2 development*

## 🏷️ Issue Labels to Create

```
Priority Labels:
- priority/critical    # Must fix for release
- priority/high       # Important for user experience  
- priority/medium     # Nice to have improvements
- priority/low        # Future consideration

Type Labels:
- type/feature        # New functionality
- type/enhancement    # Improvement to existing feature
- type/bug           # Something is broken
- type/performance   # Performance optimization
- type/docs          # Documentation update

Component Labels:
- component/editor    # Editor module (syntax highlighting, completion)
- component/tools     # Tools module (npm integration)
- component/ui        # User interface components
- component/core      # Core platform services
- component/build     # Build system and packaging

Status Labels:
- status/ready        # Ready for development
- status/in-progress  # Currently being worked on
- status/review       # Needs code review
- status/testing      # Needs testing
- status/blocked      # Blocked by external dependency
```

## 📋 Priority Issues for v0.2

### Critical Issues (Week 1)

#### Issue #1: Implement JavaScript Syntax Highlighting
```markdown
**Title:** Implement JavaScript Syntax Highlighting with NetBeans Lexer

**Labels:** `priority/critical`, `type/feature`, `component/editor`

**Description:**
Currently JavaScript files display with basic text highlighting. We need proper syntax highlighting for professional code editing experience.

**Acceptance Criteria:**
- [ ] Keywords highlighted (function, const, let, var, class, etc.)
- [ ] String literals highlighted with escape sequence support
- [ ] Comments highlighted (single-line and multi-line)
- [ ] Numbers and boolean literals distinguished
- [ ] Regular expressions properly highlighted
- [ ] ES6+ syntax support (arrow functions, template literals)
- [ ] Performance: No lag when typing in 2000+ line files

**Technical Implementation:**
- Create `JavaScriptTokenId` enum with token types
- Implement `JavaScriptLexer` with state machine
- Create `JavaScriptLanguageHierarchy` for NetBeans integration
- Define color scheme for dark theme
- Register lexer with MIME system

**Files to Create:**
- `editor/src/main/java/org/nmox/studio/editor/javascript/JavaScriptTokenId.java`
- `editor/src/main/java/org/nmox/studio/editor/javascript/JavaScriptLexer.java`
- `editor/src/main/java/org/nmox/studio/editor/javascript/JavaScriptLanguageHierarchy.java`

**Estimated Effort:** 3 days
**Dependencies:** None
**Assignee:** TBD
```

#### Issue #2: Enhance NPM Error Handling
```markdown
**Title:** User-Friendly NPM Error Messages and Suggestions

**Labels:** `priority/critical`, `type/enhancement`, `component/tools`

**Description:**
NPM command failures currently show raw console output, which is confusing for users. We need to parse common npm errors and provide helpful, actionable messages.

**Current Problem:**
```
npm ERR! code ENOENT
npm ERR! syscall spawn npm
npm ERR! path /some/long/path
npm ERR! errno -2
npm ERR! enoent spawn npm ENOENT
```

**Desired Solution:**
```
NPM not found
Please install Node.js and NPM from nodejs.org
After installation, restart NMOX Studio

[Show Technical Details] [Help]
```

**Acceptance Criteria:**
- [ ] Parse top 5 most common npm error patterns
- [ ] Display user-friendly error messages
- [ ] Provide actionable suggestions when possible
- [ ] "Show Details" button reveals technical output
- [ ] Error dialog doesn't block other IDE operations
- [ ] Help button links to troubleshooting guide

**Error Patterns to Handle:**
1. `ENOENT` - NPM not found
2. `EACCES` - Permission denied
3. `ENOTFOUND` - Package not found  
4. `missing script` - Script not in package.json
5. `ERESOLVE` - Dependency conflicts

**Technical Implementation:**
- Create `NPMErrorParser` class
- Map error patterns to user messages
- Design error dialog with expandable details
- Integrate with existing NPM command execution

**Files to Create:**
- `tools/src/main/java/org/nmox/studio/tools/npm/NPMErrorParser.java`
- `tools/src/main/java/org/nmox/studio/tools/npm/ErrorDialog.java`

**Estimated Effort:** 2 days
**Dependencies:** None
**Assignee:** TBD
```

### High Priority Issues (Week 2)

#### Issue #3: Implement Async NPM Operations
```markdown
**Title:** Non-Blocking NPM Command Execution with Progress Indicators

**Labels:** `priority/high`, `type/performance`, `component/tools`

**Description:**
NPM commands currently block the UI thread, causing the application to freeze during operations like `npm install`. This creates a poor user experience.

**Current Problem:**
- UI freezes during npm operations
- No way to cancel long-running commands
- No progress feedback for users
- Application appears unresponsive

**Acceptance Criteria:**
- [ ] NPM commands run in background threads
- [ ] UI remains responsive during all operations
- [ ] Progress indicators for operations > 2 seconds
- [ ] Cancel button terminates running processes
- [ ] Output streams to console in real-time
- [ ] Multiple commands can run simultaneously (different projects)

**Technical Implementation:**
- Create `AsyncNpmRunner` using `CompletableFuture`
- Implement progress indicators with `ProgressHandle`
- Add cancellation support with `Process.destroy()`
- Ensure UI updates happen on EDT
- Handle process cleanup and resource management

**Files to Create:**
- `tools/src/main/java/org/nmox/studio/tools/npm/AsyncNpmRunner.java`
- `tools/src/main/java/org/nmox/studio/tools/npm/NPMProgressIndicator.java`
- `tools/src/main/java/org/nmox/studio/tools/npm/CancellableOperation.java`

**Estimated Effort:** 3 days
**Dependencies:** Issue #2 (error handling)
**Assignee:** TBD
```

#### Issue #4: Startup Time Optimization
```markdown
**Title:** Reduce Application Startup Time to Under 3 Seconds

**Labels:** `priority/high`, `type/performance`, `component/core`

**Description:**
Current startup time is ~5 seconds, which feels slow for a development tool. Target is < 3 seconds for better developer experience.

**Current Startup Breakdown:**
- NetBeans platform init: ~2s
- Module loading: ~1s
- UI initialization: ~1s  
- Theme application: ~1s

**Acceptance Criteria:**
- [ ] Startup time < 3 seconds on modern hardware
- [ ] No functionality regression
- [ ] Splash screen shows progress
- [ ] Essential features available immediately
- [ ] Non-essential features load lazily

**Optimization Strategies:**
- Lazy loading for non-critical modules
- Cache frequently accessed metadata
- Optimize theme application timing
- Reduce initial UI component creation
- Profile and eliminate bottlenecks

**Files to Modify:**
- `core/src/main/java/org/nmox/studio/core/ThemeInstaller.java`
- `application/src/main/resources/META-INF/MANIFEST.MF`

**Estimated Effort:** 2 days
**Dependencies:** None
**Assignee:** TBD
```

### Medium Priority Issues (Week 3)

#### Issue #5: Settings and Configuration System
```markdown
**Title:** User Configurable Settings Panel

**Labels:** `priority/medium`, `type/feature`, `component/core`

**Description:**
Users need to customize NPM paths, default commands, and IDE preferences for their environment.

**Acceptance Criteria:**
- [ ] Settings panel in NetBeans Options
- [ ] NPM binary path configuration
- [ ] Default npm commands customization
- [ ] Theme preferences
- [ ] Settings persist across restarts
- [ ] Settings validation and error handling

**Settings to Implement:**
1. NPM binary path (default: "npm")
2. Default npm commands list
3. Project template locations
4. Error dialog preferences
5. Syntax highlighting theme

**Technical Implementation:**
- Use NetBeans `NbPreferences` API
- Create `@OptionsPanelController.SubRegistration`
- Implement settings validation
- Wire settings to service classes

**Files to Create:**
- `core/src/main/java/org/nmox/studio/core/NMOXSettings.java`
- `core/src/main/java/org/nmox/studio/core/NMOXOptionsPanel.java`

**Estimated Effort:** 3 days
**Dependencies:** None
**Assignee:** TBD
```

#### Issue #6: Project Creation Input Validation
```markdown
**Title:** Validate Project Creation Inputs and Prevent Errors

**Labels:** `priority/medium`, `type/enhancement`, `component/tools`

**Description:**
Project wizard currently accepts any input, which can lead to creation failures or invalid projects.

**Acceptance Criteria:**
- [ ] Real-time validation in project wizard
- [ ] Project name validation (alphanumeric + underscore/hyphen)
- [ ] Path existence and permission checking
- [ ] Reserved name detection (node_modules, .git, etc.)
- [ ] Warning for non-empty directories
- [ ] Helpful error messages and suggestions

**Validation Rules:**
- Project name: 1-50 characters, alphanumeric + underscore/hyphen
- No reserved names: node_modules, .git, dist, build
- Parent directory must exist and be writable
- Warn if target directory exists and is not empty

**Technical Implementation:**
- Create `ProjectValidator` class
- Add real-time validation to wizard UI
- Implement validation result types
- Provide suggestion system for fixes

**Files to Create:**
- `tools/src/main/java/org/nmox/studio/tools/npm/ProjectValidator.java`
- `tools/src/main/java/org/nmox/studio/tools/npm/ValidationResult.java`

**Estimated Effort:** 2 days
**Dependencies:** None
**Assignee:** TBD
```

### Low Priority Issues (Week 4)

#### Issue #7: Custom File Type Icons
```markdown
**Title:** Professional File Type Icons for Web Development

**Labels:** `priority/low`, `type/enhancement`, `component/ui`

**Description:**
Currently using generic NetBeans file icons. Custom icons will improve visual hierarchy and professional appearance.

**Acceptance Criteria:**
- [ ] Custom 16x16 and 32x32 icons for file types
- [ ] Dark and light theme variants
- [ ] High-DPI (retina) support
- [ ] Consistent visual style
- [ ] Icons display correctly in file trees and tabs

**Icons Needed:**
- JavaScript (.js, .jsx, .mjs)
- TypeScript (.ts, .tsx)  
- JSON (.json)
- Package.json (special icon)
- Vue Single File Components (.vue)
- CSS/SCSS (.css, .scss)
- HTML (.html)
- Markdown (.md)

**Technical Implementation:**
- Design icon set with consistent style
- Implement icon registration with NetBeans
- Support theme variants
- Register with MIME types

**Files to Create:**
- `ui/src/main/resources/org/nmox/studio/ui/icons/`
- Icon registration in DataObject classes

**Estimated Effort:** 2 days
**Dependencies:** None
**Assignee:** TBD
```

#### Issue #8: Enhanced Project Templates
```markdown
**Title:** Template System with File-Based Templates and Variables

**Labels:** `priority/low`, `type/enhancement`, `component/tools`

**Description:**
Current templates are hardcoded in Java strings. Move to file-based system with variable substitution for easier maintenance and customization.

**Current Problem:**
```java
String packageJson = "{\n" +
    "  \"name\": \"" + projectName + "\",\n" +
    // ... 50 more lines of embedded JSON
```

**Desired Solution:**
```
templates/
├── react/
│   ├── template.json          # Template metadata
│   ├── package.json.template  # With {{PROJECT_NAME}} variables
│   ├── src/
│   │   └── App.js.template
│   └── public/
│       └── index.html.template
```

**Acceptance Criteria:**
- [ ] Templates load from resource files
- [ ] Variable substitution ({{PROJECT_NAME}}, {{AUTHOR}}, etc.)
- [ ] Template validation before creation
- [ ] Easy to add new templates without code changes
- [ ] Template metadata (description, required variables)

**Templates to Implement:**
1. Enhanced React template
2. Next.js template
3. Vue 3 + Vite template
4. Svelte template
5. Vanilla TypeScript template

**Technical Implementation:**
- Create template engine with variable substitution
- Move existing templates to resource files
- Implement template validation
- Update project wizard for new templates

**Files to Create:**
- `tools/src/main/java/org/nmox/studio/tools/npm/TemplateEngine.java`
- `tools/src/main/resources/templates/`

**Estimated Effort:** 3 days
**Dependencies:** Issue #6 (validation)
**Assignee:** TBD
```

## 🧪 Testing Issues

#### Issue #9: Unit Test Coverage for Core Components
```markdown
**Title:** Implement Unit Tests for Core Components (>60% Coverage)

**Labels:** `priority/high`, `type/testing`, `component/core`

**Description:**
Establish comprehensive unit test coverage for core components to prevent regressions and enable confident refactoring.

**Acceptance Criteria:**
- [ ] >60% line coverage for core modules
- [ ] Mock external dependencies (file system, processes)
- [ ] Test both success and failure scenarios
- [ ] Fast test execution (< 30 seconds total)
- [ ] Tests run in CI/CD pipeline

**Components to Test:**
1. `NpmService` - command execution and error handling
2. `WebProjectFactory` - project recognition logic
3. `ProjectValidator` - validation rules and edge cases
4. `NPMErrorParser` - error pattern matching
5. `TemplateEngine` - template processing and variables

**Technical Implementation:**
- Set up JUnit 5 + Mockito + AssertJ
- Mock file system operations
- Mock process execution
- Create test utilities for common scenarios

**Files to Create:**
- `*/src/test/java/**/*Test.java`
- Test configuration and utilities

**Estimated Effort:** 1 week (spread across other issues)
**Dependencies:** All feature implementations
**Assignee:** TBD
```

## 📊 Milestone Structure

### v0.2 Milestone: Polish & Performance
**Target Date:** 4 weeks from start
**Release Criteria:**
- All critical and high-priority issues resolved
- Performance targets met (startup < 3s, memory < 350MB)
- Test coverage > 60% for new code
- No critical bugs or regressions

**Issue Breakdown:**
- 🔴 Critical: 2 issues (syntax highlighting, error handling)
- 🟡 High: 2 issues (async operations, startup optimization)  
- 🟢 Medium: 2 issues (settings, validation)
- 🔵 Low: 2 issues (icons, templates)
- 🧪 Testing: 1 issue (unit tests)

**Weekly Sprint Goals:**
- Week 1: Critical issues complete
- Week 2: High priority issues complete  
- Week 3: Medium priority issues complete
- Week 4: Low priority + testing + release prep

## 🔄 Issue Management Process

### Issue Creation Workflow
1. Copy appropriate template from this document
2. Customize details for specific implementation
3. Add appropriate labels and milestone
4. Assign to developer or leave unassigned
5. Link related issues and dependencies

### Issue Updates
- Daily: Update progress in comments
- Weekly: Review priority and scope
- Completed: Close with summary of changes
- Blocked: Add `status/blocked` label with explanation

### Quality Gates
- All critical issues must be completed for release
- High-priority issues preferred but can be moved to v0.3
- Medium/low-priority issues are nice-to-have

---

**These issue templates provide a clear roadmap for systematic v0.2 development. Each issue is sized appropriately and includes concrete acceptance criteria.**

*Ready to create issues? Copy templates to GitHub and start the development sprint!*