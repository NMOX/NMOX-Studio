#!/bin/bash

# Test NMOX Studio features by creating and opening test files

echo "Testing NMOX Studio Features"
echo "============================"

# Create test files
echo ""
echo "1. Creating test JavaScript file..."
cat > /tmp/test-nmox.js << 'EOF'
// Test file for NMOX Studio
// This should show enhanced syntax highlighting

class TestClass {
    constructor() {
        this.value = 42;
        this.name = "NMOX Studio Test";
    }
    
    async fetchData(url) {
        const response = await fetch(url);
        const data = await response.json();
        return data;
    }
    
    processTemplate(name) {
        const template = `Hello ${name}, welcome to NMOX Studio!`;
        console.log(template);
        return template;
    }
}

const arrowFunction = (x, y) => x + y;
const asyncArrow = async () => {
    await new Promise(resolve => setTimeout(resolve, 1000));
    return "Complete";
};

// Test our performance improvements
function testPerformance() {
    // This code would be indexed by CodeIndexService
    const startTime = performance.now();
    
    for (let i = 0; i < 1000; i++) {
        // FileCache would optimize repeated file access
        const result = Math.sqrt(i);
    }
    
    const endTime = performance.now();
    console.log(`Performance test took ${endTime - startTime}ms`);
}

export { TestClass, arrowFunction, asyncArrow, testPerformance };
EOF

echo "2. Creating test HTML file..."
cat > /tmp/test-nmox.html << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>NMOX Studio Test</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 2rem;
        }
        .container {
            max-width: 800px;
            margin: 0 auto;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>NMOX Studio v3.0.0</h1>
        <p>Enterprise-Grade IDE with Performance Enhancements</p>
        <ul>
            <li>Performance Monitoring</li>
            <li>File Caching</li>
            <li>Resource Management</li>
            <li>Code Indexing</li>
        </ul>
    </div>
    <script src="test-nmox.js"></script>
</body>
</html>
EOF

echo "3. Creating test Java file..."
cat > /tmp/TestNMOX.java << 'EOF'
package com.nmox.test;

import org.nmox.studio.core.performance.PerformanceMonitor;
import org.nmox.studio.core.cache.FileCache;
import org.nmox.studio.core.resources.ResourceManager;

/**
 * Test class for NMOX Studio features
 */
public class TestNMOX {
    
    private final PerformanceMonitor monitor;
    private final FileCache cache;
    private final ResourceManager resources;
    
    public TestNMOX() {
        this.monitor = PerformanceMonitor.getInstance();
        this.cache = FileCache.getInstance();
        this.resources = ResourceManager.getInstance();
    }
    
    public void testPerformance() {
        try (PerformanceMonitor.TimerContext timer = monitor.startTimer("test-operation")) {
            // Simulate work
            Thread.sleep(100);
            System.out.println("Operation completed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void testCache() {
        FileCache.CacheStats stats = cache.getStats();
        System.out.println("Cache entries: " + stats.entryCount);
        System.out.println("Hit rate: " + (stats.hitRate * 100) + "%");
    }
    
    public static void main(String[] args) {
        TestNMOX test = new TestNMOX();
        test.testPerformance();
        test.testCache();
    }
}
EOF

echo ""
echo "Test files created:"
echo "  - /tmp/test-nmox.js (JavaScript with syntax highlighting)"
echo "  - /tmp/test-nmox.html (HTML file)"
echo "  - /tmp/TestNMOX.java (Java file using our improvements)"

echo ""
echo "To test in NMOX Studio:"
echo "1. Use File -> Open File to open these test files"
echo "2. Check syntax highlighting in JavaScript file"
echo "3. Use Ctrl+Click (Cmd+Click) for go-to-definition"
echo "4. Check the performance monitor and resource usage"

echo ""
echo "Taking screenshot of current IDE state..."
screencapture -x /tmp/nmox-current-state.png
echo "Screenshot saved: /tmp/nmox-current-state.png"