package org.nmox.studio.tools.test;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a test run.
 */
public class TestResult {
    
    private final boolean success;
    private final int totalTests;
    private final int passedTests;
    private final int failedTests;
    private final int skippedTests;
    private final long duration;
    private final List<TestSuite> testSuites;
    private final CoverageReport coverage;
    private final String output;
    private final String errorOutput;
    
    public TestResult(boolean success, int totalTests, int passedTests, 
                     int failedTests, int skippedTests, long duration,
                     List<TestSuite> testSuites, CoverageReport coverage,
                     String output, String errorOutput) {
        this.success = success;
        this.totalTests = totalTests;
        this.passedTests = passedTests;
        this.failedTests = failedTests;
        this.skippedTests = skippedTests;
        this.duration = duration;
        this.testSuites = testSuites;
        this.coverage = coverage;
        this.output = output;
        this.errorOutput = errorOutput;
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public int getTotalTests() { return totalTests; }
    public int getPassedTests() { return passedTests; }
    public int getFailedTests() { return failedTests; }
    public int getSkippedTests() { return skippedTests; }
    public long getDuration() { return duration; }
    public List<TestSuite> getTestSuites() { return testSuites; }
    public CoverageReport getCoverage() { return coverage; }
    public String getOutput() { return output; }
    public String getErrorOutput() { return errorOutput; }
    
    /**
     * Represents a test suite.
     */
    public static class TestSuite {
        private final String name;
        private final String file;
        private final List<TestCase> testCases;
        private final long duration;
        
        public TestSuite(String name, String file, List<TestCase> testCases, long duration) {
            this.name = name;
            this.file = file;
            this.testCases = testCases;
            this.duration = duration;
        }
        
        public String getName() { return name; }
        public String getFile() { return file; }
        public List<TestCase> getTestCases() { return testCases; }
        public long getDuration() { return duration; }
        
        public int getPassedCount() {
            return (int) testCases.stream()
                .filter(tc -> tc.getStatus() == TestStatus.PASSED)
                .count();
        }
        
        public int getFailedCount() {
            return (int) testCases.stream()
                .filter(tc -> tc.getStatus() == TestStatus.FAILED)
                .count();
        }
        
        public int getSkippedCount() {
            return (int) testCases.stream()
                .filter(tc -> tc.getStatus() == TestStatus.SKIPPED)
                .count();
        }
    }
    
    /**
     * Coverage report.
     */
    public static class CoverageReport {
        private final double lineCoverage;
        private final double branchCoverage;
        private final double functionCoverage;
        private final double statementCoverage;
        private final List<FileCoverage> fileCoverage;
        
        public CoverageReport(double lineCoverage, double branchCoverage,
                            double functionCoverage, double statementCoverage,
                            List<FileCoverage> fileCoverage) {
            this.lineCoverage = lineCoverage;
            this.branchCoverage = branchCoverage;
            this.functionCoverage = functionCoverage;
            this.statementCoverage = statementCoverage;
            this.fileCoverage = fileCoverage;
        }
        
        public double getLineCoverage() { return lineCoverage; }
        public double getBranchCoverage() { return branchCoverage; }
        public double getFunctionCoverage() { return functionCoverage; }
        public double getStatementCoverage() { return statementCoverage; }
        public List<FileCoverage> getFileCoverage() { return fileCoverage; }
        
        /**
         * Coverage for a single file.
         */
        public static class FileCoverage {
            private final String file;
            private final double lineCoverage;
            private final double branchCoverage;
            private final double functionCoverage;
            private final double statementCoverage;
            private final List<Integer> uncoveredLines;
            
            public FileCoverage(String file, double lineCoverage, double branchCoverage,
                              double functionCoverage, double statementCoverage,
                              List<Integer> uncoveredLines) {
                this.file = file;
                this.lineCoverage = lineCoverage;
                this.branchCoverage = branchCoverage;
                this.functionCoverage = functionCoverage;
                this.statementCoverage = statementCoverage;
                this.uncoveredLines = uncoveredLines;
            }
            
            public String getFile() { return file; }
            public double getLineCoverage() { return lineCoverage; }
            public double getBranchCoverage() { return branchCoverage; }
            public double getFunctionCoverage() { return functionCoverage; }
            public double getStatementCoverage() { return statementCoverage; }
            public List<Integer> getUncoveredLines() { return uncoveredLines; }
        }
    }
}