# Testing & Quality Strategy

## 🎯 Quality Goals

### Coverage Targets

| Type | Current | Q1 2025 | Q2 2025 | Q4 2025 |
|------|---------|---------|---------|---------|
| Unit Tests | 85% | 90% | 92% | 95% |
| Integration Tests | 60% | 70% | 75% | 80% |
| E2E Tests | 30% | 40% | 50% | 60% |
| Mutation Coverage | 40% | 50% | 60% | 70% |

### Quality Metrics

- **Defect Escape Rate:** <5% (bugs found in production)
- **Mean Time to Detection:** <24 hours
- **Test Execution Time:** <10 minutes for CI
- **Flaky Test Rate:** <1%
- **Code Review Coverage:** 100%

## 🧪 Testing Pyramid

```
         ╱╲         E2E Tests (10%)
        ╱  ╲        - User journeys
       ╱────╲       - Critical paths
      ╱      ╲      
     ╱────────╲     Integration Tests (30%)
    ╱          ╲    - Module integration
   ╱────────────╲   - API contracts
  ╱              ╲  
 ╱────────────────╲ Unit Tests (60%)
╱                  ╲- Business logic
────────────────────- Pure functions
```

## 🔬 Unit Testing

### Test Structure

```java
@DisplayName("NpmService Unit Tests")
class NpmServiceTest {
    
    @Nested
    @DisplayName("Package Installation")
    class PackageInstallation {
        
        @Test
        @DisplayName("Should install package with correct version")
        void shouldInstallPackageWithVersion() {
            // Given
            NpmService service = new NpmService();
            File projectDir = tempDir.toFile();
            String packageName = "lodash@4.17.21";
            
            // When
            CompletableFuture<String> result = 
                service.install(projectDir, packageName);
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(30));
            assertThat(packageJsonContains(projectDir, "lodash", "4.17.21"))
                .isTrue();
        }
        
        @Test
        @DisplayName("Should handle network failures gracefully")
        void shouldHandleNetworkFailure() {
            // Test with network simulation
        }
    }
}
```

### Mocking Strategy

```java
public class TestDoubles {
    
    // Stub - Returns canned responses
    public static class FileSystemStub implements FileSystem {
        private final Map<Path, String> files = new HashMap<>();
        
        public void addFile(Path path, String content) {
            files.put(path, content);
        }
        
        @Override
        public String readFile(Path path) {
            return files.getOrDefault(path, "");
        }
    }
    
    // Mock - Verifies interactions
    public static class BuildServiceMock implements BuildService {
        private final List<BuildRequest> requests = new ArrayList<>();
        
        @Override
        public CompletableFuture<BuildResult> build(BuildRequest request) {
            requests.add(request);
            return CompletableFuture.completedFuture(
                new BuildResult(true, "Success"));
        }
        
        public void verifyBuildCalled(BuildRequest expected) {
            assertThat(requests).contains(expected);
        }
    }
    
    // Fake - Working implementation
    public static class InMemoryCache implements Cache {
        private final Map<String, Object> cache = new ConcurrentHashMap<>();
        
        @Override
        public void put(String key, Object value) {
            cache.put(key, value);
        }
        
        @Override
        public Object get(String key) {
            return cache.get(key);
        }
    }
}
```

### Property-Based Testing

```java
@Property
void parseValidJavaScriptCode(@ForAll @JavaScriptCode String code) {
    // Property: All valid JS code should parse without errors
    Parser parser = new JavaScriptParser();
    
    assertThatCode(() -> parser.parse(code))
        .doesNotThrowAnyException();
}

@Property
void completionsShouldBeRelevant(
        @ForAll @CodeContext String context,
        @ForAll @AlphaNumeric String prefix) {
    // Property: All completions should start with prefix
    List<CompletionItem> completions = 
        completionProvider.getCompletions(context, prefix);
    
    assertThat(completions)
        .allMatch(item -> item.getLabel().startsWith(prefix));
}
```

## 🔗 Integration Testing

### Module Integration Tests

