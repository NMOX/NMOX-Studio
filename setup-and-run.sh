#!/bin/bash
# NMOX Studio - Setup and Run Script
# This script configures Java environment and provides build/run options

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   NMOX Studio Setup & Build Script    ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to find Java installation
find_java() {
    local version=$1
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        /usr/libexec/java_home -v "$version" 2>/dev/null || echo ""
    else
        # Linux - check common locations
        for dir in /usr/lib/jvm/java-${version}-* /usr/lib/jvm/jdk-${version}*; do
            if [ -d "$dir" ]; then
                echo "$dir"
                return
            fi
        done
        echo ""
    fi
}

# Try to find Java 23 first, then Java 17
echo -e "${YELLOW}Detecting Java installations...${NC}"

JAVA_23=$(find_java 23)
JAVA_17=$(find_java 17)

if [ -n "$JAVA_23" ]; then
    export JAVA_HOME="$JAVA_23"
    echo -e "${GREEN}✓ Found Java 23: $JAVA_HOME${NC}"
elif [ -n "$JAVA_17" ]; then
    export JAVA_HOME="$JAVA_17"
    echo -e "${GREEN}✓ Found Java 17: $JAVA_HOME${NC}"
else
    echo -e "${RED}✗ Error: Java 17 or 23 not found!${NC}"
    echo ""
    echo "Please install Java 17 or 23:"
    echo "  macOS:   brew install openjdk@23"
    echo "  Linux:   apt install openjdk-23-jdk  (Debian/Ubuntu)"
    echo "           dnf install java-23-openjdk (Fedora/RHEL)"
    echo ""
    exit 1
fi

# Verify Java version
echo ""
echo -e "${YELLOW}Java version:${NC}"
"$JAVA_HOME/bin/java" -version
echo ""

# Show available Java installations
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo -e "${YELLOW}All available Java versions:${NC}"
    /usr/libexec/java_home -V 2>&1 | grep -E "^\s+[0-9]" || true
    echo ""
fi

# Maven version check
echo -e "${YELLOW}Maven version:${NC}"
mvn --version | head -n 1
echo ""

# Display menu
echo -e "${BLUE}What would you like to do?${NC}"
echo ""
echo "  1) Clean build (skip tests) - FAST"
echo "  2) Full build with tests"
echo "  3) Build and run IDE"
echo "  4) Run IDE (must build first)"
echo "  5) Clean everything and rebuild"
echo "  6) Run tests only"
echo "  7) Exit"
echo ""
read -p "Enter choice [1-7]: " choice

case $choice in
    1)
        echo -e "${GREEN}Building project (skipping tests)...${NC}"
        mvn clean package -DskipTests -Dnetbeans.verify.integrity=false
        echo ""
        echo -e "${GREEN}✓ Build complete!${NC}"
        echo -e "Run the IDE with: ${YELLOW}./application/target/nmoxstudio/bin/nmoxstudio${NC}"
        ;;
    2)
        echo -e "${GREEN}Building project with tests...${NC}"
        mvn clean test package -Dnetbeans.verify.integrity=false
        echo ""
        echo -e "${GREEN}✓ Build complete!${NC}"
        echo -e "Run the IDE with: ${YELLOW}./application/target/nmoxstudio/bin/nmoxstudio${NC}"
        ;;
    3)
        echo -e "${GREEN}Building and running...${NC}"
        mvn clean package -DskipTests -Dnetbeans.verify.integrity=false
        echo ""
        echo -e "${GREEN}✓ Build complete! Starting IDE...${NC}"
        echo ""
        ./application/target/nmoxstudio/bin/nmoxstudio --jdkhome "$JAVA_HOME"
        ;;
    4)
        echo -e "${GREEN}Starting IDE...${NC}"
        if [ ! -f "./application/target/nmoxstudio/bin/nmoxstudio" ]; then
            echo -e "${RED}✗ Error: IDE not built yet!${NC}"
            echo "Please run option 1, 2, or 3 first to build the project."
            exit 1
        fi
        ./application/target/nmoxstudio/bin/nmoxstudio --jdkhome "$JAVA_HOME"
        ;;
    5)
        echo -e "${GREEN}Cleaning and rebuilding...${NC}"
        mvn clean install -DskipTests -Dnetbeans.verify.integrity=false
        echo ""
        echo -e "${GREEN}✓ Clean build complete!${NC}"
        echo -e "Run the IDE with: ${YELLOW}./application/target/nmoxstudio/bin/nmoxstudio${NC}"
        ;;
    6)
        echo -e "${GREEN}Running tests...${NC}"
        mvn test
        echo ""
        echo -e "${GREEN}✓ Tests complete!${NC}"
        ;;
    7)
        echo -e "${YELLOW}Exiting...${NC}"
        exit 0
        ;;
    *)
        echo -e "${RED}Invalid choice!${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Done!${NC}"
echo -e "${BLUE}========================================${NC}"
