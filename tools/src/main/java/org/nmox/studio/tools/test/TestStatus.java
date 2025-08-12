package org.nmox.studio.tools.test;

/**
 * Test status enumeration.
 */
public enum TestStatus {
    PASSED("Passed"),
    FAILED("Failed"),
    SKIPPED("Skipped"),
    PENDING("Pending"),
    TODO("Todo");
    
    private final String displayName;
    
    TestStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}