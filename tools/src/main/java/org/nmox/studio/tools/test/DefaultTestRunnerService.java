package org.nmox.studio.tools.test;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.tools.npm.NpmService;
import org.openide.util.lookup.ServiceProvider;

/**
 * Default implementation of TestRunnerService.
 */
@ServiceProvider(service = TestRunnerService.class)
public class DefaultTestRunnerService implements TestRunnerService {
    
    private final NpmService npmService;
    private Process watchProcess;
    
    public DefaultTestRunnerService() {
        this.npmService = NpmService.getInstance();
    }
    
    @Override
    public TestRunner detectTestRunner(File projectDir) {
        // Check for test runner config files
        for (TestRunner runner : TestRunner.values()) {
            if (runner.getConfigFiles() != null) {
                for (String configFile : runner.getConfigFiles()) {
                    if (new File(projectDir, configFile).exists()) {
                        return runner;
                    }
                }
            }
        }
        
        // Check package.json for test dependencies
        File packageJson = new File(projectDir, "package.json");
        if (packageJson.exists()) {
            try {
                String content = Files.readString(packageJson.toPath());
                if (content.contains("\"jest\"")) return TestRunner.JEST;
                if (content.contains("\"mocha\"")) return TestRunner.MOCHA;
                if (content.contains("\"vitest\"")) return TestRunner.VITEST;
                if (content.contains("\"jasmine\"")) return TestRunner.JASMINE;
                if (content.contains("\"karma\"")) return TestRunner.KARMA;
                if (content.contains("\"cypress\"")) return TestRunner.CYPRESS;
                if (content.contains("\"@playwright/test\"")) return TestRunner.PLAYWRIGHT;
            } catch (IOException e) {
                // Fall through
            }
        }
        
        return TestRunner.UNKNOWN;
    }
    
    @Override
    public CompletableFuture<TestResult> runAllTests(File projectDir, TestConfiguration config) {
        TestRunner runner = detectTestRunner(projectDir);
        
        switch (runner) {
            case JEST:
                return runJestTests(projectDir, config, null);
            case MOCHA:
                return runMochaTests(projectDir, config, null);
            case VITEST:
                return runVitestTests(projectDir, config, null);
            default:
                return runNpmTest(projectDir, config);
        }
    }
    
    @Override
    public CompletableFuture<TestResult> runFileTests(File testFile, TestConfiguration config) {
        File projectDir = findProjectRoot(testFile);
        TestRunner runner = detectTestRunner(projectDir);
        
        switch (runner) {
            case JEST:
                return runJestTests(projectDir, config, testFile.getAbsolutePath());
            case MOCHA:
                return runMochaTests(projectDir, config, testFile.getAbsolutePath());
            case VITEST:
                return runVitestTests(projectDir, config, testFile.getAbsolutePath());
            default:
                return runNpmTest(projectDir, config);
        }
    }
    
    @Override
    public CompletableFuture<TestResult> runTestSuite(File projectDir, String suiteName, TestConfiguration config) {
        TestRunner runner = detectTestRunner(projectDir);
        TestConfiguration suiteConfig = TestConfiguration.builder()
            .testNamePattern(suiteName)
            .coverage(config.isCoverage())
            .verbose(config.isVerbose())
            .build();
        
        return runAllTests(projectDir, suiteConfig);
    }
    
    @Override
    public CompletableFuture<TestResult> runSingleTest(File testFile, String testName, TestConfiguration config) {
        File projectDir = findProjectRoot(testFile);
        TestRunner runner = detectTestRunner(projectDir);
        
        TestConfiguration testConfig = TestConfiguration.builder()
            .testNamePattern(testName)
            .testPathPattern(testFile.getAbsolutePath())
            .coverage(config.isCoverage())
            .verbose(config.isVerbose())
            .build();
        
        return runAllTests(projectDir, testConfig);
    }
    
    @Override
    public CompletableFuture<TestResult> runWithCoverage(File projectDir, TestConfiguration config) {
        TestConfiguration coverageConfig = TestConfiguration.builder()
            .coverage(true)
            .verbose(config.isVerbose())
            .build();
        
        return runAllTests(projectDir, coverageConfig);
    }
    
