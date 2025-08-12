# Testing Strategy & Automation Plan

*Comprehensive testing approach for NMOX Studio development*

## 🎯 Testing Philosophy

Build quality into the development process rather than testing it in afterward. Every feature should have appropriate test coverage before it's considered complete.

## 📊 Current Testing State (v0.1)

### What We Have ✅
- Basic build verification (compilation succeeds)
- Manual integration testing (application starts and runs)
- Simple Maven test structure in place

### What We Need 🔧
- Unit test coverage for core components
- Integration test automation
- Performance regression testing
- UI automation for critical workflows
- Continuous integration pipeline

## 🧪 Testing Pyramid Strategy

### Unit Tests (Foundation - 70%)
**Scope:** Individual classes and methods in isolation
**Speed:** < 1 second per test, < 30 seconds total suite
**Coverage Target:** >80% for core business logic

#### Components to Test
```
Priority 1 (Critical):
- NpmService: Command execution, error handling
- WebProjectFactory: Project recognition logic
- ProjectValidator: Input validation rules
- NPMErrorParser: Error pattern matching
- TemplateEngine: File generation and variables

Priority 2 (Important):
- JavaScriptLexer: Token generation and classification
- WebProject: Project metadata and structure
- Settings: Configuration persistence
- CompletionProvider: Code suggestion logic

Priority 3 (Nice to have):
- UI Components: Validation and state management
- File utilities: Path handling and validation
```

#### Unit Test Structure
```java
// Example test class structure
class NpmServiceTest {
    
    @Mock private ProcessBuilder processBuilder;
    @Mock private Process process;
    @Mock private InputOutput inputOutput;
    
    private NpmService npmService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        npmService = new NpmService();
    }
    
    @Nested
    @DisplayName("Command Execution")
    class CommandExecution {
        
        @Test
        @DisplayName("Should execute npm command successfully")
        void shouldExecuteNpmCommandSuccessfully() {
            // Given
            String command = "install";
            File workingDir = new File("/test/project");
            
            // When
            CommandResult result = npmService.runCommand(command, workingDir);
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).contains("npm install completed");
        }
        
        @Test
        @DisplayName("Should handle npm not found error")
        void shouldHandleNpmNotFoundError() {
            // Given
            when(processBuilder.start()).thenThrow(new IOException("npm: command not found"));
            
            // When & Then
            assertThatThrownBy(() -> npmService.runCommand("install", new File(".")))
                .isInstanceOf(NpmNotFoundException.class)
                .hasMessage("NPM not found. Please install Node.js and NPM.");
        }
    }
    
    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {
        // ... error handling tests
    }
}
```

### Integration Tests (Middle - 20%)
**Scope:** Multiple components working together
**Speed:** < 5 seconds per test, < 2 minutes total suite
**Coverage Target:** Critical user workflows

#### Integration Test Scenarios
```
File Operations:
- Create project from template → Verify all files exist with correct content
- Open project → Verify project recognition and UI updates
- Run NPM command → Verify process execution and output capture

UI Workflows:
- Project wizard completion → Verify project creation and opening
- NPM Explorer interaction → Verify command execution from UI
- Error dialog display → Verify user-friendly error messages

Cross-Module Integration:
- Settings changes → Verify NPM service uses new configuration
- Template updates → Verify project creation uses latest templates
- Theme changes → Verify UI updates across all components
```

#### Integration Test Implementation
```java
@IntegrationTest
class ProjectCreationIntegrationTest {
    
    private TemporaryFolder tempFolder;
    private ProjectService projectService;
    
    @BeforeEach
    void setUp() {
        tempFolder = TemporaryFolder.create();
        projectService = new ProjectService();
    }
    
    @Test
    @DisplayName("Should create complete React project")
    void shouldCreateCompleteReactProject() {
        // Given
        ProjectConfig config = ProjectConfig.builder()
            .name("test-react-app")
            .type("react")
            .location(tempFolder.getRoot())
            .build();
        
        // When
        Project project = projectService.createProject(config);
        
        // Then
        assertThat(project).isNotNull();
        assertThat(project.getProjectDirectory()).exists();
        
        // Verify project structure
        File projectDir = FileUtil.toFile(project.getProjectDirectory());
        assertThat(new File(projectDir, "package.json")).exists();
        assertThat(new File(projectDir, "src/App.js")).exists();
        assertThat(new File(projectDir, "public/index.html")).exists();
        
        // Verify package.json content
        String packageJson = Files.readString(new File(projectDir, "package.json").toPath());
        assertThat(packageJson).contains("\"react\":");
        assertThat(packageJson).contains("\"name\": \"test-react-app\"");
    }
    
    @Test
    @DisplayName("Should handle invalid project configuration")
    void shouldHandleInvalidProjectConfiguration() {
        // Given
        ProjectConfig invalidConfig = ProjectConfig.builder()
            .name("") // Invalid empty name
            .type("react")
            .location(tempFolder.getRoot())
            .build();
        
        // When & Then
        assertThatThrownBy(() -> projectService.createProject(invalidConfig))
            .isInstanceOf(ValidationException.class)
            .hasMessage("Project name cannot be empty");
    }
}
```

