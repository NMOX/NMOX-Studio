package org.nmox.studio.tools.test;

/**
 * Represents a single test case.
 */
public class TestCase {
    
    private final String name;
    private final String fullName;
    private final TestStatus status;
    private final long duration;
    private final String errorMessage;
    private final String errorStack;
    private final String file;
    private final int line;
    
    public TestCase(String name, String fullName, TestStatus status, 
                   long duration, String errorMessage, String errorStack,
                   String file, int line) {
        this.name = name;
        this.fullName = fullName;
        this.status = status;
        this.duration = duration;
        this.errorMessage = errorMessage;
        this.errorStack = errorStack;
        this.file = file;
        this.line = line;
    }
    
    // Getters
    public String getName() { return name; }
    public String getFullName() { return fullName; }
    public TestStatus getStatus() { return status; }
    public long getDuration() { return duration; }
    public String getErrorMessage() { return errorMessage; }
    public String getErrorStack() { return errorStack; }
    public String getFile() { return file; }
    public int getLine() { return line; }
    
    public boolean isPassed() {
        return status == TestStatus.PASSED;
    }
    
    public boolean isFailed() {
        return status == TestStatus.FAILED;
    }
    
    public boolean isSkipped() {
        return status == TestStatus.SKIPPED || status == TestStatus.PENDING;
    }
}