```java
@IntegrationTest
@SpringBootTest
class EditorToolsIntegrationTest {
    
    @Autowired
    private EditorService editorService;
    
    @Autowired
    private BuildToolService buildToolService;
    
    @Test
    void shouldTriggerBuildOnSave() {
        // Given
        File project = createTestProject();
        editorService.openFile(new File(project, "index.js"));
        
        // When
        editorService.saveFile();
        
        // Then
        await().atMost(5, SECONDS).until(() -> 
            buildToolService.getLastBuildResult() != null
        );
        
        assertThat(buildToolService.getLastBuildResult().isSuccess())
            .isTrue();
    }
}
```

### Contract Testing

```java
@PactProvider("nmox-studio")
@Provider("LanguageServer")
class LanguageServerContractTest {
    
    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }
    
    @State("editor has typescript file open")
    void setupTypescriptFile() {
        languageServer.openFile("test.ts", "const x: number = 42;");
    }
    
    @State("completion requested at position")
    void setupCompletionRequest() {
        languageServer.setCursorPosition(1, 10);
    }
}
```

## 🌐 End-to-End Testing

### UI Testing Framework

```java
@E2ETest
class UserJourneyTest {
    private Robot robot;
    private IDE ide;
    
    @BeforeEach
    void setup() {
        robot = new Robot();
        ide = IDE.launch();
    }
    
    @Test
    @DisplayName("User can create and run a React project")
    void createAndRunReactProject() {
        // Create new project
        robot.clickMenu("File", "New Project");
        robot.selectTemplate("React");
        robot.typeText("my-app");
        robot.clickButton("Create");
        
        // Wait for project creation
        await().atMost(30, SECONDS).until(() -> 
            ide.getProjectExplorer().hasProject("my-app")
        );
        
        // Open App.js
        robot.doubleClick("App.js");
        
        // Edit code
        robot.typeText("console.log('Hello World');");
        robot.shortcut(CTRL, 'S'); // Save
        
        // Run project
        robot.clickButton("Run");
        
        // Verify output
        await().atMost(10, SECONDS).until(() ->
            ide.getConsole().contains("Compiled successfully")
        );
        
        // Verify browser opens
        assertThat(Browser.isOpen("http://localhost:3000")).isTrue();
    }
}
```

### Visual Regression Testing

```java
@VisualTest
class ThemeTest {
    
    @Test
    void darkThemeLooksCorrect() {
        IDE ide = IDE.launch();
        ide.setTheme(Theme.DARK);
        ide.openFile("sample.js");
        
        BufferedImage screenshot = ide.captureScreenshot();
        
        // Compare with baseline
        VisualDiff diff = VisualComparator.compare(
            screenshot, 
            loadBaseline("dark-theme-editor.png")
        );
        
        assertThat(diff.getDifferencePercent()).isLessThan(0.1);
    }
}
```

## 🔄 Continuous Testing

### CI Pipeline

```yaml
name: Test Pipeline
on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17, 21]
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: ${{ matrix.java }}
      - name: Run unit tests
        run: mvn test
      - name: Upload coverage
        uses: codecov/codecov-action@v3
        
  integration-tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
    steps:
      - uses: actions/checkout@v3
      - name: Run integration tests
        run: mvn verify -Pintegration
        
  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Start application
        run: ./scripts/start-app.sh
      - name: Run E2E tests
        run: npm run test:e2e
      - name: Upload screenshots
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: e2e-screenshots
          path: test-results/screenshots/
```

### Test Environments

| Environment | Purpose | Data | Reset |
|-------------|---------|------|-------|
| Unit | Isolated testing | Mocked | Each test |
| Integration | Module testing | Test DB | Each suite |
| Staging | E2E testing | Synthetic | Daily |
| Performance | Load testing | Generated | Per run |
| Security | Pen testing | Sanitized | Weekly |

## 🐛 Mutation Testing

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <configuration>
        <targetClasses>
            <param>org.nmox.studio.*</param>
        </targetClasses>
        <targetTests>
            <param>org.nmox.studio.*Test</param>
        </targetTests>
        <mutators>
            <mutator>STRONGER</mutator>
        </mutators>
        <mutationThreshold>70</mutationThreshold>
    </configuration>
</plugin>
```

### Mutation Coverage Goals

```java
public class Calculator {
    public int add(int a, int b) {
        return a + b;  // Mutations: a - b, a * b, return 0
    }
}

