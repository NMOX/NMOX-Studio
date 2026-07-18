# Contributing to NMOX Studio

*Welcome! Help us build the modern web development IDE*

## 🎯 Project Vision

NMOX Studio is a NetBeans Platform IDE for web development built around the Task Rack — 50 hardware-styled task devices wired with patch cables — plus a 70+-language polyglot editor, five studios (Block, API, DB, Contract/Web3, Infra Designer), 71 Learning Spaces, and installers for all three OSes.

**Current Status:** shipping (v1.85.x line); every merge to `main` goes through a three-OS CI gate and each tag publishes a full release (installers, SBOM, in-app update-center catalog).
**Where the project is heading:** read [docs/engineering/plan.md](docs/engineering/plan.md) — the living plan with the honest gaps and ranked opportunities.

## 🚀 Quick Start for Contributors

### Prerequisites
- Java 21+ (the project targets Java 21 LTS)
- Maven 3.6+
- Git 2.30+
- Basic familiarity with NetBeans Platform (helpful but not required)

### Get Started in 5 Minutes
```bash
# 1. Clone and build
git clone https://github.com/NMOX/NMOX-Studio.git
cd NMOX-Studio
mvn clean package -DskipTests

# 2. Run the application
cd application/target/nmoxstudio/bin
./nmox-studio

# 3. Create a feature branch
git checkout -b feature/your-contribution

# 4. Make changes and test
mvn clean test
mvn clean package

# 5. Submit pull request
git push origin feature/your-contribution
```

## 📋 How to Contribute

### 1. Choose Your Contribution Type

