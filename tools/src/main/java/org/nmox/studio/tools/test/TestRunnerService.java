package org.nmox.studio.tools.test;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.openide.util.Lookup;

/**
 * Service for running JavaScript tests with various test runners.
 */
public interface TestRunnerService {
    
    /**
     * Detects which test runner is configured for the project.
     */
    TestRunner detectTestRunner(File projectDir);
    
    /**
     * Runs all tests in the project.
     */
    CompletableFuture<TestResult> runAllTests(File projectDir, TestConfiguration config);
    
    /**
     * Runs tests for a specific file.
     */
    CompletableFuture<TestResult> runFileTests(File testFile, TestConfiguration config);
    
    /**
     * Runs a specific test suite.
     */
    CompletableFuture<TestResult> runTestSuite(File projectDir, String suiteName, TestConfiguration config);
    
    /**
     * Runs a specific test.
     */
    CompletableFuture<TestResult> runSingleTest(File testFile, String testName, TestConfiguration config);
    
    /**
     * Runs tests with coverage.
     */
    CompletableFuture<TestResult> runWithCoverage(File projectDir, TestConfiguration config);
    
    /**
     * Watches tests and reruns on file changes.
     */
    CompletableFuture<Void> watchTests(File projectDir, TestConfiguration config, TestWatcher watcher);
    
    /**
     * Stops watching tests.
     */
    void stopWatching();
    
    /**
     * Gets available test scripts from package.json.
     */
    List<String> getTestScripts(File projectDir);
    
    /**
     * Gets the default instance.
     */
    static TestRunnerService getInstance() {
        return Lookup.getDefault().lookup(TestRunnerService.class);
    }
    
    /**
     * Supported test runners.
     */
    enum TestRunner {
        JEST("Jest", "jest.config.js", "jest.config.ts", "jest.config.json"),
        MOCHA("Mocha", ".mocharc.js", ".mocharc.json", "mocha.opts"),
        VITEST("Vitest", "vitest.config.js", "vitest.config.ts"),
        JASMINE("Jasmine", "jasmine.json"),
        KARMA("Karma", "karma.conf.js"),
        CYPRESS("Cypress", "cypress.json", "cypress.config.js"),
        PLAYWRIGHT("Playwright", "playwright.config.js", "playwright.config.ts"),
        UNKNOWN("Unknown");
        
        private final String displayName;
        private final String[] configFiles;
        
        TestRunner(String displayName, String... configFiles) {
            this.displayName = displayName;
            this.configFiles = configFiles;
        }
        
        public String getDisplayName() { return displayName; }
        public String[] getConfigFiles() { return configFiles; }
    }
    
    /**
     * Test configuration.
     */
    class TestConfiguration {
        private boolean watch = false;
        private boolean coverage = false;
        private boolean verbose = false;
        private boolean bail = false;
        private String[] testNamePattern;
        private String[] testPathPattern;
        private int maxWorkers = 4;
        private boolean updateSnapshots = false;
        private String reporter = "default";
        private String[] setupFiles;
        private String[] testEnvironment;
        
        // Builder pattern
        public static class Builder {
            private TestConfiguration config = new TestConfiguration();
            
            public Builder watch(boolean watch) {
                config.watch = watch;
                return this;
            }
            
            public Builder coverage(boolean coverage) {
                config.coverage = coverage;
                return this;
            }
            
            public Builder verbose(boolean verbose) {
                config.verbose = verbose;
                return this;
            }
            
            public Builder bail(boolean bail) {
                config.bail = bail;
                return this;
            }
            
            public Builder testNamePattern(String... patterns) {
                config.testNamePattern = patterns;
                return this;
            }
            
            public Builder testPathPattern(String... patterns) {
                config.testPathPattern = patterns;
                return this;
            }
            
            public Builder maxWorkers(int workers) {
                config.maxWorkers = workers;
                return this;
            }
            
            public Builder reporter(String reporter) {
                config.reporter = reporter;
                return this;
            }
            
            public TestConfiguration build() {
                return config;
            }
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public boolean isWatch() { return watch; }
        public boolean isCoverage() { return coverage; }
        public boolean isVerbose() { return verbose; }
        public boolean isBail() { return bail; }
        public String[] getTestNamePattern() { return testNamePattern; }
        public String[] getTestPathPattern() { return testPathPattern; }
        public int getMaxWorkers() { return maxWorkers; }
        public boolean isUpdateSnapshots() { return updateSnapshots; }
        public String getReporter() { return reporter; }
        public String[] getSetupFiles() { return setupFiles; }
        public String[] getTestEnvironment() { return testEnvironment; }
    }
    
    /**
     * Test watcher callback.
     */
    interface TestWatcher {
        void onTestRunStart();
        void onTestRunComplete(TestResult result);
        void onTestFileChange(File file);
        void onTestPass(TestCase test);
        void onTestFail(TestCase test);
        void onTestSkip(TestCase test);
    }
}