### End-to-End Tests (Top - 10%)
**Scope:** Complete user workflows through the UI
**Speed:** < 30 seconds per test, < 10 minutes total suite
**Coverage Target:** Critical user journeys

#### E2E Test Scenarios
```
New User Journey:
1. Start application
2. Create new React project
3. Open project in editor
4. Run npm start command
5. Verify success message

Experienced Developer Journey:
1. Open existing project
2. Edit JavaScript file
3. Use code completion
4. Run multiple NPM commands
5. Handle error scenarios gracefully
```

## 🤖 Test Automation Setup

### Maven Test Configuration
```xml
<!-- Parent pom.xml test configuration -->
<properties>
    <junit.version>5.10.2</junit.version>
    <mockito.version>5.11.0</mockito.version>
    <assertj.version>3.25.3</assertj.version>
    <testcontainers.version>1.19.7</testcontainers.version>
</properties>

<dependencies>
    <!-- Test dependencies for all modules -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>${junit.version}</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>${mockito.version}</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>${assertj.version}</version>
        <scope>test</scope>
    </dependency>
    
    <!-- NetBeans test utilities -->
    <dependency>
        <groupId>org.netbeans.api</groupId>
        <artifactId>org-netbeans-modules-nbjunit</artifactId>
        <version>${netbeans.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- Surefire for unit tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>
            <configuration>
                <includes>
                    <include>**/*Test.java</include>
                    <include>**/*Tests.java</include>
                </includes>
                <excludes>
                    <exclude>**/*IntegrationTest.java</exclude>
                </excludes>
            </configuration>
        </plugin>
        
        <!-- Failsafe for integration tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>3.2.5</version>
            <configuration>
                <includes>
                    <include>**/*IntegrationTest.java</include>
                    <include>**/*IT.java</include>
                </includes>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>integration-test</goal>
                        <goal>verify</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        
        <!-- JaCoCo for coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Test Utilities and Helpers
```java
// Common test utilities
public class TestUtils {
    
    public static TemporaryFolder createTempProjectDir() {
        return TemporaryFolder.create();
    }
    
    public static File createTestPackageJson(File dir, String projectName) {
        String content = String.format("""
            {
              "name": "%s",
              "version": "1.0.0",
              "scripts": {
                "start": "react-scripts start",
                "build": "react-scripts build"
              }
            }
            """, projectName);
        
        File packageJson = new File(dir, "package.json");
        Files.writeString(packageJson.toPath(), content);
        return packageJson;
    }
    
    public static void mockNpmCommand(ProcessBuilder processBuilder, String output, int exitCode) {
        Process mockProcess = mock(Process.class);
        when(processBuilder.start()).thenReturn(mockProcess);
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes()));
        when(mockProcess.exitValue()).thenReturn(exitCode);
    }
}

// NetBeans specific test utilities
public class NetBeansTestUtils {
    
    public static void setUpNetBeansEnvironment() {
        // Initialize NetBeans lookup and module system for testing
        System.setProperty("org.openide.util.Lookup", "org.openide.util.lookup.Lookups");
    }
    
    public static FileObject createTestFileObject(String name, String content) {
        // Create temporary FileObject for testing
        File tempFile = createTempFile(name, content);
        return FileUtil.toFileObject(tempFile);
    }
    
    public static TopComponent createTestTopComponent(Class<? extends TopComponent> componentClass) {
        // Create TopComponent instance for UI testing
        return Lookup.getDefault().lookup(componentClass);
    }
}
```

### Mock Strategies
```java
// NPM process mocking
@ExtendWith(MockitoExtension.class)
class NpmServiceMockTest {
    
    @Mock private ProcessBuilder processBuilder;
    @Mock private Process process;
    @Mock private InputStream inputStream;
    @Mock private InputOutput inputOutput;
    