#### 🐛 Bug Fixes
- Check [existing issues](https://github.com/NMOX/NMOX-Studio/issues) for reported bugs
- Small fixes welcome without prior discussion
- For complex bugs, comment on the issue before starting

#### ✨ New Features
- Review [docs/engineering/plan.md](docs/engineering/plan.md) for the current plan and ranked opportunities
- Check the [deferred-debt ledger](docs/engineering/tech-debt.md) for well-scoped open items with written context
- For new ideas, create an issue for discussion first

#### 📚 Documentation
- Fix typos, improve clarity, add examples
- Update documentation for code changes
- Create tutorials or guides

#### 🧪 Testing
- Add unit tests for existing features
- Improve test coverage
- Create integration tests

### 2. Development Workflow

#### Branch Naming
```
feature/short-description         # New features
bugfix/issue-description         # Bug fixes  
hotfix/critical-issue           # Critical fixes
refactor/component-name         # Code refactoring
docs/section-update             # Documentation
test/component-coverage         # Testing improvements
```

#### Commit Messages
```
type(scope): short description

Optional longer description.

- Bullet points for details
- Reference issues: fixes #123, closes #456

Examples:
feat(editor): add JavaScript syntax highlighting
fix(npm): handle missing package.json gracefully
docs(readme): update build instructions
test(tools): add unit tests for NpmService
```

#### Code Style
- **Java**: Follow standard Java conventions
- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters
- **Naming**: Descriptive names, camelCase for variables/methods
- **Comments**: Javadoc for public APIs, inline comments for complex logic

### 3. Testing Requirements

#### For Bug Fixes
- Add test case that reproduces the bug
- Verify fix resolves the issue
- Ensure no regressions in existing functionality

#### For New Features
- Unit tests for core logic (aim for >80% coverage)
- Integration tests for user-facing features
- Manual testing with various scenarios

#### Running Tests
```bash
# All tests
mvn test

# Specific module
cd tools/
mvn test

# Specific test class
mvn test -Dtest=NpmServiceTest

# Integration tests
mvn verify
```

## 🏗️ Architecture Guidelines

### Module Structure
```
core/           # Platform services, shared SPI (Device SPI, ProjectAim/LiveServings)
editor/         # 72 TextMate grammars, LSP, completion, Navigator outline, DAP debugging
tools/          # NPM integration, WebProject/manifest detection (51 manifests)
rack/           # The Task Rack: 50 devices, Project Studio, Block Studio, Learning Spaces
apiclient/      # API Studio        dbstudio/  # DB Studio (6 engines)
web3/           # Contract Studio   infra/     # Multi-cloud infra designer
project/        # Project explorer, Workbench
ui/             # Main window, Welcome, actions, Options, update center
branding/       # Splash, icons    application/ # Final packaging and distribution
```

### Design Principles
1. **Leverage NetBeans Platform**: Use existing APIs before creating custom solutions
2. **Incremental Enhancement**: Small, working improvements over big rewrites
3. **User-Focused**: Features should solve real developer problems
4. **Performance Matters**: No UI blocking, reasonable memory usage
5. **Simple Over Clever**: Maintainable code beats optimal algorithms

### NetBeans Platform Patterns
```java
// Service registration
@ServiceProvider(service = SomeService.class)
public class SomeServiceImpl implements SomeService {
    // Implementation
}

// Service consumption
SomeService service = Lookup.getDefault().lookup(SomeService.class);

// TopComponent for UI panels
@TopComponent.Registration(mode = "editor", openAtStartup = false)
public class MyPanel extends TopComponent {
    // UI implementation
}

// Settings persistence
Preferences prefs = NbPreferences.forModule(MyClass.class);
prefs.put("key", "value");
```

## 📊 Priority Areas

### Great First Contributions
1. **A new language vertical** — grammar + comment/completion registration + device lanes; the recipe is repeated ~70 times in-tree, so examples abound
2. **A new Learning Space** — pure JSON in `learn-catalog.json` (or a drop-in file under `~/.nmox/learn-catalog.d/`); see [docs/learning-spaces.md](docs/learning-spaces.md)
3. **A third-party rack device** — build against the frozen Device SPI without touching this repo; see [docs/device-spi.md](docs/device-spi.md)

### Larger Contributions
1. **Open ledger items** — [docs/engineering/tech-debt.md](docs/engineering/tech-debt.md) records every deliberately deferred item with its reasons
2. **i18n** — ~450 user-visible strings are hardcoded (ledger 24), a well-scoped sweep for someone who wants breadth
3. **Test coverage** — floors are gated per module; raising one is always welcome

## 🔍 Code Review Process

### Before Submitting
- [ ] Code compiles without warnings
- [ ] Tests pass locally
- [ ] Manual testing completed
- [ ] Documentation updated if needed
- [ ] Commit messages follow format
- [ ] No sensitive information (API keys, passwords)

### Pull Request Template
```markdown
## Summary
Brief description of changes and motivation.

## Changes Made
- Bullet point list of modifications
- Files added/modified/removed

## Testing
- [ ] Unit tests added/updated
- [ ] Manual testing completed
- [ ] Performance impact considered

## Documentation
- [ ] Code comments updated
- [ ] User documentation updated if needed
- [ ] API documentation updated if needed

## Related Issues
Fixes #123, addresses #456
```

### Review Criteria
- **Functionality**: Does it work as intended?
- **Code Quality**: Is it readable and maintainable?
- **Testing**: Adequate test coverage?
- **Performance**: No negative impact?
- **Security**: No security vulnerabilities?
- **Documentation**: Changes are documented?

## 🧪 Testing Guidelines

### Unit Tests
```java
// Example test structure
@Test
@DisplayName("Should parse npm error when command not found")
void shouldParseNpmErrorWhenCommandNotFound() {
    // Given
    String errorOutput = "npm: command not found";
    NPMErrorParser parser = new NPMErrorParser();
    
    // When
    NPMError result = parser.parseError(errorOutput);
    
    // Then
    assertThat(result.getUserMessage())
        .contains("NPM not found");
    assertThat(result.getSuggestions())
        .contains("Install Node.js");
}
```

### Integration Tests
```java
@Test
@DisplayName("Should create React project with proper structure")
void shouldCreateReactProjectWithProperStructure() {
    // Given
    File tempDir = createTempDirectory();
    ProjectConfig config = new ProjectConfig("test-react", tempDir);
    
    // When
    projectService.createProject("react", config);
    
    // Then
    assertThat(new File(tempDir, "package.json")).exists();
    assertThat(new File(tempDir, "src/App.js")).exists();
    // ... verify complete project structure
}
```

### Manual Testing Checklist
- [ ] Application starts without errors
- [ ] Can create new projects from templates
- [ ] NPM commands execute and show output
- [ ] Syntax highlighting works for JavaScript files
- [ ] Settings persist across restarts
- [ ] Error handling provides helpful messages

## 🌍 Community Guidelines

### Communication
- **Be Respectful**: Treat all contributors with courtesy
- **Be Constructive**: Provide helpful feedback and suggestions
- **Be Patient**: Remember that people contribute in their spare time
- **Be Inclusive**: Welcome developers of all skill levels

### Getting Help
- **Documentation**: Check docs/ folder first
- **GitHub Issues**: Search existing issues before creating new ones
- **Discussions**: Use GitHub Discussions for questions and ideas
- **Code Questions**: Comment on relevant files or issues

### Reporting Issues
```markdown
**Environment**
- NMOX Studio version: (Help ▸ About shows the stamped version)
- Operating System: e.g. macOS 15
- Java version: bundled runtime, or your JDK 21+ for source builds

**Description**
Clear description of the issue.

**Steps to Reproduce**
1. Open NMOX Studio
2. Create new React project
3. Run npm start
4. Error occurs

**Expected Behavior**
What should have happened.

**Actual Behavior**
What actually happened.

**Screenshots**
If applicable, add screenshots.

**Additional Context**
Any other relevant information.
```

## 🎉 Recognition

### Contributors
All contributors are recognized in:
- Release notes
- Contributors section in README
- Annual contributor appreciation
- Special recognition for significant contributions

### Types of Contributions Valued
- Code contributions (features, fixes, tests)
- Documentation improvements
- Bug reports and feature requests
- Community support and mentoring
- Design and UX feedback
- Performance testing and optimization

## 📚 Resources

### Essential Reading
- [NetBeans Platform Developer Guide](https://netbeans.apache.org/kb/docs/platform/)
- [The current plan](docs/engineering/plan.md)
- [The deferred-debt ledger](docs/engineering/tech-debt.md)
- [The user guide](docs/user-guide.md)

### Code Examples
- [NPM Service Implementation](tools/src/main/java/org/nmox/studio/tools/npm/NpmService.java)
- [Project Factory Pattern](tools/src/main/java/org/nmox/studio/tools/npm/WebProjectFactory.java)
- [DataObject Registration](editor/src/main/java/org/nmox/studio/editor/javascript/JavaScriptDataObject.java)

### Tools and APIs
- [NetBeans API Documentation](https://bits.netbeans.org/22/javadoc/)
- [Maven NetBeans Plugin](https://netbeans.apache.org/wiki/DevFaqActionAddProjectCustomizer)
- [FlatLaf Look and Feel](https://www.formdev.com/flatlaf/)

---

**Thank you for contributing to NMOX Studio! Every contribution, no matter how small, helps us build a better web development experience.**

*Questions? Create an issue or start a discussion. We're here to help!*