// Test must kill all mutations
@Test
void testAdd() {
    assertThat(calculator.add(2, 3)).isEqualTo(5);  // Kills a - b
    assertThat(calculator.add(0, 0)).isEqualTo(0);  // Kills return 0
    assertThat(calculator.add(-1, 1)).isEqualTo(0); // Kills a * b
}
```

## 🔍 Static Analysis

### Code Quality Tools

```xml
<!-- SpotBugs -->
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <configuration>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <failOnError>true</failOnError>
    </configuration>
</plugin>

<!-- PMD -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <configuration>
        <rulesets>
            <ruleset>/rulesets/java/nmox-studio.xml</ruleset>
        </rulesets>
    </configuration>
</plugin>

<!-- Checkstyle -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <configuration>
        <configLocation>checkstyle.xml</configLocation>
        <failOnViolation>true</failOnViolation>
    </configuration>
</plugin>
```

### Security Scanning

```yaml
# SAST with SonarQube
sonarqube:
  projectKey: nmox-studio
  sources: src
  tests: src/test
  java.binaries: target/classes
  sonar.coverage.jacoco.xmlReportPaths: target/site/jacoco/jacoco.xml
  
# Dependency scanning
dependency-check:
  failBuildOnCVSS: 7
  suppressionFile: dependency-check-suppressions.xml
```

## 📊 Test Metrics & Reporting

### Dashboard Metrics

```javascript
const testMetrics = {
    coverage: {
        unit: 85,
        integration: 60,
        e2e: 30,
        mutation: 40
    },
    execution: {
        duration: 8.5, // minutes
        parallel: true,
        flakyRate: 0.8 // %
    },
    quality: {
        defectEscape: 3.2, // %
        mttr: 18, // hours
        reviewCoverage: 100 // %
    }
};
```

### Test Report Generation

```java
@TestExecutionListener
public class TestReporter implements TestExecutionListener {
    
    @Override
    public void executionFinished(TestPlan testPlan, 
                                 TestExecutionSummary summary) {
        Report report = Report.builder()
            .totalTests(summary.getTestsFoundCount())
            .passed(summary.getTestsSucceededCount())
            .failed(summary.getTestsFailedCount())
            .skipped(summary.getTestsSkippedCount())
            .duration(summary.getTimeFinished() - summary.getTimeStarted())
            .failures(summary.getFailures())
            .build();
            
        reportGenerator.generate(report, "target/test-report.html");
        
        if (summary.getTestsFailedCount() > 0) {
            notificationService.notifyFailure(report);
        }
    }
}
```

## 🚨 Test Failure Management

### Flaky Test Detection

```java
@RetryingTest(3)
@DisplayName("Potentially flaky test")
void testWithRetry() {
    // Test that might fail due to timing issues
}

// Flaky test quarantine
@Tag("flaky")
@Disabled("Quarantined until fixed - NMOX-123")
void knownFlakyTest() {
    // Temporarily disabled
}
```

### Test Failure Analysis

```java
public class FailureAnalyzer {
    
    public FailureReport analyze(TestFailure failure) {
        return FailureReport.builder()
            .category(categorize(failure))
            .rootCause(findRootCause(failure))
            .affectedTests(findRelatedFailures(failure))
            .suggestedFix(suggestFix(failure))
            .priority(calculatePriority(failure))
            .build();
    }
    
    private FailureCategory categorize(TestFailure failure) {
        if (failure.getMessage().contains("Connection refused")) {
            return FailureCategory.ENVIRONMENT;
        } else if (failure.getMessage().contains("Timeout")) {
            return FailureCategory.TIMING;
        } else if (failure.getStackTrace().contains("NullPointer")) {
            return FailureCategory.LOGIC;
        }
        return FailureCategory.UNKNOWN;
    }
}
```

## ✅ Quality Gates

```yaml
quality-gates:
  unit-tests:
    coverage: 90
    pass-rate: 100
  integration-tests:
    coverage: 70
    pass-rate: 98
  performance:
    p95-latency: 100ms
    memory: 500MB
  security:
    high-vulnerabilities: 0
    medium-vulnerabilities: 5
  code-quality:
    duplications: 3%
    complexity: 10
    code-smells: 50
```

---

**Last Updated:** January 2025  
**QA Lead:** qa@nmox.studio  
**Test Dashboard:** [metrics.nmox.studio/tests](https://metrics.nmox.studio/tests)