    @Captor private ArgumentCaptor<String[]> commandCaptor;
    
    @Test
    void shouldExecuteCorrectNpmCommand() {
        // Given
        String command = "install";
        File workingDir = new File("/test");
        
        when(processBuilder.start()).thenReturn(process);
        when(process.getInputStream()).thenReturn(inputStream);
        when(process.exitValue()).thenReturn(0);
        
        // When
        npmService.runCommand(command, workingDir);
        
        // Then
        verify(processBuilder).command(commandCaptor.capture());
        String[] capturedCommand = commandCaptor.getValue();
        assertThat(capturedCommand).containsExactly("npm", "run", "install");
    }
}

// File system mocking
class ProjectValidatorTest {
    
    @Test
    void shouldValidateProjectPath(@TempDir Path tempDir) {
        // Given
        File validDir = tempDir.toFile();
        File invalidDir = new File("/nonexistent/path");
        
        ProjectValidator validator = new ProjectValidator();
        
        // When & Then
        assertThat(validator.validatePath(validDir)).isValid();
        assertThat(validator.validatePath(invalidDir))
            .isInvalid()
            .hasMessage("Directory does not exist");
    }
}
```

## 🚀 Continuous Integration Pipeline

### GitHub Actions Workflow
```yaml
# .github/workflows/test.yml
name: Test Suite

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [ '17', '21' ]
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK ${{ matrix.java }}
      uses: actions/setup-java@v4
      with:
        java-version: ${{ matrix.java }}
        distribution: 'temurin'
        
    - name: Cache Maven dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
        
    - name: Run unit tests
      run: mvn clean test
      
    - name: Generate test report
      run: mvn jacoco:report
      
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v3
      with:
        file: ./target/site/jacoco/jacoco.xml

  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Set up Node.js (for NPM testing)
      uses: actions/setup-node@v4
      with:
        node-version: '18'
        
    - name: Cache Maven dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
        
    - name: Run integration tests
      run: mvn clean verify
      
    - name: Archive test results
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: test-results
        path: |
          target/surefire-reports/
          target/failsafe-reports/

  build-verification:
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ ubuntu-latest, windows-latest, macos-latest ]
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Build distribution
      run: mvn clean package -DskipTests
      
    - name: Test application startup (Linux/Mac)
      if: runner.os != 'Windows'
      run: |
        cd application/target/nmoxstudio/bin
        timeout 30s ./nmox-studio --help || test $? = 124
        
    - name: Test application startup (Windows)
      if: runner.os == 'Windows'
      run: |
        cd application/target/nmoxstudio/bin
        Start-Process -FilePath "nmox-studio.exe" -ArgumentList "--help" -Wait -Timeout 30
```

### Quality Gates
```yaml
# Quality gate configuration
quality-gate:
  runs-on: ubuntu-latest
  needs: [unit-tests, integration-tests]
  
  steps:
  - name: Check coverage threshold
    run: |
      COVERAGE=$(grep -o 'line-rate="[^"]*"' target/site/jacoco/jacoco.xml | head -1 | grep -o '[0-9.]*')
      if (( $(echo "$COVERAGE < 0.6" | bc -l) )); then
        echo "Coverage $COVERAGE is below 60% threshold"
        exit 1
      fi
      
  - name: Check test results
    run: |
      if [ -f target/surefire-reports/TEST-*.xml ]; then
        FAILURES=$(grep -o 'failures="[^"]*"' target/surefire-reports/TEST-*.xml | grep -o '[0-9]*' | paste -sd+ | bc)
        if [ "$FAILURES" -gt 0 ]; then
          echo "Found $FAILURES test failures"
          exit 1
        fi
      fi