    @Override
    public CompletableFuture<Void> watchTests(File projectDir, TestConfiguration config, TestWatcher watcher) {
        return CompletableFuture.runAsync(() -> {
            TestRunner runner = detectTestRunner(projectDir);
            List<String> command = buildWatchCommand(runner, projectDir, config);
            
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(projectDir);
                pb.redirectErrorStream(true);
                
                watchProcess = pb.start();
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(watchProcess.getInputStream()))) {
                    String line;
                    StringBuilder output = new StringBuilder();
                    
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        
                        // Parse test output and notify watcher
                        if (line.contains("Test Suites:") || line.contains("Tests:")) {
                            TestResult result = parseTestOutput(output.toString(), runner);
                            watcher.onTestRunComplete(result);
                            output.setLength(0);
                        }
                    }
                }
                
            } catch (IOException e) {
                throw new RuntimeException("Failed to watch tests", e);
            }
        });
    }
    
    @Override
    public void stopWatching() {
        if (watchProcess != null && watchProcess.isAlive()) {
            watchProcess.destroy();
            watchProcess = null;
        }
    }
    
    @Override
    public List<String> getTestScripts(File projectDir) {
        List<String> scripts = new ArrayList<>();
        File packageJson = new File(projectDir, "package.json");
        
        if (packageJson.exists()) {
            try {
                String content = Files.readString(packageJson.toPath());
                Pattern pattern = Pattern.compile("\"scripts\"\\s*:\\s*\\{([^}]+)\\}");
                Matcher matcher = pattern.matcher(content);
                
                if (matcher.find()) {
                    String scriptsContent = matcher.group(1);
                    Pattern scriptPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"[^\"]*test[^\"]*\"");
                    Matcher scriptMatcher = scriptPattern.matcher(scriptsContent);
                    
                    while (scriptMatcher.find()) {
                        scripts.add(scriptMatcher.group(1));
                    }
                }
            } catch (IOException e) {
                // Return empty list
            }
        }
        
        return scripts;
    }
    
    private CompletableFuture<TestResult> runJestTests(File projectDir, TestConfiguration config, String testPath) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> command = new ArrayList<>();
            command.add("npx");
            command.add("jest");
            
            if (testPath != null) {
                command.add(testPath);
            }
            
            if (config.isCoverage()) {
                command.add("--coverage");
            }
            
            if (config.isVerbose()) {
                command.add("--verbose");
            }
            
            if (config.isBail()) {
                command.add("--bail");
            }
            
            if (config.getTestNamePattern() != null) {
                for (String pattern : config.getTestNamePattern()) {
                    command.add("-t");
                    command.add(pattern);
                }
            }
            
            command.add("--json");
            
            return executeTestCommand(projectDir, command, TestRunner.JEST);
        });
    }
    
    private CompletableFuture<TestResult> runMochaTests(File projectDir, TestConfiguration config, String testPath) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> command = new ArrayList<>();
            command.add("npx");
            command.add("mocha");
            
            if (testPath != null) {
                command.add(testPath);
            }
            
            if (config.isBail()) {
                command.add("--bail");
            }
            
            if (config.getTestNamePattern() != null && config.getTestNamePattern().length > 0) {
                command.add("--grep");
                command.add(config.getTestNamePattern()[0]);
            }
            
            command.add("--reporter");
            command.add("json");
            
            return executeTestCommand(projectDir, command, TestRunner.MOCHA);
        });
    }
    
    private CompletableFuture<TestResult> runVitestTests(File projectDir, TestConfiguration config, String testPath) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> command = new ArrayList<>();
            command.add("npx");
            command.add("vitest");
            command.add("run");
            
            if (testPath != null) {
                command.add(testPath);
            }
            
            if (config.isCoverage()) {
                command.add("--coverage");
            }
            
            if (config.isBail()) {
                command.add("--bail");
            }
            
            if (config.getTestNamePattern() != null && config.getTestNamePattern().length > 0) {
                command.add("-t");
                command.add(config.getTestNamePattern()[0]);
            }
            
            command.add("--reporter=json");
            
            return executeTestCommand(projectDir, command, TestRunner.VITEST);
        });
    }
    
    private CompletableFuture<TestResult> runNpmTest(File projectDir, TestConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> command = Arrays.asList("npm", "test");
            return executeTestCommand(projectDir, command, TestRunner.UNKNOWN);
        });
    }
    
    private TestResult executeTestCommand(File projectDir, List<String> command, TestRunner runner) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectDir);
            pb.redirectErrorStream(true);
            
            long startTime = System.currentTimeMillis();
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            long duration = System.currentTimeMillis() - startTime;
            
            String outputStr = output.toString();
            
            // Parse test output based on runner type
            return parseTestOutput(outputStr, runner);
            
        } catch (Exception e) {
            return new TestResult(false, 0, 0, 0, 0, 0, 
                new ArrayList<>(), null, "", e.getMessage());
        }
    }
    
    private TestResult parseTestOutput(String output, TestRunner runner) {
        // Parse JSON output for structured test results
        // This is simplified - real implementation would use JSON parser
        
        List<TestResult.TestSuite> suites = new ArrayList<>();
        int totalTests = 0;
        int passedTests = 0;
        int failedTests = 0;
        int skippedTests = 0;
        
        // Simple pattern matching for test counts
        Pattern testPattern = Pattern.compile("(\\d+) (passed|failed|skipped|pending)");
        Matcher matcher = testPattern.matcher(output);
        
        while (matcher.find()) {
            int count = Integer.parseInt(matcher.group(1));
            String status = matcher.group(2);
            
            switch (status) {
                case "passed":
                    passedTests = count;
                    break;
                case "failed":
                    failedTests = count;
                    break;
                case "skipped":
                case "pending":
                    skippedTests += count;
                    break;
            }
        }
        
        totalTests = passedTests + failedTests + skippedTests;
        boolean success = failedTests == 0;
        
        // Parse coverage if available
        TestResult.CoverageReport coverage = parseCoverage(output);
        
        return new TestResult(success, totalTests, passedTests, failedTests, 
            skippedTests, 0, suites, coverage, output, "");
    }
    
    private TestResult.CoverageReport parseCoverage(String output) {
        if (!output.contains("Coverage")) {
            return null;
        }
        
        // Simple coverage parsing
        Pattern coveragePattern = Pattern.compile("All files\\s+\\|\\s+([\\d.]+)\\s+\\|\\s+([\\d.]+)\\s+\\|\\s+([\\d.]+)\\s+\\|\\s+([\\d.]+)");
        Matcher matcher = coveragePattern.matcher(output);
        
        if (matcher.find()) {
            double statements = Double.parseDouble(matcher.group(1));
            double branches = Double.parseDouble(matcher.group(2));
            double functions = Double.parseDouble(matcher.group(3));
            double lines = Double.parseDouble(matcher.group(4));
            
            return new TestResult.CoverageReport(lines, branches, functions, statements, new ArrayList<>());
        }
        
        return null;
    }
    
    private List<String> buildWatchCommand(TestRunner runner, File projectDir, TestConfiguration config) {
        List<String> command = new ArrayList<>();
        
        switch (runner) {
            case JEST:
                command.add("npx");
                command.add("jest");
                command.add("--watch");
                break;
            case MOCHA:
                command.add("npx");
                command.add("mocha");
                command.add("--watch");
                break;
            case VITEST:
                command.add("npx");
                command.add("vitest");
                break;
            default:
                command.add("npm");
                command.add("test");
                command.add("--");
                command.add("--watch");
                break;
        }
        
        if (config.isCoverage()) {
            command.add("--coverage");
        }
        
        return command;
    }
    
    private File findProjectRoot(File file) {
        File current = file.getParentFile();
        
        while (current != null) {
            if (new File(current, "package.json").exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        
        return file.getParentFile();
    }
}