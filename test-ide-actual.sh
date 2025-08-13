#!/bin/bash

# Comprehensive testing script for NMOX Studio actual behavior

echo "========================================="
echo "NMOX Studio Actual Behavior Testing"
echo "========================================="
echo ""

# Function to take and check screenshot
check_screenshot() {
    local test_name="$1"
    local filename="/tmp/nmox-test-${test_name}.png"
    
    echo "Test: $test_name"
    screencapture -x "$filename"
    echo "  Screenshot saved: $filename"
    
    # Check if file exists and size
    if [ -f "$filename" ]; then
        SIZE=$(ls -lh "$filename" | awk '{print $5}')
        echo "  File size: $SIZE"
    fi
    echo ""
}

# Function to check if process is running
check_process() {
    echo "Checking NMOX Studio process..."
    PS_OUTPUT=$(ps -A | grep -i java | grep -i nmox)
    if [ -n "$PS_OUTPUT" ]; then
        echo "  ✓ Process is running"
        echo "  PID: $(echo "$PS_OUTPUT" | awk '{print $1}' | head -1)"
    else
        echo "  ✗ Process not found"
    fi
    echo ""
}

# Function to check log files
check_logs() {
    echo "Checking for errors in logs..."
    
    # Check if there's an output log
    if [ -d "application/target/userdir/var/log" ]; then
        echo "  Checking application logs..."
        tail -20 application/target/userdir/var/log/messages.log 2>/dev/null | grep -E "ERROR|SEVERE|Exception" | head -5
    fi
    
    # Check build logs
    if [ -f "application/target/nmoxstudio.log" ]; then
        echo "  Recent log entries:"
        tail -10 application/target/nmoxstudio.log 2>/dev/null | head -5
    fi
    echo ""
}

# Function to test file operations
test_file_operations() {
    echo "Testing file operations..."
    
    # Create a test JavaScript file with our enhanced syntax
    cat > /tmp/ide-test.js << 'EOF'
// Testing NMOX Studio enhanced JavaScript support
class EnhancedTest {
    constructor() {
        this.cache = new Map();
        this.monitor = null;
    }
    
    async performOperation() {
        // Should be highlighted with our optimized lexer
        const result = await fetch('/api/data');
        const template = `Status: ${result.status}`;
        return template;
    }
    
    testArrowFunction = (x, y) => {
        // Arrow function with our enhanced parsing
        return x + y;
    }
}

// Test template literals - enhanced in our lexer
const message = `Hello from NMOX Studio ${new Date().toISOString()}`;
console.log(message);
EOF
    
    echo "  Created test file: /tmp/ide-test.js"
    
    # Create a Java test file
    cat > /tmp/IDETest.java << 'EOF'
public class IDETest {
    // This should trigger our CodeIndexService
    private String testField;
    
    public void testMethod() {
        System.out.println("Testing NMOX Studio");
    }
    
    public static void main(String[] args) {
        IDETest test = new IDETest();
        test.testMethod();
    }
}
EOF
    
    echo "  Created test file: /tmp/IDETest.java"
    echo ""
}

# Function to check what windows are visible
check_windows() {
    echo "Checking visible windows..."
    
    # Use AppleScript to check window titles
    osascript -e 'tell application "System Events"
        set javaProcesses to every process whose name contains "java"
        repeat with proc in javaProcesses
            try
                set windowList to name of every window of proc
                repeat with winName in windowList
                    log winName
                end repeat
            end try
        end repeat
    end tell' 2>&1 | while read line; do
        echo "  Window: $line"
    done
    echo ""
}

# Function to check memory usage
check_memory() {
    echo "Checking memory usage..."
    PID=$(ps -A | grep -i java | grep -i nmox | awk '{print $1}' | head -1)
    if [ -n "$PID" ]; then
        MEM_INFO=$(ps -p $PID -o pid,vsz,rss,comm | tail -1)
        echo "  Memory info: $MEM_INFO"
        
        # Calculate memory in MB
        RSS=$(echo "$MEM_INFO" | awk '{print $3}')
        RSS_MB=$((RSS / 1024))
        echo "  Resident memory: ${RSS_MB}MB"
    fi
    echo ""
}

# Main testing sequence
echo "1. Process Status"
echo "-----------------"
check_process

echo "2. Memory Usage"
echo "---------------"
check_memory

echo "3. Creating Test Files"
echo "----------------------"
test_file_operations

echo "4. Window Status"
echo "----------------"
check_windows

echo "5. Taking Screenshots"
echo "---------------------"
sleep 1
check_screenshot "current-state"

echo "6. Checking Logs"
echo "----------------"
check_logs

echo "7. Checking Our Improvements"
echo "-----------------------------"

# Check if our new classes were compiled
echo "Checking compiled classes..."
if [ -d "core/target/classes/org/nmox/studio/core" ]; then
    echo "  Performance classes:"
    ls -la core/target/classes/org/nmox/studio/core/performance/*.class 2>/dev/null | wc -l | xargs echo "    PerformanceMonitor classes:"
    ls -la core/target/classes/org/nmox/studio/core/cache/*.class 2>/dev/null | wc -l | xargs echo "    FileCache classes:"
    ls -la core/target/classes/org/nmox/studio/core/resources/*.class 2>/dev/null | wc -l | xargs echo "    ResourceManager classes:"
fi

if [ -d "editor/target/classes/org/nmox/studio/editor" ]; then
    echo "  Editor classes:"
    ls -la editor/target/classes/org/nmox/studio/editor/index/*.class 2>/dev/null | wc -l | xargs echo "    CodeIndexService classes:"
fi

echo ""
echo "8. Testing Module Loading"
echo "-------------------------"
# Check if our modules are in the running app
if [ -d "application/target/nmoxstudio" ]; then
    echo "  Checking deployed modules..."
    ls -d application/target/nmoxstudio/nmoxstudio/modules/*.jar 2>/dev/null | grep -E "core|editor" | while read jar; do
        echo "    Found: $(basename $jar)"
    done
fi

echo ""
echo "========================================="
echo "Test Summary"
echo "========================================="

# Summary
PROCESS_RUNNING=$(ps -A | grep -i java | grep -i nmox | wc -l)
if [ "$PROCESS_RUNNING" -gt 0 ]; then
    echo "✓ NMOX Studio process is running"
else
    echo "✗ NMOX Studio process NOT running"
fi

if [ -f "/tmp/ide-test.js" ]; then
    echo "✓ Test files created successfully"
fi

echo ""
echo "To manually verify:"
echo "1. Check if NMOX Studio window is visible on screen"
echo "2. Try File -> Open File -> /tmp/ide-test.js"
echo "3. Check if syntax highlighting works"
echo "4. Try to create a new project"
echo ""
echo "If the IDE is not responding:"
echo "- The process may be running in background"
echo "- There may be Java compatibility issues"
echo "- Check the screenshots in /tmp/nmox-test-*.png"