```

## 📈 Performance Testing

### Startup Performance Test
```java
@Test
@DisplayName("Application startup should complete within 5 seconds")
void applicationStartupPerformance() {
    long startTime = System.currentTimeMillis();
    
    // Start application programmatically
    NMOXApplication app = new NMOXApplication();
    app.initialize();
    
    long duration = System.currentTimeMillis() - startTime;
    
    assertThat(duration)
        .as("Application startup time")
        .isLessThan(Duration.ofSeconds(5).toMillis());
}
```

### Memory Usage Test
```java
@Test
@DisplayName("Memory usage should remain under 500MB after basic operations")
void memoryUsageTest() {
    Runtime runtime = Runtime.getRuntime();
    
    // Perform typical operations
    createProject("test-project", "react");
    openMultipleFiles(5);
    runNpmCommand("install");
    
    // Force garbage collection
    System.gc();
    Thread.sleep(1000);
    
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    long usedMB = usedMemory / (1024 * 1024);
    
    assertThat(usedMB)
        .as("Memory usage in MB")
        .isLessThan(500);
}
```

### Response Time Testing
```java
@Test
@DisplayName("NPM command execution should respond within 1 second")
void npmResponseTimeTest() {
    // Given
    String fastCommand = "help"; // Quick npm command
    
    // When
    long startTime = System.nanoTime();
    CommandResult result = npmService.runCommand(fastCommand, new File("."));
    long duration = System.nanoTime() - startTime;
    
    // Then
    assertThat(Duration.ofNanos(duration))
        .as("NPM command response time")
        .isLessThan(Duration.ofSeconds(1));
    
    assertThat(result.isSuccess()).isTrue();
}
```

## 📊 Test Coverage Goals

### Coverage Targets by Module
```
core/        → 90% (simple, critical functionality)
tools/       → 80% (complex NPM integration)
editor/      → 70% (UI-heavy components)
ui/          → 60% (mostly UI logic)
project/     → 75% (project template logic)
application/ → 50% (integration and packaging)
```

### Coverage Reporting
```bash
# Generate coverage report
mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html

# Coverage threshold check
mvn jacoco:check -Djacoco.haltOnFailure=true
```

### Mutation Testing (Optional)
```xml
<!-- PITest for mutation testing -->
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.15.8</version>
    <configuration>
        <targetClasses>
            <param>org.nmox.studio.tools.*</param>
        </targetClasses>
        <targetTests>
            <param>org.nmox.studio.tools.*Test</param>
        </targetTests>
    </configuration>
</plugin>
```

## 🔧 Test Development Workflow

### TDD Approach for New Features
```java
// 1. Write failing test first
@Test
@DisplayName("Should parse npm error for missing dependency")
void shouldParseNpmErrorForMissingDependency() {
    // Given
    String errorOutput = "npm ERR! Cannot resolve dependency 'unknown-package'";
    NPMErrorParser parser = new NPMErrorParser();
    
    // When
    NPMError result = parser.parseError(errorOutput);
    
    // Then
    assertThat(result.getUserMessage())
        .contains("Package 'unknown-package' not found");
    assertThat(result.getSuggestions())
        .contains("Check package name spelling");
}

// 2. Implement minimal code to pass
public class NPMErrorParser {
    public NPMError parseError(String output) {
        if (output.contains("Cannot resolve dependency")) {
            String packageName = extractPackageName(output);
            return new NPMError(
                "Package '" + packageName + "' not found",
                Arrays.asList("Check package name spelling")
            );
        }
        return new NPMError("Unknown error", Collections.emptyList());
    }
}

// 3. Refactor and add more test cases
```

### Test Naming Conventions
```java
// Test class naming
NpmServiceTest.java          // Unit test
NpmServiceIntegrationTest.java  // Integration test
NpmServiceIT.java           // Alternative integration test naming

// Test method naming
@DisplayName("Should [expected behavior] when [condition]")
void should[ExpectedBehavior]When[Condition]() {
    // BDD-style: Given, When, Then
}

// Examples
shouldReturnErrorWhenNpmNotFound()
shouldCreateProjectWhenValidConfigProvided()
shouldDisplayProgressWhenLongRunningCommand()
```

## 📅 Testing Implementation Timeline

### Phase 1: Foundation (Week 1)
- [ ] Set up Maven test configuration
- [ ] Create test utilities and helpers
- [ ] Write unit tests for NpmService
- [ ] Set up CI pipeline basics

### Phase 2: Core Coverage (Week 2)
- [ ] Unit tests for ProjectValidator
- [ ] Unit tests for NPMErrorParser  
- [ ] Integration tests for project creation
- [ ] Coverage reporting setup

### Phase 3: Automation (Week 3)
- [ ] Complete CI/CD pipeline
- [ ] Performance benchmarks
- [ ] Quality gates implementation
- [ ] Test documentation

### Phase 4: Advanced Testing (Week 4)
- [ ] End-to-end test scenarios
- [ ] Cross-platform testing
- [ ] Load testing for large projects
- [ ] Test maintenance procedures

---

**Testing is not optional - it's the foundation that allows us to iterate quickly and ship confidently. Every feature should have appropriate test coverage before it's considered complete.**

*"Code without tests is broken by design."* ✅